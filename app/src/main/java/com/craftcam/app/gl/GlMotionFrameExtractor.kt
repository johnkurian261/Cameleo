package com.craftcam.app.gl

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.craftcam.app.motion.RollingMotionRingBuffer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Low-overhead secondary EGLSurface sampler attached inside [GLViewfinderRenderer].
 *
 * Strictly active only while Golf (`disp_classic`) Motion Photo mode is armed.
 * Uses asynchronous `ImageReader` blits (<0.4ms GLThread overhead) at <= 24 FPS with
 * non-blocking compression backpressure on a dedicated background thread so viewfinder
 * preview frame rates remain smooth and CameraX stays within 2 hardware streams.
 */
class GlMotionFrameExtractor {

    companion object {
        private const val TAG = "GlMotionFrameExtractor"
        private const val MIN_SAMPLE_INTERVAL_NS = 40_000_000L // ~24 FPS ceiling
        private const val LONG_EDGE_PX = 896
    }

    @Volatile
    private var ringBuffer: RollingMotionRingBuffer? = null

    @Volatile
    private var enabled = false

    private var imageReader: ImageReader? = null
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var targetW = 0
    private var targetH = 0
    private var lastFlipX = false

    private var lastSampleNs = 0L
    private val isCompressing = AtomicBoolean(false)
    private var compressThread: HandlerThread? = null
    private var compressHandler: Handler? = null

    private var reusableBitmap: Bitmap? = null
    private var reusableTightBuffer: ByteBuffer? = null
    private val reusableBaos = ByteArrayOutputStream(65536)

    private val quadNormal: FloatBuffer = createFloatBuffer(
        floatArrayOf(-1f, 1f, 0f, -1f, -1f, 0f, 1f, 1f, 0f, 1f, -1f, 0f)
    )
    private val quadFlippedX: FloatBuffer = createFloatBuffer(
        floatArrayOf(1f, 1f, 0f, 1f, -1f, 0f, -1f, 1f, 0f, -1f, -1f, 0f)
    )
    private val uvBuffer: FloatBuffer = createFloatBuffer(
        floatArrayOf(0f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)
    )

    fun configure(enabled: Boolean, ringBuffer: RollingMotionRingBuffer?) {
        this.ringBuffer = ringBuffer
        this.enabled = enabled && ringBuffer != null
    }

    fun maybeCaptureFrame(
        oesTextureId: Int,
        passthroughProgramId: Int,
        passthroughTextureLoc: Int,
        passthroughTexMatrixLoc: Int,
        texMatrix: FloatArray,
        uprightSensorW: Int,
        uprightSensorH: Int,
        flipHorizontal: Boolean
    ) {
        val targetRing = ringBuffer
        if (!enabled || targetRing == null || passthroughProgramId == 0 || uprightSensorW <= 0 || uprightSensorH <= 0) {
            if (eglSurface != EGL14.EGL_NO_SURFACE) teardownGlSurface()
            return
        }

        val nowNs = SystemClock.elapsedRealtimeNanos()
        if (nowNs - lastSampleNs < MIN_SAMPLE_INTERVAL_NS) return
        if (isCompressing.get()) return // Non-blocking backpressure: never stall GLThread

        val (capW, capH) = computeSampleDimensions(uprightSensorW, uprightSensorH)
        val display = EGL14.eglGetCurrentDisplay()
        val drawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val readSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        val context = EGL14.eglGetCurrentContext()
        if (display == EGL14.EGL_NO_DISPLAY || context == EGL14.EGL_NO_CONTEXT) return

        if (capW != targetW || capH != targetH || eglSurface == EGL14.EGL_NO_SURFACE) {
            setupSurface(display, context, capW, capH)
        }
        val surface = eglSurface
        if (surface == EGL14.EGL_NO_SURFACE) return

        try {
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                return
            }
            GLES30.glViewport(0, 0, capW, capH)
            GLES30.glUseProgram(passthroughProgramId)
            val qb = if (flipHorizontal) quadFlippedX else quadNormal
            qb.position(0)
            uvBuffer.position(0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, qb)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 8, uvBuffer)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            if (passthroughTextureLoc != -1) GLES30.glUniform1i(passthroughTextureLoc, 0)
            if (passthroughTexMatrixLoc != -1) GLES30.glUniformMatrix4fv(passthroughTexMatrixLoc, 1, false, texMatrix, 0)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDisableVertexAttribArray(1)

