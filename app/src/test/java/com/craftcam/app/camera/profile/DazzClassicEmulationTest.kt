package com.craftcam.app.camera.profile

import com.craftcam.app.camera.profile.GrainMode
import com.craftcam.app.color.profile.CameraProfileCompatibility
import com.craftcam.app.model.BorderStyle
import com.craftcam.app.model.GrainBlendMode
import com.craftcam.app.presets.PresetCatalog
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.max

/**
 * Architectural and colorimetric verification for DazzClassic / D Classic
 * (analog_35mm_classic) warm monochrome analog photo booth emulation.
 */
class DazzClassicEmulationTest {

    @Test
    fun dClassicProfile_propertiesAndAssetIntegrity() {
        val dClassic = CameraProfileCatalog.D_CLASSIC
        assertEquals("d_classic", dClassic.id)
        assertEquals("D Classic", dClassic.name)
        assertEquals("analog_35mm_classic", dClassic.presetId)

        // Film profile
        val film = dClassic.filmProfile
        assertEquals("lut_dazzclassic.png", film.lutAssetPath)
        assertEquals(0.025f, film.toeLift, 1e-4f)
        assertEquals(0.992f, film.shoulderClip, 1e-4f)
        assertEquals(0.92f, film.highlightRollOff, 1e-4f)
        assertEquals(0.82f, film.halationThreshold, 1e-4f)
        assertEquals(0.18f, film.halationIntensity, 1e-4f)
        assertEquals(1.0f, film.halationColorTintR, 1e-4f)
        assertEquals(0.88f, film.halationColorTintG, 1e-4f)
        assertEquals(0.70f, film.halationColorTintB, 1e-4f)

        // Grain profile
        val grain = dClassic.grainProfile
        assertEquals(GrainMode.SCANNED, grain.mode)
        assertEquals("grains/grain_dclassic.jpg", grain.assetPath)
        assertEquals(0.18f, grain.intensity, 1e-4f)
        assertFalse(grain.isColorNoise)

        // Preset properties
        val preset = PresetCatalog.getPresetById("analog_35mm_classic")
        assertNotNull(preset)
        assertEquals("cam_d_classic", preset.iconResName)
        assertEquals(0.0f, preset.colorGrading.saturation, 1e-4f)
        assertEquals(0.92f, preset.colorGrading.highlightRollOff, 1e-4f)
        assertEquals(BorderStyle.CLASSIC_35MM_RANGEFINDER, preset.border?.style)

        // Color Profile compatibility resolution
        val defaultProfile = CameraProfileCompatibility.getDefaultProfile("analog_35mm_classic")
        val lutPlan = CameraProfileCompatibility.resolveLutPlan("analog_35mm_classic", defaultProfile.index)
        assertEquals("lut_dazzclassic.png", lutPlan.primaryLutAsset)
        assertTrue(lutPlan.isMonochrome)
    }

    @Test
    fun dClassicOptics_cleanerThanDisposablesAndLeavesExistingPresetsIntact() {
        val dClassic = CameraProfileCatalog.D_CLASSIC
        val dfuns = CameraProfileCatalog.DISPOSABLE_FUNS
        val mju2 = CameraProfileCatalog.MJU_II
        val dqs = CameraProfileCatalog.DQS_CAMERA
        val cpm35 = CameraProfileCatalog.CPM_35

        assertTrue(dClassic.opticalProfile.edgeSoftness < dfuns.opticalProfile.edgeSoftness.coerceAtLeast(0.05f))
        assertTrue(dClassic.opticalProfile.vignetteStrength < cpm35.opticalProfile.vignetteStrength.coerceAtLeast(0.20f))

        // Existing preset regression sanity
        assertEquals(35, PresetCatalog.allPresets.size)
        assertEquals("lut_dfuns_v1.png", CameraProfileCatalog.D_FUNS.filmProfile.lutAssetPath)
        assertEquals("lut_dqs_preserved.png", dqs.filmProfile.lutAssetPath)
        assertEquals("lut_olympus_mju2.png", mju2.filmProfile.lutAssetPath)
        assertEquals("lut_disp_flash.png", cpm35.filmProfile.lutAssetPath)
        assertEquals("lut_ccd_r.png", CameraProfileCatalog.CCD_DIGITAL.filmProfile.lutAssetPath)
    }

