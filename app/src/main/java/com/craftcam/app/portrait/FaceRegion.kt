package com.craftcam.app.portrait

import android.graphics.PointF
import android.graphics.RectF

/**
 * Ephemeral face region coordinates detected strictly on-device.
 * Stored only temporarily during image analysis and post-processing;
 * zero biometric identities or facial embeddings are extracted or retained.
 */
data class FaceRegion(
    /** Normalized face bounding box [0.0f, 1.0f] in oriented image coordinates. */
    val bounds: RectF,
    /** Detection confidence score [0.0f, 1.0f]. */
    val confidence: Float,
    /** Face head roll rotation angle in degrees. */
    val rotation: Float = 0f,
    /** Optional normalized landmarks if provided by hardware ISP */
    val leftEye: PointF? = null,
    val rightEye: PointF? = null,
    val mouth: PointF? = null
) {
    /**
     * Converts normalized [0.0f, 1.0f] bounds to absolute pixel coordinates.
     */
    fun toPixelRect(imageWidth: Int, imageHeight: Int): RectF {
        return RectF(
            bounds.left * imageWidth,
            bounds.top * imageHeight,
            bounds.right * imageWidth,
            bounds.bottom * imageHeight
        )
    }

    /**
     * Checks if normalized coordinate (nx, ny) falls within face boundary.
     */
    fun contains(nx: Float, ny: Float): Boolean = bounds.contains(nx, ny)

    /**
     * Fraction of total frame area occupied by this face.
     */
    val areaFraction: Float get() = bounds.width() * bounds.height()
}
