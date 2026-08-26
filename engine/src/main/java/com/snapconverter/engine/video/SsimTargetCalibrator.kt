package com.snapconverter.engine.video

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.snapconverter.engine.ScLog
import com.snapconverter.engine.codec.CodecCandidate
import com.snapconverter.engine.codec.HardwareCodecSelector
import com.snapconverter.engine.policy.BitrateModeOption
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.CompressionPolicy
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.SsimLadder
import com.snapconverter.engine.policy.VideoSourceInfo
import com.snapconverter.engine.progress.EncodeProgress
import com.snapconverter.engine.progress.EncodeProgressListener
import com.snapconverter.engine.quality.QualityAnalyzer
import java.io.File

/**
 * Encodes a few short windows at candidate bitrates and picks the lowest
 * bitrate whose SSIM still meets the target. Full-file encode stays one pass.
 */
class SsimTargetCalibrator(
    private val context: Context,
    private val selector: HardwareCodecSelector,
    private val policy: CompressionPolicy,
    private val quality: QualityAnalyzer,
) {
    fun calibrate(
        input: Uri,
        source: VideoSourceInfo,
        decoder: CodecCandidate,
        encoder: CodecCandidate,
        request: CompressionRequest,
        progress: EncodeProgressListener?,
    ): SsimLadder.Result {
        val target = request.targetSsim
        val windows = SsimLadder.windows(source.durationUs)
        val hi = upperBoundBps(source)
        val lo = 200_000
        ScLog.i("ssim calibrate target=$target windows=${windows.size} lo=$lo hi=$hi")
        var step = 0
        val result = SsimLadder.minBitrateForSsim(lo, hi, target) { bitrate ->
            step++
            progress?.onProgress(
                EncodeProgress(
                    ratio = (0.02f + 0.14f * (step / 7f)).coerceIn(0.02f, 0.16f),
                    message = "正在标定 SSIM（$step）…",
                ),
            )
            measureWindows(input, source, decoder, encoder, request, windows, bitrate)
        }
        ScLog.i(
            "ssim calibrate done bitrate=${result.bitrateBps} ssim=${result.ssim} " +
                "iters=${result.iterations} met=${result.metTarget}",
        )
        progress?.onProgress(
            EncodeProgress(
                ratio = 0.18f,
                message = "SSIM 标定 ${"%.3f".format(result.ssim)} @ ${result.bitrateBps / 1000} kbps",
            ),
        )
        return result
    }

    private fun upperBoundBps(source: VideoSourceInfo): Int {
        val src = source.bitrateBps.takeIf { it > 80_000 } ?: 12_000_000
        return src.coerceIn(400_000, 40_000_000)
    }

    private fun measureWindows(
        input: Uri,
        source: VideoSourceInfo,
        decoder: CodecCandidate,
        encoder: CodecCandidate,
        request: CompressionRequest,
        windows: List<SsimLadder.Window>,
        bitrateBps: Int,
    ): Double {
        val scores = windows.mapNotNull { window ->
            measureOne(input, source, decoder, encoder, request, window, bitrateBps)
        }
        if (scores.isEmpty()) return 0.0
        return scores.average()
    }

    private fun measureOne(
        input: Uri,
        source: VideoSourceInfo,
        decoder: CodecCandidate,
        encoder: CodecCandidate,
        request: CompressionRequest,
        window: SsimLadder.Window,
        bitrateBps: Int,
    ): Double? {
        val tmp = File.createTempFile("sc-ssim-", ".mp4", context.cacheDir)
        try {
            val probeRequest = request.copy(
                mode = CompressionMode.TARGET_BITRATE,
                targetBitrateBps = bitrateBps,
                bitrateMode = BitrateModeOption.VBR,
                iFrameIntervalSec = 1,
                maxBitrateBps = (bitrateBps * 1.4).toInt(),
            )
            val plan = policy.planVideo(source, probeRequest, encoder)
            val pfdMode = ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE
            ParcelFileDescriptor.open(tmp, pfdMode).use { pfd ->
                SurfaceTranscoder(context, selector).transcode(
                    input = input,
                    outputPfd = pfd,
                    source = source,
                    decoder = decoder,
                    encoder = encoder,
                    plan = plan,
                    rangeStartUs = window.startUs,
                    rangeEndUs = window.endUs,
                    copyAudio = false,
                )
            }
            val encoded = Uri.fromFile(tmp)
            val report = quality.compare(
                kind = MediaKind.VIDEO,
                original = input,
                encoded = encoded,
                durationUs = window.durationUs,
                encodedWidth = plan.width,
                encodedHeight = plan.height,
                origOffsetUs = window.startUs,
                sampleCountOverride = 3,
                includeVmaf = false,
            )
            ScLog.i(
                "ssim probe ${window.startUs / 1000}ms @ ${bitrateBps / 1000}kbps " +
                    "ssim=${report?.ssim} samples=${report?.samples}",
            )
            return report?.ssim
        } catch (t: Throwable) {
            ScLog.w("ssim probe failed at ${bitrateBps / 1000} kbps", t)
            return null
        } finally {
            tmp.delete()
        }
    }
}
