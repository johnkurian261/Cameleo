package com.craftcam.app.threed

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.craftcam.app.portrait.FaceRegion
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Result data container for Depth Anything V2 depth estimation.
 */
data class DepthAnythingResult(
    val width: Int,
    val height: Int,
    val rawDepth: FloatArray,
    val normalizedDepth: FloatArray,
    val refinedDepth: FloatArray,
    val minRaw: Float,
    val maxRaw: Float,
    val meanRaw: Float,
    val inferenceTimeMs: Long
)

/**
 * Depth Anything V2 Small monocular depth estimation backend for CAMELEO D3D.
 *
 * Architecture & pipeline:
 * 1. Aspect-ratio-preserving dynamic resolution scaling (dimensions rounded to multiples of 14).
 * 2. Planar NCHW tensor conversion with ImageNet normalization (mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]).
 * 3. On-device ONNX Runtime inference using official Depth Anything V2 Small weights.
 * 4. Relative depth normalization mapping raw model outputs to [0.0 = far, 1.0 = near].
 * 5. Edge-preserving guided cross-bilateral filtering to lock depth boundaries to source image contours.
 * 6. Thread-safe cached session with graceful fallback capability.
 */
class DepthAnythingV2Engine(private val context: Context? = null) {

    companion object {
        private const val TAG = "DepthAnythingV2Engine"
        private const val MODEL_ASSET_PATH = "models/depth_anything_v2_small.onnx"
        private const val TARGET_MAX_DIM = 518

        // ImageNet normalization constants
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        @Volatile
        private var ortEnvironment: OrtEnvironment? = null

        @Volatile
        private var cachedSession: OrtSession? = null

        private val lock = Any()
    }

