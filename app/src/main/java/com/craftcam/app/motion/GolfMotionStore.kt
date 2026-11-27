package com.craftcam.app.motion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * Companion storage manager for Golf Motion Photographs.
 *
 * Keeps each Motion Photo as a single logical MediaStore item (`CAMELEO_Golf_*.jpg`)
 * paired with its rendered MP4 clip and metadata under `filesDir/motion_clips/`,
 * avoiding confusing duplicate gallery entries. Also injects standard Android
 * GCamera MotionPhoto XMP + MP4 container trailer into the saved JPEG bytes when requested.
 */
object GolfMotionStore {

    private const val TAG = "GolfMotionStore"
    private const val DIR_NAME = "motion_clips"

    fun clipsDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun videoFile(context: Context, mediaId: Long): File =
        File(clipsDir(context), "motion_${mediaId}.mp4")

    fun metadataFile(context: Context, mediaId: Long): File =
        File(clipsDir(context), "motion_${mediaId}.json")

    fun coverOverrideFile(context: Context, mediaId: Long): File =
        File(clipsDir(context), "cover_${mediaId}.jpg")

    fun hasMotionClip(context: Context, mediaId: Long): Boolean {
        if (mediaId <= 0L) return false
        val vf = videoFile(context, mediaId)
        return vf.exists() && vf.length() > 0L
    }

