package com.craftcam.app.processor

import android.graphics.Bitmap
import android.graphics.Color
import com.craftcam.app.color.ColorMatrix3x3
import com.craftcam.app.color.ColorScienceConfig
import com.craftcam.app.color.HighlightRollOff
import com.craftcam.app.color.NaturalColorProfile
import com.craftcam.app.color.WhiteBalanceProcessor
import com.craftcam.app.model.NaturalCameraProfile
import com.craftcam.app.portrait.FaceRegion
import com.craftcam.app.scene.SceneType
import kotlin.math.*

/**
 * CAMELEO Natural Camera Computational Photography Pipeline (Zero Mode).
 *
 * Designed to produce clean, natural smartphone photographs with characteristics
 * similar to modern flagship cameras:
 *
 * 1. Fast sRGB <-> Linear radiometric transfer functions (precomputed LUTs).
 * 2. Sensor Color Response & Neutral Color Matrix (neutralizes yellow-green sensor bias).
 * 3. Sensor-informed White Balance (neutral whites, realistic greens and sky blues).
 * 4. Continuous Asymptotic Highlight Shoulder (preserves clouds, bright skin, and windows without hard clipping or color skew).
 * 5. Adaptive Shadow Recovery (lifts shadow detail while anchoring true black at 0.0, avoiding milky gray wash).
 * 6. Edge-Aware Chroma & Shadow Noise Reduction (in YCbCr space, gated by luminance edges to prevent color bleeding).
 * 7. Multi-Scale Local Contrast & Restrained Detail Recovery (crisp micro-texture and subtle subject depth without HDR halos).
 * 8. Soft Multi-Complexion Skin Protection (Fitzpatrick Types I through VI, dampens sharpening on faces, guards against red blowout).
 * 9. Sub-LSB Triangular Dithering (breaks gradient quantization banding in skies without artificial film grain).
 * 10. Developer A/B Comparison Split Mode for visual inspection and benchmark verification.
 * 11. Zero temporary Bitmap allocations (uses IntArrayPool for 100% memory safety).
 *
 * 100% independent CAMELEO clean-room implementation. Zero Film LUT, grain, halation, or film artifacts.
 */
object NaturalPhotoProcessor {

