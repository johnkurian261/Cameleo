package com.craftcam.app.gl

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
import com.craftcam.app.portrait.FaceRegion
import com.craftcam.app.thermal.ThermalMaskSnapshot
import com.craftcam.app.thermal.ThermalSubjectEstimator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Lightweight secondary EGLSurface sampler running strictly while `thermal_vision` is active.
 *
 * Samples a tiny 128x96 RGBA surface every ~100ms (~10 Hz) on a background HandlerThread with
 * non-blocking AtomicBoolean backpressure so the live GL viewfinder never drops frames or stalls.
 * Updates a temporally smoothed [ThermalMaskSnapshot] shared lock-free with GL preview & video.
 */
class GlThermalMaskExtractor {

    companion object {
        private const val TAG = "GlThermalMaskExtractor"
        private const val SAMPLE_INTERVAL_NS = 95_000_000L // ~10.5 Hz scene/human mask update
        private const val MASK_W = ThermalSubjectEstimator.DEFAULT_MASK_WIDTH
        private const val MASK_H = ThermalSubjectEstimator.DEFAULT_MASK_HEIGHT
    }

    private val estimator = ThermalSubjectEstimator(MASK_W, MASK_H)
    private val latestSnapshot = AtomicReference<ThermalMaskSnapshot?>(null)
    private val snapshotSeq = AtomicLong(0L)

    @Volatile
    private var enabled = false

    @Volatile
    private var segmentationEnabled = true

    @Volatile
    private var latestFaces: List<FaceRegion> = emptyList()

    private var imageReader: ImageReader? = null
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var lastSampleNs = 0L
    private val isAnalyzing = AtomicBoolean(false)
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var tightRgbaBuffer: ByteBuffer? = null
    private val rowScratch = ByteArray(MASK_W * 4)

    private val quadNormal: FloatBuffer = createFloatBuffer(
        floatArrayOf(-1f, 1f, 0f, -1f, -1f, 0f, 1f, 1f, 0f, 1f, -1f, 0f)
    )
    private val quadFlippedX: FloatBuffer = createFloatBuffer(
        floatArrayOf(1f, 1f, 0f, 1f, -1f, 0f, -1f, 1f, 0f, -1f, -1f, 0f)
    )
    private val uvBuffer: FloatBuffer = createFloatBuffer(
        floatArrayOf(0f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)
    )

    fun configure(enabled: Boolean, segmentationEnabled: Boolean) {
        val wasEnabled = this.enabled
        this.enabled = enabled
        this.segmentationEnabled = segmentationEnabled
        if (!enabled && wasEnabled) {
            estimator.resetTemporalState()
            latestSnapshot.set(null)
            snapshotSeq.incrementAndGet()
        }
    }

    fun updateFaces(faces: List<FaceRegion>) {
        latestFaces = faces
    }

    fun resetTemporalHistory() {
        estimator.resetTemporalState()
        latestSnapshot.set(null)
        snapshotSeq.incrementAndGet()
    }

    fun getLatestSnapshot(): ThermalMaskSnapshot? = latestSnapshot.get()
    fun getSnapshotSequence(): Long = snapshotSeq.get()

    fun maybeSampleFrame(
        oesTextureId: Int,
        passthroughProgramId: Int,
        passthroughTextureLoc: Int,
        passthroughTexMatrixLoc: Int,
        texMatrix: FloatArray,
        flipHorizontal: Boolean
    ) {
        if (!enabled || passthroughProgramId == 0) {
            if (eglSurface != EGL14.EGL_NO_SURFACE) teardownGlSurface()
            return
        }

        val nowNs = SystemClock.elapsedRealtimeNanos()
        if (nowNs - lastSampleNs < SAMPLE_INTERVAL_NS) return
        if (isAnalyzing.get()) return

        val display = EGL14.eglGetCurrentDisplay()
        val drawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val readSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        val context = EGL14.eglGetCurrentContext()
        if (display == EGL14.EGL_NO_DISPLAY || context == EGL14.EGL_NO_CONTEXT) return

        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            setupSurface(display, context)
        }
        val surface = eglSurface
        if (surface == EGL14.EGL_NO_SURFACE) return