    /**
     * Initializes or retrieves the cached ONNX Runtime session.
     */
    private fun getSession(): OrtSession? {
        if (cachedSession != null) return cachedSession

        synchronized(lock) {
            if (cachedSession != null) return cachedSession

            try {
                if (ortEnvironment == null) {
                    ortEnvironment = OrtEnvironment.getEnvironment()
                }
                val env = ortEnvironment ?: return null

                val sessionOpts = OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    // Utilize 4 threads for balanced throughput on big/LITTLE ARM cores
                    setInterOpNumThreads(2)
                    setIntraOpNumThreads(4)
                }

                // Locate model file: from Android assets or local filesystem fallback
                val modelFile = resolveModelFile()
                if (modelFile == null || !modelFile.exists()) {
                    Log.w(TAG, "Depth Anything V2 ONNX model file not found")
                    return null
                }

                Log.i(TAG, "Initializing ONNX Runtime session for: ${modelFile.absolutePath} (${modelFile.length()} bytes)")
                val session = env.createSession(modelFile.absolutePath, sessionOpts)
                cachedSession = session
                return session
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize Depth Anything V2 ONNX session", t)
                return null
            }
        }
    }

    /**
     * Resolves the model file to a readable File on disk.
     */
    private fun resolveModelFile(): File? {
        // 1. Check Android context assets
        if (context != null) {
            try {
                val cachedModel = File(context.cacheDir, "depth_anything_v2_small.onnx")
                if (!cachedModel.exists() || cachedModel.length() == 0L) {
                    context.assets.open(MODEL_ASSET_PATH).use { input ->
                        FileOutputStream(cachedModel).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (cachedModel.exists() && cachedModel.length() > 0) {
                    return cachedModel
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not load model from Android assets: ${e.message}")
            }
        }

        // 2. Fallback to direct relative paths (unit tests & local runtimes)
        val candidates = listOf(
            File("src/main/assets/models/depth_anything_v2_small.onnx"),
            File("app/src/main/assets/models/depth_anything_v2_small.onnx"),
            File("assets/models/depth_anything_v2_small.onnx")
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.length() > 0) {
                return candidate
            }
        }
        return null
    }

    /**
     * Checks if the Depth Anything V2 model is available and ready for inference.
     */
    fun isAvailable(): Boolean {
        return getSession() != null
    }

    /**
     * Executes monocular depth inference on [sourceBitmap].
     *
     * @param sourceBitmap The clean input photo.
     * @param maxDimension Target maximum dimension for model inference (default 518).
     * @return [DepthAnythingResult] containing raw, normalized, and edge-refined depth arrays, or null on failure.
     */
    fun estimateDepth(
        sourceBitmap: Bitmap,
        maxDimension: Int = TARGET_MAX_DIM
    ): DepthAnythingResult? {
        val srcW = sourceBitmap.width
        val srcH = sourceBitmap.height
        val total = srcW * srcH
        val pixels = IntArray(total)
        sourceBitmap.getPixels(pixels, 0, srcW, 0, 0, srcW, srcH)
        return estimateDepthFromPixels(pixels, srcW, srcH, maxDimension)
    }

    /**
     * Executes monocular depth inference directly on an ARGB_8888 [pixels] array.
     * Enables zero-overhead execution and full headless JVM unit testability.
     */
    fun estimateDepthFromPixels(
        pixels: IntArray,
        srcW: Int,
        srcH: Int,
        maxDimension: Int = TARGET_MAX_DIM
    ): DepthAnythingResult? {
        val session = getSession() ?: run {
            Log.w(TAG, "Inference skipped: ONNX session not available")
            return null
        }
        val env = ortEnvironment ?: return null

        val startTime = System.currentTimeMillis()

        try {
            // 1. Calculate aspect-ratio preserving dimensions rounded to multiples of 14
            val scale = maxDimension.toFloat() / maxOf(srcW, srcH)
            val modelW = ((srcW * scale / 14f).roundToInt() * 14).coerceAtLeast(14)
            val modelH = ((srcH * scale / 14f).roundToInt() * 14).coerceAtLeast(14)

            // Resample pixels to modelW x modelH using bilinear interpolation
            val modelPixels = IntArray(modelW * modelH)
            val xRatio = (srcW - 1).toFloat() / (modelW - 1).coerceAtLeast(1)
            val yRatio = (srcH - 1).toFloat() / (modelH - 1).coerceAtLeast(1)

            for (y in 0 until modelH) {
                val srcY = y * yRatio
                val y0 = floor(srcY).toInt()
                val y1 = (y0 + 1).coerceAtMost(srcH - 1)
                val ty = srcY - y0
                val rowOffset = y * modelW

                for (x in 0 until modelW) {
                    val srcX = x * xRatio
                    val x0 = floor(srcX).toInt()
                    val x1 = (x0 + 1).coerceAtMost(srcW - 1)
                    val tx = srcX - x0

                    val c00 = pixels[y0 * srcW + x0]
                    val c10 = pixels[y0 * srcW + x1]
                    val c01 = pixels[y1 * srcW + x0]
                    val c11 = pixels[y1 * srcW + x1]

                    val r = ((c00 shr 16 and 0xFF) * (1 - tx) * (1 - ty) +
                            (c10 shr 16 and 0xFF) * tx * (1 - ty) +
                            (c01 shr 16 and 0xFF) * (1 - tx) * ty +
                            (c11 shr 16 and 0xFF) * tx * ty).roundToInt()

                    val g = ((c00 shr 8 and 0xFF) * (1 - tx) * (1 - ty) +
                            (c10 shr 8 and 0xFF) * tx * (1 - ty) +
                            (c01 shr 8 and 0xFF) * (1 - tx) * ty +
                            (c11 shr 8 and 0xFF) * tx * ty).roundToInt()

                    val b = ((c00 and 0xFF) * (1 - tx) * (1 - ty) +
                            (c10 and 0xFF) * tx * (1 - ty) +
                            (c01 and 0xFF) * (1 - tx) * ty +
                            (c11 and 0xFF) * tx * ty).roundToInt()

                    modelPixels[rowOffset + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            val totalModelPixels = modelW * modelH

            // 2. Preprocess to planar NCHW FloatBuffer with ImageNet normalization
            val buffer = FloatBuffer.allocate(1 * 3 * modelH * modelW)
            val rOffset = 0
            val gOffset = totalModelPixels
            val bOffset = totalModelPixels * 2
            val rawArray = FloatArray(3 * totalModelPixels)

            for (i in 0 until totalModelPixels) {
                val p = modelPixels[i]
                val r = ((p shr 16) and 0xFF) / 255.0f
                val g = ((p shr 8) and 0xFF) / 255.0f
                val b = (p and 0xFF) / 255.0f

                rawArray[rOffset + i] = (r - MEAN[0]) / STD[0]
                rawArray[gOffset + i] = (g - MEAN[1]) / STD[1]
                rawArray[bOffset + i] = (b - MEAN[2]) / STD[2]
            }
            buffer.put(rawArray)
            buffer.flip()

            val inputTensor = OnnxTensor.createTensor(
                env,
                buffer,
                longArrayOf(1, 3, modelH.toLong(), modelW.toLong())
            )

            // 3. Run ONNX Inference
            val inferStart = System.currentTimeMillis()
            val outputs = session.run(mapOf("pixel_values" to inputTensor))
            val inferenceTime = System.currentTimeMillis() - inferStart

            val outputTensor = outputs.get("predicted_depth").get() as OnnxTensor
            val outValue = outputTensor.value

            // 4. Extract raw predicted depth map
            val rawDepth = FloatArray(totalModelPixels)
            var minRaw = Float.MAX_VALUE
            var maxRaw = -Float.MAX_VALUE
            var sumRaw = 0.0

            if (outValue is Array<*>) {
                val batch = outValue[0] as Array<*>
                for (y in 0 until modelH) {
                    val row = batch[y] as FloatArray
                    val rowIdx = y * modelW
                    for (x in 0 until modelW) {
                        val v = row[x]
                        rawDepth[rowIdx + x] = v
                        if (v < minRaw) minRaw = v
                        if (v > maxRaw) maxRaw = v
                        sumRaw += v
                    }
                }
            } else {
                Log.e(TAG, "Unexpected output tensor value format: ${outValue?.javaClass}")
                inputTensor.close()
                outputs.close()
                return null
            }

            inputTensor.close()
            outputs.close()

            val meanRaw = (sumRaw / totalModelPixels).toFloat()

            // 5. Robust Normalization (Depth Anything: higher = near, lower = far)
            val sorted = rawDepth.clone().apply { sort() }
            val p02 = sorted[(totalModelPixels * 0.02f).toInt().coerceIn(0, totalModelPixels - 1)]
            val p98 = sorted[(totalModelPixels * 0.98f).toInt().coerceIn(0, totalModelPixels - 1)]
            val range = (p98 - p02).coerceAtLeast(0.001f)

            val normalizedDepth = FloatArray(totalModelPixels)
            for (i in 0 until totalModelPixels) {
                normalizedDepth[i] = ((rawDepth[i] - p02) / range).coerceIn(0f, 1f)
            }

            // 6. Edge-Preserving Cross-Bilateral Refinement using original luminance
            val luma = FloatArray(totalModelPixels)
            for (i in 0 until totalModelPixels) {
                val p = modelPixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                luma[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
            }

            val refinedDepth = applyCrossBilateralFilter(
                depth = normalizedDepth,
                luma = luma,
                width = modelW,
                height = modelH,
                radius = 2,
                spatialSigma = 2.0f,
                rangeSigma = 0.12f
            )

            val totalTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "Depth Anything V2 finished in ${totalTime}ms (inference: ${inferenceTime}ms, ${modelW}x${modelH})")

            return DepthAnythingResult(
                width = modelW,
                height = modelH,
                rawDepth = rawDepth,
                normalizedDepth = normalizedDepth,
                refinedDepth = refinedDepth,
                minRaw = minRaw,
                maxRaw = maxRaw,
                meanRaw = meanRaw,
                inferenceTimeMs = inferenceTime
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error during Depth Anything V2 depth estimation", t)
            return null
        }
    }

    /**
     * Cross-Bilateral filter: smooths depth within homogeneous regions while preserving
     * sharp depth boundaries along high-contrast luminance edges.
     */
    private fun applyCrossBilateralFilter(
        depth: FloatArray,
        luma: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        spatialSigma: Float,
        rangeSigma: Float
    ): FloatArray {
        val out = FloatArray(width * height)
        val twoSpatialSigmaSq = 2f * spatialSigma * spatialSigma
        val twoRangeSigmaSq = 2f * rangeSigma * rangeSigma

        for (y in 0 until height) {
            val yMin = (y - radius).coerceAtLeast(0)
            val yMax = (y + radius).coerceAtMost(height - 1)
            val rowOffset = y * width

            for (x in 0 until width) {
                val xMin = (x - radius).coerceAtLeast(0)
                val xMax = (x + radius).coerceAtMost(width - 1)
                val centerIdx = rowOffset + x
                val centerLuma = luma[centerIdx]

                var sumWeights = 0f
                var sumFiltered = 0f

                for (ny in yMin..yMax) {
                    val dy = (ny - y).toFloat()
                    val dySq = dy * dy
                    val nRowOffset = ny * width

                    for (nx in xMin..xMax) {
                        val dx = (nx - x).toFloat()
                        val spatialDistSq = dx * dx + dySq
                        val spatialWeight = exp(-spatialDistSq / twoSpatialSigmaSq)

                        val neighborIdx = nRowOffset + nx
                        val lumaDiff = luma[neighborIdx] - centerLuma
                        val rangeWeight = exp(-(lumaDiff * lumaDiff) / twoRangeSigmaSq)

                        val weight = spatialWeight * rangeWeight
                        sumWeights += weight
                        sumFiltered += depth[neighborIdx] * weight
                    }
                }

                out[centerIdx] = if (sumWeights > 0f) sumFiltered / sumWeights else depth[centerIdx]
            }
        }
        return out
    }
}
