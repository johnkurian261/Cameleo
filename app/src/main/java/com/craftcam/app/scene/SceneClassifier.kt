package com.craftcam.app.scene

import com.craftcam.app.config.SceneProcessingConfig
import com.craftcam.app.portrait.FaceRegion

/**
 * Multi-Signal Scene Classifier for CAMELEO.
 * Synthesizes hardware sensor metadata, lighting conditions, dynamic range distribution,
 * and subject composition into a quantified scene classification.
 */
object SceneClassifier {

    fun classify(
        lighting: LightingAnalysis,
        dynamicRange: DynamicRangeAnalysis,
        subject: SubjectAnalysis,
        faces: List<FaceRegion>,
        config: SceneProcessingConfig = SceneProcessingConfig()
    ): SceneClassification {
        // 1. Check Developer Debug Override
        config.forceSceneOverride?.let { override ->
            return SceneClassification(override, 1.0f, "Developer Forced Debug Override")
        }

        if (!config.isSceneDetectionEnabled) {
            return SceneClassification(SceneType.UNKNOWN, 0.0f, "Scene Detection Disabled")
        }

        val minThreshold = config.sceneConfidenceThreshold

        // 2. High-Priority Condition: Portrait (Human Subject Presence)
        if (config.isFaceDetectionEnabled && subject.isPortraitCandidate && subject.portraitConfidence >= minThreshold) {
            val isBacklitPortrait = lighting.isBacklit || dynamicRange.isBacklit
            return if (isBacklitPortrait) {
                SceneClassification(SceneType.BACKLIT, 0.90f, "Backlit Portrait Subject Detected")
            } else {
                SceneClassification(SceneType.PORTRAIT, subject.portraitConfidence, "Human Face Detected (${faces.size} faces)")
            }
        }

        // 3. High-Priority Condition: Night / Very Low Light
        if (lighting.condition == LightingCondition.VERY_LOW_LIGHT) {
            val conf = (lighting.shadowPercentage * 1.2f).coerceIn(0.65f, 0.95f)
            return SceneClassification(SceneType.NIGHT, conf, "Extreme Low Light / Night Ambient")
        }

        // 4. Backlit Scene (Without Faces)
        if (lighting.isBacklit || dynamicRange.isBacklit) {
            val conf = ((dynamicRange.highlightDistribution + dynamicRange.shadowDistribution) * 1.1f).coerceIn(0.55f, 0.92f)
            return SceneClassification(SceneType.BACKLIT, conf, "High Contrast Bimodal Backlighting")
        }

        // 5. Document Detection
        if (subject.isDocumentCandidate && subject.documentConfidence >= minThreshold) {
            return SceneClassification(SceneType.DOCUMENT, subject.documentConfidence, "Uniform Page with High-Contrast Text")
        }

        // 6. Food Detection
        if (subject.isFoodCandidate && subject.foodConfidence >= minThreshold) {
            return SceneClassification(SceneType.FOOD, subject.foodConfidence, "Warm High-Saturation Center Composition")
        }

        // 7. Landscape Detection
        if (subject.isLandscapeCandidate && subject.landscapeConfidence >= minThreshold) {
            return SceneClassification(SceneType.LANDSCAPE, subject.landscapeConfidence, "Sky and Foliage Horizon Composition")
        }

        // 8. Low-Light Scene
        if (lighting.isLowLight) {
            return SceneClassification(SceneType.NIGHT, 0.70f, "Low-Light Interior / Twilight")
        }

        // 9. Outdoor vs Indoor Heuristics
        if (lighting.condition == LightingCondition.BRIGHT || (lighting.meanLuminance > 0.52f && lighting.estimatedColorTempK >= 5200)) {
            return SceneClassification(SceneType.OUTDOOR, 0.65f, "High Ambient Daylight Luminance")
        }

        if (lighting.estimatedColorTempK <= 3800 || (lighting.meanLuminance in 0.25f..0.50f && lighting.shadowPercentage > 0.25f)) {
            return SceneClassification(SceneType.INDOOR, 0.60f, "Moderate Warm Ambient Interior")
        }

        // Default fallback if no category exceeds confidence threshold
        return SceneClassification(SceneType.UNKNOWN, 0.0f, "Ambiguous Scene Parameters")
    }
}
