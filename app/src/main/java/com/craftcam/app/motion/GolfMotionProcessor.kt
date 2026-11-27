package com.craftcam.app.motion

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.camera.profile.LensProfile
import com.craftcam.app.color.profile.DazzColorProfileCatalog
import com.craftcam.app.config.AppSettingsStore
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.processor.ImageProcessor
import com.craftcam.app.processor.writeMediaItem
import com.craftcam.app.ui.gallery.GalleryCache
import com.craftcam.app.ui.gallery.GalleryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Coordinates Golf Motion Photo development, multi-format import, cover extraction,
 * MediaStore MP4 / GIF / Still export, and single-item gallery pairing.
 */
object GolfMotionProcessor {

    private const val TAG = "GolfMotionProcessor"

    data class MotionDevelopResult(
        val mediaId: Long,
        val imageUri: Uri,
        val clipFile: File?,
        val metadata: GolfMotionMetadata?
    )

    suspend fun developCapturedMotionClip(
        context: Context,
        mediaId: Long,
        imageUri: Uri,
        frames: List<EncodedMotionFrame>,
        audioPcm: ShortArray,
        shutterTimestampNs: Long,
        config: GolfMotionConfig,
        preset: CameraPreset = PresetCatalog.getPresetById("disp_classic"),
        tuning: UserTuningConfig,
        aspectRatio: AspectRatioMode,
        lensProfile: LensProfile,
        onProgress: ((Float) -> Unit)? = null
    ): GolfMotionMetadata? = withContext(Dispatchers.Default) {
        if (mediaId <= 0L || frames.isEmpty()) return@withContext null
        val tempMp4 = File(context.cacheDir, "golf_motion_${System.currentTimeMillis()}.mp4")
        val source = EncodedJpegRingSource(frames)
        val encoder = GolfMotionEncoder()
        try {
            val fps = config.playback.targetFps
            val indices = frames.indices.toList()
            val plan = GolfTemporalEffectPlanner.planTimeline(
                orderedFrames = indices,
                timestampNsOf = { idx -> source.timestampNs(idx) },
                shutterTimestampNs = shutterTimestampNs,
                targetDurationMs = config.duration.durationMs,
                targetFps = fps,
                flashEffectEnabled = config.flashEffectEnabled
            )
            val result = encoder.encodeGolfMotionClip(
                context = context,
                source = source,
                timeline = plan,
                sourceIndexOf = { it },
                preset = preset,
                tuning = tuning,
                aspectRatio = aspectRatio,
                lensProfile = lensProfile,
                audioPcm = if (config.audioEnabled) audioPcm else ShortArray(0),
                fps = fps,
                outputFile = tempMp4,
                onProgress = onProgress
            )
            if (!result.success) return@withContext null

            val profId = DazzColorProfileCatalog.getByIndex(tuning.filmColorProfileIndex).id
            val metadata = GolfMotionMetadata(
                mediaId = mediaId,
                durationMs = result.durationMs,
                fps = fps,
                playbackMode = config.playback.name,
                loopPlayback = config.loopPlayback,
                flashEffectApplied = config.flashEffectEnabled,
                hasAudio = result.hasAudioTrack,
                coverFrameIndex = plan.shutterOutputIndex,
                totalFrames = result.encodedFrames,
                width = result.width,
                height = result.height,
                sourceKind = "capture",
                colorProfileId = profId,
                createdAtEpochSec = System.currentTimeMillis() / 1000L
            )
            GolfMotionStore.saveClipAndMetadata(context, mediaId, tempMp4, metadata)

            // Refresh single-item GalleryCache badge & thumbnail atomically
            val existing = GalleryCache.cachedItems.orEmpty().map { item ->
                if (item.id == mediaId) item.copy(hasMotionClip = true) else item
            }
            if (existing.isNotEmpty()) GalleryCache.cachedItems = existing
            metadata
        } catch (t: Throwable) {
            Log.e(TAG, "Failed developing captured Golf motion clip", t)
            null
        } finally {
            source.close()
            try { tempMp4.delete() } catch (_: Throwable) {}
        }
    }