    @Test
    fun dClassicShippedPngLut_monotonicityAndWarmMonochromeIntegrity() {
        val hald = loadRealHaldLutPixels("lut_dazzclassic.png")
        assertEquals(512 * 512, hald.size)

        // 1. Monotonicity and warm monochrome toning (R >= G >= B across all 256 steps)
        var prevOut = -1f
        for (i in 0..255) {
            val v = i / 255f
            val (lr, lg, lb) = sampleHaldTrilinear(hald, v, v, v)
            val (fr, fg, fb) = applyRendererTail(lr, lg, lb, rollOff = 0.92f, shoulderClip = 0.992f)
            assertTrue("Transfer curve must be monotonic at step $i ($prevOut -> $fr)", fr >= prevOut - 1e-4f)
            prevOut = fr

            // Warm monochrome print response: R >= G >= B
            assertTrue("Step $i must maintain warm monochrome balance (R >= G)", lr >= lg - 1e-4f)
            assertTrue("Step $i must maintain warm monochrome balance (G >= B)", lg >= lb - 1e-4f)
        }

        // 2. Defined black toe and creamy highlights
        val (b0, _, _) = applyRendererTail(0f, 0f, 0f, rollOff = 0.92f, shoulderClip = 0.992f)
        assertTrue("Defined film black toe (< 0.05, got $b0)", b0 < 0.05f)

        val whiteSample = sampleHaldTrilinear(hald, 1f, 1f, 1f)
        val (w255, _, _) = applyRendererTail(whiteSample.first, whiteSample.second, whiteSample.third, rollOff = 0.92f, shoulderClip = 0.992f)
        assertTrue("Preserved creamy ivory highlights ($w255)", w255 in 0.93f..0.985f)

        // 3. Multi-complexion skin tone luminance verification
        val fairSkin = sampleHaldTrilinear(hald, 0.88f, 0.75f, 0.67f)
        val oliveSkin = sampleHaldTrilinear(hald, 0.76f, 0.61f, 0.49f)
        val deepSkin = sampleHaldTrilinear(hald, 0.51f, 0.37f, 0.29f)

        assertTrue("Fair skin brighter than olive skin", fairSkin.first > oliveSkin.first)
        assertTrue("Olive skin brighter than deep skin", oliveSkin.first > deepSkin.first)
        assertTrue("Fair skin retains warm print toning", fairSkin.first >= fairSkin.second && fairSkin.second >= fairSkin.third)
    }

    // ─── Pure-Kotlin PNG Hald-8 Reader & Renderer Parity Helpers ─────────────

    private fun compressFilmHighlight(x: Float, rollOff: Float): Float {
        val t = (x - rollOff) / (1f - rollOff)
        return rollOff + (1f - rollOff) * (t / (1f + t))
    }

    private fun applyRendererTail(rIn: Float, gIn: Float, bIn: Float, rollOff: Float, shoulderClip: Float): Triple<Float, Float, Float> {
        val luma = 0.2126f * rIn + 0.7152f * gIn + 0.0722f * bIn
        val sLift = 0.025f * (1f - luma.coerceIn(0f, 1f)) * (1f - luma.coerceIn(0f, 1f))
        var r = rIn + sLift
        var g = gIn + sLift
        var b = bIn + sLift
        val maxC = max(r, max(g, b))
        if (maxC > rollOff) {
            val rolledMax = compressFilmHighlight(maxC, rollOff)
            val scale = rolledMax / maxC
            r *= scale; g *= scale; b *= scale
            val curLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val highOver = max(0f, curLuma - rollOff) / (1f - rollOff)
            val desat = highOver.coerceIn(0f, 1f) * 0.35f
            r = r * (1f - desat) + curLuma * desat
            g = g * (1f - desat) + curLuma * desat
            b = b * (1f - desat) + curLuma * desat
        }
        return Triple(
            (r.coerceIn(0f, 1f) * shoulderClip).coerceIn(0f, 1f),
            (g.coerceIn(0f, 1f) * shoulderClip).coerceIn(0f, 1f),
            (b.coerceIn(0f, 1f) * shoulderClip).coerceIn(0f, 1f)
        )
    }

