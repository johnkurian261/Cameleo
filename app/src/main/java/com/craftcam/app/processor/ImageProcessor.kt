package com.craftcam.app.processor

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.camera.profile.LensProfile
import com.craftcam.app.camera.profile.LensType
import com.craftcam.app.captureframe.CaptureFrameMapper
import com.craftcam.app.captureframe.CaptureFrameSnapshot
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.model.BorderStyle
import com.craftcam.app.model.GrainBlendMode
import com.craftcam.app.model.GrainSourceMode
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

object ImageProcessor {

    private const val TAG = "ImageProcessor"

    // ─── Fast Memory Caches ─────────────────────────────────────────────────
    private val lutCache = mutableMapOf<String, IntArray>()
    private val textureCache = mutableMapOf<String, Bitmap?>()
    private val grainPixelsCache = mutableMapOf<String, Pair<IntArray, Pair<Int, Int>>>()

    // ─── Main Processing & Saving Pipeline ──────────────────────────────────
    suspend fun processAndSaveImage(
        context: Context,
        imageProxy: ImageProxy,
        preset: CameraPreset,
        tuning: UserTuningConfig = UserTuningConfig(),
        aspectRatio: AspectRatioMode,
        mirrorFrontCamera: Boolean,
        engineMode: com.craftcam.app.model.CameraEngineMode = com.craftcam.app.model.CameraEngineMode.FILM,
        naturalProfile: com.craftcam.app.model.NaturalCameraProfile = com.craftcam.app.model.NaturalCameraProfile.S21_PROFILE,
        naturalColorProfile: com.craftcam.app.color.NaturalColorProfile = com.craftcam.app.color.NaturalColorProfile.S21_CALIBRATED,
        compareWithOriginal: Boolean = false,
        captureFrameSnapshot: CaptureFrameSnapshot? = null,
        lensProfile: LensProfile = LensProfile.NORMAL,
        isFlashActive: Boolean = false,
        sensorIso: Int = 100,
        exposureTimeNs: Long = 10_000_000L
    ): Uri? = withContext(Dispatchers.Default) {
        val rawBitmap: Bitmap
        val rotation: Int
        try {
            rotation = imageProxy.imageInfo.rotationDegrees
            rawBitmap = imageProxyToBitmap(imageProxy)
        } finally {
            // Instantly free the hardware buffer so CameraX never drops subsequent shots
            imageProxy.close()
        }

        Log.i(TAG, "Native full-sensor photo captured: ${rawBitmap.width}x${rawBitmap.height}, rotation=$rotation, preset=${preset.id}")
        
        // 1. Single-Pass Transformation Matrix (Downscale >4096px, Upright Rotation, Mirror, Aspect Ratio Crop)
        // Eliminates intermediate scaled, oriented, and cropped Bitmap allocations (saving up to 146MB heap churn)
        val rawW = rawBitmap.width
        val rawH = rawBitmap.height

        val matrix = Matrix()
        // A. Cap at 4096px only for ultra-high megapixel sensors to protect heap
        if (rawW > 4096 || rawH > 4096) {
            val scale = 4096f / maxOf(rawW, rawH)
            matrix.postScale(scale, scale)
        }

        // B. Rotate & mirror to produce upright photo
        if (rotation != 0) matrix.postRotate(rotation.toFloat())
        if (mirrorFrontCamera) matrix.postScale(-1f, 1f)

        // C. Map bounds to calculate oriented dimensions
        val srcBounds = RectF(0f, 0f, rawW.toFloat(), rawH.toFloat())
        val orientedBounds = RectF()
        matrix.mapRect(orientedBounds, srcBounds)

        val orientedW = orientedBounds.width().roundToInt()
        val orientedH = orientedBounds.height().roundToInt()

        // D. Calculate composition framing crop in oriented space (circular fisheye retains full frame)
        val cropRect: Rect = if (captureFrameSnapshot != null && lensProfile.type != LensType.FISHEYE_F) {
            CaptureFrameMapper.mapToImageCropRect(captureFrameSnapshot, orientedW, orientedH)
        } else {
            val srcRatio = orientedW.toFloat() / orientedH
            val targetRatio = if (orientedW > orientedH) aspectRatio.ratio else (1.0f / aspectRatio.ratio)
            val cW: Int
            val cH: Int
            if (srcRatio > targetRatio) {
                cH = orientedH
                cW = (orientedH * targetRatio).toInt().coerceIn(1, orientedW)
            } else {
                cW = orientedW
                cH = (orientedW / targetRatio).toInt().coerceIn(1, orientedH)
            }
            val sX = (orientedW - cW) / 2
            val sY = (orientedH - cH) / 2
            Rect(sX, sY, sX + cW, sY + cH)
        }

        val cropW = cropRect.width()
        val cropH = cropRect.height()
        val startX = cropRect.left
        val startY = cropRect.top

        // E. Translate bounding box to origin (0, 0) and apply center-crop translation
        matrix.postTranslate(-orientedBounds.left - startX, -orientedBounds.top - startY)

        // F. Acquire working buffer from thread-safe BitmapPool and draw in single pass
        var workingBitmap: Bitmap = BitmapPool.acquire(cropW, cropH)
        try {
            val canvas = Canvas(workingBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(rawBitmap, matrix, paint)
        } finally {
            // Free transient rawBitmap immediately: lifetime is under ~30ms
            rawBitmap.recycle()
        }

        try {
            val uri: Uri?
            if (engineMode == com.craftcam.app.model.CameraEngineMode.NATURAL) {
                // Natural Camera Computational Photography Pipeline:
                // Film grain, halation, dust, light leaks, borders, date stamps are 100% disabled
                workingBitmap = NaturalPhotoProcessor.process(
                    source = workingBitmap,
                    profile = naturalProfile,
                    colorProfile = naturalColorProfile,
                    compareWithOriginal = compareWithOriginal,
                    sensorIso = sensorIso,
                    exposureTimeNs = exposureTimeNs
                )
                uri = saveToGallery(context, workingBitmap, "CAMELEO Natural")
            } else if (engineMode == com.craftcam.app.model.CameraEngineMode.PRO) {
                // Pro Camera Manual Capture:
                // Pure full-resolution capture with manual ISO/Shutter/WB/Focus
                // Film grain, halation, dust, light leaks, borders, date stamps are 100% disabled
                uri = saveToGallery(context, workingBitmap, "CAMELEO Pro")
            } else {
                // 2. True Film-Science Emulsion & Optics Processing with Real Dazz Assets
                // Executed in-place on workingBitmap using pooled IntArrays (zero new Bitmap allocations)
                workingBitmap = applyPresetEffects(context, workingBitmap, preset, tuning, lensProfile, isFlashActive)

                // 3. Apply Retro Date Stamp (draws in-place onto mutable workingBitmap)
                if (tuning.dateStampEnabled && preset.dateStamp != null && preset.dateStamp.enabled) {
                    val stamped = DateStampRenderer.applyDateStamp(workingBitmap, preset.dateStamp, tuning.customYear, context)
                    if (stamped != workingBitmap) {
                        BitmapPool.release(workingBitmap)
                        workingBitmap = stamped
                    }
                }

                // 4. Apply Analog Film Border & Authentic Camera Cutout
                if (tuning.frameBorderEnabled && preset.border != null && preset.border.style != BorderStyle.NONE) {
                    val bordered = BorderRenderer.applyBorder(workingBitmap, preset.border, context)
                    if (bordered != workingBitmap) {
                        // Instantly return the photo bitmap to the pool, and retain bordered bitmap for saving
                        BitmapPool.release(workingBitmap)
                        workingBitmap = bordered
                    }
                }

                // 5. Save to MediaStore (DCIM/Cameleo)
                uri = saveToGallery(context, workingBitmap, preset.name)
            }
            uri
        } catch (t: Throwable) {
            Log.e(TAG, "Error in image processing pipeline", t)
            null
        } finally {
            // Release workingBitmap back into BitmapPool (or recycle if unpooled border bitmap)
            BitmapPool.release(workingBitmap)
        }
    }

    // ─── 3D Stereoscopic Wigglegram Pipeline ──────────────────────────────────
    suspend fun processAndSaveThreeDImage(
        context: Context,
        imageProxy: ImageProxy,
        preset: CameraPreset,
        tuning: UserTuningConfig = UserTuningConfig(),
        aspectRatio: AspectRatioMode,
        mirrorFrontCamera: Boolean,
        captureFrameSnapshot: CaptureFrameSnapshot? = null,
        lensProfile: LensProfile = LensProfile.NORMAL,
        isFlashActive: Boolean = false,
        faces: List<com.craftcam.app.portrait.FaceRegion> = emptyList(),
        onProgress: ((String) -> Unit)? = null
    ): Uri? = withContext(Dispatchers.Default) {
        val rawBitmap: Bitmap
        val rotation: Int
        try {
            rotation = imageProxy.imageInfo.rotationDegrees
            rawBitmap = imageProxyToBitmap(imageProxy)
        } finally {
            imageProxy.close()
        }

        val rawW = rawBitmap.width
        val rawH = rawBitmap.height

        val matrix = Matrix()
        if (rawW > 4096 || rawH > 4096) {
            val scale = 4096f / maxOf(rawW, rawH)
            matrix.postScale(scale, scale)
        }
        if (rotation != 0) matrix.postRotate(rotation.toFloat())
        if (mirrorFrontCamera) matrix.postScale(-1f, 1f)

        val srcBounds = RectF(0f, 0f, rawW.toFloat(), rawH.toFloat())
        val orientedBounds = RectF()
        matrix.mapRect(orientedBounds, srcBounds)

        val orientedW = orientedBounds.width().roundToInt()
        val orientedH = orientedBounds.height().roundToInt()

        val cropRect: Rect = if (captureFrameSnapshot != null && lensProfile.type != LensType.FISHEYE_F) {
            CaptureFrameMapper.mapToImageCropRect(captureFrameSnapshot, orientedW, orientedH)
        } else {
            val srcRatio = orientedW.toFloat() / orientedH
            val targetRatio = if (orientedW > orientedH) aspectRatio.ratio else (1.0f / aspectRatio.ratio)
            val cW: Int
            val cH: Int
            if (srcRatio > targetRatio) {
                cH = orientedH
                cW = (orientedH * targetRatio).toInt().coerceIn(1, orientedW)
            } else {
                cW = orientedW
                cH = (orientedW / targetRatio).toInt().coerceIn(1, orientedH)
            }
            val sX = (orientedW - cW) / 2
            val sY = (orientedH - cH) / 2
            Rect(sX, sY, sX + cW, sY + cH)
        }

        val cropW = cropRect.width()
        val cropH = cropRect.height()
        val startX = cropRect.left
        val startY = cropRect.top

        matrix.postTranslate(-orientedBounds.left - startX, -orientedBounds.top - startY)

        var workingBitmap: Bitmap = BitmapPool.acquire(cropW, cropH)
        try {
            val canvas = Canvas(workingBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(rawBitmap, matrix, paint)
        } finally {
            rawBitmap.recycle()
        }

        try {
            // Apply vintage film grading to the photograph WITHOUT baking in grain before 3D parallax warping
            workingBitmap = applyPresetEffects(
                context = context,
                source = workingBitmap,
                preset = preset,
                tuning = tuning,
                lensProfile = lensProfile,
                isFlashActive = isFlashActive,
                applyGrain = false
            )

            // Feed to 3D Wigglegram pipeline
            val threeDProcessor = com.craftcam.app.threed.ThreeDPhotoProcessor()
            val uri = threeDProcessor.processAndSaveThreeDPhoto(
                context = context,
                sourceBitmap = workingBitmap,
                faces = faces,
                onProgress = onProgress
            )
            uri
        } catch (t: Throwable) {
            Log.e(TAG, "Error in 3D wigglegram pipeline", t)
            null
        } finally {
            BitmapPool.release(workingBitmap)
        }
    }

    /**
     * Executes the multi-frame computational photography pipeline on a [FrameSequence].
     * Coordinates alignment, motion deghosting, Mertens radiometric exposure fusion,
     * adaptive noise reduction, tone mapping, and natural color science.
     */
    suspend fun processComputationalPhoto(
        context: Context,
        sequence: com.craftcam.app.computational.FrameSequence,
        config: com.craftcam.app.computational.ComputationalPhotoConfig,
        aspectRatio: AspectRatioMode,
        mirrorFrontCamera: Boolean,
        naturalProfile: com.craftcam.app.model.NaturalCameraProfile,
        compareWithOriginal: Boolean = false,
        captureLatencyMs: Long = 0L,
        processingProfile: com.craftcam.app.config.ProcessingProfile = com.craftcam.app.config.ProcessingProfile.NaturalDefault,
        faces: List<com.craftcam.app.portrait.FaceRegion> = emptyList(),
        portraitConfig: com.craftcam.app.portrait.PortraitConfig = com.craftcam.app.portrait.PortraitConfig(),
        colorConfig: com.craftcam.app.color.ColorScienceConfig = com.craftcam.app.color.ColorScienceConfig(),
        cameraId: String = "0",
        captureFrameSnapshot: CaptureFrameSnapshot? = null,
        onStatsUpdated: ((com.craftcam.app.computational.ComputationalStats) -> Unit)? = null
    ): Uri? = withContext(Dispatchers.Default) {
        val refFrame = sequence.referenceFrame
        val rawW = refFrame.width
        val rawH = refFrame.height
        val rotation = refFrame.rotation

        // Compute oriented bounds
        val isRotated = (rotation == 90 || rotation == 270)
        val orientedW = if (isRotated) rawH else rawW
        val orientedH = if (isRotated) rawW else rawH

        // Calculate composition framing crop in oriented space
        val cropRect: Rect = if (captureFrameSnapshot != null) {
            CaptureFrameMapper.mapToImageCropRect(captureFrameSnapshot, orientedW, orientedH)
        } else {
            val srcRatio = orientedW.toFloat() / orientedH
            val targetRatio = if (orientedW > orientedH) aspectRatio.ratio else (1.0f / aspectRatio.ratio)
            val cW: Int
            val cH: Int
            if (srcRatio > targetRatio) {
                cH = orientedH
                cW = (orientedH * targetRatio).toInt().coerceIn(1, orientedW)
            } else {
                cW = orientedW
                cH = (orientedW / targetRatio).toInt().coerceIn(1, orientedH)
            }
            val sX = (orientedW - cW) / 2
            val sY = (orientedH - cH) / 2
            Rect(sX, sY, sX + cW, sY + cH)
        }

        val cropW = cropRect.width()
        val cropH = cropRect.height()

        val engine = com.craftcam.app.computational.ComputationalCaptureEngine(config)
        var developedBitmap: Bitmap? = null
        try {
            developedBitmap = engine.processSequence(
                sequence = sequence,
                targetWidth = cropW,
                targetHeight = cropH,
                profile = naturalProfile,
                mirrorFrontCamera = mirrorFrontCamera,
                compareWithOriginal = compareWithOriginal,
                captureLatencyMs = captureLatencyMs,
                processingProfile = processingProfile,
                faces = faces,
                portraitConfig = portraitConfig,
                colorConfig = colorConfig,
                cameraId = cameraId,
                onStatsUpdated = onStatsUpdated
            )

            // Save developed photo to gallery (DCIM/Cameleo)
            val uri = saveToGallery(context, developedBitmap, "CAMELEO Natural")
            uri
        } catch (t: Throwable) {
            Log.e(TAG, "Failed in processComputationalPhoto", t)
            null
        } finally {
            if (developedBitmap != null) {
                BitmapPool.release(developedBitmap)
            }
        }
    }

    // ─── Fast Asset & LUT Loading ───────────────────────────────────────────
    private fun getOrLoadLut(context: Context, lutFileName: String): IntArray? {
        synchronized(lutCache) {
            lutCache[lutFileName]?.let { return it }
            return try {
                context.assets.open(lutFileName).use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        val lut512 = if (bmp.width == 512 && bmp.height == 512) bmp
                        else Bitmap.createScaledBitmap(bmp, 512, 512, true)
                        val pixels = IntArray(512 * 512)
                        lut512.getPixels(pixels, 0, 512, 0, 0, 512, 512)
                        lutCache[lutFileName] = pixels
                        Log.d(TAG, "Cached 3D LUT: $lutFileName (512x512)")
                        pixels
                    } else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load LUT: $lutFileName", e)
                null
            }
        }
    }

    private fun loadTexture(context: Context, name: String): Bitmap? {
        synchronized(textureCache) {
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
                Log.w(TAG, "Texture not found: $name")
                textureCache[name] = null
                null
            }
        }
    }

