package com.snapconverter.engine.gpu

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.view.Surface

/**
 * Owns an EGL context bound to an encoder (or HeifWriter) input [Surface]
 * and draws decoder OES frames into it.
 */
class GpuFrameProcessor {

    private var eglCore: EglCore? = null
    private var window: WindowSurface? = null
    private var renderer: TextureRenderer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var decoderSurface: Surface? = null
    private val frameSync = Object()
    @Volatile private var frameAvailable = false

    val inputSurfaceForDecoder: Surface
        get() = decoderSurface ?: error("GpuFrameProcessor not started")

    fun start(encoderInputSurface: Surface) {
        val core = EglCore()
        eglCore = core
        val win = WindowSurface(core, encoderInputSurface, releaseSurface = false)
        window = win
        win.makeCurrent()
        val r = TextureRenderer()
        r.create()
        renderer = r
        val st = SurfaceTexture(r.oesTextureId)
        st.setOnFrameAvailableListener {
            synchronized(frameSync) {
                frameAvailable = true
                frameSync.notifyAll()
            }
        }
        surfaceTexture = st
        decoderSurface = Surface(st)
    }

    fun setDefaultBufferSize(width: Int, height: Int) {
        surfaceTexture?.setDefaultBufferSize(width, height)
    }

    /**
     * Wait for the decoder-rendered frame, then draw it into the encoder Surface.
     * [presentationTimeUs] is converted to nanoseconds for EGL.
     */
    fun drawDecoderFrame(
        presentationTimeUs: Long,
        outputWidth: Int,
        outputHeight: Int,
        extraRotationDegrees: Int = 0,
        timeoutMs: Long = 2_500,
    ) {
        awaitFrame(timeoutMs)
        val st = surfaceTexture ?: return
        val win = window ?: return
        val r = renderer ?: return
        win.makeCurrent()
        st.updateTexImage()
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        r.draw(st, extraRotationDegrees)
        win.setPresentationTime(presentationTimeUs * 1000)
        win.swapBuffers()
    }

    fun drawAndPresent(presentationTimeUs: Long) {
        val win = window ?: return
        win.setPresentationTime(presentationTimeUs * 1000)
        win.swapBuffers()
    }

    fun makeCurrent() {
        window?.makeCurrent()
    }

    fun release() {
        decoderSurface?.release()
        decoderSurface = null
        surfaceTexture?.setOnFrameAvailableListener(null)
        surfaceTexture?.release()
        surfaceTexture = null
        renderer?.release()
        renderer = null
        window?.release()
        window = null
        eglCore?.release()
        eglCore = null
    }

    private fun awaitFrame(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(frameSync) {
            while (!frameAvailable) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    throw RuntimeException("timeout waiting for decoder SurfaceTexture frame")
                }
                frameSync.wait(remaining)
            }
            frameAvailable = false
        }
    }
}
