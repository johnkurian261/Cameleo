package com.craftcam.app.computational

import android.graphics.Bitmap

/**
 * Common contract for multi-frame exposure fusion and high-dynamic-range reconstruction.
 */
interface ExposureFusionEngine {
    /**
     * Fuses multiple aligned exposures into a single high-dynamic-range linear representation
     * and renders into [outputBitmap].
     */
    fun fuse(
        sequence: FrameSequence,
        alignments: List<AlignmentResult>,
        ghostingMask: GhostingMask,
        targetWidth: Int,
        targetHeight: Int,
        outputBitmap: Bitmap,
        mirrorFrontCamera: Boolean = false
    ): Bitmap
}
