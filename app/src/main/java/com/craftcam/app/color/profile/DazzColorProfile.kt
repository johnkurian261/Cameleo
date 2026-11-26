package com.craftcam.app.color.profile

import androidx.compose.ui.graphics.Color
import com.craftcam.app.config.FilmColorProfileAdjustment

/**
 * Authentic Dazz Cam Color Profile representation with dedicated 3D LUT assets.
 * Based on reverse engineering of Dazz Cam's `Localizable.strings` (`LOOKUP_*_TIP`),
 * `Assets.car` (`icon_filter_*`), and `DZImageFilter.framework` (`lookup_*`).
 */
data class DazzColorProfile(
    val index: Int,
    val id: String,
    val displayName: String,
    val tipKey: String,
    val description: String,
    val topColor: Color,
    val bodyColor: Color,
    val hasRainbow: Boolean = false,
    val isMonochrome: Boolean = false,
    val lutAssetPath: String,
    val thumbnailDrawableName: String = "thumb_profile_${id.lowercase()}",
    val adjustment: FilmColorProfileAdjustment
)

object DazzColorProfileCatalog {
    val PROFILES: List<DazzColorProfile> = listOf(
        // 0. 03 G: Warm daylight movie film with lifted shadows and amber halation
        DazzColorProfile(
            index = 0,
            id = "03G",
            displayName = "03 G",
            tipKey = "LOOKUP_03G_TIP",
            description = "Daylight-balanced movie film, with additional grading and reduced contrast.",
            topColor = Color(0xFF8D6E63),
            bodyColor = Color(0xFF3E2723),
            lutAssetPath = "lut_profile_03g.png",
            adjustment = FilmColorProfileAdjustment(
                tempDelta = 0.07f,
                tintDelta = -0.02f,
                fadeDelta = 0.03f,
                contrastMult = 0.95f,
                satMult = 1.00f
            )
        ),
        // 1. C400: Signature balanced daylight print film (Default)
        DazzColorProfile(
            index = 1,
            id = "C400",
            displayName = "C400",
            tipKey = "LOOKUP_C400_TIP",
            description = "Slightly overexposed ISO 400 color film. Authentic film colors.",
            topColor = Color(0xFFFFFFFF),
            bodyColor = Color(0xFF00C853),
            hasRainbow = true,
            lutAssetPath = "lut_profile_c400.png",
            adjustment = FilmColorProfileAdjustment(
                tempDelta = 0.00f,
                tintDelta = 0.00f,
                fadeDelta = 0.00f,
                contrastMult = 1.00f,
                satMult = 1.00f
            )
        ),
        // 2. 07 FF: Vibrant cinematic motion picture film
        DazzColorProfile(
            index = 2,
            id = "07FF",
            displayName = "07 FF",
            tipKey = "LOOKUP_07FF_TIP",
            description = "Daylight-balanced motion picture film, with vibrant color rendering based on scans from a standard film scanner.",
            topColor = Color(0xFF80DEEA),
            bodyColor = Color(0xFF00796B),
            lutAssetPath = "lut_profile_07ff.png",
            adjustment = FilmColorProfileAdjustment(
                tempDelta = -0.05f,
                tintDelta = -0.04f,
                fadeDelta = 0.02f,
                contrastMult = 1.02f,
                satMult = 1.12f
            )
        ),
        // 3. 19 P: Tungsten-balanced movie film with print tones
        DazzColorProfile(
            index = 3,
            id = "19P",
            displayName = "19 P",
            tipKey = "LOOKUP_19P_TIP",
            description = "Tungsten-balanced movie film, with warm-leaning color rendering based on print tones.",
            topColor = Color(0xFFD1C4E9),
            bodyColor = Color(0xFF4A148C),
            lutAssetPath = "lut_profile_19p.png",
            adjustment = FilmColorProfileAdjustment(
                tempDelta = -0.08f,
                tintDelta = 0.07f,
                fadeDelta = 0.01f,
                contrastMult = 1.08f,
                satMult = 1.06f
            )
        ),
        // 4. 19 P2: Tungsten-balanced movie film with generic print tones
        DazzColorProfile(
            index = 4,
            id = "19P2",
            displayName = "19 P2",
            tipKey = "LOOKUP_19P2_TIP",
            description = "Tungsten-balanced movie film, with warm-leaning color rendering based on generic print tones.",
            topColor = Color(0xFFCE93D8),
            bodyColor = Color(0xFF6A1B9A),
            lutAssetPath = "lut_profile_19p2.png",
            adjustment = FilmColorProfileAdjustment(
                tempDelta = -0.06f,
                tintDelta = 0.04f,
                fadeDelta = 0.02f,
                contrastMult = 1.03f,
                satMult = 1.03f
            )
        ),
        // 5. 19 M: Tungsten-balanced movie film with monitor's tone
        DazzColorProfile(
            index = 5,
            id = "19M",
            displayName = "19 M",
            tipKey = "LOOKUP_19M_TIP",
            description = "Tungsten-balanced movie film, with warm-leaning color rendering based on the monitor’s tone.",
            topColor = Color(0xFFFFCC80),
            bodyColor = Color(0xFFE65100),
            lutAssetPath = "lut_profile_19m.png",
            adjustment = FilmColorProfileAdjustment(
                tempDelta = 0.04f,
                tintDelta = 0.02f,
                fadeDelta = 0.00f,
                contrastMult = 1.05f,
                satMult = 1.08f
            )
        ),
        // 6. 22 M: Black-and-white movie film with balanced tonal rendering
        DazzColorProfile(
            index = 6,
            id = "22M",
            displayName = "22 M",
            tipKey = "LOOKUP_22M_TIP",
            description = "Black-and-white movie film with balanced tonal rendering.",
            topColor = Color(0xFFEEEEEE),
            bodyColor = Color(0xFF212121),
            isMonochrome = true,
            lutAssetPath = "lut_profile_22m.png",
            adjustment = FilmColorProfileAdjustment(
                tempDelta = 0.00f,
                tintDelta = 0.00f,
                fadeDelta = 0.02f,
                contrastMult = 1.12f,
                satMult = 0.00f
            )
        ),
        // 7. EG200: Everyday daylight film with vibrant colors and slight green tint
        DazzColorProfile(
            index = 7,
            id = "EG200",
            displayName = "EG200",
            tipKey = "LOOKUP_EG200_TIP",
            description = "Everyday daylight film with vibrant colors, a slight green tint, and solid dynamic range.",
            topColor = Color(0xFFFFE082),
            bodyColor = Color(0xFFF57F17),
            lutAssetPath = "lut_profile_eg200.png",
            adjustment = FilmColorProfileAdjustment(
                tempDelta = 0.03f,
                tintDelta = -0.06f,
                fadeDelta = 0.01f,
                contrastMult = 1.04f,
                satMult = 1.10f
            )
        ),
        // 8. FX33: Authentic CCD look with smooth tones and subtle cool tint
        DazzColorProfile(
            index = 8,
            id = "FX33",
            displayName = "FX33",
            tipKey = "LOOKUP_FX33_TIP",
            description = "A more authentic CCD look with smooth tones, a subtle cool tint, and balanced contrast.",
            topColor = Color(0xFF90CAF9),
            bodyColor = Color(0xFF1565C0),
            lutAssetPath = "lut_profile_fx33.png",
            adjustment = FilmColorProfileAdjustment(
                tempDelta = -0.04f,
                tintDelta = 0.01f,
                fadeDelta = 0.00f,
                contrastMult = 1.04f,
                satMult = 1.05f
            )
        ),
        // 9. FXN2: Vintage negative film look with smoother tones
        DazzColorProfile(
            index = 9,
            id = "FXN2",
            displayName = "FXN2",
            tipKey = "LOOKUP_FXN2_TIP",
            description = "Compared to FXN, FXN2 offers smoother tones and colors, with slightly lower contrast.",
            topColor = Color(0xFFFFAB91),
            bodyColor = Color(0xFFD84315),
            lutAssetPath = "lut_profile_fxn2.png",
            adjustment = FilmColorProfileAdjustment(
                tempDelta = 0.02f,
                tintDelta = 0.01f,
                fadeDelta = 0.02f,
                contrastMult = 0.96f,
                satMult = 0.98f
            )
        )
    )

    fun getByIndex(index: Int): DazzColorProfile =
        PROFILES.getOrNull(index.coerceIn(0, PROFILES.size - 1)) ?: PROFILES[1]

    fun getById(id: String): DazzColorProfile =
        PROFILES.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PROFILES[1]
}
