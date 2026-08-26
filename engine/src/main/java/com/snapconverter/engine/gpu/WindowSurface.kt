package com.snapconverter.engine.gpu

import android.opengl.EGL14
import android.opengl.EGLSurface
import android.view.Surface

class WindowSurface(
    private val eglCore: EglCore,
    private val surface: Surface,
    private val releaseSurface: Boolean,
) {
    private var eglSurface: EGLSurface = eglCore.createWindowSurface(surface)

    val width: Int get() = eglCore.querySurface(eglSurface, EGL14.EGL_WIDTH)
    val height: Int get() = eglCore.querySurface(eglSurface, EGL14.EGL_HEIGHT)

    fun makeCurrent() = eglCore.makeCurrent(eglSurface)

    fun swapBuffers(): Boolean = eglCore.swapBuffers(eglSurface)

    fun setPresentationTime(nsecs: Long) = eglCore.setPresentationTime(eglSurface, nsecs)

    fun release() {
        eglCore.releaseSurface(eglSurface)
        eglSurface = EGL14.EGL_NO_SURFACE
        if (releaseSurface) {
            surface.release()
        }
    }
}
