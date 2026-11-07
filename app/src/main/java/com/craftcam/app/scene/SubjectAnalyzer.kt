package com.craftcam.app.scene

import com.craftcam.app.portrait.FaceRegion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Output of subject and compositional analysis across the photographic scene.
 */
data class SubjectAnalysis(
    val isPortraitCandidate: Boolean,
    val portraitConfidence: Float,
    val isLandscapeCandidate: Boolean,
    val landscapeConfidence: Float,
    val isFoodCandidate: Boolean,
    val foodConfidence: Float,
    val isDocumentCandidate: Boolean,
    val documentConfidence: Float,
    val primarySubjectAreaFraction: Float
)

object SubjectAnalyzer {

    fun analyzeSubject(
        pixels: IntArray,
        width: Int,
        height: Int,
        faces: List<FaceRegion>
    ): SubjectAnalysis {
        val total = width * height
        if (total == 0 || pixels.isEmpty()) {
            return SubjectAnalysis(false, 0f, false, 0f, false, 0f, false, 0f, 0f)
        }

        // 1. Portrait Evaluation
        var portraitConf = 0.0f
        var maxFaceArea = 0.0f

        if (faces.isNotEmpty()) {
            for (f in faces) {
                val area = f.areaFraction
                if (area > maxFaceArea) maxFaceArea = area
            }

            // A face occupying between 4% and 75% of the frame indicates a deliberate portrait composition
            portraitConf = when {
                maxFaceArea in 0.08f..0.65f -> 0.92f
                maxFaceArea in 0.04f..0.08f -> 0.75f
                maxFaceArea > 0.65f -> 0.85f // Close-up portrait
                else -> 0.50f // Distant person
            }
        }

        // 2. Spatial Composition: Upper half vs Lower half (Landscape detection)
        var skyBlueUpper = 0
        var earthGreenLower = 0
        var centerWarm = 0
        var highContrastEdges = 0
        var uniformWhitePixels = 0

        val halfH = height / 2
        val cx0 = width / 4
        val cx1 = 3 * width / 4
        val cy0 = height / 4
        val cy1 = 3 * height / 4

        val step = max(1, total / 2500)
        var evaluatedUpper = 0
        var evaluatedLower = 0
        var evaluatedCenter = 0

        for (y in 0 until height step step) {
            val row = y * width
            val isUpper = y < halfH

            for (x in 0 until width step step) {
                val p = pixels[row + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = (0.299f * r + 0.587f * g + 0.114f * b)

                if (isUpper) {
                    evaluatedUpper++
                    // Sky signature: blue channel dominance or bright white clouds
                    if ((b > r + 15 && b > g) || (lum > 220 && abs(r - b) < 15)) {
                        skyBlueUpper++
                    }
                } else {
                    evaluatedLower++
                    // Earth/foliage signature: green or warm brown dominance
                    if ((g > b + 10 && g >= r - 15) || (r > b + 20 && g > b + 10)) {
                        earthGreenLower++
                    }
                }

                // Center ROI for Food detection
                if (x in cx0..cx1 && y in cy0..cy1) {
                    evaluatedCenter++
                    // Warm appetizing tones: red/orange dominance with moderate saturation
                    if (r > b + 35 && g > b + 10 && lum in 60.0f..220.0f) {
                        centerWarm++
                    }
                }

                // Document signature: very bright background and high contrast text strokes
                if (lum > 215) uniformWhitePixels++
            }
        }

        // Landscape Heuristic: Horizon composition with zero large faces
        val skyRatio = if (evaluatedUpper > 0) skyBlueUpper.toFloat() / evaluatedUpper else 0f
        val earthRatio = if (evaluatedLower > 0) earthGreenLower.toFloat() / evaluatedLower else 0f
        val isLandscape = (faces.isEmpty() || maxFaceArea < 0.03f) && (skyRatio > 0.25f && earthRatio > 0.20f)
        val landscapeConf = if (isLandscape) ((skyRatio + earthRatio) * 0.8f).coerceIn(0.40f, 0.90f) else 0.0f

        // Food Heuristic: Warm center saturation with no faces
        val foodRatio = if (evaluatedCenter > 0) centerWarm.toFloat() / evaluatedCenter else 0f
        val isFood = faces.isEmpty() && foodRatio > 0.35f
        val foodConf = if (isFood) (foodRatio * 1.5f).coerceIn(0.40f, 0.85f) else 0.0f

        // Document Heuristic: >55% white page background with sharp text contrast
        val whiteRatio = uniformWhitePixels.toFloat() / max(1, evaluatedUpper + evaluatedLower)
        val isDocument = faces.isEmpty() && whiteRatio > 0.50f
        val documentConf = if (isDocument) (whiteRatio * 1.2f).coerceIn(0.40f, 0.88f) else 0.0f

        return SubjectAnalysis(
            isPortraitCandidate = portraitConf >= 0.45f,
            portraitConfidence = portraitConf,
            isLandscapeCandidate = isLandscape,
            landscapeConfidence = landscapeConf,
            isFoodCandidate = isFood,
            foodConfidence = foodConf,
            isDocumentCandidate = isDocument,
            documentConfidence = documentConf,
            primarySubjectAreaFraction = maxFaceArea
        )
    }
}
