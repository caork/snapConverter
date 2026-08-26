package com.snapconverter.engine.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.Surface
import com.snapconverter.engine.ScLog
import com.snapconverter.engine.codec.CodecCandidate
import com.snapconverter.engine.codec.HardwareCodecSelector
import com.snapconverter.engine.gpu.GpuFrameProcessor
import com.snapconverter.engine.policy.VideoEncodePlan
import com.snapconverter.engine.policy.VideoSourceInfo
import com.snapconverter.engine.progress.EncodeProgress
import com.snapconverter.engine.progress.EncodeProgressListener
import java.nio.ByteBuffer

/**
 * Extract → hardware decode to Surface → GLES → hardware encode Surface → mux.
 * Runs on the calling thread, which becomes the EGL thread for the duration.
 */
class SurfaceTranscoder(
    private val context: Context,
    private val selector: HardwareCodecSelector,
) {

    fun transcode(
        input: Uri,
        outputPfd: ParcelFileDescriptor,
        source: VideoSourceInfo,
        decoder: CodecCandidate,
        encoder: CodecCandidate,
        plan: VideoEncodePlan,
        progress: EncodeProgressListener? = null,
    ) {
        val extractor = MediaExtractor()
        var decoderCodec: MediaCodec? = null
        var encoderCodec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var gpu: GpuFrameProcessor? = null
        var encoderSurface: Surface? = null
        var muxerStarted = false
        try {
            extractor.setDataSource(context, input, null)
            val videoTrack = selectTrack(extractor, "video/")
            val audioTrack = selectTrackOrNull(extractor, "audio/")
            extractor.selectTrack(videoTrack)
            val inputFormat = extractor.getTrackFormat(videoTrack)

            encoderCodec = selector.createByName(encoder)
            val vendorKeys = selector.probeVendorParameters(encoderCodec)
            val outputFormat = buildEncoderFormat(plan, encoder)
            encoderCodec.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderSurface = encoderCodec.createInputSurface()
            encoderCodec.start()
            ScLog.i(
                "encoder=${encoder.name} decoder=${decoder.name} " +
                    "${plan.width}x${plan.height}@${plan.frameRate} " +
                    "br=${plan.bitrateBps} mode=${plan.bitrateMode} vendorKeys=${vendorKeys.size}",
            )

            gpu = GpuFrameProcessor()
            gpu.start(encoderSurface)
            val decoderWidth = inputFormat.getInteger(MediaFormat.KEY_WIDTH)
            val decoderHeight = inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
            gpu.setDefaultBufferSize(decoderWidth, decoderHeight)

            decoderCodec = selector.createByName(decoder)
            decoderCodec.configure(inputFormat, gpu.inputSurfaceForDecoder, null, 0)
            decoderCodec.start()

            muxer = MediaMuxer(outputPfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(0)

            val durationUs = source.durationUs.coerceAtLeast(1L)
            val minFrameIntervalUs = 1_000_000L / plan.frameRate
            var lastEncodedPts = -minFrameIntervalUs
            val startedAt = SystemClock.elapsedRealtime()
            var framesEncoded = 0
            var bytesWritten = 0L
            fun emit(ratio: Float, pts: Long = lastEncodedPts) {
                progress?.onProgress(
                    EncodeProgress(
                        ratio = ratio.coerceIn(0f, 1f),
                        framesEncoded = framesEncoded,
                        bytesWritten = bytesWritten,
                        elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                        presentationTimeUs = pts,
                    ),
                )
            }
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var audioExtractor: MediaExtractor? = null
            if (audioTrack != null) {
                audioExtractor = MediaExtractor()
                audioExtractor.setDataSource(context, input, null)
                audioExtractor.selectTrack(audioTrack)
            }

            val decoderBufferInfo = MediaCodec.BufferInfo()
            val encoderBufferInfo = MediaCodec.BufferInfo()
            var extractorDone = false
            var decoderDone = false
            var encoderDone = false
            var signaledEncoderEos = false

            while (!encoderDone) {
                if (!extractorDone) {
                    val inIndex = decoderCodec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoderCodec.getInputBuffer(inIndex)!!
                        val sample = extractor.readSampleData(buffer, 0)
                        if (sample < 0) {
                            decoderCodec.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            extractorDone = true
                        } else {
                            decoderCodec.queueInputBuffer(
                                inIndex, 0, sample, extractor.sampleTime, 0,
                            )
                            extractor.advance()
                        }
                    }
                }

                if (!decoderDone) {
                    val outIndex = decoderCodec.dequeueOutputBuffer(decoderBufferInfo, TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                        outIndex >= 0 -> {
                            val eos = decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            val render = decoderBufferInfo.size > 0 && !eos
                            val pts = decoderBufferInfo.presentationTimeUs
                            val drop = render && (pts - lastEncodedPts) < (minFrameIntervalUs * 0.85).toLong()
                            decoderCodec.releaseOutputBuffer(outIndex, render && !drop)
                            if (render && !drop) {
                                gpu.drawDecoderFrame(
                                    presentationTimeUs = pts,
                                    outputWidth = plan.width,
                                    outputHeight = plan.height,
                                    extraRotationDegrees = source.rotation,
                                )
                                lastEncodedPts = pts
                                framesEncoded++
                                emit((pts.toDouble() / durationUs).toFloat().coerceIn(0f, 0.99f), pts)
                            }
                            if (eos) {
                                decoderDone = true
                            }
                        }
                    }
                }

                if (decoderDone && !signaledEncoderEos) {
                    encoderCodec.signalEndOfInputStream()
                    signaledEncoderEos = true
                }

                val encIndex = encoderCodec.dequeueOutputBuffer(encoderBufferInfo, TIMEOUT_US)
                when {
                    encIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) error("encoder format changed twice")
                        videoTrackIndex = muxer.addTrack(encoderCodec.outputFormat)
                        if (audioTrack != null && audioExtractor != null) {
                            audioTrackIndex = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
                        }
                        muxer.start()
                        muxerStarted = true
                    }
                    encIndex >= 0 -> {
                        val encoded = encoderCodec.getOutputBuffer(encIndex)
                        if (encoded != null && encoderBufferInfo.size > 0 && muxerStarted) {
                            if (encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                encoded.position(encoderBufferInfo.offset)
                                encoded.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, encoded, encoderBufferInfo)
                                bytesWritten += encoderBufferInfo.size
                            }
                        }
                        encoderCodec.releaseOutputBuffer(encIndex, false)
                        if (encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderDone = true
                        }
                    }
                }
            }

            if (muxerStarted && audioExtractor != null && audioTrackIndex >= 0) {
                copyAudio(audioExtractor, muxer, audioTrackIndex)
            }
            audioExtractor?.release()
            emit(1f)
            ScLog.i("transcode loop finished, releasing EGL then codecs")
        } finally {
            // EGL must drop the encoder input Surface before MediaCodec.stop(),
            // otherwise Qualcomm C2 can block the calling thread indefinitely.
            runCatching { gpu?.release() }
            gpu = null
            runCatching { decoderCodec?.stop() }
            runCatching { decoderCodec?.release() }
            runCatching { encoderCodec?.stop() }
            runCatching { encoderCodec?.release() }
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { encoderSurface?.release() }
            extractor.release()
            ScLog.i("release complete")
        }
    }

    private fun buildEncoderFormat(plan: VideoEncodePlan, encoder: CodecCandidate): MediaFormat {
        val format = MediaFormat.createVideoFormat(plan.mime, plan.width, plan.height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, plan.bitrateBps)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, plan.frameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, plan.iFrameIntervalSec)
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, plan.bitrateMode)
        format.setInteger(MediaFormat.KEY_OPERATING_RATE, plan.operatingRate)
        if (plan.codecQuality != null) {
            format.setInteger(MediaFormat.KEY_QUALITY, plan.codecQuality)
        }
        if (plan.maxBFrames > 0 && Build.VERSION.SDK_INT >= 29) {
            format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, plan.maxBFrames)
        }
        encoder.complexityRange?.let { range ->
            format.setInteger(MediaFormat.KEY_COMPLEXITY, range.last)
        }
        return format
    }

    private fun copyAudio(extractor: MediaExtractor, muxer: MediaMuxer, trackIndex: Int) {
        val info = MediaCodec.BufferInfo()
        val buffer = ByteBuffer.allocate(64 * 1024)
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(trackIndex, buffer, info)
            extractor.advance()
        }
    }

    private fun selectTrack(extractor: MediaExtractor, prefix: String): Int {
        return selectTrackOrNull(extractor, prefix) ?: error("no $prefix track")
    }

    private fun selectTrackOrNull(extractor: MediaExtractor, prefix: String): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return null
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
    }
}
