package com.craftcam.app.color

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Standard Working Color Spaces supported by CAMELEO.
 */
enum class ColorSpaceType(val displayName: String) {
    SRGB("sRGB (Rec.709 Primaries)"),
    DISPLAY_P3("Display P3 (D65 Wide Gamut)")
}

/**
 * Mathematically rigorous color space definitions and transfer functions.
 * Complies with IEC 61966-2-1 (sRGB) and SMPTE RP 431-2 / SMPTE EG 432-1 (Display P3).
 */
object ColorSpace {

    // ─── Fast Precomputed Transfer Function LUTs ─────────────────────────────
    internal val SRGB_TO_LIN_LUT = FloatArray(256) { i ->
        val c = i / 255.0f
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    internal val LIN_TO_SRGB_LUT = IntArray(1024) { i ->
        val l = (i / 1023.0f).coerceIn(0.0f, 1.0f)
        val c = if (l <= 0.0031308f) l * 12.92f else 1.055f * l.pow(1.0f / 2.4f) - 0.055f
        (c * 255.0f).roundToInt().coerceIn(0, 255)
    }

    // ─── Gamut Conversion Matrices (in Linear Radiometric Space) ─────────────
    /**
     * Converts linear Display P3 coordinates to linear sRGB coordinates (D65 white point).
     */
    val P3_TO_SRGB_MATRIX = ColorMatrix3x3(
        1.22494017f, -0.22473000f,  0.00000000f,
       -0.04205690f,  1.04192000f,  0.00000000f,
       -0.01963760f, -0.07863600f,  1.09790000f
    )

    /**
     * Converts linear sRGB coordinates to linear Display P3 coordinates (D65 white point).
     */
    val SRGB_TO_P3_MATRIX = ColorMatrix3x3(
        0.822462f, 0.177538f, 0.000000f,
        0.033194f, 0.966806f, 0.000000f,
        0.017083f, 0.072397f, 0.910520f
    )

    // ─── sRGB Transfer Functions ─────────────────────────────────────────────
    fun srgbToLinear(c: Float): Float {
        val clamped = c.coerceIn(0.0f, 1.0f)
        return if (clamped <= 0.04045f) {
            clamped / 12.92f
        } else {
            ((clamped + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    fun linearToSrgb(l: Float): Float {
        val clamped = l.coerceIn(0.0f, 1.0f)
        return if (clamped <= 0.0031308f) {
            clamped * 12.92f
        } else {
            1.055f * clamped.pow(1.0f / 2.4f) - 0.055f
        }
    }

    /**
     * Fast 8-bit sRGB to linear float via precomputed LUT.
     */
    fun fastSrgbByteToLinear(byteVal: Int): Float {
        return SRGB_TO_LIN_LUT[byteVal.coerceIn(0, 255)]
    }

    /**
     * Fast linear float [0.0, 1.0] to 8-bit sRGB byte via precomputed 10-bit LUT.
     */
    fun fastLinearToSrgbByte(lin: Float): Int {
        val idx = (lin.coerceIn(0f, 1f) * 1023.0f).toInt()
        return LIN_TO_SRGB_LUT[idx]
    }

    // ─── YUV to Linear RGB Color Matrices ────────────────────────────────────
    /**
     * BT.709 Full Range YCbCr to Linear sRGB conversion matrix:
     * Standard for modern HD/UHD digital camera sensors.
     */
    fun yuvBt709ToRgb(y: Float, cb: Float, cr: Float): FloatArray {
        val yNorm = y.coerceIn(0f, 255f)
        val cbNorm = cb - 128f
        val crNorm = cr - 128f

        val r = (yNorm + 1.5748f * crNorm).coerceIn(0f, 255f)
        val g = (yNorm - 0.1873f * cbNorm - 0.4681f * crNorm).coerceIn(0f, 255f)
        val b = (yNorm + 1.8556f * cbNorm).coerceIn(0f, 255f)

        return floatArrayOf(r / 255f, g / 255f, b / 255f)
    }

    /**
     * BT.601 Full Range YCbCr to RGB conversion matrix:
     * Standard baseline fallback for legacy mobile ISP streams.
     */
    fun yuvBt601ToRgb(y: Float, cb: Float, cr: Float): FloatArray {
        val yNorm = y.coerceIn(0f, 255f)
        val cbNorm = cb - 128f
        val crNorm = cr - 128f

        val r = (yNorm + 1.402f * crNorm).coerceIn(0f, 255f)
        val g = (yNorm - 0.344136f * cbNorm - 0.714136f * crNorm).coerceIn(0f, 255f)
        val b = (yNorm + 1.772f * cbNorm).coerceIn(0f, 255f)

        return floatArrayOf(r / 255f, g / 255f, b / 255f)
    }
}
