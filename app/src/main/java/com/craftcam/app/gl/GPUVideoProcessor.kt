package com.craftcam.app.gl

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.util.Consumer
import com.craftcam.app.gl.shaders.ShaderConstants
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.camera.UserTuningConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor

/**
 * High-performance GPU Video SurfaceProcessor that applies live 3D Hald CLUTs,
 * optical lens effects, and film grain directly to the CameraX VideoCapture stream.
 *
 * Runs on a dedicated background OpenGL ES 3.0 thread ("Cameleo-VideoGL") with zero
 * CPU readbacks and preserves hardware presentation timestamps for microsecond audio sync.
 */
class GPUVideoProcessor(
    private val context: Context,
    private val presetProvider: () -> CameraPreset,
    private val tuningProvider: () -> UserTuningConfig = { UserTuningConfig() }
) : SurfaceProcessor {

    companion object {
        private const val TAG = "GPUVideoProcessor"

        fun createCameraEffect(
            context: Context,
            presetProvider: () -> CameraPreset,
            tuningProvider: () -> UserTuningConfig = { UserTuningConfig() },
            onError: ((Throwable) -> Unit)? = null
        ): CameraEffect {
            val processor = GPUVideoProcessor(context, presetProvider, tuningProvider)
            val errorConsumer = Consumer<Throwable> { t ->
                Log.e(TAG, "CameraEffect video processing error", t)
                onError?.invoke(t)
            }
            return object : CameraEffect(
                VIDEO_CAPTURE,
                processor.glExecutor,
                processor,
                errorConsumer
            ) {}
        }
    }

    // Threading
    private val glThread = HandlerThread("Cameleo-VideoGL").apply { start() }
    val glHandler = Handler(glThread.looper)
    val glExecutor = Executor { command -> glHandler.post(command) }

    // EGL State
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var eglPbufferSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var eglOutputSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    // OpenGL State
    private var programId = 0
    private var inputTextureId = 0
    private var inputSurfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private var currentOutput: SurfaceOutput? = null
    private var outputSize = Size(1280, 720)

    // Matrices
    private val inputMatrix = FloatArray(16)
    private val finalTexMatrix = FloatArray(16)

    // Shader Uniform Locations
    private var sTextureLoc = -1
    private var uTexMatrixLoc = -1
    private var uViewportSizeLoc = -1
    private var uContrastLoc = -1
    private var uSaturationLoc = -1
    private var uTemperatureLoc = -1
    private var uTintLoc = -1
    private var uGammaLoc = -1
    private var uFadeLoc = -1
    private var uHighlightRollOffLoc = -1
    private var uShadowTintLoc = -1
    private var uVignetteIntensityLoc = -1
    private var uVignetteRadiusLoc = -1
    private var uChromaticAberrationLoc = -1
    private var uBarrelDistortionLoc = -1
    private var uGrainIntensityLoc = -1
    private var uGrainScaleLoc = -1
    private var uIsColorNoiseLoc = -1
    private var uRandomSeedLoc = -1
    private var uHalationEnabledLoc = -1
    private var uHalationThresholdLoc = -1
    private var uHalationIntensityLoc = -1
    private var uLightLeakEnabledLoc = -1
    private var uLightLeakIntensityLoc = -1
    private var uScanlineIntensityLoc = -1
    private var uLutTextureLoc = -1
    private var uLutEnabledLoc = -1

    // 3D Hald LUT Cache
    private val lutTextureCache = mutableMapOf<String, Int>()

    // Geometry Buffers
    private val vertexBuffer: FloatBuffer
    private val textureCoordBuffer: FloatBuffer

    private val quadCoords = floatArrayOf(
        -1.0f,  1.0f, 0.0f,
        -1.0f, -1.0f, 0.0f,
         1.0f,  1.0f, 0.0f,
         1.0f, -1.0f, 0.0f
    )

    private val textureCoords = floatArrayOf(
        0.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 1.0f,
        1.0f, 0.0f
    )

    private var isInitialized = false
    private var isReleased = false
    private var videoFrameCount = 0

    init {
        vertexBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadCoords)
        vertexBuffer.position(0)

        textureCoordBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(textureCoords)
        textureCoordBuffer.position(0)

        // Initialize EGL on the GL thread
        glExecutor.execute {
            initEGL()
        }
    }

    private fun initEGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "Failed to get EGL display")
            return
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            Log.e(TAG, "Failed to initialize EGL")
            return
        }

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            // Fall back without ES3 bit flag
            val fallbackAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGLExt.EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            EGL14.eglChooseConfig(eglDisplay, fallbackAttribs, 0, configs, 0, 1, numConfigs, 0)
        }
        eglConfig = configs[0]

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "Failed to create EGL context")
            return
        }

        // 1x1 Pbuffer for standby
        val pbufferAttribs = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE
        )
        eglPbufferSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
        EGL14.eglMakeCurrent(eglDisplay, eglPbufferSurface, eglPbufferSurface, eglContext)

        // Compile Viewfinder Shaders
        programId = GLShaderHelper.createProgram(
            ShaderConstants.VERTEX_SHADER_VIEWFINDER,
            ShaderConstants.FRAGMENT_SHADER_VIEWFINDER
        )

        if (programId == 0) {
            Log.e(TAG, "Failed to compile/link video GLES shader program")
            return
        }

        // Cache uniform locations
        sTextureLoc = GLES30.glGetUniformLocation(programId, "sTexture")
        uTexMatrixLoc = GLES30.glGetUniformLocation(programId, "uTexMatrix")
        uViewportSizeLoc = GLES30.glGetUniformLocation(programId, "uViewportSize")

        uContrastLoc = GLES30.glGetUniformLocation(programId, "uContrast")
        uSaturationLoc = GLES30.glGetUniformLocation(programId, "uSaturation")
        uTemperatureLoc = GLES30.glGetUniformLocation(programId, "uTemperature")
        uTintLoc = GLES30.glGetUniformLocation(programId, "uTint")
        uGammaLoc = GLES30.glGetUniformLocation(programId, "uGamma")
        uFadeLoc = GLES30.glGetUniformLocation(programId, "uFade")
        uHighlightRollOffLoc = GLES30.glGetUniformLocation(programId, "uHighlightRollOff")
        uShadowTintLoc = GLES30.glGetUniformLocation(programId, "uShadowTint")

        uVignetteIntensityLoc = GLES30.glGetUniformLocation(programId, "uVignetteIntensity")
        uVignetteRadiusLoc = GLES30.glGetUniformLocation(programId, "uVignetteRadius")
        uChromaticAberrationLoc = GLES30.glGetUniformLocation(programId, "uChromaticAberration")
        uBarrelDistortionLoc = GLES30.glGetUniformLocation(programId, "uBarrelDistortion")

        uGrainIntensityLoc = GLES30.glGetUniformLocation(programId, "uGrainIntensity")
        uGrainScaleLoc = GLES30.glGetUniformLocation(programId, "uGrainScale")
        uIsColorNoiseLoc = GLES30.glGetUniformLocation(programId, "uIsColorNoise")
        uRandomSeedLoc = GLES30.glGetUniformLocation(programId, "uRandomSeed")

        uHalationEnabledLoc = GLES30.glGetUniformLocation(programId, "uHalationEnabled")
        uHalationThresholdLoc = GLES30.glGetUniformLocation(programId, "uHalationThreshold")
        uHalationIntensityLoc = GLES30.glGetUniformLocation(programId, "uHalationIntensity")
        uLightLeakEnabledLoc = GLES30.glGetUniformLocation(programId, "uLightLeakEnabled")
        uLightLeakIntensityLoc = GLES30.glGetUniformLocation(programId, "uLightLeakIntensity")
        uScanlineIntensityLoc = GLES30.glGetUniformLocation(programId, "uScanlineIntensity")
        uLutTextureLoc = GLES30.glGetUniformLocation(programId, "uLutTexture")
        uLutEnabledLoc = GLES30.glGetUniformLocation(programId, "uLutEnabled")

        // Create CameraX external OES texture
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        inputTextureId = textures[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        isInitialized = true
        Log.d(TAG, "GPUVideoProcessor EGL and shaders initialized successfully")
    }

    override fun onInputSurface(request: SurfaceRequest) {
        if (isReleased) {
            request.willNotProvideSurface()
            return
        }

        glExecutor.execute {
            try {
                inputSurfaceTexture?.release()
                inputSurface?.release()

                val st = SurfaceTexture(inputTextureId).apply {
                    setDefaultBufferSize(request.resolution.width, request.resolution.height)
                    setOnFrameAvailableListener({
                        glExecutor.execute { renderFrame() }
                    }, glHandler)
                }
                inputSurfaceTexture = st

                val surface = Surface(st)
                inputSurface = surface

                request.provideSurface(surface, glExecutor) {
                    Log.d(TAG, "Input surface consumer completed, releasing resources")
                    surface.release()
                    st.release()
                    if (inputSurface == surface) inputSurface = null
                    if (inputSurfaceTexture == st) inputSurfaceTexture = null
                }
                Log.d(TAG, "Provided GPU input surface to CameraX: ${request.resolution}")
            } catch (t: Throwable) {
                Log.e(TAG, "Error providing input surface", t)
                request.willNotProvideSurface()
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (isReleased) {
            surfaceOutput.close()
            return
        }

        glExecutor.execute {
            try {
                if (eglOutputSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglOutputSurface)
                    eglOutputSurface = EGL14.EGL_NO_SURFACE
                }

                currentOutput = surfaceOutput
                outputSize = surfaceOutput.size

                val surface = surfaceOutput.getSurface(glExecutor) {
                    glExecutor.execute {
                        Log.d(TAG, "SurfaceOutput closed by encoder")
                        if (eglOutputSurface != EGL14.EGL_NO_SURFACE) {
                            EGL14.eglDestroySurface(eglDisplay, eglOutputSurface)
                            eglOutputSurface = EGL14.EGL_NO_SURFACE
                        }
                        surfaceOutput.close()
                        if (currentOutput == surfaceOutput) currentOutput = null
                    }
                }

                val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
                eglOutputSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
                Log.d(TAG, "Connected GPU video encoder output surface: ${outputSize.width}x${outputSize.height}")
            } catch (t: Throwable) {
                Log.e(TAG, "Error connecting output surface", t)
                surfaceOutput.close()
            }
        }
    }

    private fun renderFrame() {
        if (!isInitialized || isReleased || eglOutputSurface == EGL14.EGL_NO_SURFACE) return
        val st = inputSurfaceTexture ?: return
        val out = currentOutput ?: return

        try {
            st.updateTexImage()
            val timestampNs = st.timestamp

            // Retrieve transform matrix and map to video encoder orientation
            st.getTransformMatrix(inputMatrix)
            out.updateTransformMatrix(finalTexMatrix, inputMatrix)

            EGL14.eglMakeCurrent(eglDisplay, eglOutputSurface, eglOutputSurface, eglContext)
            GLES30.glViewport(0, 0, outputSize.width, outputSize.height)

            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            GLES30.glUseProgram(programId)

            // Vertices
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, vertexBuffer)

            // Texture coords
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 8, textureCoordBuffer)

            // Bind Camera OES Texture (unit 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTextureId)
            GLES30.glUniform1i(sTextureLoc, 0)

            // Bind 3D Hald LUT Texture (unit 1)
            val preset = presetProvider()
            val tuning = tuningProvider()
            val lutAsset = ShaderConstants.getLutAssetForPreset(preset.id)
            val lutId = getOrLoadLutTexture(lutAsset)

            val lutActive = (lutId != 0 && uLutTextureLoc != -1)
            if (lutActive) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutId)
                GLES30.glUniform1i(uLutTextureLoc, 1)
                GLES30.glUniform1i(uLutEnabledLoc, 1)
            } else if (uLutEnabledLoc != -1) {
                GLES30.glUniform1i(uLutEnabledLoc, 0)
            }

            // Uniforms
            GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, finalTexMatrix, 0)
            GLES30.glUniform2f(uViewportSizeLoc, outputSize.width.toFloat(), outputSize.height.toFloat())

            val color = preset.colorGrading
            val contrast = (color.contrast * (1.0f + tuning.contrastOffset)).coerceAtLeast(0.1f)
            val saturation = (color.saturation * (1.0f + tuning.saturationOffset)).coerceAtLeast(0.0f)
            val temperature = (color.temperature + tuning.temperatureOffset).coerceIn(-1.0f, 1.0f)
            val tint = (color.tint + tuning.tintOffset).coerceIn(-1.0f, 1.0f)

            GLES30.glUniform1f(uContrastLoc, contrast)
            GLES30.glUniform1f(uSaturationLoc, saturation)
            GLES30.glUniform1f(uTemperatureLoc, temperature)
            GLES30.glUniform1f(uTintLoc, tint)
            GLES30.glUniform1f(uGammaLoc, color.gamma)
            GLES30.glUniform1f(uFadeLoc, color.fade)
            GLES30.glUniform1f(uHighlightRollOffLoc, color.highlightRollOff)
            GLES30.glUniform3f(uShadowTintLoc, color.shadowTintR, color.shadowTintG, color.shadowTintB)

            val lens = preset.lens
            val vigIntensity = (lens.vignetteIntensity + tuning.vignetteBoost).coerceAtLeast(0f)
            GLES30.glUniform1f(uVignetteIntensityLoc, vigIntensity)
            GLES30.glUniform1f(uVignetteRadiusLoc, lens.vignetteRadius)
            GLES30.glUniform1f(uChromaticAberrationLoc, lens.chromaticAberration)
            GLES30.glUniform1f(uBarrelDistortionLoc, lens.barrelDistortion)

            videoFrameCount++
            val grain = preset.grain
            val grainIntensity = if (grain.enabled) (grain.intensity + tuning.grainBoost).coerceAtLeast(0f) else 0f
            val frameSeed = (videoFrameCount % 60).toFloat() / 60.0f
            GLES30.glUniform1f(uGrainIntensityLoc, grainIntensity)
            GLES30.glUniform1f(uGrainScaleLoc, grain.scale)
            GLES30.glUniform1i(uIsColorNoiseLoc, if (grain.isColorNoise) 1 else 0)
            GLES30.glUniform1f(uRandomSeedLoc, frameSeed)

            val halation = preset.halation
            val halationActive = tuning.halationEnabled && halation?.enabled == true
            GLES30.glUniform1i(uHalationEnabledLoc, if (halationActive) 1 else 0)
            GLES30.glUniform1f(uHalationThresholdLoc, halation?.threshold ?: 0.85f)
            GLES30.glUniform1f(uHalationIntensityLoc, halation?.intensity ?: 0f)

            val leak = preset.lightLeak
            val leakActive = tuning.lightLeakEnabled && leak?.enabled == true
            GLES30.glUniform1i(uLightLeakEnabledLoc, if (leakActive) 1 else 0)
            GLES30.glUniform1f(uLightLeakIntensityLoc, leak?.intensity ?: 0f)

            val isCamcorder = preset.category == CameraCategory.CAMCORDER
            GLES30.glUniform1f(uScanlineIntensityLoc, if (isCamcorder) 0.6f else 0f)

            // Draw quad
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDisableVertexAttribArray(1)

            // Set hardware presentation timestamp for precise audio-video sync
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglOutputSurface, timestampNs)
            EGL14.eglSwapBuffers(eglDisplay, eglOutputSurface)

        } catch (t: Throwable) {
            Log.e(TAG, "Error during GPU video frame rendering", t)
        }
    }

    private fun getOrLoadLutTexture(lutAsset: String): Int {
        lutTextureCache[lutAsset]?.let { return it }
        return try {
            context.assets.open(lutAsset).use { input ->
                val bmp = BitmapFactory.decodeStream(input) ?: return 0
                val texIds = IntArray(1)
                GLES30.glGenTextures(1, texIds, 0)
                val texId = texIds[0]
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
                bmp.recycle()
                lutTextureCache[lutAsset] = texId
                Log.d(TAG, "Loaded video 3D LUT texture: $lutAsset (id=$texId)")
                texId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load video LUT asset: $lutAsset", e)
            0
        }
    }

    fun release() {
        if (isReleased) return
        isReleased = true

        glExecutor.execute {
            inputSurface?.release()
            inputSurface = null
            inputSurfaceTexture?.release()
            inputSurfaceTexture = null

            for ((_, texId) in lutTextureCache) {
                GLES30.glDeleteTextures(1, intArrayOf(texId), 0)
            }
            lutTextureCache.clear()

            if (inputTextureId != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(inputTextureId), 0)
                inputTextureId = 0
            }

            if (programId != 0) {
                GLES30.glDeleteProgram(programId)
                programId = 0
            }

            if (eglOutputSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglOutputSurface)
                eglOutputSurface = EGL14.EGL_NO_SURFACE
            }

            if (eglPbufferSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglPbufferSurface)
                eglPbufferSurface = EGL14.EGL_NO_SURFACE
            }

            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }

            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglTerminate(eglDisplay)
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }

            glThread.quitSafely()
            Log.d(TAG, "GPUVideoProcessor released completely")
        }
    }
}

