package com.craftcam.app.color.skin

import android.graphics.RectF
import com.craftcam.app.color.digicam.DigicamIlluminant
import com.craftcam.app.portrait.FaceRegion
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Performance & Latency Benchmark for Skin Tone Intelligence Engine.
 * Measures component-wise and end-to-end processing times across resolutions.
 */
class SkinToneBenchmarkTest {

    private lateinit var engine: SkinToneIntelligenceEngine

    @Before
    fun setUp() {
        engine = SkinToneIntelligenceEngine()
    }

    private fun generateSyntheticPortrait(width: Int, height: Int): IntArray {
        val total = width * height
        val pixels = IntArray(total)
        val rng = Random(42)

        for (y in 0 until height) {
            val ny = y.toFloat() / height
            val row = y * width
            for (x in 0 until width) {
                val nx = x.toFloat() / width

                // Central face region [0.35..0.65, 0.20..0.55]
                val isFace = nx in 0.35f..0.65f && ny in 0.20f..0.55f
                val isTorso = nx in 0.25f..0.75f && ny in 0.55f..0.90f

                val (r, g, b) = when {
                    isFace -> Triple(
                        rng.nextInt(190, 230),
                        rng.nextInt(140, 180),
                        rng.nextInt(100, 140)
                    )
                    isTorso -> Triple(
                        rng.nextInt(160, 200),
                        rng.nextInt(110, 150),
                        rng.nextInt(80, 110)
                    )
                    else -> Triple(
                        rng.nextInt(40, 120),
                        rng.nextInt(40, 120),
                        rng.nextInt(40, 120)
                    )
                }

                pixels[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return pixels
    }

    @Test
    fun benchmark_720pViewfinder() {
        val width = 1280
        val height = 720
        runBenchmarkSuite("720p Viewfinder (0.92 MP)", width, height, iterations = 5)
    }

    @Test
    fun benchmark_1080pStandard() {
        val width = 1920
        val height = 1080
        runBenchmarkSuite("1080p Standard (2.07 MP)", width, height, iterations = 5)
    }

    private fun runBenchmarkSuite(label: String, width: Int, height: Int, iterations: Int) {
        val total = width * height
        val pixels = generateSyntheticPortrait(width, height)
        val face = FaceRegion(
            bounds = RectF().apply { left = 0.35f; top = 0.20f; right = 0.65f; bottom = 0.55f },
            confidence = 0.95f
        )
        val faces = listOf(face)
        val illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT
        val profile = SkinToneProfileCatalog.CPM35

        // Warm up JIT
        engine.process(pixels.copyOf(), width, height, faces, illuminant, profile)

        var totalColorProbMs = 0.0
        var totalSpatialMs = 0.0
        var totalRefineMs = 0.0
        var totalProtectionMs = 0.0
        var totalEndToEndMs = 0.0

        for (iter in 0 until iterations) {
            val working = pixels.copyOf()

            // 1. Color Probability Latency
            val t0 = System.nanoTime()
            val colorProb = engine.colorEstimator.estimateColourProbability(working, width, height, illuminant)
            val t1 = System.nanoTime()
            val colorMs = (t1 - t0) / 1_000_000.0
            totalColorProbMs += colorMs

            // 2. Spatial Prior Latency
            val spatialPrior = engine.anatomyPrior.generateSpatialPrior(width, height, faces)
            val fused = engine.anatomyPrior.fuseConfidence(colorProb, spatialPrior)
            val t2 = System.nanoTime()
            val spatialMs = (t2 - t1) / 1_000_000.0
            totalSpatialMs += spatialMs

            // 3. Mask Refinement Latency
            val refined = engine.maskRefiner.refineMask(fused, width, height, profile.config.maskFeatherRadius)
            val t3 = System.nanoTime()
            val refineMs = (t3 - t2) / 1_000_000.0
            totalRefineMs += refineMs

            // 4. End-to-end full process
            val tStart = System.nanoTime()
            val result = engine.process(working, width, height, faces, illuminant, profile)
            val tEnd = System.nanoTime()
            val endToEndMs = (tEnd - tStart) / 1_000_000.0
            totalEndToEndMs += endToEndMs

            totalProtectionMs += (endToEndMs - colorMs - spatialMs - refineMs).coerceAtLeast(0.0)
        }

        val avgColor = totalColorProbMs / iterations
        val avgSpatial = totalSpatialMs / iterations
        val avgRefine = totalRefineMs / iterations
        val avgProtection = totalProtectionMs / iterations
        val avgTotal = totalEndToEndMs / iterations

        println("============================================================")
        println("BENCHMARK REPORT: $label")
        println("Pixels: $total (${width}x${height})")
        println("Layer 2 Color Probability (OKLCh + YCbCr): ${"%.2f".format(avgColor)} ms")
        println("Layer 3 Spatial Prior & Fusion:           ${"%.2f".format(avgSpatial)} ms")
        println("Edge-Aware Separable Mask Refinement:     ${"%.2f".format(avgRefine)} ms")
        println("Layer 4 Protection Blending:              ${"%.2f".format(avgProtection)} ms")
        println("Total End-to-End Pipeline Latency:        ${"%.2f".format(avgTotal)} ms")
        println("Throughput:                               ${"%.1f".format((total / 1_000_000.0) / (avgTotal / 1000.0))} Megapixels/sec")
        println("============================================================")

        assertTrue("End-to-end latency must be non-zero", avgTotal > 0.0)
    }
}
