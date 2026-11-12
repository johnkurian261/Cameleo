package com.craftcam.app.threed

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.craftcam.app.portrait.FaceRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * High-level coordinator pipeline for CAMELEO 3D Stereoscopic Wigglegram photography.
 *
 * Execution stages:
 * 1. Working Resolution Normalization (capped to 1080px to protect memory and maximize throughput).
 * 2. Monocular Depth Estimation & Disparity Normalization ([ThreeDDepthEngine]).
 * 3. Depth-Aware Horizontal Parallax Warping with Occlusion Inpainting ([ParallaxWarpEngine]).
 * 4. Vintage Retro Analog Imperfections (RGB shift, 35mm grain, lens softness, jitter) ([RetroFrameRenderer]).
 * 5. Native Hardware H.264 MP4 Encoding ([ThreeDVideoEncoder]).
 * 6. MediaStore Video Gallery Persistence.
 */
class ThreeDPhotoProcessor(
    private val config: ThreeDConfig = ThreeDConfig()
) {
    companion object {
        private const val TAG = "ThreeDPhotoProcessor"
        private const val MAX_WORKING_DIM = 1080
    }

    private val depthEngine = ThreeDDepthEngine()
    private val warpEngine = ParallaxWarpEngine(config)
    private val videoEncoder = ThreeDVideoEncoder()

    suspend fun processAndSaveThreeDPhoto(
        context: Context,
        sourceBitmap: Bitmap,
        faces: List<FaceRegion> = emptyList(),
        onProgress: ((String) -> Unit)? = null
    ): Uri? = withContext(Dispatchers.Default) {
        val workingBitmap: Bitmap
        val shouldRecycleWorking: Boolean

        val srcW = sourceBitmap.width
        val srcH = sourceBitmap.height

        // Downscale to working resolution if needed
        if (srcW > MAX_WORKING_DIM || srcH > MAX_WORKING_DIM) {
            val scale = MAX_WORKING_DIM.toFloat() / maxOf(srcW, srcH)
            val targetW = (srcW * scale).roundToInt().coerceAtLeast(2)
            val targetH = (srcH * scale).roundToInt().coerceAtLeast(2)
            workingBitmap = Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true)
            shouldRecycleWorking = true
        } else {
            workingBitmap = sourceBitmap
            shouldRecycleWorking = false
        }

        val tempVideoFile = File(context.cacheDir, "cameleo_3d_${System.currentTimeMillis()}.mp4")

        try {
            onProgress?.invoke("Analyzing Depth Geometry...")
            val disparityMap = depthEngine.computeDisparity(workingBitmap, faces)

            onProgress?.invoke("Synthesizing Stereoscopic Viewpoints...")
            val rawFrames = warpEngine.generateParallaxFrames(workingBitmap, disparityMap)

            onProgress?.invoke("Applying Vintage Emulsion...")
            val retroRenderer = RetroFrameRenderer(context, config)
            val styledFrames = retroRenderer.processFrames(rawFrames)

            if (config.exportDebugMaps) {
                exportDebugMaps(context, disparityMap, rawFrames)
            }

            // Free unstyled raw frames
            for (f in rawFrames) {
                if (f != workingBitmap && !f.isRecycled) {
                    f.recycle()
                }
            }

            onProgress?.invoke("Encoding Looping Video...")
            val success = videoEncoder.encodeFramesToMp4(
                frames = styledFrames,
                outputFile = tempVideoFile,
                fps = config.fps,
                loopCount = config.loopCount
            )

            // Cache 0ms instant thumbnail before recycling styled frames
            val firstFrame = styledFrames.firstOrNull()
            val thumbImageBitmap = if (firstFrame != null && !firstFrame.isRecycled) {
                com.craftcam.app.ui.gallery.GalleryCache.createThumbnail(firstFrame, 256)
            } else null

            // Free styled frames
            for (f in styledFrames) {
                if (!f.isRecycled) {
                    f.recycle()
                }
            }

            if (!success || !tempVideoFile.exists() || tempVideoFile.length() == 0L) {
                Log.e(TAG, "Video encoding produced invalid or empty file")
                return@withContext null
            }

            onProgress?.invoke("Saving to Cameleo Album...")
            val savedUri = saveVideoToGallery(context, tempVideoFile)
            if (savedUri != null && thumbImageBitmap != null) {
                val id = try { android.content.ContentUris.parseId(savedUri) } catch (_: Exception) { 0L }
                if (id > 0L) {
                    com.craftcam.app.ui.gallery.GalleryCache.putThumbnail(id, thumbImageBitmap)
                }
            }
            Log.i(TAG, "3D Wigglegram video successfully saved: $savedUri (${tempVideoFile.length()} bytes)")
            savedUri
        } catch (t: Throwable) {
            Log.e(TAG, "Error generating 3D Wigglegram", t)
            null
        } finally {
            if (shouldRecycleWorking && !workingBitmap.isRecycled) {
                workingBitmap.recycle()
            }
            if (tempVideoFile.exists()) {
                tempVideoFile.delete()
            }
        }
    }

    private fun saveVideoToGallery(context: Context, videoFile: File): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "CAMELEO_3D_${timeStamp}.mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Cameleo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(videoFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            val id = try { android.content.ContentUris.parseId(uri) } catch (_: Exception) { 0L }
            if (id > 0L) {
                com.craftcam.app.ui.gallery.GalleryCache.addNewItem(
                    com.craftcam.app.ui.gallery.GalleryItem(
                        id = id,
                        uri = uri,
                        name = fileName,
                        dateAdded = System.currentTimeMillis() / 1000,
                        cameraModel = "D3D",
                        isVideo = true
                    )
                )
            }
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing video to MediaStore: ${e.message}", e)
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {}
            return null
        }
    }

    private fun exportDebugMaps(
        context: Context,
        disparityMap: DisparityMap,
        rawFrames: List<Bitmap>
    ) {
        try {
            val debugDir = context.getExternalFilesDir(null) ?: context.cacheDir

            // 1. Raw Depth Map (black = near, white = far)
            val dw = disparityMap.width
            val dh = disparityMap.height
            val depthPixels = IntArray(dw * dh)
            val depthSrc = disparityMap.rawDepthValues ?: FloatArray(dw * dh) { 1f - disparityMap.values[it] }
            for (i in depthPixels.indices) {
                val b = (depthSrc[i].coerceIn(0f, 1f) * 255f).toInt()
                depthPixels[i] = (0xFF shl 24) or (b shl 16) or (b shl 8) or b
            }
            val depthBmp = Bitmap.createBitmap(depthPixels, dw, dh, Bitmap.Config.ARGB_8888)
            File(debugDir, "D3D_DEPTH_DEBUG.png").outputStream().use {
                depthBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            depthBmp.recycle()

            // 2. Disparity Map (black = min displacement, white = max displacement)
            val dispPixels = IntArray(dw * dh)
            for (i in dispPixels.indices) {
                val b = (disparityMap.values[i].coerceIn(0f, 1f) * 255f).toInt()
                dispPixels[i] = (0xFF shl 24) or (b shl 16) or (b shl 8) or b
            }
            val dispBmp = Bitmap.createBitmap(dispPixels, dw, dh, Bitmap.Config.ARGB_8888)
            File(debugDir, "D3D_DISPARITY_DEBUG.png").outputStream().use {
                dispBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            dispBmp.recycle()

            // 3. Viewpoints Strip (L1 | L2 | L3 | L4) & Geometry Diff
            if (rawFrames.size >= 4) {
                val fw = rawFrames[0].width
                val fh = rawFrames[0].height
                val stripBmp = Bitmap.createBitmap(fw * 4, fh, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(stripBmp)
                for (idx in 0 until 4) {
                    canvas.drawBitmap(rawFrames[idx], idx * fw.toFloat(), 0f, null)
                }
                File(debugDir, "D3D_VIEWPOINTS_DEBUG.png").outputStream().use {
                    stripBmp.compress(Bitmap.CompressFormat.PNG, 95, it)
                }
                File(debugDir, "D3D_GEOMETRY_VIEWPOINTS_DEBUG.png").outputStream().use {
                    stripBmp.compress(Bitmap.CompressFormat.PNG, 95, it)
                }
                stripBmp.recycle()

                // 4. Pure Geometric Difference Map (L1 vs L4)
                val l1 = rawFrames[0]
                val l4 = rawFrames[3]
                val p1 = IntArray(fw * fh)
                val p4 = IntArray(fw * fh)
                val diffPixels = IntArray(fw * fh)
                l1.getPixels(p1, 0, fw, 0, 0, fw, fh)
                l4.getPixels(p4, 0, fw, 0, 0, fw, fh)
                for (i in diffPixels.indices) {
                    val r = kotlin.math.abs(((p1[i] shr 16) and 0xFF) - ((p4[i] shr 16) and 0xFF))
                    val g = kotlin.math.abs(((p1[i] shr 8) and 0xFF) - ((p4[i] shr 8) and 0xFF))
                    val b = kotlin.math.abs((p1[i] and 0xFF) - (p4[i] and 0xFF))
                    val amplified = (kotlin.math.max(r, kotlin.math.max(g, b)) * 3).coerceIn(0, 255)
                    diffPixels[i] = (0xFF shl 24) or (amplified shl 16) or (amplified shl 8) or amplified
                }
                val diffBmp = Bitmap.createBitmap(diffPixels, fw, fh, Bitmap.Config.ARGB_8888)
                File(debugDir, "D3D_GEOMETRY_DIFF_L1_L4.png").outputStream().use {
                    diffBmp.compress(Bitmap.CompressFormat.PNG, 95, it)
                }
                diffBmp.recycle()

                val maxPx = fw * config.d3dParallaxStrength
                val fgStep = (disparityMap.foregroundDisparity - disparityMap.pivot) * (maxPx * 0.667f)
                val bgStep = (disparityMap.backgroundDisparity - disparityMap.pivot) * (maxPx * 0.667f)
                Log.i(TAG, "=== D3D Depth-Region Motion Audit ===")
                Log.i(TAG, "Foreground Step (L1->L2): +${fgStep}px, Background Step: ${bgStep}px, Subject Step: ~0.0px")
                Log.i(TAG, "Net relative foreground <-> background separation per frame step: ${fgStep - bgStep}px")
            }
            Log.i(TAG, "Successfully exported D3D debug maps to: ${debugDir.absolutePath}")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed exporting D3D debug maps", t)
        }
    }
}

