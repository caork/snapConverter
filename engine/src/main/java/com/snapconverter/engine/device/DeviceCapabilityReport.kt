package com.snapconverter.engine.device

import com.snapconverter.engine.codec.CodecCandidate

data class DeviceCapabilityReport(
    val manufacturer: String,
    val device: String,
    val hardware: String,
    val socModel: String,
    val sdkInt: Int,
    val likelySnapdragon: Boolean,
    val hasQualcommEncoder: Boolean,
    val hasQualcommDecoder: Boolean,
    val hardwareHevcEncoder: Boolean,
    val hardwareAvcEncoder: Boolean,
    val hardwareAv1Encoder: Boolean,
    val hardwareJpegEncoder: Boolean,
    val hardwareHeicPath: Boolean,
    val vendorExtensionsApi: Boolean,
    val encoders: List<CodecCandidate>,
    val decoders: List<CodecCandidate>,
    val jpegEncoderName: String?,
    val notes: List<String>,
) {
    val v1Supported: Boolean
        get() = hasQualcommEncoder && (hardwareHevcEncoder || hardwareAvcEncoder)

    val summaryLine: String
        get() = when {
            !v1Supported -> "This device cannot run SnapConverter V1 (Qualcomm hardware encoder required)."
            hardwareHevcEncoder -> "Qualcomm hardware HEVC encoder ready."
            else -> "Qualcomm hardware AVC encoder ready (HEVC not enumerated)."
        }
}
