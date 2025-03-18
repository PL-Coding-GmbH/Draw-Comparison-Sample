package com.plcoding.drawinginjetpackcompose

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastRoundToInt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

data class DrawingState(
    val selectedColor: Color = Color.Black,
    val currentPath: PathData? = null,
    val referencePath: List<PathData> = emptyList(),
    val userPath: List<PathData> = emptyList(),
    val score: Int? = null,
)

data class PathData(
    val id: String,
    val color: Color,
    val path: List<Offset>
)

sealed interface DrawingAction {
    data object OnNewPathStart : DrawingAction
    data class OnDraw(val offset: Offset) : DrawingAction
    data object OnPathEnd : DrawingAction
    data class OnSelectColor(val color: Color) : DrawingAction
    data object OnClearCanvasClick : DrawingAction
    data class OnCompareDrawingsClick(val canvasWidth: Int, val canvasHeight: Int) : DrawingAction
    data class OnCanvasPrepared(val size: IntSize) : DrawingAction
}

class DrawingViewModel(
    private val application: Application
) : ViewModel() {

    private val _state = MutableStateFlow(DrawingState())
    val state = _state.asStateFlow()

    private var canvasSize = MutableStateFlow(IntSize.Zero)

    init {
        ImagesRepo
            .drawings
            .combine(canvasSize) { drawings, canvasSize ->
                if (canvasSize == IntSize.Zero) {
                    return@combine
                } else {
                    val rocket = drawings["fish"]
                    val path = scalePathsToCanvas(rocket!!, canvasSize)
                    _state.update {
                        it.copy(
                            referencePath = path
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }


    fun onAction(action: DrawingAction) {
        when (action) {
            is DrawingAction.OnClearCanvasClick -> onClearCanvasClick()
            is DrawingAction.OnDraw -> onDraw(action.offset)
            is DrawingAction.OnNewPathStart -> onNewPathStart()
            is DrawingAction.OnPathEnd -> onPathEnd()
            is DrawingAction.OnSelectColor -> onSelectColor(action.color)
            is DrawingAction.OnCompareDrawingsClick -> compareDrawings(action.canvasWidth, action.canvasHeight)
            is DrawingAction.OnCanvasPrepared -> canvasSize.update { action.size }
        }
    }

    private fun compareDrawings(canvasWidth: Int, canvasHeight: Int) = viewModelScope.launch {
        val userStrokeWidth = 10f
        val referenceStrokeWidth = 100f
        val defaultOffset = (referenceStrokeWidth - userStrokeWidth) / 2f

        val userPaths = _state.value.referencePath
        val (user, userPathLength) = drawPathsToBitmap(
            width = canvasWidth,
            height = canvasHeight,
            paths = userPaths,
            defaultStrokeWidth = userStrokeWidth,
            defaultOffset = defaultOffset
        )
        val referencePaths = _state.value.userPath
        val (reference, referencePathLength) = drawPathsToBitmap(
            width = canvasWidth,
            height = canvasHeight,
            paths = referencePaths,
            defaultStrokeWidth = referenceStrokeWidth
        )

        withContext(Dispatchers.IO) {
            val referenceFile = File(application.filesDir, "reference.png")
            FileOutputStream(referenceFile).use { outputStream ->
                user.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            val userFile = File(application.filesDir, "user.png")
            FileOutputStream(userFile).use { outputStream ->
                reference.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            val overlayFile = File(application.filesDir, "overlay.png")
            FileOutputStream(overlayFile).use { outputStream ->
                getOverlayBitmap(
                    base = reference,
                    cover = user
                ).compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        }

        val coverage = computeBitmapOverlapCoverage(
            reference = reference,
            user = user
        )

        println("User path length: $userPathLength")
        println("Reference path length: $referencePathLength")
        println("Ratio: ${userPathLength / referencePathLength}")

        // User path length is at least 70% of reference path length? Don't account for length then.
        // User path length is less than 70% of reference path length? PENALTY!
        val score = if(userPathLength / referencePathLength > 0.7f) {
            coverage
        } else {
            (coverage - (1f - (userPathLength / referencePathLength))).coerceAtLeast(0f)
        }

        _state.update {
            it.copy(
                score = (score * 100).fastRoundToInt()
            )
        }
    }

    private fun getOverlayBitmap(base: Bitmap, cover: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(base.width, base.height, base.config!!)

        val canvas = Canvas(output)

        canvas.drawBitmap(base, 0f, 0f, null)

        val paint = Paint().apply {
            colorFilter = PorterDuffColorFilter(android.graphics.Color.RED, PorterDuff.Mode.SRC_IN)
        }

        canvas.drawBitmap(cover, 0f, 0f, paint)

        return output
    }

    private fun scalePathsToCanvas(paths: List<PathData>, canvasSize: IntSize, paddingPercent: Float = 0.1f): List<PathData> {
        if (paths.isEmpty() || canvasSize.width == 0 || canvasSize.height == 0) return paths

        val paddingHeight = canvasSize.height * paddingPercent
        val paddingWidth = canvasSize.width * paddingPercent

        val allOffsets = paths.flatMap { it.path }
        val minX = allOffsets.minOfOrNull { it.x } ?: return paths
        val maxX = allOffsets.maxOfOrNull { it.x } ?: return paths
        val minY = allOffsets.minOfOrNull { it.y } ?: return paths
        val maxY = allOffsets.maxOfOrNull { it.y } ?: return paths

        val pathsWidth = maxX - minX
        val pathsHeight = maxY - minY

        if (pathsWidth <= 0f || pathsHeight <= 0f) return paths

        val scale = minOf(
            (canvasSize.width - paddingWidth) / pathsWidth,
            (canvasSize.height - paddingHeight) / pathsHeight
        )

        val scaledWidth = pathsWidth * scale
        val scaledHeight = pathsHeight * scale

        val centerX = canvasSize.width / 2f
        val centerY = canvasSize.height / 2f
        val translateX = centerX - scaledWidth / 2
        val translateY = centerY - scaledHeight / 2

        return paths.map { pathData ->
            val adjustedOffsets = pathData.path.map { offset ->
                val scaledX = (offset.x - minX) * scale + translateX
                val scaledY = (offset.y - minY) * scale + translateY
                Offset(scaledX, scaledY)
            }
            pathData.copy(path = adjustedOffsets)
        }
    }

    private fun onSelectColor(color: Color) {
        _state.update {
            it.copy(
                selectedColor = color
            )
        }
    }

    private fun onPathEnd() {
        val currentPathData = state.value.currentPath ?: return
        _state.update {
            it.copy(
                currentPath = null,
                userPath = it.userPath + currentPathData,
                score = null
            )
        }
    }

    private fun onNewPathStart() {
        _state.update {
            it.copy(
                currentPath = PathData(
                    id = System.currentTimeMillis().toString(),
                    color = it.selectedColor,
                    path = emptyList()
                ),
                score = null
            )
        }
    }

    private fun onDraw(offset: Offset) {
        val currentPathData = state.value.currentPath ?: return
        _state.update {
            it.copy(
                currentPath = currentPathData.copy(
                    path = currentPathData.path + offset
                ),
                score = null
            )
        }
    }

    private fun onClearCanvasClick() {
        _state.update {
            it.copy(
                currentPath = null,
                userPath = emptyList(),
                score = null
            )
        }
    }

    private fun drawPathsToBitmap(
        width: Int,
        height: Int,
        paths: List<PathData>,
        defaultStrokeWidth: Float = 10f,
        defaultOffset: Float = 0f
    ): Pair<Bitmap, Float> {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        // Clear canvas (set a transparent background)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Paint for paths
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = defaultStrokeWidth
            isAntiAlias = false // Disable anti-aliasing for pixel-accurate comparison
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val androidPaths = paths.map { it.path.toPath() }
        val bounds = RectF()
        for(path in androidPaths) {
            val tempBounds = RectF()
            path.computeBounds(tempBounds, true)
            bounds.union(tempBounds)
        }

        bounds.inset(
            -defaultOffset - defaultStrokeWidth / 2f,
            -defaultOffset - defaultStrokeWidth / 2f
        )

        val drawingWidth = bounds.width()
        val drawingHeight = bounds.height()

        val scaleX = width / drawingWidth
        val scaleY = height / drawingHeight
        val scale = min(scaleX, scaleY)

        val matrix = Matrix().apply {
            preTranslate(-bounds.left, -bounds.top)
            postScale(scale, scale)
        }
        androidPaths.forEach {
            it.transform(matrix)
        }

        var pathLength = 0f

        for(path in androidPaths) {
            paint.color = android.graphics.Color.BLACK
            canvas.drawPath(path, paint)

            pathLength += PathMeasure(path, false).length
        }

        return bitmap to pathLength
    }

    private suspend fun computeBitmapOverlapCoverage(
        reference: Bitmap,
        user: Bitmap,
        alphaThreshold: Int = 128 // Pixel "visibility" threshold
    ): Float = withContext(Dispatchers.Default) {
        require(reference.width == user.width && reference.height == user.height) {
            "Bitmap sizes must match"
        }

        var matchingUserPixelCount = 0
        var visibleUserPixelCount = 0

        for (row in 0 until reference.height) {
            for (col in 0 until reference.width) {
                val referencePixel = reference[col, row]
                val userPixel = user[col, row]
                val isReferencePixelVisible = isPixelVisible(referencePixel, alphaThreshold)
                val isUserPixelVisible = isPixelVisible(userPixel, alphaThreshold)

                if(isUserPixelVisible) {
                    visibleUserPixelCount++
                    if(isReferencePixelVisible) {
                        matchingUserPixelCount++
                    }
                }
            }
        }

        return@withContext if (matchingUserPixelCount == 0) 0f
        else (matchingUserPixelCount.toFloat() / visibleUserPixelCount.toFloat())
    }

    private fun isPixelVisible(pixel: Int, alphaThreshold: Int): Boolean {
        return android.graphics.Color.alpha(pixel) > alphaThreshold
    }
}

private fun List<Offset>.toPath(): Path {
    if(isEmpty()) {
        return Path()
    }

    val path = Path()
    path.moveTo(first().x, first().y)
    for(i in 1 until size) {
        val offset = get(i)
        path.lineTo(offset.x, offset.y)
    }

    return path
}