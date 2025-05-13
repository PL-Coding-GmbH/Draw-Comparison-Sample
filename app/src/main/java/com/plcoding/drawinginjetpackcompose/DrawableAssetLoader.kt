package com.plcoding.drawinginjetpackcompose

import android.content.Context
import android.graphics.PathMeasure
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser

class DrawableAssetLoader(
    private val scope: CoroutineScope,
    private val context: Context,
    private val drawingsRepo: DrawingsRepo = DrawingsRepo
) {
    fun loadToPathData() {
        scope.launch(Dispatchers.Default) {
            val drawableMap = mapOf(
                "butterfly" to R.drawable.butterfly,
                "fish" to R.drawable.fish,
                "house" to R.drawable.house,
                "rocket" to R.drawable.rocket
            )

            for ((name, drawableRes) in drawableMap) {
                val pathData = parseVectorDrawableToPathData(context, drawableRes)
                drawingsRepo.addDrawing(name, pathData)
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
                // Not strictly needed, but for demonstration purposes here is how
                // you can extract all the points of the parsed path
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