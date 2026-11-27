package com.craftcam.app.color

import com.craftcam.app.processor.ParallelPixelRunner
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Luminance-aware Creative Temperature transformation engine (-100 .. +100).
 *
 * Completely independent from Camera2 hardware Kelvin white balance (`COLOR_CORRECTION_GAINS`)
 * and internal WB fallback offsets:
 * - T = 0 returns exact unmodified input colors.
 * - T < 0 (-1 .. -100): subtle cool -> cold blue -> frozen icy blue.
 * - T > 0 (+1 .. +100): warm amber -> golden orange -> hot orange-red -> deep red grade.
 * - Preserves black anchor, shadow separation, highlight headroom, and optional portrait skin tones.
 *
 * Mathematical constants mirror `ShaderConstants.CREATIVE_TEMPERATURE_HELPERS_GLSL` 1:1.
 */
object CreativeTemperatureEngine {

    const val MIN_TEMP = -100.0f
    const val MAX_TEMP = 100.0f
    const val NEUTRAL_TEMP = 0.0f

    fun clampTemp(value: Float): Float = value.coerceIn(MIN_TEMP, MAX_TEMP)

    fun resolveEffectiveTemperature(creativeTemperature: Float, isMonochrome: Boolean): Float {
        if (isMonochrome) return 0.0f
        val clamped = clampTemp(creativeTemperature)
        return if (abs(clamped) < 0.05f) 0.0f else clamped
    }

    fun formatValue(value: Float): String {
        val rounded = clampTemp(value).roundToInt()
        return when {
            rounded > 0 -> "+$rounded"
            rounded < 0 -> "$rounded"
            else -> "0"
        }
    }

    fun describeTemperature(value: Float): String {
        val rounded = clampTemp(value).roundToInt()
        return when {
            rounded <= -85 -> "Frozen Blue"
            rounded <= -45 -> "Cold Blue"
            rounded <= -10 -> "Subtle Cool"
            rounded in -9..9 -> "Original"
            rounded <= 38 -> "Warm Amber"
            rounded <= 62 -> "Golden Orange"
            rounded <= 84 -> "Hot Orange-Red"
            else -> "Deep Red"
        }
    }

    private fun clamp01(x: Float): Float = when {
        x <= 0.0f -> 0.0f
        x >= 1.0f -> 1.0f
        else -> x
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp01((x - edge0) / (edge1 - edge0))
        return t * t * (3.0f - 2.0f * t)
    }

