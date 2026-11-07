package com.craftcam.app.color

import android.graphics.Color
import com.craftcam.app.portrait.FaceRegion
import kotlin.math.*

/**
 * Photographic Skin Tone Protection & Red-Channel Desaturation Guard for CAMELEO.
 *
 * Responsibilities:
 * 1. Soft Multi-Complexion Skin Tone Masking (Fitzpatrick Types I through VI).
 * 2. Prevents oversaturation, sunburned orange/magenta faces, and unnatural hue skews.
 * 3. Red Channel Clipping Guard: prevents hot specular red blowout in skin, sunsets, and flowers.
 * 4. Integrates face spatial boundaries to reinforce skin tone fidelity.
 *
 * Strictly PROHIBITED: Face beautification, skin whitening, plastic smoothing, and artificial blurring.
 */
object SkinToneProcessor {

    private const val TARGET_PEACH_HUE = 26.0f // Natural warm peach/skin tone center in degrees
    private const val RED_CLIP_THRESHOLD = 0.86f // Hot red threshold (linear scale)

    /**
     * Applies skin tone stabilization and red clipping protection in-place on HSV/RGB components.
     *
     * @param r Red component [0.0f, 1.0f].
     * @param g Green component [0.0f, 1.0f].
     * @param b Blue component [0.0f, 1.0f].
     * @param strength Global skin protection strength [0.0f, 1.0f].
     * @param isInsideFaceZone Spatial priority from face tracking.
     * @return Processed [r, g, b] float array in [0.0f, 1.0f].
     */
    fun processPixel(
        r: Float,
        g: Float,
        b: Float,
        strength: Float = 0.85f,
        isInsideFaceZone: Boolean = false
    ): FloatArray {
        val hsv = FloatArray(3)
        Color.colorToHSV(
            (0xFF shl 24) or
            ((r * 255f).roundToInt().coerceIn(0, 255) shl 16) or
            ((g * 255f).roundToInt().coerceIn(0, 255) shl 8) or
            (b * 255f).roundToInt().coerceIn(0, 255),
            hsv
        )

        var hue = hsv[0]
        var sat = hsv[1]
        var valL = hsv[2]

        // 1. Soft Skin Tone Classification across Fitzpatrick Types I to VI
        val hueDist = abs(hue - TARGET_PEACH_HUE).let { if (it > 180f) 360f - it else it }
        val isSkinHue = hueDist < 26.0f // Covers fair peach, olive, warm tan, and deep brown complexions
        val isSkinSat = sat in 0.10f..0.72f
        val isSkinVal = valL in 0.15f..0.92f

        var skinWeight = 0.0f
        if (isSkinHue && isSkinSat && isSkinVal) {
            val hueFactor = cos((hueDist / 26.0f) * (PI.toFloat() * 0.5f))
            val satFactor = smoothstep(0.10f, 0.22f, sat) * (1.0f - smoothstep(0.58f, 0.72f, sat))
            val valFactor = smoothstep(0.15f, 0.25f, valL) * (1.0f - smoothstep(0.82f, 0.92f, valL))
            skinWeight = (hueFactor * satFactor * valFactor).coerceIn(0f, 1f)
        }

        if (isInsideFaceZone) {
            // Reinforce skin protection inside confirmed face bounding box
            skinWeight = max(skinWeight, 0.65f)
        }

        skinWeight *= strength

        // 2. Skin Saturation & Hue Calibration
        if (skinWeight > 0.05f) {
            // Clamp excessive saturation to prevent sunburned or artificial look
            val maxAllowedSat = 0.52f
            if (sat > maxAllowedSat) {
                sat = sat * (1f - skinWeight) + min(sat, maxAllowedSat) * skinWeight
            }

            // Steer reddish skin (< 16 deg) gently toward warm peach (26 deg)
            if (hue < 16.0f) {
                hue = hue * (1f - skinWeight * 0.4f) + TARGET_PEACH_HUE * (skinWeight * 0.4f)
            }
        }

        // 3. Red Channel Clipping Guard (protects skin, red flowers, neon lights from saturation blowout)
        if (r > RED_CLIP_THRESHOLD && sat > 0.40f) {
            val excessRed = (r - RED_CLIP_THRESHOLD) / (1.0f - RED_CLIP_THRESHOLD)
            val redDampen = 1.0f - excessRed * 0.35f
            sat = (sat * redDampen).coerceIn(0f, 1f)
        }

        hsv[0] = hue
        hsv[1] = sat.coerceIn(0f, 1f)
        hsv[2] = valL.coerceIn(0f, 1f)

        val outColor = Color.HSVToColor(hsv)
        val outR = ((outColor shr 16) and 0xFF) / 255.0f
        val outG = ((outColor shr 8) and 0xFF) / 255.0f
        val outB = (outColor and 0xFF) / 255.0f

        return floatArrayOf(outR, outG, outB)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
        return t * t * (3.0f - 2.0f * t)
    }
}

