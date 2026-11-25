package com.craftcam.app.portrait

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
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
 *
 * Handles sensor active-array coordinate mapping, crop-region scaling (digital zoom/aspect crops),
 * sensor orientation (0, 90, 180, 270 degrees), and front-camera mirroring.
 */
object HardwareFaceDetector : LocalFaceDetector {
    private const val TAG = "HardwareFaceDetector"
    private val lock = Any()
    private var latestFaces: List<FaceRegion> = emptyList()

    /**
     * Ingests hardware face metadata from Camera2's [CaptureResult.STATISTICS_FACES].
     * Maps sensor active array coordinates into normalized [0.0f, 1.0f] upright display space,
     * accounting for [CaptureResult.SCALER_CROP_REGION] when digital crop/zoom is active.
     */
    fun updateFromCaptureResult(
        result: CaptureResult,
        sensorWidth: Int,
        sensorHeight: Int,
        sensorRotation: Int = 90,
        isFrontFacing: Boolean = false
    ) {
        val faces: Array<Face>? = result.get(CaptureResult.STATISTICS_FACES)
        if (faces == null || faces.isEmpty()) {
            synchronized(lock) {
                latestFaces = emptyList()
            }
            return
        }

        // Active crop region (if present, coordinates are relative to this crop box)
        val cropRegion: Rect? = result.get(CaptureResult.SCALER_CROP_REGION)
        val cropLeft = cropRegion?.left?.toFloat() ?: 0.0f
        val cropTop = cropRegion?.top?.toFloat() ?: 0.0f
        val cropW = (cropRegion?.width()?.toFloat() ?: sensorWidth.toFloat()).coerceAtLeast(1.0f)
        val cropH = (cropRegion?.height()?.toFloat() ?: sensorHeight.toFloat()).coerceAtLeast(1.0f)

        val mapped = ArrayList<FaceRegion>(faces.size)

        for (face in faces) {
            val bounds = face.bounds

            // Map sensor coordinate into [0.0, 1.0] relative to active crop viewport
            val relLeft = ((bounds.left.toFloat() - cropLeft) / cropW).coerceIn(0.0f, 1.0f)
            val relTop = ((bounds.top.toFloat() - cropTop) / cropH).coerceIn(0.0f, 1.0f)
            val relRight = ((bounds.right.toFloat() - cropLeft) / cropW).coerceIn(0.0f, 1.0f)
            val relBottom = ((bounds.bottom.toFloat() - cropTop) / cropH).coerceIn(0.0f, 1.0f)

            // Convert to upright display coordinates according to sensor orientation
            var normRect = when (sensorRotation) {
                90 -> RectF(relTop, 1.0f - relRight, relBottom, 1.0f - relLeft)
                180 -> RectF(1.0f - relRight, 1.0f - relBottom, 1.0f - relLeft, 1.0f - relTop)
                270 -> RectF(1.0f - relBottom, relLeft, 1.0f - relTop, relRight)
                else -> RectF(relLeft, relTop, relRight, relBottom)
            }

            // Apply horizontal mirror flip for front-facing camera in preview/selfie mode
            if (isFrontFacing) {
                normRect = RectF(1.0f - normRect.right, normRect.top, 1.0f - normRect.left, normRect.bottom)
            }

            // Extract optional landmarks if available
            fun mapPoint(pt: android.graphics.Point?): PointF? {
                if (pt == null) return null
                val px = ((pt.x.toFloat() - cropLeft) / cropW).coerceIn(0.0f, 1.0f)
                val py = ((pt.y.toFloat() - cropTop) / cropH).coerceIn(0.0f, 1.0f)
                val upright = when (sensorRotation) {
                    90 -> PointF(py, 1.0f - px)
                    180 -> PointF(1.0f - px, 1.0f - py)
                    270 -> PointF(1.0f - py, px)
                    else -> PointF(px, py)
                }
                return if (isFrontFacing) PointF(1.0f - upright.x, upright.y) else upright
            }

            val leftEye = mapPoint(face.leftEyePosition)
            val rightEye = mapPoint(face.rightEyePosition)
            val mouth = mapPoint(face.mouthPosition)

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
