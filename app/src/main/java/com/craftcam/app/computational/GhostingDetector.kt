package com.craftcam.app.computational

import kotlin.math.max
import kotlin.math.min

/**
 * Encapsulates static vs motion confidence across the burst sequence.
 */
class GhostingMask(
    val staticConfidence: FloatArray,
    val width: Int,
    val height: Int,
    val maxMotionFraction: Float
) {
    /**
     * Samples static confidence [0.0f = full motion, 1.0f = rock static]
     * for a full-resolution pixel coordinate (x, y).
     */
    fun getStaticWeight(fullX: Int, fullY: Int, fullW: Int, fullH: Int): Float {
        if (width <= 0 || height <= 0 || staticConfidence.isEmpty()) return 1.0f

        val sx = ((fullX.toFloat() / fullW) * (width - 1)).toInt().coerceIn(0, width - 1)
        val sy = ((fullY.toFloat() / fullH) * (height - 1)).toInt().coerceIn(0, height - 1)
        return staticConfidence[sy * width + sx]
    }
}

object GhostingDetector {

    /**
     * Aggregates motion maps from all alternate frames into a master [GhostingMask].
     * If any frame exhibits motion at pixel (x, y), static confidence is reduced.
     */
    fun createGhostingMask(
        motionMaps: List<MotionMap>,
        width: Int,
        height: Int
    ): GhostingMask {
        val total = width * height
        if (motionMaps.isEmpty() || total == 0) {
            return GhostingMask(FloatArray(total) { 1.0f }, width, height, 0.0f)
        }

        val staticConfidence = FloatArray(total) { 1.0f }
        var maxGlobalMotion = 0.0f

        for (map in motionMaps) {
            if (map.globalMotionFraction > maxGlobalMotion) {
                maxGlobalMotion = map.globalMotionFraction
            }
            if (map.width == width && map.height == height) {
                for (i in 0 until total) {
                    val motion = map.map[i]
                    val static = (1.0f - motion).coerceIn(0.0f, 1.0f)
                    if (static < staticConfidence[i]) {
                        staticConfidence[i] = static
                    }
                }
            }
        }

        return GhostingMask(
            staticConfidence = staticConfidence,
            width = width,
            height = height,
            maxMotionFraction = maxGlobalMotion
        )
    }
}

