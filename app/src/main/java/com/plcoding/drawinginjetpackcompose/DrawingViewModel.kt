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
    val isDrawingsSynced: Boolean = false
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
    data object ToggleSyncDrawing : DrawingAction
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
            DrawingAction.ToggleSyncDrawing -> onToggleSyncDrawings()
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
                    topCanvasPaths = it.topCanvasPaths + currentPathData
                )
            }
        }

        if (canvasPosition == CanvasPosition.BOTTOM || state.value.isDrawingsSynced) {
            val currentPathData = state.value.bottomCurrentPath ?: return
            _state.update {
                it.copy(
                    bottomCurrentPath = null,
                    bottomCanvasPaths = it.bottomCanvasPaths + currentPathData
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
                )
            }

            canvasPosition == CanvasPosition.TOP ->
                _state.update {
                    it.copy(
                        topCurrentPath = PathData(
                            id = System.currentTimeMillis().toString(),
                            color = it.selectedColor,
                            path = emptyList()
                        )
                    )
                }

            canvasPosition == CanvasPosition.BOTTOM -> _state.update {
                it.copy(
                    bottomCurrentPath = PathData(
                        id = System.currentTimeMillis().toString(),
                        color = it.selectedColor,
                        path = emptyList()
                    )
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
                    )
                )
            }
        }

        if (position == CanvasPosition.BOTTOM || state.value.isDrawingsSynced) {
            val currentPathData = state.value.bottomCurrentPath ?: return
            _state.update {
                it.copy(
                    bottomCurrentPath = currentPathData.copy(
                        path = currentPathData.path + offset
                    )
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
                        bottomCanvasPaths = emptyList()
                    )
                }
            }

            canvasPosition == CanvasPosition.TOP ->
                _state.update {
                    it.copy(
                        topCurrentPath = null,
                        topCanvasPaths = emptyList()
                    )
                }

            canvasPosition == CanvasPosition.BOTTOM -> _state.update {
                it.copy(
                    bottomCurrentPath = null,
                    bottomCanvasPaths = emptyList()
                )
            }
        }
    }
}