    private fun sampleHaldTrilinear(lut: IntArray, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val rf = r.coerceIn(0f, 1f) * 63f
        val gf = g.coerceIn(0f, 1f) * 63f
        val bf = b.coerceIn(0f, 1f) * 63f
        val r0 = rf.toInt().coerceIn(0, 62); val r1 = r0 + 1; val rx = rf - r0
        val g0 = gf.toInt().coerceIn(0, 62); val g1 = g0 + 1; val gy = gf - g0
        val b0 = bf.toInt().coerceIn(0, 62); val b1 = b0 + 1; val bz = bf - b0

        fun sliceOff(bi: Int) = ((bi shr 3) * 64 * 512) + ((bi and 7) * 64)
        val row00 = sliceOff(b0) + g0 * 512; val row01 = sliceOff(b0) + g1 * 512
        val row10 = sliceOff(b1) + g0 * 512; val row11 = sliceOff(b1) + g1 * 512

        fun ch(c: Int, shift: Int) = ((c ushr shift) and 0xFF).toFloat()
        fun lerpCh(shift: Int): Float {
            val x00 = ch(lut[row00 + r0], shift) * (1f - rx) + ch(lut[row00 + r1], shift) * rx
            val x10 = ch(lut[row01 + r0], shift) * (1f - rx) + ch(lut[row01 + r1], shift) * rx
            val x01 = ch(lut[row10 + r0], shift) * (1f - rx) + ch(lut[row10 + r1], shift) * rx
            val x11 = ch(lut[row11 + r0], shift) * (1f - rx) + ch(lut[row11 + r1], shift) * rx
            val y0 = x00 * (1f - gy) + x10 * gy
            val y1 = x01 * (1f - gy) + x11 * gy
            return (y0 * (1f - bz) + y1 * bz) / 255f
        }
        return Triple(lerpCh(16), lerpCh(8), lerpCh(0))
    }

    private fun loadRealHaldLutPixels(assetName: String): IntArray {
        val candidates = listOf(
            File("app/src/main/assets/$assetName"),
            File("src/main/assets/$assetName")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not find $assetName in ${candidates.map { it.absolutePath }}")

        val dis = DataInputStream(FileInputStream(file))
        val sig = ByteArray(8)
        dis.readFully(sig)
        var w = 0; var h = 0; var colorType = 0
        val idat = ByteArrayOutputStream()
        while (dis.available() > 0) {
            val len = dis.readInt()
            val typeBytes = ByteArray(4)
            dis.readFully(typeBytes)
            val type = String(typeBytes)
            val data = ByteArray(len)
            dis.readFully(data)
            dis.readInt() // CRC
            when (type) {
                "IHDR" -> {
                    val bb = java.nio.ByteBuffer.wrap(data)
                    w = bb.int; h = bb.int
                    bb.get() // bitDepth
                    colorType = bb.get().toInt() and 0xFF
                }
                "IDAT" -> idat.write(data)
                "IEND" -> break
            }
        }
        dis.close()

        val bpp = if (colorType == 6) 4 else 3
        val stride = w * bpp
        val raw = ByteArray((stride + 1) * h)
        val inflater = Inflater()
        inflater.setInput(idat.toByteArray())
        inflater.inflate(raw)
        inflater.end()

        val out = IntArray(w * h)
        val prevRow = IntArray(stride)
        val currRow = IntArray(stride)

        fun paeth(a: Int, b: Int, c: Int): Int {
            val p = a + b - c
            val pa = abs(p - a); val pb = abs(p - b); val pc = abs(p - c)
            return when {
                pa <= pb && pa <= pc -> a
                pb <= pc -> b
                else -> c
            }
        }

        for (y in 0 until h) {
            val rowStart = y * (stride + 1)
            val filter = raw[rowStart].toInt() and 0xFF
            for (i in 0 until stride) {
                val xVal = raw[rowStart + 1 + i].toInt() and 0xFF
                val a = if (i >= bpp) currRow[i - bpp] else 0
                val b = prevRow[i]
                val c = if (i >= bpp) prevRow[i - bpp] else 0
                currRow[i] = when (filter) {
                    0 -> xVal
                    1 -> (xVal + a) and 0xFF
                    2 -> (xVal + b) and 0xFF
                    3 -> (xVal + ((a + b) ushr 1)) and 0xFF
                    4 -> (xVal + paeth(a, b, c)) and 0xFF
                    else -> error("Unsupported PNG filter $filter")
                }
            }
            for (x in 0 until w) {
                val p = x * bpp
                val r = currRow[p]; val g = currRow[p + 1]; val b = currRow[p + 2]
                val a = if (bpp == 4) currRow[p + 3] else 255
                out[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            System.arraycopy(currRow, 0, prevRow, 0, stride)
        }
        return out
    }
}
