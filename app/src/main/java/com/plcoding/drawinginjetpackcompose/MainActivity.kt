package com.plcoding.drawinginjetpackcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plcoding.drawinginjetpackcompose.ui.theme.DrawingInJetpackComposeTheme
import java.time.ZonedDateTime
import kotlin.random.Random

val RainbowPenBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFB02FB), // Magenta
        Color(0xFF0000FF), // Blue
        Color(0xFF00EEFF), // Cyan
        Color(0xFF008000), // Green
        Color(0xFFFFFF00), // Yellow
        Color(0xFFFFA500), // Orange
        Color(0xFFFF0000), // Red
    )
)

class MainActivity : ComponentActivity() {

    val drawableAssetLoader by lazy {
        DrawableAssetLoader(
            scope = lifecycleScope,
            context = this
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        drawableAssetLoader.loadToPathData()
        setContent {
            var showNum5 by remember {
                mutableStateOf(true)
            }
            DrawingInJetpackComposeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Row(modifier = Modifier
                        .padding(innerPadding)
                        .background(Color.Yellow)) {
                        LazyColumn(
                            modifier = Modifier
                                .padding(20.dp)
                                .applyIf(true) {
                                    padding(20.dp)
                                }
                                .onGloballyPositioned {
                                    println("deadpool CORRECT ${it.size}")
                                }
                        ) {
                            (0..1000).forEach {
                                item {
                                    Box(modifier = Modifier
//                                    .alpha(if (it == 5 && !showNum5) 0f else 1f)
                                        .size(if (it == 5 && !showNum5) 50.dp else 100.dp)
                                        .background(
                                            Color(
                                                red = Random.nextFloat(),
                                                green = Random.nextFloat(),
                                                blue = Random.nextFloat(),
                                                alpha = 1f
                                            )
                                        )
                                        .onLayoutRectChanged(

                                        ) { relativeLayoutRec ->
                                            if (it == 0) {
                                                println("Called onLayoutRectChanged #$it with ${relativeLayoutRec.width} x ${relativeLayoutRec.height}")
                                            }
                                        }
                                        .onGloballyPositioned { layoutCoordinates ->
                                            if (it == 0) {
                                                println("Called onGloballyPositioned #$it with ${layoutCoordinates.size}")
                                            }
                                        }
                                        .fillMaxWidth()
                                        .height(100.dp)
                                    ) {
                                        Text("Noice #$it", Modifier
                                            .padding(5.dp)
                                            .clickable {
                                                showNum5 = !showNum5
                                            })
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .padding(20.dp)
                                .applyIfWrong(true) {
                                    padding(20.dp)
                                }
                                .onGloballyPositioned {
                                    println("deadpool WRONG ${it.size}")
                                }
                        ) {
                            (0..1000).forEach {
                                item {
                                    Box(modifier = Modifier
//                                    .alpha(if (it == 5 && !showNum5) 0f else 1f)
                                        .size(if (it == 5 && !showNum5) 50.dp else 100.dp)
                                        .background(
                                            Color(
                                                red = Random.nextFloat(),
                                                green = Random.nextFloat(),
                                                blue = Random.nextFloat(),
                                                alpha = 1f
                                            )
                                        )
                                        .onLayoutRectChanged(

                                        ) { relativeLayoutRec ->
                                            if (it == 0) {
                                                println("Called onLayoutRectChanged #$it with ${relativeLayoutRec.width} x ${relativeLayoutRec.height}")
                                            }
                                        }
                                        .onGloballyPositioned { layoutCoordinates ->
                                            if (it == 0) {
                                                println("Called onGloballyPositioned #$it with ${layoutCoordinates.size}")
                                            }
                                        }
                                        .fillMaxWidth()
                                        .height(100.dp)
                                    ) {
                                        Text("Noice #$it", Modifier
                                            .padding(5.dp)
                                            .clickable {
                                                showNum5 = !showNum5
                                            })
                                    }
                                }
                            }
                        }
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
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DrawingCanvas(
            paths = if (position == CanvasPosition.TOP) state.topCanvasPaths else state.bottomCanvasPaths,
            currentPath = if (position == CanvasPosition.TOP) state.topCurrentPath else state.bottomCurrentPath,
            onAction = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
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

fun Modifier.applyIf(
    condition: Boolean,
    modifier: Modifier.() -> Modifier
): Modifier {
    return if (condition) {
        then(Modifier.modifier()) // <-- Change made here
    } else this
}
fun Modifier.applyIfWrong(
    condition: Boolean,
    modifier: Modifier.() -> Modifier
): Modifier {
    return if (condition) {
        then(modifier()) // <-- Change made here
    } else this
}