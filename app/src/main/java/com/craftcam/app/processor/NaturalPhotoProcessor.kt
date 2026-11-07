package com.craftcam.app.processor

import android.graphics.Bitmap
import android.graphics.Color
import com.craftcam.app.color.ColorScienceConfig
import com.craftcam.app.color.ColorScienceEngine
import com.craftcam.app.model.NaturalCameraProfile
import com.craftcam.app.portrait.FaceRegion
import com.craftcam.app.scene.SceneType
import kotlin.math.*

/**
 * CAMELEO Natural Camera Engine.
 *
 * Flagship-style computational photography pipeline for clean, natural, balanced photographs:
 * 1. Fast sRGB <-> Linear radiometric transfer functions (precomputed LUTs).
 * 2. Conservative chroma noise reduction (preserves luminance micro-detail, eliminates shadow color blotches).
 * 3. Linear Natural Color Matrix & sensor-balanced white balance.
 * 4. Asymptotic highlight shoulder (prevents harsh highlight clipping in skies/windows/lights).
 * 5. Gentle shadow lifting with deep black preservation (no milky gray washed-out blacks).
 * 6. Soft HSL skin-tone protection (broad soft mask protecting all skin types from over-saturation).
 * 7. Restrained natural saturation (skies/foliage controlled, highlights desaturated).
 * 8. Edge-aware restrained sharpening & microcontrast (dampened on human skin).
 * 9. A/B comparison split-screen mode for development verification.
 * 10. Zero new Bitmap allocations (uses IntArrayPool for 100% memory safety).
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

    private val colorScienceEngine = ColorScienceEngine()

    fun process(
        source: Bitmap,
        profile: NaturalCameraProfile = NaturalCameraProfile.S21_PROFILE,
        compareWithOriginal: Boolean = false,
        colorConfig: ColorScienceConfig = ColorScienceConfig(),
        faces: List<FaceRegion> = emptyList(),
        sceneType: SceneType = SceneType.UNKNOWN,
        cameraId: String = "0"
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

            // ─── Stage 1: Chroma Noise Reduction (in YCbCr space) ───────────
            if (profile.noiseReduction > 0.05f || colorConfig.enableChromaNoiseReduction) {
                applyChromaNoiseReduction(pixels, width, height, profile.noiseReduction)
            }

            source.setPixels(pixels, 0, width, 0, 0, width, height)

            // ─── Stage 2: Advanced Color Science Pipeline (Phase 7) ──────────
            // Linearization -> Multi-Illuminant Matrix -> Tone Curve -> Highlight Roll-Off ->
            // Non-linear Vibrance -> Skin Protection -> Display P3 / sRGB -> Dithering
            val colored = colorScienceEngine.processPhoto(
                source = source,
                config = colorConfig,
                faces = faces,
                sceneType = sceneType,
                cameraId = cameraId
            )

            // Read back pixels for microcontrast and A/B comparison
            colored.getPixels(pixels, 0, width, 0, 0, width, height)

            // ─── Stage 3: Restrained Edge-Aware Sharpening & Microcontrast ───
            if (profile.sharpening > 0.05f || profile.localContrast > 0.05f) {
                applyEdgeAwareSharpening(pixels, width, height, profile.sharpening, profile.localContrast)
            }

            // ─── Stage 4: A/B Comparison Split Mode (Debug Verification) ─────
            if (compareWithOriginal && origPixels != null) {
                val splitX = width / 2
                for (y in 0 until height) {
                    val row = y * width
                    // Left half: Original Camera Capture
                    for (x in 0 until splitX) {
                        pixels[row + x] = origPixels[row + x]
                    }
                    // 2px Divider Line in Vintage Amber
                    if (splitX in 0 until width) {
                        pixels[row + splitX] = Color.rgb(255, 140, 26)
                        if (splitX + 1 < width) pixels[row + splitX + 1] = Color.rgb(255, 140, 26)
                    }
                    // Right half: CAMELEO Natural processed
                }
            }

            colored.setPixels(pixels, 0, width, 0, 0, width, height)
            return colored
        } finally {
            IntArrayPool.release(pixels)
            if (origPixels != null) {
                IntArrayPool.release(origPixels)
            }
        }
    }

    // ─── Chroma Noise Reduction ──────────────────────────────────────────────
    private fun applyChromaNoiseReduction(
        pixels: IntArray,
        width: Int,
        height: Int,
        strength: Float
    ) {
        val total = width * height
        val cbArray = FloatArray(total)
        val crArray = FloatArray(total)
        val yArray = FloatArray(total)

        // Step 1: RGB to YCbCr
        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            yArray[i] = 0.299f * r + 0.587f * g + 0.114f * b
            cbArray[i] = -0.168736f * r - 0.331264f * g + 0.500000f * b + 128f
            crArray[i] = 0.500000f * r - 0.418688f * g - 0.081312f * b + 128f
        }

        val blend = strength.coerceIn(0.1f, 0.9f)
        val step = 1

        // Step 2: Edge-gated 5-point cross filter on Cb/Cr in shadows & midtones
        for (y in step until height - step) {
            val row = y * width
            for (x in step until width - step) {
                val idx = row + x
                val yC = yArray[idx]

                // Only apply chroma filtering in shadows and midtones (where chroma noise lives)
                if (yC < 210f) {
                    val cbC = cbArray[idx]
                    val crC = crArray[idx]

                    val cbAvg = (cbC * 2f + cbArray[idx - 1] + cbArray[idx + 1] + cbArray[idx - width] + cbArray[idx + width]) * 0.1666f
                    val crAvg = (crC * 2f + crArray[idx - 1] + crArray[idx + 1] + crArray[idx - width] + crArray[idx + width]) * 0.1666f

                    val newCb = cbC * (1f - blend) + cbAvg * blend
                    val newCr = crC * (1f - blend) + crAvg * blend

                    // YCbCr back to RGB
                    val yVal = yC
                    val rNew = (yVal + 1.402f * (newCr - 128f)).roundToInt().coerceIn(0, 255)
                    val gNew = (yVal - 0.344136f * (newCb - 128f) - 0.714136f * (newCr - 128f)).roundToInt().coerceIn(0, 255)
                    val bNew = (yVal + 1.772f * (newCb - 128f)).roundToInt().coerceIn(0, 255)

                    pixels[idx] = (0xFF shl 24) or (rNew shl 16) or (gNew shl 8) or bNew
                }
            }
        }
    }

    // ─── HSL Color Correction & Skin Protection ──────────────────────────────
    private fun applyNaturalColorAndSkinProtection(
        pixels: IntArray,
        totalPixels: Int,
        profile: NaturalCameraProfile
    ) {
        val hsv = FloatArray(3)
        val targetSkinHue = 26.0f
        val skinProtectStrength = profile.skinProtection.coerceIn(0f, 1f)
        val globalSat = profile.saturation.coerceIn(0.5f, 2.0f)
        val globalCont = profile.contrast.coerceIn(0.5f, 2.0f)

        for (i in 0 until totalPixels) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]
            var sat = hsv[1]
            var lit = hsv[2]

            // Calculate soft skin weight W_skin
            val hueDist = abs(hue - targetSkinHue).let { if (it > 180f) 360f - it else it }
            val isSkinHue = hueDist < 24.0f
            val isSkinSat = sat in 0.12f..0.68f
            val isSkinLit = lit in 0.18f..0.88f

            val skinWeight = if (isSkinHue && isSkinSat && isSkinLit) {
                val hueFactor = cos((hueDist / 24.0f) * (PI.toFloat() * 0.5f))
                val satFactor = smoothstep(0.12f, 0.24f, sat) * (1.0f - smoothstep(0.55f, 0.68f, sat))
                val litFactor = smoothstep(0.18f, 0.28f, lit) * (1.0f - smoothstep(0.78f, 0.88f, lit))
                (hueFactor * satFactor * litFactor).coerceIn(0f, 1f) * skinProtectStrength
            } else 0.0f

            // 1. Natural Saturation Adjustments
            if (skinWeight > 0.05f) {
                // Skin protected: gentle saturation, prevent sunburned orange/magenta faces
                val effectiveSkinSatBoost = lerp(globalSat, 1.0f, skinWeight * 0.75f)
                sat = (sat * effectiveSkinSatBoost).coerceIn(0.10f, 0.55f)

                // Soft hue correction: if skin tilts red (< 18 deg), steer toward warm peach (26 deg)
                if (hue < 18.0f) {
                    hsv[0] = lerp(hue, targetSkinHue, skinWeight * 0.35f)
                }
            } else {
                // Non-skin regions:
                // Foliage (85-155 deg): moderate natural greens
                if (hue in 85.0f..155.0f) {
                    sat *= (globalSat * 0.94f)
                }
                // Sky (195-235 deg): natural blue
                else if (hue in 195.0f..235.0f) {
                    sat *= (globalSat * 1.02f)
                }
                // General
                else {
                    sat *= globalSat
                }

                // Highlights desaturation (prevent neon blown-out specular highlights)
                if (lit > 0.85f) {
                    val desatFactor = (lit - 0.85f) / 0.15f
                    sat *= (1.0f - desatFactor * 0.50f)
                }
            }
            hsv[1] = sat.coerceIn(0.0f, 1.0f)

            // 2. Natural Contrast (applied in Lightness channel with neutral midpoint)
            if (abs(globalCont - 1.0f) > 0.01f) {
                lit = ((lit - 0.5f) * globalCont + 0.5f).coerceIn(0.0f, 1.0f)
                hsv[2] = lit
            }

            pixels[i] = Color.HSVToColor(hsv)
        }
    }

    // ─── Edge-Aware Sharpening & Microcontrast ────────────────────────────────
    private fun applyEdgeAwareSharpening(
        pixels: IntArray,
        width: Int,
        height: Int,
        sharpening: Float,
        localContrast: Float
    ) {
        val total = width * height
        val lum = IntArrayPool.acquire(total)
        try {
            for (i in 0 until total) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                // Integer luminance scaled to 0..1020 range
                lum[i] = (299 * r + 587 * g + 114 * b + 500) / 250
            }

            val sharpBoost = sharpening.coerceIn(0f, 1f) * 0.75f
            val microBoost = localContrast.coerceIn(0f, 1f) * 0.50f
            val step = 1

            for (y in step until height - step) {
                val row = y * width
                for (x in step until width - step) {
                    val idx = row + x
                    val yC = lum[idx]

                    // Laplacian 4-neighbor high-pass
                    val yAvg = (lum[idx - 1] + lum[idx + 1] + lum[idx - width] + lum[idx + width]) shr 2
                    val deltaInt = yC - yAvg
                    val absDeltaInt = abs(deltaInt)

                    // Edge-aware gating:
                    // deltaInt in 12..220 corresponds to normalized 0.012..0.220
                    if (absDeltaInt in 12..220) {
                        val delta = deltaInt / 1000.0f
                        val p = pixels[idx]
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF

                        // Skin detection to dampen sharpening on human faces
                        val rF = r / 255f
                        val gF = g / 255f
                        val bF = b / 255f
                        val isSkinLike = (rF > gF && gF > bF && (rF - bF) > 0.12f)
                        val dampener = if (isSkinLike) 0.40f else 1.0f

                        val factor = 1.0f + delta * (sharpBoost + microBoost) * dampener
                        val rNew = (r * factor).roundToInt().coerceIn(0, 255)
                        val gNew = (g * factor).roundToInt().coerceIn(0, 255)
                        val bNew = (b * factor).roundToInt().coerceIn(0, 255)

                        pixels[idx] = (0xFF shl 24) or (rNew shl 16) or (gNew shl 8) or bNew
                    }
                }
            }
        } finally {
            IntArrayPool.release(lum)
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
        return t * t * (3.0f - 2.0f * t)
    }

    private fun lerp(start: Float, stop: Float, fraction: Float): Float {
        return start + fraction * (stop - start)
    }
}
