package com.craftcam.app.gl

import android.opengl.GLES30
import android.util.Log
import android.util.Rational
import com.craftcam.app.camera.profile.ColorFlashTint
import com.craftcam.app.camera.profile.ExposureProfile
import com.craftcam.app.camera.profile.LensProfile
import com.craftcam.app.camera.profile.LensType
import com.craftcam.app.camera.profile.OpticalProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Optical Effect Engine.
 *
 * Encapsulates all optical lens transformations and light interactions:
 * 1. Fisheye Curvilinear Distortions: Hemispherical Circular Hood (FISHEYE_F) and Full-Frame (FISHEYE_W).
 * 2. Transverse Chromatic Aberration: Scaled by viewport width (Width / 720.0) and radial distance squared (r^2).
 * 3. Multi-Facet Prism Optical Refraction: Facet splitting with chromatic dispersion.
 * 4. Specular Highlight Starburst Rays: Multi-ray diffraction spikes above luminance threshold.
 * 5. Neutral Density (ND) Interaction: Maps physical EV reduction to Camera2 AE steps and shader transmission.
 * 6. Color Gel Flash Tinting: Authentic analog colored flash gel filter simulation.
 */
object OpticalEffectEngine {

    private const val TAG = "OpticalEffectEngine"

    data class FisheyeParams(
        val k1: Float,
        val k2: Float,
        val isCircular: Boolean,
        val outerRadius: Float = 0.70f,
        val innerFeatherRadius: Float = 0.54f
    )

    data class PrismParams(
        val dispersion: Float,
        val facetWeightMax: Float = 0.45f,
        val angleRadians: Float = 0.5235988f // 30 degrees (pi/6)
    )

    data class StarburstParams(
        val strength: Float,
        val threshold: Float,
        val iterations: Int = 3
    )

    data class NdExposureParams(
        val evStopsReduction: Float,
        val transmissionFactor: Float
    )

    /**
     * Resolves fisheye curvilinear parameters for the given lens.
     */
    fun resolveFisheye(lensProfile: LensProfile): FisheyeParams = when (lensProfile.type) {
        LensType.FISHEYE_F -> FisheyeParams(
            k1 = lensProfile.distortionStrength,
            k2 = lensProfile.edgeCurvature,
            isCircular = true,
            outerRadius = 0.72f,
            innerFeatherRadius = 0.56f
        )
        LensType.FISHEYE_W -> FisheyeParams(
            k1 = lensProfile.distortionStrength,
            k2 = lensProfile.edgeCurvature,
            isCircular = false,
            outerRadius = 0.85f,
            innerFeatherRadius = 0.65f
        )
        else -> FisheyeParams(k1 = 0f, k2 = 0f, isCircular = false)
    }

    /**
     * Computes transverse chromatic aberration scaled by viewport resolution.
     * Prevents excessive pixel divergence on high-DPI displays while ensuring visible vintage dispersion.
     */
    fun computeTransverseCA(
        opticalProfile: OpticalProfile,
        lensProfile: LensProfile,
        viewportWidth: Float
    ): Float {
        val baseCA = max(opticalProfile.chromaticAberration, lensProfile.chromaticAberration)
        val lensFloor = when (lensProfile.type) {
            LensType.FISHEYE_F -> 0.016f
            LensType.FISHEYE_W -> 0.008f
            LensType.PRISM -> 0.014f
            else -> 0.0f
        }
        val effCA = max(baseCA, lensFloor)
        if (effCA <= 0.0001f) return 0f

        // Resolution scaling: 720p is normalized reference
        val resScale = if (viewportWidth > 0f) (viewportWidth / 720.0f).coerceIn(0.75f, 2.5f) else 1.0f
        return effCA * resScale
    }

    /**
     * Resolves multi-facet prism optical dispersion.
     */
    fun resolvePrism(lensProfile: LensProfile): PrismParams {
        val disp = lensProfile.prismDispersion.coerceIn(0.0f, 1.0f)
        return PrismParams(dispersion = disp)
    }

    /**
     * Resolves starburst diffraction parameters.
     */
    fun resolveStarburst(lensProfile: LensProfile): StarburstParams {
        return StarburstParams(
            strength = lensProfile.starburstStrength.coerceIn(0.0f, 1.0f),
            threshold = lensProfile.starburstThreshold.coerceIn(0.5f, 0.98f)
        )
    }

