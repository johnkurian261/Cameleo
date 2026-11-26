package com.craftcam.probe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14 as E
import android.opengl.EGLConfig
import android.opengl.GLES30 as GL
import android.opengl.GLSurfaceView
import android.view.Surface
import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.gl.GLViewfinderRenderer
import com.craftcam.app.gl.LutTextureCache
import com.craftcam.app.presets.PresetCatalog
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

/** Exercise the real OES program, sampler bindings, LUT cache and renderer uniforms on identical RGB input. */
internal fun renderPreview(context: Context, chart: Bitmap, output: (Bitmap, Boolean) -> Unit) {
    val display = E.eglGetDisplay(E.EGL_DEFAULT_DISPLAY)
    val version = IntArray(2)
    check(E.eglInitialize(display,version,0,version,1))
    val configs = arrayOfNulls<EGLConfig>(1)
    val count = IntArray(1)
    val attributes = intArrayOf(E.EGL_RENDERABLE_TYPE,64,E.EGL_SURFACE_TYPE,E.EGL_PBUFFER_BIT,
        E.EGL_RED_SIZE,8,E.EGL_GREEN_SIZE,8,E.EGL_BLUE_SIZE,8,E.EGL_ALPHA_SIZE,8,E.EGL_NONE)
    check(E.eglChooseConfig(display,attributes,0,configs,0,1,count,0) && count[0]>0)
    val ctx = E.eglCreateContext(display,configs[0],E.EGL_NO_CONTEXT,intArrayOf(E.EGL_CONTEXT_CLIENT_VERSION,3,E.EGL_NONE),0)
    val surface = E.eglCreatePbufferSurface(display,configs[0],intArrayOf(E.EGL_WIDTH,chart.width,E.EGL_HEIGHT,chart.height,E.EGL_NONE),0)
    check(E.eglMakeCurrent(display,surface,surface,ctx))
    val queue = ConcurrentLinkedQueue<Runnable>()
    val view = object : GLSurfaceView(context) {
        override fun queueEvent(r: Runnable) { queue.add(r) }
        override fun requestRender() {}
    }
    lateinit var texture: SurfaceTexture
    val renderer = GLViewfinderRenderer(context,view) { texture=it }
    try {
        renderer.onSurfaceCreated(null,null)
        renderer.onSurfaceChanged(null,chart.width,chart.height)
        // Synchronously populate the same cache used by the viewfinder before selecting FXNR.
        checkNotNull(LutTextureCache.getOrCreateLutTexture(context,"lut_fxn_r.png",prefer3D=true))
        renderer.updatePreset(PresetCatalog.allPresets.first { it.id=="analog_fxn_r" })
        renderer.updateTuning(UserTuningConfig(debugStageOptics=false,debugStageVignette=false,
            debugStageHalation=false,debugStageGrain=false))
        texture.setDefaultBufferSize(chart.width,chart.height)
        val producer = Surface(texture)
        try {
            for (front in listOf(false,true)) {
                renderer.setCameraOrientation(0,front,chart.width,chart.height)
                val canvas = producer.lockCanvas(null)
                canvas.drawBitmap(chart,null,android.graphics.Rect(0,0,chart.width,chart.height),null)
                producer.unlockCanvasAndPost(canvas)
                while (true) (queue.poll() ?: break).run()
                renderer.onFrameAvailable(texture)
                renderer.onDrawFrame(null)
                check(!renderer.useFallbackShader)
                val bytes = ByteBuffer.allocateDirect(chart.width*chart.height*4)
                GL.glReadPixels(0,0,chart.width,chart.height,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,bytes)
                val result = Bitmap.createBitmap(chart.width,chart.height,Bitmap.Config.ARGB_8888)
                result.copyPixelsFromBuffer(bytes)
                output(result,front)
                result.recycle()
            }
        } finally { producer.release() }
    } finally {
        renderer.releaseGLResources()
        renderer.release()
        E.eglMakeCurrent(display,E.EGL_NO_SURFACE,E.EGL_NO_SURFACE,E.EGL_NO_CONTEXT)
        E.eglDestroySurface(display,surface)
        E.eglDestroyContext(display,ctx)
        E.eglTerminate(display)
    }
}
