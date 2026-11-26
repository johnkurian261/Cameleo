package com.craftcam.app.model

/**
 * Camera white balance execution policy defining how color temperature and sensor gains
 * are managed across physical hardware, Camera2 ISP, and photochemical film rendering.
 */
enum class WhiteBalancePolicy(val displayName: String, val description: String) {
    /**
     * Fixed calibrated daylight balance (~5500K).
     * Used by analog daylight-balanced film and disposable cameras (e.g. D FUNS, DQS, CPM35, D3D, Classic U)
     * where chemical film emulsion possesses a fixed spectral sensitivity.
     * Prevents phone OEM AWB from dynamically neutralizing authentic warm sunset or incandescent glows.
     */
    FIXED_DAYLIGHT(
        "Fixed Daylight (5500K)",
        "Locks camera sensor gains to calibrated 5500K daylight baseline to preserve atmospheric light."
    ),

    /**
     * Fixed calibrated tungsten balance (~3200K).
     * Used for tungsten-balanced film stocks (e.g. CineStill 800T, CT2F) designed for indoor incandescent lighting.
     */
    FIXED_TUNGSTEN(
        "Fixed Tungsten (3200K)",
        "Locks camera sensor gains to calibrated 3200K tungsten baseline."
    ),

    /**
     * Standard Camera2 Auto White Balance.
     * Used for modern neutral / smartphone profiles (e.g. Zero, Original) where continuous
     * real-world color fidelity and environmental neutral balance are expected.
     */
    AUTO(
        "Auto White Balance",
        "Continuous Camera2 ISP auto white balance adapting dynamically to ambient light."
    ),

    /**
     * AWB Lock on capture.
     * Locks white balance during video recording or after tap-to-meter to prevent distracting
     * mid-take color temperature drifts.
     */
    AWB_LOCK(
        "AWB Lock",
        "Locks prevailing auto white balance gains to prevent temperature shifts during capture."
    ),

    /**
     * Hybrid Multi-Cue Illuminant Adaptation.
     * Used for vintage 2000s digicams (e.g. CCD R, Sony Cyber-shot, Ricoh GR Digital) where
     * early digital ISPs executed partial white balance while preserving golden indoor warmth.
     */
    HYBRID_ILLUMINANT(
        "Digicam Hybrid Adaptation",
        "Physically calibrated partial white balance retaining characteristic vintage digicam warmth."
    ),

    /**
     * Dedicated manual Kelvin selection.
     * Allows continuous user adjustment across [2500K..9500K].
     */
    MANUAL_KELVIN(
        "Manual Kelvin",
        "User-directed color temperature adjustment from 2500K to 9500K."
    );

    val isFixed: Boolean get() = this == FIXED_DAYLIGHT || this == FIXED_TUNGSTEN
    val isAuto: Boolean get() = this == AUTO || this == HYBRID_ILLUMINANT
}

/**
 * Camera-specific White Balance Configuration.
 */
data class WhiteBalanceConfig(
    val policy: WhiteBalancePolicy = WhiteBalancePolicy.FIXED_DAYLIGHT,
    val defaultKelvin: Int = 5500,
    val minKelvin: Int = 2500,
    val maxKelvin: Int = 9500,
    val allowManualKelvin: Boolean = true,
    val autoRecommended: Boolean = (policy == WhiteBalancePolicy.AUTO || policy == WhiteBalancePolicy.HYBRID_ILLUMINANT),
    val tintOffset: Float = 0.0f
) {
    val isDaylightBaseline: Boolean get() = defaultKelvin == 5500
    val isTungstenBaseline: Boolean get() = defaultKelvin == 3200
}
