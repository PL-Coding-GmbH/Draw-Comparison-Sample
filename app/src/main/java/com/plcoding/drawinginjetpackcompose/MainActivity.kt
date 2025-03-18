package com.plcoding.drawinginjetpackcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plcoding.drawinginjetpackcompose.ui.theme.DrawingInJetpackComposeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DrawingInJetpackComposeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel = viewModel<DrawingViewModel> {
                        DrawingViewModel(application)
                    }
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    var topCanvasSize by remember { mutableStateOf(IntSize.Zero) }
                    var bottomCanvasSize by remember { mutableStateOf(IntSize.Zero) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        CanvasScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            state = state,
                            onAction = viewModel::onAction,
                            position = CanvasPosition.TOP,
                            onCanvasSizeChange = {
                                topCanvasSize = it
                            }
                        )
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .animateContentSize()
                                .fillMaxWidth()
                        ) {
                            state.score?.let {
                                Text("$it%")
                            }
                            Button(onClick = {
                                viewModel.onAction(DrawingAction.OnCompareDrawingsClick(topCanvasSize.width, topCanvasSize.height))
                            }) {
                                Text(text = "Compare Drawings")
                            }
                            Button(onClick = {
                                viewModel.onAction(DrawingAction.OnToggleSyncDrawingClick)
                            }) {
                                Text(text = "Sync Drawings")
                            }
                            if(state.isDrawingsSynced) {
                                Box(
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                            }
                        }
                        CanvasScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            state = state,
                            onAction = viewModel::onAction,
                            position = CanvasPosition.BOTTOM,
                            onCanvasSizeChange = {
                                bottomCanvasSize = it
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CanvasScreen(
    modifier: Modifier = Modifier,
    state: DrawingState,
    onAction: (DrawingAction) -> Unit,
    onCanvasSizeChange: (IntSize) -> Unit,
    position: CanvasPosition
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DrawingCanvas(
            paths = if (position == CanvasPosition.TOP) state.topCanvasPaths else state.bottomCanvasPaths,
            currentPath = if (position == CanvasPosition.TOP) state.topCurrentPath else state.bottomCurrentPath,
            onAction = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            position = position,
            onSizeChanged = onCanvasSizeChange
        )
        Button(
            onClick = {
                onAction(DrawingAction.OnClearCanvasClick(position))
            }
        ) {
            Text("Clear Canvas")
        }
    }
}