            lastSampleNs = nowNs
            lastFlipX = flipHorizontal
            EGL14.eglSwapBuffers(display, surface)
        } catch (t: Throwable) {
            Log.w(TAG, "Frame capture blit failed; disabling surface safely", t)
            teardownGlSurface()
        } finally {
            EGL14.eglMakeCurrent(display, drawSurface, readSurface, context)
        }
    }

    private fun setupSurface(display: EGLDisplay, context: EGLContext, w: Int, h: Int) {
        teardownGlSurface()
        try {
            ensureWorkerThread()
            val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
            reader.setOnImageAvailableListener({ r ->
                val img = try { r.acquireLatestImage() } catch (_: Throwable) { null } ?: return@setOnImageAvailableListener
                val captureTsNs = SystemClock.elapsedRealtimeNanos()
                if (!isCompressing.compareAndSet(false, true)) {
                    img.close()
                    return@setOnImageAvailableListener
                }
                try {
                    val ring = ringBuffer
                    if (enabled && ring != null) {
                        val plane = img.planes[0]
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val rowPadding = rowStride - pixelStride * w
                        val srcBuf = plane.buffer
                        srcBuf.rewind()

                        val bmp = ensureReusableBitmap(w, h)
                        if (rowPadding == 0) {
                            bmp.copyPixelsFromBuffer(srcBuf)
                        } else {
                            val tight = ensureTightBuffer(w * h * 4)
                            tight.clear()
                            val rowBytes = w * 4
                            val rowScratch = ByteArray(rowBytes)
                            for (row in 0 until h) {
                                srcBuf.position(row * rowStride)
                                srcBuf.get(rowScratch, 0, rowBytes)
                                tight.put(rowScratch)
                            }
                            tight.rewind()
                            bmp.copyPixelsFromBuffer(tight)
                        }

                        reusableBaos.reset()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, reusableBaos)
                        val bytes = reusableBaos.toByteArray()
                        ring.push(captureTsNs, w, h, bytes)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Ring JPEG compression warning: ${t.message}")
                } finally {
                    try { img.close() } catch (_: Throwable) {}
                    isCompressing.set(false)
                }
            }, compressHandler)

            val cfg = chooseMatchingConfig(display, context) ?: return
            val createdSurface = EGL14.eglCreateWindowSurface(
                display,
                cfg,
                reader.surface,
                intArrayOf(EGL14.EGL_NONE),
                0
            )
            if (createdSurface == EGL14.EGL_NO_SURFACE) {
                reader.close()
                return
            }
            imageReader = reader
            eglSurface = createdSurface
            targetW = w
            targetH = h
        } catch (t: Throwable) {
            Log.w(TAG, "Failed creating secondary ImageReader EGLSurface", t)
            teardownGlSurface()
        }
    }

    private fun chooseMatchingConfig(display: EGLDisplay, context: EGLContext): EGLConfig? {
        val cfgId = IntArray(1)
        EGL14.eglQueryContext(display, context, EGL14.EGL_CONFIG_ID, cfgId, 0)
        val byId = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (cfgId[0] > 0) {
            EGL14.eglChooseConfig(
                display,
                intArrayOf(EGL14.EGL_CONFIG_ID, cfgId[0], EGL14.EGL_NONE),
                0, byId, 0, 1, count, 0
            )
            if (count[0] > 0 && byId[0] != null) return byId[0]
        }
        val fallback = arrayOfNulls<EGLConfig>(1)
        EGL14.eglChooseConfig(
            display,
            intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
            ),
            0, fallback, 0, 1, count, 0
        )
        return if (count[0] > 0) fallback[0] else null
    }

    private fun computeSampleDimensions(srcW: Int, srcH: Int): Pair<Int, Int> {
        val scale = minOf(1f, LONG_EDGE_PX.toFloat() / maxOf(srcW, srcH))
        val w = (((srcW * scale) / 16f).toInt() * 16).coerceAtLeast(320)
        val h = (((srcH * scale) / 16f).toInt() * 16).coerceAtLeast(320)
        return w to h
    }

    private fun ensureWorkerThread() {
        if (compressThread == null) {
            val thread = HandlerThread("Cameleo-MotionRing").apply {
                isDaemon = true
                start()
            }
            compressThread = thread
            compressHandler = Handler(thread.looper)
        }
    }

    private fun ensureReusableBitmap(w: Int, h: Int): Bitmap {
        val cur = reusableBitmap
        if (cur != null && !cur.isRecycled && cur.width == w && cur.height == h) return cur
        if (cur != null && !cur.isRecycled) try { cur.recycle() } catch (_: Throwable) {}
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { reusableBitmap = it }
    }

    private fun ensureTightBuffer(capBytes: Int): ByteBuffer {
        val cur = reusableTightBuffer
        if (cur != null && cur.capacity() >= capBytes) return cur
        return ByteBuffer.allocateDirect(capBytes).also { reusableTightBuffer = it }
    }

    fun teardownGlSurface() {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            try {
                val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglDestroySurface(display, eglSurface)
                }
            } catch (_: Throwable) {}
            eglSurface = EGL14.EGL_NO_SURFACE
        }
        try { imageReader?.close() } catch (_: Throwable) {}
        imageReader = null
        targetW = 0
        targetH = 0
    }

    fun release() {
        enabled = false
        ringBuffer = null
        teardownGlSurface()
        compressThread?.quitSafely()
        compressThread = null
        compressHandler = null
        reusableBitmap?.let { if (!it.isRecycled) try { it.recycle() } catch (_: Throwable) {} }
        reusableBitmap = null
        reusableTightBuffer = null
    }

    private fun createFloatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(data)
            position(0)
        }
}
