package com.craftcam.app.motion

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * Multi-format Motion Photo & Short Video Importer for Golf camera.
 *
 * Cleanly parses and validates embedded or paired video streams across:
 * 1. Samsung Motion Photos (`MotionPhoto_Data` / `SEFT` JPEG trailer)
 * 2. Google / Android Motion Photos (`GCamera:MicroVideoOffset` & `Container:Directory` XMP + EOI fallback)
 * 3. Apple Live Photos (still photo + companion `.MOV` resource)
 * 4. Standalone short MP4 / MOV video clips
 *
 * Guarantees safe fallback to [ImportProbeResult.StillOnly] whenever motion data is absent or invalid,
 * never generating fake motion from ordinary static JPEGs.
 */
object MotionPhotoImporter {

    private const val TAG = "MotionPhotoImporter"
    private const val MAX_DIRECT_READ_BYTES = 48 * 1024 * 1024

    sealed class ImportProbeResult {
        data class MotionAvailable(
            val sourceKind: String,
            val extractedVideoFile: File,
            val primaryImageUri: Uri?
        ) : ImportProbeResult()

        data class StillOnly(
            val imageUri: Uri
        ) : ImportProbeResult()

        data class Unsupported(val reason: String) : ImportProbeResult()
    }

    /**
     * Pure byte-level parser exposed for deterministic offline unit tests and physical imports.
     * Returns the exact start byte offset and byte length of an embedded MP4 inside a Motion Photo JPEG,
     * or null when the JPEG contains only still-image data.
     */
    fun findEmbeddedMp4Range(jpegBytes: ByteArray): Pair<Int, Int>? {
        if (jpegBytes.size < 32) return null

        // 1. Samsung Motion Photo signature: literal ASCII "MotionPhoto_Data" followed by ISO BMFF `....ftyp`
        val samsungMarker = "MotionPhoto_Data".toByteArray(StandardCharsets.US_ASCII)
        val samsungIdx = indexOfBytes(jpegBytes, samsungMarker, 0)
        if (samsungIdx >= 0) {
            val mp4Start = findFtypBoxStart(jpegBytes, samsungIdx + samsungMarker.size)
            if (mp4Start != null && hasValidFtypBox(jpegBytes, mp4Start)) {
                val trimEnd = findSamsungTrailerEnd(jpegBytes, mp4Start) ?: jpegBytes.size
                if (trimEnd - mp4Start >= 64) {
                    return mp4Start to (trimEnd - mp4Start)
                }
            }
        }

        // 2. Google / Android Camera Motion Photo XMP (`Container:Directory` Item:Length or `GCamera:MicroVideoOffset`)
        val headerScanLimit = minOf(jpegBytes.size, 256 * 1024)
        val xmpString = String(jpegBytes, 0, headerScanLimit, StandardCharsets.ISO_8859_1)

        parseContainerVideoLength(xmpString)?.let { videoLen ->
            val mp4Start = jpegBytes.size - videoLen
            if (mp4Start > 2 && mp4Start + 16 < jpegBytes.size && hasValidFtypBox(jpegBytes, mp4Start)) {
                return mp4Start to videoLen
            }
        }

        parseMicroVideoOffset(xmpString)?.let { offsetFromEnd ->
            val mp4Start = jpegBytes.size - offsetFromEnd
            if (mp4Start > 2 && mp4Start + 16 < jpegBytes.size && hasValidFtypBox(jpegBytes, mp4Start)) {
                return mp4Start to offsetFromEnd
            }
        }

        // 3. Conservative ISO BMFF fallback: only after JPEG EOI (`FF D9`), look for `[boxSize][ftyp]`
        val eoi = findJpegEndOfImage(jpegBytes)
        if (eoi != null && eoi + 16 < jpegBytes.size) {
            val mp4Start = findFtypBoxStart(jpegBytes, eoi)
            if (mp4Start != null && hasValidFtypBox(jpegBytes, mp4Start)) {
                return mp4Start to (jpegBytes.size - mp4Start)
            }
        }

        return null
    }

