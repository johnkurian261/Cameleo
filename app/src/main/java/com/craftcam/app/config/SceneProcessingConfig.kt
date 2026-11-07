package com.craftcam.app.config

import com.craftcam.app.scene.SceneType
import kotlin.math.roundToInt

/**
 * Encapsulates the visual rendering and computational parameters tailored
 * for a specific photographic scene type.
 */
data class ProcessingProfile(
    val name: String,
    val highlightRollOff: Float = 0.55f,      // 0.0 (raw clip) to 1.0 (asymptotic soft roll-off)
    val shadowLift: Float = 0.22f,            // 0.0 (anchor black) to 0.50 (lifted shadow detail)
    val exposureBias: Float = 0.0f,           // Hardware EV compensation hint (-1.5 to +1.0 EV)
    val skinSaturation: Float = 0.0f,         // Skin chromaticity offset (-0.30 to +0.30)
    val skinSharpeningDampening: Float = 0.60f,// Sharpening reduction factor over skin (0.0 to 1.0)
    val backgroundSharpening: Float = 0.35f,  // Microcontrast acutance outside subject (0.0 to 0.8)
    val backgroundNoiseReduction: Float = 0.40f, // Spatial chroma denoising power (0.0 to 1.0)
    val localContrast: Float = 0.12f,         // Restrained midtone depth enhancement (0.0 to 0.4)
    val microContrastGain: Float = 0.12f,     // Edge-aware texture acutance (0.0 to 0.3)
    val enableHdrBurst: Boolean = true,       // Whether multi-frame exposure fusion is favored
    val enableLowLightStack: Boolean = false  // Whether temporal short-exposure stacking is active
) {
    /**
     * Blends this profile with [other] by [weightOther] (0.0 = 100% this, 1.0 = 100% other).
     * Prevents abrupt visual jumps when confidence shifts or dual conditions occur.
     */
    fun blendWith(other: ProcessingProfile, weightOther: Float): ProcessingProfile {
        val w = weightOther.coerceIn(0.0f, 1.0f)
        val invW = 1.0f - w
        return ProcessingProfile(
            name = if (w > 0.5f) "${other.name}+${this.name}" else "${this.name}+${other.name}",
            highlightRollOff = invW * this.highlightRollOff + w * other.highlightRollOff,
            shadowLift = invW * this.shadowLift + w * other.shadowLift,
            exposureBias = invW * this.exposureBias + w * other.exposureBias,
            skinSaturation = invW * this.skinSaturation + w * other.skinSaturation,
            skinSharpeningDampening = invW * this.skinSharpeningDampening + w * other.skinSharpeningDampening,
            backgroundSharpening = invW * this.backgroundSharpening + w * other.backgroundSharpening,
            backgroundNoiseReduction = invW * this.backgroundNoiseReduction + w * other.backgroundNoiseReduction,
            localContrast = invW * this.localContrast + w * other.localContrast,
            microContrastGain = invW * this.microContrastGain + w * other.microContrastGain,
            enableHdrBurst = if (w > 0.5f) other.enableHdrBurst else this.enableHdrBurst,
            enableLowLightStack = if (w > 0.5f) other.enableLowLightStack else this.enableLowLightStack
        )
    }

    companion object {
        val NaturalDefault = ProcessingProfile(
            name = "NaturalDefault",
            highlightRollOff = 0.55f,
            shadowLift = 0.22f,
            exposureBias = 0.0f,
            skinSaturation = 0.0f,
            skinSharpeningDampening = 0.60f,
            backgroundSharpening = 0.35f,
            backgroundNoiseReduction = 0.40f,
            localContrast = 0.12f,
            microContrastGain = 0.12f,
            enableHdrBurst = false,
            enableLowLightStack = false
        )

        val NaturalPortrait = ProcessingProfile(
            name = "NaturalPortrait",
            highlightRollOff = 0.65f,
            shadowLift = 0.28f,
            exposureBias = +0.2f, // Gentle face exposure lift
            skinSaturation = -0.05f, // Suppress unnatural orange cast
            skinSharpeningDampening = 0.75f, // Protect facial skin pores and prevent blemish exaggeration
            backgroundSharpening = 0.30f,
            backgroundNoiseReduction = 0.50f, // Clean background noise
            localContrast = 0.08f, // Soft gentle roll-off around face
            microContrastGain = 0.08f,
            enableHdrBurst = true,
            enableLowLightStack = false
        )

        val NaturalLandscape = ProcessingProfile(
            name = "NaturalLandscape",
            highlightRollOff = 0.80f, // Deep sky and cloud protection
            shadowLift = 0.18f, // Deep foliage shadows remain rich
            exposureBias = -0.3f, // Preserve highlight headroom
            skinSaturation = 0.0f,
            skinSharpeningDampening = 0.40f,
            backgroundSharpening = 0.50f, // Enhanced distant foliage detail
            backgroundNoiseReduction = 0.25f,
            localContrast = 0.18f, // Enhanced atmospheric depth
            microContrastGain = 0.18f,
            enableHdrBurst = true,
            enableLowLightStack = false
        )

        val NaturalLowLight = ProcessingProfile(
            name = "NaturalLowLight",
            highlightRollOff = 0.70f,
            shadowLift = 0.35f,
            exposureBias = 0.0f,
            skinSaturation = 0.0f,
            skinSharpeningDampening = 0.50f,
            backgroundSharpening = 0.20f, // Restrained sharpening to avoid noise grain amplification
            backgroundNoiseReduction = 0.75f, // Strong temporal and spatial chroma denoising
            localContrast = 0.10f,
            microContrastGain = 0.06f,
            enableHdrBurst = true,
            enableLowLightStack = true
        )

        val NaturalBacklit = ProcessingProfile(
            name = "NaturalBacklit",
            highlightRollOff = 0.85f, // Strong asymptotic shoulder for blown background sky/window
            shadowLift = 0.40f, // Substantial toe lift to illuminate silhouetted subject
            exposureBias = +0.3f,
            skinSaturation = -0.05f,
            skinSharpeningDampening = 0.65f,
            backgroundSharpening = 0.35f,
            backgroundNoiseReduction = 0.45f,
            localContrast = 0.15f,
            microContrastGain = 0.12f,
            enableHdrBurst = true,
            enableLowLightStack = false
        )

        val NaturalFood = ProcessingProfile(
            name = "NaturalFood",
            highlightRollOff = 0.60f,
            shadowLift = 0.24f,
            exposureBias = +0.1f,
            skinSaturation = 0.0f,
            skinSharpeningDampening = 0.30f,
            backgroundSharpening = 0.45f, // Crisp food texture
            backgroundNoiseReduction = 0.35f,
            localContrast = 0.16f, // Warm appetizing separation
            microContrastGain = 0.16f,
            enableHdrBurst = false,
            enableLowLightStack = false
        )

        val NaturalDocument = ProcessingProfile(
            name = "NaturalDocument",
            highlightRollOff = 0.40f,
            shadowLift = 0.15f,
            exposureBias = 0.0f,
            skinSaturation = 0.0f,
            skinSharpeningDampening = 0.0f,
            backgroundSharpening = 0.60f, // Crisp text edges
            backgroundNoiseReduction = 0.60f, // Suppress paper texture noise
            localContrast = 0.25f, // High text-to-background contrast
            microContrastGain = 0.22f,
            enableHdrBurst = false,
            enableLowLightStack = false
        )
    }
}

/**
 * User and developer configuration parameters governing CAMELEO's Scene Intelligence.
 */
data class SceneProcessingConfig(
    val isSceneDetectionEnabled: Boolean = true,
    val isFaceDetectionEnabled: Boolean = true,
    val portraitStrength: Float = 1.0f,
    val skinProtectionStrength: Float = 0.60f,
    val landscapeStrength: Float = 1.0f,
    val lowLightStrength: Float = 1.0f,
    val backlightStrength: Float = 1.0f,
    val sceneConfidenceThreshold: Float = 0.40f,
    val faceConfidenceThreshold: Float = 0.45f,
    val forceSceneOverride: SceneType? = null // Developer debug override
)
