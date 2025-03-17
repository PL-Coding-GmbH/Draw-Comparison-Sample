package com.plcoding.drawinginjetpackcompose

import androidx.compose.ui.geometry.Offset
import kotlin.math.*  // for sqrt, pow, atan2, cos, sin

// We'll reuse the existing androidx.compose.ui.geometry.Offset,
// but you can define your own if you prefer.


fun centroid(points: List<Offset>): Offset {
    val sumX = points.sumOf { it.x.toDouble() }
    val sumY = points.sumOf { it.y.toDouble() }
    return Offset((sumX / points.size).toFloat(), (sumY / points.size).toFloat())
}

fun scaleFactor(points: List<Offset>): Double {
    val c = centroid(points)
    val sumSquares = points.sumOf { (it.x - c.x).pow(2) + (it.y - c.y).pow(2).toDouble() }
    return sqrt(sumSquares / points.size)
}

fun normalize(points: List<Offset>): List<Offset> {
    val c = centroid(points)
    val translated = points.map { Offset(it.x - c.x, it.y - c.y) }
    val factor = scaleFactor(points)
    return if (factor < 1e-9) translated else translated.map { Offset(it.x / factor.toFloat(), it.y / factor.toFloat()) }
}

fun optimalRotationAngle(ref: List<Offset>, target: List<Offset>): Double {
    // Both lists are assumed to be normalized & same size
    var numerator = 0.0
    var denominator = 0.0
    for (i in ref.indices) {
        val rx = ref[i].x; val ry = ref[i].y
        val tx = target[i].x; val ty = target[i].y
        numerator   += rx * ty - ry * tx
        denominator += rx * tx + ry * ty
    }
    return atan2(numerator, denominator)
}

fun rotate(points: List<Offset>, angle: Double): List<Offset> {
    val cosA = cos(angle)
    val sinA = sin(angle)
    return points.map { p ->
        Offset(
            (p.x * cosA - p.y * sinA).toFloat(),
            (p.x * sinA + p.y * cosA).toFloat()
        )
    }
}

/**
 * Compute the Procrustes distance (RMSE) between two lists of 2D points,
 * ignoring translation, scale, and rotation.
 */
fun procrustesDistance(a: List<Offset>, b: List<Offset>): Double {
    // Quick check to avoid dividing by zero
    if (a.isEmpty() || b.isEmpty()) return Double.POSITIVE_INFINITY
    // (Optional) If lists differ in size, you might "resample" them to a common length here.
    
    val normA = normalize(a)
    val normB = normalize(b)
    val angle = optimalRotationAngle(normA, normB)
    val rotB  = rotate(normB, angle)
    val sumSquares = normA.indices.sumOf { i ->
        (normA[i].x - rotB[i].x).pow(2) + (normA[i].y - rotB[i].y).pow(2).toDouble()
    }
    return sqrt(sumSquares / normA.size)
}

/**
 * Convert Procrustes distance into a 0–100% similarity score.
 *  - distance 0 => 100%
 *  - distance >= dMax => 0%
 */
fun accuracyFromDistance(distance: Double, dMax: Double = 1.0): Double {
    return ((1 - (distance / dMax)) * 100).coerceIn(0.0, 100.0)
}