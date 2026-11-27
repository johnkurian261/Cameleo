package com.craftcam.app.threed

import android.graphics.RectF
import com.craftcam.app.portrait.FaceRegion
import java.nio.ByteBuffer
import java.io.File

import org.junit.Test
import org.junit.Assert.*

class D3DReplayTest {
    @Test fun replayFourDecodedInputs() {
        val root = listOf(File("docs/calibration/d3d"), File("../docs/calibration/d3d")).first { it.isDirectory }
        val regions = listOf(listOf(35,67,126,208),listOf(36,87,141,224),listOf(0,99,93,227),listOf(0,63,99,190))
        val engine = ThreeDDepthEngine()
        val warp = ParallaxWarpEngine(ThreeDConfig())
        for ((index,box) in regions.withIndex()) {
            val dir = File(root,(index+1).toString())
            val w = 800
            val h = 1072
            val buildDir = File(root,"../../../app/build/d3d-audit/"+(index+1)).apply { mkdirs() }
            val pixels = IntArray(w*h)
            ByteBuffer.wrap(File(buildDir,"input.argb").readBytes()).asIntBuffer().get(pixels)
            val rect = RectF().apply { left=box[0]/200f; top=box[1]/268f; right=box[2]/200f; bottom=box[3]/268f }
            val face = FaceRegion(rect,1f)
            val runtime = Runtime.getRuntime()
            fun usedHeap() = runtime.totalMemory() - runtime.freeMemory()
            val initialHeap = usedHeap()
            var samplePeak = initialHeap
            val start = System.nanoTime()
            val map = engine.computeDisparityFromPixels(pixels,w,h,listOf(face))
            assertEquals(DepthBackend.DEPTH_ANYTHING_V2,map.backendUsed)
            samplePeak = maxOf(samplePeak, usedHeap())
            val inferenceMs = (System.nanoTime()-start)/1_000_000
            File(buildDir,"depth.pgm").outputStream().buffered().use { output ->
                output.write("P5\n${map.width} ${map.height}\n255\n".toByteArray())
                output.write(ByteArray(map.values.size) { (255*map.values[it]).toInt().toByte() })
            }
            val geometryStart = System.nanoTime()
            val grid = warp.resampleDisparity(pixels,w,h,map)
            val baseline = warp.baselinePixels(w,map,face)
            for ((i,pos) in listOf(-1f,-.333f,.333f,1f).withIndex()) {
                val frame = warp.warpPixels(pixels,grid,w,h,baseline*pos,map.pivot)
                assertTrue(frame.all { (it ushr 24) == 255 })
                val buffer = ByteBuffer.allocate(frame.size*4)
                buffer.asIntBuffer().put(frame)
                File(buildDir,"view$i.argb").writeBytes(buffer.array())
                samplePeak = maxOf(samplePeak, usedHeap())
            }
            File(dir,"replay.csv").writeText("pivot,no_face_pivot,p10,p90,baseline_px,depth_ms,warp_and_io_ms,sampled_jvm_heap_delta_bytes\n"+
                "${map.pivot},${engine.calculatePivot(map.width,map.height,map.values,emptyList())},${map.backgroundDisparity},${map.foregroundDisparity},$baseline,$inferenceMs,${(System.nanoTime()-geometryStart)/1_000_000},${samplePeak-initialHeap}\n")
        }
    }
}
