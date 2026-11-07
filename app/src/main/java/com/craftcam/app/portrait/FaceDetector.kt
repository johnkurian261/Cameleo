package com.craftcam.app.portrait

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.Face
import android.media.FaceDetector as AndroidFaceDetector
import android.util.Log

/**
 * Common contract for privacy-preserving, on-device local face detectors.
 */
interface LocalFaceDetector {
    fun getDetectedFaces(): List<FaceRegion>
}

/**
 * Hardware-accelerated Face Detector powered by Camera2 HAL ISP metadata.
 * Runs directly on the camera sensor chip at 60 FPS with zero extra CPU or battery overhead.
 */
object HardwareFaceDetector : LocalFaceDetector {
    private const val TAG = "HardwareFaceDetector"
    private val lock = Any()
    private var latestFaces: List<FaceRegion> = emptyList()

    /**
     * Ingests hardware face metadata from Camera2's [CaptureResult.STATISTICS_FACES].
     * Maps sensor pixel coordinates into normalized [0.0f, 1.0f] upright space.
     */
    fun updateFromCaptureResult(
        result: CaptureResult,
        sensorWidth: Int,
        sensorHeight: Int,
        sensorRotation: Int = 90
    ) {
        val faces: Array<Face>? = result.get(CaptureResult.STATISTICS_FACES)
        if (faces == null || faces.isEmpty()) {
            synchronized(lock) {
                latestFaces = emptyList()
            }
            return
        }

        val mapped = ArrayList<FaceRegion>(faces.size)
        val sw = sensorWidth.toFloat().coerceAtLeast(1.0f)
        val sh = sensorHeight.toFloat().coerceAtLeast(1.0f)

        for (face in faces) {
            val bounds = face.bounds
            // Normalize sensor bounds
            val leftNorm = (bounds.left / sw).coerceIn(0.0f, 1.0f)
            val topNorm = (bounds.top / sh).coerceIn(0.0f, 1.0f)
            val rightNorm = (bounds.right / sw).coerceIn(0.0f, 1.0f)
            val bottomNorm = (bounds.bottom / sh).coerceIn(0.0f, 1.0f)

            // Convert to upright display coordinates if sensor is rotated
            val normRect = if (sensorRotation == 90) {
                RectF(topNorm, 1.0f - rightNorm, bottomNorm, 1.0f - leftNorm)
            } else if (sensorRotation == 270) {
                RectF(1.0f - bottomNorm, leftNorm, 1.0f - topNorm, rightNorm)
            } else {
                RectF(leftNorm, topNorm, rightNorm, bottomNorm)
            }

            // Extract optional landmarks if available
            val leftEye = face.leftEyePosition?.let { PointF(it.x / sw, it.y / sh) }
            val rightEye = face.rightEyePosition?.let { PointF(it.x / sw, it.y / sh) }
            val mouth = face.mouthPosition?.let { PointF(it.x / sw, it.y / sh) }

            val confidence = (face.score.coerceIn(1, 100) / 100.0f)

            mapped.add(
                FaceRegion(
                    bounds = normRect,
                    confidence = confidence,
                    rotation = 0f,
                    leftEye = leftEye,
                    rightEye = rightEye,
                    mouth = mouth
                )
            )
        }

        synchronized(lock) {
            latestFaces = mapped
        }
    }

    override fun getDetectedFaces(): List<FaceRegion> {
        synchronized(lock) {
            return latestFaces
        }
    }

    fun clear() {
        synchronized(lock) {
            latestFaces = emptyList()
        }
    }
}

/**
 * Bitmap-based local face detector fallback using Android's native [android.media.FaceDetector].
 * Used during offline image development when Camera2 metadata is absent.
 */
object ImageFaceDetectorFallback {
    private const val TAG = "ImageFaceDetector"

    fun detectInBitmap(bitmap: Bitmap, maxFaces: Int = 3): List<FaceRegion> {
        return try {
            val bw = bitmap.width
            val bh = bitmap.height

            // android.media.FaceDetector requires RGB_565 config and even width
            val evenW = bw - (bw % 2)
            val rgb565Bmp = if (bitmap.config == Bitmap.Config.RGB_565 && bw == evenW) {
                bitmap
            } else {
                val temp = Bitmap.createBitmap(evenW, bh, Bitmap.Config.RGB_565)
                val canvas = android.graphics.Canvas(temp)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                temp
            }

            val detector = AndroidFaceDetector(evenW, bh, maxFaces)
            val nativeFaces = arrayOfNulls<AndroidFaceDetector.Face>(maxFaces)
            val count = detector.findFaces(rgb565Bmp, nativeFaces)

            if (rgb565Bmp != bitmap) {
                rgb565Bmp.recycle()
            }

            val results = ArrayList<FaceRegion>(count)
            val midPoint = android.graphics.PointF()

            for (i in 0 until count) {
                val f = nativeFaces[i] ?: continue
                f.getMidPoint(midPoint)
                val eyeDist = f.eyesDistance()
                val conf = f.confidence()

                // Approximate face rectangle from eye midpoint and eye distance
                val halfW = eyeDist * 1.5f
                val halfH = eyeDist * 1.8f

                val lNorm = ((midPoint.x - halfW) / evenW).coerceIn(0f, 1f)
                val tNorm = ((midPoint.y - halfH) / bh).coerceIn(0f, 1f)
                val rNorm = ((midPoint.x + halfW) / evenW).coerceIn(0f, 1f)
                val bNorm = ((midPoint.y + halfH * 1.2f) / bh).coerceIn(0f, 1f)

                results.add(
                    FaceRegion(
                        bounds = RectF(lNorm, tNorm, rNorm, bNorm),
                        confidence = conf.coerceIn(0.1f, 1.0f),
                        rotation = 0f
                    )
                )
            }
            results
        } catch (e: Throwable) {
            Log.w(TAG, "Native FaceDetector fallback failed", e)
            emptyList()
        }
    }
}