        try {
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return

            GLES30.glViewport(0, 0, MASK_W, MASK_H)
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
            EGL14.eglSwapBuffers(display, surface)
        } catch (t: Throwable) {
            Log.w(TAG, "Thermal mask sample blit failed; tearing down surface", t)
            teardownGlSurface()
        } finally {
            EGL14.eglMakeCurrent(display, drawSurface, readSurface, context)
        }
    }

    private fun setupSurface(display: EGLDisplay, context: EGLContext) {
        teardownGlSurface()
        try {
            ensureWorkerThread()
            val reader = ImageReader.newInstance(MASK_W, MASK_H, PixelFormat.RGBA_8888, 2)
            reader.setOnImageAvailableListener({ r ->
                val img = try { r.acquireLatestImage() } catch (_: Throwable) { null } ?: return@setOnImageAvailableListener
                if (!isAnalyzing.compareAndSet(false, true)) {
                    img.close()
                    return@setOnImageAvailableListener
                }
                try {
                    if (enabled) {
                        val plane = img.planes[0]
                        val srcBuf = plane.buffer
                        srcBuf.rewind()
                        val rowStride = plane.rowStride
                        val rowBytes = MASK_W * 4
                        val tight = ensureTightBuffer()
                        tight.clear()
                        if (rowStride == rowBytes) {
                            tight.put(srcBuf)
                        } else {
                            for (row in 0 until MASK_H) {
                                srcBuf.position(row * rowStride)
                                srcBuf.get(rowScratch, 0, rowBytes)
                                tight.put(rowScratch, 0, rowBytes)
                            }
                        }
                        tight.flip()

                        val snapshot = estimator.analyzeRgbaFrame(
                            rgbaBytes = tight,
                            width = MASK_W,
                            height = MASK_H,
                            faces = latestFaces,
                            segmentationEnabled = segmentationEnabled,
                            temporalSmoothing = true
                        )
                        latestSnapshot.set(snapshot)
                        snapshotSeq.incrementAndGet()
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Thermal mask analysis warning: ${t.message}")
                } finally {
                    try { img.close() } catch (_: Throwable) {}
                    isAnalyzing.set(false)
                }
            }, workerHandler)

            val cfg = chooseMatchingConfig(display, context) ?: return
            val createdSurface = EGL14.eglCreateWindowSurface(
                display, cfg, reader.surface, intArrayOf(EGL14.EGL_NONE), 0
            )
            if (createdSurface == EGL14.EGL_NO_SURFACE) {
                reader.close()
                return
            }
            imageReader = reader
            eglSurface = createdSurface
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize thermal mask EGLSurface", t)
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
                EGL14.EGL_RENDERABLE_TYPE, 4,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
            ),
            0, fallback, 0, 1, count, 0
        )
        return fallback[0]
    }

    fun teardownGlSurface() {
        try {
            val display = EGL14.eglGetCurrentDisplay()
            if (eglSurface != EGL14.EGL_NO_SURFACE && display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglDestroySurface(display, eglSurface)
            }
        } catch (_: Throwable) {}
        eglSurface = EGL14.EGL_NO_SURFACE
        try { imageReader?.close() } catch (_: Throwable) {}
        imageReader = null
    }

    fun release() {
        enabled = false
        latestSnapshot.set(null)
        teardownGlSurface()
        try {
            workerThread?.quitSafely()
        } catch (_: Throwable) {}
        workerThread = null
        workerHandler = null
        estimator.resetTemporalState()
    }

    private fun ensureWorkerThread() {
        if (workerThread == null) {
            val ht = HandlerThread("CameleoThermalMaskThread").apply { start() }
            workerThread = ht
            workerHandler = Handler(ht.looper)
        }
    }

    private fun ensureTightBuffer(): ByteBuffer {
        val needed = MASK_W * MASK_H * 4
        val existing = tightRgbaBuffer
        if (existing != null && existing.capacity() == needed) return existing
        return ByteBuffer.allocateDirect(needed).order(ByteOrder.nativeOrder()).also {
            tightRgbaBuffer = it
        }
    }

    private fun createFloatBuffer(arr: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(arr.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(arr)
            .apply { position(0) }
}