    fun detectContainerKind(jpegBytes: ByteArray): String {
        val samsungMarker = "MotionPhoto_Data".toByteArray(StandardCharsets.US_ASCII)
        if (indexOfBytes(jpegBytes, samsungMarker, 0) >= 0) return "samsung_motion_photo"
        val headerScanLimit = minOf(jpegBytes.size, 256 * 1024)
        val xmpString = String(jpegBytes, 0, headerScanLimit, StandardCharsets.ISO_8859_1)
        if (xmpString.contains("MotionPhoto", ignoreCase = true) ||
            xmpString.contains("MicroVideo", ignoreCase = true)) {
            return "google_motion_photo"
        }
        return "embedded_motion_photo"
    }

    fun probeAndExtract(
        context: Context,
        uris: List<Uri>,
        scratchDir: File = File(context.cacheDir, "motion_import").apply { mkdirs() }
    ): ImportProbeResult {
        if (uris.isEmpty()) return ImportProbeResult.Unsupported("No media selected")

        var imageUri: Uri? = null
        var videoUri: Uri? = null

        for (uri in uris) {
            val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
            val name = queryDisplayName(context, uri).orEmpty().lowercase()
            when {
                mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".m4v") -> {
                    if (videoUri == null) videoUri = uri
                }
                else -> {
                    if (imageUri == null) imageUri = uri
                }
            }
        }

        // Case A: Video present (short MP4/MOV or Apple Live Photo pair: image + MOV)
        if (videoUri != null) {
            val outFile = File(scratchDir, "import_vid_${System.currentTimeMillis()}.mp4")
            return try {
                context.contentResolver.openInputStream(videoUri)?.use { input ->
                    FileOutputStream(outFile).use { out -> input.copyTo(out, 65536) }
                } ?: return ImportProbeResult.Unsupported("Unable to open video stream")

                if (validateVideoHasTrack(outFile)) {
                    val kind = if (imageUri != null) "apple_live_photo" else "video_clip"
                    ImportProbeResult.MotionAvailable(
                        sourceKind = kind,
                        extractedVideoFile = outFile,
                        primaryImageUri = imageUri
                    )
                } else {
                    outFile.delete()
                    if (imageUri != null) ImportProbeResult.StillOnly(imageUri)
                    else ImportProbeResult.Unsupported("No playable video track found")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Video import probe failed", t)
                outFile.delete()
                if (imageUri != null) ImportProbeResult.StillOnly(imageUri)
                else ImportProbeResult.Unsupported(t.message ?: "Failed reading video")
            }
        }

        // Case B: Single Image -> check Samsung / Google Motion Photo embedded MP4
        val img = imageUri ?: return ImportProbeResult.Unsupported("No image selected")
        return try {
            val bytes = context.contentResolver.openInputStream(img)?.use { input ->
                val raw = input.readBytes()
                if (raw.size > MAX_DIRECT_READ_BYTES) null else raw
            }
            if (bytes != null) {
                val range = findEmbeddedMp4Range(bytes)
                if (range != null) {
                    val (start, length) = range
                    val outFile = File(scratchDir, "import_motion_${System.currentTimeMillis()}.mp4")
                    FileOutputStream(outFile).use { fos ->
                        fos.write(bytes, start, length)
                    }
                    if (validateVideoHasTrack(outFile)) {
                        return ImportProbeResult.MotionAvailable(
                            sourceKind = detectContainerKind(bytes),
                            extractedVideoFile = outFile,
                            primaryImageUri = img
                        )
                    }
                    outFile.delete()
                }
            }
            ImportProbeResult.StillOnly(img)
        } catch (t: Throwable) {
            Log.w(TAG, "Motion probe fallback to still for $img", t)
            ImportProbeResult.StillOnly(img)
        }
    }