    /**
     * Resolves Neutral Density (ND) exposure parameters.
     * Combines physical camera exposure compensation with optical transmission factor.
     */
    fun resolveNd(exposureProfile: ExposureProfile, lensProfile: LensProfile): NdExposureParams {
        val stops = if (lensProfile.type == LensType.ND) {
            max(lensProfile.ndExposureReduction, exposureProfile.ndExposureReductionStops)
        } else {
            exposureProfile.ndExposureReductionStops
        }

        val factor = if (lensProfile.type == LensType.ND) {
            min(lensProfile.ndTransmissionFactor, exposureProfile.ndTransmissionFactor)
        } else {
            exposureProfile.ndTransmissionFactor
        }

        return NdExposureParams(
            evStopsReduction = stops,
            transmissionFactor = factor
        )
    }

    /**
     * Calculates the target Camera2 AE compensation index for the hardware sensor.
     * Returns an integer step index clamped to the device's supported range.
     *
     * @param evStopsReduction Positive number of EV stops to darken (e.g. 2.0f stops).
     * @param stepRational Step size from CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP (e.g. 1/3 or 1/2).
     * @param minComp Lower bound from CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE.
     * @param maxComp Upper bound from CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE.
     * @return Hardware EV compensation integer index (negative for darkening).
     */
    fun calculateCamera2EvStepIndex(
        evStopsReduction: Float,
        stepRational: Rational?,
        minComp: Int,
        maxComp: Int
    ): Int {
        if (evStopsReduction <= 0.01f || stepRational == null || stepRational.numerator == 0) {
            return 0
        }
        val stepFloat = stepRational.toFloat()
        if (stepFloat <= 0.0001f) return 0

        // Darken: negative EV compensation
        val targetIndex = (-evStopsReduction / stepFloat).toInt()
        return targetIndex.coerceIn(minComp, maxComp)
    }

    /**
     * Applies curvilinear fisheye transformation to a still image pixel array.
     */
    suspend fun applyFisheyeToPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        params: FisheyeParams
    ) = coroutineScope {
        val totalPixels = width * height
        val srcCopy = pixels.clone()
        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rowsPerCore = (height + numCores - 1) / numCores

        (0 until numCores).map { coreIdx ->
            async(Dispatchers.Default) {
                val startY = coreIdx * rowsPerCore
                val endY = min(height, startY + rowsPerCore)

                for (y in startY until endY) {
                    val py = (y.toFloat() / height) - 0.5f
                    val rowOffset = y * width
                    for (x in 0 until width) {
                        val px = (x.toFloat() / width) - 0.5f
                        val r2 = px * px + py * py
                        val r = sqrt(r2)

                        if (params.isCircular && r > params.outerRadius) {
                            pixels[rowOffset + x] = 0xFF000000.toInt()
                            continue
                        }

                        val rDist = r * (1.0f + params.k1 * r2 + params.k2 * r2 * r2)
                        val invR = if (r > 0.0001f) 1.0f / r else 0.0f
                        val u = 0.5f + px * invR * rDist
                        val v = 0.5f + py * invR * rDist

                        if (u in 0f..1f && v in 0f..1f) {
                            val sx = (u * (width - 1)).toInt().coerceIn(0, width - 1)
                            val sy = (v * (height - 1)).toInt().coerceIn(0, height - 1)
                            var color = srcCopy[sy * width + sx]

                            if (params.isCircular && r > params.innerFeatherRadius) {
                                val t = ((params.outerRadius - r) / (params.outerRadius - params.innerFeatherRadius)).coerceIn(0f, 1f)
                                val a = (color ushr 24) and 0xFF
                                val cr = (((color ushr 16) and 0xFF) * t).toInt()
                                val cg = (((color ushr 8) and 0xFF) * t).toInt()
                                val cb = ((color and 0xFF) * t).toInt()
                                color = (a shl 24) or (cr shl 16) or (cg shl 8) or cb
                            }
                            pixels[rowOffset + x] = color
                        } else {
                            pixels[rowOffset + x] = 0xFF000000.toInt()
                        }
                    }
                }
            }
        }.forEach { it.await() }
    }
}

