package com.craftcam.app.thermal

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.craftcam.app.portrait.FaceRegion
import com.craftcam.app.portrait.ImageFaceDetectorFallback
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Authoritative Thermal Vision CPU image processor used for:
 * - Still camera captures (`ImageProcessor.processAndSaveImage`)
 * - Gallery photo import (`GalleryScreen`)
 * - Darkroom photo redevelopment (`GalleryScreen`)
 *
 * Mirrors the exact mathematical pipeline of the OpenGL ES thermal shader
 * (`uThermalPaletteTex` + `ThermalSubjectEstimator`) and explicitly bypasses film LUTs,
 * skin tone vintage grading, grain plates, halation, and vintage paper overlays.
 */
object ThermalVisionProcessor {

    fun developBitmapInPlace(
        bitmap: Bitmap,
        config: ThermalVisionConfig,
        faces: List<FaceRegion> = emptyList(),
        precomputedSnapshot: ThermalMaskSnapshot? = null
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val total = w * h
        if (total == 0) return bitmap

        val pixels = com.craftcam.app.processor.IntArrayPool.acquire(total)
        try {
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            developArgbPixelsInPlace(
                pixels = pixels,
                width = w,
                height = h,
                config = config,
                faces = faces,
                precomputedSnapshot = precomputedSnapshot
            )
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            return bitmap
        } finally {
            com.craftcam.app.processor.IntArrayPool.release(pixels)
        }
    }

