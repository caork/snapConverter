package com.snapconverter.engine.gpu

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws a [SurfaceTexture] OES frame into the current EGL surface with
 * optional extra rotation and UV crop. No CPU readback.
 */
class TextureRenderer {

    private var program = 0
    private var aPosition = 0
    private var aTextureCoord = 0
    private var uTexMatrix = 0
    private var textureId = 0
    private val stMatrix = FloatArray(16)
    private val extraMatrix = FloatArray(16)
    private val resultMatrix = FloatArray(16)

    val oesTextureId: Int get() = textureId

    fun create() {
        textureId = createOesTexture()
        program = buildProgram(VERTEX, FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoord = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        Matrix.setIdentityM(extraMatrix, 0)
    }

    fun draw(
        surfaceTexture: SurfaceTexture,
        extraRotationDegrees: Int = 0,
        crop: FloatArray = FULL_CROP,
    ) {
        GlUtil.check("before draw")
        surfaceTexture.getTransformMatrix(stMatrix)
        Matrix.setIdentityM(extraMatrix, 0)
        if (extraRotationDegrees % 360 != 0) {
            Matrix.translateM(extraMatrix, 0, 0.5f, 0.5f, 0f)
            Matrix.rotateM(extraMatrix, 0, extraRotationDegrees.toFloat(), 0f, 0f, 1f)
            Matrix.translateM(extraMatrix, 0, -0.5f, -0.5f, 0f)
        }
        Matrix.multiplyMM(resultMatrix, 0, extraMatrix, 0, stMatrix, 0)

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        val verts = verticesForCrop(crop)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 8, FULL_POS)
        GLES20.glEnableVertexAttribArray(aTextureCoord)
        GLES20.glVertexAttribPointer(aTextureCoord, 2, GLES20.GL_FLOAT, false, 8, verts)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, resultMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTextureCoord)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glUseProgram(0)
        GlUtil.check("after draw")
    }

    fun release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
    }

    private fun createOesTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        return id
    }

    private fun buildProgram(vertex: String, fragment: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val link = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, link, 0)
        if (link[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(prog)
            GLES20.glDeleteProgram(prog)
            throw RuntimeException("program link failed: $log")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return prog
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("shader compile failed: $log")
        }
        return shader
    }

    companion object {
        /** left, top, right, bottom in 0..1 texture space. */
        val FULL_CROP = floatArrayOf(0f, 0f, 1f, 1f)

        private val FULL_POS = floatBuffer(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        )

        private fun verticesForCrop(crop: FloatArray): FloatBuffer {
            val l = crop[0]
            val t = crop[1]
            val r = crop[2]
            val b = crop[3]
            return floatBuffer(
                l, t,
                r, t,
                l, b,
                r, b,
            )
        }

        private fun floatBuffer(vararg values: Float): FloatBuffer {
            val bb = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
            return bb.asFloatBuffer().apply {
                put(values)
                position(0)
            }
        }

        private const val VERTEX = """
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """
    }
}

object GlUtil {
    fun check(label: String) {
        var error: Int
        while (true) {
            error = GLES20.glGetError()
            if (error == GLES20.GL_NO_ERROR) break
            throw RuntimeException("$label: glError 0x" + Integer.toHexString(error))
        }
    }
}
