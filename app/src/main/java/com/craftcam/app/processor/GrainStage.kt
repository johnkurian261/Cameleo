package com.craftcam.app.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.camera.profile.GrainMode
import com.craftcam.app.camera.profile.GrainProfile
import com.craftcam.app.gl.shaders.ShaderConstants
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.model.GrainBlendMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * Authoritative Film Grain Stage.
 *
 * Enforces single-grain pipeline discipline:
 * 1. Single authoritative decision point: SCANNED, PROCEDURAL, or HYBRID.
 * 2. Explicitly PREVENTS accidental double-grain stacking (e.g., procedural noise
 *    is strictly forced to 0.0 when SCANNED grain is active).
 * 3. Handles non-repeating organic spatial jitter & anti-tiling projection.
 * 4. Provides unified configuration for both GPU preview shaders and multi-core CPU still processing.
 */
object GrainStage {

    private const val TAG = "GrainStage"

    data class ResolvedGrainConfig(
        val mode: GrainMode,
        val assetPath: String?,
        val effectiveIntensity: Float,
        val scale: Float,
        val blendMode: GrainBlendMode,
        val shadowStrength: Float,
        val highlightStrength: Float,
        val isColorNoise: Boolean,
        val useScannedTexture: Boolean,
        val proceduralIntensity: Float,
        val luminanceWeightingEnabled: Boolean = true,
        val luminanceWeightingPower: Float = 1.0f
    )

    data class AntiTilingOffsets(
        val seedX: Int,
        val seedY: Int,
        val flipX: Boolean,
        val flipY: Boolean,
        val colBaseX: IntArray,
        val colPhaseY: IntArray,
        val rowBaseY: IntArray,
        val rowPhaseX: IntArray
    )

    private val grainPixelsCache = mutableMapOf<String, Pair<IntArray, Pair<Int, Int>>>()
    private val textureCache = mutableMapOf<String, Bitmap?>()
    private val lock = Any()

    /**
     * Resolves the authoritative grain configuration for a capture or preview frame.
     * Guarantees that procedural noise and scanned textures do NOT stack inadvertently.
     */
    fun resolve(
        preset: CameraPreset,
        grainProfile: GrainProfile,
        tuning: UserTuningConfig,
        intensityFactor: Float = 1.0f
    ): ResolvedGrainConfig {
        val tuningBoost = tuning.grainBoost
        val baseIntensity = grainProfile.intensity
        val effectiveIntensity = (baseIntensity * tuningBoost * intensityFactor).coerceIn(0.0f, 1.0f)

        if (effectiveIntensity <= 0.005f) {
            return ResolvedGrainConfig(
                mode = grainProfile.mode,
                assetPath = null,
                effectiveIntensity = 0.0f,
                scale = grainProfile.scale,
                blendMode = grainProfile.blendMode,
                shadowStrength = grainProfile.shadowStrength,
                highlightStrength = grainProfile.highlightStrength,
                isColorNoise = grainProfile.isColorNoise,
                useScannedTexture = false,
                proceduralIntensity = 0.0f,
                luminanceWeightingEnabled = grainProfile.luminanceWeightingEnabled,
                luminanceWeightingPower = grainProfile.luminanceWeightingPower
            )
        }

        val isCamcorder = preset.category == CameraCategory.CAMCORDER
        val asset = tuning.debugGrainAsset
            ?: grainProfile.assetPath
            ?: preset.grain.assetPath
            ?: ShaderConstants.getGrainAssetForPreset(preset.id, isCamcorder)

        val blendMode = tuning.debugGrainBlendMode ?: grainProfile.blendMode

        return when (grainProfile.mode) {
            GrainMode.SCANNED -> {
                // STRICT DISCIPLINE: Scanned texture only. Procedural is guaranteed 0.0f.
                ResolvedGrainConfig(
                    mode = GrainMode.SCANNED,
                    assetPath = asset,
                    effectiveIntensity = effectiveIntensity,
                    scale = grainProfile.scale,
                    blendMode = blendMode,
                    shadowStrength = grainProfile.shadowStrength,
                    highlightStrength = grainProfile.highlightStrength,
                    isColorNoise = grainProfile.isColorNoise,
                    useScannedTexture = asset.isNotEmpty(),
                    proceduralIntensity = 0.0f, // NEVER stack procedural grain on scanned grain
                    luminanceWeightingEnabled = grainProfile.luminanceWeightingEnabled,
                    luminanceWeightingPower = grainProfile.luminanceWeightingPower
                )
            }
            GrainMode.PROCEDURAL -> {
                // Strictly procedural: No scanned asset texture used
                ResolvedGrainConfig(
                    mode = GrainMode.PROCEDURAL,
                    assetPath = null,
                    effectiveIntensity = effectiveIntensity,
                    scale = grainProfile.scale,
                    blendMode = blendMode,
                    shadowStrength = grainProfile.shadowStrength,
                    highlightStrength = grainProfile.highlightStrength,
                    isColorNoise = grainProfile.isColorNoise,
                    useScannedTexture = false,
                    proceduralIntensity = effectiveIntensity,
                    luminanceWeightingEnabled = grainProfile.luminanceWeightingEnabled,
                    luminanceWeightingPower = grainProfile.luminanceWeightingPower
                )
            }
            GrainMode.HYBRID -> {
                // Scanned primary texture (85%) + subtle micro-fine crystal procedural layer (15%)
                val hasAsset = asset.isNotEmpty()
                ResolvedGrainConfig(
                    mode = GrainMode.HYBRID,
                    assetPath = if (hasAsset) asset else null,
                    effectiveIntensity = effectiveIntensity * 0.85f,
                    scale = grainProfile.scale,
                    blendMode = blendMode,
                    shadowStrength = grainProfile.shadowStrength,
                    highlightStrength = grainProfile.highlightStrength,
                    isColorNoise = grainProfile.isColorNoise,
                    useScannedTexture = hasAsset,
                    proceduralIntensity = if (hasAsset) effectiveIntensity * 0.15f else effectiveIntensity,
                    luminanceWeightingEnabled = grainProfile.luminanceWeightingEnabled,
                    luminanceWeightingPower = grainProfile.luminanceWeightingPower
                )
            }
        }
    }

