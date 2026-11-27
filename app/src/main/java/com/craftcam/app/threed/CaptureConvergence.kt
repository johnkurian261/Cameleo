package com.craftcam.app.threed

import android.graphics.RectF
import android.graphics.Matrix
import android.graphics.PointF
import com.craftcam.app.portrait.FaceRegion
import android.hardware.camera2.CaptureResult
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.CameraCaptureResults

data class ConvergencePoint(val x: Float, val y: Float)

internal object CaptureConvergence {
    data class Metadata(val focus: FloatArray? = null, val faces: List<FaceRegion> = emptyList()) {
        fun workingFocus(matrix: Matrix, w: Int, h: Int): ConvergencePoint? = focus?.let {
            val point = it.clone()
            matrix.mapPoints(point)
            ConvergencePoint(point[0] / w, point[1] / h).takeIf { p -> p.x in 0f..1f && p.y in 0f..1f }
        }

        fun workingFaces(matrix: Matrix, w: Int, h: Int): List<FaceRegion> = faces.mapNotNull { face ->
            val rect = RectF(face.bounds)
            matrix.mapRect(rect)
            if (!rect.intersect(0f, 0f, w.toFloat(), h.toFloat())) return@mapNotNull null
            fun eye(p: PointF?): PointF? = p?.let {
                val xy = floatArrayOf(it.x,it.y)
                matrix.mapPoints(xy)
                PointF(xy[0]/w,xy[1]/h)
            }
            face.copy(bounds = RectF(rect.left/w,rect.top/h,rect.right/w,rect.bottom/h),
                leftEye = eye(face.leftEye), rightEye = eye(face.rightEye))
        }
    }

    @android.annotation.SuppressLint("RestrictedApi")
    fun inBuffer(image: ImageProxy): Metadata {
        val info = image.imageInfo
        val result = CameraCaptureResults.retrieveCameraCaptureResult(info)?.captureResult ?: return Metadata()
        if (result.get(CaptureResult.SENSOR_TIMESTAMP) != info.timestamp) return Metadata()
        val transform = info.sensorToBufferTransformMatrix
        val faces = result.get(CaptureResult.STATISTICS_FACES).orEmpty().map { face ->
            val bounds = RectF(face.bounds)
            transform.mapRect(bounds)
            fun eye(p: android.graphics.Point?): PointF? = p?.let {
                val xy = floatArrayOf(it.x.toFloat(),it.y.toFloat())
                transform.mapPoints(xy)
                PointF(xy[0],xy[1])
            }
            FaceRegion(bounds,face.score / 100f,leftEye = eye(face.leftEyePosition),rightEye = eye(face.rightEyePosition))
        }
        val crop = result.get(CaptureResult.SCALER_CROP_REGION)
        val region = if (crop != null && result.get(CaptureResult.CONTROL_AF_STATE) == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED)
            result.get(CaptureResult.CONTROL_AF_REGIONS)?.filter {
                it.meteringWeight > 0 && it.rect.width() < crop.width() / 2 && it.rect.height() < crop.height() / 2
            }?.maxByOrNull { it.meteringWeight }?.rect else null
        val focus = region?.let { floatArrayOf(it.exactCenterX(), it.exactCenterY()).also { point -> transform.mapPoints(point) } }
        return Metadata(focus, faces)
    }
}
