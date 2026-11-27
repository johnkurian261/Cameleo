package com.craftcam.app.gl

import android.opengl.GLES30
import com.craftcam.app.portrait.FaceRegion
import com.craftcam.app.thermal.ThermalMaskSnapshot
import com.craftcam.app.thermal.ThermalPaletteEngine
import com.craftcam.app.thermal.ThermalSubjectEstimator
import com.craftcam.app.thermal.ThermalVisionConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shared OpenGL ES 3.0 texture/uniform utility used cleanly by both
 * [GLViewfinderRenderer] and [GPUVideoProcessor] so Thermal Vision preview and video
 * recording share identical shader bindings without code duplication.
 */
internal object ThermalGlHelper {

    data class UniformLocs(
        val enabled: Int,
        val palette: Int,
        val sensitivity: Int,
        val contrast: Int,
        val inverted: Int,
        val softness: Int,
        val noiseIntensity: Int,
        val adaptiveContrast: Int,
        val maskValid: Int,
        val maskYFlip: Int,
        val minLuma: Int,
        val maxLuma: Int,
        val paletteTex: Int,
        val maskTex: Int,
        val hasFace: Int,
        val faceRect: Int
    ) {
        fun locate(programId: Int): UniformLocs = UniformLocs(
            enabled = GLES30.glGetUniformLocation(programId, "uThermalEnabled"),
            palette = GLES30.glGetUniformLocation(programId, "uThermalPalette"),
            sensitivity = GLES30.glGetUniformLocation(programId, "uThermalSensitivity"),
            contrast = GLES30.glGetUniformLocation(programId, "uThermalContrast"),
            inverted = GLES30.glGetUniformLocation(programId, "uThermalInverted"),
            softness = GLES30.glGetUniformLocation(programId, "uThermalSoftness"),
            noiseIntensity = GLES30.glGetUniformLocation(programId, "uThermalNoiseIntensity"),
            adaptiveContrast = GLES30.glGetUniformLocation(programId, "uThermalAdaptiveContrast"),
            maskValid = GLES30.glGetUniformLocation(programId, "uThermalMaskValid"),
            maskYFlip = GLES30.glGetUniformLocation(programId, "uThermalMaskYFlip"),
            minLuma = GLES30.glGetUniformLocation(programId, "uThermalMinLuma"),
            maxLuma = GLES30.glGetUniformLocation(programId, "uThermalMaxLuma"),
            paletteTex = GLES30.glGetUniformLocation(programId, "uThermalPaletteTex"),
            maskTex = GLES30.glGetUniformLocation(programId, "uThermalMaskTex"),
            hasFace = GLES30.glGetUniformLocation(programId, "uThermalHasFace"),
            faceRect = GLES30.glGetUniformLocation(programId, "uThermalFaceRect")
        )

        companion object {
            val UNSET = UniformLocs(-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1)
        }
    }

