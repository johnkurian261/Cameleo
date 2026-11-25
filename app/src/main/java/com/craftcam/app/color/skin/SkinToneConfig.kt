package com.craftcam.app.color.skin

import com.craftcam.app.color.digicam.DigicamIlluminant

/**
 * Modular, versioned configuration for the CAMELEO Skin Tone Intelligence Engine.
 *
 * Parameters control:
 * 1. Multi-space color probability estimation (Layer 2).
 * 2. Anatomical spatial confidence and fusion (Layer 3).
 * 3. Photographic protection against filter degradation (Layer 4).
 * 4. Illuminant-adaptive response (Daylight, Tungsten, Flash, Artificial).
 *
 * Strictly PROHIBITED: Beauty smoothing, skin whitening, plastic blur, and forced hue homogenization.
 */
data class SkinToneConfig(
    val version: Int = 1,
    val enabled: Boolean = true,
    val detectionEnabled: Boolean = true,
    val protectionEnabled: Boolean = true,

    /** Minimum confidence threshold for skin detection activation. */
    val detectionConfidence: Float = 0.35f,

    /** Weight of anatomical spatial prior when faces are detected (0.0 = color only, 1.0 = anatomy only). */
    val spatialConfidenceWeight: Float = 0.65f,

    /** Weight of raw color probability. */
    val colourConfidenceWeight: Float = 0.35f,

    /** Background false-positive suppression factor when humans are detected (0.0 to 1.0). */
    val backgroundSuppression: Float = 0.80f,

    /** Edge-aware feathering radius as a fraction of image dimension. */
    val maskFeatherRadius: Float = 0.025f,

    /** Strength of camera-specific skin tone warmth adjustment (0.0 = neutral, 1.0 = full profile warmth). */
    val warmthStrength: Float = 0.15f,

    /** Protection against excessive saturation in skin tones (prevents sunburned or neon appearance). */
    val saturationProtection: Float = 0.85f,

    /** Maximum allowable saturation in detected skin regions before compression kicks in. */
    val maxAllowedSkinSaturation: Float = 0.65f,

    /** Protection against highlight blowout and chromaticity skew on facial features. */
    val highlightProtection: Float = 0.88f,

    /** Protection against crushing deep skin tones into muddy, noisy shadows. */
    val shadowProtection: Float = 0.82f,

    /** Strict limit in degrees for any hue adjustment (prevents forcing diverse complexions to a single peach). */
    val maximumHueShiftDegrees: Float = 5.0f,

    /** Strict limit on chroma adjustments. */
    val maximumChromaAdjustment: Float = 0.18f,

    /** Tone curve lower-midtone protection factor. */
    val lowerMidtoneProtection: Float = 0.75f,

    /** Whether yellow-cast protection is active (guards against blue-channel starvation and mustard skin). */
    val yellowCastProtectionEnabled: Boolean = false,

    /** Strength of yellow-cast correction [0.0..1.0]. */
    val yellowCastProtectionStrength: Float = 0.85f,

    /** Conservative headless baseline spatial confidence when no faces are detected. */
    val headlessBaselineConfidence: Float = 0.28f,

    /** Whether protection uses source-referred comparison to distinguish genuine lighting from filter error. */
    val sourceRelativeProtectionEnabled: Boolean = true,

    /** Dimension threshold above which mask estimation runs at reduced resolution for performance. */
    val adaptiveResolutionThreshold: Int = 1600,

    /** Developer debug stage export flag. */
    val debugVisualizationsEnabled: Boolean = false
)
