package com.craftcam.app.scene

import com.craftcam.app.config.ProcessingProfile
import com.craftcam.app.config.SceneProcessingConfig
import com.craftcam.app.portrait.FaceRegion

/**
 * Intelligent Processing Decision Engine.
 * Translates scene classifications, lighting conditions, and facial analysis into an
 * optimal [ProcessingProfile] using continuous weighted blending to prevent abrupt mode transitions.
 */
object ProcessingDecisionEngine {

    fun decideProfile(
        classification: SceneClassification,
        lighting: LightingAnalysis,
        dynamicRange: DynamicRangeAnalysis,
        faces: List<FaceRegion>,
        config: SceneProcessingConfig = SceneProcessingConfig()
    ): ProcessingProfile {
        val baseDefault = ProcessingProfile.NaturalDefault
        val conf = classification.confidence

        // If confidence is below threshold or unknown, apply default profile
        if (conf < config.sceneConfidenceThreshold || classification.type == SceneType.UNKNOWN) {
            return baseDefault
        }

        // 1. Select Primary Target Profile
        val targetProfile = when (classification.type) {
            SceneType.PORTRAIT -> ProcessingProfile.NaturalPortrait
            SceneType.BACKLIT -> {
                if (faces.isNotEmpty()) {
                    // Backlit Portrait: Blend Backlit highlight protection with Portrait skin care
                    ProcessingProfile.NaturalBacklit.blendWith(ProcessingProfile.NaturalPortrait, 0.50f)
                } else {
                    ProcessingProfile.NaturalBacklit
                }
            }
            SceneType.NIGHT -> ProcessingProfile.NaturalLowLight
            SceneType.LANDSCAPE -> ProcessingProfile.NaturalLandscape
            SceneType.FOOD -> ProcessingProfile.NaturalFood
            SceneType.DOCUMENT -> ProcessingProfile.NaturalDocument
            SceneType.OUTDOOR -> {
                // Outdoor: subtle highlight roll-off boost
                baseDefault.copy(
                    highlightRollOff = 0.68f,
                    localContrast = 0.14f
                )
            }
            SceneType.INDOOR -> {
                // Indoor: subtle shadow noise reduction boost
                baseDefault.copy(
                    backgroundNoiseReduction = 0.50f,
                    shadowLift = 0.25f
                )
            }
            SceneType.UNKNOWN -> baseDefault
        }

        // 2. Modulate by User Strength Sliders from Config
        val strength = when (classification.type) {
            SceneType.PORTRAIT -> config.portraitStrength
            SceneType.BACKLIT -> config.backlightStrength
            SceneType.NIGHT -> config.lowLightStrength
            SceneType.LANDSCAPE -> config.landscapeStrength
            else -> 1.0f
        }

        // 3. Continuous Parameter Blending:
        // Effective weight = confidence * userStrength
        val effectiveWeight = (conf * strength).coerceIn(0.0f, 1.0f)

        return baseDefault.blendWith(targetProfile, effectiveWeight)
    }
}
