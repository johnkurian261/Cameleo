package com.craftcam.app.computational

import android.graphics.Bitmap
import com.craftcam.app.processor.IntArrayPool
import kotlin.math.*

/**
 * Conservative Photographic Tone Mapper for CAMELEO.
 * Avoids the surreal, hyper-saturated "HDR filter" look by applying an asymptotic
 * highlight compression curve and a rational toe curve in linear radiometric space:
 * - Highlights undergo continuous, gentle shoulder roll-off toward 1.0.
 * - Deep shadows (true blacks) remain anchored at 0.0 to preserve rich contrast.
 * - Channel-proportional luminance scaling maintains chromaticity and prevents hue shifting.
 */
object ToneMapper {

    fun applyToneMapping(
        source: Bitmap,
        highlightKnee: Float = 0.68f,
        shadowLift: Float = 0.22f,
        shoulderSoftness: Float = 0.35f
    ): Bitmap {
        val width = source.width
        val height = source.height
        val total = width * height

        val pixels = IntArrayPool.acquire(total)

        try {
            source.getPixels(pixels, 0, width, 0, 0, width, height)

            val srgbToLin = HDRProcessor.SRGB_TO_LINEAR
            val knee = highlightKnee.coerceIn(0.50f, 0.85f)
            val softness = shoulderSoftness.coerceIn(0.15f, 0.60f)
            val lift = shadowLift.coerceIn(0.0f, 0.50f)

            for (i in 0 until total) {
                val p = pixels[i]
                val ir = (p shr 16) and 0xFF
                val ig = (p shr 8) and 0xFF
                val ib = p and 0xFF

                val rLin = srgbToLin[ir]
                val gLin = srgbToLin[ig]
                val bLin = srgbToLin[ib]

                // Linear Rec. 709 Luminance
                val yLin = 0.2126f * rLin + 0.7152f * gLin + 0.0722f * bLin

                if (yLin <= 1e-5f) continue

                var yMapped = yLin

                // 1. Rational Toe Shadow Lift (Anchors 0.0 to strictly 0.0)
                if (lift > 0.01f && yMapped < 0.60f) {
                    val oneMinusY = (1.0f - yMapped).coerceAtLeast(0.0f)
                    val toeFactor = (oneMinusY * oneMinusY) / (1.0f + 4.0f * yMapped)
                    yMapped = yMapped * (1.0f + lift * toeFactor)
                }

                // 2. Asymptotic Highlight Shoulder Roll-off
                if (yMapped > knee) {
                    val delta = yMapped - knee
                    yMapped = knee + (1.0f - knee) * (delta / (delta + softness))
                }

                // 3. Proportional Chrominance Scaling (Preserves hue & natural saturation)
                val gain = (yMapped / yLin).coerceIn(0.2f, 2.5f)
                val rOutLin = (rLin * gain).coerceIn(0.0f, 1.0f)
                val gOutLin = (gLin * gain).coerceIn(0.0f, 1.0f)
                val bOutLin = (bLin * gain).coerceIn(0.0f, 1.0f)

                val rOut = HDRProcessor.linearToSrgb(rOutLin)
                val gOut = HDRProcessor.linearToSrgb(gOutLin)
                val bOut = HDRProcessor.linearToSrgb(bOutLin)

                pixels[i] = (0xFF shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
            }

            source.setPixels(pixels, 0, width, 0, 0, width, height)
            return source
        } finally {
            IntArrayPool.release(pixels)
        }
    }
}