    suspend fun importAndDevelopWithGolf(
        context: Context,
        probe: MotionPhotoImporter.ImportProbeResult.MotionAvailable,
        config: GolfMotionConfig,
        aspectRatio: AspectRatioMode = AspectRatioMode.RATIO_4_3,
        lensProfile: LensProfile = LensProfile.NORMAL,
        creativeTemperature: Float = 0.0f,
        creativeTempSkinProtection: Boolean = true,
        onProgress: ((Float) -> Unit)? = null
    ): MotionDevelopResult? = withContext(Dispatchers.Default) {
        val golfPreset = PresetCatalog.getPresetById("disp_classic")
        val savedProfId = AppSettingsStore.get(context).getColorProfileForCamera(golfPreset.id)
        val profileIdx = DazzColorProfileCatalog.getById(savedProfId).index
        val lutPlan = com.craftcam.app.color.profile.CameraProfileCompatibility.resolveLutPlan(
            presetId = golfPreset.id,
            colorProfileIndex = profileIdx
        )
        val tuning = UserTuningConfig(
            filmColorProfileIndex = profileIdx,
            creativeTemperature = com.craftcam.app.color.CreativeTemperatureEngine.resolveEffectiveTemperature(
                creativeTemperature,
                lutPlan.isMonochrome
            ),
            creativeTempSkinProtection = creativeTempSkinProtection,
            isHardwareWbActive = false
        )
        val fps = config.playback.targetFps
        val videoSource = VideoClipFrameSource(
            videoFile = probe.extractedVideoFile,
            maxDurationMs = config.duration.durationMs,
            targetFps = fps
        )
        val tempMp4 = File(context.cacheDir, "golf_import_motion_${System.currentTimeMillis()}.mp4")
        var coverBitmap: Bitmap? = null
        try {
            // 1. Obtain high-res cover still image (original image URI when available, else first video frame)
            coverBitmap = if (probe.primaryImageUri != null) {
                context.contentResolver.openInputStream(probe.primaryImageUri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply {
                        inMutable = true
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            } else null
            if (coverBitmap == null && videoSource.frameCount > 0) {
                coverBitmap = videoSource.loadBitmap(0)
            }
            val stillSource = coverBitmap ?: return@withContext null

            // Crop cover still to chosen aspect ratio so still & motion align seamlessly
            val stillRatio = when {
                stillSource.width == stillSource.height -> AspectRatioMode.RATIO_1_1
                else -> aspectRatio
            }
            val stillUri = ImageProcessor.developBitmap(
                context = context,
                sourceBitmap = stillSource,
                preset = golfPreset,
                tuning = tuning,
                lensProfile = lensProfile,
                skipDecorationsIfBaked = false
            ) ?: return@withContext null

            val mediaId = try { ContentUris.parseId(stillUri) } catch (_: Exception) { 0L }
            val indices = (0 until videoSource.frameCount).toList()
            val midIdx = indices.size / 2
            val shutterTs = if (indices.isNotEmpty()) videoSource.timestampNs(midIdx) else 0L
            val plan = GolfTemporalEffectPlanner.planTimeline(
                orderedFrames = indices,
                timestampNsOf = { idx -> videoSource.timestampNs(idx) },
                shutterTimestampNs = shutterTs,
                targetDurationMs = config.duration.durationMs,
                targetFps = fps,
                flashEffectEnabled = config.flashEffectEnabled
            )
            val result = GolfMotionEncoder().encodeGolfMotionClip(
                context = context,
                source = videoSource,
                timeline = plan,
                sourceIndexOf = { it },
                preset = golfPreset,
                tuning = tuning,
                aspectRatio = stillRatio,
                lensProfile = lensProfile,
                audioPcm = ShortArray(0),
                fps = fps,
                outputFile = tempMp4,
                onProgress = onProgress
            )
            val metadata = if (result.success && mediaId > 0L) {
                GolfMotionMetadata(
                    mediaId = mediaId,
                    durationMs = result.durationMs,
                    fps = fps,
                    playbackMode = config.playback.name,
                    loopPlayback = config.loopPlayback,
                    flashEffectApplied = config.flashEffectEnabled,
                    hasAudio = false,
                    coverFrameIndex = plan.shutterOutputIndex,
                    totalFrames = result.encodedFrames,
                    width = result.width,
                    height = result.height,
                    sourceKind = probe.sourceKind,
                    colorProfileId = DazzColorProfileCatalog.getById(savedProfId).id,
                    createdAtEpochSec = System.currentTimeMillis() / 1000L
                ).also { meta ->
                    GolfMotionStore.saveClipAndMetadata(context, mediaId, tempMp4, meta)
                    GalleryCache.cachedItems = GalleryCache.cachedItems.orEmpty().map { g ->
                        if (g.id == mediaId) g.copy(hasMotionClip = true) else g
                    }
                }
            } else null

            MotionDevelopResult(
                mediaId = mediaId,
                imageUri = stillUri,
                clipFile = if (metadata != null) GolfMotionStore.videoFile(context, mediaId) else null,
                metadata = metadata
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Import motion photo development failed", t)
            null
        } finally {
            videoSource.close()
            try { if (coverBitmap != null && !coverBitmap.isRecycled) coverBitmap.recycle() } catch (_: Throwable) {}
            try { probe.extractedVideoFile.delete() } catch (_: Throwable) {}
            try { tempMp4.delete() } catch (_: Throwable) {}
        }
    }

    fun extractFrameAt(context: Context, clipFile: File, timeMs: Long): Bitmap? {
        if (!clipFile.exists() || clipFile.length() == 0L) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(clipFile.absolutePath)
            retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    suspend fun exportMotionMp4ToMediaStore(
        context: Context,
        mediaId: Long,
        baseName: String = "Golf"
    ): Uri? = withContext(Dispatchers.IO) {
        val clipFile = GolfMotionStore.videoFile(context, mediaId)
        if (!clipFile.exists() || clipFile.length() == 0L) return@withContext null
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "CAMELEO_${baseName}_MOTION_${ts}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Cameleo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        try {
            context.contentResolver.writeMediaItem(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) { out ->
                FileInputStream(clipFile).use { input -> input.copyTo(out, 65536) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "MP4 export to MediaStore failed", t)
            null
        }
    }

    suspend fun exportMotionGifToMediaStore(
        context: Context,
        mediaId: Long,
        loop: Boolean = true
    ): Uri? = withContext(Dispatchers.IO) {
        val clipFile = GolfMotionStore.videoFile(context, mediaId)
        if (!clipFile.exists() || clipFile.length() == 0L) return@withContext null
        val meta = GolfMotionStore.loadMetadata(context, mediaId)
        val tempGif = File(context.cacheDir, "golf_export_${mediaId}_${System.currentTimeMillis()}.gif")
        try {
            val ok = GolfGifExporter.exportMp4ToGif(
                mp4File = clipFile,
                outputGifFile = tempGif,
                targetFps = (meta?.fps ?: 14).coerceAtMost(15),
                loop = loop
            )
            if (!ok) return@withContext null

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val displayName = "CAMELEO_Golf_${ts}.gif"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Cameleo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            context.contentResolver.writeMediaItem(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) { out ->
                FileInputStream(tempGif).use { input -> input.copyTo(out, 65536) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "GIF export failed", t)
            null
        } finally {
            try { tempGif.delete() } catch (_: Throwable) {}
        }
    }

    suspend fun extractStillFrameToMediaStore(
        context: Context,
        mediaId: Long,
        timeMs: Long
    ): Uri? = withContext(Dispatchers.IO) {
        val clipFile = GolfMotionStore.videoFile(context, mediaId)
        val bmp = extractFrameAt(context, clipFile, timeMs) ?: return@withContext null
        try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "CAMELEO_Golf_Still_${ts}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Cameleo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.writeMediaItem(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 94, out)
            }
            if (uri != null) {
                val id = try { ContentUris.parseId(uri) } catch (_: Exception) { 0L }
                if (id > 0L) {
                    GalleryCache.putThumbnail(id, GalleryCache.createThumbnail(bmp, 256))
                    GalleryCache.addNewItem(
                        GalleryItem(
                            id = id,
                            uri = uri,
                            name = fileName,
                            dateAdded = System.currentTimeMillis() / 1000L,
                            cameraModel = "Golf",
                            isVideo = false,
                            hasMotionClip = false
                        )
                    )
                }
            }
            uri
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    fun getShareableFileUri(context: Context, file: File): Uri? {
        return try {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (t: Throwable) {
            Log.w(TAG, "FileProvider URI lookup failed for ${file.absolutePath}", t)
            null
        }
    }
}
