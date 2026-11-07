package com.craftcam.app.scene

/**
 * Photographic scene categories classified by CAMELEO's on-device scene intelligence.
 */
enum class SceneType(val displayName: String) {
    UNKNOWN("Standard"),
    OUTDOOR("Outdoor"),
    INDOOR("Indoor"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait"),
    FOOD("Food"),
    DOCUMENT("Document"),
    NIGHT("Night"),
    BACKLIT("Backlit")
}

/**
 * Scene classification outcome with quantified confidence [0.0f, 1.0f] and operational reasoning.
 */
data class SceneClassification(
    val type: SceneType,
    val confidence: Float,
    val reason: String = ""
)
