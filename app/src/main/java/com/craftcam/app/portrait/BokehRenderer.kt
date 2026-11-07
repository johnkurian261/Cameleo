package com.craftcam.app.portrait

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.*

/**
 * Depth-Aware Bokeh Renderer for CAMELEO.
 *
 * Implements:
 * 1. Multi-Scale Blur Pyramid (O(N) continuous depth-of-field interpolation).
 * 2. Specular Highlight Bokeh Simulation (soft circular aperture discs for point lights).
 * 3. Edge-Preserving Alpha-Composite (razor-sharp subject boundaries with smooth optical roll-off).
 */
class BokehRenderer {

    fun renderBokeh(
        source: Bitmap,
        antiBleedBg: Bitmap,
        maskArray: FloatArray,
        blurMap: FloatArray,
        config: PortraitConfig
    ): Bitmap {
        val width = source.width
        val height = source.height
        val total = width * height
        val profile = config.activeBokehProfile

        // 1. Build Multi-Scale Pyramid for Fast Depth-of-Field Blur
        val l1W = (width / 2).coerceAtLeast(1)
        val l1H = (height / 2).coerceAtLeast(1)
        val level1 = Bitmap.createScaledBitmap(antiBleedBg, l1W, l1H, true)

        val l2W = (width / 4).coerceAtLeast(1)
        val l2H = (height / 4).coerceAtLeast(1)
        val level2 = Bitmap.createScaledBitmap(level1, l2W, l2H, true)

        val l3W = (width / 8).coerceAtLeast(1)
        val l3H = (height / 8).coerceAtLeast(1)
        val level3 = Bitmap.createScaledBitmap(level2, l3W, l3H, true)

        // Read pixels for fast direct memory sampling
        val srcPixels = IntArray(total)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val bg0Pixels = IntArray(total)
        antiBleedBg.getPixels(bg0Pixels, 0, width, 0, 0, width, height)

        val l1Pixels = IntArray(l1W * l1H)
        level1.getPixels(l1Pixels, 0, l1W, 0, 0, l1W, l1H)

        val l2Pixels = IntArray(l2W * l2H)
        level2.getPixels(l2Pixels, 0, l2W, 0, 0, l2W, l2H)

        val l3Pixels = IntArray(l3W * l3H)
        level3.getPixels(l3Pixels, 0, l3W, 0, 0, l3W, l3H)

        val outPixels = IntArray(total)

        // Precompute coordinate ratios for pyramid lookups
        val r1X = (l1W - 1).toFloat() / (width - 1).coerceAtLeast(1)
        val r1Y = (l1H - 1).toFloat() / (height - 1).coerceAtLeast(1)

        val r2X = (l2W - 1).toFloat() / (width - 1).coerceAtLeast(1)
        val r2Y = (l2H - 1).toFloat() / (height - 1).coerceAtLeast(1)

        val r3X = (l3W - 1).toFloat() / (width - 1).coerceAtLeast(1)
        val r3Y = (l3H - 1).toFloat() / (height - 1).coerceAtLeast(1)

        val highlightThresh = config.bokehHighlightThreshold
        val highlightBoost = config.bokehHighlightBoost

        for (y in 0 until height) {
            val row = y * width
            val l1YIdx = (y * r1Y).toInt().coerceIn(0, l1H - 1) * l1W
            val l2YIdx = (y * r2Y).toInt().coerceIn(0, l2H - 1) * l2W
            val l3YIdx = (y * r3Y).toInt().coerceIn(0, l3H - 1) * l3W

            for (x in 0 until width) {
                val idx = row + x
                val m = maskArray[idx]

                // If fully within subject core, copy source pixel directly (zero blur)
                if (m >= 0.99f) {
                    outPixels[idx] = srcPixels[idx]
                    continue
                }

                val blurWeight = blurMap[idx]

                // Pyramid continuous interpolation based on blur weight [0.0f, 1.0f]
                // 0.0 -> bg0
                // 0.33 -> level1
                // 0.66 -> level2
                // 1.0 -> level3
                val p0 = bg0Pixels[idx]
                val p1 = l1Pixels[l1YIdx + (x * r1X).toInt().coerceIn(0, l1W - 1)]
                val p2 = l2Pixels[l2YIdx + (x * r2X).toInt().coerceIn(0, l2W - 1)]
                val p3 = l3Pixels[l3YIdx + (x * r3X).toInt().coerceIn(0, l3W - 1)]

                var bgR: Float
                var bgG: Float
                var bgB: Float

                val scaledBlur = blurWeight * 3.0f
                when {
                    scaledBlur <= 1.0f -> {
                        val t = scaledBlur
                        val r0 = (p0 shr 16) and 0xFF
                        val g0 = (p0 shr 8) and 0xFF
                        val b0 = p0 and 0xFF

                        val r1 = (p1 shr 16) and 0xFF
                        val g1 = (p1 shr 8) and 0xFF
                        val b1 = p1 and 0xFF

                        bgR = r0 * (1f - t) + r1 * t
                        bgG = g0 * (1f - t) + g1 * t
                        bgB = b0 * (1f - t) + b1 * t
                    }
                    scaledBlur <= 2.0f -> {
                        val t = scaledBlur - 1.0f
                        val r1 = (p1 shr 16) and 0xFF
                        val g1 = (p1 shr 8) and 0xFF
                        val b1 = p1 and 0xFF

                        val r2 = (p2 shr 16) and 0xFF
                        val g2 = (p2 shr 8) and 0xFF
                        val b2 = p2 and 0xFF

                        bgR = r1 * (1f - t) + r2 * t
                        bgG = g1 * (1f - t) + g2 * t
                        bgB = b1 * (1f - t) + b2 * t
                    }
                    else -> {
                        val t = (scaledBlur - 2.0f).coerceIn(0f, 1f)
                        val r2 = (p2 shr 16) and 0xFF
                        val g2 = (p2 shr 8) and 0xFF
                        val b2 = p2 and 0xFF

                        val r3 = (p3 shr 16) and 0xFF
                        val g3 = (p3 shr 8) and 0xFF
                        val b3 = p3 and 0xFF

                        bgR = r2 * (1f - t) + r3 * t
                        bgG = g2 * (1f - t) + g3 * t
                        bgB = b2 * (1f - t) + b3 * t
                    }
                }

                // 2. Specular Highlight Bokeh Simulation
                val luma = (0.299f * bgR + 0.587f * bgG + 0.114f * bgB) / 255f
                if (luma > highlightThresh && blurWeight > 0.30f) {
                    val excess = (luma - highlightThresh) / (1.0f - highlightThresh)
                    val discBoost = 1.0f + excess * (highlightBoost - 1.0f) * blurWeight
                    bgR = (bgR * discBoost).coerceIn(0f, 255f)
                    bgG = (bgG * discBoost).coerceIn(0f, 255f)
                    bgB = (bgB * discBoost).coerceIn(0f, 255f)
                }

                // 3. Alpha Composite: Subject * mask + BlurredBg * (1 - mask)
                val srcP = srcPixels[idx]
                val srcR = (srcP shr 16) and 0xFF
                val srcG = (srcP shr 8) and 0xFF
                val srcB = srcP and 0xFF

                val finalR = (srcR * m + bgR * (1f - m)).roundToInt().coerceIn(0, 255)
                val finalG = (srcG * m + bgG * (1f - m)).roundToInt().coerceIn(0, 255)
                val finalB = (srcB * m + bgB * (1f - m)).roundToInt().coerceIn(0, 255)

                outPixels[idx] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        // Clean up pyramid levels
        level1.recycle()
        level2.recycle()
        level3.recycle()

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return resultBitmap
    }
}