    fun listMotionMediaIds(context: Context): Set<Long> {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) return emptySet()
        val files = dir.listFiles().orEmpty()
        val ids = HashSet<Long>()
        for (file in files) {
            val name = file.name
            if (name.startsWith("motion_") && name.endsWith(".mp4") && file.length() > 0L) {
                name.removePrefix("motion_").removeSuffix(".mp4").toLongOrNull()?.let { ids.add(it) }
            }
        }
        return ids
    }

    fun saveClipAndMetadata(
        context: Context,
        mediaId: Long,
        renderedMp4File: File,
        metadata: GolfMotionMetadata,
        coverBitmap: Bitmap? = null
    ): File? {
        if (mediaId <= 0L || !renderedMp4File.exists() || renderedMp4File.length() == 0L) return null
        return try {
            val dst = videoFile(context, mediaId)
            renderedMp4File.inputStream().use { input ->
                FileOutputStream(dst).use { out -> input.copyTo(out, 65536) }
            }
            saveMetadata(context, metadata)
            if (coverBitmap != null && !coverBitmap.isRecycled) {
                saveCoverOverride(context, mediaId, coverBitmap)
            }
            dst
        } catch (t: Throwable) {
            Log.e(TAG, "Failed persisting Golf motion clip for ID=$mediaId", t)
            null
        }
    }

    fun saveMetadata(context: Context, metadata: GolfMotionMetadata) {
        try {
            val json = JSONObject().apply {
                put("mediaId", metadata.mediaId)
                put("durationMs", metadata.durationMs)
                put("fps", metadata.fps)
                put("playbackMode", metadata.playbackMode)
                put("loopPlayback", metadata.loopPlayback)
                put("flashEffectApplied", metadata.flashEffectApplied)
                put("hasAudio", metadata.hasAudio)
                put("coverFrameIndex", metadata.coverFrameIndex)
                put("totalFrames", metadata.totalFrames)
                put("width", metadata.width)
                put("height", metadata.height)
                put("sourceKind", metadata.sourceKind)
                put("colorProfileId", metadata.colorProfileId)
                put("createdAtEpochSec", metadata.createdAtEpochSec)
            }
            metadataFile(context, metadata.mediaId).writeText(json.toString(2), StandardCharsets.UTF_8)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed writing Golf motion metadata for ID=${metadata.mediaId}", t)
        }
    }

    fun loadMetadata(context: Context, mediaId: Long): GolfMotionMetadata? {
        if (mediaId <= 0L) return null
        val mf = metadataFile(context, mediaId)
        if (!mf.exists()) {
            val vf = videoFile(context, mediaId)
            return if (vf.exists() && vf.length() > 0L) {
                GolfMotionMetadata(
                    mediaId = mediaId,
                    durationMs = 2000L,
                    fps = 14,
                    playbackMode = GolfPlaybackCadence.VINTAGE.name,
                    loopPlayback = true,
                    flashEffectApplied = false,
                    hasAudio = false,
                    coverFrameIndex = 0,
                    totalFrames = 28,
                    width = 0,
                    height = 0,
                    sourceKind = "capture",
                    colorProfileId = "C400",
                    createdAtEpochSec = vf.lastModified() / 1000L
                )
            } else null
        }
        return try {
            val json = JSONObject(mf.readText(StandardCharsets.UTF_8))
            GolfMotionMetadata(
                mediaId = json.optLong("mediaId", mediaId),
                durationMs = json.optLong("durationMs", 2000L),
                fps = json.optInt("fps", 14),
                playbackMode = json.optString("playbackMode", GolfPlaybackCadence.VINTAGE.name),
                loopPlayback = json.optBoolean("loopPlayback", true),
                flashEffectApplied = json.optBoolean("flashEffectApplied", true),
                hasAudio = json.optBoolean("hasAudio", false),
                coverFrameIndex = json.optInt("coverFrameIndex", 0),
                totalFrames = json.optInt("totalFrames", 28),
                width = json.optInt("width", 0),
                height = json.optInt("height", 0),
                sourceKind = json.optString("sourceKind", "capture"),
                colorProfileId = json.optString("colorProfileId", "C400"),
                createdAtEpochSec = json.optLong("createdAtEpochSec", 0L)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed parsing motion metadata for ID=$mediaId", t)
            null
        }
    }

    fun updateCoverSelection(
        context: Context,
        mediaId: Long,
        coverFrameIndex: Int,
        coverBitmap: Bitmap?
    ) {
        val current = loadMetadata(context, mediaId) ?: return
        val clamped = coverFrameIndex.coerceIn(0, (current.totalFrames - 1).coerceAtLeast(0))
        saveMetadata(context, current.copy(coverFrameIndex = clamped))
        if (coverBitmap != null && !coverBitmap.isRecycled) {
            saveCoverOverride(context, mediaId, coverBitmap)
        }
    }

    fun saveCoverOverride(context: Context, mediaId: Long, bitmap: Bitmap) {
        try {
            FileOutputStream(coverOverrideFile(context, mediaId)).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed writing cover override for ID=$mediaId", t)
        }
    }

    fun loadCoverOverride(context: Context, mediaId: Long): Bitmap? {
        if (mediaId <= 0L) return null
        val file = coverOverrideFile(context, mediaId)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Throwable) {
            null
        }
    }

    fun deleteMotionAssets(context: Context, mediaId: Long) {
        if (mediaId <= 0L) return
        try { videoFile(context, mediaId).delete() } catch (_: Throwable) {}
        try { metadataFile(context, mediaId).delete() } catch (_: Throwable) {}
        try { coverOverrideFile(context, mediaId).delete() } catch (_: Throwable) {}
    }

    /**
     * Packages an upright JPEG and an MP4 video stream into a Google/Android standard
     * Motion Photo JPEG (APP1 XMP container directory + trailing MP4 bytes),
     * while preserving valid JPEG SOI..EOI boundaries for standard image viewers.
     */
    fun buildGoogleMotionPhotoBytes(jpegBytes: ByteArray, mp4Bytes: ByteArray): ByteArray? {
        if (jpegBytes.size < 4 || mp4Bytes.size < 12) return null
        if (jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) return null

        val padding = 0
        val itemLength = mp4Bytes.size
        val totalLen = itemLength + padding
        val xmpBody = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
             <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
              <rdf:Description rdf:about=""
                xmlns:GCamera="http://ns.google.com/cameras/1.0/camera/"
                xmlns:Camera="http://ns.google.com/cameras/1.0/camera/"
                xmlns:Container="http://ns.google.com/photos/1.0/container/"
                xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
                GCamera:MotionPhoto="1"
                GCamera:MotionPhotoVersion="1"
                GCamera:MicroVideo="1"
                GCamera:MicroVideoVersion="1"
                GCamera:MicroVideoOffset="$totalLen">
               <Container:Directory>
                <rdf:Seq>
                 <rdf:li rdf:parseType="Resource">
                  <Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/>
                 </rdf:li>
                 <rdf:li rdf:parseType="Resource">
                  <Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="$itemLength"/>
                 </rdf:li>
                </rdf:Seq>
               </Container:Directory>
              </rdf:Description>
             </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()

        val headerPrefix = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(StandardCharsets.ISO_8859_1)
        val xmpData = xmpBody.toByteArray(StandardCharsets.UTF_8)
        val app1Length = 2 + headerPrefix.size + xmpData.size
        if (app1Length > 65535) return null

        val out = ByteArray(2 + 2 + app1Length + (jpegBytes.size - 2) + mp4Bytes.size)
        var pos = 0
        // JPEG SOI
        out[pos++] = 0xFF.toByte()
        out[pos++] = 0xD8.toByte()
        // APP1 marker + length + XMP
        out[pos++] = 0xFF.toByte()
        out[pos++] = 0xE1.toByte()
        out[pos++] = ((app1Length shr 8) and 0xFF).toByte()
        out[pos++] = (app1Length and 0xFF).toByte()
        System.arraycopy(headerPrefix, 0, out, pos, headerPrefix.size)
        pos += headerPrefix.size
        System.arraycopy(xmpData, 0, out, pos, xmpData.size)
        pos += xmpData.size
        // Remaining JPEG bytes after SOI
        System.arraycopy(jpegBytes, 2, out, pos, jpegBytes.size - 2)
        pos += jpegBytes.size - 2
        // Appended MP4 track
        System.arraycopy(mp4Bytes, 0, out, pos, mp4Bytes.size)
        return out
    }
}
