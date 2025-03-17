package com.plcoding.drawinginjetpackcompose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DrawingState(
    val selectedColor: Color = Color.Black,
    val topCurrentPath: PathData? = null,
    val bottomCurrentPath: PathData? = null,
    val topCanvasPaths: List<PathData> = emptyList(),
    val bottomCanvasPaths: List<PathData> = emptyList(),
    val isDrawingsSynced: Boolean = false,
    val score: Double? = null,
)

val allColors = listOf(
    Color.Black,
    Color.Red,
    Color.Blue,
    Color.Green,
    Color.Yellow,
    Color.Magenta,
    Color.Cyan,
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
    data object OnCompareDrawingsClick : DrawingAction
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
            DrawingAction.OnCompareDrawingsClick -> {
                _state.update {
                    it.copy(
                        score = computeAccuracyScore()
                    )
                }
            }
        }
    }

    fun computeAccuracyScore(
        dMax: Double = 6.0 // forgiveness level (higher = more forgiving)
    ): Double {
        // 1) Gather all top offsets
        val topOffsets = state.value.topCanvasPaths
            .flatMap { it.path }  // Flatten all PathData.path lists into one big list

        // 2) Gather all bottom offsets
        val bottomOffsets = state.value.bottomCanvasPaths
            .flatMap { it.path }

        val topCount = topOffsets.size
        val bottomCount = bottomOffsets.size

        // If both lists have the same size, skip resampling:
        val (topProcessed, bottomProcessed) = if (topCount == bottomCount) {
            Pair(topOffsets, bottomOffsets)
        } else {
            // Otherwise, resample both to some large fixed number, e.g. 500
            val target = 500
            val newTop = resamplePath(topOffsets, target)
            val newBottom = resamplePath(bottomOffsets, target)
            Pair(newTop, newBottom)
        }

        // Now do Procrustes distance with topProcessed + bottomProcessed
        val distance = procrustesDistance(topProcessed, bottomProcessed)

        // Convert distance -> 0..100% score
        return accuracyFromDistance(distance, dMax)
    }

    private fun resamplePath(points: List<Offset>, targetCount: Int): List<Offset> {
        if (points.size <= 1 || targetCount <= 1) return points

        // Calculate the cumulative distances between consecutive points
        val distances = mutableListOf(0f)
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            distances.add(distances.last() + kotlin.math.hypot(dx, dy))
        }
        val totalLength = distances.last()

        // The desired spacing between resampled points
        val segmentLength = totalLength / (targetCount - 1)

        val resampled = mutableListOf<Offset>()
        resampled.add(points.first())

        var currentDist = segmentLength
        var idx = 1

        while (idx < points.size) {
            if (distances[idx] >= currentDist) {
                // figure out how far along the segment we should be
                val ratio = (currentDist - distances[idx - 1]) / (distances[idx] - distances[idx - 1])
                val px = points[idx - 1].x + ratio * (points[idx].x - points[idx - 1].x)
                val py = points[idx - 1].y + ratio * (points[idx].y - points[idx - 1].y)
                resampled.add(Offset(px, py))

                currentDist += segmentLength
            } else {
                idx++
            }
        }

        // Ensure we add the last point if needed
        if (resampled.size < targetCount) {
            resampled.add(points.last())
        }

        return resampled
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
}