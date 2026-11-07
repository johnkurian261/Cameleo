package com.craftcam.app.scene

import android.os.SystemClock
import com.craftcam.app.config.ProcessingProfile
import com.craftcam.app.config.SceneProcessingConfig
import com.craftcam.app.portrait.FaceRegion
import com.craftcam.app.portrait.HardwareFaceDetector

/**
 * Encapsulates the complete scene intelligence analysis payload.
 */
data class SceneAnalysisResult(
    val classification: SceneClassification,
    val lighting: LightingAnalysis,
    val dynamicRange: DynamicRangeAnalysis,
    val faces: List<FaceRegion>,
    val profile: ProcessingProfile,
    val analysisLatencyMs: Long
)

/**
 * Top-Level Scene Intelligence Coordinator.
 * Runs asynchronously during viewfinder preview (at 5-10 Hz) or upon photo capture.
 */
class SceneAnalysisEngine(
    private var config: SceneProcessingConfig = SceneProcessingConfig()
) {
    fun updateConfig(newConfig: SceneProcessingConfig) {
        this.config = newConfig
    }

    /**
     * Executes scene, lighting, dynamic range, and subject analysis on a downsampled frame.
     */
    fun analyze(
        downsampledPixels: IntArray,
        downsampledLuminance: FloatArray,
        width: Int,
        height: Int,
        histogram: IntArray,
        sensorIso: Int,
        exposureTimeNs: Long,
        overrideFaces: List<FaceRegion>? = null
    ): SceneAnalysisResult {
        val startTime = SystemClock.elapsedRealtime()

        // 1. Gather on-device detected faces (Hardware ISP or image fallback)
        val faces = overrideFaces ?: HardwareFaceDetector.getDetectedFaces()

        // 2. Analyze Lighting Conditions
        val lighting = LightingAnalyzer.analyze(
            luminanceArray = downsampledLuminance,
            width = width,
            height = height,
            sensorIso = sensorIso,
            exposureTimeNs = exposureTimeNs
        )

        // 3. Analyze Dynamic Range from 256-bin histogram
        val dynamicRange = DynamicRangeAnalyzer.analyzeHistogram(histogram)

        // 4. Analyze Subject & Composition
        val subject = SubjectAnalyzer.analyzeSubject(
            pixels = downsampledPixels,
            width = width,
            height = height,
            faces = faces
        )

        // 5. Synthesize Scene Classification
        val classification = SceneClassifier.classify(
            lighting = lighting,
            dynamicRange = dynamicRange,
            subject = subject,
            faces = faces,
            config = config
        )

        // 6. Formulate Optimal Processing Profile via Continuous Blending
        val profile = ProcessingDecisionEngine.decideProfile(
            classification = classification,
            lighting = lighting,
            dynamicRange = dynamicRange,
            faces = faces,
            config = config
        )

        val latency = SystemClock.elapsedRealtime() - startTime

        return SceneAnalysisResult(
            classification = classification,
            lighting = lighting,
            dynamicRange = dynamicRange,
            faces = faces,
            profile = profile,
            analysisLatencyMs = latency
        )
    }
}
