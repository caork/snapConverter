package com.snapconverter.engine.progress

data class EncodeProgress(
    val ratio: Float,
    val framesEncoded: Int = 0,
    val bytesWritten: Long = 0,
    val elapsedMs: Long = 0,
    val presentationTimeUs: Long = 0,
    val message: String? = null,
) {
    val framesPerSecond: Float
        get() = if (elapsedMs <= 0) 0f else framesEncoded * 1000f / elapsedMs

    val megabytesPerSecond: Float
        get() = if (elapsedMs <= 0) 0f else (bytesWritten / 1_000_000f) / (elapsedMs / 1000f)

    val etaMs: Long
        get() {
            if (ratio <= 0.02f || ratio >= 1f || elapsedMs <= 0) return -1
            return ((elapsedMs / ratio) - elapsedMs).toLong().coerceAtLeast(0)
        }
}

fun interface EncodeProgressListener {
    fun onProgress(update: EncodeProgress)
}
