package com.craftcam.app.motion

import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.camera.profile.CameraProfileCatalog
import com.craftcam.app.camera.profile.GrainMode
import com.craftcam.app.model.BorderStyle
import com.craftcam.app.model.DateStampStyle
import com.craftcam.app.presets.PresetCatalog
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import kotlin.math.abs

class GolfMotionPhotoTest {

    @Test
    fun golfPreset_strictlyPreservesOriginalPhotographicIdentity() {
        val golf = PresetCatalog.getPresetById("disp_classic")
        assertEquals("Golf", golf.name)
        assertEquals(1.10f, golf.colorGrading.contrast, 1e-5f)
        assertEquals(1.15f, golf.colorGrading.saturation, 1e-5f)
        assertEquals(0.15f, golf.colorGrading.temperature, 1e-5f)
        assertEquals(-0.02f, golf.colorGrading.tint, 1e-5f)
        assertEquals(0.05f, golf.colorGrading.fade, 1e-5f)
        assertEquals(0.88f, golf.colorGrading.highlightRollOff, 1e-5f)

        assertTrue(golf.grain.enabled)
        assertEquals(0.28f, golf.grain.intensity, 1e-5f)
        assertEquals(1.20f, golf.grain.scale, 1e-5f)
        assertEquals("grains/grain_dexp2.jpg", golf.grain.assetPath)
        assertEquals(0.40f, golf.grain.shadowStrength, 1e-5f)
        assertEquals(0.18f, golf.grain.highlightStrength, 1e-5f)

        assertEquals(0.26f, golf.lens.vignetteIntensity, 1e-5f)
        assertEquals(0.84f, golf.lens.vignetteRadius, 1e-5f)
        assertEquals(0.007f, golf.lens.chromaticAberration, 1e-5f)
        assertEquals(0.10f, golf.lens.softness, 1e-5f)

        assertEquals(BorderStyle.GOLF_FRAME, golf.border?.style)
        assertEquals(DateStampStyle.SEVEN_SEGMENT_ORANGE, golf.dateStamp?.style)
        assertEquals(-31, golf.dateStamp?.customYearOffset)

        val profile = CameraProfileCatalog.getProfileForPreset(golf)
        assertEquals("lut_disp_classic.png", profile.filmProfile.lutAssetPath)
        assertEquals(GrainMode.SCANNED, profile.grainProfile.mode)
        assertEquals("grains/grain_dfuns.jpg", profile.grainProfile.assetPath)
    }

    @Test
    fun golfMotionConfig_supports1s2s3sAndVintageSmoothCadences() {
        val defaults = GolfMotionConfig()
        assertEquals(GolfMotionDuration.TWO_SEC, defaults.duration)
        assertEquals(GolfPlaybackCadence.VINTAGE, defaults.playback)
        assertEquals(1000L, GolfMotionDuration.ONE_SEC.durationMs)
        assertEquals(2000L, GolfMotionDuration.TWO_SEC.durationMs)
        assertEquals(3000L, GolfMotionDuration.THREE_SEC.durationMs)
        assertEquals(1000L, GolfMotionDuration.TWO_SEC.preShutterMs)
        assertEquals(1000L, GolfMotionDuration.TWO_SEC.postShutterMs)
        assertEquals(14, GolfPlaybackCadence.VINTAGE.targetFps)
        assertEquals(24, GolfPlaybackCadence.SMOOTH.targetFps)
        assertEquals(GolfMotionDuration.ONE_SEC, GolfMotionDuration.fromSeconds(1))
        assertEquals(GolfMotionDuration.THREE_SEC, GolfMotionDuration.fromSeconds(3))
        assertEquals(GolfPlaybackCadence.SMOOTH, GolfPlaybackCadence.fromName("SMOOTH"))
    }

    @Test
    fun rollingMotionRingBuffer_enforcesBoundedFramesBytesAndTimeWindow() {
        val ring = RollingMotionRingBuffer(
            maxFrames = 20,
            maxTotalBytes = 1024 * 1024, // 1 MB
            maxWindowNs = 1_500_000_000L
        )
        val payload = ByteArray(150 * 1024) { 0x5A } // 150 KB -> 1 MB cap allows <= 6 frames
        for (i in 0 until 40) {
            ring.push(
                timestampNs = i * 50_000_000L,
                width = 640,
                height = 480,
                jpegBytes = payload
            )
        }
        assertTrue("Frame count ${ring.frameCount} must remain bounded by byte budget", ring.frameCount <= 6)
        assertTrue("Retained bytes ${ring.retainedBytes} must remain <= 1 MB", ring.retainedBytes <= 1024 * 1024)

        val window = ring.snapshotWindow(1_800_000_000L, 1_950_000_000L)
        assertEquals(4, window.size)
        for (i in 1 until window.size) {
            assertTrue(window[i].timestampNs >= window[i - 1].timestampNs)
        }
    }

