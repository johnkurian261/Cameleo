package com.craftcam.app.motion

import kotlin.math.roundToInt

enum class GolfMotionDuration(val durationMs: Long, val seconds: Int, val label: String) {
    ONE_SEC(1000L, 1, "1s"),
    TWO_SEC(2000L, 2, "2s"),
    THREE_SEC(3000L, 3, "3s");

    val preShutterMs: Long get() = durationMs / 2L
    val postShutterMs: Long get() = durationMs - preShutterMs

    companion object {
        fun fromSeconds(seconds: Int): GolfMotionDuration = when (seconds) {
            1 -> ONE_SEC
            3 -> THREE_SEC
            else -> TWO_SEC
        }
    }
}

enum class GolfPlaybackCadence(val targetFps: Int, val label: String) {
    VINTAGE(14, "Vintage"),
    SMOOTH(24, "Smooth");

    companion object {
        fun fromName(name: String?): GolfPlaybackCadence = when {
            name.equals("SMOOTH", ignoreCase = true) -> SMOOTH
            else -> VINTAGE
        }
    }
}

data class GolfMotionConfig(
    val enabled: Boolean = false,
    val duration: GolfMotionDuration = GolfMotionDuration.TWO_SEC,
    val playback: GolfPlaybackCadence = GolfPlaybackCadence.VINTAGE,
    val flashEffectEnabled: Boolean = true,
    val loopPlayback: Boolean = true,
    val audioEnabled: Boolean = true
)

enum class GolfMotionCapturePhase {
    IDLE,
    ARMED,
    CAPTURING_POST_SHUTTER,
    DEVELOPING_MOTION
}

data class GolfMotionUiState(
    val enabled: Boolean = false,
    val duration: GolfMotionDuration = GolfMotionDuration.TWO_SEC,
    val playback: GolfPlaybackCadence = GolfPlaybackCadence.VINTAGE,
    val flashEffectEnabled: Boolean = true,
    val loopPlayback: Boolean = true,
    val audioEnabled: Boolean = true,
    val phase: GolfMotionCapturePhase = GolfMotionCapturePhase.IDLE,
    val progressFraction: Float = 0f,
    val remainingMs: Long = 0L
) {
    val isRecordingMotion: Boolean
        get() = phase == GolfMotionCapturePhase.CAPTURING_POST_SHUTTER ||
            phase == GolfMotionCapturePhase.DEVELOPING_MOTION
}

data class GolfMotionMetadata(
    val mediaId: Long,
    val durationMs: Long,
    val fps: Int,
    val playbackMode: String,
    val loopPlayback: Boolean,
    val flashEffectApplied: Boolean,
    val hasAudio: Boolean,
    val coverFrameIndex: Int,
    val totalFrames: Int,
    val width: Int,
    val height: Int,
    val sourceKind: String, // "capture", "samsung_motion_photo", "google_motion_photo", "apple_live_photo", "video_clip"
    val colorProfileId: String,
    val createdAtEpochSec: Long
) {
    fun coverTimeMs(): Long {
        if (totalFrames <= 1 || durationMs <= 0L) return 0L
        val clampedIdx = coverFrameIndex.coerceIn(0, totalFrames - 1)
        return ((clampedIdx.toFloat() / (totalFrames - 1)) * durationMs).roundToInt().toLong()
    }
}
