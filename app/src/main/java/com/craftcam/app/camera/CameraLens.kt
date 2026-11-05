package com.craftcam.app.camera

data class CameraLens(
    val id: String,
    val cameraId: String,
    val physicalCameraId: String? = null,
    val facing: Int,
    val focalLengthMm: Float,
    val equivalentFocalLength35mm: Float,
    val equivalentZoom: Float,
    val displayName: String,
    val lensDescription: String,
    val isPhysicalLens: Boolean = true
)

