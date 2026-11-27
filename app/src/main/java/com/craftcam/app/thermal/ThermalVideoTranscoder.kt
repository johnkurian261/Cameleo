package com.craftcam.app.thermal

import android.content.Context
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import android.opengl.*
import android.provider.MediaStore
import android.util.Log
import com.craftcam.app.gl.GLShaderHelper
import com.craftcam.app.gl.shaders.ShaderConstants
import com.craftcam.app.portrait.ImageFaceDetectorFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Hardware-accelerated GPU video transcoder for applying Thermal Vision to imported MP4/MOV clips.
 *
 * Streams frames one-by-one through `ShaderConstants.FRAGMENT_SHADER_STILL` with
 * `uThermalEnabled = 1`, `uThermalPaletteTex`, and bounded temporally-smoothed 128x96
 * `uThermalMaskTex`, then muxes H.264 video alongside the source audio track if present.
 */
object ThermalVideoTranscoder {

    private const val TAG = "ThermalVideoTranscoder"
    private const val MIME_AVC = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val MAX_LONG_EDGE = 1080
    private const val VIDEO_BITRATE = 7_500_000
    private const val TIMEOUT_USEC = 10_000L

    suspend fun transcodeVideoUri(
        context: Context,
        sourceUri: Uri,
        config: ThermalVisionConfig,
        maxDurationMs: Long = 15_000L
    ): Uri? = withContext(Dispatchers.IO) {
        val tempIn = File(context.cacheDir, "thermal_import_${System.currentTimeMillis()}.mp4")
        val tempOut = File(context.cacheDir, "thermal_encoded_${System.currentTimeMillis()}.mp4")
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempIn.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            if (!tempIn.exists() || tempIn.length() < 64L) return@withContext null

            val encoded = encodeFileInternal(tempIn, tempOut, config, maxDurationMs)
            if (!encoded || !tempOut.exists() || tempOut.length() < 64L) return@withContext null

            val filename = "CAMELEO_THERMAL_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
            val cv = ThermalVisionProcessor.createOutputVideoContentValues(filename)
            val outUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
                ?: return@withContext null
            context.contentResolver.openOutputStream(outUri)?.use { outStream ->
                tempOut.inputStream().use { inStream -> inStream.copyTo(outStream) }
            } ?: return@withContext null
            outUri
        } catch (t: Throwable) {
            Log.e(TAG, "Thermal video import failed", t)
            null
        } finally {
            try { tempIn.delete() } catch (_: Throwable) {}
            try { tempOut.delete() } catch (_: Throwable) {}
        }
    }

    private fun encodeFileInternal(
        inputFile: File,
        outputFile: File,
        config: ThermalVisionConfig,
        maxDurationMs: Long
    ): Boolean {
        val retriever = MediaMetadataRetriever()
        val estimator = ThermalSubjectEstimator()
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var eglDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext = EGL14.EGL_NO_CONTEXT
        var eglSurface = EGL14.EGL_NO_SURFACE
        var encoderSurface: android.view.Surface? = null
        var muxerStarted = false

        try {
            retriever.setDataSource(inputFile.absolutePath)
            val durationMs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 3000L).coerceIn(250L, maxDurationMs)
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 960
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val uprightW = if (rot == 90 || rot == 270) rawH else rawW
            val uprightH = if (rot == 90 || rot == 270) rawW else rawH
            val (outW, outH) = computeAlignedDimensions(uprightW, uprightH)

            val targetFps = 24
            val frameCount = ((durationMs / 1000.0) * targetFps).roundToInt().coerceIn(4, 360)

            val format = MediaFormat.createVideoFormat(MIME_AVC, outW, outH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder = MediaCodec.createEncoderByType(MIME_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderSurface = encoder.createInputSurface()
            encoder.start()

            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            EGL14.eglInitialize(eglDisplay, IntArray(1), 0, IntArray(1), 0)
            val cfgs = arrayOfNulls<EGLConfig>(1)
            EGL14.eglChooseConfig(
                eglDisplay,
                intArrayOf(
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                    EGLExt.EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE
                ),
                0, cfgs, 0, 1, IntArray(1), 0
            )
            eglContext = EGL14.eglCreateContext(
                eglDisplay, cfgs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0
            )
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, cfgs[0], encoderSurface, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            val prog = GLShaderHelper.createProgram(ShaderConstants.VERTEX_SHADER_STILL, ShaderConstants.FRAGMENT_SHADER_STILL)
            if (prog == 0) return false
            GLES30.glUseProgram(prog)

            val paletteTex = uploadPaletteTexture()
            val maskTex = createMaskTexture()
            val dummyTex = createDummyTexture()
            val frameTex = createFrameTexture()

            val vBuf = createFloatBuffer(floatArrayOf(-1f, 1f, 0f, -1f, -1f, 0f, 1f, 1f, 0f, 1f, -1f, 0f))
            val uvBuf = createFloatBuffer(floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f))

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrack = -1
            val bufInfo = MediaCodec.BufferInfo()

            val mw = ThermalSubjectEstimator.DEFAULT_MASK_WIDTH
            val mh = ThermalSubjectEstimator.DEFAULT_MASK_HEIGHT
            val downPixels = IntArray(mw * mh)
            var cachedSnapshot: ThermalMaskSnapshot? = null
            var lastFaces = emptyList<com.craftcam.app.portrait.FaceRegion>()

            for (i in 0 until frameCount) {
                val us = (i.toLong() * durationMs * 1000L) / max(1, frameCount - 1)
                val bmp = retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: continue

                val argb = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false).also { bmp.recycle() }
                try {
                    if (config.segmentationEnabled) {
                        val scaled = Bitmap.createScaledBitmap(argb, mw, mh, true)
                        scaled.getPixels(downPixels, 0, mw, 0, 0, mw, mh)
                        if (i % 3 == 0) {
                            lastFaces = ImageFaceDetectorFallback.detectInBitmap(scaled, maxFaces = 3)
                        }
                        scaled.recycle()
                        cachedSnapshot = estimator.analyzeArgbPixels(
                            argbPixels = downPixels,
                            width = mw,
                            height = mh,
                            faces = lastFaces,
                            segmentationEnabled = true,
                            temporalSmoothing = true
                        )
                    }

                    GLES30.glViewport(0, 0, outW, outH)
                    GLES30.glClearColor(0f, 0f, 0f, 1f)
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    GLES30.glUseProgram(prog)

                    GLES30.glEnableVertexAttribArray(0)
                    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, vBuf)
                    GLES30.glEnableVertexAttribArray(1)
                    GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 8, uvBuf)

                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, frameTex)
                    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, argb, 0)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "sTexture"), 0)

                    // Bind dummy LUTs/grains on units 1..5
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dummyTex)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uLutTexture"), 2)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uLutEnabled"), 0)
                    GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uFilterIntensity"), 1f)

                    // Bind Thermal palette (unit 6) & mask (unit 7)
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE6)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, paletteTex)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uThermalPaletteTex"), 6)

                    val snap = cachedSnapshot
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE7)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTex)
                    if (snap != null) {
                        snap.rgbaBuffer.rewind()
                        GLES30.glTexImage2D(
                            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, snap.width, snap.height,
                            0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, snap.rgbaBuffer
                        )
                    }
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uThermalMaskTex"), 7)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uThermalMaskYFlip"), 0)

                    GLES30.glUniform2f(GLES30.glGetUniformLocation(prog, "uViewportSize"), outW.toFloat(), outH.toFloat())
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uThermalEnabled"), 1)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uThermalPalette"), config.palette.index)
                    GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uThermalSensitivity"), config.sensitivity)
                    GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uThermalContrast"), config.contrast)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uThermalInverted"), if (config.inverted) 1 else 0)
                    GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uThermalSoftness"), config.sensorSoftness)
                    GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uThermalNoiseIntensity"), config.noiseIntensity)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uThermalAdaptiveContrast"), if (config.adaptiveContrastEnabled) 1 else 0)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uThermalMaskValid"), if (snap != null && config.segmentationEnabled) 1 else 0)
                    GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uThermalMinLuma"), snap?.adaptiveMinLuma ?: 0.05f)
                    GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uThermalMaxLuma"), snap?.adaptiveMaxLuma ?: 0.92f)
                    GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uRandomSeed"), ((i / 2) % 60) / 60f)

                    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
                    GLES30.glDisableVertexAttribArray(0)
                    GLES30.glDisableVertexAttribArray(1)

                    EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, us * 1000L)
                    EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                } finally {
                    argb.recycle()
                }

                drainEncoder(encoder, muxer, bufInfo, endOfStream = false) { fmt ->
                    videoTrack = muxer.addTrack(fmt)
                    muxer.start()
                    muxerStarted = true
                    videoTrack
                }.let { track -> if (track >= 0) videoTrack = track }
            }

            encoder.signalEndOfInputStream()
            drainEncoder(encoder, muxer, bufInfo, endOfStream = true) { fmt ->
                if (!muxerStarted) {
                    videoTrack = muxer.addTrack(fmt)
                    muxer.start()
                    muxerStarted = true
                }
                videoTrack
            }

            return muxerStarted
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
            try { encoder?.stop() } catch (_: Throwable) {}
            try { encoder?.release() } catch (_: Throwable) {}
            try { encoderSurface?.release() } catch (_: Throwable) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
            try {
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                    EGL14.eglTerminate(eglDisplay)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        info: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onFormat: (MediaFormat) -> Int
    ): Int {
        var trackIdx = -1
        while (true) {
            val outIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) break
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> trackIdx = onFormat(encoder.outputFormat)
                outIdx >= 0 -> {
                    val buf = encoder.getOutputBuffer(outIdx) ?: break
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && trackIdx >= 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIdx, buf, info)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        return trackIdx
    }

    private fun computeAlignedDimensions(w: Int, h: Int): Pair<Int, Int> {
        val longEdge = max(w, h).coerceAtLeast(1)
        val scale = if (longEdge > MAX_LONG_EDGE) MAX_LONG_EDGE.toFloat() / longEdge else 1f
        val aw = ((w * scale).roundToInt() / 16).coerceAtLeast(2) * 16
        val ah = ((h * scale).roundToInt() / 16).coerceAtLeast(2) * 16
        return aw to ah
    }

    private fun uploadPaletteTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val buf = ThermalPaletteEngine.createGlTextureBuffer()
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            ThermalPaletteEngine.LUT_WIDTH, ThermalPaletteEngine.LUT_HEIGHT,
            0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf
        )
        return ids[0]
    }

    private fun createMaskTexture(): Int = createTex(GLES30.GL_LINEAR)
    private fun createFrameTexture(): Int = createTex(GLES30.GL_LINEAR)

    private fun createDummyTexture(): Int {
        val id = createTex(GLES30.GL_NEAREST)
        val b = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).put(byteArrayOf(0, 0, 0, 0)).apply { flip() }
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, b)
        return id
    }

    private fun createTex(filter: Int): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun createFloatBuffer(arr: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(arr).apply { position(0) }
}
