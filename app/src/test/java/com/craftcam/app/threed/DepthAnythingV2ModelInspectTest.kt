package com.craftcam.app.threed

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer

class DepthAnythingV2ModelInspectTest {

    @Test
    fun testInspectDepthAnythingModel() {
        var modelFile = File("src/main/assets/models/depth_anything_v2_small.onnx")
        if (!modelFile.exists()) {
            modelFile = File("app/src/main/assets/models/depth_anything_v2_small.onnx")
        }
        if (!modelFile.exists()) {
            println("Model file not found at: ${modelFile.absolutePath}")
            return
        }

        println("Loading ONNX model: ${modelFile.absolutePath} (${modelFile.length()} bytes)")
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        val session = env.createSession(modelFile.absolutePath, opts)

        for (dim in listOf(392, 518)) {
            val h = dim
            val w = dim
            val floatCount = 1 * 3 * h * w
            val buffer = FloatBuffer.allocate(floatCount)
            for (i in 0 until floatCount) buffer.put(0.5f)
            buffer.flip()

            val tensor = OnnxTensor.createTensor(env, buffer, longArrayOf(1, 3, h.toLong(), w.toLong()))
            val startTime = System.currentTimeMillis()
            val results = session.run(mapOf("pixel_values" to tensor))
            val elapsed = System.currentTimeMillis() - startTime
            val outputTensor = results.get("predicted_depth").get() as OnnxTensor
            println("Resolution ${w}x${h}: Inference in ${elapsed}ms, output shape: ${outputTensor.info.shape.contentToString()}")
            results.close()
            tensor.close()
        }

        session.close()
        env.close()
    }
}
