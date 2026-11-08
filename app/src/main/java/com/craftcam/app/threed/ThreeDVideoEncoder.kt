package com.craftcam.app.threed

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.*
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Hardware-accelerated video encoder for CAMELEO 3D stereoscopic wigglegrams.
 *
 * Encodes a sequence of viewpoint Bitmaps into a looping MP4 (H.264/AVC) video file
 * using Android's native MediaCodec with an EGL-backed input Surface and MediaMuxer.
 */
class ThreeDVideoEncoder {

    companion object {
        private const val TAG = "ThreeDVideoEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val TIMEOUT_USEC = 10_000L

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }

    /**
     * Encodes [frames] into an MP4 file at [outputFile].
     *
     * @param frames List of viewpoint Bitmaps.
     * @param outputFile Target MP4 file destination.
     * @param fps Playback frame rate (e.g. 15 fps).
     * @param loopCount Number of complete oscillation cycles in the output video.
     * @return true if encoding succeeded, false otherwise.
     */
    fun encodeFramesToMp4(
        frames: List<Bitmap>,
        outputFile: File,
        fps: Int = 15,
        loopCount: Int = 3
    ): Boolean {
        if (frames.isEmpty()) {
            Log.e(TAG, "Cannot encode empty frame sequence")
            return false
        }

        val firstFrame = frames[0]
        // Hardware encoders require 16-pixel macroblock aligned dimensions to prevent green bars
        val encWidth = (firstFrame.width / 16) * 16
        val encHeight = (firstFrame.height / 16) * 16

        var mediaCodec: MediaCodec? = null
        var mediaMuxer: MediaMuxer? = null
        var eglHelper: EglSurfaceRenderer? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, encWidth, encHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 10_000_000) // 10 Mbps pristine stereoscopic quality
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            val inputSurface = mediaCodec.createInputSurface()
            eglHelper = EglSurfaceRenderer(inputSurface, encWidth, encHeight)
            eglHelper.makeCurrent()

            mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            mediaCodec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationNs = 1_000_000_000L / fps
            var presentationTimeNs = 0L

            val distinctFrameCount = frames.size
            val totalFramesToEncode = distinctFrameCount * loopCount

            for (i in 0 until totalFramesToEncode) {
                val frameBitmap = frames[i % distinctFrameCount]

                // Render Bitmap texture to EGL surface
                eglHelper.drawBitmap(frameBitmap)
                eglHelper.setPresentationTime(presentationTimeNs)
                eglHelper.swapBuffers()

                presentationTimeNs += frameDurationNs

                // Drain pending encoder outputs
                drainEncoder(mediaCodec, mediaMuxer, bufferInfo, endOfStream = false) { newTrackIndex ->
                    trackIndex = newTrackIndex
                    muxerStarted = true
                }
            }

            // Signal End of Stream
            mediaCodec.signalEndOfInputStream()
            drainEncoder(mediaCodec, mediaMuxer, bufferInfo, endOfStream = true) { newTrackIndex ->
                trackIndex = newTrackIndex
                muxerStarted = true
            }

            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Hardware video encoding failed", t)
            return false
        } finally {
            try {
                mediaCodec?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping MediaCodec: ${e.message}")
            }
            try {
                mediaCodec?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaCodec: ${e.message}")
            }
            try {
                if (muxerStarted) {
                    mediaMuxer?.stop()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping MediaMuxer: ${e.message}")
            }
            try {
                mediaMuxer?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaMuxer: ${e.message}")
            }
            eglHelper?.release()
        }
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer?,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onMuxerStarted: (Int) -> Unit
    ) {
        var muxerStarted = false
        var trackIndex = -1

        while (true) {
            val encoderStatus = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                trackIndex = muxer?.addTrack(newFormat) ?: -1
                muxer?.start()
                muxerStarted = true
                onMuxerStarted(trackIndex)
            } else if (encoderStatus >= 0) {
                val encodedData = codec.getOutputBuffer(encoderStatus)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && muxer != null) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex.coerceAtLeast(0), encodedData, bufferInfo)
                    }

                    codec.releaseOutputBuffer(encoderStatus, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    /**
     * EGL and OpenGL ES 2.0 helper for rendering Bitmaps onto a MediaCodec input Surface.
     */
    private class EglSurfaceRenderer(
        private val surface: android.view.Surface,
        private val width: Int,
        private val height: Int
    ) {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private var programId = 0
        private var textureId = 0
        private var aPositionLoc = -1
        private var aTexCoordLoc = -1
        private var uTextureLoc = -1

        private val vertexBuffer: FloatBuffer
        private val texCoordBuffer: FloatBuffer

        init {
            // Full-screen quad coordinates [-1, 1]
            val quadCoords = floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
            )
            // Texture coordinates: vertically flipped for GLES framebuffer conventions
            val texCoords = floatArrayOf(
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
            )

            vertexBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(quadCoords)
                    position(0)
                }

            texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(texCoords)
                    position(0)
                }

            initEgl()
            initGl()
        }

        private fun initEgl() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
            val config = configs[0] ?: throw RuntimeException("Unable to find suitable EGL config")

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfaceAttribs, 0)
        }

        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun initGl() {
            makeCurrent()
            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

            programId = GLES20.glCreateProgram()
            GLES20.glAttachShader(programId, vertexShader)
            GLES20.glAttachShader(programId, fragmentShader)
            GLES20.glLinkProgram(programId)

            aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
            aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
            uTextureLoc = GLES20.glGetUniformLocation(programId, "uTexture")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glViewport(0, 0, width, height)
        }

        fun drawBitmap(bitmap: Bitmap) {
            makeCurrent()
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(programId)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glUniform1i(uTextureLoc, 0)

            GLES20.glEnableVertexAttribArray(aPositionLoc)
            GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            GLES20.glEnableVertexAttribArray(aTexCoordLoc)
            GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPositionLoc)
            GLES20.glDisableVertexAttribArray(aTexCoordLoc)
        }

        fun setPresentationTime(nsecs: Long) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
        }

        fun swapBuffers() {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface !== EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext !== EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }

        companion object {
            private const val EGL_RECORDABLE_ANDROID = 0x3142
        }
    }
}

