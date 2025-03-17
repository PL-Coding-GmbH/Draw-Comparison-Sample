package com.plcoding.drawinginjetpackcompose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.util.fastRoundToInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

class DrawingViewModel : ViewModel() {

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
        val coverage = computeBitmapOverlapCoverage(
            reference = drawPathsToBitmap(
                width = canvasWidth,
                height = canvasHeight,
                paths = _state.value.topCanvasPaths,
                defaultStrokeWidth = 10f
            ),
            user = drawPathsToBitmap(
                width = canvasWidth,
                height = canvasHeight,
                paths = _state.value.bottomCanvasPaths,
                defaultStrokeWidth = 50f
            )
        )

        _state.update {
            it.copy(
                score = (coverage * 100).fastRoundToInt()
            )
        }
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
        defaultStrokeWidth: Float = 10f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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

        // Draw each path to the canvas
        for (pathData in paths) {
            paint.color = pathData.color.toArgb() // Use the color from PathData
            val androidPath = androidx.compose.ui.graphics.Path().apply {
                val offsets = pathData.path
                if (offsets.isNotEmpty()) {
                    moveTo(offsets.first().x, offsets.first().y)
                    for (i in 1 until offsets.size) {
                        lineTo(offsets[i].x, offsets[i].y)
                    }
                }
            }.asAndroidPath()

            canvas.drawPath(androidPath, paint)
        }

        return bitmap
    }

    private suspend fun computeBitmapOverlapCoverage(
        reference: Bitmap,
        user: Bitmap,
        alphaThreshold: Int = 128 // Pixel "visibility" threshold
    ): Float = withContext(Dispatchers.IO){
        require(reference.width == user.width && reference.height == user.height) {
            "Bitmap sizes must match"
        }

        var refPixelCount = 0
        var overlapCount = 0

        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val refPixel = reference.getPixel(x, y)
                val refAlpha = android.graphics.Color.alpha(refPixel)
                if (refAlpha > alphaThreshold) {
                    refPixelCount++

                    // Check for overlap
                    val userPixel = user.getPixel(x, y)
                    val userAlpha = android.graphics.Color.alpha(userPixel)

                    if (userAlpha > alphaThreshold) {
                        overlapCount++
                    } else println("NOT at ($x, $y)")
                }
            }
        }

        return@withContext if (refPixelCount == 0) 0f else (overlapCount.toFloat() / refPixelCount.toFloat())
    }
}