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
import com.craftcam.app.camera.profile.LensProfile
import com.craftcam.app.camera.profile.LensType
import com.craftcam.app.gl.shaders.ShaderConstants
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.model.GrainBlendMode
import com.craftcam.app.model.NaturalCameraProfile
import com.craftcam.app.model.ProCameraConfig
import com.craftcam.app.presets.PresetCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private var uSoftnessLoc = -1

    private var uGrainIntensityLoc = -1
    private var uGrainScaleLoc = -1
    private var uIsColorNoiseLoc = -1
    private var uRandomSeedLoc = -1
    private var uGrainTextureLoc = -1
    private var uHasGrainTextureLoc = -1
    private var uGrainBlendModeLoc = -1
    private var uGrainOffsetLoc = -1
    private var uGrainShadowStrengthLoc = -1
    private var uGrainHighlightStrengthLoc = -1

    private var uHalationEnabledLoc = -1
    private var uHalationThresholdLoc = -1
    private var uHalationIntensityLoc = -1
    private var uHalationColorTintLoc = -1
    private var uLightLeakEnabledLoc = -1
    private var uLightLeakIntensityLoc = -1
    private var uScanlineIntensityLoc = -1
    private var uLutTextureLoc = -1
    private var sLut3DLoc = -1
    private var uUseLut3DLoc = -1
    private var uLutInputSpaceLoc = -1
    private var uClarityStrengthLoc = -1
    private var uSkinProtectionLoc = -1
    private var uLutEnabledLoc = -1
    private var uFilterIntensityLoc = -1
    private var uNaturalModeEnabledLoc = -1
    private var uNaturalShadowLiftLoc = -1

    // Reverse-engineering recipe parity uniforms
    private var uPreLinearizationEnabledLoc = -1
    private var uPreLinearizationStrengthLoc = -1
    private var uLutTextureBLoc = -1
    private var sLut3D_BLoc = -1
    private var uHasSecondaryLutLoc = -1
    private var uDualLutMixLoc = -1
    private var uToeLiftLoc = -1
    private var uShoulderClipLoc = -1
    private var uGrainLumaWeightEnabledLoc = -1
    private var uGrainLumaWeightPowerLoc = -1

    // Developer Stage Visual A/B Toggles
    private var uDebugStagePreLinearizationLoc = -1
    private var uDebugStageLutLoc = -1
    private var uDebugStageToneCurveLoc = -1
    private var uDebugStageGrainLoc = -1
    private var uDebugStageHalationLoc = -1
    private var uDebugStageOpticsLoc = -1
    private var uDebugStageVignetteLoc = -1

    // Pro Viewfinder Tools
    private var uFocusPeakingEnabledLoc = -1
    private var uPeakingColorLoc = -1
    private var uPeakingThresholdLoc = -1
    private var uZebraEnabledLoc = -1
    private var uZebraThresholdLoc = -1
    private var uZebraTimeLoc = -1

    // Camera Lens System
    private var uLensTypeLoc = -1
    private var uLensDistortionLoc = -1
    private var uLensEdgeCurvatureLoc = -1
    private var uLensVignetteLoc = -1
    private var uLensPrismDispersionLoc = -1
    private var uLensStarburstStrengthLoc = -1
    private var uLensStarburstThresholdLoc = -1
    private var uLensNdFactorLoc = -1
    private var uLensColorFlashTintLoc = -1
    private var uLensIsFlashActiveLoc = -1

    @Volatile
    private var currentLensProfile: LensProfile = LensProfile.NORMAL

    @Volatile
    private var isLensFlashActive: Boolean = false

    fun updateLensProfile(profile: LensProfile, isFlashActive: Boolean) {
        currentLensProfile = profile
        isLensFlashActive = isFlashActive
        requestRenderSafe()
    }

    private val lutTextureCache = HashMap<String, Int>()
    private val grainTextureCache = HashMap<String, Int>()

    @Volatile
    private var isNaturalMode: Boolean = false

    @Volatile
    private var isProMode: Boolean = false

    @Volatile
    private var naturalProfile: NaturalCameraProfile = NaturalCameraProfile.S21_PROFILE

    @Volatile
    private var proConfig: ProCameraConfig = ProCameraConfig()

    @Volatile
    private var activePreset: CameraPreset = PresetCatalog.allPresets.first()

    @Volatile
    private var currentTuning: UserTuningConfig = UserTuningConfig()

    var onHistogramUpdated: ((IntArray) -> Unit)? = null

    private val histogramPixelBuffer = ByteBuffer.allocateDirect(64 * 48 * 4).order(ByteOrder.nativeOrder())
    private val histogramBins = IntArray(256)
    @Volatile
    private var isComputingHistogram = false

    fun setNaturalMode(enabled: Boolean, profile: NaturalCameraProfile) {
        isNaturalMode = enabled
        naturalProfile = profile
        glSurfaceView.requestRender()
    }

    fun setProMode(enabled: Boolean, config: ProCameraConfig) {
        isProMode = enabled
        proConfig = config
        glSurfaceView.requestRender()
    }

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

    private var activeColorProfile: com.craftcam.app.color.ColorProfile = com.craftcam.app.color.ColorProfile.Natural

    fun updateColorScience(config: com.craftcam.app.color.ColorScienceConfig) {
        activeColorProfile = config.activeProfile
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

    private fun getOrLoadGrainTexture(grainAsset: String): Int {
        grainTextureCache[grainAsset]?.let { return it }
        return try {
            val resolvedAsset = if (grainAsset.startsWith("grains/")) grainAsset else {
                try {
                    context.assets.open(grainAsset).close()
                    grainAsset
                } catch (e: Exception) {
                    "grains/$grainAsset"
                }
            }
            context.assets.open(resolvedAsset).use { input ->
                val bmp = BitmapFactory.decodeStream(input) ?: return 0
                val texIds = IntArray(1)
                GLES30.glGenTextures(1, texIds, 0)
                val texId = texIds[0]
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
                bmp.recycle()
                grainTextureCache[grainAsset] = texId
                grainTextureCache[resolvedAsset] = texId
                Log.d(TAG, "Uploaded GPU Grain texture: $resolvedAsset (id=$texId)")
                texId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load grain asset: $grainAsset", e)
            0
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.05f, 0.05f, 0.07f, 1.0f)

        // 1. Initialize OES external texture & SurfaceTexture first so camera preview pipeline ALWAYS connects
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

        // 2. Compile full film simulation viewfinder program
        programId = GLShaderHelper.createProgram(
            ShaderConstants.VERTEX_SHADER_VIEWFINDER,
            ShaderConstants.FRAGMENT_SHADER_VIEWFINDER
        )

        // Safe fallback 1: if complex shader ever fails compilation, fall back to minimal passthrough
        if (programId == 0) {
            Log.e(TAG, "Failed to compile full viewfinder shader program, falling back to minimal passthrough")
            programId = GLShaderHelper.createProgram(
                ShaderConstants.VERTEX_SHADER_VIEWFINDER,
                ShaderConstants.FRAGMENT_SHADER_PASSTHROUGH
            )
        }

        // Safe fallback 2: universal GLES 2.0 passthrough for any device driver limitations
        if (programId == 0) {
            Log.e(TAG, "Failed to compile GLES3 passthrough, falling back to universal GLES2 passthrough")
            programId = GLShaderHelper.createProgram(
                ShaderConstants.VERTEX_SHADER_PASSTHROUGH_ES2,
                ShaderConstants.FRAGMENT_SHADER_PASSTHROUGH_ES2
            )
        }

        if (programId == 0) {
            Log.e(TAG, "CRITICAL: All viewfinder shader compilation paths failed")
            return
        }

        // 3. Cache uniform locations
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
        uSoftnessLoc = GLES30.glGetUniformLocation(programId, "uSoftness")

        uGrainIntensityLoc = GLES30.glGetUniformLocation(programId, "uGrainIntensity")
        uGrainScaleLoc = GLES30.glGetUniformLocation(programId, "uGrainScale")
        uIsColorNoiseLoc = GLES30.glGetUniformLocation(programId, "uIsColorNoise")
        uRandomSeedLoc = GLES30.glGetUniformLocation(programId, "uRandomSeed")
        uGrainTextureLoc = GLES30.glGetUniformLocation(programId, "sGrainTexture")
        uHasGrainTextureLoc = GLES30.glGetUniformLocation(programId, "uHasGrainTexture")
        uGrainBlendModeLoc = GLES30.glGetUniformLocation(programId, "uGrainBlendMode")
        uGrainOffsetLoc = GLES30.glGetUniformLocation(programId, "uGrainOffset")
        uGrainShadowStrengthLoc = GLES30.glGetUniformLocation(programId, "uGrainShadowStrength")
        uGrainHighlightStrengthLoc = GLES30.glGetUniformLocation(programId, "uGrainHighlightStrength")

        uHalationEnabledLoc = GLES30.glGetUniformLocation(programId, "uHalationEnabled")
        uHalationThresholdLoc = GLES30.glGetUniformLocation(programId, "uHalationThreshold")
        uHalationIntensityLoc = GLES30.glGetUniformLocation(programId, "uHalationIntensity")
        uHalationColorTintLoc = GLES30.glGetUniformLocation(programId, "uHalationColorTint")
        uLightLeakEnabledLoc = GLES30.glGetUniformLocation(programId, "uLightLeakEnabled")
        uLightLeakIntensityLoc = GLES30.glGetUniformLocation(programId, "uLightLeakIntensity")
        uScanlineIntensityLoc = GLES30.glGetUniformLocation(programId, "uScanlineIntensity")
        uLutTextureLoc = GLES30.glGetUniformLocation(programId, "uLutTexture")
        sLut3DLoc = GLES30.glGetUniformLocation(programId, "sLut3D")
        uUseLut3DLoc = GLES30.glGetUniformLocation(programId, "uUseLut3D")
        uLutInputSpaceLoc = GLES30.glGetUniformLocation(programId, "uLutInputSpace")
        uClarityStrengthLoc = GLES30.glGetUniformLocation(programId, "uClarityStrength")
        uSkinProtectionLoc = GLES30.glGetUniformLocation(programId, "uSkinProtection")
        uLutEnabledLoc = GLES30.glGetUniformLocation(programId, "uLutEnabled")
        uFilterIntensityLoc = GLES30.glGetUniformLocation(programId, "uFilterIntensity")
        uNaturalModeEnabledLoc = GLES30.glGetUniformLocation(programId, "uNaturalModeEnabled")
        uNaturalShadowLiftLoc = GLES30.glGetUniformLocation(programId, "uNaturalShadowLift")

        // Parity & Tuning Uniform Locations
        uPreLinearizationEnabledLoc = GLES30.glGetUniformLocation(programId, "uPreLinearizationEnabled")
        uPreLinearizationStrengthLoc = GLES30.glGetUniformLocation(programId, "uPreLinearizationStrength")
        uLutTextureBLoc = GLES30.glGetUniformLocation(programId, "uLutTextureB")
        sLut3D_BLoc = GLES30.glGetUniformLocation(programId, "sLut3D_B")
        uHasSecondaryLutLoc = GLES30.glGetUniformLocation(programId, "uHasSecondaryLut")
        uDualLutMixLoc = GLES30.glGetUniformLocation(programId, "uDualLutMix")
        uToeLiftLoc = GLES30.glGetUniformLocation(programId, "uToeLift")
        uShoulderClipLoc = GLES30.glGetUniformLocation(programId, "uShoulderClip")
        uGrainLumaWeightEnabledLoc = GLES30.glGetUniformLocation(programId, "uGrainLumaWeightEnabled")
        uGrainLumaWeightPowerLoc = GLES30.glGetUniformLocation(programId, "uGrainLumaWeightPower")

        // Developer Stage Visual A/B Toggles
        uDebugStagePreLinearizationLoc = GLES30.glGetUniformLocation(programId, "uDebugStagePreLinearization")
        uDebugStageLutLoc = GLES30.glGetUniformLocation(programId, "uDebugStageLut")
        uDebugStageToneCurveLoc = GLES30.glGetUniformLocation(programId, "uDebugStageToneCurve")
        uDebugStageGrainLoc = GLES30.glGetUniformLocation(programId, "uDebugStageGrain")
        uDebugStageHalationLoc = GLES30.glGetUniformLocation(programId, "uDebugStageHalation")
        uDebugStageOpticsLoc = GLES30.glGetUniformLocation(programId, "uDebugStageOptics")
        uDebugStageVignetteLoc = GLES30.glGetUniformLocation(programId, "uDebugStageVignette")

        uFocusPeakingEnabledLoc = GLES30.glGetUniformLocation(programId, "uFocusPeakingEnabled")
        uPeakingColorLoc = GLES30.glGetUniformLocation(programId, "uPeakingColor")
        uPeakingThresholdLoc = GLES30.glGetUniformLocation(programId, "uPeakingThreshold")
        uZebraEnabledLoc = GLES30.glGetUniformLocation(programId, "uZebraEnabled")
        uZebraThresholdLoc = GLES30.glGetUniformLocation(programId, "uZebraThreshold")
        uZebraTimeLoc = GLES30.glGetUniformLocation(programId, "uZebraTime")

        // Cache Camera Lens System uniforms
        uLensTypeLoc = GLES30.glGetUniformLocation(programId, "uLensType")
        uLensDistortionLoc = GLES30.glGetUniformLocation(programId, "uLensDistortion")
        uLensEdgeCurvatureLoc = GLES30.glGetUniformLocation(programId, "uLensEdgeCurvature")
        uLensVignetteLoc = GLES30.glGetUniformLocation(programId, "uLensVignette")
        uLensPrismDispersionLoc = GLES30.glGetUniformLocation(programId, "uLensPrismDispersion")
        uLensStarburstStrengthLoc = GLES30.glGetUniformLocation(programId, "uLensStarburstStrength")
        uLensStarburstThresholdLoc = GLES30.glGetUniformLocation(programId, "uLensStarburstThreshold")
        uLensNdFactorLoc = GLES30.glGetUniformLocation(programId, "uLensNdFactor")
        uLensColorFlashTintLoc = GLES30.glGetUniformLocation(programId, "uLensColorFlashTint")
        uLensIsFlashActiveLoc = GLES30.glGetUniformLocation(programId, "uLensIsFlashActive")
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
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
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

        // Upload hardware texture transform matrix and viewport dimensions
        if (uTexMatrixLoc != -1) {
            GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
        }
        if (uViewportSizeLoc != -1) {
            val vw = if (viewportWidth > 0) viewportWidth.toFloat() else 1080f
            val vh = if (viewportHeight > 0) viewportHeight.toFloat() else 1920f
            GLES30.glUniform2f(uViewportSizeLoc, vw, vh)
        }

        if (isProMode) {
            // Pro Camera Mode: 100% clean, neutral preview, 0 film simulation
            GLES30.glUniform1i(uNaturalModeEnabledLoc, 0)
            GLES30.glUniform1f(uNaturalShadowLiftLoc, 0f)
            GLES30.glUniform1i(uLutEnabledLoc, 0)
            if (uFilterIntensityLoc != -1) GLES30.glUniform1f(uFilterIntensityLoc, 0f)
            GLES30.glUniform1i(uHasGrainTextureLoc, 0)
            GLES30.glUniform1f(uGrainIntensityLoc, 0f)
            GLES30.glUniform1i(uHalationEnabledLoc, 0)
            GLES30.glUniform1i(uLightLeakEnabledLoc, 0)
            GLES30.glUniform1f(uScanlineIntensityLoc, 0f)
            GLES30.glUniform1f(uVignetteIntensityLoc, 0f)
            GLES30.glUniform1f(uChromaticAberrationLoc, 0f)
            GLES30.glUniform1f(uBarrelDistortionLoc, 0f)
            GLES30.glUniform1f(uSoftnessLoc, 0f)
            GLES30.glUniform1f(uFadeLoc, 0f)
            if (uClarityStrengthLoc != -1) GLES30.glUniform1f(uClarityStrengthLoc, 0f)
            if (uSkinProtectionLoc != -1) GLES30.glUniform1f(uSkinProtectionLoc, 0f)
            if (uUseLut3DLoc != -1) GLES30.glUniform1i(uUseLut3DLoc, 0)
            if (uLutInputSpaceLoc != -1) GLES30.glUniform1i(uLutInputSpaceLoc, 0)
            GLES30.glUniform3f(uShadowTintLoc, 0f, 0f, 0f)
            GLES30.glUniform1f(uGammaLoc, 1.0f)
            GLES30.glUniform1f(uContrastLoc, 1.0f)
            GLES30.glUniform1f(uSaturationLoc, 1.0f)
            GLES30.glUniform1f(uTemperatureLoc, 0.0f)
            GLES30.glUniform1f(uTintLoc, 0.0f)
            GLES30.glUniform1f(uHighlightRollOffLoc, 1.0f)

            if (uLensTypeLoc != -1) GLES30.glUniform1i(uLensTypeLoc, 0)
            if (uLensDistortionLoc != -1) GLES30.glUniform1f(uLensDistortionLoc, 0f)
            if (uLensEdgeCurvatureLoc != -1) GLES30.glUniform1f(uLensEdgeCurvatureLoc, 0f)
            if (uLensVignetteLoc != -1) GLES30.glUniform1f(uLensVignetteLoc, 0f)
            if (uLensPrismDispersionLoc != -1) GLES30.glUniform1f(uLensPrismDispersionLoc, 0f)
            if (uLensStarburstStrengthLoc != -1) GLES30.glUniform1f(uLensStarburstStrengthLoc, 0f)
            if (uLensNdFactorLoc != -1) GLES30.glUniform1f(uLensNdFactorLoc, 1.0f)
            if (uLensIsFlashActiveLoc != -1) GLES30.glUniform1i(uLensIsFlashActiveLoc, 0)

            if (uPreLinearizationEnabledLoc != -1) GLES30.glUniform1i(uPreLinearizationEnabledLoc, 0)
            if (uPreLinearizationStrengthLoc != -1) GLES30.glUniform1f(uPreLinearizationStrengthLoc, 0f)
            if (uHasSecondaryLutLoc != -1) GLES30.glUniform1i(uHasSecondaryLutLoc, 0)
            if (uToeLiftLoc != -1) GLES30.glUniform1f(uToeLiftLoc, 0f)
            if (uShoulderClipLoc != -1) GLES30.glUniform1f(uShoulderClipLoc, 1f)
            if (uHalationColorTintLoc != -1) GLES30.glUniform3f(uHalationColorTintLoc, 1.0f, 0.32f, 0.10f)
            if (uGrainLumaWeightEnabledLoc != -1) GLES30.glUniform1i(uGrainLumaWeightEnabledLoc, 0)
            if (uGrainLumaWeightPowerLoc != -1) GLES30.glUniform1f(uGrainLumaWeightPowerLoc, 1f)
            if (uDebugStagePreLinearizationLoc != -1) GLES30.glUniform1i(uDebugStagePreLinearizationLoc, 1)
            if (uDebugStageLutLoc != -1) GLES30.glUniform1i(uDebugStageLutLoc, 1)
            if (uDebugStageToneCurveLoc != -1) GLES30.glUniform1i(uDebugStageToneCurveLoc, 1)
            if (uDebugStageGrainLoc != -1) GLES30.glUniform1i(uDebugStageGrainLoc, 1)
            if (uDebugStageHalationLoc != -1) GLES30.glUniform1i(uDebugStageHalationLoc, 1)
            if (uDebugStageOpticsLoc != -1) GLES30.glUniform1i(uDebugStageOpticsLoc, 1)
            if (uDebugStageVignetteLoc != -1) GLES30.glUniform1i(uDebugStageVignetteLoc, 1)

            // Focus Peaking
            if (proConfig.focusPeakingEnabled && uFocusPeakingEnabledLoc != -1) {
                GLES30.glUniform1i(uFocusPeakingEnabledLoc, 1)
                val peakColor = proConfig.peakingColor
                GLES30.glUniform3f(uPeakingColorLoc, peakColor.r, peakColor.g, peakColor.b)
                GLES30.glUniform1f(uPeakingThresholdLoc, proConfig.peakingThreshold)
            } else if (uFocusPeakingEnabledLoc != -1) {
                GLES30.glUniform1i(uFocusPeakingEnabledLoc, 0)
            }

            // Zebra Warning
            if (proConfig.zebraEnabled && uZebraEnabledLoc != -1) {
                GLES30.glUniform1i(uZebraEnabledLoc, 1)
                GLES30.glUniform1f(uZebraThresholdLoc, proConfig.zebraThreshold)
                val time = (android.os.SystemClock.uptimeMillis() % 100000L) / 1000.0f
                GLES30.glUniform1f(uZebraTimeLoc, time)
            } else if (uZebraEnabledLoc != -1) {
                GLES30.glUniform1i(uZebraEnabledLoc, 0)
            }
        } else if (isNaturalMode) {
            if (uFocusPeakingEnabledLoc != -1) GLES30.glUniform1i(uFocusPeakingEnabledLoc, 0)
            if (uZebraEnabledLoc != -1) GLES30.glUniform1i(uZebraEnabledLoc, 0)

            // Natural Camera Mode: 100% clean, bypasses all film simulation
            GLES30.glUniform1i(uNaturalModeEnabledLoc, 1)
            GLES30.glUniform1f(uNaturalShadowLiftLoc, activeColorProfile.toneCurve.shadowLift)
            GLES30.glUniform1i(uLutEnabledLoc, 0)
            if (uFilterIntensityLoc != -1) GLES30.glUniform1f(uFilterIntensityLoc, 0f)
            GLES30.glUniform1i(uHasGrainTextureLoc, 0)
            GLES30.glUniform1f(uGrainIntensityLoc, 0f)
            GLES30.glUniform1i(uHalationEnabledLoc, 0)
            GLES30.glUniform1i(uLightLeakEnabledLoc, 0)
            GLES30.glUniform1f(uScanlineIntensityLoc, 0f)
            GLES30.glUniform1f(uVignetteIntensityLoc, 0f)
            GLES30.glUniform1f(uChromaticAberrationLoc, 0f)
            GLES30.glUniform1f(uBarrelDistortionLoc, 0f)
            GLES30.glUniform1f(uSoftnessLoc, 0f)
            GLES30.glUniform1f(uFadeLoc, 0f)
            if (uClarityStrengthLoc != -1) GLES30.glUniform1f(uClarityStrengthLoc, 0f)
            if (uSkinProtectionLoc != -1) GLES30.glUniform1f(uSkinProtectionLoc, 0f)
            if (uUseLut3DLoc != -1) GLES30.glUniform1i(uUseLut3DLoc, 0)
            if (uLutInputSpaceLoc != -1) GLES30.glUniform1i(uLutInputSpaceLoc, 0)
            GLES30.glUniform3f(uShadowTintLoc, 0f, 0f, 0f)
            GLES30.glUniform1f(uGammaLoc, 1.0f)

            GLES30.glUniform1f(uContrastLoc, naturalProfile.contrast)
            GLES30.glUniform1f(uSaturationLoc, naturalProfile.saturation)
            GLES30.glUniform1f(uTemperatureLoc, naturalProfile.temperature)
            GLES30.glUniform1f(uTintLoc, naturalProfile.tint)
            GLES30.glUniform1f(uHighlightRollOffLoc, naturalProfile.highlightRollOff)

            if (uLensTypeLoc != -1) GLES30.glUniform1i(uLensTypeLoc, 0)
            if (uLensDistortionLoc != -1) GLES30.glUniform1f(uLensDistortionLoc, 0f)
            if (uLensEdgeCurvatureLoc != -1) GLES30.glUniform1f(uLensEdgeCurvatureLoc, 0f)
            if (uLensVignetteLoc != -1) GLES30.glUniform1f(uLensVignetteLoc, 0f)
            if (uLensPrismDispersionLoc != -1) GLES30.glUniform1f(uLensPrismDispersionLoc, 0f)
            if (uLensStarburstStrengthLoc != -1) GLES30.glUniform1f(uLensStarburstStrengthLoc, 0f)
            if (uLensNdFactorLoc != -1) GLES30.glUniform1f(uLensNdFactorLoc, 1.0f)
            if (uLensIsFlashActiveLoc != -1) GLES30.glUniform1i(uLensIsFlashActiveLoc, 0)

            if (uPreLinearizationEnabledLoc != -1) GLES30.glUniform1i(uPreLinearizationEnabledLoc, 0)
            if (uPreLinearizationStrengthLoc != -1) GLES30.glUniform1f(uPreLinearizationStrengthLoc, 0f)
            if (uHasSecondaryLutLoc != -1) GLES30.glUniform1i(uHasSecondaryLutLoc, 0)
            if (uToeLiftLoc != -1) GLES30.glUniform1f(uToeLiftLoc, 0f)
            if (uShoulderClipLoc != -1) GLES30.glUniform1f(uShoulderClipLoc, 1f)
            if (uHalationColorTintLoc != -1) GLES30.glUniform3f(uHalationColorTintLoc, 1.0f, 0.32f, 0.10f)
            if (uGrainLumaWeightEnabledLoc != -1) GLES30.glUniform1i(uGrainLumaWeightEnabledLoc, 0)
            if (uGrainLumaWeightPowerLoc != -1) GLES30.glUniform1f(uGrainLumaWeightPowerLoc, 1f)
            if (uDebugStagePreLinearizationLoc != -1) GLES30.glUniform1i(uDebugStagePreLinearizationLoc, 1)
            if (uDebugStageLutLoc != -1) GLES30.glUniform1i(uDebugStageLutLoc, 1)
            if (uDebugStageToneCurveLoc != -1) GLES30.glUniform1i(uDebugStageToneCurveLoc, 1)
            if (uDebugStageGrainLoc != -1) GLES30.glUniform1i(uDebugStageGrainLoc, 1)
            if (uDebugStageHalationLoc != -1) GLES30.glUniform1i(uDebugStageHalationLoc, 1)
            if (uDebugStageOpticsLoc != -1) GLES30.glUniform1i(uDebugStageOpticsLoc, 1)
            if (uDebugStageVignetteLoc != -1) GLES30.glUniform1i(uDebugStageVignetteLoc, 1)
        } else {
            if (uFocusPeakingEnabledLoc != -1) GLES30.glUniform1i(uFocusPeakingEnabledLoc, 0)
            if (uZebraEnabledLoc != -1) GLES30.glUniform1i(uZebraEnabledLoc, 0)

            GLES30.glUniform1i(uNaturalModeEnabledLoc, 0)
            GLES30.glUniform1f(uNaturalShadowLiftLoc, 0f)

            // Bind 3D Hald CLUT Film Emulsion Texture (unit 1)
            val preset = activePreset
            val unifiedProfile = com.craftcam.app.camera.profile.CameraProfileCatalog.getUnifiedProfile(preset)
            val canonicalLut = getLutAssetForPreset(preset.id)
            val profileLut = unifiedProfile.filmProfile.lutAssetPath?.ifEmpty { null }
            val lutAsset = profileLut ?: canonicalLut
            var cachedLut = LutTextureCache.getOrCreateLutTexture(context, lutAsset, prefer3D = true)
            if (cachedLut == null && lutAsset != canonicalLut) {
                cachedLut = LutTextureCache.getOrCreateLutTexture(context, canonicalLut, prefer3D = true)
            }
            if (cachedLut != null && uLutEnabledLoc != -1) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                if (cachedLut.target == GLES30.GL_TEXTURE_3D && sLut3DLoc != -1) {
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, cachedLut.textureId)
                    GLES30.glUniform1i(sLut3DLoc, 1)
                    if (uUseLut3DLoc != -1) GLES30.glUniform1i(uUseLut3DLoc, 1)
                } else if (uLutTextureLoc != -1) {
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, cachedLut.textureId)
                    GLES30.glUniform1i(uLutTextureLoc, 1)
                    if (uUseLut3DLoc != -1) GLES30.glUniform1i(uUseLut3DLoc, 0)
                }
                GLES30.glUniform1i(uLutEnabledLoc, 1)
                val spaceInt = when (unifiedProfile.filmProfile.lutInputSpace) {
                    com.craftcam.app.camera.profile.LutInputSpace.SRGB -> 0
                    com.craftcam.app.camera.profile.LutInputSpace.LINEAR -> 1
                    com.craftcam.app.camera.profile.LutInputSpace.CUSTOM_PRETRANSFORM -> 2
                }
                if (uLutInputSpaceLoc != -1) GLES30.glUniform1i(uLutInputSpaceLoc, spaceInt)
            } else if (uLutEnabledLoc != -1) {
                GLES30.glUniform1i(uLutEnabledLoc, 0)
                if (uUseLut3DLoc != -1) GLES30.glUniform1i(uUseLut3DLoc, 0)
            }

            val tuning = currentTuning

            // Pre-linearization ISP flattening (CUSTOM_PRETRANSFORM)
            if (uPreLinearizationEnabledLoc != -1) {
                val preLinActive = tuning.preLinearizationEnabled && unifiedProfile.filmProfile.lutInputSpace == com.craftcam.app.camera.profile.LutInputSpace.CUSTOM_PRETRANSFORM
                GLES30.glUniform1i(uPreLinearizationEnabledLoc, if (preLinActive) 1 else 0)
            }
            if (uPreLinearizationStrengthLoc != -1) {
                GLES30.glUniform1f(uPreLinearizationStrengthLoc, tuning.preLinearizationStrength)
            }

            // Dual-LUT support (Section 7)
            val secLutAsset = unifiedProfile.filmProfile.secondaryLutAssetPath
            if (!secLutAsset.isNullOrEmpty() && uHasSecondaryLutLoc != -1) {
                val cachedSecLut = LutTextureCache.getOrCreateLutTexture(context, secLutAsset, prefer3D = true)
                if (cachedSecLut != null) {
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
                    if (cachedSecLut.target == GLES30.GL_TEXTURE_3D && sLut3D_BLoc != -1) {
                        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, cachedSecLut.textureId)
                        GLES30.glUniform1i(sLut3D_BLoc, 3)
                    } else if (uLutTextureBLoc != -1) {
                        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, cachedSecLut.textureId)
                        GLES30.glUniform1i(uLutTextureBLoc, 3)
                    }
                    GLES30.glUniform1i(uHasSecondaryLutLoc, 1)
                    if (uDualLutMixLoc != -1) {
                        GLES30.glUniform1f(uDualLutMixLoc, unifiedProfile.filmProfile.dualLutBlendFactor)
                    }
                } else {
                    GLES30.glUniform1i(uHasSecondaryLutLoc, 0)
                }
            } else if (uHasSecondaryLutLoc != -1) {
                GLES30.glUniform1i(uHasSecondaryLutLoc, 0)
            }

            val isCamcorder = preset.category == CameraCategory.CAMCORDER
            val intensity = tuning.filterIntensity.coerceIn(0.0f, 1.0f)
            if (uFilterIntensityLoc != -1) {
                GLES30.glUniform1f(uFilterIntensityLoc, intensity)
            }

            // Resolve single authoritative GrainStage (prevents double grain stacking)
            val resolvedGrain = com.craftcam.app.processor.GrainStage.resolve(preset, unifiedProfile.grainProfile, tuning, intensity)
            val grainTexId = if (resolvedGrain.useScannedTexture && !resolvedGrain.assetPath.isNullOrEmpty()) {
                getOrLoadGrainTexture(resolvedGrain.assetPath)
            } else 0
            if (grainTexId != 0 && uGrainTextureLoc != -1) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, grainTexId)
                GLES30.glUniform1i(uGrainTextureLoc, 2)
                GLES30.glUniform1i(uHasGrainTextureLoc, 1)
            } else if (uHasGrainTextureLoc != -1) {
                GLES30.glUniform1i(uHasGrainTextureLoc, 0)
            }

            val color = preset.colorGrading
            val film = unifiedProfile.filmProfile
            val baseContrast = if (film.contrast != 1.0f) film.contrast else color.contrast
            val baseSaturation = if (film.saturation != 1.0f) film.saturation else color.saturation
            val baseTemp = if (film.temperature != 0.0f) film.temperature else color.temperature
            val baseTint = if (film.tint != 0.0f) film.tint else color.tint
            val baseHighlightRollOff = if (film.highlightRollOff != 1.0f) film.highlightRollOff else color.highlightRollOff
            val baseShadowR = if (film.shadowTintR != 0.0f) film.shadowTintR else color.shadowTintR
            val baseShadowG = if (film.shadowTintG != 0.0f) film.shadowTintG else color.shadowTintG
            val baseShadowB = if (film.shadowTintB != 0.0f) film.shadowTintB else color.shadowTintB

            val rawContrast = (baseContrast * (1.0f + tuning.contrastOffset)).coerceAtLeast(0.1f)
            val contrast = 1.0f + (rawContrast - 1.0f) * intensity
            val rawSat = (baseSaturation * (1.0f + tuning.saturationOffset)).coerceAtLeast(0.0f)
            val saturation = 1.0f + (rawSat - 1.0f) * intensity
            val temperature = ((baseTemp + tuning.temperatureOffset) * intensity).coerceIn(-1.0f, 1.0f)
            val tint = ((baseTint + tuning.tintOffset) * intensity).coerceIn(-1.0f, 1.0f)
            val gamma = 1.0f + (color.gamma - 1.0f) * intensity
            val fade = color.fade * intensity
            val highlightRollOff = 1.0f - (1.0f - baseHighlightRollOff) * intensity

            GLES30.glUniform1f(uContrastLoc, contrast)
            GLES30.glUniform1f(uSaturationLoc, saturation)
            GLES30.glUniform1f(uTemperatureLoc, temperature)
            GLES30.glUniform1f(uTintLoc, tint)
            GLES30.glUniform1f(uGammaLoc, gamma)
            GLES30.glUniform1f(uFadeLoc, fade)
            GLES30.glUniform1f(uHighlightRollOffLoc, highlightRollOff)
            GLES30.glUniform3f(uShadowTintLoc, baseShadowR * intensity, baseShadowG * intensity, baseShadowB * intensity)
            if (uToeLiftLoc != -1) GLES30.glUniform1f(uToeLiftLoc, unifiedProfile.filmProfile.toeLift * intensity)
            if (uShoulderClipLoc != -1) {
                val clip = 1.0f - (1.0f - unifiedProfile.filmProfile.shoulderClip) * intensity
                GLES30.glUniform1f(uShoulderClipLoc, clip)
            }
            if (uClarityStrengthLoc != -1) GLES30.glUniform1f(uClarityStrengthLoc, unifiedProfile.filmProfile.clarityStrength * intensity)
            if (uSkinProtectionLoc != -1) GLES30.glUniform1f(uSkinProtectionLoc, unifiedProfile.filmProfile.skinProtection)

            val lens = preset.lens
            val vigIntensity = (lens.vignetteIntensity * tuning.vignetteBoost * intensity).coerceIn(0f, 1f)
            GLES30.glUniform1f(uVignetteIntensityLoc, vigIntensity)
            GLES30.glUniform1f(uVignetteRadiusLoc, lens.vignetteRadius)
            GLES30.glUniform1f(uChromaticAberrationLoc, lens.chromaticAberration * intensity)
            GLES30.glUniform1f(uBarrelDistortionLoc, lens.barrelDistortion * intensity)
            GLES30.glUniform1f(uSoftnessLoc, lens.softness * intensity)

            val grainIntensity = if (resolvedGrain.useScannedTexture) resolvedGrain.effectiveIntensity else resolvedGrain.proceduralIntensity
            val frameSeed = ((frameCount / 2) % 60).toFloat() / 60.0f
            GLES30.glUniform1f(uGrainIntensityLoc, grainIntensity)
            GLES30.glUniform1f(uGrainScaleLoc, resolvedGrain.scale)
            GLES30.glUniform1i(uIsColorNoiseLoc, if (resolvedGrain.isColorNoise) 1 else 0)
            GLES30.glUniform1f(uRandomSeedLoc, frameSeed)

            val blendModeInt = when (resolvedGrain.blendMode) {
                GrainBlendMode.OVERLAY -> 0
                GrainBlendMode.SCREEN -> 1
                GrainBlendMode.MULTIPLY -> 2
                GrainBlendMode.NORMAL -> 3
            }
            GLES30.glUniform1i(uGrainBlendModeLoc, blendModeInt)
            GLES30.glUniform1f(uGrainShadowStrengthLoc, resolvedGrain.shadowStrength)
            GLES30.glUniform1f(uGrainHighlightStrengthLoc, resolvedGrain.highlightStrength)
            if (uGrainLumaWeightEnabledLoc != -1) {
                GLES30.glUniform1i(uGrainLumaWeightEnabledLoc, if (resolvedGrain.luminanceWeightingEnabled) 1 else 0)
            }
            if (uGrainLumaWeightPowerLoc != -1) {
                GLES30.glUniform1f(uGrainLumaWeightPowerLoc, resolvedGrain.luminanceWeightingPower)
            }

            // Dynamic 2D jitter offset updates for authentic analog shutter movement
            val seedX = ((frameCount * 73) % 1000) / 1000.0f
            val seedY = ((frameCount * 157) % 1000) / 1000.0f
            GLES30.glUniform2f(uGrainOffsetLoc, seedX, seedY)

            val halation = preset.halation
            val halationActive = tuning.halationEnabled && (halation?.enabled == true || unifiedProfile.filmProfile.halationThreshold < 0.99f) && intensity > 0.01f
            GLES30.glUniform1i(uHalationEnabledLoc, if (halationActive) 1 else 0)
            val effHalationThresh = halation?.threshold ?: unifiedProfile.filmProfile.halationThreshold
            GLES30.glUniform1f(uHalationThresholdLoc, effHalationThresh)
            val effHalationInt = (halation?.intensity ?: unifiedProfile.filmProfile.halationIntensity) * intensity
            GLES30.glUniform1f(uHalationIntensityLoc, effHalationInt)
            if (uHalationColorTintLoc != -1) {
                GLES30.glUniform3f(
                    uHalationColorTintLoc,
                    unifiedProfile.filmProfile.halationColorTintR,
                    unifiedProfile.filmProfile.halationColorTintG,
                    unifiedProfile.filmProfile.halationColorTintB
                )
            }

            val leak = preset.lightLeak
            val leakActive = tuning.lightLeakEnabled && leak?.enabled == true && intensity > 0.01f
            GLES30.glUniform1i(uLightLeakEnabledLoc, if (leakActive) 1 else 0)
            GLES30.glUniform1f(uLightLeakIntensityLoc, (leak?.intensity ?: 0f) * intensity)

            GLES30.glUniform1f(uScanlineIntensityLoc, if (isCamcorder) 0.6f * intensity else 0f)

            // Visual A/B Developer Stage Toggles (Section 24)
            if (uDebugStagePreLinearizationLoc != -1) GLES30.glUniform1i(uDebugStagePreLinearizationLoc, if (tuning.debugStagePreLinearization) 1 else 0)
            if (uDebugStageLutLoc != -1) GLES30.glUniform1i(uDebugStageLutLoc, if (tuning.debugStageLut) 1 else 0)
            if (uDebugStageToneCurveLoc != -1) GLES30.glUniform1i(uDebugStageToneCurveLoc, if (tuning.debugStageToneCurve) 1 else 0)
            if (uDebugStageGrainLoc != -1) GLES30.glUniform1i(uDebugStageGrainLoc, if (tuning.debugStageGrain) 1 else 0)
            if (uDebugStageHalationLoc != -1) GLES30.glUniform1i(uDebugStageHalationLoc, if (tuning.debugStageHalation) 1 else 0)
            if (uDebugStageOpticsLoc != -1) GLES30.glUniform1i(uDebugStageOpticsLoc, if (tuning.debugStageOptics) 1 else 0)
            if (uDebugStageVignetteLoc != -1) GLES30.glUniform1i(uDebugStageVignetteLoc, if (tuning.debugStageVignette) 1 else 0)

            // Upload active camera lens optical configuration
            val lensProf = currentLensProfile
            val lensTypeInt = when (lensProf.type) {
                LensType.NORMAL -> 0
                LensType.WIDE -> 1
                LensType.FISHEYE_F -> 2
                LensType.FISHEYE_W -> 3
                LensType.PRISM -> 4
                LensType.STARBURST -> 5
                LensType.ND -> 6
                LensType.COLOR_FLASH -> 7
            }
            if (uLensTypeLoc != -1) GLES30.glUniform1i(uLensTypeLoc, lensTypeInt)
            if (uLensDistortionLoc != -1) GLES30.glUniform1f(uLensDistortionLoc, lensProf.distortionStrength)
            if (uLensEdgeCurvatureLoc != -1) GLES30.glUniform1f(uLensEdgeCurvatureLoc, lensProf.edgeCurvature)
            if (uLensVignetteLoc != -1) GLES30.glUniform1f(uLensVignetteLoc, lensProf.vignetteStrength)
            if (uLensPrismDispersionLoc != -1) GLES30.glUniform1f(uLensPrismDispersionLoc, lensProf.prismDispersion)
            if (uLensStarburstStrengthLoc != -1) GLES30.glUniform1f(uLensStarburstStrengthLoc, lensProf.starburstStrength)
            if (uLensStarburstThresholdLoc != -1) GLES30.glUniform1f(uLensStarburstThresholdLoc, lensProf.starburstThreshold)
            if (uLensNdFactorLoc != -1) GLES30.glUniform1f(uLensNdFactorLoc, lensProf.ndTransmissionFactor)
            if (uLensColorFlashTintLoc != -1) {
                val (tr, tg, tb) = lensProf.colorFlashTint.rgb
                GLES30.glUniform3f(uLensColorFlashTintLoc, tr, tg, tb)
            }
            if (uLensIsFlashActiveLoc != -1) {
                GLES30.glUniform1i(uLensIsFlashActiveLoc, if (isLensFlashActive) 1 else 0)
            }
        }

        // Draw quad
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)

        // Reset active texture to unit 0 to prevent state leakage to next frame
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        // Live Histogram Calculation (downsampled 64x48 non-blocking readback)
        if (isProMode && proConfig.histogramEnabled && !isComputingHistogram && (frameCount % 4 == 0) && viewportWidth >= 64 && viewportHeight >= 48) {
            computeHistogramDownsampled(viewportWidth, viewportHeight)
        }

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

    private fun computeHistogramDownsampled(w: Int, h: Int) {
        val startX = (w / 2 - 32).coerceAtLeast(0)
        val startY = (h / 2 - 24).coerceAtLeast(0)
        histogramPixelBuffer.rewind()
        GLES30.glReadPixels(startX, startY, 64, 48, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, histogramPixelBuffer)
        histogramPixelBuffer.rewind()
        isComputingHistogram = true
        CoroutineScope(Dispatchers.Default).launch {
            try {
                java.util.Arrays.fill(histogramBins, 0)
                val totalBytes = 64 * 48 * 4
                var idx = 0
                while (idx < totalBytes) {
                    val r = histogramPixelBuffer.get(idx).toInt() and 0xFF
                    val g = histogramPixelBuffer.get(idx + 1).toInt() and 0xFF
                    val b = histogramPixelBuffer.get(idx + 2).toInt() and 0xFF
                    val luma = (77 * r + 150 * g + 29 * b) shr 8
                    histogramBins[luma]++
                    idx += 4
                }
                val copy = histogramBins.clone()
                onHistogramUpdated?.invoke(copy)
            } catch (_: Throwable) {
            } finally {
                isComputingHistogram = false
            }
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        updateSurface = true
        requestRenderSafe()
    }
}
