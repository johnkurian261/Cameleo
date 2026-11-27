package com.craftcam.app.motion

import android.content.Context
import android.graphics.*
import android.media.*
import android.opengl.*
import android.os.SystemClock
import android.util.Log
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.camera.profile.CameraProfileCatalog
import com.craftcam.app.camera.profile.LensProfile
import com.craftcam.app.camera.profile.LensType
import com.craftcam.app.camera.profile.LutInputSpace
import com.craftcam.app.color.profile.CameraProfileCompatibility
import com.craftcam.app.gl.GLShaderHelper
import com.craftcam.app.gl.shaders.ShaderConstants
import com.craftcam.app.model.BorderStyle
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.model.GrainBlendMode
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.processor.BorderRenderer
import com.craftcam.app.processor.DateStampRenderer
import com.craftcam.app.processor.GrainStage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Streaming source abstraction so motion capture frames and imported videos decode
 * strictly one-at-a-time without retaining large uncompressed Bitmap sequences in memory.
 */
interface MotionFrameSource : AutoCloseable {
    val frameCount: Int
    val sourceWidth: Int
    val sourceHeight: Int
    fun timestampNs(index: Int): Long
    fun loadBitmap(index: Int): Bitmap?
}

class EncodedJpegRingSource(
    private val frames: List<EncodedMotionFrame>
) : MotionFrameSource {
    override val frameCount: Int get() = frames.size
    override val sourceWidth: Int get() = frames.firstOrNull()?.width ?: 0
    override val sourceHeight: Int get() = frames.firstOrNull()?.height ?: 0

    override fun timestampNs(index: Int): Long = frames[index].timestampNs

    override fun loadBitmap(index: Int): Bitmap? {
        val bytes = frames.getOrNull(index)?.jpegBytes ?: return null
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    override fun close() {}
}

class VideoClipFrameSource(
    private val videoFile: File,
    maxDurationMs: Long,
    targetFps: Int
) : MotionFrameSource {
    private val retriever = MediaMetadataRetriever()
    private val timestampsUs: List<Long>
    override val sourceWidth: Int
    override val sourceHeight: Int

    init {
        retriever.setDataSource(videoFile.absolutePath)
        val rawDurMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 2000L
        val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val wMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 960
        val hMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
        if (rot == 90 || rot == 270) {
            sourceWidth = hMeta
            sourceHeight = wMeta
        } else {
            sourceWidth = wMeta
            sourceHeight = hMeta
        }

        val effectiveMs = rawDurMs.coerceIn(250L, maxDurationMs)
        val fps = targetFps.coerceIn(12, 30)
        val count = ((effectiveMs / 1000.0) * fps).roundToInt().coerceIn(2, 90)
        timestampsUs = List(count) { idx ->
            if (count <= 1) 0L else ((idx.toLong() * effectiveMs * 1000L) / (count - 1))
        }
    }

    override val frameCount: Int get() = timestampsUs.size

    override fun timestampNs(index: Int): Long = timestampsUs[index] * 1000L

    override fun loadBitmap(index: Int): Bitmap? {
        val us = timestampsUs.getOrNull(index) ?: return null
        return retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    override fun close() {
        try { retriever.release() } catch (_: Throwable) {}
    }
}

/**
 * Hardware-accelerated GPU video encoder for CAMELEO Golf Motion Photos.
 *
 * Uses the EXACT untouched `ShaderConstants.FRAGMENT_SHADER_STILL` + `CameraProfileCatalog` +
 * `CameraProfileCompatibility.resolveLutPlan(...)` + `GrainStage.resolve(...)` so Golf's color
 * science, LUT, grain, contrast, skin tone, and highlights match preview and still output cleanly.
 *
 * GPU Pipeline inside offscreen EGL ES 3.0 context:
 * 1. Pass A (FBO): Center-crops frame to exact user AspectRatioMode, interpolates crossfade
 *    across frame transitions, and applies controlled linear temporal EV modulation
 *    (micro-flicker + optional shutter-synchronized brief flash envelope).
 * 2. Pass B (MediaCodec Surface): Executes `ShaderConstants.FRAGMENT_SHADER_STILL` once.
 * 3. Pass C (Overlay): Alpha-blends optional static Golf border & orange 7-segment date stamp.
 * 4. Optional AAC-LC audio muxed alongside H.264/AVC in MediaMuxer.
 */
class GolfMotionEncoder {

    companion object {
        private const val TAG = "GolfMotionEncoder"
        private const val MIME_AVC = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val MIME_AAC = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val MAX_LONG_EDGE = 1080
        private const val VIDEO_BITRATE = 7_500_000
        private const val TIMEOUT_USEC = 10_000L

        private const val PREPASS_VERT = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTextureCoord;
out vec2 vTexCoord;
void main() {
    gl_Position = aPosition;
    vTexCoord = aTextureCoord;
}
"""

        private const val PREPASS_FRAG = """#version 300 es
precision highp float;
uniform sampler2D uCurrTex;
uniform sampler2D uPrevTex;
uniform float uHasPrev;
uniform float uCrossfade;
uniform float uExposureScale;
uniform vec4 uCropRect; // (left, top, width, height) in normalized UVs
in vec2 vTexCoord;
out vec4 fragColor;
void main() {
    vec2 uv = clamp(uCropRect.xy + vTexCoord * uCropRect.zw, vec2(0.001), vec2(0.999));
    vec3 curr = texture(uCurrTex, uv).rgb;
    vec3 col = curr;
    if (uHasPrev > 0.5 && uCrossfade > 0.001) {
        vec3 prev = texture(uPrevTex, uv).rgb;
        col = mix(curr, prev, clamp(uCrossfade, 0.0, 0.45));
    }
    col *= uExposureScale;
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""

        private const val OVERLAY_FRAG = """#version 300 es
precision mediump float;
uniform sampler2D uOverlayTex;
in vec2 vTexCoord;
out vec4 fragColor;
void main() {
    fragColor = texture(uOverlayTex, vec2(vTexCoord.x, 1.0 - vTexCoord.y));
}
"""

        /**
         * Pure helper computing 16-pixel macroblock aligned encoder dimensions that preserve
         * the user's selected aspect ratio without forcing 9:16 cropping.
         */
        fun computeEncodedDimensions(
            sourceWidth: Int,
            sourceHeight: Int,
            aspectRatio: AspectRatioMode,
            maxLongEdge: Int = MAX_LONG_EDGE
        ): Pair<Int, Int> {
            val isPortrait = sourceWidth < sourceHeight
            val targetRatio = if (isPortrait) (1.0f / aspectRatio.ratio) else aspectRatio.ratio
            val rawW: Float
            val rawH: Float
            if (targetRatio >= 1.0f) {
                rawW = maxLongEdge.toFloat()
                rawH = rawW / targetRatio
            } else {
                rawH = maxLongEdge.toFloat()
                rawW = rawH * targetRatio
            }
            val w16 = ((rawW / 16f).roundToInt() * 16).coerceIn(256, maxLongEdge)
            val h16 = ((rawH / 16f).roundToInt() * 16).coerceIn(256, maxLongEdge)
            return w16 to h16
        }

        fun computeCropUvRect(
            srcW: Int,
            srcH: Int,
            encW: Int,
            encH: Int
        ): FloatArray {
            if (srcW <= 0 || srcH <= 0 || encW <= 0 || encH <= 0) {
                return floatArrayOf(0f, 0f, 1f, 1f)
            }
            val srcAspect = srcW.toFloat() / srcH
            val dstAspect = encW.toFloat() / encH
            return if (srcAspect > dstAspect) {
                val fracW = (dstAspect / srcAspect).coerceIn(0.05f, 1f)
                floatArrayOf((1f - fracW) * 0.5f, 0f, fracW, 1f)
            } else {
                val fracH = (srcAspect / dstAspect).coerceIn(0.05f, 1f)
                floatArrayOf(0f, (1f - fracH) * 0.5f, 1f, fracH)
            }
        }
    }

    data class EncodeResult(
        val success: Boolean,
        val width: Int,
        val height: Int,
        val encodedFrames: Int,
        val durationMs: Long,
        val hasAudioTrack: Boolean
    )

    fun <T> encodeGolfMotionClip(
        context: Context,
        source: MotionFrameSource,
        timeline: GolfTemporalEffectPlanner.CadenceSelection<T>,
        sourceIndexOf: (T) -> Int,
        preset: CameraPreset = PresetCatalog.getPresetById("disp_classic"),
        tuning: UserTuningConfig,
        aspectRatio: AspectRatioMode,
        lensProfile: LensProfile,
        audioPcm: ShortArray = ShortArray(0),
        audioSampleRate: Int = 44_100,
        fps: Int = 14,
        outputFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): EncodeResult {
        if (timeline.frames.isEmpty() || source.sourceWidth <= 0 || source.sourceHeight <= 0) {
            return EncodeResult(false, 0, 0, 0, 0L, false)
        }

        val (encW, encH) = computeEncodedDimensions(source.sourceWidth, source.sourceHeight, aspectRatio)
        val cropRect = computeCropUvRect(source.sourceWidth, source.sourceHeight, encW, encH)

        // Pre-encode AAC audio samples into small byte packets so MediaMuxer starts cleanly with both tracks
        val encodedAudio = if (audioPcm.isNotEmpty()) {
            encodePcmToAacPackets(audioPcm, audioSampleRate)
        } else null

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: android.view.Surface? = null
        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        var muxerStarted = false

        // GPU handles owned strictly inside this encoding EGL context
        var preProgram = 0
        var stillProgram = 0
        var overlayProgram = 0
        var fboId = 0
        var fboTexId = 0
        var currTexId = 0
        var prevTexId = 0
        var lutPrimaryTex = 0
        var lutSecondaryTex = 0
        var grainTex = 0
        var overlayTex = 0
        var dummy2DTex = 0
        var dummy3DTex = 0

        try {
            val videoFormat = MediaFormat.createVideoFormat(MIME_AVC, encW, encH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4)
            }

            codec = MediaCodec.createEncoderByType(MIME_AVC)
            try {
                codec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (_: Throwable) {
                // Safe Baseline/default profile fallback on constrained encoders
                val fallbackFmt = MediaFormat.createVideoFormat(MIME_AVC, encW, encH).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                codec.configure(fallbackFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }

            inputSurface = codec.createInputSurface()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Initialize offscreen EGL ES 3.0 Context for MediaCodec surface
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val vers = IntArray(2)
            EGL14.eglInitialize(eglDisplay, vers, 0, vers, 1)
            val attrib = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attrib, 0, configs, 0, 1, numConfigs, 0)
            val cfg = configs[0] ?: throw IllegalStateException("No EGL ES3 config for MediaCodec Surface")
            eglContext = EGL14.eglCreateContext(
                eglDisplay, cfg, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0
            )
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, cfg, inputSurface, intArrayOf(EGL14.EGL_NONE), 0)
            check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "eglMakeCurrent failed" }

            preProgram = GLShaderHelper.createProgram(PREPASS_VERT, PREPASS_FRAG)
            stillProgram = GLShaderHelper.createProgram(ShaderConstants.VERTEX_SHADER_STILL, ShaderConstants.FRAGMENT_SHADER_STILL)
            overlayProgram = GLShaderHelper.createProgram(PREPASS_VERT, OVERLAY_FRAG)
            check(preProgram != 0 && stillProgram != 0 && overlayProgram != 0) { "Shader compilation failed" }

            // Create geometry buffers
            val quadBuf = createFloatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            val uvBufFlip = createFloatBuffer(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
            val uvBufUpright = createFloatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

            dummy2DTex = createDummy2DTexture()
            dummy3DTex = createDummy3DTexture()

            // Create FBO for Pass A
            val fbos = IntArray(1)
            GLES30.glGenFramebuffers(1, fbos, 0)
            fboId = fbos[0]
            fboTexId = createEmpty2DTexture(encW, encH)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fboTexId, 0)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            currTexId = createTexture2D()
            prevTexId = createTexture2D()

            // Resolve Golf color LUT & grain using exact unified helpers
            val unifiedProfile = CameraProfileCatalog.getUnifiedProfile(preset)
            val film = unifiedProfile.filmProfile
            val lutPlan = CameraProfileCompatibility.resolveLutPlan(
                presetId = preset.id,
                colorProfileIndex = tuning.filmColorProfileIndex,
                cameraLutAsset = film.lutAssetPath?.ifEmpty { null } ?: ShaderConstants.getLutAssetForPreset(preset.id),
                cameraSecondaryLutAsset = film.secondaryLutAssetPath,
                cameraDualLutMix = film.dualLutBlendFactor
            )
            lutPrimaryTex = load2DAssetTexture(context, lutPlan.primaryLutAsset, clamp = true)
            lutSecondaryTex = if (!lutPlan.secondaryLutAsset.isNullOrEmpty() && lutPlan.dualLutMix > 0.001f) {
                load2DAssetTexture(context, lutPlan.secondaryLutAsset, clamp = true)
            } else 0

            val intensity = tuning.filterIntensity.coerceIn(0f, 1f)
            val resolvedGrain = GrainStage.resolve(preset, unifiedProfile.grainProfile, tuning, intensity)
            grainTex = if (resolvedGrain.useScannedTexture && !resolvedGrain.assetPath.isNullOrEmpty()) {
                load2DAssetTexture(context, resolvedGrain.assetPath, clamp = false)
            } else 0

            // Build single reusable transparent Golf Border + Date Stamp overlay if enabled
            val overlayBmp = buildStaticOverlayBitmap(context, encW, encH, preset, tuning, lensProfile)
            if (overlayBmp != null) {
                overlayTex = createTexture2D()
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, overlayTex)
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, overlayBmp, 0)
                overlayBmp.recycle()
            }

            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var hasPrevFrame = false
            var lastLoadedSrcIdx = -1

            fun drainVideo(eos: Boolean) {
                val deadline = SystemClock.elapsedRealtime() + 6000L
                while (true) {
                    val status = codec.dequeueOutputBuffer(bufferInfo, if (eos) TIMEOUT_USEC else 0L)
                    if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!eos) break
                        check(SystemClock.elapsedRealtime() < deadline) { "Encoder EOS timeout" }
                    } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        check(!muxerStarted) { "Muxer already started" }
                        videoTrackIndex = muxer.addTrack(codec.outputFormat)
                        if (encodedAudio != null && encodedAudio.packets.isNotEmpty()) {
                            audioTrackIndex = muxer.addTrack(encodedAudio.format)
                        }
                        muxer.start()
                        muxerStarted = true
                    } else if (status >= 0) {
                        val data = codec.getOutputBuffer(status)
                        if (data != null) {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0 && muxerStarted) {
                                data.position(bufferInfo.offset)
                                data.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, data, bufferInfo)
                            }
                        }
                        codec.releaseOutputBuffer(status, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }

            // Encode each timeline frame streaming one bitmap at a time
            val total = timeline.frames.size
            for (i in 0 until total) {
                val item = timeline.frames[i]
                val tp = timeline.params[i]
                val srcIdx = sourceIndexOf(item)

                if (srcIdx != lastLoadedSrcIdx) {
                    if (lastLoadedSrcIdx >= 0) {
                        // Swap texture IDs so previous frame remains in prevTexId with zero extra copy
                        val tmp = prevTexId
                        prevTexId = currTexId
                        currTexId = tmp
                        hasPrevFrame = true
                    }
                    val bmp = source.loadBitmap(srcIdx) ?: continue
                    try {
                        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currTexId)
                        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
                        lastLoadedSrcIdx = srcIdx
                    } finally {
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                }

                // ── Pass A: Render cropped / crossfaded / EV-modulated frame into FBO ──
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
                GLES30.glViewport(0, 0, encW, encH)
                GLES30.glUseProgram(preProgram)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, quadBuf)
                GLES30.glEnableVertexAttribArray(1)
                GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, uvBufFlip)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currTexId)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(preProgram, "uCurrTex"), 0)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (hasPrevFrame) prevTexId else currTexId)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(preProgram, "uPrevTex"), 1)

                GLES30.glUniform1f(GLES30.glGetUniformLocation(preProgram, "uHasPrev"), if (hasPrevFrame) 1f else 0f)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(preProgram, "uCrossfade"), tp.crossfadePrevAlpha)
                val evScale = 2.0.pow(tp.totalExposureOffsetEv.toDouble()).toFloat()
                GLES30.glUniform1f(GLES30.glGetUniformLocation(preProgram, "uExposureScale"), evScale)
                GLES30.glUniform4fv(GLES30.glGetUniformLocation(preProgram, "uCropRect"), 1, cropRect, 0)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

                // ── Pass B: Render FBO through untouched Golf FRAGMENT_SHADER_STILL onto MediaCodec Surface ──
                GLES30.glViewport(0, 0, encW, encH)
                GLES30.glClearColor(0f, 0f, 0f, 1f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                GLES30.glUseProgram(stillProgram)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, quadBuf)
                GLES30.glEnableVertexAttribArray(1)
                GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, uvBufFlip)

                bindStillUniforms(
                    program = stillProgram,
                    encW = encW,
                    encH = encH,
                    fboTexId = fboTexId,
                    lutPrimaryTex = lutPrimaryTex,
                    lutSecondaryTex = lutSecondaryTex,
                    grainTex = grainTex,
                    dummy2DTex = dummy2DTex,
                    dummy3DTex = dummy3DTex,
                    preset = preset,
                    tuning = tuning,
                    lensProfile = lensProfile,
                    isFlashPeak = tp.flashBoostEv > 0.08f,
                    lutPlan = lutPlan,
                    resolvedGrain = resolvedGrain,
                    tp = tp
                )
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

                // ── Pass C: Optional static Golf Frame Border + DateStamp alpha overlay ──
                if (overlayTex != 0) {
                    GLES30.glEnable(GLES30.GL_BLEND)
                    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                    GLES30.glUseProgram(overlayProgram)
                    GLES30.glEnableVertexAttribArray(0)
                    GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, quadBuf)
                    GLES30.glEnableVertexAttribArray(1)
                    GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, uvBufUpright)
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, overlayTex)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(overlayProgram, "uOverlayTex"), 0)
                    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
                    GLES30.glDisable(GLES30.GL_BLEND)
                }

                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, tp.presentationTimeNs)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                drainVideo(eos = false)
                onProgress?.invoke((i + 1).toFloat() / total)
            }

            codec.signalEndOfInputStream()
            drainVideo(eos = true)

            // Write pre-encoded AAC packets interleaved cleanly within [0..clipDurationUs]
            val maxVideoUs = (timeline.actualDurationMs * 1000L).coerceAtLeast(500_000L)
            var wroteAudio = false
            if (muxerStarted && audioTrackIndex >= 0 && encodedAudio != null) {
                for (pkt in encodedAudio.packets) {
                    if (pkt.info.presentationTimeUs <= maxVideoUs) {
                        val buf = ByteBuffer.wrap(pkt.data)
                        muxer.writeSampleData(audioTrackIndex, buf, pkt.info)
                        wroteAudio = true
                    }
                }
            }

            check(muxerStarted) { "Muxer never started" }
            muxer.stop()
            muxerStarted = false

            return EncodeResult(
                success = outputFile.exists() && outputFile.length() > 128L,
                width = encW,
                height = encH,
                encodedFrames = total,
                durationMs = timeline.actualDurationMs,
                hasAudioTrack = wroteAudio
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Golf motion GPU encoding failed", t)
            try { outputFile.delete() } catch (_: Throwable) {}
            return EncodeResult(false, encW, encH, 0, 0L, false)
        } finally {
            // Delete GL objects cleanly inside active context BEFORE destroying EGL context
            if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT) {
                try {
                    if (eglSurface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                    }
                    val texList = intArrayOf(fboTexId, currTexId, prevTexId, lutPrimaryTex, lutSecondaryTex, grainTex, overlayTex, dummy2DTex, dummy3DTex)
                        .filter { it != 0 }.toIntArray()
                    if (texList.isNotEmpty()) GLES30.glDeleteTextures(texList.size, texList, 0)
                    if (fboId != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
                    if (preProgram != 0) GLES30.glDeleteProgram(preProgram)
                    if (stillProgram != 0) GLES30.glDeleteProgram(stillProgram)
                    if (overlayProgram != 0) GLES30.glDeleteProgram(overlayProgram)
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                } catch (_: Throwable) {}
            }
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            try { inputSurface?.release() } catch (_: Throwable) {}
        }
    }

    private fun bindStillUniforms(
        program: Int,
        encW: Int,
        encH: Int,
        fboTexId: Int,
        lutPrimaryTex: Int,
        lutSecondaryTex: Int,
        grainTex: Int,
        dummy2DTex: Int,
        dummy3DTex: Int,
        preset: CameraPreset,
        tuning: UserTuningConfig,
        lensProfile: LensProfile,
        isFlashPeak: Boolean,
        lutPlan: CameraProfileCompatibility.ResolvedLutPlan,
        resolvedGrain: GrainStage.ResolvedGrainConfig,
        tp: TemporalFrameParams
    ) {
        fun uLoc(name: String) = GLES30.glGetUniformLocation(program, name)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexId)
        GLES30.glUniform1i(uLoc("sTexture"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, dummy3DTex)
        GLES30.glUniform1i(uLoc("sLut3D"), 1)

        val lutActive = lutPrimaryTex != 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (lutActive) lutPrimaryTex else dummy2DTex)
        GLES30.glUniform1i(uLoc("uLutTexture"), 2)
        GLES30.glUniform1i(uLoc("uLutEnabled"), if (lutActive) 1 else 0)
        GLES30.glUniform1i(uLoc("uUseLut3D"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (grainTex != 0) grainTex else dummy2DTex)
        GLES30.glUniform1i(uLoc("sGrainTexture"), 3)
        GLES30.glUniform1i(uLoc("uHasGrainTexture"), if (grainTex != 0) 1 else 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, dummy3DTex)
        GLES30.glUniform1i(uLoc("sLut3D_B"), 4)

        val secActive = lutSecondaryTex != 0 && lutPlan.dualLutMix > 0.001f
        GLES30.glActiveTexture(GLES30.GL_TEXTURE5)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (secActive) lutSecondaryTex else dummy2DTex)
        GLES30.glUniform1i(uLoc("uLutTextureB"), 5)
        GLES30.glUniform1i(uLoc("uHasSecondaryLut"), if (secActive) 1 else 0)
        GLES30.glUniform1f(uLoc("uDualLutMix"), if (secActive) lutPlan.dualLutMix else 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        val unified = CameraProfileCatalog.getUnifiedProfile(preset)
        val film = unified.filmProfile
        val intensity = tuning.filterIntensity.coerceIn(0f, 1f)
        val primaryIsProfile = lutPlan.isMonochrome && lutPlan.isCustomProfile
        val spaceInt = if (primaryIsProfile) 0 else when (film.lutInputSpace) {
            LutInputSpace.SRGB -> 0
            LutInputSpace.LINEAR -> 1
            LutInputSpace.CUSTOM_PRETRANSFORM -> 2
        }
        GLES30.glUniform1i(uLoc("uLutInputSpace"), spaceInt)
        GLES30.glUniform1i(
            uLoc("uPreLinearizationEnabled"),
            if (!primaryIsProfile && tuning.preLinearizationEnabled && film.lutInputSpace == LutInputSpace.CUSTOM_PRETRANSFORM) 1 else 0
        )
        GLES30.glUniform1f(uLoc("uPreLinearizationStrength"), tuning.preLinearizationStrength)
        GLES30.glUniform1f(uLoc("uFilterIntensity"), intensity)
        GLES30.glUniform1f(uLoc("uLowerMidtoneStrength"), film.lowerMidtoneStrength * intensity)
        GLES30.glUniform1f(uLoc("uClarityStrength"), film.clarityStrength * intensity)
        GLES30.glUniform1f(uLoc("uSkinProtection"), if (lutPlan.isMonochrome) 0f else film.skinProtection)
        GLES30.glUniform2f(uLoc("uViewportSize"), encW.toFloat(), encH.toFloat())
        GLES30.glUniform1i(uLoc("uDiagnosticMode"), 0)

        val color = preset.colorGrading
        val adj = lutPlan.effectiveAdjustment
        val isMono = lutPlan.isMonochrome
        val baseContrast = if (film.contrast != 1.0f) film.contrast else color.contrast
        val baseSat = if (film.saturation != 1.0f) film.saturation else color.saturation
        val baseTemp = if (film.temperature != 0.0f) film.temperature else color.temperature
        val baseTint = if (film.tint != 0.0f) film.tint else color.tint
        val baseShadowR = if (film.shadowTintR != 0.0f) film.shadowTintR else color.shadowTintR
        val baseShadowG = if (film.shadowTintG != 0.0f) film.shadowTintG else color.shadowTintG
        val baseShadowB = if (film.shadowTintB != 0.0f) film.shadowTintB else color.shadowTintB

        val rawContrast = (baseContrast * adj.contrastMult * (1f + tuning.contrastOffset)).coerceAtLeast(0.1f)
        val rawSat = if (isMono) 0f else (baseSat * adj.satMult * (1f + tuning.saturationOffset)).coerceAtLeast(0f)
        val userTempOffset = if (tuning.isHardwareWbActive) 0f else tuning.temperatureOffset

        GLES30.glUniform1f(uLoc("uContrast"), 1f + (rawContrast - 1f) * intensity)
        GLES30.glUniform1f(uLoc("uSaturation"), if (isMono) 0f else 1f + (rawSat - 1f) * intensity)
        GLES30.glUniform1f(uLoc("uTemperature"), if (isMono) 0f else ((baseTemp + adj.tempDelta + userTempOffset) * intensity).coerceIn(-1f, 1f))
        GLES30.glUniform1f(uLoc("uTint"), if (isMono) 0f else ((baseTint + adj.tintDelta + tuning.tintOffset) * intensity).coerceIn(-1f, 1f))
        GLES30.glUniform1f(uLoc("uGamma"), 1f + (color.gamma - 1f) * intensity)
        GLES30.glUniform1f(uLoc("uFade"), film.fadeAtIntensity(color.fade, adj.fadeDelta, intensity))
        GLES30.glUniform1f(uLoc("uToeLift"), film.toeLift * intensity)
        GLES30.glUniform1f(uLoc("uShoulderClip"), 1f - (1f - film.shoulderClip) * intensity)
        GLES30.glUniform1f(uLoc("uHighlightRollOff"), film.shoulderAtIntensity(intensity))
        GLES30.glUniform3f(uLoc("uShadowTint"), if (isMono) 0f else baseShadowR * intensity, if (isMono) 0f else baseShadowG * intensity, if (isMono) 0f else baseShadowB * intensity)

        val lens = preset.lens
        GLES30.glUniform1f(uLoc("uVignetteIntensity"), (lens.vignetteIntensity * tuning.vignetteBoost * intensity).coerceIn(0f, 1f))
        GLES30.glUniform1f(uLoc("uVignetteRadius"), lens.vignetteRadius)
        GLES30.glUniform1f(uLoc("uChromaticAberration"), lens.chromaticAberration * intensity)
        GLES30.glUniform1f(uLoc("uBarrelDistortion"), lens.barrelDistortion * intensity)
        GLES30.glUniform1f(uLoc("uSoftness"), lens.softness * intensity)

        val lensTypeInt = when (lensProfile.type) {
            LensType.NORMAL -> 0
            LensType.WIDE -> 1
            LensType.FISHEYE_F -> 2
            LensType.FISHEYE_W -> 3
            LensType.PRISM -> 4
            LensType.STARBURST -> 5
            LensType.ND -> 6
            LensType.COLOR_FLASH -> 7
        }
        GLES30.glUniform1i(uLoc("uLensType"), lensTypeInt)
        GLES30.glUniform1f(uLoc("uLensDistortion"), lensProfile.distortionStrength)
        GLES30.glUniform1f(uLoc("uLensEdgeCurvature"), lensProfile.edgeCurvature)
        GLES30.glUniform1f(uLoc("uLensVignette"), lensProfile.vignetteStrength)
        GLES30.glUniform1f(uLoc("uLensPrismDispersion"), lensProfile.prismDispersion)
        GLES30.glUniform1f(uLoc("uLensStarburstStrength"), lensProfile.starburstStrength)
        GLES30.glUniform1f(uLoc("uLensStarburstThreshold"), lensProfile.starburstThreshold)
        GLES30.glUniform1f(uLoc("uLensNdFactor"), lensProfile.ndTransmissionFactor)
        val (tr, tg, tb) = lensProfile.colorFlashTint.rgb
        GLES30.glUniform3f(uLoc("uLensColorFlashTint"), tr, tg, tb)
        GLES30.glUniform1i(uLoc("uLensIsFlashActive"), if (isFlashPeak) 1 else 0)

        GLES30.glUniform1i(uLoc("uD3DEnabled"), 0)
        GLES30.glUniform1f(uLoc("uD3DStrength"), 0f)
        GLES30.glUniform1f(uLoc("uD3DParallax"), 0f)
        GLES30.glUniform1f(uLoc("uD3DRgbSeparation"), 0f)
        GLES30.glUniform1f(uLoc("uD3DAnaglyph"), 0f)

        // Exact unchanged Golf Grain config + animated per-frame offset
        val gInt = if (resolvedGrain.useScannedTexture) resolvedGrain.effectiveIntensity else resolvedGrain.proceduralIntensity
        val blendInt = when (resolvedGrain.blendMode) {
            GrainBlendMode.OVERLAY -> 0
            GrainBlendMode.SCREEN -> 1
            GrainBlendMode.MULTIPLY -> 2
            GrainBlendMode.NORMAL -> 3
            else -> 0
        }
        GLES30.glUniform1f(uLoc("uGrainIntensity"), gInt)
        GLES30.glUniform1f(uLoc("uGrainScale"), resolvedGrain.scale)
        GLES30.glUniform1i(uLoc("uIsColorNoise"), if (resolvedGrain.isColorNoise) 1 else 0)
        GLES30.glUniform1f(uLoc("uRandomSeed"), tp.grainSeed)
        GLES30.glUniform1i(uLoc("uGrainBlendMode"), blendInt)
        val offX = ((tp.frameIndex * 73) % 1000) / 1000.0f
        val offY = ((tp.frameIndex * 157) % 1000) / 1000.0f
        GLES30.glUniform2f(uLoc("uGrainOffset"), offX, offY)
        GLES30.glUniform1f(uLoc("uGrainShadowStrength"), resolvedGrain.shadowStrength)
        GLES30.glUniform1f(uLoc("uGrainHighlightStrength"), resolvedGrain.highlightStrength)
        GLES30.glUniform1i(uLoc("uGrainLumaWeightEnabled"), if (resolvedGrain.luminanceWeightingEnabled) 1 else 0)
        GLES30.glUniform1f(uLoc("uGrainLumaWeightPower"), resolvedGrain.luminanceWeightingPower)

        val hal = preset.halation
        val halActive = tuning.halationEnabled && (hal?.enabled == true || film.halationThreshold < 0.99f) && intensity > 0.01f
        GLES30.glUniform1i(uLoc("uHalationEnabled"), if (halActive) 1 else 0)
        GLES30.glUniform1f(uLoc("uHalationThreshold"), hal?.threshold ?: film.halationThreshold)
        // Brief flash envelope subtly lifts highlight bloom only at flash peak without changing permanent color
        val flashHalBoost = if (isFlashPeak) 1.15f else 1.0f
        GLES30.glUniform1f(uLoc("uHalationIntensity"), (hal?.intensity ?: film.halationIntensity) * intensity * flashHalBoost)
        GLES30.glUniform3f(uLoc("uHalationColorTint"), film.halationColorTintR, film.halationColorTintG, film.halationColorTintB)

        val leak = preset.lightLeak
        val leakActive = tuning.lightLeakEnabled && leak?.enabled == true && intensity > 0.01f
        GLES30.glUniform1i(uLoc("uLightLeakEnabled"), if (leakActive) 1 else 0)
        GLES30.glUniform1f(uLoc("uLightLeakIntensity"), (leak?.intensity ?: 0f) * intensity)
        GLES30.glUniform1f(uLoc("uScanlineIntensity"), 0f)

        GLES30.glUniform1i(uLoc("uDebugStagePreLinearization"), if (tuning.debugStagePreLinearization) 1 else 0)
        GLES30.glUniform1i(uLoc("uDebugStageLut"), if (tuning.debugStageLut) 1 else 0)
        GLES30.glUniform1i(uLoc("uDebugStageToneCurve"), if (tuning.debugStageToneCurve) 1 else 0)
        GLES30.glUniform1i(uLoc("uDebugStageGrain"), if (tuning.debugStageGrain) 1 else 0)
        GLES30.glUniform1i(uLoc("uDebugStageHalation"), if (tuning.debugStageHalation) 1 else 0)
        GLES30.glUniform1i(uLoc("uDebugStageOptics"), if (tuning.debugStageOptics) 1 else 0)
        GLES30.glUniform1i(uLoc("uDebugStageVignette"), if (tuning.debugStageVignette) 1 else 0)

        GLES30.glUniform1i(uLoc("uNaturalModeEnabled"), 0)
        GLES30.glUniform1f(uLoc("uNaturalShadowLift"), 0f)
        GLES30.glUniform1i(uLoc("uFocusPeakingEnabled"), 0)
        GLES30.glUniform3f(uLoc("uPeakingColor"), 1f, 0.7f, 0f)
        GLES30.glUniform1f(uLoc("uPeakingThreshold"), 0.2f)
        GLES30.glUniform1i(uLoc("uZebraEnabled"), 0)
        GLES30.glUniform1f(uLoc("uZebraThreshold"), 0.95f)
        GLES30.glUniform1f(uLoc("uZebraTime"), 0f)
    }

    private fun buildStaticOverlayBitmap(
        context: Context,
        w: Int,
        h: Int,
        preset: CameraPreset,
        tuning: UserTuningConfig,
        lensProfile: LensProfile
    ): Bitmap? {
        val isFisheye = lensProfile.type == LensType.FISHEYE_F || lensProfile.type == LensType.FISHEYE_W
        val borderConfig = preset.border
        val dateConfig = preset.dateStamp
        val wantBorder = !isFisheye && tuning.frameBorderEnabled && borderConfig != null && borderConfig.style != BorderStyle.NONE
        val wantDate = tuning.dateStampEnabled && dateConfig != null && dateConfig.enabled
        if (!wantBorder && !wantDate) return null

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bmp)
        if (wantBorder) {
            val bCfg = requireNotNull(borderConfig)
            BorderRenderer.renderBorderOnCanvas(
                canvas = canvas,
                style = bCfg.style,
                assetPath = bCfg.assetPath,
                dstLeft = 0f,
                dstTop = 0f,
                dstRight = w.toFloat(),
                dstBottom = h.toFloat(),
                context = context
            )
        }
        if (wantDate) {
            val dCfg = requireNotNull(dateConfig)
            DateStampRenderer.applyDateStamp(bmp, dCfg, tuning.customYear, context)
        }
        return bmp
    }

    private data class EncodedAacPacket(val data: ByteArray, val info: MediaCodec.BufferInfo)
    private data class EncodedAudioTrack(val format: MediaFormat, val packets: List<EncodedAacPacket>)

    private fun encodePcmToAacPackets(pcm: ShortArray, sampleRate: Int): EncodedAudioTrack? {
        if (pcm.isEmpty()) return null
        var aacCodec: MediaCodec? = null
        return try {
            val fmt = MediaFormat.createAudioFormat(MIME_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            aacCodec = MediaCodec.createEncoderByType(MIME_AAC)
            aacCodec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aacCodec.start()

            val packets = ArrayList<EncodedAacPacket>()
            var outFormat: MediaFormat? = null
            val info = MediaCodec.BufferInfo()
            val pcmBytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                for (s in pcm) putShort(s)
            }.array()
            var offset = 0
            var inputDone = false
            var outputDone = false
            val nsPerByte = 1_000_000_000.0 / (sampleRate * 2.0)

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = aacCodec.dequeueInputBuffer(TIMEOUT_USEC)
                    if (inIdx >= 0) {
                        val inBuf = aacCodec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val remaining = pcmBytes.size - offset
                            if (remaining <= 0) {
                                val ptsUs = ((offset * nsPerByte) / 1000.0).toLong()
                                aacCodec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val chunk = minOf(inBuf.capacity(), remaining)
                                inBuf.clear()
                                inBuf.put(pcmBytes, offset, chunk)
                                val ptsUs = ((offset * nsPerByte) / 1000.0).toLong()
                                offset += chunk
                                aacCodec.queueInputBuffer(inIdx, 0, chunk, ptsUs, 0)
                            }
                        }
                    }
                }

                val outIdx = aacCodec.dequeueOutputBuffer(info, TIMEOUT_USEC)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = aacCodec.outputFormat
                    outIdx >= 0 -> {
                        val buf = aacCodec.getOutputBuffer(outIdx)
                        if (buf != null) {
                            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                                val copy = ByteArray(info.size)
                                buf.position(info.offset)
                                buf.get(copy, 0, info.size)
                                val infoCopy = MediaCodec.BufferInfo().apply {
                                    set(0, copy.size, info.presentationTimeUs, info.flags)
                                }
                                packets.add(EncodedAacPacket(copy, infoCopy))
                            }
                        }
                        aacCodec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            }
            if (outFormat != null && packets.isNotEmpty()) EncodedAudioTrack(outFormat, packets) else null
        } catch (t: Throwable) {
            Log.w(TAG, "AAC audio encoding skipped cleanly: ${t.message}")
            null
        } finally {
            try { aacCodec?.stop() } catch (_: Throwable) {}
            try { aacCodec?.release() } catch (_: Throwable) {}
        }
    }

    private fun createFloatBuffer(coords: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(coords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(coords)
            position(0)
        }

    private fun createTexture2D(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun createEmpty2DTexture(w: Int, h: Int): Int {
        val id = createTexture2D()
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        return id
    }

    private fun load2DAssetTexture(context: Context, assetPath: String, clamp: Boolean): Int {
        return try {
            val resolved = if (!clamp && !assetPath.startsWith("grains/")) {
                try { context.assets.open(assetPath).close(); assetPath } catch (_: Exception) { "grains/$assetPath" }
            } else assetPath
            val bmp = context.assets.open(resolved).use { BitmapFactory.decodeStream(it) } ?: return 0
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
            val wrap = if (clamp) GLES30.GL_CLAMP_TO_EDGE else GLES30.GL_REPEAT
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
            ids[0]
        } catch (_: Throwable) {
            0
        }
    }

    private fun createDummy2DTexture(): Int {
        val id = createTexture2D()
        val buf = ByteBuffer.allocateDirect(4).apply { put(byteArrayOf(0, 0, 0, 255.toByte())); position(0) }
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        return id
    }

    private fun createDummy3DTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        val buf = ByteBuffer.allocateDirect(4).apply { put(byteArrayOf(0, 0, 0, 255.toByte())); position(0) }
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA, 1, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        return ids[0]
    }
}
