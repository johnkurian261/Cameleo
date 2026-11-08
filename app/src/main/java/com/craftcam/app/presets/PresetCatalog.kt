package com.craftcam.app.presets

import com.craftcam.app.model.*

object PresetCatalog {

    val allPresets: List<CameraPreset> = listOf(
        // =====================================================================
        // A. FLAGSHIP NATURAL SENSOR
        // =====================================================================
        CameraPreset(
            id = "zero",
            iconResName = "cam_zero",
            name = "Zero",
            subtitle = "Natural Photographic Sensor",
            category = CameraCategory.CCD_DIGITAL,
            year = "2024",
            description = "Flagship natural camera pipeline. Pristine color calibration, asymptotic highlight shoulder, adaptive shadow recovery, edge-aware noise reduction, restrained sharpening, and soft multi-complexion skin protection without film grain or vintage artifacts.",
            colorGrading = ColorGradingConfig(
                contrast = 1.02f,
                saturation = 1.00f,
                temperature = 0.0f,
                tint = 0.0f,
                gamma = 1.0f,
                fade = 0.0f,
                highlightRollOff = 0.92f,
                shadowTintR = 0.0f,
                shadowTintG = 0.0f,
                shadowTintB = 0.0f
            ),
            grain = GrainConfig(
                enabled = false,
                intensity = 0.0f,
                scale = 1.0f,
                assetPath = null,
                blendMode = GrainBlendMode.OVERLAY,
                shadowStrength = 0.0f,
                highlightStrength = 0.0f
            ),
            lens = LensConfig(
                vignetteIntensity = 0.0f,
                vignetteRadius = 1.0f,
                chromaticAberration = 0.0f,
                softness = 0.0f
            ),
            halation = HalationConfig(enabled = false, threshold = 1.0f, intensity = 0.0f, radius = 0f),
            dust = DustConfig(enabled = false),
            lightLeak = LightLeakConfig(enabled = false),
            dateStamp = DateStampConfig(enabled = false),
            border = BorderConfig(style = BorderStyle.NONE)
        ),

        // =====================================================================
        // B. ANALOG FILM CAMERAS (35mm & 120 Roll Film)
        // =====================================================================
        CameraPreset(
            id = "analog_35mm_classic",
            iconResName = "cam_d_classic",
            name = "D Classic",
            subtitle = "Universal 35mm Point & Shoot",
            category = CameraCategory.ANALOG_FILM,
            year = "1984",
            description = "Natural skin tones, balanced contrast, and fine silver halide grain. The iconic standard for everyday 35mm point-and-shoot street photography.",
            colorGrading = ColorGradingConfig(
                contrast = 1.14f,
                saturation = 1.08f,
                temperature = 0.12f,
                tint = 0.02f,
                fade = 0.04f,
                highlightRollOff = 0.80f,
                shadowTintR = 0.02f,
                shadowTintG = 0.01f,
                shadowTintB = 0.0f
            ),
            grain = GrainConfig(
                enabled = true,
                intensity = 0.22f,
                scale = 1.05f,
                assetPath = "grains/grain_dclassic.jpg",
                blendMode = GrainBlendMode.OVERLAY,
                shadowStrength = 0.35f,
                highlightStrength = 0.15f
            ),
            lens = LensConfig(
                vignetteIntensity = 0.35f,
                vignetteRadius = 0.80f,
                chromaticAberration = 0.0025f,
                softness = 0.05f
            ),
            halation = HalationConfig(enabled = true, threshold = 0.74f, intensity = 0.26f, radius = 18f, haloRed = 1.0f, haloGreen = 0.28f, haloBlue = 0.06f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.60f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.35f, warmColorBias = 0.85f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -28),
            border = BorderConfig(style = BorderStyle.CLASSIC_35MM_RANGEFINDER, assetPath = "dbalck_frame_in.png")
        ),

        CameraPreset(
            id = "analog_fxn_r",
            iconResName = "cam_fxnr",
            name = "FXNR",
            subtitle = "Classic Negative",
            category = CameraCategory.ANALOG_FILM,
            year = "2000",
            description = "Legendary 1600 ISO high-speed film for low-light ambiance. Authenticated warm amber/cream color temperature, velvety lifted shadow response, compressed highlight shoulder, and delicate ruby halation.",
            colorGrading = ColorGradingConfig(
                contrast = 1.08f,
                saturation = 1.02f,
                temperature = 0.18f,
                tint = 0.02f,
                fade = 0.032f,
                highlightRollOff = 0.78f,
                shadowTintR = 0.048f,
                shadowTintG = 0.022f,
                shadowTintB = 0.008f
            ),
            grain = GrainConfig(
                enabled = true,
                intensity = 0.20f,
                scale = 1.0f,
                assetPath = "grains/grain_ou.jpg",
                blendMode = GrainBlendMode.OVERLAY,
                shadowStrength = 0.26f,
                highlightStrength = 0.10f
            ),
            lens = LensConfig(
                vignetteIntensity = 0.16f,
                vignetteRadius = 0.88f,
                chromaticAberration = 0.002f,
                softness = 0.08f
            ),
            halation = HalationConfig(enabled = true, threshold = 0.78f, intensity = 0.26f, radius = 12f, haloRed = 1.0f, haloGreen = 0.35f, haloBlue = 0.08f),
            dust = DustConfig(enabled = false),
            lightLeak = LightLeakConfig(enabled = false),
            dateStamp = DateStampConfig(enabled = false),
            border = BorderConfig(style = BorderStyle.NONE)
        ),

        CameraPreset(
            id = "analog_135ne",
            iconResName = "cam_135ne",
            name = "135 NE",
            subtitle = "Kodak ColorPlus 135 Negative",
            category = CameraCategory.ANALOG_FILM,
            year = "1994",
            description = "Authentic Kodak consumer film tones with warm amber highlights, rich midtones, and classic 35mm rebate edges.",
            colorGrading = ColorGradingConfig(
                contrast = 1.14f,
                saturation = 1.08f,
                temperature = 0.14f,
                tint = 0.04f,
                fade = 0.038f,
                highlightRollOff = 0.78f,
                shadowTintR = 0.04f,
                shadowTintG = 0.03f,
                shadowTintB = 0.01f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.26f, scale = 1.10f, assetPath = "grains/grain_135sr.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.38f, highlightStrength = 0.18f),
            lens = LensConfig(vignetteIntensity = 0.40f, vignetteRadius = 0.78f, chromaticAberration = 0.004f, softness = 0.06f),
            dust = DustConfig(enabled = true, assetPath = "135_dust.png", opacity = 0.55f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.40f, warmColorBias = 0.88f),
            border = BorderConfig(style = BorderStyle.FILM_STRIP_135, assetPath = "135ne_frame_cut1.png"),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -26)
        ),

        CameraPreset(
            id = "analog_soft_film",
            iconResName = "cam_ct2f",
            name = "CT2F",
            subtitle = "Contax T2 Carl Zeiss Sonnar",
            category = CameraCategory.ANALOG_FILM,
            year = "1990",
            description = "Carl Zeiss Sonnar 38mm f/2.8 titanium compact styling. Creamy pastel highlights, gentle contrast, and velvety micro-contrast.",
            colorGrading = ColorGradingConfig(
                contrast = 0.94f,
                saturation = 0.92f,
                temperature = 0.06f,
                tint = 0.01f,
                fade = 0.055f,
                highlightRollOff = 0.75f,
                shadowTintR = 0.01f,
                shadowTintG = 0.02f,
                shadowTintB = 0.03f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 0.95f, assetPath = "grains/grain_s67.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.30f, highlightStrength = 0.14f),
            lens = LensConfig(vignetteIntensity = 0.32f, vignetteRadius = 0.82f, chromaticAberration = 0.002f, softness = 0.08f),
            halation = HalationConfig(enabled = true, threshold = 0.74f, intensity = 0.22f, radius = 14f, haloRed = 1.0f, haloGreen = 0.42f, haloBlue = 0.20f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.40f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.MINIMAL_WHITE, customYearOffset = -34),
            border = BorderConfig(style = BorderStyle.NONE)
        ),

        CameraPreset(
            id = "disp_warm",
            iconResName = "cam_135sr",
            name = "135 SR",
            subtitle = "Superia Reala 135 SLR",
            category = CameraCategory.ANALOG_FILM,
            year = "1998",
            description = "Fourth-layer color emulsion technology with natural skin rendition, luminous emerald greens, and crisp SLR optics.",
            colorGrading = ColorGradingConfig(
                contrast = 1.16f,
                saturation = 1.10f,
                temperature = 0.16f,
                tint = 0.02f,
                fade = 0.04f,
                highlightRollOff = 0.78f,
                shadowTintR = 0.03f,
                shadowTintG = 0.02f,
                shadowTintB = 0.01f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.25f, scale = 1.15f, assetPath = "grains/grain540_01.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.36f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.42f, vignetteRadius = 0.75f, chromaticAberration = 0.005f, softness = 0.08f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.55f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.35f, warmColorBias = 0.85f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -24)
        ),

        CameraPreset(
            id = "analog_srx",
            iconResName = "cam_srx",
            name = "SRX",
            subtitle = "Superia Reala Extra",
            category = CameraCategory.ANALOG_FILM,
            year = "2001",
            description = "Refined professional emulsion with expanded shadow latitude, rich micro-contrast, and subtle cyan-magenta balance.",
            colorGrading = ColorGradingConfig(
                contrast = 1.18f,
                saturation = 1.12f,
                temperature = 0.10f,
                tint = 0.01f,
                fade = 0.03f,
                highlightRollOff = 0.76f,
                shadowTintR = 0.02f,
                shadowTintG = 0.01f,
                shadowTintB = 0.02f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 1.05f, assetPath = "grains/grain540_02.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.32f, highlightStrength = 0.14f),
            lens = LensConfig(vignetteIntensity = 0.36f, vignetteRadius = 0.80f, chromaticAberration = 0.003f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.45f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -23),
            border = BorderConfig(style = BorderStyle.NONE)
        ),

        CameraPreset(
            id = "analog_ofm",
            iconResName = "cam_ofm",
            name = "OFM",
            subtitle = "Olympus OM System Pro SLR",
            category = CameraCategory.ANALOG_FILM,
            year = "1979",
            description = "Professional 35mm SLR film scanner simulation. Finer grain structure, enhanced micro-contrast, and cinematic highlight gradation.",
            colorGrading = ColorGradingConfig(
                contrast = 1.12f,
                saturation = 1.05f,
                temperature = 0.04f,
                tint = 0.00f,
                fade = 0.025f,
                highlightRollOff = 0.82f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.18f, scale = 0.90f, assetPath = "grains/grain_ofm.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.28f, highlightStrength = 0.12f),
            lens = LensConfig(vignetteIntensity = 0.30f, vignetteRadius = 0.85f, chromaticAberration = 0.002f),
            dust = DustConfig(enabled = false),
            dateStamp = DateStampConfig(enabled = false),
            border = BorderConfig(style = BorderStyle.NONE)
        ),

        CameraPreset(
            id = "analog_dblack",
            iconResName = "cam_dblack",
            name = "D Black",
            subtitle = "Monochrome Rangefinder",
            category = CameraCategory.ANALOG_FILM,
            year = "1954",
            description = "High-contrast black-and-white street photography emulsion inspired by Tri-X 400. Deep velvety shadows and punchy midtones.",
            colorGrading = ColorGradingConfig(
                contrast = 1.30f,
                saturation = 0.00f,
                temperature = 0.00f,
                tint = 0.00f,
                fade = 0.02f,
                highlightRollOff = 0.72f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.32f, scale = 1.25f, assetPath = "grains/grain_dexp.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.45f, highlightStrength = 0.20f),
            lens = LensConfig(vignetteIntensity = 0.45f, vignetteRadius = 0.72f, chromaticAberration = 0.000f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.50f),
            dateStamp = DateStampConfig(enabled = false),
            border = BorderConfig(style = BorderStyle.NONE)
        ),

        CameraPreset(
            id = "analog_s67",
            iconResName = "cam_s67",
            name = "S67",
            subtitle = "6x7 Medium Format Roll Film",
            category = CameraCategory.ANALOG_FILM,
            year = "1970",
            description = "Legendary 6x7 medium format camera rendering. Enormous optical depth, silky smooth tonal roll-off, and medium format border markers.",
            colorGrading = ColorGradingConfig(
                contrast = 1.15f,
                saturation = 1.10f,
                temperature = 0.06f,
                tint = 0.01f,
                fade = 0.035f,
                highlightRollOff = 0.78f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.16f, scale = 0.85f, assetPath = "grains/grain_s67.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.25f, highlightStrength = 0.10f),
            lens = LensConfig(vignetteIntensity = 0.35f, vignetteRadius = 0.82f, chromaticAberration = 0.002f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.40f),
            border = BorderConfig(style = BorderStyle.MEDIUM_FORMAT_67, assetPath = "s67_frame_t1.png"),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "analog_sclassic",
            iconResName = "cam_sclassic",
            name = "S Classic",
            subtitle = "120 Color Slide Film",
            category = CameraCategory.ANALOG_FILM,
            year = "1982",
            description = "Vibrant 120 format color reversal slide film with deep saturated primaries, brilliant warm sunlight, and rich contrast.",
            colorGrading = ColorGradingConfig(
                contrast = 1.22f,
                saturation = 1.16f,
                temperature = 0.12f,
                tint = 0.02f,
                fade = 0.02f,
                highlightRollOff = 0.74f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.18f, scale = 0.90f, assetPath = "grains/grain_sclassic.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.26f, highlightStrength = 0.12f),
            lens = LensConfig(vignetteIntensity = 0.38f, vignetteRadius = 0.80f, chromaticAberration = 0.003f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.40f),
            dateStamp = DateStampConfig(enabled = false),
            border = BorderConfig(style = BorderStyle.NONE)
        ),

        CameraPreset(
            id = "analog_nt16",
            iconResName = "cam_nt16",
            name = "NT16",
            subtitle = "Natura 1600 Street Night",
            category = CameraCategory.ANALOG_FILM,
            year = "2001",
            description = "Specialized night street emulsion with cool cyan shadow bias, luminous neon highlight retention, and atmospheric high-speed grain.",
            colorGrading = ColorGradingConfig(
                contrast = 1.18f,
                saturation = 1.08f,
                temperature = -0.08f,
                tint = 0.03f,
                fade = 0.03f,
                highlightRollOff = 0.72f,
                shadowTintR = 0.00f,
                shadowTintG = 0.03f,
                shadowTintB = 0.05f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.28f, scale = 1.15f, assetPath = "grains/grain_ou_lowlight.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.40f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.45f, vignetteRadius = 0.76f, chromaticAberration = 0.004f),
            dust = DustConfig(enabled = false),
            dateStamp = DateStampConfig(enabled = false),
            border = BorderConfig(style = BorderStyle.NONE)
        ),

        CameraPreset(
            id = "analog_vintage_color",
            iconResName = "cam_dexp",
            name = "D Exp",
            subtitle = "Double Exposure Experimental",
            category = CameraCategory.ANALOG_FILM,
            year = "1989",
            description = "Dual-layer optical exposure simulation with dreamy blending, magenta/cyan shifts, and creative analog light leaks.",
            colorGrading = ColorGradingConfig(
                contrast = 1.10f,
                saturation = 1.05f,
                temperature = -0.05f,
                tint = 0.08f,
                fade = 0.06f,
                highlightRollOff = 0.72f,
                shadowTintR = 0.04f,
                shadowTintG = 0.00f,
                shadowTintB = 0.03f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.26f, scale = 1.10f, assetPath = "grains/grain_dexp.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.35f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.42f, vignetteRadius = 0.78f, chromaticAberration = 0.006f, softness = 0.12f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.50f, warmColorBias = 0.75f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "disp_night",
            iconResName = "cam_halfframe",
            name = "D Half",
            subtitle = "Olympus Pen Half Frame",
            category = CameraCategory.ANALOG_FILM,
            year = "1968",
            description = "Splits 35mm film into two vertical 18x24mm frames with characteristic grain enlargement and vertical diptych framing.",
            colorGrading = ColorGradingConfig(
                contrast = 1.20f,
                saturation = 1.12f,
                temperature = 0.18f,
                tint = 0.04f,
                fade = 0.06f,
                highlightRollOff = 0.70f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.32f, scale = 1.35f, assetPath = "grains/grain_ou_lowlight.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.45f, highlightStrength = 0.22f),
            lens = LensConfig(vignetteIntensity = 0.50f, vignetteRadius = 0.70f, chromaticAberration = 0.006f, softness = 0.15f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.60f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.45f, warmColorBias = 0.90f),
            border = BorderConfig(style = BorderStyle.HALF_FRAME, assetPath = "half_border.png"),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -23)
        ),

        CameraPreset(
            id = "analog_dslide",
            iconResName = "cam_dslide",
            name = "D Slide",
            subtitle = "Mounted Color Slide Film",
            category = CameraCategory.ANALOG_FILM,
            year = "1977",
            description = "Slide film mounted in authentic cardstock projector frames with high color contrast, crisp blacks, and vibrant slide transparency.",
            colorGrading = ColorGradingConfig(
                contrast = 1.25f,
                saturation = 1.18f,
                temperature = 0.08f,
                tint = 0.01f,
                fade = 0.015f,
                highlightRollOff = 0.74f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.18f, scale = 0.95f, assetPath = "grains/grain_dexp.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.26f, highlightStrength = 0.12f),
            lens = LensConfig(vignetteIntensity = 0.38f, vignetteRadius = 0.82f, chromaticAberration = 0.003f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.40f),
            border = BorderConfig(style = BorderStyle.SLIDE_MOUNT, assetPath = "border_slidep1.jpg"),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "analog_slidep",
            iconResName = "cam_slidep",
            name = "Slide P",
            subtitle = "Polachrome Instant Slide",
            category = CameraCategory.ANALOG_FILM,
            year = "1983",
            description = "Additive color screen instant slide film with fine linear color stripe raster, high contrast, and retro projection aura.",
            colorGrading = ColorGradingConfig(
                contrast = 1.28f,
                saturation = 1.14f,
                temperature = 0.05f,
                tint = -0.02f,
                fade = 0.02f,
                highlightRollOff = 0.72f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 1.05f, assetPath = "grains/grain_slidep.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.32f, highlightStrength = 0.15f),
            lens = LensConfig(vignetteIntensity = 0.42f, vignetteRadius = 0.78f, chromaticAberration = 0.004f),
            border = BorderConfig(style = BorderStyle.SLIDE_MOUNT, assetPath = "border_slidep2.jpg"),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "analog_kv88",
            iconResName = "cam_kv88",
            name = "KV88",
            subtitle = "Kiev 88 6x6 Medium Format",
            category = CameraCategory.ANALOG_FILM,
            year = "1980",
            description = "Soviet medium format mechanical SLR with square 1:1 aspect ratio, metallic lens flare, and heavy industrial glass rendering.",
            colorGrading = ColorGradingConfig(
                contrast = 1.18f,
                saturation = 1.08f,
                temperature = 0.08f,
                tint = 0.02f,
                fade = 0.04f,
                highlightRollOff = 0.78f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.24f, scale = 1.00f, assetPath = "grains/grain_kv88.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.34f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.40f, vignetteRadius = 0.78f, chromaticAberration = 0.004f),
            border = BorderConfig(style = BorderStyle.SQUARE_INSTANT_WHITE),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "analog_kv80",
            iconResName = "cam_kv80",
            name = "KV80",
            subtitle = "Kiev 80 Compact Medium Format",
            category = CameraCategory.ANALOG_FILM,
            year = "1984",
            description = "Compact variant of medium format SLR with muted earth tones, soft edge falloff, and classic square format.",
            colorGrading = ColorGradingConfig(
                contrast = 1.14f,
                saturation = 1.04f,
                temperature = 0.10f,
                tint = 0.01f,
                fade = 0.035f,
                highlightRollOff = 0.80f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.20f, scale = 0.95f, assetPath = "grains/grain_kv88.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.30f, highlightStrength = 0.14f),
            lens = LensConfig(vignetteIntensity = 0.36f, vignetteRadius = 0.82f, chromaticAberration = 0.003f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "analog_cinema_film",
            iconResName = "cam_d3d",
            name = "D3D",
            subtitle = "Single-Image 3D Photography",
            category = CameraCategory.ANALOG_FILM,
            year = "1989",
            description = "Single-image 3D photography filter. Computational depth estimation, depth-dependent parallax, virtual stereo views, subtle chromatic separation, and dimensional relief from a single photo.",
            colorGrading = ColorGradingConfig(
                contrast = 1.10f,
                saturation = 1.05f,
                temperature = 0.00f,
                tint = 0.00f,
                fade = 0.02f,
                highlightRollOff = 0.82f,
                shadowTintR = 0.01f,
                shadowTintG = 0.01f,
                shadowTintB = 0.02f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.20f, scale = 1.0f, assetPath = "grains/grain_d3d1.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.30f, highlightStrength = 0.15f),
            lens = LensConfig(vignetteIntensity = 0.25f, vignetteRadius = 0.85f, chromaticAberration = 0.004f, softness = 0.0f),
            halation = HalationConfig(enabled = false, threshold = 0.82f, intensity = 0.25f, radius = 20f, haloRed = 1.0f, haloGreen = 0.25f, haloBlue = 0.05f),
            dust = DustConfig(enabled = false),
            dateStamp = DateStampConfig(enabled = false),
            border = BorderConfig(style = BorderStyle.NONE),
            d3dProfile = com.craftcam.app.camera.profile.D3DProfile.Default
        ),

        CameraPreset(
            id = "analog_ir",
            iconResName = "cam_ir",
            name = "IR",
            subtitle = "Aerochrome Color Infrared",
            category = CameraCategory.ANALOG_FILM,
            year = "1974",
            description = "False-color infrared aerial film turning chlorophyll-rich vegetation into vivid crimson and magenta while preserving blue skies.",
            colorGrading = ColorGradingConfig(
                contrast = 1.25f,
                saturation = 1.30f,
                temperature = -0.15f,
                tint = 0.35f,
                fade = 0.02f,
                highlightRollOff = 0.70f,
                shadowTintR = 0.08f,
                shadowTintG = 0.00f,
                shadowTintB = 0.04f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.30f, scale = 1.10f, assetPath = "grains/grain_IR.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.40f, highlightStrength = 0.18f),
            lens = LensConfig(vignetteIntensity = 0.40f, vignetteRadius = 0.75f, chromaticAberration = 0.005f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "analog_hoga",
            iconResName = "cam_hoga",
            name = "Hoga",
            subtitle = "Holga 120 Toy Camera",
            category = CameraCategory.ANALOG_FILM,
            year = "1982",
            description = "Optical plastic meniscus lens with heavy vignetting, chromatic fringing, soft dreamy edges, and quirky light leaks.",
            colorGrading = ColorGradingConfig(
                contrast = 1.20f,
                saturation = 1.15f,
                temperature = 0.15f,
                tint = 0.02f,
                fade = 0.05f,
                highlightRollOff = 0.68f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.34f, scale = 1.25f, assetPath = "grains/grain_hoga.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.46f, highlightStrength = 0.22f),
            lens = LensConfig(vignetteIntensity = 0.65f, vignetteRadius = 0.62f, chromaticAberration = 0.012f, softness = 0.22f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.55f, warmColorBias = 0.85f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "analog_original",
            iconResName = "cam_original",
            name = "Original",
            subtitle = "Analog Clean Film Base",
            category = CameraCategory.ANALOG_FILM,
            year = "1976",
            description = "True uncorrected negative film baseline with neutral color response, low distortion, and subtle filmic gamma.",
            colorGrading = ColorGradingConfig(
                contrast = 1.08f,
                saturation = 1.02f,
                temperature = 0.02f,
                tint = 0.00f,
                fade = 0.02f,
                highlightRollOff = 0.85f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.15f, scale = 0.85f, assetPath = "grains/grain_dclassic.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.22f, highlightStrength = 0.10f),
            lens = LensConfig(vignetteIntensity = 0.20f, vignetteRadius = 0.90f, chromaticAberration = 0.001f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "analog_kino",
            iconResName = "cam_kino",
            name = "Kino",
            subtitle = "Cinema 35mm Motion Still",
            category = CameraCategory.ANALOG_FILM,
            year = "1965",
            description = "Color motion picture stock with rem-jet halation, warm tungsten balance, and panoramic cinematic aspect ratio.",
            colorGrading = ColorGradingConfig(
                contrast = 1.16f,
                saturation = 1.06f,
                temperature = -0.06f,
                tint = 0.02f,
                fade = 0.025f,
                highlightRollOff = 0.74f,
                shadowTintR = 0.00f,
                shadowTintG = 0.02f,
                shadowTintB = 0.03f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.24f, scale = 1.05f, assetPath = "grains/grain_16mm.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.34f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.35f, vignetteRadius = 0.80f, chromaticAberration = 0.003f),
            halation = HalationConfig(enabled = true, threshold = 0.76f, intensity = 0.38f, radius = 20f, haloRed = 1.0f, haloGreen = 0.30f, haloBlue = 0.08f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "analog_glow",
            iconResName = "cam_glow",
            name = "Glow",
            subtitle = "Soft Focus Diffusion Glow",
            category = CameraCategory.ANALOG_FILM,
            year = "1972",
            description = "Vintage diffusion glass filter creating ethereal bloom on skin highlights while preserving deep velvety shadow contrast.",
            colorGrading = ColorGradingConfig(
                contrast = 0.98f,
                saturation = 0.96f,
                temperature = 0.05f,
                tint = 0.01f,
                fade = 0.04f,
                highlightRollOff = 0.72f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.16f, scale = 0.90f, assetPath = "grains/grain_dclassic.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.24f, highlightStrength = 0.10f),
            lens = LensConfig(vignetteIntensity = 0.28f, vignetteRadius = 0.85f, chromaticAberration = 0.002f, softness = 0.25f),
            halation = HalationConfig(enabled = true, threshold = 0.70f, intensity = 0.45f, radius = 28f, haloRed = 1.0f, haloGreen = 0.65f, haloBlue = 0.40f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = false)
        ),

        // =====================================================================
        // C. CCD DIGITAL CAMERAS (Early 2000s & Street Digital)
        // =====================================================================
        CameraPreset(
            id = "ccd_r",
            iconResName = "cam_ccdr",
            name = "CCD R",
            subtitle = "Rangefinder CCD Digital",
            category = CameraCategory.CCD_DIGITAL,
            year = "2006",
            description = "Early CCD rangefinder sensor signature. Rich primary colors, saturated reds, high micro-contrast and punchy highlight response.",
            colorGrading = ColorGradingConfig(
                contrast = 1.24f,
                saturation = 1.20f,
                temperature = 0.06f,
                tint = -0.02f,
                fade = 0.015f,
                highlightRollOff = 0.94f,
                shadowTintR = 0.00f,
                shadowTintG = 0.01f,
                shadowTintB = 0.03f
            ),
            grain = GrainConfig(
                enabled = true,
                intensity = 0.24f,
                scale = 0.95f,
                isColorNoise = true,
                assetPath = "grains/grain_ccdr.jpg",
                blendMode = GrainBlendMode.OVERLAY,
                shadowStrength = 0.38f,
                highlightStrength = 0.20f
            ),
            lens = LensConfig(
                vignetteIntensity = 0.32f,
                vignetteRadius = 0.85f,
                chromaticAberration = 0.003f
            ),
            halation = HalationConfig(enabled = true, threshold = 0.92f, intensity = 0.08f, radius = 8f, haloRed = 0.85f, haloGreen = 0.85f, haloBlue = 1.0f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.40f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "analog_warm_film",
            iconResName = "cam_grdr",
            name = "GR D",
            subtitle = "Ricoh GR Digital Snap",
            category = CameraCategory.CCD_DIGITAL,
            year = "2005",
            description = "Legendary 28mm wide street snap camera. High micro-contrast, sharp optical rendering, and deep, evocative shadow delineation.",
            colorGrading = ColorGradingConfig(
                contrast = 1.26f,
                saturation = 1.12f,
                temperature = 0.16f,
                tint = 0.02f,
                fade = 0.02f,
                highlightRollOff = 0.82f,
                shadowTintR = 0.02f,
                shadowTintG = 0.01f,
                shadowTintB = 0.00f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.25f, scale = 1.05f, isColorNoise = false, assetPath = "grains/grain_ou.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.36f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.45f, vignetteRadius = 0.72f, chromaticAberration = 0.003f),
            dust = DustConfig(enabled = true, assetPath = "135_dust.png", opacity = 0.45f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -21)
        ),

        CameraPreset(
            id = "ccd_y2k_digital",
            iconResName = "cam_classicu",
            name = "Classic U",
            subtitle = "Y2K Compact CCD Sensor",
            category = CameraCategory.CCD_DIGITAL,
            year = "2002",
            description = "The defining aesthetic of early 2000s digicams. Cool blue sky saturation, sharp edges, and distinctive digital highlight glare.",
            colorGrading = ColorGradingConfig(
                contrast = 1.28f,
                saturation = 1.15f,
                temperature = -0.12f,
                tint = 0.05f,
                fade = 0.02f,
                highlightRollOff = 0.90f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.20f, scale = 0.90f, isColorNoise = true, assetPath = "grains/grain_classicu.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.32f, highlightStrength = 0.15f),
            lens = LensConfig(vignetteIntensity = 0.28f, vignetteRadius = 0.85f, chromaticAberration = 0.005f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.35f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.DIGITAL_LCD_GREEN, customYearOffset = -22)
        ),

        CameraPreset(
            id = "ccd_retro_compact",
            iconResName = "cam_dqs",
            name = "DQS",
            subtitle = "Digital QuickSnap Hybrid",
            category = CameraCategory.CCD_DIGITAL,
            year = "2003",
            description = "Hybrid digital point-and-shoot combining early digital processing with disposable camera optical punch.",
            colorGrading = ColorGradingConfig(
                contrast = 1.22f,
                saturation = 1.10f,
                temperature = 0.05f,
                tint = 0.00f,
                fade = 0.015f,
                highlightRollOff = 0.88f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.18f, scale = 0.90f, isColorNoise = true, assetPath = "grains/grain_dqs.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.28f, highlightStrength = 0.12f),
            lens = LensConfig(vignetteIntensity = 0.35f, vignetteRadius = 0.80f, chromaticAberration = 0.004f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.35f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -21)
        ),

        CameraPreset(
            id = "ccd_flash",
            iconResName = "cam_dfuns",
            name = "D Funs",
            subtitle = "CCD Flash Party Camera",
            category = CameraCategory.CCD_DIGITAL,
            year = "2001",
            description = "Punchy high-energy direct flash look. High contrast, saturated neon tones, and rapid radial light falloff.",
            colorGrading = ColorGradingConfig(
                contrast = 1.30f,
                saturation = 1.18f,
                temperature = 0.08f,
                tint = 0.02f,
                fade = 0.02f,
                highlightRollOff = 0.88f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 1.00f, isColorNoise = true, assetPath = "grains/grain_dfuns.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.34f, highlightStrength = 0.15f),
            lens = LensConfig(vignetteIntensity = 0.45f, vignetteRadius = 0.72f, chromaticAberration = 0.004f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.35f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -23)
        ),

        CameraPreset(
            id = "ccd_dam",
            iconResName = "cam_dam",
            name = "DAM",
            subtitle = "Digital Analog Master",
            category = CameraCategory.CCD_DIGITAL,
            year = "2006",
            description = "Professional medium format digital back color science with supreme dynamic fidelity, neutral gradations, and studio balance.",
            colorGrading = ColorGradingConfig(
                contrast = 1.10f,
                saturation = 1.04f,
                temperature = 0.00f,
                tint = 0.00f,
                fade = 0.01f,
                highlightRollOff = 0.90f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.12f, scale = 0.80f, isColorNoise = false, assetPath = "grains/grain_ccdr.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.20f, highlightStrength = 0.08f),
            lens = LensConfig(vignetteIntensity = 0.22f, vignetteRadius = 0.90f, chromaticAberration = 0.001f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = false)
        ),

        // =====================================================================
        // D. DISPOSABLE CAMERAS (Single-Use & Toy)
        // =====================================================================
        CameraPreset(
            id = "disp_classic",
            iconResName = "cam_golf",
            name = "Golf",
            subtitle = "Vintage Sports Disposable",
            category = CameraCategory.DISPOSABLE,
            year = "1993",
            description = "Sports single-use camera with high-contrast sunlight tuning, fast shutter freeze, and green golf course color boost.",
            colorGrading = ColorGradingConfig(
                contrast = 1.25f,
                saturation = 1.15f,
                temperature = 0.15f,
                tint = -0.02f,
                fade = 0.05f,
                highlightRollOff = 0.74f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.28f, scale = 1.20f, assetPath = "grains/grain_dexp2.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.40f, highlightStrength = 0.18f),
            lens = LensConfig(vignetteIntensity = 0.50f, vignetteRadius = 0.70f, chromaticAberration = 0.007f, softness = 0.10f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.60f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.40f, warmColorBias = 0.80f),
            border = BorderConfig(style = BorderStyle.GOLF_FRAME, assetPath = "golf_frame.jpg"),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -31)
        ),

        CameraPreset(
            id = "disp_flash",
            iconResName = "cam_cpm35",
            name = "CPM 35",
            subtitle = "Color Print Motion 35",
            category = CameraCategory.DISPOSABLE,
            year = "1996",
            description = "Disposable camera loaded with motion print film featuring intense red/orange halation around bright light sources.",
            colorGrading = ColorGradingConfig(
                contrast = 1.22f,
                saturation = 1.12f,
                temperature = 0.12f,
                tint = 0.02f,
                fade = 0.04f,
                highlightRollOff = 0.76f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.28f, scale = 1.20f, assetPath = "grains/grain_cpm35.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.40f, highlightStrength = 0.18f),
            lens = LensConfig(vignetteIntensity = 0.48f, vignetteRadius = 0.72f, chromaticAberration = 0.006f, softness = 0.10f),
            halation = HalationConfig(enabled = true, threshold = 0.72f, intensity = 0.42f, radius = 22f, haloRed = 1.0f, haloGreen = 0.22f, haloBlue = 0.04f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.55f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.45f, warmColorBias = 0.90f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -28)
        ),

        CameraPreset(
            id = "disp_fqs",
            iconResName = "cam_fqs",
            name = "FQS",
            subtitle = "Fast QuickSnap 800",
            category = CameraCategory.DISPOSABLE,
            year = "1999",
            description = "High-speed 800 ISO single-use camera for dusk and indoor parties with gritty grain and punchy flash colors.",
            colorGrading = ColorGradingConfig(
                contrast = 1.26f,
                saturation = 1.14f,
                temperature = 0.10f,
                tint = 0.02f,
                fade = 0.045f,
                highlightRollOff = 0.72f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.32f, scale = 1.25f, assetPath = "grains/grain_dqs.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.44f, highlightStrength = 0.20f),
            lens = LensConfig(vignetteIntensity = 0.46f, vignetteRadius = 0.72f, chromaticAberration = 0.006f, softness = 0.12f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.50f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -25),
            border = BorderConfig(style = BorderStyle.NONE)
        ),

        CameraPreset(
            id = "disp_puli",
            iconResName = "cam_puli",
            name = "Puli",
            subtitle = "Pulight Toy Flash",
            category = CameraCategory.DISPOSABLE,
            year = "1995",
            description = "Playful novelty toy camera with whimsical color saturation, high flash bounce, and soft plastic edge distortion.",
            colorGrading = ColorGradingConfig(
                contrast = 1.24f,
                saturation = 1.22f,
                temperature = 0.14f,
                tint = 0.06f,
                fade = 0.05f,
                highlightRollOff = 0.70f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.28f, scale = 1.15f, assetPath = "grains/grain_dfuns.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.38f, highlightStrength = 0.18f),
            lens = LensConfig(vignetteIntensity = 0.52f, vignetteRadius = 0.68f, chromaticAberration = 0.008f, softness = 0.18f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.55f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "analog_fisheye_f",
            iconResName = "cam_grdr",
            name = "Fisheye Bubble",
            subtitle = "Ultra-Wide Circular Fisheye",
            category = CameraCategory.DISPOSABLE,
            year = "1995",
            description = "Curvilinear 180-degree optical fisheye barrel distortion with intense peripheral vignette and psychedelic skate video aesthetic.",
            colorGrading = ColorGradingConfig(
                contrast = 1.22f,
                saturation = 1.15f,
                temperature = 0.08f,
                tint = 0.02f,
                fade = 0.03f,
                highlightRollOff = 0.78f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.20f, scale = 1.0f, assetPath = "grains/grain_dclassic.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.30f, highlightStrength = 0.14f),
            lens = LensConfig(vignetteIntensity = 0.65f, vignetteRadius = 0.60f, chromaticAberration = 0.022f, barrelDistortion = 0.92f, softness = 0.12f),
            border = BorderConfig(style = BorderStyle.NONE),
            dateStamp = DateStampConfig(enabled = false)
        ),

        // =====================================================================
        // E. INSTANT CAMERAS (Integral & Peel-Apart)
        // =====================================================================
        CameraPreset(
            id = "inst_vintage",
            iconResName = "cam_instc",
            name = "Inst C",
            subtitle = "Instax Color Mini",
            category = CameraCategory.INSTANT,
            year = "1998",
            description = "Vibrant credit-card size instant analog film with lifted cyan shadows, creamy pastel highlights, and sharp instant optics.",
            colorGrading = ColorGradingConfig(
                contrast = 1.12f,
                saturation = 1.10f,
                temperature = -0.04f,
                tint = 0.04f,
                fade = 0.09f,
                highlightRollOff = 0.74f,
                shadowTintR = 0.01f,
                shadowTintG = 0.04f,
                shadowTintB = 0.06f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.20f, scale = 0.90f, assetPath = "grains/grain_instc.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.28f, highlightStrength = 0.12f),
            lens = LensConfig(vignetteIntensity = 0.35f, vignetteRadius = 0.82f, chromaticAberration = 0.003f, softness = 0.10f),
            dust = DustConfig(enabled = true, assetPath = "dust_instc.png", opacity = 0.45f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.30f, warmColorBias = 0.70f),
            border = BorderConfig(style = BorderStyle.POLAROID_WHITE_CHIN, assetPath = "vinst_border1.png"),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "inst_sq",
            iconResName = "cam_instsq",
            name = "Inst SQ",
            subtitle = "Square Instant Color",
            category = CameraCategory.INSTANT,
            year = "2017",
            description = "1:1 square format instant film with rich color saturation, deep chemical blacks, and iconic square white borders.",
            colorGrading = ColorGradingConfig(
                contrast = 1.15f,
                saturation = 1.14f,
                temperature = 0.02f,
                tint = 0.02f,
                fade = 0.07f,
                highlightRollOff = 0.75f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.18f, scale = 0.90f, assetPath = "grains/grain_instc.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.26f, highlightStrength = 0.12f),
            lens = LensConfig(vignetteIntensity = 0.32f, vignetteRadius = 0.85f, chromaticAberration = 0.002f),
            border = BorderConfig(style = BorderStyle.SQUARE_INSTANT_WHITE, assetPath = "instsq_frame01.png"),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "inst_sqc",
            iconResName = "cam_instsqc",
            name = "Inst SQC",
            subtitle = "Square Color Filter Instant",
            category = CameraCategory.INSTANT,
            year = "2019",
            description = "Square instant camera with creative color flash gels (amber, orange, magenta) for surreal nighttime portraits.",
            colorGrading = ColorGradingConfig(
                contrast = 1.18f,
                saturation = 1.20f,
                temperature = 0.14f,
                tint = 0.06f,
                fade = 0.06f,
                highlightRollOff = 0.74f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.20f, scale = 0.95f, assetPath = "grains/grain_instc.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.28f, highlightStrength = 0.14f),
            lens = LensConfig(vignetteIntensity = 0.38f, vignetteRadius = 0.80f, chromaticAberration = 0.003f),
            border = BorderConfig(style = BorderStyle.SQUARE_INSTANT_WHITE, assetPath = "instsq_frame02.png"),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "inst_ss",
            iconResName = "cam_instss",
            name = "Inst SS",
            subtitle = "Silver Instant Monochrome",
            category = CameraCategory.INSTANT,
            year = "2015",
            description = "Monochrome instant chemistry with deep silver blacks, delicate midtone greys, and wide dynamic range.",
            colorGrading = ColorGradingConfig(
                contrast = 1.22f,
                saturation = 0.00f,
                temperature = 0.00f,
                tint = 0.00f,
                fade = 0.05f,
                highlightRollOff = 0.76f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 1.00f, assetPath = "grains/grain_instc.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.32f, highlightStrength = 0.15f),
            lens = LensConfig(vignetteIntensity = 0.35f, vignetteRadius = 0.82f, chromaticAberration = 0.000f),
            border = BorderConfig(style = BorderStyle.POLAROID_WHITE_CHIN, assetPath = "vinst_border2.png"),
            dateStamp = DateStampConfig(enabled = false)
        ),

        CameraPreset(
            id = "inst_paf",
            iconResName = "cam_paf",
            name = "PAF",
            subtitle = "Polaroid Autofocus SLR 680",
            category = CameraCategory.INSTANT,
            year = "1982",
            description = "Legendary folding SLR instant camera with sonar autofocus, glass 4-element lens, rich chemistry, and iconic square frame.",
            colorGrading = ColorGradingConfig(
                contrast = 1.16f,
                saturation = 1.12f,
                temperature = 0.10f,
                tint = 0.03f,
                fade = 0.08f,
                highlightRollOff = 0.74f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 1.00f, assetPath = "grains/grain_paf.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.30f, highlightStrength = 0.14f),
            lens = LensConfig(vignetteIntensity = 0.40f, vignetteRadius = 0.78f, chromaticAberration = 0.003f),
            dust = DustConfig(enabled = true, assetPath = "dust_instc.png", opacity = 0.40f),
            border = BorderConfig(style = BorderStyle.POLAROID_WHITE_CHIN, assetPath = "vinst_border3.png"),
            dateStamp = DateStampConfig(enabled = false)
        ),

        // =====================================================================
        // F. CAMCORDERS (Analog Video)
        // =====================================================================
        CameraPreset(
            id = "cam_retro_vhs",
            iconResName = "cam_bessa",
            name = "Retro VHS",
            subtitle = "Tape Distortion & OSD",
            category = CameraCategory.CAMCORDER,
            year = "1988",
            description = "Magnetic tape artifacts, horizontal color ghosting and iconic tracking distortion.",
            colorGrading = ColorGradingConfig(
                contrast = 1.10f,
                saturation = 0.95f,
                temperature = 0.04f,
                tint = 0.08f,
                fade = 0.08f,
                highlightRollOff = 0.85f,
                shadowTintR = 0.04f,
                shadowTintG = 0.01f,
                shadowTintB = 0.05f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.32f, scale = 1.40f, isColorNoise = true, assetPath = "grains/vhs_grain01.jpg", blendMode = GrainBlendMode.SCREEN, shadowStrength = 0.40f, highlightStrength = 0.20f),
            lens = LensConfig(vignetteIntensity = 0.35f, vignetteRadius = 0.80f, chromaticAberration = 0.012f, softness = 0.25f),
            dust = DustConfig(enabled = true, assetPath = "vhs_grain_dust01.jpg", opacity = 0.50f),
            border = BorderConfig(style = BorderStyle.CAMCORDER_FRAME),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.DIGITAL_LCD_GREEN, customYearOffset = -36)
        ),

        CameraPreset(
            id = "cam_analog_video",
            iconResName = "cam_quicksnap",
            name = "Analog Video",
            subtitle = "8mm Home Video Tape",
            category = CameraCategory.CAMCORDER,
            year = "1994",
            description = "Soft rounded home video colors with gentle horizontal chroma smear and warm ambient lighting.",
            colorGrading = ColorGradingConfig(
                contrast = 1.08f,
                saturation = 1.02f,
                temperature = 0.14f,
                tint = 0.02f,
                fade = 0.05f,
                highlightRollOff = 0.82f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.24f, scale = 1.30f, assetPath = "grains/grain_16mm.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.32f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.26f, vignetteRadius = 0.85f, chromaticAberration = 0.004f, softness = 0.15f),
            dust = DustConfig(enabled = true, assetPath = "8mm_dust.png", opacity = 0.45f),
            border = BorderConfig(style = BorderStyle.ROUNDED_16MM_GATE, assetPath = "mask_16mm_02_r.jpg"),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.MONOSPACE_DOT_MATRIX, customYearOffset = -30)
        ),

        CameraPreset(
            id = "cam_90s_camcorder",
            iconResName = "cam_lomo",
            name = "90s Camcorder",
            subtitle = "Handheld CCD Video",
            category = CameraCategory.CAMCORDER,
            year = "1997",
            description = "Crisp vintage television broadcast look with sharp LCD timecodes and high-energy 90s aesthetic.",
            colorGrading = ColorGradingConfig(
                contrast = 1.15f,
                saturation = 1.08f,
                temperature = -0.06f,
                tint = 0.04f,
                fade = 0.03f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 1.20f, isColorNoise = true, assetPath = "grains/grain_dcr_01.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.30f, highlightStrength = 0.15f),
            lens = LensConfig(vignetteIntensity = 0.24f, vignetteRadius = 0.88f, chromaticAberration = 0.003f),
            dust = DustConfig(enabled = true, assetPath = "vhs_grain_dust05.jpg", opacity = 0.40f),
            border = BorderConfig(style = BorderStyle.CAMCORDER_FRAME),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.DIGITAL_LCD_GREEN, customYearOffset = -27)
        )
    )

    fun getPresetById(id: String): CameraPreset {
        // Fast canonical alias mappings
        when (id.lowercase()) {
            "analog_35mm_classic", "d_classic", "dclassic", "d classic", "classic_35mm" ->
                return allPresets.find { it.id == "analog_35mm_classic" } ?: allPresets.first()
            "analog_fxn_r", "fxnr", "fxn_r" ->
                return allPresets.find { it.id == "analog_fxn_r" } ?: allPresets.first()
            "ccd_r", "ccdr", "ccd r", "ccd-r", "ccd_classic" ->
                return allPresets.find { it.id == "ccd_r" } ?: allPresets.first()
            "ccd_flash", "ccd flash", "d_funs", "dfuns" ->
                return allPresets.find { it.id == "ccd_flash" } ?: allPresets.first()
            "ccd_retro_compact", "dqs" ->
                return allPresets.find { it.id == "ccd_retro_compact" } ?: allPresets.first()
            "ccd_y2k_digital", "classic_u", "classicu", "classic u" ->
                return allPresets.find { it.id == "ccd_y2k_digital" } ?: allPresets.first()
            "disp_classic", "golf" ->
                return allPresets.find { it.id == "disp_classic" } ?: allPresets.first()
            "disp_flash", "cpm35", "cpm 35" ->
                return allPresets.find { it.id == "disp_flash" } ?: allPresets.first()
            "disp_warm", "135sr", "135 sr" ->
                return allPresets.find { it.id == "disp_warm" } ?: allPresets.first()
            "disp_night", "dhalf", "d half", "half" ->
                return allPresets.find { it.id == "disp_night" } ?: allPresets.first()
            "inst_vintage", "inst_c", "instc" ->
                return allPresets.find { it.id == "inst_vintage" } ?: allPresets.first()
            "analog_135ne", "135ne", "135 ne" ->
                return allPresets.find { it.id == "analog_135ne" } ?: allPresets.first()
            "analog_soft_film", "ct2f" ->
                return allPresets.find { it.id == "analog_soft_film" } ?: allPresets.first()
            "analog_warm_film", "grd", "gr dr", "gr digital" ->
                return allPresets.find { it.id == "analog_warm_film" } ?: allPresets.first()
            "analog_vintage_color", "dexp", "d exp" ->
                return allPresets.find { it.id == "analog_vintage_color" } ?: allPresets.first()
            "analog_cinema_film", "d3d", "d 3d", "d_3d" ->
                return allPresets.find { it.id == "analog_cinema_film" } ?: allPresets.first()
            "analog_s67", "s67" ->
                return allPresets.find { it.id == "analog_s67" } ?: allPresets.first()
            "analog_sclassic", "sclassic", "s classic" ->
                return allPresets.find { it.id == "analog_sclassic" } ?: allPresets.first()
            "analog_ofm", "ofm" ->
                return allPresets.find { it.id == "analog_ofm" } ?: allPresets.first()
            "analog_nt16", "nt16" ->
                return allPresets.find { it.id == "analog_nt16" } ?: allPresets.first()
            "ccd_dam", "dam" ->
                return allPresets.find { it.id == "ccd_dam" } ?: allPresets.first()
            "inst_paf", "paf" ->
                return allPresets.find { it.id == "inst_paf" } ?: allPresets.first()
            "inst_sq", "instsq" ->
                return allPresets.find { it.id == "inst_sq" } ?: allPresets.first()
            "inst_ss", "instss" ->
                return allPresets.find { it.id == "inst_ss" } ?: allPresets.first()
        }
        return allPresets.find { it.id == id } ?: allPresets.first()
    }
}
