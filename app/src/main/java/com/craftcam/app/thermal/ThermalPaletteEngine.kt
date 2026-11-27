package com.craftcam.app.thermal

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * C1-smooth thermal false-color palette lookup engine shared identically by:
 * - OpenGL ES `uThermalPaletteTex` (256x5 RGBA8888 texture uploaded once for GPU lookup)
 * - CPU capture, gallery import, and darkroom redevelopment (`sampleRgbNormalized`).
 */
object ThermalPaletteEngine {

    const val LUT_WIDTH = 256
    const val LUT_HEIGHT = 5 // One horizontal ramp per ThermalPalette

    private data class Stop(val pos: Float, val r: Float, val g: Float, val b: Float)

    private fun stop(pos: Float, argb: Int): Stop {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return Stop(pos, r, g, b)
    }

    private val PALETTE_STOPS: Map<ThermalPalette, List<Stop>> = mapOf(
        ThermalPalette.RAINBOW_THERMAL to listOf(
            stop(0.00f, 0x12052C),
            stop(0.16f, 0x232EA8),
            stop(0.35f, 0x0AA8DC),
            stop(0.54f, 0x19C65E),
            stop(0.74f, 0xF4E022),
            stop(0.90f, 0xEF421A),
            stop(1.00f, 0xD91818)
        ),
        ThermalPalette.IRONBOW to listOf(
            stop(0.00f, 0x06030E),
            stop(0.22f, 0x3E0C6E),
            stop(0.45f, 0x9E1E5C),
            stop(0.66f, 0xDC4622),
            stop(0.84f, 0xFA920A),
            stop(1.00f, 0xFFF79C)
        ),
        ThermalPalette.RED_HOT to listOf(
            stop(0.00f, 0x071538),
            stop(0.24f, 0x15458F),
            stop(0.46f, 0x2C639E),
            stop(0.64f, 0x8D2652),
            stop(0.84f, 0xE32419),
            stop(1.00f, 0xFF6A45)
        ),
        ThermalPalette.WHITE_HOT to listOf(
            stop(0.00f, 0x07080A),
            stop(0.35f, 0x454950),
            stop(0.70f, 0xA8ADB5),
            stop(1.00f, 0xF8FAFC)
        ),
        ThermalPalette.BLACK_HOT to listOf(
            stop(0.00f, 0xF8FAFC),
            stop(0.30f, 0xA8ADB5),
            stop(0.65f, 0x454950),
            stop(1.00f, 0x07080A)
        )
    )

    // Precomputed 256 x 5 ARGB table (256 * 5 = 1280 ints = 5 KB) for zero-allocation lookup.
    private val LUT_ARGB: IntArray = IntArray(LUT_WIDTH * LUT_HEIGHT).also { table ->
        for (p in ThermalPalette.entries) {
            val stops = PALETTE_STOPS.getValue(p)
            val rowOff = p.index * LUT_WIDTH
            for (x in 0 until LUT_WIDTH) {
                val t = x / (LUT_WIDTH - 1f)
                val rgb = evaluateSpline(stops, t)
                val ir = (rgb[0] * 255f + 0.5f).toInt().coerceIn(0, 255)
                val ig = (rgb[1] * 255f + 0.5f).toInt().coerceIn(0, 255)
                val ib = (rgb[2] * 255f + 0.5f).toInt().coerceIn(0, 255)
                table[rowOff + x] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }
    }

    /**
     * Builds an immutable direct RGBA8888 ByteBuffer (256 x 5 x 4 = 5120 bytes) ready for
     * `GLES30.glTexImage2D` with `GL_LINEAR` filtering and `GL_CLAMP_TO_EDGE`.
     */
    fun createGlTextureBuffer(): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(LUT_WIDTH * LUT_HEIGHT * 4).order(ByteOrder.nativeOrder())
        for (i in LUT_ARGB.indices) {
            val c = LUT_ARGB[i]
            buf.put(((c ushr 16) and 0xFF).toByte())
            buf.put(((c ushr 8) and 0xFF).toByte())
            buf.put((c and 0xFF).toByte())
            buf.put(0xFF.toByte())
        }
        buf.position(0)
        return buf
    }

    /**
     * Reads normalized RGB [0f..1f] linearly interpolated along the precomputed 256-entry palette,
     * matching GPU `GL_LINEAR` hardware lookup identically.
     */
    fun sampleRgbNormalized(
        palette: ThermalPalette,
        heatNorm: Float,
        inverted: Boolean,
        outRgb: FloatArray
    ) {
        val tRaw = if (inverted) 1f - heatNorm else heatNorm
        val pos = tRaw.coerceIn(0f, 1f) * (LUT_WIDTH - 1f)
        val x0 = pos.toInt().coerceIn(0, LUT_WIDTH - 2)
        val x1 = x0 + 1
        val f = (pos - x0).coerceIn(0f, 1f)

        val rowOff = palette.index * LUT_WIDTH
        val c0 = LUT_ARGB[rowOff + x0]
        val c1 = LUT_ARGB[rowOff + x1]

        val r0 = ((c0 ushr 16) and 0xFF) / 255f
        val g0 = ((c0 ushr 8) and 0xFF) / 255f
        val b0 = (c0 and 0xFF) / 255f

        val r1 = ((c1 ushr 16) and 0xFF) / 255f
        val g1 = ((c1 ushr 8) and 0xFF) / 255f
        val b1 = (c1 and 0xFF) / 255f

        outRgb[0] = r0 + (r1 - r0) * f
        outRgb[1] = g0 + (g1 - g0) * f
        outRgb[2] = b0 + (b1 - b0) * f
    }

    fun getRawLutTableCopy(): IntArray = LUT_ARGB.copyOf()

    private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (
            (2f * p1) +
                (-p0 + p2) * t +
                (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                (-p0 + 3f * p1 - 3f * p2 + p3) * t3
            )
    }

    private fun evaluateSpline(stops: List<Stop>, tIn: Float): FloatArray {
        val t = tIn.coerceIn(0f, 1f)
        if (t <= stops.first().pos) {
            val s = stops.first()
            return floatArrayOf(s.r, s.g, s.b)
        }
        if (t >= stops.last().pos) {
            val s = stops.last()
            return floatArrayOf(s.r, s.g, s.b)
        }

        var idx = 0
        for (i in 0 until stops.size - 1) {
            if (t >= stops[i].pos && t <= stops[i + 1].pos) {
                idx = i
                break
            }
        }
        val c1 = stops[idx]
        val c2 = stops[idx + 1]
        val c0 = stops[max(0, idx - 1)]
        val c3 = stops[min(stops.size - 1, idx + 2)]
        val localT = ((t - c1.pos) / max(1e-6f, c2.pos - c1.pos)).coerceIn(0f, 1f)

        fun interp(v0: Float, v1: Float, v2: Float, v3: Float): Float {
            val v = catmullRom(v0, v1, v2, v3, localT)
            val lo = max(0f, min(v1, v2) - 0.02f)
            val hi = min(1f, max(v1, v2) + 0.02f)
            return v.coerceIn(lo, hi)
        }

        return floatArrayOf(
            interp(c0.r, c1.r, c2.r, c3.r),
            interp(c0.g, c1.g, c2.g, c3.g),
            interp(c0.b, c1.b, c2.b, c3.b)
        )
    }
}
