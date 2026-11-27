package com.craftcam.app.threed

import android.graphics.RectF
import com.craftcam.app.portrait.FaceRegion
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Test

class HybridReplayTest {
    @Test fun compareFourScenes() {
        val root = listOf(File("."), File("..")).first { File(it, "docs/calibration/d3d").exists() }
        val warp = ParallaxWarpEngine()
        val engine = ThreeDDepthEngine()
        val boxes = listOf(listOf(35,67,126,208), listOf(36,87,141,224), listOf(0,99,93,227), listOf(0,63,99,190))
        for ((index, b) in boxes.withIndex()) {
            val out = File(root, "app/build/d3d-hybrid/${index+1}").apply { mkdirs() }
            val w = 800
            val h = 1072
            val pixels = IntArray(w*h)
            ByteBuffer.wrap(File(root,"app/build/d3d-audit/${index+1}/input.argb").readBytes()).asIntBuffer().get(pixels)
            val rect = RectF().apply { left=b[0]/200f; top=b[1]/268f; right=b[2]/200f; bottom=b[3]/268f }
            val face = FaceRegion(rect,1f)
            val map = engine.computeDisparityFromPixels(pixels,w,h,listOf(face))
            assertEquals(DepthBackend.DEPTH_ANYTHING_V2, map.backendUsed)
            val grid = warp.resampleDisparity(pixels,w,h,map)
            val base = warp.baselinePixels(w,map,face)
            // Parent of f7e0a45 sampled the middle half of the face on both axes.
            val cx = (rect.left+rect.right)/2
            val cy = (rect.top+rect.bottom)/2
            val rx = (rect.right-rect.left)/4
            val ry = (rect.bottom-rect.top)/4
            val samples = mutableListOf<Float>()
            for (y in ((cy-ry)*map.height).toInt()..((cy+ry)*map.height).toInt())
                for (x in ((cx-rx)*map.width).toInt()..((cx+rx)*map.width).toInt())
                    samples.add(map.getDisparity(x,y))
            val historicalPivot = samples.sorted()[samples.size/2]
            data class Candidate(val label:String,val baseline:Float,val pivot:Float,val gain:Float=0f)
            val cases = listOf(Candidate("A",w*.032f,historicalPivot),Candidate("B",base,map.pivot),
                Candidate("C",w*.032f,map.pivot),Candidate("D",base,historicalPivot),
                Candidate("E",base*1.5f,map.pivot),Candidate("F",base,map.pivot,ParallaxWarpEngine.backgroundGain(map)))
            val sweep = listOf(1.25f,1.75f,2f).map { Candidate("x$it",base*it,map.pivot) }
            val metrics = File(out,"edges.csv")
            metrics.writeText("candidate,view,interior_holes,border_holes,longest_interior_gap,folded_pairs,total_pixels\n")
            for (c in cases+sweep) {
                for ((i, offset) in listOf(-1f,-.333f,.333f,1f).withIndex()) {
                    val occupied = BooleanArray(w)
                    var interior = 0
                    var border = 0
                    var longest = 0
                    var folds = 0
                    for (y in 0 until h) {
                        occupied.fill(false)
                        var previous = Float.NEGATIVE_INFINITY
                        for (x in 0 until w) {
                            val dst = x + ParallaxWarpEngine.projectedOffset(grid[y*w+x],c.pivot,offset*c.baseline,c.gain)
                            if (dst < previous) folds++
                            previous = dst
                            val lo = kotlin.math.floor(dst).toInt()
                            if (lo in occupied.indices) occupied[lo] = true
                            if (dst > lo && lo+1 in occupied.indices) occupied[lo+1] = true
                        }
                        var x = 0
                        while (x < w) {
                            if (occupied[x]) { x++; continue }
                            val start = x
                            while (x < w && !occupied[x]) x++
                            val gap = x-start
                            if (start == 0 || x == w) border += gap else {
                                interior += gap
                                longest = maxOf(longest,gap)
                            }
                        }
                    }
                    metrics.appendText("${c.label},$i,$interior,$border,$longest,$folds,${w*h}\n")
                    val frame = warp.warpPixels(pixels,grid,w,h,offset*c.baseline,c.pivot,c.gain)
                    check(frame.all { it ushr 24 == 255 })
                    val bytes = ByteBuffer.allocate(frame.size*4)
                    bytes.asIntBuffer().put(frame)
                    File(out,"${c.label}-$i.argb").writeBytes(bytes.array())
                }
            }
        }
    }
}
