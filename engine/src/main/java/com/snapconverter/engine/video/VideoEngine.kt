package com.snapconverter.engine.video

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.snapconverter.engine.codec.HardwareCodecSelector
import com.snapconverter.engine.media.CaptureTimestamp
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
                    width = format.getInteger(MediaFormat.KEY_WIDTH)
                    height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                        rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                    }
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
            if (rotation == 0) {
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            }
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
            width = source.width,
            height = source.height,
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
