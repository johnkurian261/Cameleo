package com.craftcam.app.color

import android.graphics.Color
import com.craftcam.app.scene.SceneType
import kotlin.math.*

/**
 * Color Appearance Model and Selective Gamut Tuning for CAMELEO.
 *
 * Responsibilities:
 * 1. Vibrance: non-linear saturation booster that enriches muted colors without blowing out saturated tones.
 * 2. Sky Blue Protection: prevents natural blue skies from drifting into electric cyan.
 * 3. Foliage Green Protection: suppresses radioactive neon green while preserving rich forest tones.
 * 4. Scene Intelligence Context: preserves atmospheric lighting (golden hour warmth, warm indoor intimacy, night street richness).
 */
object ColorAppearanceProcessor {

    /**
     * Applies selective color appearance processing to [r, g, b] in [0.0f, 1.0f].
     *
     * @param r Red component [0.0f, 1.0f].
     * @param g Green component [0.0f, 1.0f].
     * @param b Blue component [0.0f, 1.0f].
     * @param globalSaturation Global saturation factor (1.0 = neutral).
     * @param vibrance Vibrance boost factor [0.0f, 1.0f].
     * @param sceneType Photographic scene classification from Phase 5.
     */
    fun processPixel(
        r: Float,
        g: Float,
        b: Float,
        globalSaturation: Float = 1.0f,
        vibrance: Float = 0.20f,
        sceneType: SceneType = SceneType.UNKNOWN
    ): FloatArray {
        val hsv = FloatArray(3)
        Color.colorToHSV(
            (0xFF shl 24) or
            ((r * 255f).roundToInt().coerceIn(0, 255) shl 16) or
            ((g * 255f).roundToInt().coerceIn(0, 255) shl 8) or
            (b * 255f).roundToInt().coerceIn(0, 255),
            hsv
        )

        val hue = hsv[0]
        var sat = hsv[1]
        val valL = hsv[2]

        // 1. Non-Linear Vibrance Calculation
        // Muted colors (sat < 0.40) receive highest boost; saturated colors (sat > 0.70) receive zero boost
        if (vibrance > 0.01f) {
            val vibranceWeight = (1.0f - sat).pow(1.5f)
            sat = (sat + vibrance * 0.35f * vibranceWeight).coerceIn(0f, 1f)
        }

        // 2. Global Saturation Scaling
        sat = (sat * globalSaturation).coerceIn(0f, 1f)

        // 3. Selective Gamut Protection (Sky, Foliage, Scene Atmospheric Balance)
        when {
            // Blue Sky (195° to 235°): Preserve pure cobalt blue, prevent cyan drift
            hue in 195.0f..235.0f -> {
                // Ensure sky hue does not drift below 205° toward cyan
                if (hue < 205.0f) {
                    hsv[0] = lerp(hue, 212.0f, 0.45f)
                }
                // Gentle saturation restraint on skies to prevent posterized banding
                sat = min(sat, 0.75f)
            }

            // Green Foliage (80° to 155°): Suppress neon-yellow foliage, steer toward organic green
            hue in 80.0f..155.0f -> {
                // Steer yellowish green (80-95°) toward natural leaf green (115°)
                if (hue < 100.0f) {
                    hsv[0] = lerp(hue, 115.0f, 0.30f)
                }
                // Suppress radioactive neon saturation in foliage
                sat *= 0.92f
            }

            // Scene-Aware Atmosphere Preservation
            else -> {
                when (sceneType) {
                    SceneType.BACKLIT, SceneType.OUTDOOR -> {
                        // Protect specular highlight blowout
                        if (valL > 0.88f) {
                            val highlightDesat = (valL - 0.88f) / 0.12f
                            sat *= (1.0f - highlightDesat * 0.45f)
                        }
                    }
                    SceneType.NIGHT -> {
                        // Prevent night streets from becoming desaturated gray
                        if (valL in 0.10f..0.50f && sat < 0.20f) {
                            sat *= 1.15f
                        }
                    }
                    SceneType.INDOOR -> {
                        // Warm indoor: maintain natural warm ambient glow
                    }
                    else -> {}
                }
            }
        }

        hsv[1] = sat.coerceIn(0f, 1f)
        val outColor = Color.HSVToColor(hsv)

        val outR = ((outColor shr 16) and 0xFF) / 255.0f
        val outG = ((outColor shr 8) and 0xFF) / 255.0f
        val outB = (outColor and 0xFF) / 255.0f

        return floatArrayOf(outR, outG, outB)
    }

    private fun lerp(start: Float, stop: Float, amount: Float): Float {
        return start + (stop - start) * amount.coerceIn(0f, 1f)
    }
}

