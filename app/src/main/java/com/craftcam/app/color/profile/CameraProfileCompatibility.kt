package com.craftcam.app.color.profile

import com.craftcam.app.config.FilmColorProfileAdjustment
import com.craftcam.app.gl.shaders.ShaderConstants

/**
 * Camera Body & Color Profile Compatibility Matrix.
 * 
 * Decouples physical camera hardware/body characteristics (optical distortion, lens softness,
 * vignetting, chromatic aberration, halation, grain, frame borders, shutter/flash behaviour)
 * from interchangeable creative color profiles (film emulsions & digital grades).
 * 
 * Preserves each camera's dedicated calibrated LUT on its default profile selection and blends
 * creative profiles when a user explicitly selects a non-default profile, ensuring consistent
 * color routing across live preview, still capture, gallery import, redevelopment, and video.
 */
object CameraProfileCompatibility {

    private val NEUTRAL_ADJUSTMENT = FilmColorProfileAdjustment(
        tempDelta = 0.0f,
        tintDelta = 0.0f,
        fadeDelta = 0.0f,
        contrastMult = 1.0f,
        satMult = 1.0f
    )

    private val FIXED_MONOCHROME_IDS = setOf(
        "analog_dblack", "dblack", "d black", "vsco_b1", "b1", "vsco_x1", "x1"
    )

    data class ResolvedLutPlan(
        val primaryLutAsset: String,
        val secondaryLutAsset: String?,
        val dualLutMix: Float,
        val isMonochrome: Boolean,
        val isCustomProfile: Boolean,
        val effectiveAdjustment: FilmColorProfileAdjustment,
        val activeProfile: DazzColorProfile
    )

    data class ProfilePolicy(
        val isSupported: Boolean,
        val defaultProfileId: String = "C400",
        val supportedProfileIds: List<String> = DazzColorProfileCatalog.PROFILES.map { it.id },
        val supportsVideo: Boolean = true,
        val supportsStill: Boolean = true
    )

    private val POLICIES = mapOf(
        // ── 35mm & Consumer Print Film Cameras ──
        "analog_35mm_classic" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "d_classic" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "analog_mju2" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "mju2" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "mju_ii" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "mju ii" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "olympus_mju2" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "stylus_epic" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "analog_135sr" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "135sr" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "analog_135ne" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "135ne" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "analog_dclassic" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "analog_s67" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "s67" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "analog_faded_film" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),

        // ── Disposable Cameras ──
        "ccd_flash" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"), // D Funs
        "dfuns" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "d_funs" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "ccd_retro_compact" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"), // DQS
        "dqs" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "disp_flash" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"), // CPM 35
        "cpm35" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "disp_cpm35" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "analog_cinema_film" to ProfilePolicy(isSupported = true, defaultProfileId = "19P"), // D3D
        "d3d" to ProfilePolicy(isSupported = true, defaultProfileId = "19P"),
        "disp_classic" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"), // Golf
        "golf" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "disp_warm" to ProfilePolicy(isSupported = true, defaultProfileId = "EG200"),
        "disp_night" to ProfilePolicy(isSupported = true, defaultProfileId = "19P"),

        // ── Cinema / Motion Picture Film Stocks ──
        "analog_soft_film" to ProfilePolicy(isSupported = true, defaultProfileId = "19P"), // CT2F
        "ct2f" to ProfilePolicy(isSupported = true, defaultProfileId = "19P"),
        "analog_warm_film" to ProfilePolicy(isSupported = true, defaultProfileId = "FX33"), // GR D legacy alias
        "analog_vintage_color" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"), // D EXP
        "dexp" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "analog_nt16" to ProfilePolicy(isSupported = true, defaultProfileId = "03G"),
        "nt16" to ProfilePolicy(isSupported = true, defaultProfileId = "03G"),

        // ── Vintage Negative & Classic Digicams ──
        "analog_fxn_r" to ProfilePolicy(isSupported = true, defaultProfileId = "FXN2"), // FXN-R
        "fxnr" to ProfilePolicy(isSupported = true, defaultProfileId = "FXN2"),
        "fxn_r" to ProfilePolicy(isSupported = true, defaultProfileId = "FXN2"),
        "ccd_r" to ProfilePolicy(isSupported = true, defaultProfileId = "FX33"), // CCD R
        "ccdr" to ProfilePolicy(isSupported = true, defaultProfileId = "FX33"),
        "ccd_grd" to ProfilePolicy(isSupported = true, defaultProfileId = "FX33"), // GR D
        "grd" to ProfilePolicy(isSupported = true, defaultProfileId = "FX33"),
        "ccd_y2k_digital" to ProfilePolicy(isSupported = true, defaultProfileId = "FX33"), // Classic U
        "classic_u" to ProfilePolicy(isSupported = true, defaultProfileId = "FX33"),
        "classicu" to ProfilePolicy(isSupported = true, defaultProfileId = "FX33"),
        "ccd_classic" to ProfilePolicy(isSupported = true, defaultProfileId = "FX33"),

