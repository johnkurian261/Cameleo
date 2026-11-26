package com.craftcam.app.color.profile

/**
 * Camera Body & Color Profile Compatibility Matrix.
 * 
 * Decouples physical camera hardware/body characteristics (optical distortion, lens softness,
 * vignetting, chromatic aberration, halation, grain, frame borders, shutter/flash behaviour)
 * from interchangeable creative color profiles (film emulsions & digital grades).
 * 
 * Enforces camera-specific compatibility, default profile assignments, and reset behaviour.
 */
object CameraProfileCompatibility {

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
        "analog_warm_film" to ProfilePolicy(isSupported = true, defaultProfileId = "03G"),
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
        "vsco_b1" to ProfilePolicy(isSupported = true, defaultProfileId = "22M"),
        "vsco_x1" to ProfilePolicy(isSupported = true, defaultProfileId = "22M"),

        // ── Fixed Dedicated Pipelines (Color Profiles not applicable / fixed look) ──
        "zero" to ProfilePolicy(isSupported = false, defaultProfileId = "C400"), // Modern natural smartphone ISP
        "analog_dblack" to ProfilePolicy(isSupported = false, defaultProfileId = "22M"), // Dedicated monochrome
        "dblack" to ProfilePolicy(isSupported = false, defaultProfileId = "22M"),
        "cam_retro_vhs" to ProfilePolicy(isSupported = false), // Fixed analog VHS tape
        "cam_analog_video" to ProfilePolicy(isSupported = false), // Fixed CRT video
        "cam_90s_camcorder" to ProfilePolicy(isSupported = false) // Fixed Hi8 camcorder
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
}
