package com.snapconverter.engine.device

import android.os.Build
import com.snapconverter.engine.codec.HardwareCodecSelector
import com.snapconverter.engine.codec.MimeTypes
import com.snapconverter.engine.codec.VendorFamily

class DeviceCapabilityProbe(
    private val selector: HardwareCodecSelector = HardwareCodecSelector(
        HardwareCodecSelector.Flags(
            requireHardware = true,
            requireVendor = false,
            preferQualcomm = true,
            requireQualcommEncoder = false,
            allowSoftware = false,
        ),
    ),
) {
    fun probe(): DeviceCapabilityReport {
        val encoders = selector.listCandidates(encoder = true)
        val decoders = selector.listCandidates(encoder = false)
        val videoEnc = encoders.filter { it.mime.startsWith("video/") }
        val videoDec = decoders.filter { it.mime.startsWith("video/") }
        val qEnc = videoEnc.filter { it.vendorFamily == VendorFamily.QUALCOMM }
        val qDec = videoDec.filter { it.vendorFamily == VendorFamily.QUALCOMM }
        val jpeg = selector.findJpegHardwareEncoder()
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else ""
        val hardware = Build.HARDWARE
        val likelySnapdragon = listOf(soc, hardware, Build.BOARD, Build.HARDWARE)
            .any { it.contains("qcom", true) || it.contains("lahaina") || it.contains("taro") ||
                it.contains("kalama") || it.contains("pineapple") || it.contains("sun") }

        val notes = buildList {
            if (qEnc.isEmpty()) {
                add("No Qualcomm video encoder enumerated. V1 will refuse to encode.")
            }
            if (jpeg == null) {
                add("No public hardware JPEG encoder. JPEG output is disabled; use HEIC.")
            }
            if (Build.VERSION.SDK_INT < 31) {
                add("API ${Build.VERSION.SDK_INT}: vendor extension probe requires API 31+.")
            }
            if (videoEnc.none { it.mime.equals(MimeTypes.AV1, true) }) {
                add("No hardware AV1 encoder. AV1 output is hidden.")
            }
        }

        return DeviceCapabilityReport(
            manufacturer = Build.MANUFACTURER,
            device = "${Build.BRAND} ${Build.MODEL}",
            hardware = hardware,
            socModel = soc,
            sdkInt = Build.VERSION.SDK_INT,
            likelySnapdragon = likelySnapdragon || qEnc.isNotEmpty(),
            hasQualcommEncoder = qEnc.isNotEmpty(),
            hasQualcommDecoder = qDec.isNotEmpty(),
            hardwareHevcEncoder = qEnc.any { it.mime.equals(MimeTypes.HEVC, true) },
            hardwareAvcEncoder = qEnc.any { it.mime.equals(MimeTypes.AVC, true) },
            hardwareAv1Encoder = qEnc.any { it.mime.equals(MimeTypes.AV1, true) },
            hardwareJpegEncoder = jpeg != null,
            hardwareHeicPath = qEnc.any { it.mime.equals(MimeTypes.HEVC, true) },
            vendorExtensionsApi = Build.VERSION.SDK_INT >= 31,
            encoders = videoEnc + encoders.filter { it.mime.startsWith("image/") },
            decoders = videoDec,
            jpegEncoderName = jpeg?.name,
            notes = notes,
        )
    }
}