    /**
     * Pure JVM-safe ARGB pixel development matching the GLSL thermal shader.
     * Mutates [pixels] in place across [width] * [height] elements.
     */
    fun developArgbPixelsInPlace(
        pixels: IntArray,
        width: Int,
        height: Int,
        config: ThermalVisionConfig,
        faces: List<FaceRegion> = emptyList(),
        precomputedSnapshot: ThermalMaskSnapshot? = null
    ) {
        val w = width
        val h = height
        val total = w * h
        if (total == 0 || pixels.size < total) return

        val lumaBuf = com.craftcam.app.processor.FloatArrayPool.acquire(total)
        try {
            // 1. Precompute base luma in parallel stripes
            runRowStripes(h, total) { startY, endY ->
                for (y in startY until endY) {
                    val row = y * w
                    for (x in 0 until w) {
                        val c = pixels[row + x]
                        val r = ((c ushr 16) and 0xFF) / 255f
                        val g = ((c ushr 8) and 0xFF) / 255f
                        val b = (c and 0xFF) / 255f
                        lumaBuf[row + x] = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    }
                }
            }

            // 2. Obtain reduced-resolution human/scene thermal guidance mask (max 128x96)
            val snapshot = precomputedSnapshot ?: estimateThermalSnapshot(
                pixels = pixels,
                width = w,
                height = h,
                faces = faces,
                segmentationEnabled = config.segmentationEnabled
            )

            val maskW = snapshot.width
            val maskH = snapshot.height
            val humanMask = snapshot.humanMask
            val skinMask = snapshot.skinMask
            val minLuma = if (config.adaptiveContrastEnabled) snapshot.adaptiveMinLuma else 0f
            val maxLuma = if (config.adaptiveContrastEnabled) snapshot.adaptiveMaxLuma else 1f
            val lumaSpan = max(0.28f, maxLuma - minLuma)

            val softness = config.sensorSoftness.coerceIn(0f, 1f)
            val sensitivity = config.sensitivity.coerceIn(0.4f, 2.2f)
            val gainShift = (sensitivity - 1.0f) * 0.26f
            val contrast = config.contrast.coerceIn(0.5f, 2.0f)
            val noiseAmp = config.noiseIntensity.coerceIn(0f, 0.15f)
            val palette = config.palette
            val inverted = config.inverted
            val hasMask = config.segmentationEnabled && maskW > 1 && maskH > 1

            // Microbolometer neighbor step scaled proportionally to working resolution
            val blurStep = max(1, min(w, h) / 480)
            val invWMinus1 = 1f / max(1, w - 1)
            val invHMinus1 = 1f / max(1, h - 1)

            // 3. Evaluate thermal heat map & smooth palette lookup in parallel rows
            runRowStripes(h, total) { startY, endY ->
                val outRgb = FloatArray(3)
                for (y in startY until endY) {
                    val row = y * w
                    val yUp = max(0, y - blurStep) * w
                    val yDn = min(h - 1, y + blurStep) * w
                    val ny = y * invHMinus1
                    val myf = ny * (maskH - 1)
                    val my0 = myf.toInt().coerceIn(0, maskH - 2)
                    val my1 = my0 + 1
                    val mfy = (myf - my0).coerceIn(0f, 1f)

                    for (x in 0 until w) {
                        val idx = row + x
                        val c = pixels[idx]
                        val r = ((c ushr 16) and 0xFF) / 255f
                        val g = ((c ushr 8) and 0xFF) / 255f
                        val b = (c and 0xFF) / 255f

                        val baseLuma = lumaBuf[idx]
                        val xL = max(0, x - blurStep)
                        val xR = min(w - 1, x + blurStep)
                        val blurredLuma = 0.25f * (
                            lumaBuf[row + xL] +
                                lumaBuf[row + xR] +
                                lumaBuf[yUp + x] +
                                lumaBuf[yDn + x]
                            )
                        val detailEdge = (baseLuma - blurredLuma) * 0.22f
                        val sharpLuma = baseLuma + detailEdge
                        val radLuma = sharpLuma + (blurredLuma - sharpLuma) * softness

                        val normLuma = ((radLuma - minLuma) / lumaSpan).coerceIn(0f, 1f)
                        val warmBias = ((r - b) * 0.14f).coerceIn(-0.06f, 0.11f)

                        var heat: Float
                        if (hasMask) {
                            val nx = x * invWMinus1
                            val mxf = nx * (maskW - 1)
                            val mx0 = mxf.toInt().coerceIn(0, maskW - 2)
                            val mx1 = mx0 + 1
                            val mfx = (mxf - mx0).coerceIn(0f, 1f)

                            val mOff00 = my0 * maskW + mx0
                            val mOff01 = my0 * maskW + mx1
                            val mOff10 = my1 * maskW + mx0
                            val mOff11 = my1 * maskW + mx1

                            val h0 = humanMask[mOff00] + (humanMask[mOff01] - humanMask[mOff00]) * mfx
                            val h1 = humanMask[mOff10] + (humanMask[mOff11] - humanMask[mOff10]) * mfx
                            val humanConf = h0 + (h1 - h0) * mfy

                            val s0 = skinMask[mOff00] + (skinMask[mOff01] - skinMask[mOff00]) * mfx
                            val s1 = skinMask[mOff10] + (skinMask[mOff11] - skinMask[mOff10]) * mfx
                            val skinConf = s0 + (s1 - s0) * mfy

                            val bgHeat = (normLuma * 0.74f - 0.05f + warmBias * 0.65f).coerceIn(0f, 0.92f)
                            val subjInternal = 0.42f + (0.92f - 0.42f) * normLuma
                            val subjHeat = (subjInternal + 0.14f * skinConf + warmBias * 0.45f).coerceIn(0f, 0.98f)
                            heat = bgHeat + (subjHeat - bgHeat) * humanConf
                        } else {
                            val skinChromaWarm = if (baseLuma > 0.15f) {
                                (((r - g) * 0.45f + (r - b) * 0.20f).coerceIn(0f, 0.25f)) *
                                    smoothstep(0.15f, 0.75f, baseLuma)
                            } else 0f
                            heat = (normLuma * 0.92f + warmBias + skinChromaWarm * 0.35f).coerceIn(0f, 1f)
                        }

                        heat = softHeatCompress((heat - 0.5f) * contrast + 0.5f + gainShift)

                        // Fine electronic bolometer noise + anti-banding dither
                        val hash = deterministicHash(x, y, 17)
                        if (noiseAmp > 0.001f) {
                            val colPattern = deterministicHash(x / 2, 1, 7) * 0.18f
                            heat = (heat + (hash + colPattern) * noiseAmp).coerceIn(0f, 1f)
                        }

                        ThermalPaletteEngine.sampleRgbNormalized(palette, heat, inverted, outRgb)

                        val dither = hash * (0.85f / 255f)
                        val r8 = ((outRgb[0] + dither).coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                        val g8 = ((outRgb[1] + dither).coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                        val b8 = ((outRgb[2] + dither).coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                        pixels[idx] = (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
                    }
                }
            }
        } finally {
            com.craftcam.app.processor.FloatArrayPool.release(lumaBuf)
        }
    }

    private inline fun runRowStripes(
        height: Int,
        totalPixels: Int,
        crossinline block: (startY: Int, endY: Int) -> Unit
    ) {
        if (height <= 0) return
        val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 6)
        if (workers == 1 || totalPixels < 40_000) {
            block(0, height)
            return
        }
        val chunk = (height + workers - 1) / workers
        val pool = java.util.concurrent.ForkJoinPool.commonPool()
        val tasks = ArrayList<java.util.concurrent.Future<*>>(workers)
        var y = 0
        while (y < height) {
            val s = y
            val e = min(height, s + chunk)
            y = e
            tasks.add(pool.submit { block(s, e) })
        }
        for (task in tasks) {
            task.get()
        }
    }

    private fun estimateThermalSnapshot(
        pixels: IntArray,
        width: Int,
        height: Int,
        faces: List<FaceRegion>,
        segmentationEnabled: Boolean
    ): ThermalMaskSnapshot {
        val mw = ThermalSubjectEstimator.DEFAULT_MASK_WIDTH
        val mh = ThermalSubjectEstimator.DEFAULT_MASK_HEIGHT
        val downPixels = IntArray(mw * mh)
        for (y in 0 until mh) {
            val sy = ((y.toFloat() / max(1, mh - 1)) * (height - 1)).toInt().coerceIn(0, height - 1)
            val srcRow = sy * width
            val dstRow = y * mw
            for (x in 0 until mw) {
                val sx = ((x.toFloat() / max(1, mw - 1)) * (width - 1)).toInt().coerceIn(0, width - 1)
                downPixels[dstRow + x] = pixels[srcRow + sx]
            }
        }

        val resolvedFaces = if (faces.isNotEmpty() || !segmentationEnabled) {
            faces
        } else {
            // Run fast fallback face detector ONLY for offline still capture / gallery import
            val bmpSmall = Bitmap.createBitmap(downPixels, mw, mh, Bitmap.Config.ARGB_8888)
            try {
                ImageFaceDetectorFallback.detectInBitmap(bmpSmall, maxFaces = 4)
            } finally {
                bmpSmall.recycle()
            }
        }

        val estimator = ThermalSubjectEstimator(mw, mh)
        return estimator.analyzeArgbPixels(
            argbPixels = downPixels,
            width = mw,
            height = mh,
            faces = resolvedFaces,
            segmentationEnabled = segmentationEnabled,
            temporalSmoothing = false
        )
    }

    fun softHeatCompress(v: Float): Float {
        if (v <= 0f) return 0f
        if (v >= 1f) return 1f
        return when {
            v < 0.06f -> {
                val t = v / 0.06f
                0.06f * (t * (0.75f + 0.25f * t))
            }
            v > 0.94f -> {
                val ex = (v - 0.94f) / 0.06f
                0.94f + 0.06f * (ex / (1f + 0.35f * (1f - ex)))
            }
            else -> v
        }.coerceIn(0f, 1f)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / max(1e-6f, edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun deterministicHash(x: Int, y: Int, seed: Int): Float {
        var n = x * 374761393 + y * 668265263 + seed * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return ((n and 0xFFFF) / 65535.0f) - 0.5f
    }

    /**
     * Transcodes a user-picked video (.mp4 / .mov) into CAMELEO's gallery using the Thermal shader
     * pipeline when importing videos with Thermal Vision active.
     */
    fun createOutputVideoContentValues(filename: String): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Cameleo")
        }
    }
}
