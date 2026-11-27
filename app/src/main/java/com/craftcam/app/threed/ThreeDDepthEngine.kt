package com.craftcam.app.threed

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.craftcam.app.portrait.DepthMap
import com.craftcam.app.portrait.FaceRegion
import com.craftcam.app.portrait.MonocularDepthEstimator
import com.craftcam.app.portrait.PortraitConfig
import java.io.File
import java.io.FileOutputStream
import kotlin.math.floor

/**
 * Normalized Disparity Map for 3D stereoscopic parallax synthesis.
 *
 * Disparity convention:
 * - 1.0f = Nearest foreground plane (receives maximum parallax shift).
 * - 0.0f = Infinite background distance (receives minimal or reverse parallax shift).
 */
data class DisparityMap(
    val width: Int,
    val height: Int,
    val values: FloatArray,
    val pivot: Float = 0.50f,
    val rawDepthValues: FloatArray? = null,
    val depthMin: Float = 0f,
    val depthMax: Float = 1f,
    val depthMean: Float = 0.5f,
    val depthStdDev: Float = 0.2f,
    val disparityMean: Float = 0.5f,
    val disparityStdDev: Float = 0.2f,
    val foregroundDisparity: Float = 0.85f,
    val backgroundDisparity: Float = 0.15f,
    val backendUsed: DepthBackend = DepthBackend.DEPTH_ANYTHING_V2
) {
    init {
        require(values.size == width * height) {
            "DisparityMap size (${values.size}) must match $width * $height"
        }
    }

    /**
     * Retrieves normalized disparity at integer coordinate (x, y).
     */
    fun getDisparity(x: Int, y: Int): Float {
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        return values[cy * width + cx]
    }

    /**
     * Samples disparity at normalized coordinates (u, v) in [0.0, 1.0] using bilinear interpolation.
     */
    fun sampleBilinear(u: Float, v: Float): Float {
        val x = (u * (width - 1)).coerceIn(0f, (width - 1).toFloat())
        val y = (v * (height - 1)).coerceIn(0f, (height - 1).toFloat())

        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)

        val tx = x - x0
        val ty = y - y0

        val d00 = values[y0 * width + x0]
        val d10 = values[y0 * width + x1]
        val d01 = values[y1 * width + x0]
        val d11 = values[y1 * width + x1]

        val top = d00 * (1f - tx) + d10 * tx
        val bottom = d01 * (1f - tx) + d11 * tx
        return top * (1f - ty) + bottom * ty
    }
}

/**
 * Depth estimation and normalization engine for CAMELEO 3D mode.
 *
 * Supports:
 * 1. AI Monocular Depth Estimation using Depth Anything V2 Small ([DepthAnythingV2Engine]).
 * 2. Automatic, zero-crash fallback to heuristic Monocular Depth ([MonocularDepthEstimator]).
 * 3. A/B testing backend selection via [ThreeDConfig.depthBackend].
 * 4. Dynamic subject convergence pinning (Face Anchor or Center-Weighted).
 * 5. Diagnostic depth map exports (01_original, 02_raw_depth, 03_normalized_depth, 04_refined_depth).
 */
