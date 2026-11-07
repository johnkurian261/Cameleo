package com.craftcam.app.computational

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.craftcam.app.processor.BitmapPool
import com.craftcam.app.processor.IntArrayPool
import kotlin.math.roundToInt

/**
 * Encapsulates an individual frame captured in a multi-frame burst sequence.
 * Holds compressed JPEG payload in memory (~2MB) rather than an uncompressed
 * 49MB ARGB_8888 Bitmap, guaranteeing total memory safety under high burst counts.
 */
class CapturedFrame(
    val index: Int,
    val evOffset: Float,
    val iso: Int,
    val exposureTimeNs: Long,
    val jpegBytes: ByteArray,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val timestampNs: Long
) {
    // Cached downsampled luminance buffer (1/4 resolution) for alignment and quality analysis
    var downsampledWidth: Int = 0
        private set
    var downsampledHeight: Int = 0
        private set
    private var cachedDownsampledY: FloatArray? = null

    /**
     * Extracts normalized grayscale luminance [0.0f, 1.0f] at a downsampled resolution (default sampleSize 4).
     * Decodes directly via BitmapFactory.Options.inSampleSize to avoid full-resolution heap allocation.
     */
    fun getDownsampledY(sampleSize: Int = 4): FloatArray {
        cachedDownsampledY?.let { return it }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val smallBmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts)
            ?: throw IllegalStateException("Failed to decode downsampled JPEG frame #$index")

        val sw = smallBmp.width
        val sh = smallBmp.height
        downsampledWidth = sw
        downsampledHeight = sh
        val total = sw * sh

        val rawPixels = IntArrayPool.acquire(total)
        val yArray = FloatArray(total)

        try {
            smallBmp.getPixels(rawPixels, 0, sw, 0, 0, sw, sh)
            for (i in 0 until total) {
                val p = rawPixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                // Rec. 709 linear luminance approximation for analysis
                yArray[i] = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255.0f
            }
        } finally {
            IntArrayPool.release(rawPixels)
            smallBmp.recycle()
        }

        cachedDownsampledY = yArray
        return yArray
    }

    /**
     * Decodes full-resolution image pixels into a caller-supplied IntArray.
     * Applies upright rotation, optional front-camera mirroring, and center crop in a single pass.
     * Prevents heap thrashing by recycling transient Bitmap objects immediately.
     */
    fun decodeFullPixels(
        destPixels: IntArray,
        targetWidth: Int,
        targetHeight: Int,
        applyRotation: Int = rotation,
        mirror: Boolean = false
    ) {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        val fullBmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts)
            ?: throw IllegalStateException("Failed to decode full resolution JPEG frame #$index")

        try {
            if (applyRotation == 0 && !mirror && fullBmp.width == targetWidth && fullBmp.height == targetHeight) {
                fullBmp.getPixels(destPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            } else {
                val rawW = fullBmp.width
                val rawH = fullBmp.height

                val matrix = Matrix()
                if (rawW > 4096 || rawH > 4096) {
                    val scale = 4096f / maxOf(rawW, rawH)
                    matrix.postScale(scale, scale)
                }
                if (applyRotation != 0) matrix.postRotate(applyRotation.toFloat())
                if (mirror) matrix.postScale(-1f, 1f)

                val srcBounds = RectF(0f, 0f, rawW.toFloat(), rawH.toFloat())
                val orientedBounds = RectF()
                matrix.mapRect(orientedBounds, srcBounds)

                val startX = (orientedBounds.width() - targetWidth) / 2f
                val startY = (orientedBounds.height() - targetHeight) / 2f
                matrix.postTranslate(-orientedBounds.left - startX, -orientedBounds.top - startY)

                val workingBmp = BitmapPool.acquire(targetWidth, targetHeight)
                try {
                    val canvas = Canvas(workingBmp)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(fullBmp, matrix, paint)
                    workingBmp.getPixels(destPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
                } finally {
                    BitmapPool.release(workingBmp)
                }
            }
        } finally {
            fullBmp.recycle()
        }
    }

    /**
     * Releases any cached downsampled buffers associated with this frame.
     */
    fun release() {
        cachedDownsampledY = null
    }
}

/**
 * A sequence of captured frames representing a computational burst.
 */
class FrameSequence(
    val frames: List<CapturedFrame>,
    var referenceIndex: Int = 0
) {
    val size: Int get() = frames.size
    val referenceFrame: CapturedFrame get() = frames[referenceIndex.coerceIn(0, frames.lastIndex)]

    fun release() {
        for (f in frames) {
            f.release()
        }
    }
}
