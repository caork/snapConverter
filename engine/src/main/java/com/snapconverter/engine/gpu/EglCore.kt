package com.snapconverter.engine.gpu

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

/**
 * EGL 1.4 core for recordable GLES 3 (fallback GLES 2) contexts.
 * Must be used from a single thread.
 */
class EglCore(sharedContext: EGLContext = EGL14.EGL_NO_CONTEXT) {

    val display: EGLDisplay
    val context: EGLContext
    private val config: EGLConfig
    val glesVersion: Int

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw RuntimeException("unable to initialize EGL14")
        }
        val gles3 = chooseConfig(3)
        if (gles3 != null) {
            config = gles3
            context = createContext(sharedContext, 3)
            glesVersion = 3
        } else {
            config = chooseConfig(2) ?: throw RuntimeException("no EGL config")
            context = createContext(sharedContext, 2)
            glesVersion = 2
        }
        if (context == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("failed to create EGL context: 0x" + Integer.toHexString(EGL14.eglGetError()))
        }
    }

    fun createWindowSurface(surface: Surface): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, attribs, 0)
        checkEgl("eglCreateWindowSurface")
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglCreateWindowSurface returned NO_SURFACE")
        }
        return eglSurface
    }

    fun makeCurrent(eglSurface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            throw RuntimeException("eglMakeCurrent failed: 0x" + Integer.toHexString(EGL14.eglGetError()))
        }
    }

    fun swapBuffers(eglSurface: EGLSurface): Boolean =
        EGL14.eglSwapBuffers(display, eglSurface)

    fun setPresentationTime(eglSurface: EGLSurface, nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, nsecs)
    }

    fun querySurface(eglSurface: EGLSurface, what: Int): Int {
        val value = IntArray(1)
        EGL14.eglQuerySurface(display, eglSurface, what, value, 0)
        return value[0]
    }

    fun releaseSurface(eglSurface: EGLSurface) {
        EGL14.eglDestroySurface(display, eglSurface)
    }

    fun makeNothingCurrent() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    }

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
    }

    private fun createContext(shared: EGLContext, version: Int): EGLContext {
        val attribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, version, EGL14.EGL_NONE)
        return EGL14.eglCreateContext(display, config, shared, attribs, 0)
    }

    private fun chooseConfig(glesVersion: Int): EGLConfig? {
        val renderable = if (glesVersion >= 3) {
            EGLExt.EGL_OPENGL_ES3_BIT_KHR
        } else {
            EGL14.EGL_OPENGL_ES2_BIT
        }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, renderable,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)) {
            return null
        }
        return if (num[0] > 0) configs[0] else null
    }

    private fun checkEgl(op: String) {
        val error = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            throw RuntimeException("$op: EGL error 0x" + Integer.toHexString(error))
        }
    }

    companion object {
        const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
