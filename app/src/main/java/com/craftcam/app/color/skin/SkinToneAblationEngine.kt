package com.craftcam.app.color.skin

import com.craftcam.app.color.digicam.DigicamIlluminant
import com.craftcam.app.portrait.FaceRegion
import kotlin.math.roundToInt

/**
 * 7-Stage Photographic Ablation Engine for CAMELEO Skin Tone Intelligence.
 *
 * Enables rigorous visual and numerical comparison across each pipeline layer:
 * - Stage A: Source Original (unprocessed capture).
 * - Stage B: No Skin Processing (global camera grade only).
 * - Stage C: Color Probability Map (Layer 2 continuous OKLCh + YCbCr confidence).
 * - Stage D: Anatomical Spatial Prior Map (Layer 3 anatomical spatial prior).
 * - Stage E: Refined Skin Mask (Layer 3 fused & edge-feathered mask).
 * - Stage F: Protection Only (Layer 4 bounds active, zero warmth or tone shifts).
 * - Stage G: Complete Skin Intelligence (Full integrated pipeline).
 */
class SkinToneAblationEngine(
    private val engine: SkinToneIntelligenceEngine = SkinToneIntelligenceEngine()
) {

    enum class AblationStage(val stageCode: String, val description: String) {
        STAGE_A_SOURCE("Stage_A", "Source original unprocessed capture"),
        STAGE_B_NO_SKIN_PROCESSING("Stage_B", "Global camera grade without skin processing"),
        STAGE_C_COLOR_PROBABILITY("Stage_C", "Layer 2 continuous color probability confidence map"),
        STAGE_D_ANATOMICAL_PRIOR("Stage_D", "Layer 3 anatomical spatial prior map"),
        STAGE_E_REFINED_MASK("Stage_E", "Layer 3 fused & edge-refined skin confidence mask"),
        STAGE_F_PROTECTION_ONLY("Stage_F", "Layer 4 photographic protection only (zero warmth shift)"),
        STAGE_G_COMPLETE("Stage_G", "Complete integrated Skin Tone Intelligence pipeline")
    }

    data class AblationReport(
        val stage: AblationStage,
        val outputPixels: IntArray,
        val metrics: Map<String, Any>
    )

    companion object {
        fun maskToGreyscalePixels(mask: FloatArray): IntArray {
            val total = mask.size
            val pixels = IntArray(total)
            for (i in 0 until total) {
                val v = (mask[i].coerceIn(0.0f, 1.0f) * 255.0f).roundToInt()
                pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            return pixels
        }
    }

    /**
     * Renders a specific [stage] from [inputPixels].
     */
    fun renderStage(
        inputPixels: IntArray,
        width: Int,
        height: Int,
        faces: List<FaceRegion> = emptyList(),
        illuminant: DigicamIlluminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
        profile: SkinToneProfile = SkinToneProfileCatalog.CPM35,
        stage: AblationStage
    ): AblationReport {
        val total = width * height
        val workingCopy = inputPixels.copyOf()

        return when (stage) {
            AblationStage.STAGE_A_SOURCE -> {
                AblationReport(
                    stage = stage,
                    outputPixels = workingCopy,
                    metrics = mapOf("stage" to "A", "description" to "Source Original")
                )
            }

            AblationStage.STAGE_B_NO_SKIN_PROCESSING -> {
                // Return image without any skin intelligence adjustments
                AblationReport(
                    stage = stage,
                    outputPixels = workingCopy,
                    metrics = mapOf("stage" to "B", "skinProcessingApplied" to false)
                )
            }

            AblationStage.STAGE_C_COLOR_PROBABILITY -> {
                val colorProb = engine.colorEstimator.estimateColourProbability(
                    workingCopy, width, height, illuminant
                )
                val greyscale = maskToGreyscalePixels(colorProb)
                val nonZeroCount = colorProb.count { it > 0.05f }
                AblationReport(
                    stage = stage,
                    outputPixels = greyscale,
                    metrics = mapOf(
                        "stage" to "C",
                        "activeColorPixels" to nonZeroCount,
                        "activeColorFraction" to (nonZeroCount.toFloat() / total)
                    )
                )
            }

            AblationStage.STAGE_D_ANATOMICAL_PRIOR -> {
                val spatialPrior = engine.anatomyPrior.generateSpatialPrior(
                    width, height, faces, profile.config.backgroundSuppression
                )
                val greyscale = maskToGreyscalePixels(spatialPrior)
                AblationReport(
                    stage = stage,
                    outputPixels = greyscale,
                    metrics = mapOf(
                        "stage" to "D",
                        "faceCount" to faces.size
                    )
                )
            }

            AblationStage.STAGE_E_REFINED_MASK -> {
                val colorProb = engine.colorEstimator.estimateColourProbability(
                    workingCopy, width, height, illuminant
                )
                val spatialPrior = engine.anatomyPrior.generateSpatialPrior(
                    width, height, faces, profile.config.backgroundSuppression
                )
                val fused = engine.anatomyPrior.fuseConfidence(
                    colorProb, spatialPrior,
                    profile.config.spatialConfidenceWeight,
                    profile.config.colourConfidenceWeight
                )
                val refined = engine.maskRefiner.refineMask(
                    fused, width, height, profile.config.maskFeatherRadius
                )
                val greyscale = maskToGreyscalePixels(refined)
                val detectedCount = refined.count { it > 0.10f }
                AblationReport(
                    stage = stage,
                    outputPixels = greyscale,
                    metrics = mapOf(
                        "stage" to "E",
                        "detectedSkinPixels" to detectedCount,
                        "skinFraction" to (detectedCount.toFloat() / total)
                    )
                )
            }

            AblationStage.STAGE_F_PROTECTION_ONLY -> {
                val result = engine.process(
                    pixels = workingCopy,
                    width = width,
                    height = height,
                    faces = faces,
                    illuminant = illuminant,
                    profile = profile,
                    protectionOnly = true
                )
                AblationReport(
                    stage = stage,
                    outputPixels = workingCopy,
                    metrics = mapOf(
                        "stage" to "F",
                        "skinPixelsProtected" to result.skinPixelCount,
                        "averageConfidence" to result.averageSkinConfidence
                    )
                )
            }

            AblationStage.STAGE_G_COMPLETE -> {
                val result = engine.process(
                    pixels = workingCopy,
                    width = width,
                    height = height,
                    faces = faces,
                    illuminant = illuminant,
                    profile = profile,
                    protectionOnly = false
                )
                AblationReport(
                    stage = stage,
                    outputPixels = workingCopy,
                    metrics = mapOf(
                        "stage" to "G",
                        "skinPixelsTotal" to result.skinPixelCount,
                        "averageConfidence" to result.averageSkinConfidence,
                        "averageLuma" to result.averageSkinLuma
                    )
                )
            }
        }
    }

    /**
     * Executes all 7 stages in order.
     */
    fun runCompleteAblation(
        inputPixels: IntArray,
        width: Int,
        height: Int,
        faces: List<FaceRegion> = emptyList(),
        illuminant: DigicamIlluminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
        profile: SkinToneProfile = SkinToneProfileCatalog.CPM35
    ): Map<AblationStage, AblationReport> {
        val results = mutableMapOf<AblationStage, AblationReport>()
        for (stage in AblationStage.values()) {
            results[stage] = renderStage(inputPixels, width, height, faces, illuminant, profile, stage)
        }
        return results
    }
}