    @Test
    fun temporalEffectPlanner_vintageVsSmoothAndControlledShutterFlash() {
        val frames = (0 until 48).map { idx -> idx to (idx * 41_666_667L) }
        val shutterNs = 1_000_000_000L

        val vintage = GolfTemporalEffectPlanner.planTimeline(
            orderedFrames = frames,
            timestampNsOf = { it.second },
            shutterTimestampNs = shutterNs,
            targetDurationMs = 2000L,
            targetFps = GolfPlaybackCadence.VINTAGE.targetFps,
            flashEffectEnabled = true
        )
        assertEquals(28, vintage.frames.size)
        assertEquals(2000L, vintage.actualDurationMs)
        assertTrue("Shutter anchor should sit near middle", vintage.shutterOutputIndex in 11..16)
        assertTrue("Shutter frame should get brief flash pulse", vintage.params[vintage.shutterOutputIndex].flashBoostEv > 0.2f)
        assertEquals(0f, vintage.params.first().flashBoostEv, 1e-5f)
        assertEquals(0f, vintage.params.last().flashBoostEv, 1e-5f)
        assertTrue(vintage.params.all { abs(it.exposureFlickerEv) <= 0.026f })
        assertTrue("Grain seeds should animate across frames", vintage.params.map { it.grainSeed }.distinct().size > 10)

        val smoothNoFlash = GolfTemporalEffectPlanner.planTimeline(
            orderedFrames = frames,
            timestampNsOf = { it.second },
            shutterTimestampNs = shutterNs,
            targetDurationMs = 2000L,
            targetFps = GolfPlaybackCadence.SMOOTH.targetFps,
            flashEffectEnabled = false
        )
        assertEquals(48, smoothNoFlash.frames.size)
        assertTrue("Flash off should produce zero flash boost", smoothNoFlash.params.all { it.flashBoostEv == 0f })
    }

    @Test
    fun golfMotionEncoder_preservesSelectedAspectRatiosWithoutForced9x16() {
        // Landscape 4:3 (1440 x 1080, never forced 9:16)
        val l43 = GolfMotionEncoder.computeEncodedDimensions(1920, 1440, AspectRatioMode.RATIO_4_3, maxLongEdge = 1440)
        assertEquals(0, l43.first % 16)
        assertEquals(0, l43.second % 16)
        assertTrue(l43.first > l43.second)
        assertEquals(4f / 3f, l43.first.toFloat() / l43.second.toFloat(), 0.02f)

        // Portrait 4:3 (1088 x 1440, matching 3:4 still orientation)
        val p43 = GolfMotionEncoder.computeEncodedDimensions(1440, 1920, AspectRatioMode.RATIO_4_3, maxLongEdge = 1440)
        assertEquals(0, p43.first % 16)
        assertEquals(0, p43.second % 16)
        assertTrue(p43.second > p43.first)
        assertEquals(3f / 4f, p43.first.toFloat() / p43.second.toFloat(), 0.02f)

        // Square 1:1
        val sq = GolfMotionEncoder.computeEncodedDimensions(1440, 1440, AspectRatioMode.RATIO_1_1, maxLongEdge = 1440)
        assertEquals(sq.first, sq.second)
        assertEquals(0, sq.first % 16)

        // 16:9 & 3:2
        val l169 = GolfMotionEncoder.computeEncodedDimensions(1920, 1080, AspectRatioMode.RATIO_16_9, maxLongEdge = 1440)
        assertEquals(16f / 9f, l169.first.toFloat() / l169.second.toFloat(), 0.03f)
        val l32 = GolfMotionEncoder.computeEncodedDimensions(1500, 1000, AspectRatioMode.RATIO_3_2, maxLongEdge = 1440)
        assertEquals(3f / 2f, l32.first.toFloat() / l32.second.toFloat(), 0.02f)

        val uv = GolfMotionEncoder.computeCropUvRect(1920, 1080, 1440, 1088)
        assertEquals(4, uv.size)
        assertTrue(uv[0] >= 0f && uv[2] <= 1f)
        assertEquals(1f, uv[0] * 2f + uv[2], 1e-4f)
    }

