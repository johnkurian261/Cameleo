package com.craftcam.app.processor.digicam

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Developer Diagnostics & Stage Exporter for CPM35 and CCD Calibration (Section 21).
 *
 * Exports intermediate stages:
 * - 01_original_capture.jpg
 * - 02_estimated_illuminant.txt
 * - 03_partial_white_balance.jpg
 * - 04_optical_scatter_glow.jpg
 * - 05_sensor_electronic_noise.jpg
 * - 06_final_rendered_digicam.jpg
 */
object DigicamDiagnostics {

    private const val TAG = "DigicamDiagnostics"

    fun exportDebugStages(
        context: Context?,
        original: Bitmap,
        illuminantName: String,
        paramsString: String,
        stagePwb: Bitmap?,
        stageOptical: Bitmap?,
        stageNoise: Bitmap?,
        finalBitmap: Bitmap
    ) {
        if (context == null) return
        try {
            val debugDir = File(context.getExternalFilesDir(null) ?: context.cacheDir, "digicam_debug")
            debugDir.mkdirs()

            // 1. Original
            File(debugDir, "01_original_capture.jpg").outputStream().use {
                original.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }

            // 2. Metadata & Illuminant diagnostics
            File(debugDir, "02_estimated_illuminant.txt").writeText(
                """
                === CAMELEO DIGICAM ILLUMINANT ESTIMATION ===
                Estimated Illuminant: $illuminantName
                Parameters:
                $paramsString
                """.trimIndent()
            )

            // 3. Partial White Balance stage
            stagePwb?.let { bmp ->
                File(debugDir, "03_partial_white_balance.jpg").outputStream().use {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
                }
            }

            // 4. Optical scatter / glow stage
            stageOptical?.let { bmp ->
                File(debugDir, "04_optical_scatter_glow.jpg").outputStream().use {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
                }
            }

            // 5. Electronic noise stage
            stageNoise?.let { bmp ->
                File(debugDir, "05_sensor_electronic_noise.jpg").outputStream().use {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
                }
            }

            // 6. Final rendered output
            File(debugDir, "06_final_rendered_digicam.jpg").outputStream().use {
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }

            Log.i(TAG, "Successfully exported digicam debug stages to: ${debugDir.absolutePath}")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed exporting digicam debug stages", t)
        }
    }
}