        // ── Instant / Polaroid Cameras ──
        "inst_vintage" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "inst_classic" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "inst_soft_polaroid" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "inst_paf" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),

        // ── VSCO Film Stocks ──
        "vsco_a6" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "vsco_c1" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),
        "vsco_f2" to ProfilePolicy(isSupported = true, defaultProfileId = "03G"),
        "vsco_g3" to ProfilePolicy(isSupported = true, defaultProfileId = "EG200"),
        "vsco_m5" to ProfilePolicy(isSupported = true, defaultProfileId = "19M"),
        "vsco_hb1" to ProfilePolicy(isSupported = true, defaultProfileId = "C400"),

        // ── Fixed Dedicated Pipelines (Color Profiles not applicable / fixed look) ──
        "vsco_b1" to ProfilePolicy(isSupported = false, defaultProfileId = "22M"), // Dedicated B&W negative
        "vsco_x1" to ProfilePolicy(isSupported = false, defaultProfileId = "22M"), // Dedicated high-contrast B&W
        "ccd_cool_digital" to ProfilePolicy(isSupported = false, defaultProfileId = "C400"), // Aerochrome IR false-color
        "ir" to ProfilePolicy(isSupported = false, defaultProfileId = "C400"),
        "vsco_handycam" to ProfilePolicy(isSupported = false), // Fixed 8mm handycam video emulsion
        "zero" to ProfilePolicy(isSupported = false, defaultProfileId = "C400"), // Modern natural smartphone ISP
        "analog_dblack" to ProfilePolicy(isSupported = false, defaultProfileId = "22M"), // Dedicated monochrome
        "dblack" to ProfilePolicy(isSupported = false, defaultProfileId = "22M"),
        "cam_retro_vhs" to ProfilePolicy(isSupported = false), // Fixed analog VHS tape
        "cam_analog_video" to ProfilePolicy(isSupported = false), // Fixed CRT video
        "cam_90s_camcorder" to ProfilePolicy(isSupported = false), // Fixed Hi8 camcorder
        "thermal_vision" to ProfilePolicy(isSupported = false, supportsVideo = true, supportsStill = true),
        "thermal" to ProfilePolicy(isSupported = false, supportsVideo = true, supportsStill = true),
        "thermal_ir" to ProfilePolicy(isSupported = false, supportsVideo = true, supportsStill = true)
    )

    fun getPolicy(presetId: String): ProfilePolicy =
        POLICIES[presetId.lowercase()] ?: ProfilePolicy(isSupported = true, defaultProfileId = "C400")

    fun isProfileSupported(presetId: String): Boolean =
        getPolicy(presetId).isSupported

    fun getDefaultProfile(presetId: String): DazzColorProfile {
        val policy = getPolicy(presetId)
        return DazzColorProfileCatalog.getById(policy.defaultProfileId)
    }

    fun getSupportedProfiles(presetId: String): List<DazzColorProfile> {
        val policy = getPolicy(presetId)
        if (!policy.isSupported) return emptyList()
        return policy.supportedProfileIds.map { DazzColorProfileCatalog.getById(it) }
    }

    /**
     * Resolves the authoritative 3D LUT + adjustment plan for any preset/profile combination.
     *
     * Key invariant:
     * - Fixed-pipeline cameras (`zero`, camcorders, B&W, IR) and default-profile selections preserve
     *   100% of the camera's own calibrated LUT with zero extra profile adjustment stacking.
     * - Custom chromatic profile selections blend the camera's native LUT (35%) with the selected
     *   color profile LUT (65%) so the camera keeps its tonal DNA while reflecting the chosen profile.
     * - Monochrome 22M switches cleanly to `lut_profile_22m.png` with chromatic suppression.
     */
    fun resolveLutPlan(
        presetId: String,
        colorProfileIndex: Int,
        cameraLutAsset: String? = null,
        cameraSecondaryLutAsset: String? = null,
        cameraDualLutMix: Float = 0.0f
    ): ResolvedLutPlan {
        val policy = getPolicy(presetId)
        val normalizedId = presetId.lowercase()
        val resolvedCameraLut = cameraLutAsset?.ifEmpty { null }
            ?: ShaderConstants.getLutAssetForPreset(presetId)
        val defaultProfile = DazzColorProfileCatalog.getById(policy.defaultProfileId)

        if (!policy.isSupported) {
            return ResolvedLutPlan(
                primaryLutAsset = resolvedCameraLut,
                secondaryLutAsset = cameraSecondaryLutAsset?.ifEmpty { null },
                dualLutMix = cameraDualLutMix.coerceIn(0f, 1f),
                isMonochrome = normalizedId in FIXED_MONOCHROME_IDS,
                isCustomProfile = false,
                effectiveAdjustment = NEUTRAL_ADJUSTMENT,
                activeProfile = defaultProfile
            )
        }

        val selectedProfile = DazzColorProfileCatalog.getByIndex(colorProfileIndex)
        val isCustom = !selectedProfile.id.equals(defaultProfile.id, ignoreCase = true)

        if (!isCustom) {
            return ResolvedLutPlan(
                primaryLutAsset = resolvedCameraLut,
                secondaryLutAsset = cameraSecondaryLutAsset?.ifEmpty { null },
                dualLutMix = cameraDualLutMix.coerceIn(0f, 1f),
                isMonochrome = selectedProfile.isMonochrome || normalizedId in FIXED_MONOCHROME_IDS,
                isCustomProfile = false,
                effectiveAdjustment = NEUTRAL_ADJUSTMENT,
                activeProfile = defaultProfile
            )
        }

        if (selectedProfile.isMonochrome) {
            return ResolvedLutPlan(
                primaryLutAsset = selectedProfile.lutAssetPath,
                secondaryLutAsset = null,
                dualLutMix = 0.0f,
                isMonochrome = true,
                isCustomProfile = true,
                effectiveAdjustment = selectedProfile.adjustment,
                activeProfile = selectedProfile
            )
        }

        return ResolvedLutPlan(
            primaryLutAsset = resolvedCameraLut,
            secondaryLutAsset = selectedProfile.lutAssetPath,
            dualLutMix = 0.65f,
            isMonochrome = false,
            isCustomProfile = true,
            effectiveAdjustment = selectedProfile.adjustment,
            activeProfile = selectedProfile
        )
    }
}