    /**
     * Loads raw grain pixel buffer for multi-core CPU still processing.
     */
    fun getOrLoadGrainPixels(context: Context, assetName: String): Pair<IntArray, Pair<Int, Int>>? {
        synchronized(lock) {
            grainPixelsCache[assetName]?.let { return it }
            val resolvedName = if (assetName.startsWith("grains/")) assetName else {
                try {
                    context.assets.open(assetName).close()
                    assetName
                } catch (e: Exception) {
                    "grains/$assetName"
                }
            }
            grainPixelsCache[resolvedName]?.let {
                grainPixelsCache[assetName] = it
                return it
            }

            val bmp = loadTexture(context, resolvedName) ?: loadTexture(context, assetName) ?: return null
            val gw = bmp.width
            val gh = bmp.height
            val gp = IntArray(gw * gh)
            bmp.getPixels(gp, 0, gw, 0, 0, gw, gh)
            val result = Pair(gp, Pair(gw, gh))
            grainPixelsCache[assetName] = result
            grainPixelsCache[resolvedName] = result
            return result
        }
    }

    private fun loadTexture(context: Context, name: String): Bitmap? {
        if (textureCache.containsKey(name)) {
            return textureCache[name]
        }
        return try {
            context.assets.open(name).use { input ->
                val bmp = BitmapFactory.decodeStream(input)
                textureCache[name] = bmp
                bmp
            }
        } catch (e: Exception) {
            Log.w(TAG, "Scanned grain asset not found: $name")
            textureCache[name] = null
            null
        }
    }

    /**
     * Computes anti-tiling continuous coordinate transformation tables for high-resolution images.
     * Prevents periodic tiling grids on 12MP/48MP outputs without inner-loop trigonometry cost.
     */
    fun computeAntiTilingTables(
        width: Int,
        height: Int,
        grainWidth: Int,
        grainHeight: Int,
        grainScale: Float
    ): AntiTilingOffsets {
        val captureSeed = (System.currentTimeMillis() xor (width.toLong() shl 16) xor height.toLong())
        val gw = maxOf(1, grainWidth)
        val gh = maxOf(1, grainHeight)
        val seedX = ((captureSeed ushr 7) % gw).toInt().let { if (it < 0) it + gw else it }
        val seedY = ((captureSeed ushr 19) % gh).toInt().let { if (it < 0) it + gh else it }
        val flipX = (captureSeed and 1L) == 1L
        val flipY = (captureSeed and 2L) == 2L

        val invScale = 1.0f / maxOf(0.1f, grainScale)
        val colBaseX = IntArray(width) { x -> (x * invScale).toInt() + seedX }
        val colPhaseY = IntArray(width) { x -> (cos(x * invScale * 0.00785f + seedX * 0.01f) * 26f).toInt() }
        val rowBaseY = IntArray(height) { y -> (y * invScale).toInt() + seedY }
        val rowPhaseX = IntArray(height) { y -> (sin(y * invScale * 0.00785f + seedY * 0.01f) * 26f).toInt() }

        return AntiTilingOffsets(
            seedX = seedX,
            seedY = seedY,
            flipX = flipX,
            flipY = flipY,
            colBaseX = colBaseX,
            colPhaseY = colPhaseY,
            rowBaseY = rowBaseY,
            rowPhaseX = rowPhaseX
        )
    }

    fun clearCache() {
        synchronized(lock) {
            grainPixelsCache.clear()
            textureCache.values.forEach { it?.recycle() }
            textureCache.clear()
        }
    }
}