    private fun getOrLoadGrainPixels(context: Context, name: String): Pair<IntArray, Pair<Int, Int>>? {
        synchronized(grainPixelsCache) {
            grainPixelsCache[name]?.let { return it }
            val resolvedName = if (name.startsWith("grains/")) name else {
                try {
                    context.assets.open(name).close()
                    name
                } catch (e: Exception) {
                    "grains/$name"
                }
            }
            grainPixelsCache[resolvedName]?.let {
                grainPixelsCache[name] = it
                return it
            }
            val bmp = loadTexture(context, resolvedName) ?: loadTexture(context, name) ?: return null
            val gw = bmp.width
            val gh = bmp.height
            val gp = IntArray(gw * gh)
            bmp.getPixels(gp, 0, gw, 0, 0, gw, gh)
            val result = Pair(gp, Pair(gw, gh))
            grainPixelsCache[name] = result
            grainPixelsCache[resolvedName] = result
            return result
        }
    }

    // ─── True Film Emulsion & Dazz Cam Processing Engine ─────────────────────
    internal suspend fun applyPresetEffects(
        context: Context,
        source: Bitmap,
        preset: CameraPreset,
        tuning: UserTuningConfig = UserTuningConfig(),
        lensProfile: LensProfile = LensProfile.NORMAL,
        isFlashActive: Boolean = false,
        applyGrain: Boolean = true
    ): Bitmap {
        val width = source.width
        val height = source.height
        val totalPixels = width * height
        val pixels = IntArrayPool.acquire(totalPixels)
        try {
            source.getPixels(pixels, 0, width, 0, 0, width, height)

            val grading = preset.colorGrading
            val lens = preset.lens
            val grain = preset.grain
            val halation = preset.halation
            val leak = preset.lightLeak
            val isCamcorder = preset.category == CameraCategory.CAMCORDER

            // 1. Identify camera-specific 3D LUT
            val lutFile = selectLutForPreset(preset.id)
            val lut = getOrLoadLut(context, lutFile)

            // Micro-tuning user offsets
            val tempShift = tuning.temperatureOffset.coerceIn(-1.0f, 1.0f)
            val tintShift = tuning.tintOffset.coerceIn(-1.0f, 1.0f)
            val satBoost  = (1.0f + tuning.saturationOffset).coerceIn(0.0f, 2.0f)
            val contBoost = (1.0f + tuning.contrastOffset).coerceIn(0.5f, 2.0f)

            // Filter intensity interpolation (0.0 = clean raw image, 1.0 = full analog photochemical grade)
            val intensity = tuning.filterIntensity.coerceIn(0.0f, 1.0f)

            // Combined preset color grading + user tuning scaled by filter intensity
            val rawContrast = (grading.contrast * contBoost).coerceAtLeast(0.1f)
            val effectiveContrast = 1.0f + (rawContrast - 1.0f) * intensity
            val rawSat = (grading.saturation * satBoost).coerceAtLeast(0.0f)
            val effectiveSat = 1.0f + (rawSat - 1.0f) * intensity
            val effectiveTemp = ((grading.temperature + tempShift) * intensity).coerceIn(-1.0f, 1.0f)
            val effectiveTint = ((grading.tint + tintShift) * intensity).coerceIn(-1.0f, 1.0f)
            val effectiveGamma = 1.0f + (grading.gamma - 1.0f) * intensity
            val effectiveFade = grading.fade * intensity
            val shadowR = grading.shadowTintR * intensity
            val shadowG = grading.shadowTintG * intensity
            val shadowB = grading.shadowTintB * intensity
            val rollOff = 1.0f - (1.0f - grading.highlightRollOff) * intensity

            // ── Step 1: Pre-calculate Vignette & Film Grain Parameters for Fused Processing ──
            val vigIntensity = (lens.vignetteIntensity * tuning.vignetteBoost * intensity).coerceIn(0.0f, 1.0f)
            val hasVig = vigIntensity > 0.03f
            val cx = width * 0.5f
            val cy = height * 0.5f
            val maxD2 = cx * cx + cy * cy
            val rad = lens.vignetteRadius
            val vigDenom = maxD2 * rad * rad

            val unifiedProfile = com.craftcam.app.camera.profile.CameraProfileCatalog.getUnifiedProfile(preset)
            val resolvedGrain = GrainStage.resolve(preset, unifiedProfile.grainProfile, tuning, intensity)
            val grainData = if (applyGrain && resolvedGrain.useScannedTexture && !resolvedGrain.assetPath.isNullOrEmpty()) {
                GrainStage.getOrLoadGrainPixels(context, resolvedGrain.assetPath)
            } else null
            val grainPixels: IntArray? = grainData?.first
            val gw = grainData?.second?.first ?: 1
            val gh = grainData?.second?.second ?: 1
            val grainScale = resolvedGrain.scale.coerceIn(0.5f, 3.0f)
            val grainStrength = resolvedGrain.effectiveIntensity * 1.35f
            val blendMode = resolvedGrain.blendMode
            val shadowStrength = resolvedGrain.shadowStrength
            val highlightStrength = resolvedGrain.highlightStrength
            val proceduralIntensity = resolvedGrain.proceduralIntensity
            val useHybridProcedural = proceduralIntensity > 0.01f

            // Anti-Tiling: Continuous coordinate transformation prevents grid repetition
            val antiTiling = if (grainPixels != null) {
                GrainStage.computeAntiTilingTables(width, height, gw, gh, grainScale)
            } else null
            val colBaseX = antiTiling?.colBaseX
            val colPhaseY = antiTiling?.colPhaseY
            val rowBaseY = antiTiling?.rowBaseY
            val rowPhaseX = antiTiling?.rowPhaseX
            val flipX = antiTiling?.flipX ?: false
            val flipY = antiTiling?.flipY ?: false

            // ── Step 1b: Optical Lens Softness (Radial spherical aberration) ────
            val effSoftness = preset.lens.softness * intensity
            if (effSoftness > 0.01f && tuning.debugStageOptics) {
                applyOpticalSoftness(pixels, width, height, effSoftness)
            }

            // ── Step 1c: Camera Lens Optical Transformations (Fisheye F/W, Prism) ────
            if (tuning.debugStageOptics) {
                if (lensProfile.type == LensType.FISHEYE_F || lensProfile.type == LensType.FISHEYE_W) {
                    applyFisheyeCurvilinear(pixels, width, height, lensProfile)
                } else if (lensProfile.type == LensType.PRISM && lensProfile.prismDispersion > 0.01f) {
                    applyPrismRefraction(pixels, width, height, lensProfile)
                }

                // ── Step 1d: Transverse Chromatic Aberration Parity ────
                val baseCA = preset.lens.chromaticAberration * intensity
                val effCA = when (lensProfile.type) {
                    LensType.FISHEYE_F -> maxOf(baseCA, 0.016f)
                    LensType.FISHEYE_W -> maxOf(baseCA, 0.008f)
                    LensType.PRISM -> maxOf(baseCA, 0.014f)
                    else -> baseCA
                }
                if (effCA > 0.001f) {
                    applyTransverseCA(pixels, width, height, effCA)
                }
            }

            // ── Step 2: High-Performance Fused Parallel Processing (LUT + Grading + Vignette + Grain) ──
            val secLutAsset = unifiedProfile.filmProfile.secondaryLutAssetPath
            val lutB = if (!secLutAsset.isNullOrEmpty() && tuning.debugStageLut) getOrLoadLut(context, secLutAsset) else null
            val dualLutMix = unifiedProfile.filmProfile.dualLutBlendFactor.coerceIn(0f, 1f)
            val hasDualLut = lutB != null && dualLutMix > 0.001f
            val isPreLin = tuning.preLinearizationEnabled && tuning.debugStagePreLinearization && 
                           unifiedProfile.filmProfile.lutInputSpace == com.craftcam.app.camera.profile.LutInputSpace.CUSTOM_PRETRANSFORM
            val preLinStrength = tuning.preLinearizationStrength.coerceIn(0f, 1f)
            val stageLut = tuning.debugStageLut
            val stageToneCurve = tuning.debugStageToneCurve
            val stageVignette = tuning.debugStageVignette
            val stageGrain = tuning.debugStageGrain && applyGrain
            val toeLift = unifiedProfile.filmProfile.toeLift * intensity
            val shoulderClip = 1.0f - (1.0f - unifiedProfile.filmProfile.shoulderClip) * intensity
            val hasToeOrShoulder = toeLift > 0.001f || shoulderClip < 0.999f

            val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
            val chunkSize = (totalPixels + numCores - 1) / numCores

            coroutineScope {
                (0 until numCores).map { coreIndex ->
                    async(Dispatchers.Default) {
                        val start = coreIndex * chunkSize
                        val end = minOf(totalPixels, start + chunkSize)

                        for (i in start until end) {
                        val px = pixels[i]
                        val ri = (px shr 16) and 0xFF
                        val gi = (px shr 8) and 0xFF
                        val bi = px and 0xFF

                        var r: Float
                        var g: Float
                        var b: Float

                        if (lut != null && stageLut) {
                            var inR = ri * (1.0f / 255.0f)
                            var inG = gi * (1.0f / 255.0f)
                            var inB = bi * (1.0f / 255.0f)

                            if (isPreLin) {
                                val linR = if (inR <= 0.5f) (inR * 2.0f).pow(1.25f) * 0.5f else 1.0f - ((1.0f - inR) * 2.0f).pow(1.25f) * 0.5f
                                val linG = if (inG <= 0.5f) (inG * 2.0f).pow(1.25f) * 0.5f else 1.0f - ((1.0f - inG) * 2.0f).pow(1.25f) * 0.5f
                                val linB = if (inB <= 0.5f) (inB * 2.0f).pow(1.25f) * 0.5f else 1.0f - ((1.0f - inB) * 2.0f).pow(1.25f) * 0.5f
                                inR = (inR * (1f - preLinStrength) + linR.coerceIn(0f, 1f) * preLinStrength).coerceIn(0f, 1f)
                                inG = (inG * (1f - preLinStrength) + linG.coerceIn(0f, 1f) * preLinStrength).coerceIn(0f, 1f)
                                inB = (inB * (1f - preLinStrength) + linB.coerceIn(0f, 1f) * preLinStrength).coerceIn(0f, 1f)
                            } else if (unifiedProfile.filmProfile.lutInputSpace == com.craftcam.app.camera.profile.LutInputSpace.LINEAR) {
                                inR = inR.pow(2.2f).coerceIn(0f, 1f)
                                inG = inG.pow(2.2f).coerceIn(0f, 1f)
                                inB = inB.pow(2.2f).coerceIn(0f, 1f)
                            }

                            val rf = inR * 63.0f
                            val gf = inG * 63.0f
                            val bf = inB * 63.0f

                            val r0 = rf.toInt().coerceIn(0, 62)
                            val r1 = r0 + 1
                            val rx = rf - r0

                            val g0 = gf.toInt().coerceIn(0, 62)
                            val g1 = g0 + 1
                            val gy = gf - g0

                            val b0 = bf.toInt().coerceIn(0, 62)
                            val b1 = b0 + 1
                            val bz = bf - b0

                            val tX0 = (b0 and 7) shl 6
                            val tY0 = (b0 shr 3) shl 6
                            val tX1 = (b1 and 7) shl 6
                            val tY1 = (b1 shr 3) shl 6

                            val row0_0 = (tY0 + g0) * 512 + tX0
                            val row0_1 = (tY0 + g1) * 512 + tX0
                            val row1_0 = (tY1 + g0) * 512 + tX1
                            val row1_1 = (tY1 + g1) * 512 + tX1

                            val c000 = lut[row0_0 + r0]
                            val c100 = lut[row0_0 + r1]
                            val c010 = lut[row0_1 + r0]
                            val c110 = lut[row0_1 + r1]
                            val c001 = lut[row1_0 + r0]
                            val c101 = lut[row1_0 + r1]
                            val c011 = lut[row1_1 + r0]
                            val c111 = lut[row1_1 + r1]

                            val r00 = ((c000 shr 16) and 0xFF) * (1f - rx) + ((c100 shr 16) and 0xFF) * rx
                            val r10 = ((c010 shr 16) and 0xFF) * (1f - rx) + ((c110 shr 16) and 0xFF) * rx
                            val r01 = ((c001 shr 16) and 0xFF) * (1f - rx) + ((c101 shr 16) and 0xFF) * rx
                            val r11 = ((c011 shr 16) and 0xFF) * (1f - rx) + ((c111 shr 16) and 0xFF) * rx
                            val r0_y = r00 * (1f - gy) + r10 * gy
                            val r1_y = r01 * (1f - gy) + r11 * gy
                            var sampledR = (r0_y * (1f - bz) + r1_y * bz) * (1.0f / 255.0f)

                            val g00 = ((c000 shr 8) and 0xFF) * (1f - rx) + ((c100 shr 8) and 0xFF) * rx
                            val g10 = ((c010 shr 8) and 0xFF) * (1f - rx) + ((c110 shr 8) and 0xFF) * rx
                            val g01 = ((c001 shr 8) and 0xFF) * (1f - rx) + ((c101 shr 8) and 0xFF) * rx
                            val g11 = ((c011 shr 8) and 0xFF) * (1f - rx) + ((c111 shr 8) and 0xFF) * rx
                            val g0_y = g00 * (1f - gy) + g10 * gy
                            val g1_y = g01 * (1f - gy) + g11 * gy
                            var sampledG = (g0_y * (1f - bz) + g1_y * bz) * (1.0f / 255.0f)

                            val b00 = (c000 and 0xFF) * (1f - rx) + (c100 and 0xFF) * rx
                            val b10 = (c010 and 0xFF) * (1f - rx) + (c110 and 0xFF) * rx
                            val b01 = (c001 and 0xFF) * (1f - rx) + (c101 and 0xFF) * rx
                            val b11 = (c011 and 0xFF) * (1f - rx) + (c111 and 0xFF) * rx
                            val b0_y = b00 * (1f - gy) + b10 * gy
                            val b1_y = b01 * (1f - gy) + b11 * gy
                            var sampledB = (b0_y * (1f - bz) + b1_y * bz) * (1.0f / 255.0f)

                            if (hasDualLut) {
                                val nonNullLutB = lutB!!
                                val b000 = nonNullLutB[row0_0 + r0]
                                val b100 = nonNullLutB[row0_0 + r1]
                                val b010 = nonNullLutB[row0_1 + r0]
                                val b110 = nonNullLutB[row0_1 + r1]
                                val b001 = nonNullLutB[row1_0 + r0]
                                val b101 = nonNullLutB[row1_0 + r1]
                                val b011 = nonNullLutB[row1_1 + r0]
                                val b111 = nonNullLutB[row1_1 + r1]

                                val br00 = ((b000 shr 16) and 0xFF) * (1f - rx) + ((b100 shr 16) and 0xFF) * rx
                                val br10 = ((b010 shr 16) and 0xFF) * (1f - rx) + ((b110 shr 16) and 0xFF) * rx
                                val br01 = ((b001 shr 16) and 0xFF) * (1f - rx) + ((b101 shr 16) and 0xFF) * rx
                                val br11 = ((b011 shr 16) and 0xFF) * (1f - rx) + ((b111 shr 16) and 0xFF) * rx
                                val br_out = ((br00 * (1f - gy) + br10 * gy) * (1f - bz) + (br01 * (1f - gy) + br11 * gy) * bz) * (1.0f / 255.0f)

                                val bg00 = ((b000 shr 8) and 0xFF) * (1f - rx) + ((b100 shr 8) and 0xFF) * rx
                                val bg10 = ((b010 shr 8) and 0xFF) * (1f - rx) + ((b110 shr 8) and 0xFF) * rx
                                val bg01 = ((b001 shr 8) and 0xFF) * (1f - rx) + ((b101 shr 8) and 0xFF) * rx
                                val bg11 = ((b011 shr 8) and 0xFF) * (1f - rx) + ((b111 shr 8) and 0xFF) * rx
                                val bg_out = ((bg00 * (1f - gy) + bg10 * gy) * (1f - bz) + (bg01 * (1f - gy) + bg11 * gy) * bz) * (1.0f / 255.0f)

                                val bb00 = (b000 and 0xFF) * (1f - rx) + (b100 and 0xFF) * rx
                                val bb10 = (b010 and 0xFF) * (1f - rx) + (b110 and 0xFF) * rx
                                val bb01 = (b001 and 0xFF) * (1f - rx) + (b101 and 0xFF) * rx
                                val bb11 = (b011 and 0xFF) * (1f - rx) + (b111 and 0xFF) * rx
                                val bb_out = ((bb00 * (1f - gy) + bb10 * gy) * (1f - bz) + (bb01 * (1f - gy) + bb11 * gy) * bz) * (1.0f / 255.0f)

                                sampledR = sampledR * (1f - dualLutMix) + br_out * dualLutMix
                                sampledG = sampledG * (1f - dualLutMix) + bg_out * dualLutMix
                                sampledB = sampledB * (1f - dualLutMix) + bb_out * dualLutMix
                            }

                            if (intensity < 0.999f) {
                                val origR = ri * (1.0f / 255.0f)
                                val origG = gi * (1.0f / 255.0f)
                                val origB = bi * (1.0f / 255.0f)
                                r = origR * (1.0f - intensity) + sampledR * intensity
                                g = origG * (1.0f - intensity) + sampledG * intensity
                                b = origB * (1.0f - intensity) + sampledB * intensity
                            } else {
                                r = sampledR
                                g = sampledG
                                b = sampledB
                            }
                        } else if (stageLut) {
                            r = applyFilmicCurve(ri / 255.0f, grading.contrast, grading.fade)
                            g = applyFilmicCurve(gi / 255.0f, grading.contrast, grading.fade)
                            b = applyFilmicCurve(bi / 255.0f, grading.contrast, grading.fade)
                        } else {
                            r = ri / 255.0f
                            g = gi / 255.0f
                            b = bi / 255.0f
                        }

                        // A. Preset & User Temperature / Tint (Warm/Cool Shift)
                        if (abs(effectiveTemp) > 0.001f) {
                            r += 0.10f * effectiveTemp
                            b -= 0.10f * effectiveTemp
                        }
                        if (abs(effectiveTint) > 0.001f) {
                            g += 0.06f * effectiveTint
                            r -= 0.03f * effectiveTint
                            b -= 0.03f * effectiveTint
                        }

                        if (stageToneCurve) {
                            // B. Preset & User Contrast
                            if (abs(effectiveContrast - 1.0f) > 0.01f) {
                                r = (r - 0.5f) * effectiveContrast + 0.5f
                                g = (g - 0.5f) * effectiveContrast + 0.5f
                                b = (b - 0.5f) * effectiveContrast + 0.5f
                            }

                            // C. Preset & User Saturation
                            var curLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                            if (abs(effectiveSat - 1.0f) > 0.01f) {
                                r = curLuma + effectiveSat * (r - curLuma)
                                g = curLuma + effectiveSat * (g - curLuma)
                                b = curLuma + effectiveSat * (b - curLuma)
                                curLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                            }

                            // D. Gamma Curve
                            if (abs(effectiveGamma - 1.0f) > 0.01f) {
                                val invGamma = 1.0f / effectiveGamma
                                r = r.coerceAtLeast(0.0f).pow(invGamma)
                                g = g.coerceAtLeast(0.0f).pow(invGamma)
                                b = b.coerceAtLeast(0.0f).pow(invGamma)
                                curLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                            }

                            // E. Shadow Tint
                            val shadowWeight = (1.0f - curLuma * 3.0f).coerceIn(0.0f, 1.0f)
                            r += shadowR * shadowWeight
                            g += shadowG * shadowWeight
                            b += shadowB * shadowWeight

                            // F. Filmic Highlight Rolloff & Desaturating Shoulder
                            if (rollOff < 0.99f) {
                                val knee = maxOf(0.01f, 1.0f - rollOff)
                                if (r > rollOff) r = rollOff + knee * (1.0f - exp(-(r - rollOff) / knee))
                                if (g > rollOff) g = rollOff + knee * (1.0f - exp(-(g - rollOff) / knee))
                                if (b > rollOff) b = rollOff + knee * (1.0f - exp(-(b - rollOff) / knee))
                                curLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b

                                val highOver = maxOf(0.0f, curLuma - rollOff) / knee
                                val desat = (highOver * 0.35f).coerceIn(0.0f, 0.65f)
                                r = r * (1.0f - desat) + curLuma * desat
                                g = g * (1.0f - desat) + curLuma * desat
                                b = b * (1.0f - desat) + curLuma * desat
                            }

                            // F2. Lifted Toe & Shoulder Compression (Lightroom Film Profile Parity)
                            if (hasToeOrShoulder) {
                                r = (toeLift + r.coerceIn(0f, 1f) * (shoulderClip - toeLift)).coerceIn(0f, 1f)
                                g = (toeLift + g.coerceIn(0f, 1f) * (shoulderClip - toeLift)).coerceIn(0f, 1f)
                                b = (toeLift + b.coerceIn(0f, 1f) * (shoulderClip - toeLift)).coerceIn(0f, 1f)
                            }

                            // G. Film Fade (Lifted blacks)
                            if (effectiveFade > 0.001f) {
                                r = r * (1.0f - effectiveFade) + effectiveFade
                                g = g * (1.0f - effectiveFade) + effectiveFade
                                b = b * (1.0f - effectiveFade) + effectiveFade
                            }
                        }

                        // Flattering Skin Tone Radiance (Dazz Cam HighPassSkinSmoothing parity)
                        if (r > g && g > b && r > 0.22f && r < 0.90f) {
                            val skinFactor = ((r - b) * 2.2f).coerceIn(0.0f, 1.0f)
                            val midLuma = (0.2126f * r + 0.7152f * g + 0.0722f * b)
                            val peachWeight = (1.0f - abs(midLuma - 0.55f) * 2.2f).coerceIn(0.0f, 1.0f) * skinFactor
                            r = (r + 0.025f * peachWeight).coerceIn(0f, 1f)
                            g = (g + 0.010f * peachWeight).coerceIn(0f, 1f)
                        }

                        val pxX = i % width
                        val pxY = i / width

                        // Camcorder CRT scanline modulation
                        if (isCamcorder) {
                            val scanline = (sin(pxY.toFloat() / height * 600.0 * PI) * 0.5 + 0.5).toFloat()
                            val sf = 0.90f + 0.10f * scanline
                            r *= sf; g *= sf; b *= sf
                        }

                        // Fused Optical Cosine Fourth Lens Vignette
                        if (hasVig && stageVignette) {
                            val dx = pxX - cx
                            val dy = pxY - cy
                            val rn2 = (dx * dx + dy * dy) / vigDenom
                            val cos4Vig = 1.0f / ((1.0f + rn2 * 0.55f) * (1.0f + rn2 * 0.55f))
                            val v = (1.0f - vigIntensity + vigIntensity * cos4Vig).coerceIn(0.25f, 1.0f)
                            r *= v; g *= v; b *= v
                        }

                        // Camera Lens Optical Modulations (ND filter & Color Flash)
                        if (lensProfile.type == LensType.ND && lensProfile.ndTransmissionFactor < 0.99f) {
                            r *= lensProfile.ndTransmissionFactor
                            g *= lensProfile.ndTransmissionFactor
                            b *= lensProfile.ndTransmissionFactor
                        }
                        if (lensProfile.type == LensType.COLOR_FLASH && isFlashActive) {
                            val (tr, tg, tb) = lensProfile.colorFlashTint.rgb
                            r = (r * tr * 1.30f).coerceIn(0f, 1f)
                            g = (g * tg * 1.30f).coerceIn(0f, 1f)
                            b = (b * tb * 1.30f).coerceIn(0f, 1f)
                        }

                        // Fused Multi-Tone Film Halide Grain with Anti-Tiling, Dye Cloud Separation & S-Curve Weighting
                        if (stageGrain && grainPixels != null && colBaseX != null && rowBaseY != null && rowPhaseX != null && colPhaseY != null) {
                            var gx = (colBaseX[pxX] + rowPhaseX[pxY]) % gw
                            if (gx < 0) gx += gw
                            if (flipX) gx = gw - 1 - gx

                            var gy = (rowBaseY[pxY] + colPhaseY[pxX]) % gh
                            if (gy < 0) gy += gh
                            if (flipY) gy = gh - 1 - gy

                            val gp = grainPixels[gy * gw + gx]
                            val rG = gp shr 16 and 0xFF
                            val gG = gp shr 8 and 0xFF
                            val bG = gp and 0xFF
                            val rawLuma = rG * 0.299f + gG * 0.587f + bG * 0.114f
                            // Normalize and amplify scanned halide density variation from low-contrast 8-bit scan
                            val gl = (((rawLuma - 128f) * (1.0f / 128f)) * 4.5f).coerceIn(-1.0f, 1.0f)
                            val postLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                            
                            // Luminance-Aware Grain Mask (Smoothstep response in shadows and highlights)
                            val shadowT = (postLuma / 0.35f).coerceIn(0.0f, 1.0f)
                            val shadowMask = shadowT * shadowT * (3.0f - 2.0f * shadowT)
                            val highlightT = ((postLuma - 0.65f) / 0.35f).coerceIn(0.0f, 1.0f)
                            val highlightMask = 1.0f - (highlightT * highlightT * (3.0f - 2.0f * highlightT))
                            var grainWeight = (shadowStrength + (1.0f - shadowStrength) * shadowMask) * (highlightStrength + (1.0f - highlightStrength) * highlightMask)

                            // Parabolic midtone weighting: M(L) = 4L(1-L)
                            if (resolvedGrain.luminanceWeightingEnabled && resolvedGrain.luminanceWeightingPower > 0.01f) {
                                val midParabola = (4.0f * postLuma.coerceIn(0f, 1f) * (1.0f - postLuma.coerceIn(0f, 1f))).coerceIn(0f, 1f)
                                val lumaMod = (1.0f - resolvedGrain.luminanceWeightingPower) + midParabola * resolvedGrain.luminanceWeightingPower
                                grainWeight *= lumaMod
                            }

                            // Flattering skin protection: gently attenuate grain on smooth skin
                            if (r > g && g > b && r > 0.25f && r < 0.90f) {
                                val skinWeight = ((r - b) * 2.0f).coerceIn(0.0f, 1.0f)
                                grainWeight *= (1.0f - 0.30f * skinWeight)
                            }

                            // Blend Mode Selection (OVERLAY, SCREEN, MULTIPLY, NORMAL)
                            when (blendMode) {
                                GrainBlendMode.SCREEN -> {
                                    // SCREEN mode for push-processed / low-light film grain emulsion lift
                                    val grainNorm = ((gl + 1.0f) * 0.5f).coerceIn(0.0f, 1.0f)
                                    val screenFactor = grainStrength * grainWeight * 0.85f
                                    if (preset.grain.isColorNoise) {
                                        val rNorm = ((((rG - 128f) * (1.0f / 128f) * 3.5f + 1.0f) * 0.5f)).coerceIn(0f, 1f)
                                        val bNorm = ((((bG - 128f) * (1.0f / 128f) * 3.5f + 1.0f) * 0.5f)).coerceIn(0f, 1f)
                                        r = 1.0f - (1.0f - r) * (1.0f - rNorm * screenFactor)
                                        g = 1.0f - (1.0f - g) * (1.0f - grainNorm * screenFactor)
                                        b = 1.0f - (1.0f - b) * (1.0f - bNorm * screenFactor)
                                    } else {
                                        r = 1.0f - (1.0f - r) * (1.0f - grainNorm * screenFactor)
                                        g = 1.0f - (1.0f - g) * (1.0f - grainNorm * screenFactor)
                                        b = 1.0f - (1.0f - b) * (1.0f - grainNorm * screenFactor)
                                    }
                                }
                                GrainBlendMode.MULTIPLY -> {
                                    // MULTIPLY mode for negative exposure printing
                                    val grainNorm = ((gl + 1.0f) * 0.5f).coerceIn(0.0f, 1.0f)
                                    val multFactor = grainStrength * grainWeight * 0.85f
                                    val grainMult = 1.0f - (1.0f - grainNorm) * multFactor
                                    r *= grainMult
                                    g *= grainMult
                                    b *= grainMult
                                }
                                GrainBlendMode.NORMAL -> {
                                    // NORMAL mode for gentle alpha overlay
                                    val grainNorm = ((gl + 1.0f) * 0.5f).coerceIn(0.0f, 1.0f)
                                    val normFactor = grainStrength * grainWeight * 0.45f
                                    r = r * (1.0f - normFactor) + grainNorm * normFactor
                                    g = g * (1.0f - normFactor) + grainNorm * normFactor
                                    b = b * (1.0f - normFactor) + grainNorm * normFactor
                                }
                                GrainBlendMode.OVERLAY -> {
                                    // OVERLAY mode for standard photographic chemical halide modulation
                                    val deltaG = gl * grainStrength * grainWeight
                                    val deltaR = gl * (grainStrength * 1.08f) * grainWeight
                                    val deltaB = gl * (grainStrength * 0.92f) * grainWeight

                                    if (preset.grain.isColorNoise) {
                                        val rChroma = (((rG - 128f) * (1.0f / 128f)) * 3.5f).coerceIn(-1.0f, 1.0f) * grainStrength * grainWeight * 0.40f
                                        val bChroma = (((bG - 128f) * (1.0f / 128f)) * 3.5f).coerceIn(-1.0f, 1.0f) * grainStrength * grainWeight * 0.40f
                                        r += deltaR + rChroma
                                        g += deltaG
                                        b += deltaB + bChroma
                                    } else {
                                        val dyeCloud = gl * 0.12f * grainStrength * grainWeight
                                        r += deltaR + dyeCloud
                                        g += deltaG
                                        b += deltaB - dyeCloud
                                    }
                                }
                            }

                            // Optional subtle hybrid procedural micro-halide (Section 14)
                            if (useHybridProcedural) {
                                val hCoord = (pxX * 12.9898f + pxY * 78.233f) % 3.14159f
                                val pNoise = (kotlin.math.sin(hCoord) * 43758.5453f).let { it - kotlin.math.floor(it) - 0.5f }
                                val pDelta = pNoise * grainStrength * grainWeight * (proceduralIntensity.coerceIn(0.0f, 1.0f))
                                r += pDelta
                                g += pDelta
                                b += pDelta
                            }
                        } else if (stageGrain && proceduralIntensity > 0.01f) {
                            val hCoord = (pxX * 12.9898f + pxY * 78.233f) % 3.14159f
                            val pNoise = (kotlin.math.sin(hCoord) * 43758.5453f).let { it - kotlin.math.floor(it) - 0.5f }
                            val postLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                            val shadowT = (postLuma / 0.35f).coerceIn(0.0f, 1.0f)
                            val shadowMask = shadowT * shadowT * (3.0f - 2.0f * shadowT)
                            val highlightT = ((postLuma - 0.65f) / 0.35f).coerceIn(0.0f, 1.0f)
                            val highlightMask = 1.0f - (highlightT * highlightT * (3.0f - 2.0f * highlightT))
                            var grainWeight = (shadowStrength + (1.0f - shadowStrength) * shadowMask) * (highlightStrength + (1.0f - highlightStrength) * highlightMask)
                            if (resolvedGrain.luminanceWeightingEnabled && resolvedGrain.luminanceWeightingPower > 0.01f) {
                                val midParabola = (4.0f * postLuma.coerceIn(0f, 1f) * (1.0f - postLuma.coerceIn(0f, 1f))).coerceIn(0f, 1f)
                                val lumaMod = (1.0f - resolvedGrain.luminanceWeightingPower) + midParabola * resolvedGrain.luminanceWeightingPower
                                grainWeight *= lumaMod
                            }
                            if (r > g && g > b && r > 0.25f && r < 0.90f) {
                                val skinWeight = ((r - b) * 2.0f).coerceIn(0.0f, 1.0f)
                                grainWeight *= (1.0f - 0.30f * skinWeight)
                            }
                            val pDelta = pNoise * proceduralIntensity * grainWeight * 0.45f
                            r += pDelta
                            g += pDelta
                            b += pDelta
                        }

                        val ir2 = (r.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
                        val ig2 = (g.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
                        val ib2 = (b.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
                        pixels[i] = (0xFF shl 24) or (ir2 shl 16) or (ig2 shl 8) or ib2
                    }
                }
            }.awaitAll()
        }

            // ── Step 2b: Optical Starburst Diffraction Rays ────
            if (lensProfile.type == LensType.STARBURST && lensProfile.starburstStrength > 0.01f) {
                applyStarburstDiffraction(pixels, width, height, lensProfile)
            }

            // In-place update of source bitmap (eliminates 48.8MB result Bitmap allocation)
            source.setPixels(pixels, 0, width, 0, 0, width, height)

            // ── Step 4: Canvas Layer Overlays ────────────────────────────────────
            val canvas = Canvas(source)

            // 4a. Authentic Optical Halation (Photochemical Highlight Bloom)
            val halationActive = tuning.debugStageHalation && tuning.halationEnabled && 
                                 (halation?.enabled == true || unifiedProfile.filmProfile.halationThreshold < 0.99f) && 
                                 intensity > 0.01f
            if (halationActive) {
                val halationThresh = halation?.threshold ?: unifiedProfile.filmProfile.halationThreshold
                val halationInt = (halation?.intensity ?: unifiedProfile.filmProfile.halationIntensity) * intensity
                applyAuthenticHalation(
                    pixels, width, height, canvas, halationThresh, halationInt,
                    unifiedProfile.filmProfile.halationColorTintR,
                    unifiedProfile.filmProfile.halationColorTintG,
                    unifiedProfile.filmProfile.halationColorTintB
                )
            }

            // 4b. Real Scanned Dust & Scratches (bypassed for Fisheye)
            val isFisheye = preset.id == "analog_fisheye_f" || lensProfile.type == LensType.FISHEYE_F || lensProfile.type == LensType.FISHEYE_W
            val dustConfig = preset.dust
            val dustActive = !isFisheye && tuning.dustEnabled && (dustConfig?.enabled ?: true)
            if (dustActive) {
                BorderRenderer.renderDustOnCanvas(
                    canvas = canvas,
                    dustConfig = dustConfig,
                    dstLeft = 0f,
                    dstTop = 0f,
                    dstRight = width.toFloat(),
                    dstBottom = height.toFloat(),
                    context = context,
                    opacityMultiplier = 1.0f
                )
            }

            // 4c. Real Photographic Light Leaks
            val leakEnabled = tuning.lightLeakEnabled && leak?.enabled == true && leak.intensity > 0.01f && intensity > 0.01f
            if (leakEnabled) {
                val leakAsset = selectLightLeakForPreset(preset.id)
                loadTexture(context, leakAsset)?.let { tex ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                        alpha = ((leak.intensity * 0.85f * intensity) * 255.0f).toInt().coerceIn(0, 255)
                    }
                    BorderRenderer.drawAssetCover(canvas, tex, 0f, 0f, width.toFloat(), height.toFloat(), paint)
                }
            }

            // 4d. Direct Flash Illumination Overlay
            val flashOverlay = preset.flashOverlay
            val flashActive = flashOverlay?.enabled == true || selectFlashForPreset(preset.id) != null
            val flashAsset = flashOverlay?.assetPath ?: selectFlashForPreset(preset.id)
            if (flashActive && flashAsset != null) {
                loadTexture(context, flashAsset)?.let { tex ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                        val flashOpacity = flashOverlay?.opacity ?: 0.65f
                        alpha = (flashOpacity * 255f).toInt().coerceIn(100, 255)
                    }
                    BorderRenderer.drawAssetCover(canvas, tex, 0f, 0f, width.toFloat(), height.toFloat(), paint)
                }
            }

            return source
        } finally {
            IntArrayPool.release(pixels)
        }
    }

    // ─── Transverse Chromatic Aberration Parity ─────────────────────────────
    private suspend fun applyTransverseCA(
        pixels: IntArray,
        width: Int,
        height: Int,
        strength: Float
    ) = coroutineScope {
        val totalPixels = width * height
        val srcCopy = IntArrayPool.acquire(totalPixels)
        try {
            System.arraycopy(pixels, 0, srcCopy, 0, totalPixels)
            val cx = width * 0.5f
            val cy = height * 0.5f
            val maxD2 = (cx * cx + cy * cy).coerceAtLeast(1.0f)

            val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
            val rowsPerCore = (height + numCores - 1) / numCores

            (0 until numCores).map { coreIdx ->
                async(Dispatchers.Default) {
                    val startY = coreIdx * rowsPerCore
                    val endY = minOf(height, startY + rowsPerCore)

                    for (y in startY until endY) {
                        val dy = y - cy
                        val dy2 = dy * dy
                        val rowOffset = y * width
                        for (x in 0 until width) {
                            val dx = x - cx
                            val r2 = (dx * dx + dy2) / maxD2
                            val caOffset = r2 * strength * 14.0f

                            val rX = (x + dx * caOffset * 0.05f).toInt().coerceIn(0, width - 1)
                            val rY = (y + dy * caOffset * 0.05f).toInt().coerceIn(0, height - 1)

                            val bX = (x - dx * caOffset * 0.07f).toInt().coerceIn(0, width - 1)
                            val bY = (y - dy * caOffset * 0.07f).toInt().coerceIn(0, height - 1)

                            val pOrig = srcCopy[rowOffset + x]
                            val pR = srcCopy[rY * width + rX]
                            val pB = srcCopy[bY * width + bX]

                            val r = (pR shr 16) and 0xFF
                            val g = (pOrig shr 8) and 0xFF
                            val b = pB and 0xFF
                            val a = (pOrig ushr 24) and 0xFF

                            pixels[rowOffset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                }
            }.awaitAll()
        } finally {
            IntArrayPool.release(srcCopy)
        }
    }

    // ─── Authentic Optical Film Halation Bloom ───────────────────────────────
    private fun applyAuthenticHalation(
        pixels: IntArray,
        width: Int,
        height: Int,
        canvas: Canvas,
        threshold: Float,
        intensity: Float,
        tintR: Float = 1.0f,
        tintG: Float = 0.32f,
        tintB: Float = 0.10f
    ) {
        val bw = maxOf(1, width / 4)
        val bh = maxOf(1, height / 4)
        val totalBloom = bw * bh
        val bloomPixels = IntArrayPool.acquire(totalBloom)
        val temp = IntArrayPool.acquire(totalBloom)
        val bloomBitmap = BitmapPool.acquire(bw, bh)

        try {
            val thresh255 = (threshold * 255f).toInt()
            val range = (255 - thresh255).coerceAtLeast(1)

            for (by in 0 until bh) {
                val sy = (by * height) / bh
                val sRow = sy * width
                val bRow = by * bw
                for (bx in 0 until bw) {
                    val sx = (bx * width) / bw
                    val px = pixels[sRow + sx]
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    val luma = (54 * r + 183 * g + 19 * b) shr 8
                    if (luma > thresh255) {
                        val excess = (luma - thresh255).toFloat() / range
                        val a = (excess * intensity * 255f).toInt().coerceIn(0, 255)
                        val hr = (255f * tintR).toInt().coerceIn(0, 255)
                        val hg = ((tintG * 255f) * excess).toInt().coerceIn(0, 255)
                        val hb = ((tintB * 255f) * excess).toInt().coerceIn(0, 255)
                        bloomPixels[bRow + bx] = (a shl 24) or (hr shl 16) or (hg shl 8) or hb
                    } else {
                        bloomPixels[bRow + bx] = 0
                    }
                }
            }

            fastBoxBlur(bloomPixels, bw, bh, 8, temp)

            bloomBitmap.setPixels(bloomPixels, 0, bw, 0, 0, bw, bh)

            val srcRect = Rect(0, 0, bw, bh)
            val dstRect = Rect(0, 0, width, height)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                alpha = 255
            }
            canvas.drawBitmap(bloomBitmap, srcRect, dstRect, paint)
        } finally {
            BitmapPool.release(bloomBitmap)
            IntArrayPool.release(bloomPixels)
            IntArrayPool.release(temp)
        }
    }

    private fun fastBoxBlur(pixels: IntArray, w: Int, h: Int, r: Int, temp: IntArray) {
        val div = 2 * r + 1

        for (y in 0 until h) {
            val row = y * w
            var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val px = pixels[row + i.coerceIn(0, w - 1)]
                aSum += (px ushr 24)
                rSum += (px shr 16 and 0xFF)
                gSum += (px shr 8 and 0xFF)
                bSum += (px and 0xFF)
            }
            for (x in 0 until w) {
                temp[row + x] = ((aSum / div) shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val leftPx = pixels[row + (x - r).coerceIn(0, w - 1)]
                val rightPx = pixels[row + (x + r + 1).coerceIn(0, w - 1)]
                aSum += (rightPx ushr 24) - (leftPx ushr 24)
                rSum += (rightPx shr 16 and 0xFF) - (leftPx shr 16 and 0xFF)
                gSum += (rightPx shr 8 and 0xFF) - (leftPx shr 8 and 0xFF)
                bSum += (rightPx and 0xFF) - (leftPx and 0xFF)
            }
        }

        for (x in 0 until w) {
            var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val px = temp[i.coerceIn(0, h - 1) * w + x]
                aSum += (px ushr 24)
                rSum += (px shr 16 and 0xFF)
                gSum += (px shr 8 and 0xFF)
                bSum += (px and 0xFF)
            }
            for (y in 0 until h) {
                val idx = y * w + x
                pixels[idx] = ((aSum / div) shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val topPx = temp[(y - r).coerceIn(0, h - 1) * w + x]
                val btmPx = temp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                aSum += (btmPx ushr 24) - (topPx ushr 24)
                rSum += (btmPx shr 16 and 0xFF) - (topPx shr 16 and 0xFF)
                gSum += (btmPx shr 8 and 0xFF) - (topPx shr 8 and 0xFF)
                bSum += (btmPx and 0xFF) - (topPx and 0xFF)
            }
        }
    }

    // ─── Optical Lens Softness (Radial spherical aberration) ────────────────
    private fun applyOpticalSoftness(pixels: IntArray, width: Int, height: Int, softness: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val maxDistSq = (cx * cx + cy * cy).coerceAtLeast(1f)
        val totalPixels = width * height
        val copy = IntArrayPool.acquire(totalPixels)
        try {
            System.arraycopy(pixels, 0, copy, 0, totalPixels)
            val step = maxOf(1, minOf(width, height) / 900)

            for (y in step until height - step) {
                val row = y * width
                val dy = y - cy
                val dySq = dy * dy
                for (x in step until width - step) {
                    val dx = x - cx
                    val r2 = (dx * dx + dySq) / maxDistSq
                    val rSoftness = (r2 * 2.5f * softness).coerceIn(0f, 0.85f)
                    if (rSoftness > 0.02f) {
                        val pL = copy[row + (x - step)]
                        val pR = copy[row + (x + step)]
                        val pT = copy[(y - step) * width + x]
                        val pB = copy[(y + step) * width + x]

                        val rAvg = (((pL shr 16 and 0xFF) + (pR shr 16 and 0xFF) + (pT shr 16 and 0xFF) + (pB shr 16 and 0xFF)) shr 2)
                        val gAvg = (((pL shr 8 and 0xFF) + (pR shr 8 and 0xFF) + (pT shr 8 and 0xFF) + (pB shr 8 and 0xFF)) shr 2)
                        val bAvg = (((pL and 0xFF) + (pR and 0xFF) + (pT and 0xFF) + (pB and 0xFF)) shr 2)

                        val orig = copy[row + x]
                        val rOrig = (orig shr 16) and 0xFF
                        val gOrig = (orig shr 8) and 0xFF
                        val bOrig = orig and 0xFF

                        val blend = rSoftness * 0.50f
                        val rNew = (rOrig * (1f - blend) + rAvg * blend).toInt().coerceIn(0, 255)
                        val gNew = (gOrig * (1f - blend) + gAvg * blend).toInt().coerceIn(0, 255)
                        val bNew = (bOrig * (1f - blend) + bAvg * blend).toInt().coerceIn(0, 255)

                        pixels[row + x] = (0xFF shl 24) or (rNew shl 16) or (gNew shl 8) or bNew
                    }
                }
            }
        } finally {
            IntArrayPool.release(copy)
        }
    }

    // ─── Camera Lens Optical Transformations ─────────────────────────────────
    private suspend fun applyFisheyeCurvilinear(
        pixels: IntArray,
        width: Int,
        height: Int,
        profile: LensProfile
    ) = coroutineScope {
        val totalPixels = width * height
        val srcCopy = IntArrayPool.acquire(totalPixels)
        try {
            System.arraycopy(pixels, 0, srcCopy, 0, totalPixels)

            val aspect = width.toFloat() / maxOf(1, height).toFloat()
            val isPortrait = aspect <= 1.0f
            val isCircularFisheye = profile.type == LensType.FISHEYE_F

            val rBubble = 0.465f
            val rCorner = if (isPortrait) {
                kotlin.math.sqrt(0.25f + 0.25f / (aspect * aspect))
            } else {
                kotlin.math.sqrt(0.25f * aspect * aspect + 0.25f)
            }

            val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
            val rowsPerCore = (height + numCores - 1) / numCores

            (0 until numCores).map { coreIdx ->
                async(Dispatchers.Default) {
                    val startY = coreIdx * rowsPerCore
                    val endY = minOf(height, startY + rowsPerCore)

                    for (y in startY until endY) {
                        val py = (y.toFloat() / height) - 0.5f
                        val pPhysY = if (isPortrait) py / aspect else py
                        val rowOffset = y * width

                        for (x in 0 until width) {
                            val px = (x.toFloat() / width) - 0.5f
                            val pPhysX = if (isPortrait) px else px * aspect
                            val rPhys = kotlin.math.sqrt(pPhysX * pPhysX + pPhysY * pPhysY)

                            if (isCircularFisheye) {
                                if (rPhys > rBubble) {
                                    pixels[rowOffset + x] = 0xFF000000.toInt()
                                    continue
                                }
                                val rn = rPhys / rBubble
                                val rDistNorm = rn * (0.36f + 0.64f * rn * rn)
                                val rDistPhys = rDistNorm * 0.465f

                                val invR = if (rPhys > 0.0001f) 1.0f / rPhys else 0.0f
                                val pDistPhysX = pPhysX * invR * rDistPhys
                                val pDistPhysY = pPhysY * invR * rDistPhys

                                val u = 0.5f + (if (isPortrait) pDistPhysX else pDistPhysX / aspect)
                                val v = 0.5f + (if (isPortrait) pDistPhysY * aspect else pDistPhysY)

                                val sx = (u * (width - 1)).toInt().coerceIn(0, width - 1)
                                val sy = (v * (height - 1)).toInt().coerceIn(0, height - 1)
                                var color = srcCopy[sy * width + sx]

                                // Soft optical aperture rim at circular bubble boundary
                                if (rPhys > rBubble - 0.015f) {
                                    val rimT = ((rBubble - rPhys) / 0.015f).coerceIn(0f, 1f)
                                    val a = (color ushr 24) and 0xFF
                                    val cr = (((color ushr 16) and 0xFF) * rimT).toInt()
                                    val cg = (((color ushr 8) and 0xFF) * rimT).toInt()
                                    val cb = ((color and 0xFF) * rimT).toInt()
                                    color = (a shl 24) or (cr shl 16) or (cg shl 8) or cb
                                }
                                pixels[rowOffset + x] = color
                            } else {
                                // FISHEYE_W (Full Frame)
                                val rn = (rPhys / rCorner).coerceIn(0f, 1f)
                                val rDistNorm = rn * (0.55f + 0.45f * rn * rn)
                                val rDistPhys = rDistNorm * rCorner

                                val invR = if (rPhys > 0.0001f) 1.0f / rPhys else 0.0f
                                val pDistPhysX = pPhysX * invR * rDistPhys
                                val pDistPhysY = pPhysY * invR * rDistPhys

                                val u = (0.5f + (if (isPortrait) pDistPhysX else pDistPhysX / aspect)).coerceIn(0.001f, 0.999f)
                                val v = (0.5f + (if (isPortrait) pDistPhysY * aspect else pDistPhysY)).coerceIn(0.001f, 0.999f)

                                val sx = (u * (width - 1)).toInt().coerceIn(0, width - 1)
                                val sy = (v * (height - 1)).toInt().coerceIn(0, height - 1)
                                var color = srcCopy[sy * width + sx]

                                if (rn > 0.65f) {
                                    val vig = ((1.05f - rn) / 0.40f).coerceIn(0f, 1f)
                                    val vigFactor = 1.0f - (1.0f - vig) * 0.30f
                                    val a = (color ushr 24) and 0xFF
                                    val cr = (((color ushr 16) and 0xFF) * vigFactor).toInt()
                                    val cg = (((color ushr 8) and 0xFF) * vigFactor).toInt()
                                    val cb = ((color and 0xFF) * vigFactor).toInt()
                                    color = (a shl 24) or (cr shl 16) or (cg shl 8) or cb
                                }
                                pixels[rowOffset + x] = color
                            }
                        }
                    }
                }
            }.awaitAll()
        } finally {
            IntArrayPool.release(srcCopy)
        }
    }

    private suspend fun applyPrismRefraction(
        pixels: IntArray,
        width: Int,
        height: Int,
        profile: LensProfile
    ) = coroutineScope {
        val totalPixels = width * height
        val srcCopy = IntArrayPool.acquire(totalPixels)
        try {
            System.arraycopy(pixels, 0, srcCopy, 0, totalPixels)

            val disp = (width * 0.035f * profile.prismDispersion).toInt()
            val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
            val rowsPerCore = (height + numCores - 1) / numCores

            (0 until numCores).map { coreIdx ->
                async(Dispatchers.Default) {
                    val startY = coreIdx * rowsPerCore
                    val endY = minOf(height, startY + rowsPerCore)

                    for (y in startY until endY) {
                        val py = (y.toFloat() / height) - 0.5f
                        val rowOffset = y * width
                        for (x in 0 until width) {
                            val px = (x.toFloat() / width) - 0.5f
                            val r = kotlin.math.sqrt(px * px + py * py)
                            val facetWeight = (r * 1.35f * profile.prismDispersion).coerceIn(0f, 0.45f)

                            if (facetWeight > 0.02f) {
                                val off1X = (disp * 0.866f).toInt()
                                val off1Y = (disp * 0.50f).toInt()
                                val s1x = (x + off1X).coerceIn(0, width - 1)
                                val s1y = (y + off1Y).coerceIn(0, height - 1)
                                val s2x = (x - off1X).coerceIn(0, width - 1)
                                val s2y = (y + off1Y).coerceIn(0, height - 1)

                                val p1 = srcCopy[s1y * width + s1x]
                                val p2 = srcCopy[s2y * width + s2x]
                                val p0 = pixels[rowOffset + x]

                                val r0 = (p0 ushr 16) and 0xFF
                                val g0 = (p0 ushr 8) and 0xFF
                                val b0 = p0 and 0xFF

                                val rGhost = (((p1 ushr 16) and 0xFF) * 1.08f + ((p2 ushr 16) and 0xFF) * 0.92f) * 0.5f
                                val gGhost = (((p1 ushr 8) and 0xFF) + ((p2 ushr 8) and 0xFF)) * 0.5f
                                val bGhost = (((p1 and 0xFF) * 0.92f + (p2 and 0xFF) * 1.12f)) * 0.5f

                                val rf = (r0 * (1f - facetWeight) + rGhost * facetWeight).toInt().coerceIn(0, 255)
                                val gf = (g0 * (1f - facetWeight) + gGhost * facetWeight).toInt().coerceIn(0, 255)
                                val bf = (b0 * (1f - facetWeight) + bGhost * facetWeight).toInt().coerceIn(0, 255)

                                pixels[rowOffset + x] = (0xFF shl 24) or (rf shl 16) or (gf shl 8) or bf
                            }
                        }
                    }
                }
            }.awaitAll()
        } finally {
            IntArrayPool.release(srcCopy)
        }
    }

    private suspend fun applyStarburstDiffraction(
        pixels: IntArray,
        width: Int,
        height: Int,
        profile: LensProfile
    ) = coroutineScope {
        val totalPixels = width * height
        val srcCopy = IntArrayPool.acquire(totalPixels)
        try {
            System.arraycopy(pixels, 0, srcCopy, 0, totalPixels)

            val threshold = (profile.starburstThreshold * 255f).toInt()
            val strength = profile.starburstStrength * 0.70f
            val maxRayLen = (minOf(width, height) * 0.04f).toInt().coerceAtLeast(8)

            val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
            val rowsPerCore = (height + numCores - 1) / numCores

            (0 until numCores).map { coreIdx ->
                async(Dispatchers.Default) {
                    val startY = coreIdx * rowsPerCore
                    val endY = minOf(height, startY + rowsPerCore)

                    for (y in startY until endY) {
                        for (x in 0 until width) {
                            val px = srcCopy[y * width + x]
                            val r = (px ushr 16) and 0xFF
                            val g = (px ushr 8) and 0xFF
                            val b = px and 0xFF
                            val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt()

                            if (luma > threshold) {
                                val over = (luma - threshold) * strength
                                for (step in 1..maxRayLen) {
                                    val falloff = (1.0f - step.toFloat() / maxRayLen) * (over / 255f) * 0.25f
                                    if (falloff <= 0.005f) break

                                    val dirs = arrayOf(
                                        Pair(x + step, y + step),
                                        Pair(x - step, y - step),
                                        Pair(x + step, y - step),
                                        Pair(x - step, y + step)
                                    )

                                    for ((dx, dy) in dirs) {
                                        if (dx in 0 until width && dy in 0 until height) {
                                            val idx = dy * width + dx
                                            val base = pixels[idx]
                                            val br = minOf(255, ((base ushr 16) and 0xFF) + (r * falloff).toInt())
                                            val bg = minOf(255, ((base ushr 8) and 0xFF) + (g * falloff).toInt())
                                            val bb = minOf(255, (base and 0xFF) + (b * falloff).toInt())
                                            pixels[idx] = (0xFF shl 24) or (br shl 16) or (bg shl 8) or bb
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        } finally {
            IntArrayPool.release(srcCopy)
        }
    }

    // ─── Optical Vignette Shading ────────────────────────────────────────────
    private fun applyTextureVignette(
        pixels: IntArray,
        width: Int,
        height: Int,
        vigTex: Bitmap,
        intensity: Float
    ) {
        val vw = vigTex.width
        val vh = vigTex.height
        val vigPixels = IntArray(vw * vh)
        vigTex.getPixels(vigPixels, 0, vw, 0, 0, vw, vh)

        // Find min and max luminance of the texture mask for proper photographic normalization
        var minL = 255f
        var maxL = 0f
        for (vp in vigPixels) {
            val l = (vp shr 16 and 0xFF) * 0.299f + (vp shr 8 and 0xFF) * 0.587f + (vp and 0xFF) * 0.114f
            if (l < minL) minL = l
            if (l > maxL) maxL = l
        }
        val range = (maxL - minL).coerceAtLeast(1.0f)

        for (y in 0 until height) {
            val vy = (y * vh) / height
            val vRow = vy * vw
            val row = y * width
            for (x in 0 until width) {
                val vx = (x * vw) / width
                val vp = vigPixels[vRow + vx]
                val vl = (vp shr 16 and 0xFF) * 0.299f + (vp shr 8 and 0xFF) * 0.587f + (vp and 0xFF) * 0.114f
                val norm = ((vl - minL) / range).coerceIn(0.0f, 1.0f)
                val factor = (1.0f - intensity * (1.0f - norm)).coerceIn(0.20f, 1.0f)

                val i = row + x
                val cp = pixels[i]
                val a = cp ushr 24
                val r = (((cp shr 16 and 0xFF) * factor).toInt()).coerceIn(0, 255)
                val g = (((cp shr 8 and 0xFF) * factor).toInt()).coerceIn(0, 255)
                val b = (((cp and 0xFF) * factor).toInt()).coerceIn(0, 255)
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun applyMathVignetteToPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        intensity: Float,
        radius: Float
    ) {
        val cx = width * 0.5f
        val cy = height * 0.5f
        val maxD = sqrt(cx * cx + cy * cy)

        for (y in 0 until height) {
            val dy = y - cy
            val row = y * width
            for (x in 0 until width) {
                val dx = x - cx
                val dn = (sqrt(dx * dx + dy * dy) / maxD) / radius
                val v = (1.0f - intensity * dn * dn).coerceIn(0.25f, 1.0f)

                val i = row + x
                val cp = pixels[i]
                val a = cp ushr 24
                val r = (((cp shr 16 and 0xFF) * v).toInt()).coerceIn(0, 255)
                val g = (((cp shr 8 and 0xFF) * v).toInt()).coerceIn(0, 255)
                val b = (((cp and 0xFF) * v).toInt()).coerceIn(0, 255)
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    // ─── Scanned Film Halide Grain Texture ───────────────────────────────────
    private fun applyGrainTexture(
        pixels: IntArray,
        width: Int,
        height: Int,
        grainTex: Bitmap,
        intensity: Float
    ) {
        val gw = grainTex.width
        val gh = grainTex.height
        val grainPixels = IntArray(gw * gh)
        grainTex.getPixels(grainPixels, 0, gw, 0, 0, gw, gh)

        val grainStrength = intensity * 0.45f

        for (y in 0 until height) {
            val gy = (y % gh) * gw
            val row = y * width
            for (x in 0 until width) {
                val gx = x % gw
                val gp = grainPixels[gy + gx]
                val gl = (((gp shr 16 and 0xFF) * 0.299f + (gp shr 8 and 0xFF) * 0.587f + (gp and 0xFF) * 0.114f) - 128f) * (1.0f / 128f)

                val i = row + x
                val cp = pixels[i]
                var r = ((cp shr 16 and 0xFF) / 255.0f)
                var g = ((cp shr 8 and 0xFF) / 255.0f)
                var b = ((cp and 0xFF) / 255.0f)
                val luma = r * 0.299f + g * 0.587f + b * 0.114f
                val midD = luma - 0.45f
                val bell = (1.0f - 2.8f * midD * midD).coerceIn(0.0f, 1.0f)
                var grainWeight = 0.45f + 0.55f * bell - 0.15f * maxOf(0.0f, luma - 0.70f)

                if (r > g && g > b && r > 0.25f && r < 0.90f) {
                    val skinWeight = ((r - b) * 2.0f).coerceIn(0.0f, 1.0f)
                    grainWeight *= (1.0f - 0.30f * skinWeight)
                }

                val delta = gl * grainStrength * grainWeight
                r = (r + delta).coerceIn(0.0f, 1.0f)
                g = (g + delta).coerceIn(0.0f, 1.0f)
                b = (b + delta).coerceIn(0.0f, 1.0f)

                val a = cp ushr 24
                val ir = (r * 255.0f).toInt()
                val ig = (g * 255.0f).toInt()
                val ib = (b * 255.0f).toInt()
                pixels[i] = (a shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }
    }

    // ─── Hald CLUT Sampling Math ─────────────────────────────────────────────
    private fun sampleLut(lut: IntArray, r64: Int, g64: Int, b64: Int): Int {
        val tileX = (b64 % 8) * 64
        val tileY = (b64 / 8) * 64
        val lutX = tileX + r64
        val lutY = tileY + g64
        return lut[lutY * 512 + lutX]
    }

    private fun trilinear(
        c000: Float, c100: Float, c010: Float, c110: Float,
        c001: Float, c101: Float, c011: Float, c111: Float,
        xf: Float, yf: Float, zf: Float
    ): Float {
        val c00 = c000 * (1.0f - xf) + c100 * xf
        val c10 = c010 * (1.0f - xf) + c110 * xf
        val c01 = c001 * (1.0f - xf) + c101 * xf
        val c11 = c011 * (1.0f - xf) + c111 * xf
        val c0 = c00 * (1.0f - yf) + c10 * yf
        val c1 = c01 * (1.0f - yf) + c11 * yf
        return (c0 * (1.0f - zf) + c1 * zf).coerceIn(0.0f, 1.0f)
    }

    // ─── Preset to Asset Routing Tables ──────────────────────────────────────
    private fun selectLutForPreset(presetId: String): String = when (presetId) {
        "analog_fxn_r"         -> "lut_fxn_r.png"
        "ccd_r", "ccdr", "ccd r", "ccd-r", "ccd_classic" -> "lut_ccd_r.png"
        "analog_35mm_classic", "d_classic", "dclassic", "d classic", "classic_35mm" -> "lut_analog_35mm_classic.png"
        "analog_135ne"         -> "lut_135ne.png"
        "analog_warm_film", "ccd_grd" -> "lut_analog_warm_film.png"
        "analog_soft_film", "analog_ct2f" -> "lut_ct2f.png"
        "analog_vintage_color", "analog_dexp" -> "lut_dexp.png"
        "analog_cinema_film", "analog_d3d" -> "lut_d3d.png"
        "ccd_flash", "ccd flash", "d_funs", "dfuns" -> "lut_dfuns.png"
        "ccd_y2k_digital", "classic_u", "classicu" -> "lut_ccd_y2k_digital.png"
        "ccd_retro_compact", "dqs" -> "lut_ccd_retro_compact.png"
        "disp_classic", "disp_golf" -> "lut_disp_classic.png"
        "disp_flash", "disp_cpm35" -> "lut_disp_flash.png"
        "disp_warm", "analog_135sr" -> "lut_disp_warm.png"
        "disp_night", "analog_dhalf" -> "lut_disp_night.png"
        "inst_vintage", "inst_c"   -> "lut_inst_vintage.png"
        "analog_nt16"          -> "lut_nt16.png"
        "analog_dblack"        -> "lut_bw.png"
        "analog_ir"            -> "lut_ir.png"
        "analog_s67"           -> "lut_analog_35mm_classic.png"
        "analog_sclassic"      -> "lut_analog_warm_film.png"
        "analog_ofm"           -> "lut_analog_35mm_classic.png"
        "analog_srx"           -> "lut_disp_warm.png"
        "analog_dslide"        -> "lut_analog_vintage_color.png"
        "analog_slidep"        -> "lut_analog_vintage_color.png"
        "analog_kv88", "analog_kv80" -> "lut_analog_35mm_classic.png"
        "analog_hoga"          -> "lut_analog_warm_film.png"
        "analog_original"      -> "lut_analog_35mm_classic.png"
        "analog_collage"       -> "lut_analog_35mm_classic.png"
        "analog_kino"          -> "lut_analog_cinema_film.png"
        "analog_glow"          -> "lut_analog_soft_film.png"
        "ccd_dam"              -> "lut_ccd_r.png"
        "disp_fqs"             -> "lut_disp_classic.png"
        "disp_puli"            -> "lut_disp_flash.png"
        "inst_sq", "inst_sqc", "inst_ss", "inst_paf" -> "lut_inst_vintage.png"
        "cam_retro_vhs"        -> "lut_cam_retro_vhs.png"
        "cam_analog_video"     -> "lut_cam_analog_video.png"
        "cam_90s_camcorder"    -> "lut_cam_90s_camcorder.png"
        else                   -> "lut_analog_35mm_classic.png"
    }

    private fun selectGrainForPreset(presetId: String, isCamcorder: Boolean): String = when {
        isCamcorder                            -> "grains/grain8mm_01.jpg"
        presetId == "analog_fxn_r"             -> "grains/grain_ou.jpg"
        presetId in listOf("ccd_r", "ccdr", "ccd_dam") -> "grains/grain_ccdr.jpg"
        presetId in listOf("analog_35mm_classic", "d_classic", "dclassic", "analog_ofm", "analog_original") -> "grains/grain_dclassic.jpg"
        presetId in listOf("analog_soft_film", "analog_s67", "analog_sclassic") -> "grains/grain_s67.jpg"
        presetId in listOf("analog_vintage_color", "analog_dexp", "analog_dslide", "analog_slidep") -> "grains/grain_dexp.jpg"
        presetId in listOf("analog_cinema_film", "analog_d3d", "analog_kino") -> "grains/grain_d3d1.jpg"
        presetId in listOf("ccd_flash", "dfuns", "disp_puli") -> "grains/grain_dfuns.jpg"
        presetId in listOf("ccd_y2k_digital", "classicu") -> "grains/grain_classicu.jpg"
        presetId in listOf("ccd_retro_compact", "dqs", "disp_fqs") -> "grains/grain_dqs.jpg"
        presetId in listOf("disp_classic", "disp_golf") -> "grains/grain_dexp2.jpg"
        presetId in listOf("disp_flash", "disp_cpm35") -> "grains/grain_cpm35.jpg"
        presetId in listOf("disp_warm", "analog_135sr", "analog_srx") -> "grains/grain540_01.jpg"
        presetId in listOf("disp_night", "analog_dhalf", "analog_nt16") -> "grains/grain_ou_lowlight.jpg"
        presetId in listOf("inst_vintage", "inst_c", "inst_sq", "inst_sqc", "inst_ss", "inst_paf") -> "grains/grain_instc.jpg"
        presetId in listOf("analog_135ne", "135ne") -> "grains/grain_135sr.jpg"
        presetId == "analog_dblack"            -> "grains/grain_dexp.jpg"
        presetId == "analog_hoga"              -> "grains/grain_hoga.jpg"
        presetId in listOf("analog_kv88", "analog_kv80") -> "grains/grain_kv88.jpg"
        presetId == "analog_glow"              -> "grains/grain_classicu.jpg"
        presetId == "analog_ir"                -> "grains/grain_IR.jpg"
        else                                   -> "grains/grain_dclassic.jpg"
    }

    private fun selectVignetteForPreset(presetId: String): String? = when (presetId) {
        "analog_fxn_r", "analog_nt16"          -> "vignette_nt16.jpg"
        "analog_35mm_classic", "d_classic", "dclassic", "d classic", "classic_35mm", "analog_warm_film", "ccd_grd" -> "vignette_dclassic.jpg"
        "analog_soft_film", "analog_ct2f"      -> "vignette_ct2f.jpg"
        "analog_vintage_color", "analog_dexp"  -> "vignette_dexp.jpg"
        "analog_cinema_film", "analog_d3d"     -> "vignette_dclassic.jpg"
        "disp_flash", "disp_cpm35"             -> "vignette_cpm35.jpg"
        "disp_warm", "analog_135sr", "analog_srx" -> "135sr_vig.jpg"
        "inst_vintage", "inst_c", "inst_sq", "inst_sqc", "inst_ss", "inst_paf" -> "135sr_vig.jpg"
        "analog_hoga"                          -> "mask_lens_paf.jpg"
        "ccd_flash", "ccd_y2k_digital", "classicu", "ccd_r", "ccdr", "ccd_dam" -> "sclassic_vintage.jpg"
        else                                   -> null
    }

    private fun selectDustForPreset(presetId: String): String = when (presetId) {
        "analog_fxn_r"         -> "dust_135.png"
        "ccd_r", "ccdr", "ccd r", "ccd-r", "ccd_classic" -> "dust_black.png"
        "analog_35mm_classic", "d_classic", "dclassic", "d classic", "classic_35mm" -> "dust_135.png"
        "analog_warm_film"     -> "135_dust.png"
        "analog_soft_film"     -> "dust_black.png"
        "analog_vintage_color" -> "dust_135.png"
        "analog_cinema_film"   -> "dust_135.png"
        "ccd_flash"            -> "dust_black.png"
        "ccd_y2k_digital", "classic_u", "classicu" -> "dust_black.png"
        "ccd_retro_compact", "dqs" -> "dust_black.png"
        "disp_classic"         -> "dust_135.png"
        "disp_flash"           -> "dust_135.png"
        "disp_warm"            -> "dust_135.png"
        "disp_night"           -> "dust_black.png"
        "inst_vintage"         -> "dust_instc.png"
        "cam_retro_vhs"        -> "vhs_grain_dust01.jpg"
        "cam_analog_video"     -> "8mm_dust.png"
        "cam_90s_camcorder"    -> "vhs_grain_dust05.jpg"
        else                   -> "dust_135.png"
    }

    private fun selectFlashForPreset(presetId: String): String? = when (presetId) {
        "disp_flash", "analog_35mm_classic", "d_classic", "dclassic", "d classic", "classic_35mm" -> "flash_01.jpg"
        "ccd_flash", "ccd flash", "d_funs", "dfuns" -> "overlay_flashc.jpg"
        else                      -> null
    }

    private fun selectLightLeakForPreset(presetId: String): String {
        return if (presetId.startsWith("inst")) {
            val idx = (System.currentTimeMillis() % 10).toInt() + 1
            "lightleaks_inst_%02d.jpg".format(idx)
        } else {
            val idx = (System.currentTimeMillis() % 17).toInt() + 1
            "lightleaks%d.jpg".format(idx)
        }
    }

    private fun applyFilmicCurve(v: Float, contrast: Float, fade: Float): Float {
        val x = v.coerceIn(0.0f, 1.0f)
        val s = if (x < 0.5f) 0.5f * (2.0f * x).pow(contrast)
                else 1.0f - 0.5f * (2.0f * (1.0f - x)).pow(contrast)
        return if (fade > 0.001f) {
            val lift = fade * 0.12f
            (s * (1.0f - lift) + lift).coerceIn(0.0f, 1.0f)
        } else s.coerceIn(0.0f, 1.0f)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        try {
            return image.toBitmap()
        } catch (e: Throwable) {
            Log.w(TAG, "image.toBitmap() failed, falling back to planes buffer decode", e)
        }
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Failed to decode ImageProxy to Bitmap")
    }

    private fun cropToAspectRatio(bitmap: Bitmap, mode: AspectRatioMode): Bitmap {
        val srcW = bitmap.width
        val srcH = bitmap.height
        val srcRatio = srcW.toFloat() / srcH
        val targetRatio = if (srcW > srcH) mode.ratio else (1.0f / mode.ratio)
        val cropW: Int
        val cropH: Int
        if (srcRatio > targetRatio) {
            cropH = srcH
            cropW = (srcH * targetRatio).toInt().coerceIn(1, srcW)
        } else {
            cropW = srcW
            cropH = (srcW / targetRatio).toInt().coerceIn(1, srcH)
        }
        val startX = (srcW - cropW) / 2
        val startY = (srcH - cropH) / 2
        return Bitmap.createBitmap(bitmap, startX, startY, cropW, cropH)
    }

    internal fun saveToGallery(context: Context, bitmap: Bitmap, cameraName: String): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "CAMELEO_${timeStamp}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Cameleo")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
                try {
                    context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        exif.setAttribute(ExifInterface.TAG_MAKE, "Cameleo")
                        exif.setAttribute(ExifInterface.TAG_MODEL, cameraName)
                        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Cameleo Vintage Camera 1.0")
                        exif.saveAttributes()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to write EXIF attributes", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write image data to MediaStore", e)
                return null
            }
        }
        return uri
    }
}
