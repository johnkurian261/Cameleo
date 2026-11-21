package com.craftcam.app.threed

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer

class DepthAnythingV2NonSquareTest {

    @Test
    fun testNonSquareInput() {
        var modelFile = File("src/main/assets/models/depth_anything_v2_small.onnx")
        if (!modelFile.exists()) {
            modelFile = File("app/src/main/assets/models/depth_anything_v2_small.onnx")
        }
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions()
        val session = env.createSession(modelFile.absolutePath, opts)

        // 4:3 portrait: width = 392 (28*14), height = 518 (37*14)
        val w = 392
        val h = 518
        val floatCount = 1 * 3 * h * w
        val buffer = FloatBuffer.allocate(floatCount)
        for (i in 0 until floatCount) buffer.put(0.5f)
        buffer.flip()

        val tensor = OnnxTensor.createTensor(env, buffer, longArrayOf(1, 3, h.toLong(), w.toLong()))
        val startTime = System.currentTimeMillis()
        val results = session.run(mapOf("pixel_values" to tensor))
        val elapsed = System.currentTimeMillis() - startTime
        val outputTensor = results.get("predicted_depth").get() as OnnxTensor

        println("4:3 non-square test (${w}x${h}): Finished in ${elapsed}ms, output shape: ${outputTensor.info.shape.contentToString()}")

        org.junit.Assert.assertArrayEquals(longArrayOf(1, h.toLong(), w.toLong()), outputTensor.info.shape)

        results.close()
        tensor.close()
        session.close()
        env.close()
    }
}