    fun createPaletteTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val id = ids[0]
        if (id == 0) return 0
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val buf = ThermalPaletteEngine.createGlTextureBuffer()
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA,
            ThermalPaletteEngine.LUT_WIDTH,
            ThermalPaletteEngine.LUT_HEIGHT,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            buf
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return id
    }

    fun createMaskTexture(): Int {
        val w = ThermalSubjectEstimator.DEFAULT_MASK_WIDTH
        val h = ThermalSubjectEstimator.DEFAULT_MASK_HEIGHT
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val id = ids[0]
        if (id == 0) return 0
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val zero = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA,
            w,
            h,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            zero
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return id
    }

    fun bindSamplerUnits(locs: UniformLocs, paletteTexId: Int, maskTexId: Int, dummy2DTexId: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE6)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (paletteTexId != 0) paletteTexId else dummy2DTexId)
        if (locs.paletteTex != -1) GLES30.glUniform1i(locs.paletteTex, 6)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE7)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (maskTexId != 0) maskTexId else dummy2DTexId)
        if (locs.maskTex != -1) GLES30.glUniform1i(locs.maskTex, 7)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    fun bindInactive(locs: UniformLocs, dummy2DTexId: Int) {
        if (locs.enabled != -1) GLES30.glUniform1i(locs.enabled, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE6)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dummy2DTexId)
        if (locs.paletteTex != -1) GLES30.glUniform1i(locs.paletteTex, 6)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE7)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dummy2DTexId)
        if (locs.maskTex != -1) GLES30.glUniform1i(locs.maskTex, 7)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    fun bindActiveStage(
        locs: UniformLocs,
        config: ThermalVisionConfig,
        paletteTexId: Int,
        maskTexId: Int,
        dummy2DTexId: Int,
        snapshot: ThermalMaskSnapshot?,
        uploadSnapshotToTex: Boolean,
        faces: List<FaceRegion>,
        randomSeedLoc: Int,
        randomSeed: Float
    ) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE6)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (paletteTexId != 0) paletteTexId else dummy2DTexId)
        if (locs.paletteTex != -1) GLES30.glUniform1i(locs.paletteTex, 6)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE7)
        val useMask = config.segmentationEnabled && snapshot != null && maskTexId != 0
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (useMask) maskTexId else dummy2DTexId)
        if (useMask && uploadSnapshotToTex) {
            snapshot!!.rgbaBuffer.rewind()
            GLES30.glTexSubImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                0,
                0,
                snapshot.width,
                snapshot.height,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                snapshot.rgbaBuffer
            )
        }
        if (locs.maskTex != -1) GLES30.glUniform1i(locs.maskTex, 7)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        if (locs.enabled != -1) GLES30.glUniform1i(locs.enabled, 1)
        if (locs.palette != -1) GLES30.glUniform1i(locs.palette, config.palette.index)
        if (locs.sensitivity != -1) GLES30.glUniform1f(locs.sensitivity, config.sensitivity.coerceIn(0.4f, 2.2f))
        if (locs.contrast != -1) GLES30.glUniform1f(locs.contrast, config.contrast.coerceIn(0.5f, 2.0f))
        if (locs.inverted != -1) GLES30.glUniform1i(locs.inverted, if (config.inverted) 1 else 0)
        if (locs.softness != -1) GLES30.glUniform1f(locs.softness, config.sensorSoftness.coerceIn(0f, 1f))
        if (locs.noiseIntensity != -1) GLES30.glUniform1f(locs.noiseIntensity, config.noiseIntensity.coerceIn(0f, 0.15f))
        if (locs.adaptiveContrast != -1) GLES30.glUniform1i(locs.adaptiveContrast, if (config.adaptiveContrastEnabled) 1 else 0)
        if (locs.maskValid != -1) GLES30.glUniform1i(locs.maskValid, if (useMask) 1 else 0)
        if (locs.maskYFlip != -1) GLES30.glUniform1i(locs.maskYFlip, 1)
        if (locs.minLuma != -1) GLES30.glUniform1f(locs.minLuma, snapshot?.adaptiveMinLuma ?: 0.05f)
        if (locs.maxLuma != -1) GLES30.glUniform1f(locs.maxLuma, snapshot?.adaptiveMaxLuma ?: 0.92f)

        val primaryFace = if (config.segmentationEnabled) faces.firstOrNull() else null
        if (locs.hasFace != -1) GLES30.glUniform1i(locs.hasFace, if (primaryFace != null) 1 else 0)
        if (locs.faceRect != -1) {
            if (primaryFace != null) {
                val r = primaryFace.bounds
                GLES30.glUniform4f(
                    locs.faceRect,
                    r.centerX().coerceIn(0f, 1f),
                    r.centerY().coerceIn(0f, 1f),
                    (r.width() * 0.65f).coerceAtLeast(0.06f),
                    (r.height() * 0.78f).coerceAtLeast(0.08f)
                )
            } else {
                GLES30.glUniform4f(locs.faceRect, 0.5f, 0.5f, 0.1f, 0.12f)
            }
        }
        if (randomSeedLoc != -1) GLES30.glUniform1f(randomSeedLoc, randomSeed)
    }
}
