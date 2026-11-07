package com.craftcam.app.portrait

import android.graphics.Bitmap
import kotlin.math.*

/**
 * Depth-Aware Blur Computation and Anti-Color-Bleed Pre-processor for CAMELEO.
 *
 * Responsibilities:
 * 1. Computes continuous depth-dependent blur weighting b(x, y) based on DepthMap, subject distance, and mask.
 * 2. In-paints / dilates background layer under the subject silhouette to eliminate color bleed into the bokeh.
 * 3. Enforces optical focus continuity (near background receives subtle blur; distant background receives full bokeh).
 */
class DepthAwareBlur {

    /**
     * Computes the per-pixel blur weighting map [0.0f, 1.0f]:
     * - 0.0f = Pin-sharp subject (no blur).
     * - 0.15f - 0.40f = Near-background subtle roll-off.
     * - 0.40f - 0.70f = Mid-background moderate blur.
     * - 0.70f - 1.0f = Distant background maximum bokeh.
     */
    fun computeBlurMap(
        depthArray: FloatArray,
        maskArray: FloatArray,
        width: Int,
        height: Int,
        profile: BokehProfile
    ): FloatArray {
        val total = width * height
        val blurMap = FloatArray(total)
        val subjectDepth = 0.15f // In-focus plane
        val depthSpan = (1.0f - subjectDepth).coerceAtLeast(0.01f)

        for (i in 0 until total) {
            val d = depthArray[i]
            val m = maskArray[i]

            // If confirmed subject (m >= 1.0), blur is strictly 0.0
            if (m >= 0.98f) {
                blurMap[i] = 0.0f
                continue
            }

            // Depth falloff relative to the subject focus plane
            val relDepth = ((d - subjectDepth) / depthSpan).coerceIn(0.0f, 1.0f)
            val shapedDepth = relDepth.pow(profile.depthFalloff)

            // Blur scales with distance from subject AND inverse subject confidence
            val backgroundWeight = (1.0f - m).coerceIn(0.0f, 1.0f)
            blurMap[i] = (shapedDepth * backgroundWeight).coerceIn(0.0f, 1.0f)
        }

        return blurMap
    }

    /**
     * Prepares an anti-color-bleeding background image by dilating surrounding background pixels
     * inwards over the subject silhouette. When blurred, this ensures foreground subject colors
     * (e.g. hair or brightly colored clothing) cannot bleed outward into the blurred background bokeh.
     */
    fun prepareAntiBleedBackground(
        source: Bitmap,
        maskArray: FloatArray,
        width: Int,
        height: Int,
        bleedRadius: Int = 4
    ): Bitmap {
        val outBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val total = width * height
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(total)
        System.arraycopy(pixels, 0, outPixels, 0, total)

        // For pixels where subject mask is strong (>= 0.50), sample nearest background pixel outside mask
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                val m = maskArray[idx]

                if (m > 0.50f) {
                    // Search outwards in a radial pattern for the nearest background pixel (m < 0.20)
                    var foundPixel = pixels[idx]
                    var found = false

                    for (r in 1..bleedRadius * 3) {
                        for (angle in 0 until 8) {
                            val theta = angle * (PI.toFloat() / 4f)
                            val sx = (x + r * cos(theta)).roundToInt().coerceIn(0, width - 1)
                            val sy = (y + r * sin(theta)).roundToInt().coerceIn(0, height - 1)
                            val sIdx = sy * width + sx

                            if (maskArray[sIdx] < 0.20f) {
                                foundPixel = pixels[sIdx]
                                found = true
                                break
                            }
                        }
                        if (found) break
                    }

                    outPixels[idx] = foundPixel
                }
            }
        }

        outBmp.setPixels(outPixels, 0, width, 0, 0, width, height)
        return outBmp
    }
}

