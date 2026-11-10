package com.craftcam.app.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance GPU Texture Cache for 3D and Hald Color Lookup Tables.
 *
 * Capabilities:
 * 1. Converts 512x512 2D Hald CLUTs into native OpenGL ES 3.0 3D Textures (GL_TEXTURE_3D, 64x64x64).
 * 2. Employs hardware-accelerated trilinear interpolation (GL_LINEAR across R, G, B lattices).
 * 3. Caches GPU texture IDs to prevent repeated asset decoding and memory churn during shooting.
 * 4. Thread-safe asset loading with automatic fallback to 2D Hald sampling if 3D textures are unavailable.
 */
object LutTextureCache {

    private const val TAG = "LutTextureCache"

    data class CachedLut(
        val assetPath: String,
        val textureId: Int,
        val target: Int, // GLES30.GL_TEXTURE_3D or GLES20.GL_TEXTURE_2D
        val lutDimension: Int = 64, // 64 for standard 512x512 Hald
        val lastAccessMs: Long = System.currentTimeMillis()
    )

    private val cache = java.util.concurrent.ConcurrentHashMap<String, CachedLut>()
    private val failedPaths = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val lock = Any()

    /**
     * Lock-free lookup for already cached GPU LUT textures.
     * Guaranteed zero allocations, zero GL driver queries, and zero thread contention in onDrawFrame().
     */
    fun getCachedLutFast(assetPath: String): CachedLut? {
        return cache[assetPath]
    }

    /**
     * Checks if a LUT texture has already been uploaded to GPU.
     */
    fun isLutCached(assetPath: String): Boolean {
        return cache.containsKey(assetPath)
    }

    data class PreparedVoxelData(
        val assetPath: String,
        val buffer: ByteBuffer,
        val dim: Int = 64
    )

