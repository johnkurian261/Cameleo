package com.craftcam.app.threed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlin.math.*

/**
 * Renders authentic analog photographic imperfections onto 3D parallax frames:
 * 1. Scanned 35mm Kodak Vision3 film grain (grain_d3d1.jpg, grain_d3d2.jpg, grain_d3d3.jpg)
 *    with parabolic midtone weighting (4L(1-L)).
 * 2. Sub-pixel transverse RGB misalignment (lateral chromatic aberration).
 * 3. Mechanical film registration jitter (micro-pixel camera gate tolerance).
 * 4. Frame-to-frame mechanical leaf-shutter exposure variation (±1.2%).
 * 5. Optical lens softness (disabled by default to preserve crisp details).
 * 6. Subtle vintage corner light leak (configurable).
 */
class RetroFrameRenderer(
    private val context: Context? = null,
    private val config: ThreeDConfig = ThreeDConfig()
) {

    companion object {
        private const val TAG = "RetroFrameRenderer"
        private val GRAIN_ASSET_NAMES = arrayOf(
            "grains/grain_d3d1.jpg",
            "grains/grain_d3d2.jpg",
            "grains/grain_d3d3.jpg"
        )
    }

    private data class GrainTexture(
        val pixels: IntArray,
        val width: Int,
        val height: Int
    )

    private var cachedGrains: List<GrainTexture>? = null

    private fun getOrLoadGrains(): List<GrainTexture> {
        cachedGrains?.let { return it }
        val ctx = context ?: return emptyList()
        val list = ArrayList<GrainTexture>(GRAIN_ASSET_NAMES.size)

        for (assetName in GRAIN_ASSET_NAMES) {
            try {
                ctx.assets.open(assetName).use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        val gw = bmp.width
                        val gh = bmp.height
                        val gp = IntArray(gw * gh)
                        bmp.getPixels(gp, 0, gw, 0, 0, gw, gh)
                        list.add(GrainTexture(gp, gw, gh))
                        bmp.recycle()
                        Log.d(TAG, "Loaded scanned 3D film grain: $assetName (${gw}x${gh})")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not load scanned grain asset: $assetName", t)
            }
        }

        cachedGrains = list
        return list
    }

    /**
     * Applies retro analog effects to a list of viewpoint frames.
     *
     * @param frames Warped viewpoint Bitmaps.
     * @return Processed Bitmaps with vintage film characteristics.
     */
    fun processFrames(frames: List<Bitmap>): List<Bitmap> {
        val count = frames.size
        val outFrames = ArrayList<Bitmap>(count)
        val grains = getOrLoadGrains()

        for (i in 0 until count) {
            val src = frames[i]
            val processed = processSingleFrame(src, frameIndex = i, totalFrames = count, grains = grains)
            outFrames.add(processed)
        }

        return outFrames
    }

    private fun processSingleFrame(
        source: Bitmap,
        frameIndex: Int,
        totalFrames: Int,
        grains: List<GrainTexture>
    ): Bitmap {
        val width = source.width
        val height = source.height
        val total = width * height

        val srcPixels = IntArray(total)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)
        val outPixels = IntArray(total)

        // 1. Frame-to-Frame Exposure Variance (±1.2% leaf shutter mechanical variance)
        val exposureMult = 1.0f + config.exposureVariance * sin(frameIndex * 2.1f + 0.5f)

        // 2. Mechanical Registration Jitter (sub-pixel film gate tolerance: ±0.35 px)
        val jitterX = config.mechanicalJitterPx * sin(frameIndex * 3.1f + 0.8f)
        val jitterY = config.mechanicalJitterPx * 0.5f * cos(frameIndex * 2.3f + 0.4f)

        // 3. Sub-pixel Transverse RGB Misalignment (lateral chromatic aberration: Red +0.8px, Blue -0.8px)
        val rShiftX = (config.rgbSeparationPx + jitterX)
        val rShiftY = jitterY
        val bShiftX = (-config.rgbSeparationPx + jitterX)
        val bShiftY = jitterY
        val gShiftX = jitterX
        val gShiftY = jitterY

        // Fast random hash seed per frame for procedural fallback
        val frameSeed = (frameIndex * 1664525 + 1013904223)

        // Scanned grain texture selection
        val hasScannedGrain = grains.isNotEmpty()
        val grainTex = if (hasScannedGrain) grains[frameIndex % grains.size] else null
        val invertGrain = (frameIndex >= grains.size)

        // Light leak parameters
        val leakActive = config.lightLeakEnabled && config.lightLeakIntensity > 0f
        val leakCornerX = 0f
        val leakCornerY = 0f
        val leakRadius = max(width, height) * 0.65f
        val leakPhase = (2.0 * Math.PI * frameIndex / totalFrames).toFloat()
        val leakStrength = config.lightLeakIntensity * (0.85f + 0.15f * sin(leakPhase * 2f))

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                // Sample Red channel with sub-pixel shift
                val r = sampleChannel(srcPixels, width, height, x + rShiftX, y + rShiftY, channel = 0)
                // Sample Green channel with mechanical jitter
                val g = sampleChannel(srcPixels, width, height, x + gShiftX, y + gShiftY, channel = 1)
                // Sample Blue channel with reverse sub-pixel shift
                val b = sampleChannel(srcPixels, width, height, x + bShiftX, y + bShiftY, channel = 2)

                // Apply exposure multiplier
                var finalR = (r * exposureMult).coerceIn(0f, 255f)
                var finalG = (g * exposureMult).coerceIn(0f, 255f)
                var finalB = (b * exposureMult).coerceIn(0f, 255f)

                // Apply optional warm corner light leak
                if (leakActive) {
                    val dist = sqrt((x - leakCornerX) * (x - leakCornerX) + (y - leakCornerY) * (y - leakCornerY))
                    if (dist < leakRadius) {
                        val leakFalloff = (1f - dist / leakRadius).pow(2.2f) * leakStrength
                        finalR = (finalR + 255f * leakFalloff * 0.90f).coerceAtMost(255f)
                        finalG = (finalG + 255f * leakFalloff * 0.55f).coerceAtMost(255f)
                        finalB = (finalB + 255f * leakFalloff * 0.20f).coerceAtMost(255f)
                    }
                }

                // 4. Photographic 35mm Film Grain (Parabolic Midtone Weighting 4L(1-L))
                if (config.grainIntensity > 0f) {
                    val luma = (0.299f * finalR + 0.587f * finalG + 0.114f * finalB) / 255f
                    val grainWeight = (4.0f * luma * (1.0f - luma)).coerceIn(0f, 1f)

                    val grainDelta = if (grainTex != null) {
                        val gw = grainTex.width
                        val gh = grainTex.height
                        val gx = if (invertGrain) (gw - 1 - (x % gw)) else (x % gw)
                        val gy = if (invertGrain) (gh - 1 - (y % gh)) else (y % gh)
                        val gp = grainTex.pixels[gy * gw + gx]
                        val rG = (gp shr 16) and 0xFF
                        val gG = (gp shr 8) and 0xFF
                        val bG = gp and 0xFF
                        val scanLuma = rG * 0.299f + gG * 0.587f + bG * 0.114f
                        ((scanLuma - 128f) * (1.0f / 128f)) * config.grainIntensity * 70f
                    } else {
                        val noise = pseudoRandom(x, y, frameSeed) * 2f - 1f
                        noise * config.grainIntensity * 55f
                    }

                    finalR = (finalR + grainDelta * grainWeight).coerceIn(0f, 255f)
                    finalG = (finalG + grainDelta * grainWeight).coerceIn(0f, 255f)
                    finalB = (finalB + grainDelta * grainWeight).coerceIn(0f, 255f)
                }

                val ir = finalR.roundToInt()
                val ig = finalG.roundToInt()
                val ib = finalB.roundToInt()
                outPixels[rowOffset + x] = 0xFF000000.toInt() or (ir shl 16) or (ig shl 8) or ib
            }
        }

        // 5. Subtle Optical Softness (Only if explicitly enabled)
        if (config.softnessRadius > 0.1f) {
            applyGentleDiffusion(outPixels, width, height)
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Sub-pixel bilinear channel sampling.
     * channel: 0 = R, 1 = G, 2 = B
     */
    private fun sampleChannel(
        pixels: IntArray,
        w: Int,
        h: Int,
        x: Float,
        y: Float,
        channel: Int
    ): Float {
        val cx = x.coerceIn(0f, (w - 1).toFloat())
        val cy = y.coerceIn(0f, (h - 1).toFloat())

        val x0 = floor(cx).toInt()
        val y0 = floor(cy).toInt()
        val x1 = (x0 + 1).coerceAtMost(w - 1)
        val y1 = (y0 + 1).coerceAtMost(h - 1)

        val tx = cx - x0
        val ty = cy - y0

        val shift = when (channel) {
            0 -> 16
            1 -> 8
            else -> 0
        }

        val c00 = (pixels[y0 * w + x0] shr shift) and 0xFF
        val c10 = (pixels[y0 * w + x1] shr shift) and 0xFF
        val c01 = (pixels[y1 * w + x0] shr shift) and 0xFF
        val c11 = (pixels[y1 * w + x1] shr shift) and 0xFF

        val top = c00 * (1f - tx) + c10 * tx
        val bottom = c01 * (1f - tx) + c11 * tx
        return top * (1f - ty) + bottom * ty
    }

    /**
     * High-speed deterministic PRNG returning [0.0, 1.0].
     */
    private fun pseudoRandom(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return (h and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat()
    }

    /**
     * In-place lightweight 3-tap Gaussian-like diffusion: [0.08, 0.84, 0.08]
     * Softens harsh digital edges into organic vintage lens rendition.
     */
    private fun applyGentleDiffusion(pixels: IntArray, width: Int, height: Int) {
        val temp = pixels.clone()
        val w0 = 0.84f
        val w1 = 0.08f

        for (y in 0 until height) {
            val row = y * width
            for (x in 1 until width - 1) {
                val pL = temp[row + x - 1]
                val pC = temp[row + x]
                val pR = temp[row + x + 1]

                val r = (((pL shr 16) and 0xFF) * w1 + ((pC shr 16) and 0xFF) * w0 + ((pR shr 16) and 0xFF) * w1).roundToInt()
                val g = (((pL shr 8) and 0xFF) * w1 + ((pC shr 8) and 0xFF) * w0 + ((pR shr 8) and 0xFF) * w1).roundToInt()
                val b = ((pL and 0xFF) * w1 + (pC and 0xFF) * w0 + (pR and 0xFF) * w1).roundToInt()

                pixels[row + x] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
            }
        }
    }
}
