package com.craftcam.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import android.util.SizeF
import androidx.camera.core.CameraSelector
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

object CameraDiscovery {

    private const val TAG = "CameraDiscovery"
    private const val FULL_FRAME_DIAG_MM = 43.2667f // sqrt(36^2 + 24^2)

    data class DiscoveredLenses(
        val rearLenses: List<CameraLens>,
        val frontLenses: List<CameraLens>
    )

    fun discover(context: Context): DiscoveredLenses {
        val cameraService = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return fallbackLenses()

        return try {
            val cameraIds = cameraService.cameraIdList
            val rearRawList = mutableListOf<CameraLens>()
            val frontRawList = mutableListOf<CameraLens>()

            for (camId in cameraIds) {
                try {
                    val chars = cameraService.getCameraCharacteristics(camId)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue

                    // Check if logical multi-camera with physical sub-cameras
                    val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                    val isLogicalMulti = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)

                    var physicalIds: Set<String>? = null
                    if (isLogicalMulti && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        physicalIds = chars.physicalCameraIds
                    }

                    if (physicalIds != null && physicalIds.isNotEmpty()) {
                        // Query physical sub-cameras
                        for (physId in physicalIds) {
                            try {
                                val physChars = cameraService.getCameraCharacteristics(physId)
                                val lens = createLensFromCharacteristics(
                                    logicalId = camId,
                                    physicalId = physId,
                                    facing = facing,
                                    chars = physChars
                                )
                                if (lens != null) {
                                    if (facing == CameraSelector.LENS_FACING_BACK) {
                                        rearRawList.add(lens)
                                    } else {
                                        frontRawList.add(lens)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to read physical camera $physId: ${e.message}")
                            }
                        }
                    } else {
                        // Standard camera or logical camera without exposed physical IDs
                        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: SizeF(5.6f, 4.2f)

                        if (focalLengths != null && focalLengths.isNotEmpty()) {
                            for (fl in focalLengths) {
                                val eq35 = calculate35mmEquivalent(fl, sensorSize)
                                val lens = CameraLens(
                                    id = "${camId}_${fl.roundToInt()}",
                                    cameraId = camId,
                                    physicalCameraId = null,
                                    facing = facing,
                                    focalLengthMm = fl,
                                    equivalentFocalLength35mm = eq35,
                                    equivalentZoom = 1.0f, // will calibrate relative to reference
                                    displayName = "1×",
                                    lensDescription = if (facing == CameraSelector.LENS_FACING_BACK) "Main Camera" else "Front Camera",
                                    isPhysicalLens = true
                                )
                                if (facing == CameraSelector.LENS_FACING_BACK) {
                                    rearRawList.add(lens)
                                } else {
                                    frontRawList.add(lens)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error inspecting camera $camId: ${e.message}")
                }
            }

            // Calibrate equivalent zoom ratios relative to the primary rear lens (~24-28mm eq)
            val calibratedRear = calibrateRearLenses(rearRawList, cameraService)
            val calibratedFront = calibrateFrontLenses(frontRawList)

            Log.d(TAG, "Discovered ${calibratedRear.size} rear lenses and ${calibratedFront.size} front lenses")
            DiscoveredLenses(
                rearLenses = if (calibratedRear.isNotEmpty()) calibratedRear else defaultRearLenses(),
                frontLenses = if (calibratedFront.isNotEmpty()) calibratedFront else defaultFrontLenses()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed discovering camera lenses, using fallbacks", e)
            fallbackLenses()
        }
    }

    private fun createLensFromCharacteristics(
        logicalId: String,
        physicalId: String,
        facing: Int,
        chars: CameraCharacteristics
    ): CameraLens? {
        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: return null
        if (focalLengths.isEmpty()) return null
        val focalLength = focalLengths[0]
        val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: SizeF(5.6f, 4.2f)
        val eq35 = calculate35mmEquivalent(focalLength, sensorSize)

        return CameraLens(
            id = "${logicalId}_$physicalId",
            cameraId = logicalId,
            physicalCameraId = physicalId,
            facing = facing,
            focalLengthMm = focalLength,
            equivalentFocalLength35mm = eq35,
            equivalentZoom = 1.0f, // calibrated in next step
            displayName = "1×",
            lensDescription = "Camera Lens",
            isPhysicalLens = true
        )
    }

    private fun calculate35mmEquivalent(focalLength: Float, sensorSize: SizeF): Float {
        val diag = sqrt(sensorSize.width * sensorSize.width + sensorSize.height * sensorSize.height)
        val cropFactor = if (diag > 0.5f) FULL_FRAME_DIAG_MM / diag else 7.0f
        return focalLength * cropFactor
    }

    private fun calibrateRearLenses(
        rawList: List<CameraLens>,
        cameraService: CameraManager
    ): List<CameraLens> {
        if (rawList.isEmpty()) return emptyList()

        // Deduplicate lenses with nearly identical focal length
        val deduped = mutableListOf<CameraLens>()
        for (lens in rawList.sortedBy { it.equivalentFocalLength35mm }) {
            val exists = deduped.any { abs(it.equivalentFocalLength35mm - lens.equivalentFocalLength35mm) < 2.0f }
            if (!exists) {
                deduped.add(lens)
            }
        }

        // Find reference main lens (closest to 26mm, standard 1.0x wide)
        val refLens = deduped.minByOrNull { abs(it.equivalentFocalLength35mm - 26f) } ?: deduped[0]
        val refFocal = refLens.equivalentFocalLength35mm.coerceAtLeast(10f)

        // Query logical camera zoom ratio range if supported (e.g. S21 exposes [0.5, 30.0])
        var minHardwareZoom = 1.0f
        var maxHardwareZoom = 10.0f
        try {
            val mainChars = cameraService.getCameraCharacteristics(refLens.cameraId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val zoomRange = mainChars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                if (zoomRange != null) {
                    minHardwareZoom = zoomRange.lower
                    maxHardwareZoom = zoomRange.upper
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        val result = mutableListOf<CameraLens>()
        for (lens in deduped) {
            val rawZoom = lens.equivalentFocalLength35mm / refFocal
            val clampedZoom = rawZoom.coerceIn(minHardwareZoom, maxHardwareZoom)
            val displayName = formatZoom(clampedZoom)
            val desc = getLensDescription(clampedZoom)

            result.add(
                lens.copy(
                    equivalentZoom = clampedZoom,
                    displayName = displayName,
                    lensDescription = desc
                )
            )
        }

        // If device has a logical multi-camera with wide zoom range (e.g. 0.6x to 30x)
        // but only exposed a single focal length in Camera2, expand standard flagship lenses
        if (result.size == 1) {
            val single = result[0]
            val expanded = mutableListOf<CameraLens>()
            if (minHardwareZoom <= 0.65f) {
                expanded.add(
                    single.copy(
                        id = "${single.cameraId}_ultrawide",
                        equivalentZoom = 0.6f,
                        displayName = "0.6×",
                        lensDescription = "Ultra Wide, 0.6x",
                        isPhysicalLens = true
                    )
                )
            }
            expanded.add(
                single.copy(
                    id = "${single.cameraId}_wide",
                    equivalentZoom = 1.0f,
                    displayName = "1×",
                    lensDescription = "Main Wide, 1x",
                    isPhysicalLens = true
                )
            )
            if (maxHardwareZoom >= 2.8f) {
                expanded.add(
                    single.copy(
                        id = "${single.cameraId}_telephoto",
                        equivalentZoom = 3.0f,
                        displayName = "3×",
                        lensDescription = "Telephoto, 3x",
                        isPhysicalLens = true
                    )
                )
            } else if (maxHardwareZoom >= 1.9f) {
                expanded.add(
                    single.copy(
                        id = "${single.cameraId}_tele_2x",
                        equivalentZoom = 2.0f,
                        displayName = "2×",
                        lensDescription = "Telephoto 2x, 2x",
                        isPhysicalLens = false
                    )
                )
            }
            return expanded
        }

        return result.sortedBy { it.equivalentZoom }
    }

    private fun calibrateFrontLenses(rawList: List<CameraLens>): List<CameraLens> {
        val result = mutableListOf<CameraLens>()
        if (rawList.isEmpty()) {
            return defaultFrontLenses()
        }
        val first = rawList[0]
        result.add(
            first.copy(
                equivalentZoom = 1.0f,
                displayName = "1×",
                lensDescription = "Front Camera, 1x"
            )
        )
        return result
    }

    fun formatZoom(zoom: Float): String {
        return when {
            abs(zoom - 1.0f) < 0.05f -> "1×"
            abs(zoom - 2.0f) < 0.08f -> "2×"
            abs(zoom - 3.0f) < 0.08f -> "3×"
            abs(zoom - 5.0f) < 0.10f -> "5×"
            abs(zoom - 10.0f) < 0.15f -> "10×"
            zoom < 1.0f -> {
                val rounded = (zoom * 10f).roundToInt() / 10f
                if (rounded >= 1.0f) "1×" else "${String.format(Locale.US, "%.1f", rounded)}×"
            }
            else -> {
                val rounded = (zoom * 10f).roundToInt() / 10f
                if (abs(rounded - rounded.toInt()) < 0.05f) {
                    "${rounded.toInt()}×"
                } else {
                    "${String.format(Locale.US, "%.1f", rounded)}×"
                }
            }
        }
    }

    private fun getLensDescription(zoom: Float): String {
        return when {
            zoom < 0.8f -> "Ultra Wide, ${formatZoom(zoom)}"
            zoom in 0.8f..1.3f -> "Main Camera, ${formatZoom(zoom)}"
            zoom in 1.4f..2.5f -> "Telephoto 2x, ${formatZoom(zoom)}"
            else -> "Telephoto, ${formatZoom(zoom)}"
        }
    }

    private fun defaultRearLenses(): List<CameraLens> = listOf(
        CameraLens(
            id = "rear_06x",
            cameraId = "0",
            physicalCameraId = null,
            facing = CameraSelector.LENS_FACING_BACK,
            focalLengthMm = 2.2f,
            equivalentFocalLength35mm = 13.0f,
            equivalentZoom = 0.6f,
            displayName = "0.6×",
            lensDescription = "Ultra Wide, 0.6x",
            isPhysicalLens = true
        ),
        CameraLens(
            id = "rear_1x",
            cameraId = "0",
            physicalCameraId = null,
            facing = CameraSelector.LENS_FACING_BACK,
            focalLengthMm = 5.4f,
            equivalentFocalLength35mm = 26.0f,
            equivalentZoom = 1.0f,
            displayName = "1×",
            lensDescription = "Main Camera, 1x",
            isPhysicalLens = true
        ),
        CameraLens(
            id = "rear_3x",
            cameraId = "0",
            physicalCameraId = null,
            facing = CameraSelector.LENS_FACING_BACK,
            focalLengthMm = 8.9f,
            equivalentFocalLength35mm = 78.0f,
            equivalentZoom = 3.0f,
            displayName = "3×",
            lensDescription = "Telephoto, 3x",
            isPhysicalLens = true
        )
    )

    private fun defaultFrontLenses(): List<CameraLens> = listOf(
        CameraLens(
            id = "front_1x",
            cameraId = "1",
            physicalCameraId = null,
            facing = CameraSelector.LENS_FACING_FRONT,
            focalLengthMm = 3.8f,
            equivalentFocalLength35mm = 26.0f,
            equivalentZoom = 1.0f,
            displayName = "1×",
            lensDescription = "Front Camera, 1x",
            isPhysicalLens = true
        )
    )

    private fun fallbackLenses(): DiscoveredLenses = DiscoveredLenses(
        rearLenses = defaultRearLenses(),
        frontLenses = defaultFrontLenses()
    )
}