    /**
     * Evaluates a single normalized linear/sRGB triplet `(rIn, gIn, bIn)` in `[0, 1]`
     * and writes `[rOut, gOut, bOut]` to [outRgb] with zero heap allocations.
     */
    fun applyRgbNormalized(
        rIn: Float,
        gIn: Float,
        bIn: Float,
        tempValue: Float,
        skinProtect: Boolean,
        outRgb: FloatArray
    ) {
        val tNorm = (tempValue / 100.0f).coerceIn(-1.0f, 1.0f)
        if (abs(tNorm) < 0.001f) {
            outRgb[0] = rIn
            outRgb[1] = gIn
            outRgb[2] = bIn
            return
        }

        val r = clamp01(rIn)
        val g = clamp01(gIn)
        val b = clamp01(bIn)

        val y0 = 0.2126f * r + 0.7152f * g + 0.0722f * b

        // 1. Optional continuous RGB-ratio skin tone protection mask
        val skinW = if (skinProtect) {
            val rgbSum = max(0.001f, r + g + b)
            val ng = g / rgbSum
            val sDom = smoothstep(0.01f, 0.08f, r - g) * smoothstep(0.02f, 0.14f, r - b)
            val sGreen = smoothstep(0.22f, 0.28f, ng) * (1.0f - smoothstep(0.35f, 0.40f, ng))
            val sLuma = smoothstep(0.08f, 0.20f, y0) * (1.0f - smoothstep(0.86f, 0.96f, y0))
            clamp01(sDom * sGreen * sLuma * 1.35f)
        } else {
            0.0f
        }

        val effT = if (tNorm < 0.0f) {
            tNorm * (1.0f - 0.56f * skinW)
        } else {
            tNorm * (1.0f - 0.52f * skinW)
        }

        val u = abs(effT)
        val u2 = u * u
        val u3 = u2 * u
        val isCold = effT < 0.0f

        // 2. Luminance-aware modulation envelope
        val shadowGuard = smoothstep(0.0f, 0.15f, y0)
        val highlightGuard = 1.0f - 0.46f * smoothstep(0.68f, 1.0f, y0)
        val chromaBell = 0.42f + 0.58f * (4.0f * y0 * (1.0f - y0))
        val weight = shadowGuard * highlightGuard * chromaBell

        // 3. Non-linear multi-stop chromatic shift
        val dR: Float
        val dG: Float
        val dB: Float
        if (isCold) {
            dR = -(0.36f * u + 0.34f * u2)
            dG = 0.11f * u - 0.22f * u2
            dB = 0.44f * u + 0.24f * u2
        } else {
            dR = 0.36f * u + 0.24f * u2
            val gQuad = 0.44f - 0.16f * skinW
            val gCube = 0.42f - 0.24f * skinW
            dG = 0.26f * u - gQuad * u2 - gCube * u3
            dB = -(0.46f * u + 0.36f * u2)
        }

        val gainR = max(0.08f, 1.0f + dR * (0.60f + 0.42f * weight))
        val gainG = max(0.14f, 1.0f + dG * (0.58f + 0.38f * weight))
        val gainB = max(0.06f, 1.0f + dB * (0.62f + 0.44f * weight))

        val addR = if (dR >= 0.0f) dR * 0.090f * weight else dR * 0.055f * weight * smoothstep(0.05f, 0.35f, r)
        val addG = if (dG >= 0.0f) dG * 0.070f * weight else dG * 0.040f * weight * smoothstep(0.05f, 0.35f, g)
        val addB = if (dB >= 0.0f) dB * 0.090f * weight else dB * 0.050f * weight * smoothstep(0.05f, 0.35f, b)

        var tr = max(0.0f, r * gainR + addR)
        var tg = max(0.0f, g * gainG + addG)
        var tb = max(0.0f, b * gainB + addB)

        // 4. Partial Rec.709 luminance preservation
        val y1 = 0.2126f * tr + 0.7152f * tg + 0.0722f * tb
        val lumPreserve = 0.74f * (1.0f - 0.34f * u2)
        val rawRatio = if (y1 > 1e-4f) y0 / y1 else 1.0f
        val clampedRatio = rawRatio.coerceIn(0.54f, 1.55f)
        val scale = 1.0f + (clampedRatio - 1.0f) * lumPreserve
        tr *= scale
        tg *= scale
        tb *= scale

        // Deep-red tonal density refinement above +65
        if (!isCold && u > 0.65f) {
            val deepRedPull = ((u - 0.65f) / 0.35f) * 0.14f * (1.0f - 0.55f * skinW) * smoothstep(0.25f, 0.90f, y0)
            tr *= (1.0f - deepRedPull * 0.25f)
            tg *= (1.0f - deepRedPull * 1.15f)
            tb *= (1.0f - deepRedPull * 1.65f)
        }

        // 5. Soft-knee filmic gamut compression above 0.84
        val knee = 0.84f
        val headroom = 1.0f - knee
        val maxCh = max(tr, max(tg, tb))
        if (maxCh > knee && maxCh > 1e-4f) {
            val over = maxCh - knee
            val rolledMax = knee + headroom * (over / (over + headroom * 0.55f))
            val gScale = rolledMax / maxCh
            tr *= gScale
            tg *= gScale
            tb *= gScale
        }

        outRgb[0] = compressChannel(tr, knee, headroom)
        outRgb[1] = compressChannel(tg, knee, headroom)
        outRgb[2] = compressChannel(tb, knee, headroom)
    }

    private fun compressChannel(v: Float, knee: Float, headroom: Float): Float {
        if (v > knee) {
            val ex = v - knee
            return clamp01(knee + headroom * (ex / (ex + headroom * 0.65f)))
        }
        return clamp01(v)
    }

    /**
     * Applies Creative Temperature in place across an ARGB_8888 [pixels] array (`width * height` rows).
     * Exact no-op when `abs(tempValue) < 0.05f` or [isMonochrome] is true.
     */
    fun applyArgbPixelsInPlace(
        pixels: IntArray,
        width: Int,
        height: Int,
        tempValue: Float,
        skinProtect: Boolean,
        isMonochrome: Boolean = false
    ) {
        val effTemp = resolveEffectiveTemperature(tempValue, isMonochrome)
        if (effTemp == 0.0f) return
        val total = min(pixels.size, width * height)
        if (total <= 0) return

        val inv255 = 1.0f / 255.0f
        ParallelPixelRunner.runStripes(0, height, total) { _, startY, endY, isCancelled ->
            val rgbOut = FloatArray(3)
            for (y in startY until endY) {
                if (isCancelled()) return@runStripes
                val row = y * width
                for (x in 0 until width) {
                    val idx = row + x
                    val c = pixels[idx]
                    val a = c ushr 24
                    val r = ((c ushr 16) and 0xFF) * inv255
                    val g = ((c ushr 8) and 0xFF) * inv255
                    val b = (c and 0xFF) * inv255
                    applyRgbNormalized(r, g, b, effTemp, skinProtect, rgbOut)
                    val ro = (rgbOut[0] * 255.0f).roundToInt().coerceIn(0, 255)
                    val go = (rgbOut[1] * 255.0f).roundToInt().coerceIn(0, 255)
                    val bo = (rgbOut[2] * 255.0f).roundToInt().coerceIn(0, 255)
                    pixels[idx] = (a shl 24) or (ro shl 16) or (go shl 8) or bo
                }
            }
        }
    }
}
