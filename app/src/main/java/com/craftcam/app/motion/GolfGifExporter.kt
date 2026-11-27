package com.craftcam.app.motion

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Lightweight pure-Kotlin GIF89a animated exporter for Golf Motion Photos.
 * Quantizes pixels uniformly to a 216-color RGB cube + 40-step luminance ramp (256 palette)
 * using ordered Floyd/Bayer dithering so files export quickly with low bounded memory.
 */
object GolfGifExporter {

    private const val TAG = "GolfGifExporter"
    private const val MAX_GIF_DIM = 480

    suspend fun exportMp4ToGif(
        mp4File: File,
        outputGifFile: File,
        targetFps: Int = 12,
        loop: Boolean = true,
        maxDurationMs: Long = 3000L
    ): Boolean = withContext(Dispatchers.Default) {
        if (!mp4File.exists() || mp4File.length() == 0L) return@withContext false
        val retriever = MediaMetadataRetriever()
        val palette = buildWebSafePlusGrayPalette()
        val paletteBytes = ByteArray(256 * 3)
        for (i in 0 until 256) {
            val c = palette[i]
            paletteBytes[i * 3] = ((c ushr 16) and 0xFF).toByte()
            paletteBytes[i * 3 + 1] = ((c ushr 8) and 0xFF).toByte()
            paletteBytes[i * 3 + 2] = (c and 0xFF).toByte()
        }

        try {
            retriever.setDataSource(mp4File.absolutePath)
            val rawDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 2000L
            val clipMs = rawDurationMs.coerceIn(250L, maxDurationMs)
            val fps = targetFps.coerceIn(8, 18)
            val delayCs = (100f / fps).roundToInt().coerceIn(5, 20)
            val frameCount = ((clipMs / 1000.0) * fps).roundToInt().coerceIn(2, 48)

            val firstRaw = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@withContext false
            val scale = minOf(1.0f, MAX_GIF_DIM.toFloat() / maxOf(firstRaw.width, firstRaw.height))
            val outW = (firstRaw.width * scale).roundToInt().coerceAtLeast(16)
            val outH = (firstRaw.height * scale).roundToInt().coerceAtLeast(16)
            firstRaw.recycle()

            FileOutputStream(outputGifFile).buffered(65536).use { out ->
                writeHeaderAndScreen(out, outW, outH, paletteBytes)
                if (loop) writeNetscapeLoopExtension(out)

                val pixels = IntArray(outW * outH)
                val indices = ByteArray(outW * outH)
                for (i in 0 until frameCount) {
                    coroutineContext.ensureActive()
                    val tUs = if (frameCount <= 1) 0L else ((i.toLong() * clipMs * 1000L) / (frameCount - 1))
                    val raw = retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: continue
                    val scaled = if (raw.width == outW && raw.height == outH) raw else Bitmap.createScaledBitmap(raw, outW, outH, true)
                    if (scaled !== raw) raw.recycle()
                    try {
                        scaled.getPixels(pixels, 0, outW, 0, 0, outW, outH)
                        quantizeOrdered(pixels, outW, outH, indices)
                        writeGraphicControlExt(out, delayCs)
                        writeImageBlock(out, outW, outH, indices)
                    } finally {
                        if (!scaled.isRecycled) scaled.recycle()
                    }
                }
                out.write(0x3B) // GIF Trailer
                out.flush()
            }
            outputGifFile.exists() && outputGifFile.length() > 64L
        } catch (t: Throwable) {
            Log.e(TAG, "GIF export failed", t)
            try { outputGifFile.delete() } catch (_: Throwable) {}
            false
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    internal fun encodeBitmapsToGifBytes(frames: List<Bitmap>, delayCs: Int = 7, loop: Boolean = true): ByteArray {
        if (frames.isEmpty()) return ByteArray(0)
        val outW = frames.first().width
        val outH = frames.first().height
        val rgbFrames = frames.map { bmp ->
            val scaled = if (bmp.width == outW && bmp.height == outH) bmp else Bitmap.createScaledBitmap(bmp, outW, outH, true)
            val pixels = IntArray(outW * outH)
            scaled.getPixels(pixels, 0, outW, 0, 0, outW, outH)
            if (scaled !== bmp) scaled.recycle()
            pixels
        }
        return encodeRgbFramesToGifBytes(rgbFrames, outW, outH, delayCs, loop)
    }

    internal fun encodeRgbFramesToGifBytes(
        rgbFrames: List<IntArray>,
        outW: Int,
        outH: Int,
        delayCs: Int = 7,
        loop: Boolean = true
    ): ByteArray {
        if (rgbFrames.isEmpty() || outW <= 0 || outH <= 0) return ByteArray(0)
        val palette = buildWebSafePlusGrayPalette()
        val paletteBytes = ByteArray(256 * 3)
        for (i in 0 until 256) {
            val c = palette[i]
            paletteBytes[i * 3] = ((c ushr 16) and 0xFF).toByte()
            paletteBytes[i * 3 + 1] = ((c ushr 8) and 0xFF).toByte()
            paletteBytes[i * 3 + 2] = (c and 0xFF).toByte()
        }
        val baos = ByteArrayOutputStream(rgbFrames.size * outW * outH / 3 + 1024)
        writeHeaderAndScreen(baos, outW, outH, paletteBytes)
        if (loop) writeNetscapeLoopExtension(baos)
        val indices = ByteArray(outW * outH)
        for (pixels in rgbFrames) {
            quantizeOrdered(pixels, outW, outH, indices)
            writeGraphicControlExt(baos, delayCs)
            writeImageBlock(baos, outW, outH, indices)
        }
        baos.write(0x3B)
        return baos.toByteArray()
    }

    private fun buildWebSafePlusGrayPalette(): IntArray {
        val pal = IntArray(256)
        var idx = 0
        val levels = intArrayOf(0, 51, 102, 153, 204, 255)
        for (r in levels) {
            for (g in levels) {
                for (b in levels) {
                    pal[idx++] = (r shl 16) or (g shl 8) or b
                }
            }
        }
        // Remaining 40 slots: grayscale steps
        for (i in 0 until 40) {
            val v = (i * 255) / 39
            pal[idx++] = (v shl 16) or (v shl 8) or v
        }
        return pal
    }

    private val bayer4x4 = intArrayOf(
        -24, 8, -16, 16,
        24, -8, 12, -20,
        -12, 20, -24, 8,
        16, -16, 20, -12
    )

    private fun quantizeOrdered(pixels: IntArray, w: Int, h: Int, out: ByteArray) {
        for (y in 0 until h) {
            val rowOff = y * w
            val by = (y and 3) shl 2
            for (x in 0 until w) {
                val d = bayer4x4[by + (x and 3)]
                val p = pixels[rowOff + x]
                val r = (((p ushr 16) and 0xFF) + d).coerceIn(0, 255)
                val g = (((p ushr 8) and 0xFF) + d).coerceIn(0, 255)
                val b = ((p and 0xFF) + d).coerceIn(0, 255)
                val ri = (r * 5 + 127) / 255
                val gi = (g * 5 + 127) / 255
                val bi = (b * 5 + 127) / 255
                out[rowOff + x] = (ri * 36 + gi * 6 + bi).toByte()
            }
        }
    }

    private fun writeShortLE(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun writeHeaderAndScreen(out: OutputStream, w: Int, h: Int, globalColorTable: ByteArray) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShortLE(out, w)
        writeShortLE(out, h)
        out.write(0xF7) // Global Color Table flag=1, color res=8, sort=0, size=2^(7+1)=256
        out.write(0)    // Background color index
        out.write(0)    // Pixel aspect ratio
        out.write(globalColorTable)
    }

    private fun writeNetscapeLoopExtension(out: OutputStream) {
        out.write(0x21)
        out.write(0xFF)
        out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3)
        out.write(1)
        writeShortLE(out, 0) // Loop indefinitely
        out.write(0)
    }

    private fun writeGraphicControlExt(out: OutputStream, delayCs: Int) {
        out.write(0x21)
        out.write(0xF9)
        out.write(4)
        out.write(0x04) // disposal=1, user input=0, transparent=0
        writeShortLE(out, delayCs)
        out.write(0)
        out.write(0)
    }

    private fun writeImageBlock(out: OutputStream, w: Int, h: Int, indices: ByteArray) {
        out.write(0x2C)
        writeShortLE(out, 0)
        writeShortLE(out, 0)
        writeShortLE(out, w)
        writeShortLE(out, h)
        out.write(0) // No local color table

        // Simple clear-code-resetting variable-width LZW stream (8-bit pixels -> min code size 8)
        out.write(8)
        val packet = ByteArray(256)
        var pktLen = 0
        var bitBuf = 0
        var bitCount = 0

        fun flushPacket() {
            if (pktLen > 0) {
                out.write(pktLen)
                out.write(packet, 0, pktLen)
                pktLen = 0
            }
        }

        fun emitCode(code: Int, width: Int) {
            bitBuf = bitBuf or (code shl bitCount)
            bitCount += width
            while (bitCount >= 8) {
                packet[pktLen++] = (bitBuf and 0xFF).toByte()
                if (pktLen == 254) flushPacket()
                bitBuf = bitBuf ushr 8
                bitCount -= 8
            }
        }

        val clearCode = 256
        val eoiCode = 257
        val tablePrefix = IntArray(4096)
        val tableSuffix = IntArray(4096)
        val tableNext = IntArray(4096)
        val hashHead = IntArray(5003)

        fun resetDict(): Pair<Int, Int> {
            hashHead.fill(-1)
            return 258 to 9
        }

        var (nextCode, codeWidth) = resetDict()
        emitCode(clearCode, codeWidth)

        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val k = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val hIdx = (key * 2654435761L ushr 19).toInt().and(0x7FFFFFFF) % hashHead.size
            var match = -1
            var cur = hashHead[hIdx]
            while (cur != -1) {
                if (tablePrefix[cur] == prefix && tableSuffix[cur] == k) {
                    match = cur
                    break
                }
                cur = tableNext[cur]
            }
            if (match != -1) {
                prefix = match
            } else {
                emitCode(prefix, codeWidth)
                if (nextCode < 4096) {
                    tablePrefix[nextCode] = prefix
                    tableSuffix[nextCode] = k
                    tableNext[nextCode] = hashHead[hIdx]
                    hashHead[hIdx] = nextCode
                    if (nextCode == (1 shl codeWidth) && codeWidth < 12) {
                        codeWidth++
                    }
                    nextCode++
                } else {
                    emitCode(clearCode, codeWidth)
                    val reset = resetDict()
                    nextCode = reset.first
                    codeWidth = reset.second
                }
                prefix = k
            }
        }
        emitCode(prefix, codeWidth)
        emitCode(eoiCode, codeWidth)
        if (bitCount > 0) {
            packet[pktLen++] = (bitBuf and 0xFF).toByte()
        }
        flushPacket()
        out.write(0) // Block terminator
    }
}
