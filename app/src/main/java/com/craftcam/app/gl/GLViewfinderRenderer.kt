package com.craftcam.app.gl

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.gl.shaders.ShaderConstants
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.presets.PresetCatalog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLViewfinderRenderer(
    private val context: Context,
    private val glSurfaceView: GLSurfaceView,
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val TAG = "GLViewfinderRenderer"

    private var programId = 0
    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null

    private val vertexBuffer: FloatBuffer
    private val textureCoordBuffer: FloatBuffer

    private val texMatrix = FloatArray(16).apply { android.opengl.Matrix.setIdentityM(this, 0) }
    private val rotMatrix = FloatArray(16).apply { android.opengl.Matrix.setIdentityM(this, 0) }
    private val finalTexMatrix = FloatArray(16).apply { android.opengl.Matrix.setIdentityM(this, 0) }
    @Volatile
    private var updateSurface = false

    private var viewportWidth = 1080
    private var viewportHeight = 1920
    private var bufferWidth = 1920
    private var bufferHeight = 1080
    private var sensorRotation = 90
    private var isFront = false

    // Uniform locations
    private var uTexMatrixLoc = -1
    private var sTextureLoc = -1
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

    private val lutTextureCache = HashMap<String, Int>()

    @Volatile
    private var activePreset: CameraPreset = PresetCatalog.allPresets.first()

    @Volatile
    private var currentTuning: UserTuningConfig = UserTuningConfig()

    // Performance telemetry
    var lastGpuTimeMs: Float = 0f
        private set
    var currentFps: Float = 0f
        private set
    private var frameCount = 0
    private var lastFpsTimestamp = System.nanoTime()

    // Default 1:1 quad
    private val squareCoords = floatArrayOf(
        -1.0f,  1.0f, 0.0f,
        -1.0f, -1.0f, 0.0f,
         1.0f,  1.0f, 0.0f,
         1.0f, -1.0f, 0.0f
    )

    // Canonical OpenGL texture coordinates (0,0 bottom-left to 1,1 top-right)
    private val textureCoords = floatArrayOf(
        0.0f, 1.0f, // Vertex 0: Top-Left
        0.0f, 0.0f, // Vertex 1: Bottom-Left
        1.0f, 1.0f, // Vertex 2: Top-Right
        1.0f, 0.0f  // Vertex 3: Bottom-Right
    )

    init {
        vertexBuffer = ByteBuffer.allocateDirect(squareCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(squareCoords)
        vertexBuffer.position(0)

        textureCoordBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(textureCoords)
        textureCoordBuffer.position(0)
    }

    private fun requestRenderSafe() {
        try {
            glSurfaceView.requestRender()
        } catch (t: Throwable) {
            // Safe fallback if GL thread is not yet initialized or already released
        }
    }

    fun setCameraOrientation(rotationDegrees: Int, isFrontCamera: Boolean, bufW: Int, bufH: Int) {
        sensorRotation = rotationDegrees
        isFront = isFrontCamera
        bufferWidth = bufW
        bufferHeight = bufH
        updateGeometry()
        requestRenderSafe()
    }

    private fun updateGeometry() {
        // Natural 4:3 camera aspect fit (preserves full optical field of view, no double-crop)
        val rotW = if (sensorRotation == 90 || sensorRotation == 270) bufferHeight else bufferWidth
        val rotH = if (sensorRotation == 90 || sensorRotation == 270) bufferWidth else bufferHeight

        if (rotW > 0 && rotH > 0 && viewportWidth > 0 && viewportHeight > 0) {
            val bufferAspect = rotW.toFloat() / rotH.toFloat()
            val viewAspect = viewportWidth.toFloat() / viewportHeight.toFloat()

            val scaleX: Float
            val scaleY: Float
            if (viewAspect <= bufferAspect) {
                // Viewport is taller than sensor aspect (standard phone portrait) -> fit width
                scaleX = 1.0f
                scaleY = (viewAspect / bufferAspect).coerceAtMost(1.0f)
            } else {
                // Viewport is wider than sensor -> fit height
                scaleX = (bufferAspect / viewAspect).coerceAtMost(1.0f)
                scaleY = 1.0f
            }

            val quad = floatArrayOf(
                -scaleX,  scaleY, 0.0f,
                -scaleX, -scaleY, 0.0f,
                 scaleX,  scaleY, 0.0f,
                 scaleX, -scaleY, 0.0f
            )

            vertexBuffer.clear()
            vertexBuffer.put(quad)
            vertexBuffer.position(0)
        }
    }

    fun updatePreset(preset: CameraPreset) {
        activePreset = preset
        requestRenderSafe()
    }

    fun updateTuning(tuning: UserTuningConfig) {
        currentTuning = tuning
        requestRenderSafe()
    }

    private fun getLutAssetForPreset(presetId: String): String =
        ShaderConstants.getLutAssetForPreset(presetId)

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
                Log.d(TAG, "Uploaded GPU 3D LUT texture: $lutAsset (id=$texId)")
                texId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LUT asset: $lutAsset", e)
            0
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.05f, 0.05f, 0.07f, 1.0f)

        programId = GLShaderHelper.createProgram(
            ShaderConstants.VERTEX_SHADER_VIEWFINDER,
            ShaderConstants.FRAGMENT_SHADER_VIEWFINDER
        )

        if (programId == 0) {
            Log.e(TAG, "Failed to compile/link viewfinder shader program")
            return
        }

        // Cache uniform locations
        uTexMatrixLoc = GLES30.glGetUniformLocation(programId, "uTexMatrix")
        sTextureLoc = GLES30.glGetUniformLocation(programId, "sTexture")
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

        // Create OES external texture
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val mainHandler = Handler(Looper.getMainLooper())
        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(this@GLViewfinderRenderer, mainHandler)
            onSurfaceTextureReady(this)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        updateGeometry()
    }

    override fun onDrawFrame(gl: GL10?) {
        val startNs = System.nanoTime()

        synchronized(this) {
            if (updateSurface) {
                try {
                    surfaceTexture?.updateTexImage()
                    surfaceTexture?.getTransformMatrix(texMatrix)
                } catch (t: Throwable) {
                    Log.w(TAG, "Error updating viewfinder surface texture", t)
                }
                updateSurface = false
            }
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (programId == 0) return

        GLES30.glUseProgram(programId)

        // Set vertices
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, vertexBuffer)

        // Set texture coords
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 8, textureCoordBuffer)

        // Bind OES texture (unit 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glUniform1i(sTextureLoc, 0)

        // Bind 3D Hald CLUT Film Emulsion Texture (unit 1)
        val preset = activePreset
        val lutAsset = getLutAssetForPreset(preset.id)
        val lutId = getOrLoadLutTexture(lutAsset)
        if (lutId != 0 && uLutTextureLoc != -1) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutId)
            GLES30.glUniform1i(uLutTextureLoc, 1)
            GLES30.glUniform1i(uLutEnabledLoc, 1)
        } else if (uLutEnabledLoc != -1) {
            GLES30.glUniform1i(uLutEnabledLoc, 0)
        }

        // Upload transform matrix and viewport size
        if (isFront) {
            android.opengl.Matrix.setIdentityM(rotMatrix, 0)
            android.opengl.Matrix.translateM(rotMatrix, 0, 0.5f, 0.5f, 0f)
            android.opengl.Matrix.scaleM(rotMatrix, 0, -1f, 1f, 1f)
            android.opengl.Matrix.translateM(rotMatrix, 0, -0.5f, -0.5f, 0f)
            android.opengl.Matrix.multiplyMM(finalTexMatrix, 0, texMatrix, 0, rotMatrix, 0)
            GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, finalTexMatrix, 0)
        } else {
            GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
        }
        GLES30.glUniform2f(uViewportSizeLoc, viewportWidth.toFloat(), viewportHeight.toFloat())

        if (frameCount % 60 == 0) {
            Log.d(TAG, "RenderFrame: fps=$currentFps rot=$sensorRotation isFront=$isFront\ntexM = ${texMatrix.joinToString(" ")}\nfinalM = ${finalTexMatrix.joinToString(" ")}")
        }

        // Upload preset parameters combined with live user tuning offsets
        val tuning = currentTuning
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

        val grain = preset.grain
        val grainIntensity = if (grain.enabled) (grain.intensity + tuning.grainBoost).coerceAtLeast(0f) else 0f
        val frameSeed = (frameCount % 60).toFloat() / 60.0f
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

        // Measure GPU execution time and frame rate
        lastGpuTimeMs = (System.nanoTime() - startNs) / 1_000_000f
        frameCount++
        val now = System.nanoTime()
        if (now - lastFpsTimestamp >= 1_000_000_000L) {
            currentFps = frameCount * 1_000_000_000f / (now - lastFpsTimestamp)
            frameCount = 0
            lastFpsTimestamp = now
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        updateSurface = true
        requestRenderSafe()
    }
}
