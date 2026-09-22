package com.snapconverter.engine.video

import android.content.Context
import android.net.Uri
import com.snapconverter.engine.codec.CodecCandidate
import com.snapconverter.engine.codec.HardwareCodecSelector
import com.snapconverter.engine.policy.CompressionPolicy
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.SsimLadder
import com.snapconverter.engine.policy.VideoSourceInfo
import com.snapconverter.engine.progress.EncodeProgressListener
import com.snapconverter.engine.quality.QualityAnalyzer

/**
 * SSIM target search. Delegates to [QualityTargetCalibrator].
 */
class SsimTargetCalibrator(
    context: Context,
    selector: HardwareCodecSelector,
    policy: CompressionPolicy,
    quality: QualityAnalyzer,
) {
    private val inner = QualityTargetCalibrator(context, selector, policy, quality)

    fun calibrate(
        input: Uri,
        source: VideoSourceInfo,
        decoder: CodecCandidate,
        encoder: CodecCandidate,
        request: CompressionRequest,
        progress: EncodeProgressListener?,
    ): SsimLadder.Result = inner.calibrate(
        input = input,
        source = source,
        decoder = decoder,
        encoder = encoder,
        request = request,
        metric = QualityTargetMetric.SSIM,
        target = request.targetSsim,
        progress = progress,
    )
}
