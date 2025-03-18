package com.plcoding.drawinginjetpackcompose

import android.content.Context
import android.graphics.PathMeasure
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plcoding.drawinginjetpackcompose.ui.theme.DrawingInJetpackComposeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser

object ImagesRepo {

    private val _drawings = MutableStateFlow<Map<String, List<PathData>>>(emptyMap())
    val drawings = _drawings.asStateFlow()

    fun addDrawing(id: String, path: List<PathData>) {
        _drawings.update {
            it.toMutableMap().apply {
                put(id, path)
                toMap()
            }
        }
    }

}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        loadDrawingsFromAssets()
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
                        ReferenceCanvasScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            pathData = state.referencePath,
                            onCanvasSizeChange = {
                                topCanvasSize = it
                                viewModel.onAction(DrawingAction.OnCanvasPrepared(it))
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
                                viewModel.onAction(
                                    DrawingAction.OnCompareDrawingsClick(
                                        topCanvasSize.width,
                                        topCanvasSize.height
                                    )
                                )
                            }) {
                                Text(text = "Compare Drawings")
                            }
                        }
                        CanvasScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            state = state,
                            onAction = viewModel::onAction,
                            onCanvasSizeChange = {
                                bottomCanvasSize = it
                            }
                        )
                    }
                }
            }
        }
    }

    private fun loadDrawingsFromAssets() {
        lifecycleScope.launch {
            val drawableMap = mapOf(
                "butterfly" to R.drawable.butterfly,
                "fish" to R.drawable.fish,
                "house" to R.drawable.house,
                "rocket" to R.drawable.rocket
            )

            for ((name, drawableRes) in drawableMap) {
                val pathData = parseVectorDrawableToPathData(this@MainActivity, drawableRes)
                ImagesRepo.addDrawing(name, pathData)
            }

        }
    }

    private fun parseVectorDrawableToPathData(
        context: Context,
        drawableResId: Int
    ): List<PathData> {
        val parser = context.resources.getXml(drawableResId)
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val pathDataList = mutableListOf<PathData>()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "path") {

                val pathDataString = parser.getAttributeValue(androidNamespace, "pathData")
                requireNotNull(pathDataString) {
                    "Android Vector Drawables must have \"$androidNamespace:pathData\" in <path>"
                }

                val parsedPath = PathParser().parsePathString(pathDataString).toPath()
                val points = collectPathPoints(parsedPath.asAndroidPath())

                pathDataList.add(
                    PathData(
                        id = "path_${pathDataList.size}",
                        color = Color.Black,
                        path = points
                    )
                )
            }
            parser.next()
        }

        return pathDataList
    }

    private fun collectPathPoints(
        androidPath: android.graphics.Path,
        step: Float = 1f
    ): List<Offset> {
        val points = mutableListOf<Offset>()
        val pathMeasure = PathMeasure(androidPath, false)
        val pathLength = pathMeasure.length
        val position = FloatArray(2)

        var distance = 0f
        while (distance < pathLength) {
            if (pathMeasure.getPosTan(distance, position, null)) {
                points.add(Offset(position[0], position[1]))
            }
            distance += step
        }

        return points
    }

}

@Composable
fun ReferenceCanvasScreen(
    modifier: Modifier = Modifier,
    pathData: List<PathData>,
    onCanvasSizeChange: (IntSize) -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .onGloballyPositioned {
                    onCanvasSizeChange(it.size)
                }
                .background(Color.White)
        ) {
            pathData.fastForEach { pathData ->
                drawPath(
                    path = pathData.path,
                    color = pathData.color
                )
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
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DrawingCanvas(
            paths = state.userPath,
            currentPath = state.currentPath,
            onAction = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onSizeChanged = onCanvasSizeChange
        )
        Button(
            onClick = {
                onAction(DrawingAction.OnClearCanvasClick)
            }
        ) {
            Text("Clear Canvas")
        }
    }
}