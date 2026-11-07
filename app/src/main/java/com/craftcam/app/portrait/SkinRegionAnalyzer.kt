package com.craftcam.app.portrait

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Soft Skin-Tone and Human Anatomy Segmenter.
 * Combines spatial proximity to detected facial regions with normalized chromaticity bounds
 * to prevent background objects (walls, wood, clothing) from being falsely detected as human skin.
 */
object SkinRegionAnalyzer {

    /**
     * Generates a soft skin confidence map [0.0f, 1.0f] on downsampled pixels.
     */
    fun generateSkinMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        faceRegions: List<FaceRegion>
    ): FloatArray {
        val total = width * height
        val mask = FloatArray(total)

        if (total == 0 || pixels.isEmpty()) return mask

        // Convert normalized face rects to downsampled pixel bounds and expand for neck/shoulders
        val expandedFaces = faceRegions.map { f ->
            val r = f.toPixelRect(width, height)
            val expandX = r.width() * 0.40f
            val expandTop = r.height() * 0.20f
            val expandBottom = r.height() * 0.90f // Expand downward for neck and collarbone
            RectF(
                (r.left - expandX).coerceAtLeast(0f),
                (r.top - expandTop).coerceAtLeast(0f),
                (r.right + expandX).coerceAtMost(width.toFloat()),
                (r.bottom + expandBottom).coerceAtMost(height.toFloat())
            )
        }

        for (y in 0 until height) {
            val row = y * width
            val yF = y.toFloat()

            for (x in 0 until width) {
                val idx = row + x
                val p = pixels[idx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                // 1. Check normalized chromaticity
                val sum = (r + g + b).toFloat()
                if (sum < 50f || r <= g || g <= b) {
                    mask[idx] = 0.0f
                    continue
                }

                val rNorm = r / sum
                val gNorm = g / sum
                val isChromaticSkin = (rNorm in 0.36f..0.58f) && (gNorm in 0.26f..0.38f) && ((r - g) in 10..90)

                if (!isChromaticSkin) {
                    mask[idx] = 0.0f
                    continue
                }

                // 2. Spatial proximity weighting based on detected face locations
                val xF = x.toFloat()
                var spatialWeight = 0.25f // Base ambient skin probability

                if (expandedFaces.isNotEmpty()) {
                    var inAnyFace = false
                    for (fRect in expandedFaces) {
                        if (fRect.contains(xF, yF)) {
                            inAnyFace = true
                            break
                        }
                    }
                    spatialWeight = if (inAnyFace) 1.0f else 0.15f
                }

                // Compute final soft confidence
                val diffFactor = ((r - g) / 45.0f).coerceIn(0.5f, 1.0f)
                mask[idx] = (spatialWeight * diffFactor).coerceIn(0.0f, 1.0f)
            }
        }

        return mask
    }
}
