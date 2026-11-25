package com.craftcam.app.color.digicam

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.craftcam.app.color.skin.SkinProcessingResult
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Developer Diagnostic Inspector for Digicam Rendering (Phase 2).
 *
 * Provides granular stage-by-stage visibility into:
 * 1. Capture metadata (ISO, exposure, flash, format).
 * 2. Estimated illuminant, confidence score, and continuous lighting regime weights.
 * 3. Partial white balance linear gains and chromatic adaptation.
 * 4. Skin tone intelligence detection and protection metrics.
 * 5. Red dominance protection metrics.
 * 6. Optical highlight scatter diffusion and veiling glare.
 * 7. Electronic CCD noise parameters.
 * 8. Intermediate stage image exports to internal storage for developer review.
 */
object DigicamInspector {

    private const val TAG = "DigicamInspector"

    data class InspectionReport(
        val timestamp: Long = System.currentTimeMillis(),
        val model: DigicamModel,
        val illuminantWeights: LightingRegimeWeights,
        val resolvedParams: IlluminantParameters,
        val isFlashActive: Boolean,
        val sensorIso: Int,
        val skinResult: SkinProcessingResult? = null,
        val stagesSaved: List<String> = emptyList()
    ) {
        fun toJson(): String {
            val json = JSONObject()
            json.put("timestamp", timestamp)
            json.put("model", model.name)
            json.put("primaryIlluminant", illuminantWeights.primaryIlluminant.name)
            json.put("confidence", illuminantWeights.confidence.toDouble())
            json.put("estimatedColorTempK", illuminantWeights.estimatedColorTempK)

            val weightsObj = JSONObject()
            weightsObj.put("daylight", illuminantWeights.daylightWeight.toDouble())
            weightsObj.put("tungsten", illuminantWeights.tungstenWeight.toDouble())
            weightsObj.put("flash", illuminantWeights.flashWeight.toDouble())
            weightsObj.put("artificial", illuminantWeights.artificialWeight.toDouble())
            weightsObj.put("neutralPixelFraction", illuminantWeights.neutralPixelFraction.toDouble())
            json.put("regimeWeights", weightsObj)

            val paramsObj = JSONObject()
            paramsObj.put("gainR", resolvedParams.gains[0].toDouble())
            paramsObj.put("gainG", resolvedParams.gains[1].toDouble())
            paramsObj.put("gainB", resolvedParams.gains[2].toDouble())
            paramsObj.put("retentionRatio", resolvedParams.retentionRatio.toDouble())
            paramsObj.put("redSaturationBoost", resolvedParams.redSaturationBoost.toDouble())
            paramsObj.put("contrast", resolvedParams.contrast.toDouble())
            paramsObj.put("saturation", resolvedParams.saturation.toDouble())
            paramsObj.put("opticalScatterIntensity", resolvedParams.opticalScatterIntensity.toDouble())
            paramsObj.put("noiseMultiplier", resolvedParams.noiseMultiplier.toDouble())
            json.put("resolvedParams", paramsObj)

            skinResult?.let {
                val skinObj = JSONObject()
                skinObj.put("faceCount", it.faceCount)
                skinObj.put("skinPixelCount", it.skinPixelCount)
                skinObj.put("averageConfidence", it.averageSkinConfidence.toDouble())
                skinObj.put("averageLuma", it.averageSkinLuma.toDouble())
                json.put("skinIntelligence", skinObj)
            }

            json.put("isFlashActive", isFlashActive)
            json.put("sensorIso", sensorIso)
            return json.toString(2)
        }
    }

    /**
     * Exports intermediate stage bitmaps and JSON diagnostic report to app cache.
     */
    fun exportDiagnostics(
        context: Context,
        model: DigicamModel,
        weights: LightingRegimeWeights,
        params: IlluminantParameters,
        isFlashActive: Boolean,
        sensorIso: Int,
        sourceBmp: Bitmap?,
        wbBmp: Bitmap?,
        opticalBmp: Bitmap?,
        noiseBmp: Bitmap?,
        finalBmp: Bitmap,
        skinResult: SkinProcessingResult? = null
    ): InspectionReport {
        val outDir = File(context.cacheDir, "digicam_inspector")
        if (!outDir.exists()) outDir.mkdirs()

        val savedStages = mutableListOf<String>()

        fun saveStage(bmp: Bitmap?, filename: String) {
            if (bmp == null) return
            try {
                val file = File(outDir, filename)
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                savedStages.add(filename)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save diagnostic stage $filename: ${e.message}")
            }
        }

        saveStage(sourceBmp, "01_source.jpg")
        saveStage(wbBmp, "02_partial_white_balance.jpg")
        saveStage(opticalBmp, "03_optical_diffusion.jpg")
        saveStage(noiseBmp, "04_electronic_noise.jpg")
        saveStage(finalBmp, "05_final_digicam.jpg")

        val report = InspectionReport(
            model = model,
            illuminantWeights = weights,
            resolvedParams = params,
            isFlashActive = isFlashActive,
            sensorIso = sensorIso,
            skinResult = skinResult,
            stagesSaved = savedStages
        )

        try {
            val reportFile = File(outDir, "digicam_inspection_report.json")
            reportFile.writeText(report.toJson())
            Log.d(TAG, "Digicam inspection report saved to ${reportFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write inspection report json: ${e.message}")
        }

        return report
    }
}
