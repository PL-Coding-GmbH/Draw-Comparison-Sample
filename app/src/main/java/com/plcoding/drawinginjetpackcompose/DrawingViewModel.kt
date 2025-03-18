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
import androidx.compose.ui.util.fastRoundToInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

data class DrawingState(
    val selectedColor: Color = Color.Black,
    val topCurrentPath: PathData? = null,
    val bottomCurrentPath: PathData? = null,
    val topCanvasPaths: List<PathData> = emptyList(),
    val bottomCanvasPaths: List<PathData> = emptyList(),
    val isDrawingsSynced: Boolean = false,
    val score: Int? = null,
)

data class PathData(
    val id: String,
    val color: Color,
    val path: List<Offset>
)

enum class CanvasPosition {
    TOP, BOTTOM
}

sealed interface DrawingAction {
    data class OnNewPathStart(val canvasPosition: CanvasPosition) : DrawingAction
    data class OnDraw(val offset: Offset, val canvasPosition: CanvasPosition) : DrawingAction
    data class OnPathEnd(val canvasPosition: CanvasPosition) : DrawingAction
    data class OnSelectColor(val color: Color) : DrawingAction
    data class OnClearCanvasClick(val canvasPosition: CanvasPosition) : DrawingAction
    data object OnToggleSyncDrawingClick : DrawingAction
    data class OnCompareDrawingsClick(val canvasWidth: Int, val canvasHeight: Int) : DrawingAction
}

class DrawingViewModel(
    private val application: Application
) : ViewModel() {

    private val _state = MutableStateFlow(DrawingState())
    val state = _state.asStateFlow()

    fun onAction(action: DrawingAction) {
        when (action) {
            is DrawingAction.OnClearCanvasClick -> onClearCanvasClick(action.canvasPosition)
            is DrawingAction.OnDraw -> onDraw(action.offset, action.canvasPosition)
            is DrawingAction.OnNewPathStart -> onNewPathStart(action.canvasPosition)
            is DrawingAction.OnPathEnd -> onPathEnd(action.canvasPosition)
            is DrawingAction.OnSelectColor -> onSelectColor(action.color)
            DrawingAction.OnToggleSyncDrawingClick -> onToggleSyncDrawings()
            is DrawingAction.OnCompareDrawingsClick -> compareDrawings(action.canvasWidth, action.canvasHeight)
        }
    }

    private fun compareDrawings(canvasWidth: Int, canvasHeight: Int) = viewModelScope.launch {
        val userStrokeWidth = 10f
        val referenceStrokeWidth = 100f
        val defaultOffset = (referenceStrokeWidth - userStrokeWidth) / 2f

        val userPaths = _state.value.topCanvasPaths
        val (user, userPathLength) = drawPathsToBitmap(
            width = canvasWidth,
            height = canvasHeight,
            paths = userPaths,
            defaultStrokeWidth = userStrokeWidth,
            defaultOffset = defaultOffset
        )
        val referencePaths = _state.value.bottomCanvasPaths
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

    private fun onToggleSyncDrawings() {
        _state.update {
            it.copy(
                isDrawingsSynced = !it.isDrawingsSynced
            )
        }
    }

    private fun onSelectColor(color: Color) {
        _state.update {
            it.copy(
                selectedColor = color
            )
        }
    }

    private fun onPathEnd(canvasPosition: CanvasPosition) {
        if (canvasPosition == CanvasPosition.TOP || state.value.isDrawingsSynced) {
            val currentPathData = state.value.topCurrentPath ?: return
            _state.update {
                it.copy(
                    topCurrentPath = null,
                    topCanvasPaths = it.topCanvasPaths + currentPathData,
                    score = null
                )
            }
        }

        if (canvasPosition == CanvasPosition.BOTTOM || state.value.isDrawingsSynced) {
            val currentPathData = state.value.bottomCurrentPath ?: return
            _state.update {
                it.copy(
                    bottomCurrentPath = null,
                    bottomCanvasPaths = it.bottomCanvasPaths + currentPathData,
                    score = null
                )
            }
        }
    }

    private fun onNewPathStart(canvasPosition: CanvasPosition) {
        when {
            state.value.isDrawingsSynced -> _state.update {
                it.copy(
                    topCurrentPath = PathData(
                        id = System.currentTimeMillis().toString(),
                        color = it.selectedColor,
                        path = emptyList()
                    ),
                    bottomCurrentPath = PathData(
                        id = System.currentTimeMillis().toString(),
                        color = it.selectedColor,
                        path = emptyList()
                    ),
                    score = null
                )
            }

            canvasPosition == CanvasPosition.TOP ->
                _state.update {
                    it.copy(
                        topCurrentPath = PathData(
                            id = System.currentTimeMillis().toString(),
                            color = it.selectedColor,
                            path = emptyList()
                        ),
                        score = null
                    )
                }

            canvasPosition == CanvasPosition.BOTTOM -> _state.update {
                it.copy(
                    bottomCurrentPath = PathData(
                        id = System.currentTimeMillis().toString(),
                        color = it.selectedColor,
                        path = emptyList()
                    ),
                    score = null
                )
            }
        }
    }

    private fun onDraw(offset: Offset, position: CanvasPosition) {
        if (position == CanvasPosition.TOP || state.value.isDrawingsSynced) {
            val currentPathData = state.value.topCurrentPath ?: return
            _state.update {
                it.copy(
                    topCurrentPath = currentPathData.copy(
                        path = currentPathData.path + offset
                    ),
                    score = null
                )
            }
        }

        if (position == CanvasPosition.BOTTOM || state.value.isDrawingsSynced) {
            val currentPathData = state.value.bottomCurrentPath ?: return
            _state.update {
                it.copy(
                    bottomCurrentPath = currentPathData.copy(
                        path = currentPathData.path + offset
                    ),
                    score = null
                )
            }
        }
    }

    private fun onClearCanvasClick(canvasPosition: CanvasPosition) {
        when {
            state.value.isDrawingsSynced -> {
                _state.update {
                    it.copy(
                        topCurrentPath = null,
                        topCanvasPaths = emptyList(),
                        bottomCurrentPath = null,
                        bottomCanvasPaths = emptyList(),
                        score = null
                    )
                }
            }

            canvasPosition == CanvasPosition.TOP ->
                _state.update {
                    it.copy(
                        topCurrentPath = null,
                        topCanvasPaths = emptyList(),
                        score = null
                    )
                }

            canvasPosition == CanvasPosition.BOTTOM -> _state.update {
                it.copy(
                    bottomCurrentPath = null,
                    bottomCanvasPaths = emptyList(),
                    score = null
                )
            }
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