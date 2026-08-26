package com.snapconverter.engine.codec

/**
 * Qualcomm MediaCodec vendor parameter keys.
 *
 * These are the public `vendor.qti-ext-*` strings documented by Qualcomm
 * (QtiVideoExt / QMediaExtensions) and by Qualcomm's MediaCodec vendor
 * extensions guide. They are protocol names, not a vendored copy of QTI source.
 *
 * Always probe with [android.media.MediaCodec.getSupportedVendorParameters]
 * (API 31+) before writing a key. Support varies by SoC and firmware.
 *
 * @see <a href="https://github.com/quic/android-on-snapdragon">quic/android-on-snapdragon</a>
 */
object QtiVendorParameters {
    const val INIT_QP_I_ENABLE = "vendor.qti-ext-enc-initial-qp.qp-i-enable"
    const val INIT_QP_P_ENABLE = "vendor.qti-ext-enc-initial-qp.qp-p-enable"
    const val INIT_QP_B_ENABLE = "vendor.qti-ext-enc-initial-qp.qp-b-enable"
    const val INIT_QP_I = "vendor.qti-ext-enc-initial-qp.qp-i"
    const val INIT_QP_P = "vendor.qti-ext-enc-initial-qp.qp-p"
    const val INIT_QP_B = "vendor.qti-ext-enc-initial-qp.qp-b"

    const val ROI_INFO_TYPE = "vendor.qti-ext-enc-roiinfo.type"
    const val ROI_RECT_INFO = "vendor.qti-ext-enc-roiinfo.rect-payload"
    const val ROI_RECT_INFO_EXT = "vendor.qti-ext-enc-roiinfo.rect-payload-ext"
    const val ROI_INFO_TIMESTAMP = "vendor.qti-ext-enc-roiinfo.timestamp"
    const val ROI_MAP_MB_SIDE_LENGTH = "vendor.qti-ext-enc-roi-mbmap-info.mb_side_length"
    const val ROI_MAP_MB_QP_BIAS = "vendor.qti-ext-enc-roi-mbmap-info.qp_bias_map"

    const val ADV_QP_BITRATE_MODE = "vendor.qti-ext-enc-bitrate-mode.value"
    const val ADV_QP_FRAME_QP = "vendor.qti-ext-enc-frame-qp.value"

    const val LTR_MAX_FRAMES = "vendor.qti-ext-enc-ltr-count.num-ltr-frames"
    const val LTR_MARK_FRAME = "vendor.qti-ext-enc-ltr.mark-frame"
    const val LTR_USE_FRAME = "vendor.qti-ext-enc-ltr.use-frame"
    const val LTR_RESPONSE = "vendor.qti-ext-enc-info-ltr.ltr-use-mark"

    const val RESYNC_MARKER_SIZE =
        "vendor.qti-ext-enc-error-correction.resync-marker-spacing-bits"
    const val SLICE_SPACING = "vendor.qti-ext-enc-slice.spacing"
    const val PROSIGHT_MODE = "vendor.qti-ext-encoding-mode.value"

    val INITIAL_QP_KEYS = listOf(
        INIT_QP_I_ENABLE,
        INIT_QP_P_ENABLE,
        INIT_QP_B_ENABLE,
        INIT_QP_I,
        INIT_QP_P,
        INIT_QP_B,
    )
}
