package com.craftcam.app.config

import android.content.Context
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Persistent application settings backed by SharedPreferences and exposed reactively
 * for Compose UI, OpenGL viewfinder rendering, and capture/gallery pipelines.
 */
data class AppSettingsState(
    val mirrorFrontCamera: Boolean = true,
    val saveFilmNegatives: Boolean = true,
    val highResolutionExport: Boolean = true,
    val tactileHaptics: Boolean = true,
    val preserveExif: Boolean = true
)

class AppSettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        AppSettingsState(
            mirrorFrontCamera = prefs.getBoolean(KEY_MIRROR_FRONT_CAMERA, true),
            saveFilmNegatives = prefs.getBoolean(KEY_SAVE_FILM_NEGATIVES, true),
            highResolutionExport = prefs.getBoolean(KEY_HIGH_RESOLUTION_EXPORT, true),
            tactileHaptics = prefs.getBoolean(KEY_TACTILE_HAPTICS, true),
            preserveExif = prefs.getBoolean(KEY_PRESERVE_EXIF, true)
        )
    )

    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    val current: AppSettingsState
        get() = _state.value

    fun setMirrorFrontCamera(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MIRROR_FRONT_CAMERA, enabled).apply()
        _state.update { it.copy(mirrorFrontCamera = enabled) }
    }

    fun setSaveFilmNegatives(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_FILM_NEGATIVES, enabled).apply()
        _state.update { it.copy(saveFilmNegatives = enabled) }
    }

    fun setHighResolutionExport(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_RESOLUTION_EXPORT, enabled).apply()
        _state.update { it.copy(highResolutionExport = enabled) }
    }

    fun setTactileHaptics(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TACTILE_HAPTICS, enabled).apply()
        _state.update { it.copy(tactileHaptics = enabled) }
    }

    fun setPreserveExif(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRESERVE_EXIF, enabled).apply()
        _state.update { it.copy(preserveExif = enabled) }
    }

    companion object {
        private const val PREFS_NAME = "cameleo_settings"
        private const val KEY_MIRROR_FRONT_CAMERA = "mirror_front_camera"
        private const val KEY_SAVE_FILM_NEGATIVES = "save_film_negatives"
        private const val KEY_HIGH_RESOLUTION_EXPORT = "high_resolution_export"
        private const val KEY_TACTILE_HAPTICS = "tactile_haptics"
        private const val KEY_PRESERVE_EXIF = "preserve_exif"

        @Volatile
        private var instance: AppSettingsStore? = null

        fun get(context: Context): AppSettingsStore {
            return instance ?: synchronized(this) {
                instance ?: AppSettingsStore(context).also { instance = it }
            }
        }
    }
}

val LocalAppSettings = compositionLocalOf { AppSettingsState() }

fun View.performCameleoHaptic(
    enabled: Boolean,
    feedbackConstant: Int = HapticFeedbackConstants.KEYBOARD_TAP
) {
    if (enabled) {
        performHapticFeedback(feedbackConstant)
    }
}

/**
 * Film emulsion color profile offsets matching the Collection sheet swatches:
 * 0 = 03 G (Warm golden matte film)
 * 1 = C400 (Balanced daylight print film - default neutral)
 * 2 = 07 FF (Cool cyan-green cinematic emulsion)
 * 3 = 19 P (Deep punchy twilight indigo/magenta cast)
 */
data class FilmColorProfileAdjustment(
    val tempDelta: Float,
    val tintDelta: Float,
    val fadeDelta: Float,
    val contrastMult: Float,
    val satMult: Float
)

fun filmColorProfileAdjustment(index: Int): FilmColorProfileAdjustment = when (index) {
    0 -> FilmColorProfileAdjustment(
        tempDelta = 0.07f,
        tintDelta = -0.02f,
        fadeDelta = 0.03f,
        contrastMult = 0.95f,
        satMult = 1.00f
    )
    2 -> FilmColorProfileAdjustment(
        tempDelta = -0.05f,
        tintDelta = -0.05f,
        fadeDelta = 0.02f,
        contrastMult = 1.00f,
        satMult = 1.04f
    )
    3 -> FilmColorProfileAdjustment(
        tempDelta = -0.08f,
        tintDelta = 0.07f,
        fadeDelta = 0.00f,
        contrastMult = 1.06f,
        satMult = 1.05f
    )
    else -> FilmColorProfileAdjustment(
        tempDelta = 0.00f,
        tintDelta = 0.00f,
        fadeDelta = 0.00f,
        contrastMult = 1.00f,
        satMult = 1.00f
    )
}