    private fun validateVideoHasTrack(file: File): Boolean {
        if (!file.exists() || file.length() < 64L) return false
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var foundVideo = false
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    foundVideo = true
                    break
                }
            }
            foundVideo
        } catch (_: Throwable) {
            false
        } finally {
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Throwable) {
            uri.lastPathSegment
        }
    }

    private fun parseContainerVideoLength(xmp: String): Int? {
        if (!xmp.contains("MotionPhoto", ignoreCase = true) &&
            !xmp.contains("Directory", ignoreCase = true)) return null
        val itemPattern = Regex("""<[^>]*Container:Item[^>]*>|<rdf:li[^>]*>.*?</rdf:li>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        for (block in itemPattern.findAll(xmp)) {
            val text = block.value
            if (text.contains("video/mp4", ignoreCase = true) || text.contains("video/quicktime", ignoreCase = true)) {
                val lenMatch = Regex("""Item:Length\s*=\s*["'](\d+)["']""").find(text)
                    ?: Regex("""<Item:Length>(\d+)</Item:Length>""").find(text)
                val v = lenMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (v != null && v > 64) return v
            }
        }
        return null
    }

    private fun parseMicroVideoOffset(xmp: String): Int? {
        if (!xmp.contains("MicroVideo=\"1\"", ignoreCase = true) &&
            !xmp.contains("MicroVideo>1<", ignoreCase = true)) {
            return null
        }
        val m = Regex("""MicroVideoOffset\s*=\s*["'](\d+)["']""").find(xmp)
            ?: Regex("""<[^>]*MicroVideoOffset>(\d+)</[^>]*>""").find(xmp)
        return m?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 64 }
    }

    private fun indexOfBytes(haystack: ByteArray, needle: ByteArray, fromIndex: Int): Int {
        if (needle.isEmpty() || fromIndex < 0) return -1
        val limit = haystack.size - needle.size
        var i = fromIndex
        while (i <= limit) {
            var matched = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
            i++
        }
        return -1
    }

    private fun findFtypBoxStart(bytes: ByteArray, startOffset: Int): Int? {
        val ftyp = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        val ftypIdx = indexOfBytes(bytes, ftyp, startOffset.coerceAtLeast(4))
        if (ftypIdx < 4) return null
        return ftypIdx - 4
    }

    private fun hasValidFtypBox(bytes: ByteArray, offset: Int): Boolean {
        if (offset < 0 || offset + 12 > bytes.size) return false
        val boxSize = ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
        if (boxSize < 12 || boxSize > 128) return false
        return bytes[offset + 4] == 'f'.code.toByte() &&
            bytes[offset + 5] == 't'.code.toByte() &&
            bytes[offset + 6] == 'y'.code.toByte() &&
            bytes[offset + 7] == 'p'.code.toByte()
    }

    private fun findSamsungTrailerEnd(bytes: ByteArray, mp4Start: Int): Int? {
        // Samsung appends SEFH...SEFT block after MP4 on newer devices (`SEFT` footer at very end)
        val seft = "SEFT".toByteArray(StandardCharsets.US_ASCII)
        val tailStart = maxOf(mp4Start, bytes.size - 16)
        val seftIdx = indexOfBytes(bytes, seft, tailStart)
        if (seftIdx >= 0) {
            val sefh = "SEFH".toByteArray(StandardCharsets.US_ASCII)
            val sefhIdx = indexOfBytes(bytes, sefh, mp4Start)
            if (sefhIdx > mp4Start + 64) return sefhIdx
            if (seftIdx > mp4Start + 64) return seftIdx
        }
        return null
    }

    private fun findJpegEndOfImage(bytes: ByteArray): Int? {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        var i = 2
        while (i + 1 < bytes.size) {
            if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD9.toByte()) {
                return i + 2
            }
            i++
        }
        return null
    }
}
