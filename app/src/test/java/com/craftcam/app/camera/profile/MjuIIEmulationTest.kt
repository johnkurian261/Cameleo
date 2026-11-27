package com.craftcam.app.camera.profile

import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.color.digicam.DigicamIlluminant
import com.craftcam.app.color.profile.CameraProfileCompatibility
import com.craftcam.app.color.profile.DazzColorProfileCatalog
import com.craftcam.app.color.skin.SkinToneIntelligenceEngine
import com.craftcam.app.color.skin.SkinToneProfileCatalog
import com.craftcam.app.gl.shaders.ShaderConstants
import com.craftcam.app.model.BorderStyle
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.DateStampStyle
import com.craftcam.app.model.GrainSourceMode
import com.craftcam.app.model.WhiteBalancePolicy
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.processor.GrainStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MjuIIEmulationTest {

    @Test
    fun mju2IdentityAndOpticalSpec_match1997CompactFilmCamera() {
        val preset = PresetCatalog.getPresetById("analog_mju2")
        assertEquals("analog_mju2", preset.id)
        assertEquals("MJU II", preset.name)
        assertEquals("1997 Compact Film", preset.subtitle)
        assertEquals(CameraCategory.ANALOG_FILM, preset.category)
        assertEquals("1997", preset.year)
        assertEquals("cam_mju2", preset.iconResName)
        assertEquals(WhiteBalancePolicy.FIXED_DAYLIGHT, preset.whiteBalance.policy)
        assertEquals(5500, preset.whiteBalance.defaultKelvin)
        assertFalse(preset.dust?.enabled ?: true)
        assertFalse(preset.lightLeak?.enabled ?: true)
        assertFalse(preset.flashOverlay?.enabled ?: true)
        assertEquals(BorderStyle.CLASSIC_35MM_BLACK, preset.border?.style)
        assertEquals(DateStampStyle.SEVEN_SEGMENT_ORANGE, preset.dateStamp?.style)
        assertEquals(-29, preset.dateStamp?.customYearOffset)
        assertEquals(GrainSourceMode.PROCEDURAL_ONLY, preset.grain.sourceMode)

        // Alias resolution
        assertEquals("analog_mju2", PresetCatalog.getPresetById("mju2").id)
        assertEquals("analog_mju2", PresetCatalog.getPresetById("stylus_epic").id)
        assertEquals("analog_mju2", PresetCatalog.getPresetById("olympus_mju2").id)

        val profile = CameraProfileCatalog.getProfileForPreset(preset)
        assertEquals("mju_ii", profile.id)
        assertEquals(AspectRatioMode.RATIO_3_2, profile.defaultRatio)
        assertEquals(35f, profile.defaultFocalLengthMm, 1e-4f)
        assertNull("MJU II must not mount 3D stereoscopic metadata", profile.d3dProfile)
        assertFalse(profile.supportedLenses.contains(LensType.FISHEYE_F))
        assertFalse(profile.supportedLenses.contains(LensType.FISHEYE_W))
        assertTrue(profile.supportedLenses.contains(LensType.NORMAL))
        assertTrue(profile.supportedLenses.contains(LensType.COLOR_FLASH))

        // No duplicate contrast / saturation / temp multipliers stacked over LUT
        assertEquals(1.0f, profile.filmProfile.contrast, 1e-5f)
        assertEquals(1.0f, profile.filmProfile.saturation, 1e-5f)
        assertEquals(0.0f, profile.filmProfile.temperature, 1e-5f)
        assertEquals(0.0f, profile.filmProfile.tint, 1e-5f)
        assertEquals(0.0f, profile.filmProfile.fade, 1e-5f)
        assertEquals(LutInputSpace.SRGB, profile.filmProfile.lutInputSpace)
    }

    @Test
    fun mju2LutAndProfileCompatibility_preventsBypassDoubleGradeAndSupports22M() {
        val preset = PresetCatalog.getPresetById("analog_mju2")
        val profile = CameraProfileCatalog.getProfileForPreset(preset)
        assertEquals("lut_olympus_mju2.png", ShaderConstants.getLutAssetForPreset(preset.id))
        assertEquals("lut_olympus_mju2.png", profile.filmProfile.lutAssetPath)
        assertEquals("", ShaderConstants.getGrainAssetForPreset(preset.id, isCamcorder = false))

        // Default C400 preserves 100% of lut_olympus_mju2.png without extra profile adjustment
        val c400 = DazzColorProfileCatalog.getById("C400")
        val defaultPlan = CameraProfileCompatibility.resolveLutPlan(
            presetId = preset.id,
            colorProfileIndex = c400.index,
            cameraLutAsset = profile.filmProfile.lutAssetPath
        )
        assertEquals("lut_olympus_mju2.png", defaultPlan.primaryLutAsset)
        assertNull(defaultPlan.secondaryLutAsset)
        assertEquals(0f, defaultPlan.dualLutMix, 1e-5f)
        assertFalse(defaultPlan.isCustomProfile)
        assertFalse(defaultPlan.isMonochrome)
        assertEquals(1.0f, defaultPlan.effectiveAdjustment.contrastMult, 1e-5f)

        // EG200, FXN2, 03G blend smoothly via secondary LUT without erasing MJU II tone base
        for (pid in listOf("EG200", "FXN2", "03G")) {
            val prof = DazzColorProfileCatalog.getById(pid)
            val plan = CameraProfileCompatibility.resolveLutPlan(
                presetId = preset.id,
                colorProfileIndex = prof.index,
                cameraLutAsset = profile.filmProfile.lutAssetPath
            )
            assertEquals("lut_olympus_mju2.png", plan.primaryLutAsset)
            assertEquals(prof.lutAssetPath, plan.secondaryLutAsset)
            assertEquals(0.65f, plan.dualLutMix, 1e-5f)
            assertTrue(plan.isCustomProfile)
            assertFalse(plan.isMonochrome)
        }

        // 22M monochrome enforces strict achromatic output
        val bw22m = DazzColorProfileCatalog.getById("22M")
        val bwPlan = CameraProfileCompatibility.resolveLutPlan(
            presetId = preset.id,
            colorProfileIndex = bw22m.index,
            cameraLutAsset = profile.filmProfile.lutAssetPath
        )
        assertEquals("lut_profile_22m.png", bwPlan.primaryLutAsset)
        assertNull(bwPlan.secondaryLutAsset)
        assertTrue(bwPlan.isMonochrome)

        // Procedural-only grain discipline (zero scanned plate double stacking)
        val grain = GrainStage.resolve(preset, profile.grainProfile, UserTuningConfig(), intensityFactor = 1.0f)
        assertEquals(GrainMode.PROCEDURAL, grain.mode)
        assertFalse(grain.useScannedTexture)
        assertEquals(0.14f, grain.proceduralIntensity, 1e-5f)
        assertFalse(grain.isColorNoise)
    }

    @Test
    fun mju2Optics_cleanerThanDisposablesAndLeavesExistingPresetsIntact() {
        val mju2 = CameraProfileCatalog.MJU_II
        val dfuns = CameraProfileCatalog.DISPOSABLE_FUNS
        val dqs = CameraProfileCatalog.DQS_CAMERA
        val cpm35 = CameraProfileCatalog.CPM_35

        assertTrue(mju2.opticalProfile.edgeSoftness < dfuns.opticalProfile.edgeSoftness.coerceAtLeast(0.05f))
        assertTrue(mju2.opticalProfile.vignetteStrength < cpm35.opticalProfile.vignetteStrength.coerceAtLeast(0.20f))
        assertTrue(mju2.opticalProfile.chromaticAberration < 0.002f)

        // Existing preset regression sanity
        assertEquals(35, PresetCatalog.allPresets.size)
        assertEquals("lut_disp_classic.png", CameraProfileCatalog.DISPOSABLE_FUNS.filmProfile.lutAssetPath)
        assertEquals("lut_dfuns_v1.png", CameraProfileCatalog.D_FUNS.filmProfile.lutAssetPath)
        assertEquals("lut_dqs_preserved.png", dqs.filmProfile.lutAssetPath)
        assertEquals("lut_disp_flash.png", cpm35.filmProfile.lutAssetPath)
        assertNotEquals("lut_olympus_mju2.png", CameraProfileCatalog.D_CLASSIC.filmProfile.lutAssetPath)
    }

    @Test
    fun mju2ShippedPngLut_neutralMonotonicAndColorimetricIntegrity() {
        val hald = loadRealHaldLutPixels("lut_olympus_mju2.png")
        assertEquals(512 * 512, hald.size)

        // 1. Monotonicity, black definition, highlight headroom, green-black shadow split toning
        var prevOut = -1f
        for (i in 0..255) {
            val v = i / 255f
            val (lr, lg, lb) = sampleHaldTrilinear(hald, v, v, v)
            val (fr, fg, fb) = applyRendererTail(lr, lg, lb, rollOff = 0.92f, shoulderClip = 0.992f)
            assertTrue("Transfer curve must be monotonic at step $i ($prevOut -> $fr)", fr >= prevOut - 1e-4f)
            prevOut = fr

            // In deep shadows (step 10..35), verify calibrated forest-green shadow undertone (G >= R)
            if (i in 10..35) {
                assertTrue("Shadow undertone must exhibit subtle green-black richness (lg >= lr)", lg >= lr - 1e-4f)
            }
        }

        val (b0, _, _) = applyRendererTail(0f, 0f, 0f, rollOff = 0.92f, shoulderClip = 0.992f)
        assertTrue("Defined film black toe (< 0.05)", b0 < 0.05f)
        val whiteSample = sampleHaldTrilinear(hald, 1f, 1f, 1f)
        val (w255, _, _) = applyRendererTail(whiteSample.first, whiteSample.second, whiteSample.third, rollOff = 0.92f, shoulderClip = 0.992f)
        assertTrue("Preserved creamy highlights without hard clipping ($w255)", w255 in 0.91f..0.985f)

        // 2. Multi-complexion skin & flash highlight preservation (no red clipping or mustard collapse)
        val flashSkin = sampleHaldTrilinear(hald, 0.93f, 0.78f, 0.68f)
        val (fsr, fsg, fsb) = applyRendererTail(flashSkin.first, flashSkin.second, flashSkin.third, rollOff = 0.89f, shoulderClip = 0.992f)
        assertTrue("Flash highlight must not hard-clip R ($fsr)", fsr < 0.98f)
        assertTrue("Flash skin keeps R > G > B without yellow inversion", fsr > fsg && fsg > fsb)
        assertTrue("Flash skin preserves blue channel headroom", fsb > fsr * 0.60f)

        // 3. Sky blue stays rich blue without cyan dominance over blue
        val sky = sampleHaldTrilinear(hald, 0.29f, 0.53f, 0.85f)
        assertTrue("Sky output remains blue-dominant", sky.third > sky.second && sky.second > sky.first)
    }

    @Test
    fun mju2SkinToneIntelligence_preservesComplexionsUnderDaylightAndFlash() {
        val skinProfile = SkinToneProfileCatalog.getProfileForPreset("analog_mju2", CameraCategory.ANALOG_FILM)
        assertEquals("skin_mju2", skinProfile.id)

        val w = 160
        val h = 120
        val warmSkinPx = (0xFF shl 24) or (215 shl 16) or (162 shl 8) or 134
        val pixels = IntArray(w * h) { warmSkinPx }

        val engine = SkinToneIntelligenceEngine()
        val daylightRes = engine.process(
            pixels = pixels.copyOf(),
            width = w,
            height = h,
            illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
            profile = skinProfile,
            isFlashActive = false
        )
        assertEquals("skin_mju2", daylightRes.profileApplied.id)
        assertTrue(daylightRes.skinPixelCount > 0)

        val flashBuf = IntArray(w * h) { (0xFF shl 24) or (242 shl 16) or (202 shl 8) or 172 }
        val flashRes = engine.process(
            pixels = flashBuf,
            width = w,
            height = h,
            illuminant = DigicamIlluminant.GOLDEN_DIRECT_FLASH,
            profile = skinProfile.copy(skinHighlightKnee = 0.84f),
            isFlashActive = true
        )
        assertTrue(flashRes.skinPixelCount > 0)
        val outPx = flashBuf[(h / 2) * w + (w / 2)]
        val rOut = (outPx shr 16) and 0xFF
        val gOut = (outPx shr 8) and 0xFF
        val bOut = outPx and 0xFF
        assertTrue("Flash skin roll-off protects against R=255 blowout (got $rOut)", rOut < 252)
        assertTrue("Flash skin keeps R > G > B ordering ($rOut, $gOut, $bOut)", rOut > gOut && gOut > bOut)
    }

    // ─── Pure-Kotlin PNG Hald-8 Reader & Renderer Parity Helpers ─────────────

    private fun applyRendererTail(rIn: Float, gIn: Float, bIn: Float, rollOff: Float, shoulderClip: Float): Triple<Float, Float, Float> {
        val luma = 0.2126f * rIn + 0.7152f * gIn + 0.0722f * bIn
        val sLift = 0.035f * (1f - luma.coerceIn(0f, 1f)) * (1f - luma.coerceIn(0f, 1f))
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
            val desat = highOver.coerceIn(0f, 1f) * 0.45f
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