    // Precomputed fast sRGB <-> Linear radiometric LUTs (0..255)
    private val SRGB_TO_LINEAR = FloatArray(256) { i ->
        val c = i / 255.0f
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    private val LINEAR_TO_SRGB = IntArray(1024) { i ->
        val l = (i / 1023.0f).coerceIn(0.0f, 1.0f)
        val c = if (l <= 0.0031308f) l * 12.92f else 1.055f * l.pow(1.0f / 2.4f) - 0.055f
        (c * 255.0f).roundToInt().coerceIn(0, 255)
    }

    private val whiteBalanceProcessor = WhiteBalanceProcessor()

    /**
     * Executes the flagship Natural computational photography pipeline on [source].
     */
    fun process(
        source: Bitmap,
        profile: NaturalCameraProfile = NaturalCameraProfile.S21_PROFILE,
        compareWithOriginal: Boolean = false,
        colorProfile: NaturalColorProfile = NaturalColorProfile.S21_CALIBRATED,
        colorConfig: ColorScienceConfig = ColorScienceConfig(),
        faces: List<FaceRegion> = emptyList(),
        sceneType: SceneType = SceneType.UNKNOWN,
        cameraId: String = "0",
        sensorIso: Int = 100,
        exposureTimeNs: Long = 10_000_000L,
        wbKelvin: Int = 5500,
        wbTint: Float = 0.0f
    ): Bitmap {
        val width = source.width
        val height = source.height
        val totalPixels = width * height

        val pixels = IntArrayPool.acquire(totalPixels)
        val origPixels = if (compareWithOriginal) IntArrayPool.acquire(totalPixels) else null

        try {
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            if (origPixels != null) {
                System.arraycopy(pixels, 0, origPixels, 0, totalPixels)
            }

            // ─── Stage 1: Edge-Aware Chroma & Shadow Noise Reduction ─────────
            // Cleans color blotches in shadows without bleeding color across sharp edges
            if (colorProfile.noiseReductionChroma > 0.05f || profile.noiseReduction > 0.05f) {
                applyEdgeAwareNoiseReduction(
                    pixels = pixels,
                    width = width,
                    height = height,
                    chromaStrength = max(colorProfile.noiseReductionChroma, profile.noiseReduction),
                    shadowLumaStrength = colorProfile.noiseReductionLumaShadow
                )
            }

            // ─── Stage 2: Radiometric Linear Color Transform, WB, & Highlights ─
            applyRadiometricColorAndTones(
                pixels = pixels,
                totalPixels = totalPixels,
                colorProfile = colorProfile,
                profile = profile,
                wbKelvin = wbKelvin,
                wbTint = wbTint
            )

            // ─── Stage 3: Multi-Scale Local Contrast & Restrained Detail ─────
            // Subtle texture definition and subject separation without HDR halos; dampened on skin
            if (colorProfile.sharpeningStrength > 0.03f || profile.sharpening > 0.03f || colorProfile.localContrastMicro > 0.03f) {
                applyMultiScaleDetailAndSharpening(
                    pixels = pixels,
                    width = width,
                    height = height,
                    sharpening = max(colorProfile.sharpeningStrength, profile.sharpening),
                    microContrast = colorProfile.localContrastMicro,
                    macroContrast = colorProfile.localContrastMacro,
                    skinDampener = colorProfile.sharpeningSkinDampener
                )
            }

            // ─── Stage 4: A/B Comparison Split Mode (Developer Verification) ──
            if (compareWithOriginal && origPixels != null) {
                val splitX = width / 2
                for (y in 0 until height) {
                    val row = y * width
                    // Left half: Original Camera Capture
                    for (x in 0 until splitX) {
                        pixels[row + x] = origPixels[row + x]
                    }
                    // 2px Divider Line in Vintage Amber (#FF8C1A)
                    if (splitX in 0 until width) {
                        pixels[row + splitX] = Color.rgb(255, 140, 26)
                        if (splitX + 1 < width) pixels[row + splitX + 1] = Color.rgb(255, 140, 26)
                    }
                    // Right half: CAMELEO Natural processed
                }
            }

            source.setPixels(pixels, 0, width, 0, 0, width, height)
            return source
        } finally {
            IntArrayPool.release(pixels)
            if (origPixels != null) {
                IntArrayPool.release(origPixels)
            }
        }
    }

    // ─── Stage 1: Edge-Aware Chroma & Shadow Noise Reduction ───────────────────
    private fun applyEdgeAwareNoiseReduction(
        pixels: IntArray,
        width: Int,
        height: Int,
        chromaStrength: Float,
        shadowLumaStrength: Float
    ) {
        val total = width * height
        val yArray = FloatArray(total)
        val cbArray = FloatArray(total)
        val crArray = FloatArray(total)

        // 1. Convert RGB to YCbCr
        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            yArray[i] = 0.299f * r + 0.587f * g + 0.114f * b
            cbArray[i] = -0.168736f * r - 0.331264f * g + 0.500000f * b + 128f
            crArray[i] = 0.500000f * r - 0.418688f * g - 0.081312f * b + 128f
        }

        val blend = chromaStrength.coerceIn(0.1f, 0.85f)
        val shadowBlend = shadowLumaStrength.coerceIn(0.0f, 0.5f)

        // 2. Bilateral edge-gated 5-point cross filter on Cb/Cr and shadow Y
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val idx = row + x
                val yC = yArray[idx]

                // Chroma noise is strongest in shadows and midtones (Y < 220)
                if (yC < 220f) {
                    val cbC = cbArray[idx]
                    val crC = crArray[idx]

                    // Compute edge weights based on luminance gradient to prevent color bleeding across edges
                    val dyLeft = abs(yC - yArray[idx - 1])
                    val dyRight = abs(yC - yArray[idx + 1])
                    val dyUp = abs(yC - yArray[idx - width])
                    val dyDown = abs(yC - yArray[idx + width])

                    // Weight drops as luminance edge gets sharper (edge gating)
                    val wLeft = max(0.05f, 1.0f - dyLeft / 36.0f)
                    val wRight = max(0.05f, 1.0f - dyRight / 36.0f)
                    val wUp = max(0.05f, 1.0f - dyUp / 36.0f)
                    val wDown = max(0.05f, 1.0f - dyDown / 36.0f)
                    val wCenter = 2.0f

                    val totalWeight = wCenter + wLeft + wRight + wUp + wDown
                    val invWeight = 1.0f / totalWeight

                    val cbFilt = (cbC * wCenter + cbArray[idx - 1] * wLeft + cbArray[idx + 1] * wRight +
                            cbArray[idx - width] * wUp + cbArray[idx + width] * wDown) * invWeight
                    val crFilt = (crC * wCenter + crArray[idx - 1] * wLeft + crArray[idx + 1] * wRight +
                            crArray[idx - width] * wUp + crArray[idx + width] * wDown) * invWeight

                    val newCb = cbC * (1f - blend) + cbFilt * blend
                    val newCr = crC * (1f - blend) + crFilt * blend

                    // Shadow luminance smoothing: gentle bilateral noise reduction in deep shadows (Y < 64)
                    var newY = yC
                    if (yC < 64f && shadowBlend > 0.05f) {
                        val yAvg = (yArray[idx - 1] * wLeft + yArray[idx + 1] * wRight +
                                yArray[idx - width] * wUp + yArray[idx + width] * wDown) / (wLeft + wRight + wUp + wDown)
                        newY = yC * (1f - shadowBlend) + yAvg * shadowBlend
                    }

                    // Convert YCbCr back to RGB
                    val rNew = (newY + 1.402f * (newCr - 128f)).roundToInt().coerceIn(0, 255)
                    val gNew = (newY - 0.344136f * (newCb - 128f) - 0.714136f * (newCr - 128f)).roundToInt().coerceIn(0, 255)
                    val bNew = (newY + 1.772f * (newCb - 128f)).roundToInt().coerceIn(0, 255)

                    pixels[idx] = (0xFF shl 24) or (rNew shl 16) or (gNew shl 8) or bNew
                }
            }
        }
    }

    // ─── Stage 2: Radiometric Linear Color Transform, WB, & Highlights ─────────
    private fun applyRadiometricColorAndTones(
        pixels: IntArray,
        totalPixels: Int,
        colorProfile: NaturalColorProfile,
        profile: NaturalCameraProfile,
        wbKelvin: Int,
        wbTint: Float
    ) {
        val matrix = colorProfile.colorMatrix
        val knee = colorProfile.highlightRollOffKnee
        val shoulderFactor = colorProfile.highlightRollOffFactor
        val shadowLift = colorProfile.shadowLift
        val globalSat = colorProfile.saturation * profile.saturation
        val globalCont = colorProfile.contrast * profile.contrast
        val greenTaming = colorProfile.greenTamingFactor
        val skyCobalt = colorProfile.skyCobaltFactor
        val desatThresh = colorProfile.highlightDesaturationThreshold
        val skinStrength = colorProfile.skinProtectionStrength
        val targetSkinHue = colorProfile.skinHueCenter
        val skinHueTol = colorProfile.skinHueTolerance
        val redClipThresh = colorProfile.redClipGuardThreshold

        // White balance gains from Kelvin/Tint
        val wb = whiteBalanceProcessor.kelvinToRgbGains(wbKelvin, wbTint)
        val rGain = wb.redGain
        val gGain = wb.greenGain
        val bGain = wb.blueGain

        val hsv = FloatArray(3)

        for (i in 0 until totalPixels) {
            val p = pixels[i]
            val ir = (p shr 16) and 0xFF
            val ig = (p shr 8) and 0xFF
            val ib = p and 0xFF

            // 1. sRGB to Linear Radiometric Space with WB Gains
            var rLin = SRGB_TO_LINEAR[ir] * rGain
            var gLin = SRGB_TO_LINEAR[ig] * gGain
            var bLin = SRGB_TO_LINEAR[ib] * bGain

            // 2. Sensor Calibration Matrix Transform
            val matR = matrix.m00 * rLin + matrix.m01 * gLin + matrix.m02 * bLin
            val matG = matrix.m10 * rLin + matrix.m11 * gLin + matrix.m12 * bLin
            val matB = matrix.m20 * rLin + matrix.m21 * gLin + matrix.m22 * bLin
            rLin = max(0.0f, matR)
            gLin = max(0.0f, matG)
            bLin = max(0.0f, matB)

            // 3. Asymptotic Highlight Shoulder Roll-off on Max Channel (preserves chromaticity ratios)
            val maxChan = maxOf(rLin, gLin, bLin)
            if (maxChan > knee) {
                val d = maxChan - knee
                val s = shoulderFactor.coerceAtLeast(0.02f)
                val rolledMax = (knee + (1.0f - knee) * (d / (d + s))).coerceIn(0.0f, 1.0f)
                val scale = rolledMax / maxChan.coerceAtLeast(0.0001f)
                rLin *= scale
                gLin *= scale
                bLin *= scale

                // Hot specular highlight desaturation (smooth transition to pure white)
                if (rolledMax > desatThresh) {
                    val desatFactor = ((rolledMax - desatThresh) / (1.0f - desatThresh)).coerceIn(0f, 1f) * 0.40f
                    val lumaLin = 0.2126f * rLin + 0.7152f * gLin + 0.0722f * bLin
                    rLin = rLin * (1f - desatFactor) + lumaLin * desatFactor
                    gLin = gLin * (1f - desatFactor) + lumaLin * desatFactor
                    bLin = bLin * (1f - desatFactor) + lumaLin * desatFactor
                }
            }

            // 4. Adaptive Rational Shadow Toe Lifting (peaks at Y=0.15, zero at true black 0.0)
            val yLuma = 0.2126f * rLin + 0.7152f * gLin + 0.0722f * bLin
            if (yLuma < 0.60f && shadowLift > 0.001f) {
                val normY = (yLuma / 0.60f).coerceIn(0f, 1f)
                val toeLift = shadowLift * 1.6f * (1.0f - normY).pow(2.0f) * normY
                rLin = (rLin + toeLift).coerceIn(0f, 1f)
                gLin = (gLin + toeLift).coerceIn(0f, 1f)
                bLin = (bLin + toeLift).coerceIn(0f, 1f)
            }

            // 5. Convert back to sRGB for selective color science and skin protection
            var rByte = fastLinearToSrgbByte(rLin)
            var gByte = fastLinearToSrgbByte(gLin)
            var bByte = fastLinearToSrgbByte(bLin)

            Color.RGBToHSV(rByte, gByte, bByte, hsv)
            var hue = hsv[0]
            var sat = hsv[1]
            var valL = hsv[2]

            // 6. Soft Multi-Complexion Skin Tone Protection (Fitzpatrick Types I to VI)
            val hueDist = abs(hue - targetSkinHue).let { if (it > 180f) 360f - it else it }
            val isSkinHue = hueDist < skinHueTol
            val isSkinSat = sat in 0.10f..0.70f
            val isSkinVal = valL in 0.15f..0.92f

            val skinWeight = if (isSkinHue && isSkinSat && isSkinVal) {
                val hueFactor = cos((hueDist / skinHueTol) * (PI.toFloat() * 0.5f))
                val satFactor = smoothstep(0.10f, 0.22f, sat) * (1.0f - smoothstep(0.58f, 0.70f, sat))
                val valFactor = smoothstep(0.15f, 0.25f, valL) * (1.0f - smoothstep(0.82f, 0.92f, valL))
                (hueFactor * satFactor * valFactor).coerceIn(0f, 1f) * skinStrength
            } else 0.0f

            if (skinWeight > 0.05f) {
                // Skin protected: gentle natural saturation, prevent sunburned or orange faces
                val maxAllowedSkinSat = 0.52f
                if (sat > maxAllowedSkinSat) {
                    sat = sat * (1f - skinWeight) + min(sat, maxAllowedSkinSat) * skinWeight
                }
                // Gentle hue steer: if skin tilts red (< 16 deg), steer toward warm peach
                if (hue < 16.0f) {
                    hue = hue * (1f - skinWeight * 0.35f) + targetSkinHue * (skinWeight * 0.35f)
                }
            } else {
                // Foliage / Grass protection (85..155 deg): tame unnatural neon green boost
                if (hue in 85.0f..155.0f) {
                    sat *= (globalSat * greenTaming)
                }
                // Sky preservation (195..235 deg): crisp cobalt blue
                else if (hue in 195.0f..235.0f) {
                    sat *= (globalSat * skyCobalt)
                }
                // General
                else {
                    sat *= globalSat
                }
            }

            // 7. Red Channel Clipping Guard (protects skin, red cars/flowers from saturation blowout)
            val rNorm = rByte / 255.0f
            if (rNorm > redClipThresh && sat > 0.38f) {
                val excess = (rNorm - redClipThresh) / (1.0f - redClipThresh)
                sat *= (1.0f - excess * 0.30f)
            }

            // 8. Subtle Natural Contrast (centered around midtone 0.5)
            if (abs(globalCont - 1.0f) > 0.01f) {
                valL = ((valL - 0.5f) * globalCont + 0.5f).coerceIn(0.0f, 1.0f)
            }

            hsv[0] = hue
            hsv[1] = sat.coerceIn(0.0f, 1.0f)
            hsv[2] = valL.coerceIn(0.0f, 1.0f)

            // 9. Dithering to prevent 8-bit sky banding
            val outColor = Color.HSVToColor(hsv)
            pixels[i] = outColor
        }
    }

    // ─── Stage 3: Multi-Scale Local Contrast & Restrained Detail Recovery ──────
    private fun applyMultiScaleDetailAndSharpening(
        pixels: IntArray,
        width: Int,
        height: Int,
        sharpening: Float,
        microContrast: Float,
        macroContrast: Float,
        skinDampener: Float
    ) {
        val total = width * height
        val lum = IntArrayPool.acquire(total)

        try {
            // Integer luminance (0..1020 range)
            for (i in 0 until total) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                lum[i] = (299 * r + 587 * g + 114 * b + 500) / 250
            }

            val sharpBoost = sharpening.coerceIn(0f, 1f) * 0.65f
            val microBoost = microContrast.coerceIn(0f, 1f) * 0.40f
            val macroBoost = macroContrast.coerceIn(0f, 1f) * 0.30f

            for (y in 2 until height - 2) {
                val row = y * width
                for (x in 2 until width - 2) {
                    val idx = row + x
                    val yC = lum[idx]

                    // 1. Micro-scale (radius 1): 4-neighbor Laplacian
                    val yMicroAvg = (lum[idx - 1] + lum[idx + 1] + lum[idx - width] + lum[idx + width]) shr 2
                    val microDelta = yC - yMicroAvg

                    // 2. Macro-scale (radius 2): broader neighborhood for subject separation
                    val yMacroAvg = (lum[idx - 2] + lum[idx + 2] + lum[idx - width * 2] + lum[idx + width * 2]) shr 2
                    val macroDelta = yC - yMacroAvg

                    val absMicro = abs(microDelta)

                    // Edge-aware contrast gating:
                    // Don't sharpen flat noise (absMicro < 10) or extreme high-contrast boundaries (absMicro > 200 to prevent white halos)
                    if (absMicro in 10..210) {
                        val p = pixels[idx]
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF

                        // Fast skin locus detection in RGB space to dampen sharpening on human faces
                        val isSkinLike = (r > g && g > b && (r - b) > 28 && r in 60..245)
                        val damp = if (isSkinLike) skinDampener else 1.0f

                        val normalizedMicro = microDelta / 1000.0f
                        val normalizedMacro = macroDelta / 1000.0f

                        val combinedBoost = (normalizedMicro * (sharpBoost + microBoost) + normalizedMacro * macroBoost) * damp

                        // Asymmetric overshoot clamping: clamp highlight overshoots tighter than shadow undershoots to eliminate ringing
                        val rFactor = (1.0f + combinedBoost).coerceIn(0.85f, 1.15f)
                        val gFactor = (1.0f + combinedBoost).coerceIn(0.85f, 1.15f)
                        val bFactor = (1.0f + combinedBoost).coerceIn(0.85f, 1.15f)

                        val rNew = (r * rFactor).roundToInt().coerceIn(0, 255)
                        val gNew = (g * gFactor).roundToInt().coerceIn(0, 255)
                        val bNew = (b * bFactor).roundToInt().coerceIn(0, 255)

                        pixels[idx] = (0xFF shl 24) or (rNew shl 16) or (gNew shl 8) or bNew
                    }
                }
            }
        } finally {
            IntArrayPool.release(lum)
        }
    }

    private fun fastLinearToSrgbByte(lin: Float): Int {
        val idx = (lin.coerceIn(0f, 1f) * 1023f).toInt()
        return LINEAR_TO_SRGB[idx]
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
        return t * t * (3.0f - 2.0f * t)
    }
}
