package com.snapconverter.engine.codec

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.EncoderCapabilities

data class CodecCandidate(
    val name: String,
    val mime: String,
    val isEncoder: Boolean,
    val hardwareAccelerated: Boolean,
    val softwareOnly: Boolean,
    val vendor: Boolean,
    val vendorFamily: VendorFamily,
    val maxWidth: Int,
    val maxHeight: Int,
    val bitrateModes: Set<Int>,
    val qualityRange: IntRange?,
    val complexityRange: IntRange?,
    val profileLevels: List<Pair<Int, Int>> = emptyList(),
    val vendorParameters: List<String> = emptyList(),
) {
    val isQualcomm: Boolean get() = vendorFamily == VendorFamily.QUALCOMM

    fun supportsBitrateMode(mode: Int): Boolean = mode in bitrateModes

    fun supportsProfile(profile: Int): Boolean =
        profileLevels.isEmpty() || profileLevels.any { it.first == profile }

    fun highestLevelFor(profile: Int): Int? =
        profileLevels.filter { it.first == profile }.maxByOrNull { it.second }?.second

    fun supportsSize(width: Int, height: Int): Boolean {
        if (maxWidth <= 0 || maxHeight <= 0) return true
        val w = maxOf(width, height)
        val h = minOf(width, height)
        val maxW = maxOf(maxWidth, maxHeight)
        val maxH = minOf(maxWidth, maxHeight)
        return w <= maxW && h <= maxH
    }

    companion object {
        fun from(info: MediaCodecInfo, mime: String): CodecCandidate {
            val caps: CodecCapabilities? = runCatching { info.getCapabilitiesForType(mime) }.getOrNull()
            val video = caps?.videoCapabilities
            val encoder: EncoderCapabilities? = caps?.encoderCapabilities
            val modes = buildSet {
                if (encoder != null) {
                    if (encoder.isBitrateModeSupported(EncoderCapabilities.BITRATE_MODE_VBR)) {
                        add(EncoderCapabilities.BITRATE_MODE_VBR)
                    }
                    if (encoder.isBitrateModeSupported(EncoderCapabilities.BITRATE_MODE_CBR)) {
                        add(EncoderCapabilities.BITRATE_MODE_CBR)
                    }
                    if (encoder.isBitrateModeSupported(EncoderCapabilities.BITRATE_MODE_CQ)) {
                        add(EncoderCapabilities.BITRATE_MODE_CQ)
                    }
                }
            }
            val quality = encoder?.qualityRange?.let { it.lower..it.upper }
            val complexity = encoder?.complexityRange?.let { it.lower..it.upper }
            val profileLevels = caps?.profileLevels?.map { it.profile to it.level }.orEmpty()
            return CodecCandidate(
                name = info.name,
                mime = mime,
                isEncoder = info.isEncoder,
                hardwareAccelerated = info.isHardwareAccelerated,
                softwareOnly = info.isSoftwareOnly,
                vendor = info.isVendor,
                vendorFamily = CodecName.vendorFamily(info.name),
                maxWidth = video?.supportedWidths?.upper ?: 0,
                maxHeight = video?.supportedHeights?.upper ?: 0,
                bitrateModes = modes,
                qualityRange = quality,
                complexityRange = complexity,
                profileLevels = profileLevels,
            )
        }
    }
}
