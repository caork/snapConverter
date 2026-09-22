package com.snapconverter.engine.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.snapconverter.engine.ScLog
import com.snapconverter.engine.progress.EncodeProgress
import com.snapconverter.engine.progress.EncodeProgressListener
import java.nio.ByteBuffer

/** Thrown when the source container has no audio track to extract. */
class NoAudioTrackException(message: String) : Exception(message)

/**
 * Passthrough audio extraction: MediaExtractor -> MediaMuxer (M4A).
 * No codec is created, nothing is decoded or re-encoded — this is a pure
 * sample copy, so the V1 "audio is passthrough" rule is preserved and no
 * hardware or software encoder is involved.
 */
class AudioExtractor(private val context: Context) {

    /** @return number of audio samples copied into the output container. */
    fun extract(
        input: Uri,
        outputPfd: ParcelFileDescriptor,
        progress: EncodeProgressListener? = null,
    ): Long {
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(outputPfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        try {
            extractor.setDataSource(context, input, null)
            val track = selectAudioTrack(extractor)
                ?: throw NoAudioTrackException("此文件没有音轨，无法提取音频。")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            val maxInput = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                0
            }
            val buffer = ByteBuffer.allocate(maxInput.coerceIn(64 * 1024, 4 * 1024 * 1024))

            val audioTrack = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true

            val info = MediaCodec.BufferInfo()
            val startedAt = SystemClock.elapsedRealtime()
            var samples = 0L
            var bytes = 0L
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(audioTrack, buffer, info)
                samples++
                bytes += size
                val ratio = if (durationUs > 0) {
                    (info.presentationTimeUs.toDouble() / durationUs).toFloat().coerceIn(0f, 0.99f)
                } else {
                    0f
                }
                progress?.onProgress(
                    EncodeProgress(
                        ratio = ratio,
                        bytesWritten = bytes,
                        elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                        presentationTimeUs = info.presentationTimeUs,
                        message = "正在直通提取音频…",
                    ),
                )
                extractor.advance()
            }
            if (samples == 0L) {
                throw IllegalStateException("音轨为空，没有可提取的音频样本。")
            }
            progress?.onProgress(
                EncodeProgress(
                    ratio = 1f,
                    bytesWritten = bytes,
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                    message = "音频提取完成",
                ),
            )
            ScLog.i("audio passthrough done: samples=" + samples + " bytes=" + bytes)
            return samples
        } finally {
            runCatching { if (muxerStarted) muxer.stop() }
            runCatching { muxer.release() }
            extractor.release()
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }
}
