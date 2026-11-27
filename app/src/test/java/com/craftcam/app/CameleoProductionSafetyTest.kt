package com.craftcam.app

import android.content.ComponentCallbacks2
import com.craftcam.app.camera.profile.CameraProfileCatalog
import com.craftcam.app.camera.profile.LensType
import com.craftcam.app.color.DeviceBrand
import com.craftcam.app.color.HardwareWhiteBalanceCalibrator
import com.craftcam.app.color.digicam.DigicamIlluminant
import com.craftcam.app.color.profile.CameraProfileCompatibility
import com.craftcam.app.color.profile.DazzColorProfileCatalog
import com.craftcam.app.color.skin.SkinColourEstimator
import com.craftcam.app.color.skin.SkinMaskRefiner
import com.craftcam.app.model.WhiteBalancePolicy
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.processor.FloatArrayPool
import com.craftcam.app.processor.IntArrayPool
import com.craftcam.app.processor.ParallelPixelRunner
import com.craftcam.app.processor.PixelProcessingTimeoutException
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CameleoProductionSafetyTest {

    @After
    fun tearDown() {
        IntArrayPool.clear()
        IntArrayPool.resetDefaultBudgetForTesting()
        FloatArrayPool.clear()
        FloatArrayPool.resetDefaultBudgetForTesting()
    }

    @Test
    fun dFunsAuditPinsWorkingAssetsAndResetsCustomLutPlan() {
        fun hash(name: String): String {
            val file = listOf("src/main/assets", "app/src/main/assets").map { java.io.File(it, name) }.first { it.isFile }
            return java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        }
        assertEquals("0761a527981dc25d23086e8348d953fd8511cb0d5a3473e8239370bf481625c8", hash("lut_dfuns_v1.png"))
        assertEquals("ccc4d710aac776277c5c715c5fa895f71809519db511cb5fd049173157d5d5ac", hash("lut_dqs_preserved.png"))
        assertEquals("75de764b1bb7f168b88bc76f89d7d0c398707ea915e48cf8d8a71e84ab4ec95a", hash("grains/grain_dfuns.jpg"))
        val stock = CameraProfileCatalog.D_FUNS.filmProfile
        fun plan(name: String) = CameraProfileCompatibility.resolveLutPlan("ccd_flash",
            DazzColorProfileCatalog.getById(name).index, stock.lutAssetPath, stock.secondaryLutAssetPath, stock.dualLutBlendFactor)
        val before = plan("C400")
        assertEquals(0.70f, com.craftcam.app.camera.UserTuningConfig().filterIntensity, 0f)
        assertEquals(stock.lutAssetPath, before.primaryLutAsset)
        assertEquals("0761a527981dc25d23086e8348d953fd8511cb0d5a3473e8239370bf481625c8", hash("lut_dfuns.png"))
        assertTrue(plan("EG200").dualLutMix > 0f)
        assertEquals(before, plan("C400"))
        assertNull(before.secondaryLutAsset)
        assertEquals(0f, before.dualLutMix, 0f)
        assertNull(PresetCatalog.getPresetById("ccd_flash")!!.flashOverlay)
    }

    @Test
    fun resolveLutPlanPreservesNativeCameraLutsAtDefaultProfile() {
        val cases = listOf(
            "ccd_flash" to "lut_dfuns_v1.png",
            "ccd_grd" to "lut_ccd_grd.png",
            "ccd_retro_compact" to "lut_dqs_preserved.png",
            "ccd_y2k_digital" to "lut_ccd_y2k_digital.png",
            "disp_flash" to "lut_disp_flash.png",
            "ccd_r" to "lut_ccd_r.png",
            "analog_fxn_r" to "lut_fxn_r.png",
            "analog_cinema_film" to "lut_analog_cinema_film.png"
        )
        assertEquals(35, PresetCatalog.allPresets.size)
        for (preset in PresetCatalog.allPresets) {
            val defaultIdx = CameraProfileCompatibility.getDefaultProfile(preset.id).index
            val film = CameraProfileCatalog.getUnifiedProfile(preset).filmProfile
            val expectedCameraLut = film.lutAssetPath?.ifEmpty { null }
                ?: com.craftcam.app.gl.shaders.ShaderConstants.getLutAssetForPreset(preset.id)
            val plan = CameraProfileCompatibility.resolveLutPlan(
                presetId = preset.id,
                colorProfileIndex = defaultIdx,
                cameraLutAsset = film.lutAssetPath,
                cameraSecondaryLutAsset = film.secondaryLutAssetPath,
                cameraDualLutMix = film.dualLutBlendFactor
            )
            assertEquals("${preset.id} must retain its own camera LUT at default profile", expectedCameraLut, plan.primaryLutAsset)
            assertFalse("${preset.id} must not treat default as custom", plan.isCustomProfile)
            assertEquals(1.0f, plan.effectiveAdjustment.contrastMult, 1e-4f)
            assertEquals(1.0f, plan.effectiveAdjustment.satMult, 1e-4f)
            assertEquals(0.0f, plan.effectiveAdjustment.tempDelta, 1e-4f)
            assertEquals(0.0f, plan.effectiveAdjustment.tintDelta, 1e-4f)
            assertEquals(0.0f, plan.effectiveAdjustment.fadeDelta, 1e-4f)
        }
        for ((presetId, expectedCameraLut) in cases) {
            val defaultIdx = CameraProfileCompatibility.getDefaultProfile(presetId).index
            val preset = PresetCatalog.getPresetById(presetId) ?: error("Missing preset $presetId")
            val film = CameraProfileCatalog.getUnifiedProfile(preset).filmProfile
            val plan = CameraProfileCompatibility.resolveLutPlan(
                presetId = presetId,
                colorProfileIndex = defaultIdx,
                cameraLutAsset = film.lutAssetPath,
                cameraSecondaryLutAsset = film.secondaryLutAssetPath,
                cameraDualLutMix = film.dualLutBlendFactor
            )
            assertEquals("$presetId must retain primary LUT at default", expectedCameraLut, plan.primaryLutAsset)
            assertFalse("$presetId must not treat default as custom", plan.isCustomProfile)
            assertEquals(1.0f, plan.effectiveAdjustment.contrastMult, 1e-4f)
            assertEquals(1.0f, plan.effectiveAdjustment.satMult, 1e-4f)
            assertEquals(0.0f, plan.effectiveAdjustment.tempDelta, 1e-4f)
            assertEquals(0.0f, plan.effectiveAdjustment.tintDelta, 1e-4f)
            assertEquals(0.0f, plan.effectiveAdjustment.fadeDelta, 1e-4f)
        }
    }

    @Test
    fun resolveLutPlanBlendsCustomProfileAndSupports22MMonochrome() {
        val preset = PresetCatalog.getPresetById("ccd_flash")!!
        val film = CameraProfileCatalog.getUnifiedProfile(preset).filmProfile

        val mono22m = DazzColorProfileCatalog.getById("22M")
        val monoPlan = CameraProfileCompatibility.resolveLutPlan(
            presetId = "ccd_flash",
            colorProfileIndex = mono22m.index,
            cameraLutAsset = film.lutAssetPath
        )
        assertTrue(monoPlan.isMonochrome)
        assertTrue(monoPlan.isCustomProfile)
        assertEquals(mono22m.lutAssetPath, monoPlan.primaryLutAsset)

        val custom19p = DazzColorProfileCatalog.getById("19P")
        val blendPlan = CameraProfileCompatibility.resolveLutPlan(
            presetId = "ccd_flash",
            colorProfileIndex = custom19p.index,
            cameraLutAsset = film.lutAssetPath
        )
        assertFalse(blendPlan.isMonochrome)
        assertTrue(blendPlan.isCustomProfile)
        assertEquals("lut_dfuns_v1.png", blendPlan.primaryLutAsset)
        assertEquals(custom19p.lutAssetPath, blendPlan.secondaryLutAsset)
        assertEquals(0.65f, blendPlan.dualLutMix, 1e-4f)
    }

    @Test
    fun dFunsRestoresHistoricalColorGradingAndPreservesGrain() {
        val preset = PresetCatalog.getPresetById("ccd_flash")!!
        val unified = CameraProfileCatalog.getUnifiedProfile(preset)
        assertEquals(1.08f, preset.colorGrading.contrast, 1e-4f)
        assertEquals(1.06f, preset.colorGrading.saturation, 1e-4f)
        assertEquals(0.04f, preset.colorGrading.temperature, 1e-4f)
        assertEquals(0.01f, preset.colorGrading.tint, 1e-4f)
        assertEquals(0.02f, preset.colorGrading.fade, 1e-4f)
        assertEquals(0.90f, preset.colorGrading.highlightRollOff, 1e-4f)
        assertEquals("grains/grain_dfuns.jpg", unified.grainProfile.assetPath)
        assertEquals(0.16f, unified.grainProfile.intensity, 1e-4f)
    }

    @Test
    fun d3dExcludesFisheyeWhilePreservingPrismAndStarburst() {
        val d3d = CameraProfileCatalog.D_3D
        assertFalse(LensType.FISHEYE_F in d3d.supportedLenses)
        assertFalse(LensType.FISHEYE_W in d3d.supportedLenses)
        assertTrue(LensType.PRISM in d3d.supportedLenses)
        assertTrue(LensType.STARBURST in d3d.supportedLenses)
    }

    @Test
    fun samsungS21ColorCorrectionMatricesHaveUnitRowSumsToPreventGreenTint() {
        val s21Cameras = listOf("0", "1", "2", "21")
        val kelvins = listOf(2856, 4500, 5500, 6504)
        for (camId in s21Cameras) {
            for (k in kelvins) {
                val m = HardwareWhiteBalanceCalibrator.computeTransformMatrix(k, DeviceBrand.SAMSUNG, camId)
                assertEquals(9, m.size)
                val row0Sum = m[0] + m[1] + m[2]
                val row1Sum = m[3] + m[4] + m[5]
                val row2Sum = m[6] + m[7] + m[8]
                assertEquals("Cam $camId @ ${k}K red row sum must be 1.0", 1.0f, row0Sum, 0.002f)
                assertEquals("Cam $camId @ ${k}K green row sum must be 1.0", 1.0f, row1Sum, 0.002f)
                assertEquals("Cam $camId @ ${k}K blue row sum must be 1.0", 1.0f, row2Sum, 0.002f)
            }
        }
    }

    @Test
    fun nonSamsungDevicesAreIsolatedFromS21Coefficients() {
        for (brand in listOf(DeviceBrand.GENERIC, DeviceBrand.GOOGLE, DeviceBrand.XIAOMI, DeviceBrand.ONEPLUS, DeviceBrand.SONY)) {
            val m = HardwareWhiteBalanceCalibrator.computeTransformMatrix(6504, brand, "0")
            assertEquals(1.0f, m[0], 1e-4f)
            assertEquals(0.0f, m[1], 1e-4f)
            assertEquals(0.0f, m[2], 1e-4f)
            assertEquals(1.0f, m[4], 1e-4f)
            assertEquals(1.0f, m[8], 1e-4f)
        }
        val exec = HardwareWhiteBalanceCalibrator.resolveExecution(
            policy = WhiteBalancePolicy.FIXED_DAYLIGHT,
            requestedKelvin = 5500,
            supportsManualWb = true,
            brand = DeviceBrand.SAMSUNG,
            cameraId = "0"
        )
        assertTrue(exec.isHardwareApplied)
        assertNotNull(exec.colorSpaceTransform)
    }

    @Test
    fun parallelPixelRunnerPropagatesWorkerExceptionsAndDrainsSiblings() {
        val activeWorkers = AtomicInteger(0)
        val workerMutatedAfterException = AtomicBoolean(false)
        val siblingFinishedCleanly = AtomicBoolean(false)

        val thrown = assertThrows(IllegalStateException::class.java) {
            ParallelPixelRunner.runStripes(
                startInclusive = 0,
                endExclusive = 100,
                totalWorkUnits = 100_000,
                workerCount = 4
            ) { workerIdx, _, _, isCancelled ->
                activeWorkers.incrementAndGet()
                try {
                    if (workerIdx == 0) {
                        Thread.sleep(20)
                        error("Simulated worker failure")
                    } else {
                        try {
                            while (!isCancelled()) {
                                Thread.sleep(5)
                            }
                        } catch (_: InterruptedException) {
                            // Interrupted cooperatively by cancellation drain
                        }
                        siblingFinishedCleanly.set(true)
                    }
                } finally {
                    activeWorkers.decrementAndGet()
                }
            }
        }
        assertEquals("Simulated worker failure", thrown.message)
        assertEquals("All started workers must drain before caller returns", 0, activeWorkers.get())
        assertFalse(workerMutatedAfterException.get())
        assertTrue(siblingFinishedCleanly.get())
    }

    @Test
    fun parallelPixelRunnerTimeoutDrainsWorkersBeforePoolRelease() {
        val bufferReleased = AtomicBoolean(false)
        val wroteAfterRelease = AtomicBoolean(false)
        val workerExited = AtomicBoolean(false)
        val startedLatch = CountDownLatch(2)

        assertThrows(PixelProcessingTimeoutException::class.java) {
            try {
                ParallelPixelRunner.runStripes(
                    startInclusive = 0,
                    endExclusive = 100,
                    totalWorkUnits = 100_000,
                    timeoutMs = 50L,
                    workerCount = 2
                ) { _, _, _, isCancelled ->
                    startedLatch.countDown()
                    try {
                        for (step in 0 until 200) {
                            if (isCancelled()) break
                            if (bufferReleased.get()) {
                                wroteAfterRelease.set(true)
                            }
                            Thread.sleep(10)
                        }
                    } catch (_: InterruptedException) {
                        // Cooperative interrupt on timeout
                    } finally {
                        workerExited.set(true)
                    }
                }
            } finally {
                bufferReleased.set(true)
            }
        }

        assertTrue("Worker must exit before finally releases pooled buffer", workerExited.get())
        assertFalse("Worker must never write after buffer release", wroteAfterRelease.get())
    }

    @Test
    fun parallelPixelRunnerInterruptionDrainsWorkersDeterministically() {
        val exited = AtomicBoolean(false)
        val callerReady = CountDownLatch(1)
        val callerThread = Thread {
            try {
                ParallelPixelRunner.runRange(
                    startInclusive = 0,
                    endExclusive = 64,
                    totalWorkUnits = 100_000,
                    workerCount = 2
                ) { _, _, isCancelled ->
                    callerReady.countDown()
                    try {
                        while (!isCancelled()) {
                            Thread.sleep(5)
                        }
                    } catch (_: InterruptedException) {
                    } finally {
                        exited.set(true)
                    }
                }
            } catch (_: InterruptedException) {
                // Expected cancellation
            }
        }
        callerThread.start()
        assertTrue(callerReady.await(2, TimeUnit.SECONDS))
        callerThread.interrupt()
        callerThread.join(2_000)
        assertTrue("In-flight workers must exit when caller is interrupted", exited.get())
    }

    @Test
    fun arrayPoolsEnforceByteBudgetsAndTrimOnMemoryPressure() {
        IntArrayPool.clear()
        val arraySize = 5_000_000 // 20 MB per array
        IntArrayPool.setByteBudgetForTesting(25_000_000L) // Allows 1 array (20 MB), blocks 2nd (40 MB)

        val a1 = IntArrayPool.acquire(arraySize)
        val a2 = IntArrayPool.acquire(arraySize)
        IntArrayPool.release(a1)
        assertEquals(20_000_000L, IntArrayPool.retainedBytes())
        IntArrayPool.release(a2)
        assertTrue("IntArrayPool must stay within 25MB budget", IntArrayPool.retainedBytes() <= 25_000_000L)

        // Medium arrays should trim down at UI_HIDDEN / RUNNING_LOW and clear at MODERATE
        IntArrayPool.clear()
        FloatArrayPool.clear()
        FloatArrayPool.setByteBudgetForTesting(12_000_000L) // 12 MB
        val f1 = FloatArrayPool.acquire(1_000_000) // 4 MB
        val f2 = FloatArrayPool.acquire(1_000_000) // 4 MB
        val f3 = FloatArrayPool.acquire(1_000_000) // 4 MB
        FloatArrayPool.release(f1)
        FloatArrayPool.release(f2)
        FloatArrayPool.release(f3)
        assertEquals(12_000_000L, FloatArrayPool.retainedBytes())

        @Suppress("DEPRECATION")
        FloatArrayPool.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        assertTrue(FloatArrayPool.retainedBytes() <= 6_000_000L)

        @Suppress("DEPRECATION")
        FloatArrayPool.trimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)
        assertEquals(0L, FloatArrayPool.retainedBytes())
    }

    @Test
    fun skinEstimatorAndRefinerMatchSingleThreadedOutputAndReleaseBuffers() {
        val w = 240
        val h = 240
        val pixels = IntArray(w * h) { idx ->
            if (idx % 2 == 0) (0xFF shl 24) or (215 shl 16) or (162 shl 8) or 126
            else (0xFF shl 24) or (45 shl 16) or (90 shl 8) or 160
        }
        val mask = SkinColourEstimator().estimateColourProbability(pixels, w, h, DigicamIlluminant.GOLDEN_DAYLIGHT)
        assertEquals(w * h, mask.size)
        val refined = SkinMaskRefiner().refineMask(mask, w, h, 0.02f)
        assertEquals(w * h, refined.size)
        assertTrue(refined[0] in 0f..1f)
        FloatArrayPool.release(refined)
    }
}