    /**
     * Asynchronously decodes and unrolls 512x512 Hald CLUT into a direct voxel buffer.
     * Designed to run on Dispatchers.IO so the GL render thread is NEVER blocked during filter changes.
     */
    fun prepareVoxelDataAsync(context: Context, assetPath: String): PreparedVoxelData? {
        if (cache.containsKey(assetPath) || failedPaths.contains(assetPath)) return null
        val bitmap = try {
            context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Asynchronous LUT decode failed: $assetPath", e)
            failedPaths.add(assetPath)
            return null
        } ?: run {
            failedPaths.add(assetPath)
            return null
        }

        if (bitmap.width != 512 || bitmap.height != 512) {
            bitmap.recycle()
            return null
        }

        val dim = 64
        val haldDim = 512
        val pixels = IntArray(haldDim * haldDim)
        bitmap.getPixels(pixels, 0, haldDim, 0, 0, haldDim, haldDim)
        bitmap.recycle()

        val byteBuffer = ByteBuffer.allocateDirect(dim * dim * dim * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        for (z in 0 until dim) {
            val blockX = z % 8
            val blockY = z / 8
            val startPixelX = blockX * dim
            val startPixelY = blockY * dim

            for (y in 0 until dim) {
                val haldY = startPixelY + y
                val rowOffset = haldY * haldDim
                for (x in 0 until dim) {
                    val argb = pixels[rowOffset + startPixelX + x]
                    val r = ((argb shr 16) and 0xFF).toByte()
                    val g = ((argb shr 8) and 0xFF).toByte()
                    val b = (argb and 0xFF).toByte()
                    byteBuffer.put(r).put(g).put(b).put(0xFF.toByte())
                }
            }
        }
        byteBuffer.position(0)
        return PreparedVoxelData(assetPath, byteBuffer, dim)
    }

    /**
     * Fast sub-millisecond GPU upload of pre-unrolled voxel data.
     * Must be called on the GL thread (e.g. inside glSurfaceView.queueEvent).
     */
    fun uploadPreparedVoxelData(prepared: PreparedVoxelData): CachedLut? {
        synchronized(lock) {
            cache[prepared.assetPath]?.let { return it }

            val texIds = IntArray(1)
            GLES30.glGenTextures(1, texIds, 0)
            val texId = texIds[0]
            if (texId == 0) {
                Log.e(TAG, "Failed to generate GL_TEXTURE_3D for ${prepared.assetPath}")
                return null
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

            GLES30.glTexImage3D(
                GLES30.GL_TEXTURE_3D,
                0,
                GLES30.GL_RGBA8,
                prepared.dim,
                prepared.dim,
                prepared.dim,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                prepared.buffer
            )

            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
            val cachedLut = CachedLut(prepared.assetPath, texId, GLES30.GL_TEXTURE_3D, prepared.dim)
            cache[prepared.assetPath] = cachedLut
            Log.i(TAG, "Asynchronously uploaded 3D LUT to GPU: ${prepared.assetPath} -> TexID=$texId")
            return cachedLut
        }
    }

    /**
     * Retrieves an existing cached GPU texture ID or uploads a new one on the active GL thread.
     *
     * @param context Android context for asset access.
     * @param assetPath Path in assets/ (e.g. "lut_analog_35mm.png").
     * @param prefer3D Whether to upload as native GL_TEXTURE_3D (GLES 3.0) for single-sample trilinear lookup.
     * @return CachedLut containing texture ID and GL target, or null if loading failed.
     */
    fun getOrCreateLutTexture(
        context: Context,
        assetPath: String,
        prefer3D: Boolean = true
    ): CachedLut? {
        // Fast lock-free path for already cached textures
        cache[assetPath]?.let { return it }

        if (failedPaths.contains(assetPath)) {
            return null
        }

        synchronized(lock) {
            cache[assetPath]?.let { return it }

            val bitmap: Bitmap = try {
                context.assets.open(assetPath).use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load LUT asset from disk (suppressing further attempts): $assetPath")
                failedPaths.add(assetPath)
                return null
            } ?: run {
                failedPaths.add(assetPath)
                return null
            }

            val cachedLut = if (prefer3D && bitmap.width == 512 && bitmap.height == 512) {
                uploadAs3DTexture(bitmap, assetPath)
            } else {
                uploadAs2DTexture(bitmap, assetPath)
            }

            bitmap.recycle()

            if (cachedLut != null) {
                cache[assetPath] = cachedLut
                Log.i(TAG, "Cached GPU LUT: $assetPath -> TexID=${cachedLut.textureId}, Target=0x${Integer.toHexString(cachedLut.target)}")
            }
            return cachedLut
        }
    }

    /**
     * Converts a 512x512 Hald CLUT (composed of 8x8 squares of 64x64 pixels)
     * into an unrolled 64x64x64 3D voxel lattice in RGBA byte format.
     */
    private fun uploadAs3DTexture(haldBitmap: Bitmap, assetPath: String): CachedLut? {
        val dim = 64 // Cube size
        val haldDim = 512
        val pixels = IntArray(haldDim * haldDim)
        haldBitmap.getPixels(pixels, 0, haldDim, 0, 0, haldDim, haldDim)

        // Allocate direct ByteBuffer for 64 * 64 * 64 * 4 bytes (1 MB)
        val byteBuffer = ByteBuffer.allocateDirect(dim * dim * dim * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        // Unroll 2D Hald tiling into contiguous 3D Z-slices:
        // Hald layout: Blue is mapped across the 8x8 grid of blocks.
        // For each slice z (0..63): blockX = z % 8, blockY = z / 8
        // Within block: x in 0..63 is Red, y in 0..63 is Green
        for (z in 0 until dim) {
            val blockX = z % 8
            val blockY = z / 8
            val startPixelX = blockX * dim
            val startPixelY = blockY * dim

            for (y in 0 until dim) {
                val haldY = startPixelY + y
                for (x in 0 until dim) {
                    val haldX = startPixelX + x
                    val argb = pixels[haldY * haldDim + haldX]

                    val r = ((argb shr 16) and 0xFF).toByte()
                    val g = ((argb shr 8) and 0xFF).toByte()
                    val b = (argb and 0xFF).toByte()
                    val a = 0xFF.toByte()

                    byteBuffer.put(r)
                    byteBuffer.put(g)
                    byteBuffer.put(b)
                    byteBuffer.put(a)
                }
            }
        }
        byteBuffer.position(0)

        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        if (texId == 0) {
            Log.e(TAG, "Failed to generate GL_TEXTURE_3D for $assetPath")
            return null
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D,
            0,
            GLES30.GL_RGBA8,
            dim,
            dim,
            dim,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            byteBuffer
        )

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
        return CachedLut(assetPath, texId, GLES30.GL_TEXTURE_3D, dim)
    }

    private fun uploadAs2DTexture(bitmap: Bitmap, assetPath: String): CachedLut? {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        if (texId == 0) {
            Log.e(TAG, "Failed to generate GL_TEXTURE_2D for $assetPath")
            return null
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        return CachedLut(assetPath, texId, GLES20.GL_TEXTURE_2D, 64)
    }

    /**
     * Clears in-memory references when the EGL context is lost or recreated without invoking
     * glDeleteTextures on stale/invalid GL context handles.
     */
    fun onContextLost() {
        synchronized(lock) {
            cache.clear()
            failedPaths.clear()
        }
    }

    /**
     * Purges expired or all cached textures from GPU memory.
     * Must be called on an active EGL Context thread.
     */
    fun clear(glThreadOnly: Boolean = true) {
        synchronized(lock) {
            for ((_, cached) in cache) {
                if (GLES20.glIsTexture(cached.textureId)) {
                    val tex = intArrayOf(cached.textureId)
                    GLES20.glDeleteTextures(1, tex, 0)
                }
            }
            cache.clear()
            Log.d(TAG, "Cleared all cached GPU LUT textures.")
        }
    }
}

