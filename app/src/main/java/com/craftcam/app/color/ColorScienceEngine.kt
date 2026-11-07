package com.craftcam.app.color

import android.graphics.Bitmap
import android.graphics.Color
import com.craftcam.app.portrait.FaceRegion
import com.craftcam.app.processor.IntArrayPool
import com.craftcam.app.scene.SceneType
import kotlin.math.*

/**
 * Master Color Science Engine for CAMELEO.
 *
 * Implements the 10-stage photographic color processing pipeline:
 * Input sRGB -> Linear Radiometric Space -> Multi-Illuminant Sensor Matrix ->
 * Temporal White Balance -> Smooth Parametric Tone Curve -> Asymptotic Highlight Roll-Off ->
 * Non-Linear Vibrance & Selective Gamut Protection -> Soft Multi-Complexion Skin Protection ->
 * Target Color Space (sRGB / Display P3) -> Sub-LSB Quantization Dithering -> Output MediaStore JPEG.
 *
 * 100% independent CAMELEO clean-room implementation. Zero external cloud dependencies.
 */
class ColorScienceEngine(
    private val whiteBalanceProcessor: WhiteBalanceProcessor = WhiteBalanceProcessor()
) {

    /**
     * Executes the complete color science pipeline on [source].
     *
     * @param source Working photo bitmap.
     * @param config Color science configuration and active profile.
     * @param faces Detected face regions from Phase 5/6 (for skin protection priority).
     * @param sceneType Photographic scene classification from Phase 5.
     * @param cameraId Active Camera2 camera ID ("0" = wide rear, "1" = front, etc.).
     */
    fun processPhoto(
        source: Bitmap,
        config: ColorScienceConfig = ColorScienceConfig(),
        faces: List<FaceRegion> = emptyList(),
        sceneType: SceneType = SceneType.UNKNOWN,
        cameraId: String = "0"
    ): Bitmap {
        val width = source.width
        val height = source.height
        val total = width * height

        val profile = config.activeProfile
        val pixels = IntArrayPool.acquire(total)

        try {
            source.getPixels(pixels, 0, width, 0, 0, width, height)

            // 1. Resolve Sensor Color Matrix & White Balance
            val sensorMatrix = if (config.enableSensorMatrix) {
                profile.colorMatrix
            } else {
                ColorMatrix3x3.IDENTITY
            }

            val wbState = whiteBalanceProcessor.kelvinToRgbGains(
                config.customKelvin,
                config.customTint
            )

            val rGain = wbState.redGain
            val gGain = wbState.greenGain
            val bGain = wbState.blueGain

            val knee = profile.highlightRollOffKnee
            val shoulderFactor = profile.highlightRollOffFactor
            val toneCurve = profile.toneCurve

            val effSat = (profile.saturation + config.saturationOffset).coerceIn(0.2f, 2.0f)
            val effVibrance = (profile.vibrance + config.vibranceOffset).coerceIn(0.0f, 1.0f)
            val effSkinStrength = profile.skinProtectionStrength

            val isP3 = config.targetColorSpace == ColorSpaceType.DISPLAY_P3
            val pixelFaces = faces.map { it.toPixelRect(width, height) }

            // 2. Linear Color Operations & Tone Mapping Pass
            for (y in 0 until height) {
                val row = y * width
                val yF = y.toFloat()

                for (x in 0 until width) {
                    val idx = row + x
                    val p = pixels[idx]
                    val ir = (p shr 16) and 0xFF
                    val ig = (p shr 8) and 0xFF
                    val ib = p and 0xFF

                    // A. sRGB to Linear Radiometric Space
                    var rLin = ColorSpace.fastSrgbByteToLinear(ir) * rGain
                    var gLin = ColorSpace.fastSrgbByteToLinear(ig) * gGain
                    var bLin = ColorSpace.fastSrgbByteToLinear(ib) * bGain

                    // B. Sensor Calibration Matrix Transform
                    if (config.enableSensorMatrix) {
                        val matR = sensorMatrix.m00 * rLin + sensorMatrix.m01 * gLin + sensorMatrix.m02 * bLin
                        val matG = sensorMatrix.m10 * rLin + sensorMatrix.m11 * gLin + sensorMatrix.m12 * bLin
                        val matB = sensorMatrix.m20 * rLin + sensorMatrix.m21 * gLin + sensorMatrix.m22 * bLin
                        rLin = matR.coerceAtLeast(0.0f)
                        gLin = matG.coerceAtLeast(0.0f)
                        bLin = matB.coerceAtLeast(0.0f)
                    }

                    // C. Tone Curve & Shadow Toe Lifting
                    if (config.enableToneCurve) {
                        rLin = toneCurve.evaluate(rLin)
                        gLin = toneCurve.evaluate(gLin)
                        bLin = toneCurve.evaluate(bLin)
                    }

                    // D. Asymptotic Highlight Roll-off (Shoulder Compression)
                    if (config.enableHighlightRollOff) {
                        val rolled = HighlightRollOff.applyRgb(rLin, gLin, bLin, knee, shoulderFactor)
                        rLin = rolled[0]
                        gLin = rolled[1]
                        bLin = rolled[2]
                    }

                    // E. Color Appearance (Non-linear Vibrance, Sky Blue, and Foliage Protection)
                    if (config.enableVibrance) {
                        val appeared = ColorAppearanceProcessor.processPixel(
                            rLin, gLin, bLin, effSat, effVibrance, sceneType
                        )
                        rLin = appeared[0]
                        gLin = appeared[1]
                        bLin = appeared[2]
                    }

                    // F. Skin Tone Protection & Red Clipping Guard
                    if (config.enableSkinProtection) {
                        val xF = x.toFloat()
                        val inFaceZone = pixelFaces.any { it.contains(xF, yF) }
                        val protected = SkinToneProcessor.processPixel(
                            rLin, gLin, bLin, effSkinStrength, inFaceZone
                        )
                        rLin = protected[0]
                        gLin = protected[1]
                        bLin = protected[2]
                    }

                    // G. Target Gamut Conversion (sRGB to Display P3 if selected)
                    if (isP3) {
                        val p3 = ColorSpace.SRGB_TO_P3_MATRIX.apply(rLin, gLin, bLin)
                        rLin = p3[0].coerceIn(0f, 1f)
                        gLin = p3[1].coerceIn(0f, 1f)
                        bLin = p3[2].coerceIn(0f, 1f)
                    }

                    // H. Sub-LSB Triangular Dithering (breaks gradient quantization in skies without grain)
                    var outRByte: Int
                    var outGByte: Int
                    var outBByte: Int

                    if (config.enableDithering) {
                        // High-frequency deterministic dither pattern based on pixel coordinates
                        val dither = (((x xor y * 149) and 0x07) - 3.5f) / 1024.0f
                        outRByte = ColorSpace.fastLinearToSrgbByte((rLin + dither).coerceIn(0f, 1f))
                        outGByte = ColorSpace.fastLinearToSrgbByte((gLin + dither).coerceIn(0f, 1f))
                        outBByte = ColorSpace.fastLinearToSrgbByte((bLin + dither).coerceIn(0f, 1f))
                    } else {
                        outRByte = ColorSpace.fastLinearToSrgbByte(rLin)
                        outGByte = ColorSpace.fastLinearToSrgbByte(gLin)
                        outBByte = ColorSpace.fastLinearToSrgbByte(bLin)
                    }

                    pixels[idx] = (0xFF shl 24) or (outRByte shl 16) or (outGByte shl 8) or outBByte
                }
            }

            source.setPixels(pixels, 0, width, 0, 0, width, height)
            return source
        } finally {
            IntArrayPool.release(pixels)
        }
    }
}

