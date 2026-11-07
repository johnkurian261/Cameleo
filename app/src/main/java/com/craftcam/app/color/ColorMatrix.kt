package com.craftcam.app.color

/**
 * 3x3 Color Transformation Matrix for CAMELEO Advanced Color Science.
 *
 * Used for sensor calibration transforms, forward matrices, and color space conversions.
 * Supports GPU matrix conversions, algebraic multiplication, inversion, and multi-illuminant interpolation.
 */
data class ColorMatrix3x3(
    val m00: Float, val m01: Float, val m02: Float,
    val m10: Float, val m11: Float, val m12: Float,
    val m20: Float, val m21: Float, val m22: Float
) {
    companion object {
        val IDENTITY = ColorMatrix3x3(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
        )

        fun fromArray(a: FloatArray): ColorMatrix3x3 {
            require(a.size >= 9) { "Array must contain at least 9 elements" }
            return ColorMatrix3x3(
                a[0], a[1], a[2],
                a[3], a[4], a[5],
                a[6], a[7], a[8]
            )
        }
    }

    /**
     * Matrix multiplication (this * other).
     */
    operator fun times(o: ColorMatrix3x3): ColorMatrix3x3 {
        return ColorMatrix3x3(
            m00 * o.m00 + m01 * o.m10 + m02 * o.m20,
            m00 * o.m01 + m01 * o.m11 + m02 * o.m21,
            m00 * o.m02 + m01 * o.m12 + m02 * o.m22,

            m10 * o.m00 + m11 * o.m10 + m12 * o.m20,
            m10 * o.m01 + m11 * o.m11 + m12 * o.m21,
            m10 * o.m02 + m11 * o.m12 + m12 * o.m22,

            m20 * o.m00 + m21 * o.m10 + m22 * o.m20,
            m20 * o.m01 + m21 * o.m11 + m22 * o.m21,
            m20 * o.m02 + m21 * o.m12 + m22 * o.m22
        )
    }

    /**
     * Transforms an RGB vector [r, g, b] in linear color space.
     */
    fun apply(r: Float, g: Float, b: Float): FloatArray {
        return floatArrayOf(
            m00 * r + m01 * g + m02 * b,
            m10 * r + m11 * g + m12 * b,
            m20 * r + m21 * g + m22 * b
        )
    }

    /**
     * Interpolates linearly between this matrix and [other] by fraction [t] in [0.0f, 1.0f].
     * Essential for multi-illuminant color calibration (e.g. Daylight D65 vs Standard A Tungsten).
     */
    fun interpolate(other: ColorMatrix3x3, t: Float): ColorMatrix3x3 {
        val s = 1.0f - t
        return ColorMatrix3x3(
            m00 * s + other.m00 * t, m01 * s + other.m01 * t, m02 * s + other.m02 * t,
            m10 * s + other.m10 * t, m11 * s + other.m11 * t, m12 * s + other.m12 * t,
            m20 * s + other.m20 * t, m21 * s + other.m21 * t, m22 * s + other.m22 * t
        )
    }

    /**
     * Computes the matrix determinant.
     */
    fun determinant(): Float {
        return m00 * (m11 * m22 - m12 * m21) -
               m01 * (m10 * m22 - m12 * m20) +
               m02 * (m10 * m21 - m11 * m20)
    }

    /**
     * Inverts the 3x3 matrix. Returns IDENTITY if matrix is singular.
     */
    fun inverse(): ColorMatrix3x3 {
        val det = determinant()
        if (kotlin.math.abs(det) < 1e-7f) return IDENTITY

        val invDet = 1.0f / det
        return ColorMatrix3x3(
            (m11 * m22 - m12 * m21) * invDet,
            (m02 * m21 - m01 * m22) * invDet,
            (m01 * m12 - m02 * m11) * invDet,

            (m12 * m20 - m10 * m22) * invDet,
            (m00 * m22 - m02 * m20) * invDet,
            (m02 * m10 - m00 * m12) * invDet,

            (m10 * m21 - m11 * m20) * invDet,
            (m01 * m20 - m00 * m21) * invDet,
            (m00 * m11 - m01 * m10) * invDet
        )
    }

    /**
     * Exports matrix as row-major FloatArray for OpenGL shader uniform ingestion.
     */
    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            m00, m01, m02,
            m10, m11, m12,
            m20, m21, m22
        )
    }
}