    @Test
    fun motionPhotoImporter_parsesSamsungGoogleAndRejectsPlainStaticJpeg() {
        val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x00, 0x00, 0xFF.toByte(), 0xD9.toByte())
        val fakeMp4Header = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte()
        )
        val fakeMp4 = fakeMp4Header + ByteArray(96) { 0x22 }

        // 1. Plain static JPEG returns null (never fakes motion)
        assertNull(MotionPhotoImporter.findEmbeddedMp4Range(fakeJpeg))

        // 2. Google / Android Motion Photo XMP Container round-trip
        val googleBytes = GolfMotionStore.buildGoogleMotionPhotoBytes(fakeJpeg, fakeMp4)
        assertNotNull(googleBytes)
        val googleRange = MotionPhotoImporter.findEmbeddedMp4Range(googleBytes!!)
        assertNotNull(googleRange)
        assertEquals(fakeMp4.size, googleRange!!.second)
        assertArrayEquals(
            fakeMp4,
            googleBytes.copyOfRange(googleRange.first, googleRange.first + googleRange.second)
        )
        assertEquals("google_motion_photo", MotionPhotoImporter.detectContainerKind(googleBytes))

        // 3. Samsung MotionPhoto_Data + SEFH...SEFT trailer (and minimal SEFT trailer fallback)
        val marker = "MotionPhoto_Data".toByteArray(StandardCharsets.US_ASCII)
        val sefhTrailer = "SEFH\u0001\u0000\u0000\u0000SEFT".toByteArray(StandardCharsets.US_ASCII)
        val samsungBytes = fakeJpeg + marker + fakeMp4 + sefhTrailer
        val samsungRange = MotionPhotoImporter.findEmbeddedMp4Range(samsungBytes)
        assertNotNull(samsungRange)
        assertEquals(fakeMp4.size, samsungRange!!.second)
        assertArrayEquals(
            fakeMp4,
            samsungBytes.copyOfRange(samsungRange.first, samsungRange.first + samsungRange.second)
        )
        assertEquals("samsung_motion_photo", MotionPhotoImporter.detectContainerKind(samsungBytes))

        val minimalSeft = fakeJpeg + marker + fakeMp4 + "SEFT".toByteArray(StandardCharsets.US_ASCII)
        val minimalRange = MotionPhotoImporter.findEmbeddedMp4Range(minimalSeft)
        assertEquals(fakeMp4.size, minimalRange!!.second)
    }

    @Test
    fun golfGifExporter_generatesValidAnimatedGif89aContainer() {
        val w = 32
        val h = 24
        val f1 = IntArray(w * h) { 0xFFCC8844.toInt() }
        val f2 = IntArray(w * h) { 0xFF44AA66.toInt() }
        val gifBytes = GolfGifExporter.encodeRgbFramesToGifBytes(listOf(f1, f2), w, h, delayCs = 8, loop = true)

        assertTrue(gifBytes.size > 800)
        // GIF89a header + Logical Screen Descriptor width/height little-endian
        assertEquals("GIF89a", String(gifBytes, 0, 6, StandardCharsets.US_ASCII))
        assertEquals(w, (gifBytes[6].toInt() and 0xFF) or ((gifBytes[7].toInt() and 0xFF) shl 8))
        assertEquals(h, (gifBytes[8].toInt() and 0xFF) or ((gifBytes[9].toInt() and 0xFF) shl 8))
        assertEquals(0xF7.toByte(), gifBytes[10]) // 256-entry Global Color Table flag

        // NETSCAPE2.0 loop block + 2 Graphic Control Extensions (0x21 0xF9) + 2 Image Descriptors (0x2C) + Trailer (0x3B)
        val asciiHeader = String(gifBytes, 0, minOf(gifBytes.size, 820), StandardCharsets.ISO_8859_1)
        assertTrue(asciiHeader.contains("NETSCAPE2.0"))
        assertEquals(0x3B.toByte(), gifBytes.last())

        var gceCount = 0
        var imageBlockCount = 0
        for (idx in 780 until gifBytes.size - 1) {
            if (gifBytes[idx] == 0x21.toByte() && gifBytes[idx + 1] == 0xF9.toByte()) {
                gceCount++
            }
            if (gifBytes[idx] == 0x2C.toByte() &&
                idx + 9 < gifBytes.size &&
                gifBytes[idx + 1] == 0.toByte() &&
                gifBytes[idx + 2] == 0.toByte() &&
                gifBytes[idx + 5] == w.toByte() &&
                gifBytes[idx + 7] == h.toByte()
            ) {
                imageBlockCount++
            }
        }
        assertEquals(2, gceCount)
        assertEquals(2, imageBlockCount)
    }
}