class ThreeDDepthEngine(
    private val context: Context? = null,
    private val config: ThreeDConfig = ThreeDConfig()
) {

    companion object {
        private const val TAG = "ThreeDDepthEngine"
    }

    private val depthAnythingV2 = DepthAnythingV2Engine(context)
    private val legacyEstimator = MonocularDepthEstimator()

    /**
     * Computes the normalized [DisparityMap] for [source].
     */
    fun computeDisparity(
        source: Bitmap,
        faces: List<FaceRegion> = emptyList(),
        focus: ConvergencePoint? = null
    ): DisparityMap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        return computeDisparityFromPixels(pixels, w, h, faces, focus)
    }

    /**
     * Computes the normalized [DisparityMap] directly from an ARGB_8888 [pixels] buffer.
     * Enables zero-overhead JVM unit testing and Android execution.
     */
    fun computeDisparityFromPixels(
        pixels: IntArray,
        srcW: Int,
        srcH: Int,
        faces: List<FaceRegion> = emptyList(),
        focus: ConvergencePoint? = null
    ): DisparityMap {
        // Mode 1: Depth Anything V2 Small (when configured and available)
        if (config.depthBackend == DepthBackend.DEPTH_ANYTHING_V2) {
            try {
                Log.i(TAG, "Executing AI Depth Anything V2 Small depth estimation...")
                val aiResult = depthAnythingV2.estimateDepthFromPixels(pixels, srcW, srcH, maxDimension = 518)
                if (aiResult != null) {
                    val w = aiResult.width
                    val h = aiResult.height
                    val total = w * h
                    val disparityValues = aiResult.refinedDepth

                    // Calculate convergence pivot disparity
                    val pivot = calculatePivot(w, h, disparityValues, faces, focus)

                    // Export diagnostic debug passes if requested
                    if (config.d3dDebugDepth && context != null) {
                        exportDepthDebugPasses(pixels, srcW, srcH, aiResult)
                    }

                    // Compute pipeline audit metrics
                    var sumDisp = 0.0
                    var sumSqDisp = 0.0
                    for (i in 0 until total) {
                        val v = disparityValues[i]
                        sumDisp += v
                        sumSqDisp += v * v
                    }
                    val dispMean = (sumDisp / total).toFloat()
                    val dispVariance = (sumSqDisp / total) - (dispMean.toDouble() * dispMean.toDouble())
                    val dispStdDev = kotlin.math.sqrt(dispVariance.coerceAtLeast(0.0)).toFloat()

                    val sortedDisp = disparityValues.clone().apply { sort() }
                    val foregroundDisparity = sortedDisp[(total * 0.90f).toInt().coerceIn(0, total - 1)]
                    val backgroundDisparity = sortedDisp[(total * 0.10f).toInt().coerceIn(0, total - 1)]

                    Log.i(TAG, "=== Depth Anything V2 Audit ===")
                    Log.i(TAG, "rawMin=${aiResult.minRaw}, rawMax=${aiResult.maxRaw}, rawMean=${aiResult.meanRaw}")
                    Log.i(TAG, "disparityMean=$dispMean, disparityStdDev=$dispStdDev")
                    Log.i(TAG, "foreground(p90)=$foregroundDisparity, background(p10)=$backgroundDisparity, pivot=$pivot")
                    Log.i(TAG, "Inference runtime: ${aiResult.inferenceTimeMs}ms at ${w}x${h}")

                    return DisparityMap(
                        width = w,
                        height = h,
                        values = disparityValues,
                        pivot = pivot,
                        rawDepthValues = aiResult.rawDepth,
                        depthMin = aiResult.minRaw,
                        depthMax = aiResult.maxRaw,
                        depthMean = aiResult.meanRaw,
                        depthStdDev = 0.2f,
                        disparityMean = dispMean,
                        disparityStdDev = dispStdDev,
                        foregroundDisparity = foregroundDisparity,
                        backgroundDisparity = backgroundDisparity,
                        backendUsed = DepthBackend.DEPTH_ANYTHING_V2
                    )
                } else {
                    Log.w(TAG, "Depth Anything V2 returned null; falling back to legacy depth engine")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Depth Anything V2 failed; seamlessly falling back to legacy depth engine", t)
            }
        }

        // Mode 2: Legacy Defocus + Perspective Prior Fallback
        Log.i(TAG, "Using Legacy MonocularDepthEstimator (defocus + perspective prior)")
        return computeLegacyDisparityFromPixels(pixels, srcW, srcH, faces, focus)
    }

    /**
     * Legacy depth estimation pipeline using Sobel sharpness + perspective gradient prior.
     */
    private fun computeLegacyDisparityFromPixels(
        pixels: IntArray,
        srcW: Int,
        srcH: Int,
        faces: List<FaceRegion>,
        focus: ConvergencePoint? = null
    ): DisparityMap {
        try {
            val depthW = 320
            val depthH = 240
            val total = depthW * depthH
            val disparityValues = FloatArray(total)

            // Calculate luminance at 320x240
            val luma = FloatArray(total)
            val xRatio = (srcW - 1).toFloat() / (depthW - 1).coerceAtLeast(1)
            val yRatio = (srcH - 1).toFloat() / (depthH - 1).coerceAtLeast(1)

            for (y in 0 until depthH) {
                val sy = (y * yRatio).toInt().coerceIn(0, srcH - 1)
                val rowOffset = y * depthW
                for (x in 0 until depthW) {
                    val sx = (x * xRatio).toInt().coerceIn(0, srcW - 1)
                    val p = pixels[sy * srcW + sx]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    luma[rowOffset + x] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                }
            }

            // Defocus sharpness via Sobel
            val sharpness = FloatArray(total)
            var maxSharp = 0.001f
            for (y in 1 until depthH - 1) {
                val r0 = (y - 1) * depthW
                val r1 = y * depthW
                val r2 = (y + 1) * depthW
                for (x in 1 until depthW - 1) {
                    val gx = (luma[r2 + x + 1] + 2f * luma[r1 + x + 1] + luma[r0 + x + 1]) -
                            (luma[r2 + x - 1] + 2f * luma[r1 + x - 1] + luma[r0 + x - 1])
                    val gy = (luma[r2 + x - 1] + 2f * luma[r2 + x] + luma[r2 + x + 1]) -
                            (luma[r0 + x - 1] + 2f * luma[r0 + x] + luma[r0 + x + 1])
                    val mag = kotlin.math.sqrt(gx * gx + gy * gy)
                    sharpness[r1 + x] = mag
                    if (mag > maxSharp) maxSharp = mag
                }
            }

            // Combine perspective gradient prior with sharpness
            for (y in 0 until depthH) {
                val yNorm = y.toFloat() / (depthH - 1)
                val r1 = y * depthW
                for (x in 0 until depthW) {
                    val xNorm = x.toFloat() / (depthW - 1)
                    val s = (sharpness[r1 + x] / maxSharp).coerceIn(0f, 1f)
                    val centerDist = kotlin.math.sqrt((xNorm - 0.5f) * (xNorm - 0.5f) + (yNorm - 0.5f) * (yNorm - 0.5f))
                    // Bottom is near (1.0), top is far (0.0)
                    val perspective = yNorm * 0.65f + 0.15f
                    val centerBonus = (1f - centerDist * 1.5f).coerceIn(0f, 0.2f)
                    disparityValues[r1 + x] = (perspective + s * 0.2f + centerBonus).coerceIn(0f, 1f)
                }
            }

            val pivot = calculatePivot(depthW, depthH, disparityValues, faces, focus)

            val sortedDisp = disparityValues.clone().apply { sort() }
            val foregroundDisparity = sortedDisp[(total * 0.90f).toInt().coerceIn(0, total - 1)]
            val backgroundDisparity = sortedDisp[(total * 0.10f).toInt().coerceIn(0, total - 1)]

            return DisparityMap(
                width = depthW,
                height = depthH,
                values = disparityValues,
                pivot = pivot,
                rawDepthValues = disparityValues,
                depthMin = 0f,
                depthMax = 1f,
                depthMean = 0.5f,
                depthStdDev = 0.2f,
                disparityMean = 0.5f,
                disparityStdDev = 0.2f,
                foregroundDisparity = foregroundDisparity,
                backgroundDisparity = backgroundDisparity,
                backendUsed = DepthBackend.CURRENT
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Legacy depth estimation failed; falling back to mathematical gradient prior", t)
            return createPerspectiveFallback(320, 240, faces, focus)
        }
    }

    /**
     * Determines the convergence / optical pivot disparity.
     * The subject at this disparity plane remains perfectly stationary in physical space
     * while foreground and background exhibit stereoscopic parallax around them.
     */
    internal fun primaryFace(faces: List<FaceRegion>): FaceRegion? = faces.filter {
        val b = it.bounds
        it.confidence >= 0.3f && b.left in 0f..1f && b.top in 0f..1f &&
            b.right in 0f..1f && b.bottom in 0f..1f && b.right > b.left && b.bottom > b.top
    }.maxByOrNull { (it.bounds.right - it.bounds.left) * (it.bounds.bottom - it.bounds.top) * it.confidence }

    private fun depthSubject(w: Int, h: Int, values: FloatArray): ConvergencePoint? {
        // Look for a coherent near surface inside the composition. A single bright depth outlier
        // or an isolated hand at the frame boundary is insufficient to move convergence.
        val tiles = 9
        val grid = FloatArray(tiles * tiles) { i ->
            val x = ((i % tiles + 0.5f) * w / tiles).toInt().coerceIn(0, w - 1)
            val y = ((i / tiles + 0.5f) * h / tiles).toInt().coerceIn(0, h - 1)
            values[y * w + x]
        }
        val valid = grid.filter { it in 0f..1f }.sorted()
        if (valid.size < grid.size / 2) return null
        val far = valid[valid.size / 5]
        val near = valid[valid.size * 4 / 5]
        if (near - far < 0.18f) return null
        val threshold = far + (near - far) * 0.75f
        var best = -1f
        var point: ConvergencePoint? = null
        for (y in 1 until tiles - 1) for (x in 1 until tiles - 1) {
            val depth = grid[y * tiles + x]
            if (depth !in threshold..1f) continue
            var coherent = 0
            for (dy in -1..1) for (dx in -1..1) {
                if (kotlin.math.abs(grid[(y + dy) * tiles + x + dx] - depth) < 0.12f) coherent++
            }
            if (coherent < 6) continue
            val distance = (x - 4) * (x - 4) + (y - 4) * (y - 4)
            val score = coherent - 0.4f * distance
            if (score > best) {
                best = score
                point = ConvergencePoint((x + 0.5f) / tiles, (y + 0.5f) / tiles)
            }
        }
        return point
    }

    internal fun calculatePivot(
        w: Int,
        h: Int,
        disparityValues: FloatArray,
        faces: List<FaceRegion>,
        focus: ConvergencePoint? = null
    ): Float {
        // Anchor a real surface. Averaging a foreground face with a distant wall
        // creates a convergence plane that neither object actually occupies.
        val target = focus?.takeIf { it.x in 0f..1f && it.y in 0f..1f }
        val primary = if (target == null) primaryFace(faces) else null
        val face = primary?.bounds
        val left = primary?.leftEye
        val right = primary?.rightEye
        val eyes = if (face != null && left != null && right != null &&
            left.x in face.left..face.right && right.x in face.left..face.right &&
            left.y in face.top..face.bottom && right.y in face.top..face.bottom)
            ConvergencePoint((left.x + right.x) * 0.5f, (left.y + right.y) * 0.5f) else null
        val sceneTarget = target ?: if (face == null) depthSubject(w, h, disparityValues) else null
        val cx = if (face == null) sceneTarget?.x ?: 0.5f else eyes?.x ?: (face.left + face.right) * 0.5f
        val cy = if (face == null) sceneTarget?.y ?: 0.5f else eyes?.y ?: (face.top + (face.bottom - face.top) * 0.46f)
        val rx = if (face == null) 0.04f else (face.right - face.left) * 0.18f
        val ry = if (face == null) 0.04f else (face.bottom - face.top) * 0.12f
        val x0 = ((cx - rx) * w).toInt().coerceIn(0, w - 1)
        val x1 = ((cx + rx) * w).toInt().coerceIn(x0, w - 1)
        val y0 = ((cy - ry) * h).toInt().coerceIn(0, h - 1)
        val y1 = ((cy + ry) * h).toInt().coerceIn(y0, h - 1)
        val samples = FloatArray((x1 - x0 + 1) * (y1 - y0 + 1))
        var index = 0
        for (y in y0..y1) {
            for (x in x0..x1) {
                val value = disparityValues[y * w + x]
                if (value in 0f..1f) samples[index++] = value
            }
        }
        if (index == 0 && target != null) return calculatePivot(w, h, disparityValues, faces)
        if (index == 0) return if (face != null) calculatePivot(w, h, disparityValues, emptyList()) else config.pivotDisparity
        samples.sort(0, index)
        return samples[index / 2]
    }

    /**
     * Exports internal development-only depth passes:
     * - 01_original.jpg
     * - 02_raw_depth.png
     * - 03_normalized_depth.png
     * - 04_refined_depth.png
     */
    private fun exportDepthDebugPasses(pixels: IntArray, srcW: Int, srcH: Int, result: DepthAnythingResult) {
        try {
            val debugDir = File(context?.getExternalFilesDir(null) ?: context?.cacheDir, "d3d_debug")
            debugDir.mkdirs()

            // 01_original.jpg
            try {
                val origBmp = Bitmap.createBitmap(pixels, srcW, srcH, Bitmap.Config.ARGB_8888)
                if (origBmp != null) {
                    File(debugDir, "01_original.jpg").outputStream().use {
                        origBmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
                    }
                    origBmp.recycle()
                }
            } catch (_: Throwable) {}

            // 02_raw_depth.png (contrast-stretched raw model output: black=far, white=near)
            val w = result.width
            val h = result.height
            val total = w * h
            val rawRange = (result.maxRaw - result.minRaw).coerceAtLeast(0.001f)
            val rawPixels = IntArray(total)
            for (i in 0 until total) {
                val norm = ((result.rawDepth[i] - result.minRaw) / rawRange).coerceIn(0f, 1f)
                val b = (norm * 255f).toInt()
                rawPixels[i] = (0xFF shl 24) or (b shl 16) or (b shl 8) or b
            }
            try {
                val rawBmp = Bitmap.createBitmap(rawPixels, w, h, Bitmap.Config.ARGB_8888)
                if (rawBmp != null) {
                    File(debugDir, "02_raw_depth.png").outputStream().use {
                        rawBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    rawBmp.recycle()
                }
            } catch (_: Throwable) {}

            // 03_normalized_depth.png
            val normPixels = IntArray(total)
            for (i in 0 until total) {
                val b = (result.normalizedDepth[i].coerceIn(0f, 1f) * 255f).toInt()
                normPixels[i] = (0xFF shl 24) or (b shl 16) or (b shl 8) or b
            }
            try {
                val normBmp = Bitmap.createBitmap(normPixels, w, h, Bitmap.Config.ARGB_8888)
                if (normBmp != null) {
                    File(debugDir, "03_normalized_depth.png").outputStream().use {
                        normBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    normBmp.recycle()
                }
            } catch (_: Throwable) {}

            // 04_refined_depth.png (edge-preserved cross-bilateral filtered)
            val refPixels = IntArray(total)
            for (i in 0 until total) {
                val b = (result.refinedDepth[i].coerceIn(0f, 1f) * 255f).toInt()
                refPixels[i] = (0xFF shl 24) or (b shl 16) or (b shl 8) or b
            }
            try {
                val refBmp = Bitmap.createBitmap(refPixels, w, h, Bitmap.Config.ARGB_8888)
                if (refBmp != null) {
                    File(debugDir, "04_refined_depth.png").outputStream().use {
                        refBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    refBmp.recycle()
                }
            } catch (_: Throwable) {}

            Log.i(TAG, "Successfully exported 4 depth debug passes to: ${debugDir.absolutePath}")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not export depth debug passes", t)
        }
    }

    /**
     * Fallback perspective gradient prior when depth estimation is unavailable.
     */
    private fun createPerspectiveFallback(w: Int, h: Int, faces: List<FaceRegion>, focus: ConvergencePoint?): DisparityMap {
        val total = w * h
        val values = FloatArray(total)
        for (y in 0 until h) {
            val yNorm = y.toFloat() / (h - 1).coerceAtLeast(1)
            val row = y * w
            for (x in 0 until w) {
                val xNorm = x.toFloat() / (w - 1).coerceAtLeast(1)
                val centerDist = kotlin.math.sqrt((xNorm - 0.5f) * (xNorm - 0.5f) + (yNorm - 0.5f) * (yNorm - 0.5f))
                val base = yNorm * 0.7f + 0.15f
                val centerBonus = (1f - centerDist * 1.5f).coerceIn(0f, 0.25f)
                values[row + x] = (base + centerBonus).coerceIn(0f, 1f)
            }
        }
        val pivot = calculatePivot(w, h, values, faces, focus)
        return DisparityMap(w, h, values, pivot, backendUsed = DepthBackend.CURRENT)
    }
}
