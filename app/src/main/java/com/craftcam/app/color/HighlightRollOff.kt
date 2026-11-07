package com.craftcam.app.color

/**
 * Continuous Asymptotic Highlight Roll-Off Operator for CAMELEO.
 *
 * Prevents harsh specular clipping and color-channel skewing in bright scenes
 * (sunlit clouds, window lights, direct reflections, bright skin tones).
 *
 * Implements a rational asymptotic shoulder:
 * y = knee + (1.0 - knee) * (d / (d + s))
 * where d = x - knee, and s is the shoulder compression factor.
 */
object HighlightRollOff {

    /**
     * Applies asymptotic shoulder roll-off to a linear channel or luminance value [x].
     *
     * @param x Linear irradiance in [0.0, infinity).
     * @param knee Threshold where compression begins (typically 0.60 to 0.75).
     * @param compressionFactor Falloff curvature (lower = softer asymptotic curve).
     */
    fun apply(
        x: Float,
        knee: Float = 0.65f,
        compressionFactor: Float = 0.35f
    ): Float {
        if (x <= knee) return x

        val d = x - knee
        val factor = compressionFactor.coerceAtLeast(0.01f)
        val compressed = knee + (1.0f - knee) * (d / (d + factor))
        return compressed.coerceIn(0.0f, 1.0f)
    }

    /**
     * Vectorized roll-off across RGB channels, preserving original chromaticity ratios.
     */
    fun applyRgb(
        r: Float,
        g: Float,
        b: Float,
        knee: Float = 0.65f,
        compressionFactor: Float = 0.35f
    ): FloatArray {
        // Luminance-weighted roll-off prevents hue shifts when only one channel saturates
        val maxChan = maxOf(r, g, b)
        if (maxChan <= knee) return floatArrayOf(r, g, b)

        val rolledMax = apply(maxChan, knee, compressionFactor)
        val scale = rolledMax / maxChan.coerceAtLeast(0.0001f)

        return floatArrayOf(
            (r * scale).coerceIn(0f, 1f),
            (g * scale).coerceIn(0f, 1f),
            (b * scale).coerceIn(0f, 1f)
        )
    }
}

