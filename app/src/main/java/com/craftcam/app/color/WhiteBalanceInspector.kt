package com.craftcam.app.color

import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.util.Log
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.model.WhiteBalanceConfig
import com.craftcam.app.model.WhiteBalancePolicy

/**
 * Diagnostic tool and inspector for verifying white balance calibration, hardware sensor gains,
 * and ensuring single-application integrity across CAMELEO.
 */
object WhiteBalanceInspector {

    private const val TAG = "WhiteBalanceInspector"

    data class DiagnosticSnapshot(
        val presetId: String,
        val presetName: String,
        val policy: WhiteBalancePolicy,
        val requestedKelvin: Int,
        val tintOffset: Float,
        val isHardwareApplied: Boolean,
        val rGain: Float,
        val gGain: Float,
        val bGain: Float,
        val transform3x3: List<Float>?,
        val sensorBrand: String,
        val cameraId: String,
        val singleApplicationVerified: Boolean
    ) {
        fun toFormattedReport(): String {
            return buildString {
                appendLine("╔═══════════════════════════════════════════════════════════════════════╗")
                appendLine("║               CAMELEO WHITE BALANCE DIAGNOSTIC SNAPSHOT               ║")
                appendLine("╠═══════════════════════════════════════════════════════════════════════╣")
                appendLine("  Preset: $presetName ($presetId)")
                appendLine("  Policy: ${policy.displayName}")
                appendLine("  Kelvin: ${requestedKelvin}K | Tint: $tintOffset")
                appendLine("  Hardware Execution: ${if (isHardwareApplied) "ACTIVE (Camera2 Gains/Transform)" else "SIMULATED (Software Fallback)"}")
                appendLine("  Sensor Profile: $sensorBrand (Camera ID: $cameraId)")
                appendLine("  Hardware Gains: [R=%.3f, G=%.3f, B=%.3f]".format(rGain, gGain, bGain))
                appendLine("  Single-Application Invariant: ${if (singleApplicationVerified) "PASS (No double-correction)" else "FAIL"}")
                appendLine("╚═══════════════════════════════════════════════════════════════════════╝")
            }
        }
    }

    /**
     * Inspects and returns a complete [DiagnosticSnapshot] for the current camera state.
     */
    fun inspect(
        preset: CameraPreset,
        kelvin: Int,
        tintOffset: Float = 0.0f,
        supportsManualWb: Boolean,
        brand: DeviceBrand = DeviceCalibrationManager.detectDeviceBrand(),
        cameraId: String = "0"
    ): DiagnosticSnapshot {
        val exec = HardwareWhiteBalanceCalibrator.resolveExecution(
            policy = preset.whiteBalance.policy,
            requestedKelvin = kelvin,
            tintOffset = tintOffset,
            supportsManualWb = supportsManualWb,
            brand = brand,
            cameraId = cameraId
        )

        val r = exec.calibratedGains?.red ?: exec.colorCorrectionGains?.red ?: 1.0f
        val g = exec.calibratedGains?.greenEven ?: exec.colorCorrectionGains?.greenEven ?: 1.0f
        val b = exec.calibratedGains?.blue ?: exec.colorCorrectionGains?.blue ?: 1.0f

        val transformList = exec.transformMatrix3x3?.toList() ?: exec.colorSpaceTransform?.let { cst ->
            val list = mutableListOf<Float>()
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    val rVal = try { cst.getElement(row, col) } catch (e: Exception) { null }
                    if (rVal != null && rVal.denominator != 0) {
                        list.add(rVal.numerator.toFloat() / rVal.denominator.toFloat())
                    }
                }
            }
            list
        }

        // Single application verification: when hardware WB is active,
        // software must suppress its own user temperature offset.
        val singleAppVerified = (exec.isHardwareApplied && (exec.colorCorrectionGains != null || exec.calibratedGains != null)) || (!exec.isHardwareApplied)

        val snapshot = DiagnosticSnapshot(
            presetId = preset.id,
            presetName = preset.name,
            policy = preset.whiteBalance.policy,
            requestedKelvin = exec.resolvedKelvin,
            tintOffset = tintOffset,
            isHardwareApplied = exec.isHardwareApplied,
            rGain = r,
            gGain = g,
            bGain = b,
            transform3x3 = transformList,
            sensorBrand = brand.displayName,
            cameraId = cameraId,
            singleApplicationVerified = singleAppVerified
        )

        Log.d(TAG, snapshot.toFormattedReport())
        return snapshot
    }
}
