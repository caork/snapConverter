package com.snapconverter.engine.video

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.snapconverter.engine.codec.HardwareCodecSelector
import com.snapconverter.engine.ScLog
import com.snapconverter.engine.media.CaptureTimestamp
import com.snapconverter.engine.media.VideoGeometry
import com.snapconverter.engine.policy.CompressionPolicy
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.VideoSourceInfo
import com.snapconverter.engine.progress.EncodeProgressListener

class VideoEngine(
    private val context: Context,
    private val selector: HardwareCodecSelector = HardwareCodecSelector(),
    private val policy: CompressionPolicy = CompressionPolicy(),
) {
    fun inspect(uri: Uri): VideoSourceInfo {
        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()
        try {
            extractor.setDataSource(context, uri, null)
            retriever.setDataSource(context, uri)
            var videoMime = "video/avc"
            var width = 0
            var height = 0
            var rotation = 0
            var frameRate = 30f
            var bitrate = 0
            var durationUs = 0L
            var audioMime: String? = null
            var audioBitrate = 0
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoMime = mime
                    val coded = codedFrameSize(format)
                    width = coded.first
                    height = coded.second
                    rotation = formatInt(format, MediaFormat.KEY_ROTATION)
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = try {
                            format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                        } catch (_: Exception) {
                            format.getFloat(MediaFormat.KEY_FRAME_RATE)
                        }
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    }
                } else if (mime.startsWith("audio/")) {
                    audioMime = mime
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        audioBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                    }
                }
            }
            if (width == 0 || height == 0) {
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            }
            val retrieverRotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION,
            )?.toIntOrNull() ?: 0
            val retrieverWidth = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
            )?.toIntOrNull() ?: 0
            val retrieverHeight = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
            )?.toIntOrNull() ?: 0
            rotation = VideoGeometry.detectRotation(
                extractorRotation = rotation,
                retrieverRotation = retrieverRotation,
                codedWidth = width,
                codedHeight = height,
                retrieverWidth = retrieverWidth,
                retrieverHeight = retrieverHeight,
            )
            ScLog.i(
                "inspect coded=${width}x${height} rot=$rotation " +
                    "(extractor/retriever=$retrieverRotation ${retrieverWidth}x${retrieverHeight}) " +
                    "display=${VideoGeometry.displayWidth(width, height, rotation)}x" +
                    "${VideoGeometry.displayHeight(width, height, rotation)}",
            )
            if (durationUs == 0L) {
                durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            }
            if (bitrate == 0) {
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            }
            val identity = CaptureTimestamp.read(context, uri, videoMime)
            return VideoSourceInfo(
                width = width,
                height = height,
                rotation = rotation,
                durationUs = durationUs,
                frameRate = frameRate,
                bitrateBps = bitrate,
                mime = videoMime,
                audioMime = audioMime,
                audioBitrateBps = audioBitrate,
                displayName = identity.displayName,
                fileSizeBytes = identity.fileSizeBytes,
                captureTimeMs = identity.captureTimeMs,
            )
        } finally {
            extractor.release()
            retriever.release()
        }
    }

    private fun codedFrameSize(format: MediaFormat): Pair<Int, Int> {
        var w = format.getInteger(MediaFormat.KEY_WIDTH)
        var h = format.getInteger(MediaFormat.KEY_HEIGHT)
        if (format.containsKey("crop-right") && format.containsKey("crop-left")) {
            w = format.getInteger("crop-right") - format.getInteger("crop-left") + 1
        }
        if (format.containsKey("crop-bottom") && format.containsKey("crop-top")) {
            h = format.getInteger("crop-bottom") - format.getInteger("crop-top") + 1
        }
        return w to h
    }

    private fun formatInt(format: MediaFormat, key: String): Int {
        if (!format.containsKey(key)) return 0
        return runCatching { format.getInteger(key) }.getOrDefault(0)
    }

    fun compress(
        input: Uri,
        outputPfd: ParcelFileDescriptor,
        request: CompressionRequest,
        progress: EncodeProgressListener? = null,
    ) {
        val source = inspect(input)
        val decoder = selector.selectDecoder(source.mime)
        val encoder = selector.selectPreferredVideoEncoder(
            preferredMimes = policy.preferredMimes(request.videoCodec),
            width = source.displayWidth,
            height = source.displayHeight,
        )
        val plan = policy.planVideo(source, request, encoder)
        SurfaceTranscoder(context, selector).transcode(
            input = input,
            outputPfd = outputPfd,
            source = source,
            decoder = decoder,
            encoder = encoder,
            plan = plan,
            progress = progress,
        )
    }
}
