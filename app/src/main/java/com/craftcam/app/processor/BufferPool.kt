package com.craftcam.app.processor

import android.graphics.Bitmap
import android.util.Log

/**
 * Thread-safe, bounded memory pool for full-resolution and intermediate Bitmaps.
 * Prevents repeated ~48.8MB allocations during high-frequency camera capture by
 * recycling and reconfiguring existing Bitmap buffers via Android's [Bitmap.reconfigure].
 */
object BitmapPool {
    private const val TAG = "BitmapPool"

    // Bounded pool limits to prevent excessive memory retention:
    // Max 2 full-resolution bitmaps (~49MB each) - sufficient for concurrent pipeline overlap
    private const val MAX_LARGE_BITMAPS = 2
    // Max 2 downsampled intermediate bitmaps (~3MB each) - used for halation bloom
    private const val MAX_MEDIUM_BITMAPS = 2

    // Thresholds:
    // 12.2MP ARGB_8888 photo: 4032 x 3024 x 4 = ~48,771,072 bytes (~48.77MB)
    // Downsampled 1/4 halation bloom: 1008 x 756 x 4 = ~3,048,448 bytes (~3.05MB)
    private const val LARGE_BYTE_THRESHOLD = 20_000_000 // 20 MB

    private val lock = Any()
    private val largePool = ArrayList<Bitmap>(MAX_LARGE_BITMAPS)
    private val mediumPool = ArrayList<Bitmap>(MAX_MEDIUM_BITMAPS)

    /**
     * Acquires a mutable Bitmap of the requested [width], [height], and [config].
     * If a compatible buffer is available in the pool, it is reconfigured
     * with zero native memory allocation.
     * Otherwise, allocates a new Bitmap as a controlled fallback.
     */
    fun acquire(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap {
        val requiredBytes = width * height * bytesPerPixel(config)
        synchronized(lock) {
            val pool = if (requiredBytes >= LARGE_BYTE_THRESHOLD) largePool else mediumPool
            val iterator = pool.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                iterator.remove()
                if (!candidate.isRecycled && candidate.isMutable && candidate.allocationByteCount >= requiredBytes) {
                    try {
                        candidate.reconfigure(width, height, config)
                        return candidate
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to reconfigure pooled bitmap, recycling", e)
                        try { candidate.recycle() } catch (_: Throwable) {}
                    }
                } else if (!candidate.isRecycled) {
                    try { candidate.recycle() } catch (_: Throwable) {}
                }
            }
        }

        // Controlled fallback allocation:
        return Bitmap.createBitmap(width, height, config)
    }

    /**
     * Releases a Bitmap back into the pool if compatible and below capacity limits.
     * If the pool is full or the Bitmap is ineligible, it is recycled immediately.
     */
    fun release(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled || !bitmap.isMutable) return

        synchronized(lock) {
            val byteCount = bitmap.allocationByteCount
            if (byteCount >= LARGE_BYTE_THRESHOLD) {
                if (largePool.size < MAX_LARGE_BITMAPS) {
                    largePool.add(bitmap)
                    return
                }
            } else if (byteCount >= 1_000_000) {
                if (mediumPool.size < MAX_MEDIUM_BITMAPS) {
                    mediumPool.add(bitmap)
                    return
                }
            }
        }

        // Pool full or not eligible: safely recycle
        try {
            bitmap.recycle()
        } catch (_: Throwable) {}
    }

    /**
     * Clears all pooled bitmaps and reclaims memory. Call during low memory conditions.
     */
    fun clear() {
        synchronized(lock) {
            for (bmp in largePool) {
                try { if (!bmp.isRecycled) bmp.recycle() } catch (_: Throwable) {}
            }
            largePool.clear()
            for (bmp in mediumPool) {
                try { if (!bmp.isRecycled) bmp.recycle() } catch (_: Throwable) {}
            }
            mediumPool.clear()
        }
    }

    private fun bytesPerPixel(config: Bitmap.Config): Int = when (config) {
        Bitmap.Config.ARGB_8888 -> 4
        Bitmap.Config.RGB_565 -> 2
        Bitmap.Config.ALPHA_8 -> 1
        Bitmap.Config.RGBA_F16 -> 8
        else -> 4
    }
}

/**
 * Thread-safe, bounded memory pool for IntArray pixel buffers.
 * Eliminates repeated multi-megabyte heap churn during pixel grading, halation,
 * and optical softness blurs.
 */
object IntArrayPool {
    private const val TAG = "IntArrayPool"

    // Max 2 full-res arrays (12.2M ints = ~48.8MB) for concurrent/overlapping photo development
    private const val MAX_LARGE_ARRAYS = 2
    // Max 2 medium arrays (~762K ints = ~3.05MB) for halation bloom downsampled passes
    private const val MAX_MEDIUM_ARRAYS = 2

    private const val LARGE_INT_THRESHOLD = 8_000_000  // ~8M elements (32MB)
    private const val MEDIUM_INT_THRESHOLD = 500_000   // ~500K elements (2MB)

    private val lock = Any()
    private val largePool = ArrayList<IntArray>(MAX_LARGE_ARRAYS)
    private val mediumPool = ArrayList<IntArray>(MAX_MEDIUM_ARRAYS)

    /**
     * Acquires an IntArray with at least [minSize] elements.
     * Reuses an existing array from the pool if available; otherwise allocates a new one.
     */
    fun acquire(minSize: Int): IntArray {
        synchronized(lock) {
            val pool = if (minSize >= LARGE_INT_THRESHOLD) {
                largePool
            } else if (minSize >= MEDIUM_INT_THRESHOLD) {
                mediumPool
            } else {
                null
            }

            if (pool != null) {
                val iterator = pool.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (candidate.size >= minSize) {
                        iterator.remove()
                        return candidate
                    }
                }
            }
        }

        // Controlled fallback allocation:
        return IntArray(minSize)
    }

    /**
     * Releases an IntArray back to the pool.
     * Re-pools only if within bounded limits; otherwise allows GC collection.
     */
    fun release(array: IntArray?) {
        if (array == null) return

        synchronized(lock) {
            if (array.size >= LARGE_INT_THRESHOLD) {
                if (largePool.size < MAX_LARGE_ARRAYS) {
                    largePool.add(array)
                }
            } else if (array.size >= MEDIUM_INT_THRESHOLD) {
                if (mediumPool.size < MAX_MEDIUM_ARRAYS) {
                    mediumPool.add(array)
                }
            }
        }
    }

    /**
     * Clears all pooled arrays.
     */
    fun clear() {
        synchronized(lock) {
            largePool.clear()
            mediumPool.clear()
        }
    }
}

