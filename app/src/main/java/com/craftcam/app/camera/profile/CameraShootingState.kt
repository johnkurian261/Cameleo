package com.craftcam.app.camera.profile

import com.craftcam.app.camera.AspectRatioMode

/**
 * Dedicated shooting state driving the camera-specific shooting screen.
 * Survives recomposition and dynamically updates optical parameters in real time.
 */
data class CameraShootingState(
    val cameraProfile: CameleoCameraProfile = CameraProfileCatalog.FXN_R,
    val selectedFocalLength: FocalLengthOption = CameraProfileCatalog.FXN_R.getDefaultFocalOption(),
    val selectedLens: LensProfile = LensProfile.NORMAL,
    val selectedAspectRatio: AspectRatioMode = CameraProfileCatalog.FXN_R.defaultRatio,
    val exposureCompensation: Float = 0.0f,
    val isFlashActive: Boolean = false
)

