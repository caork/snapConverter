package com.snapconverter.engine.codec

import android.media.MediaCodec
import android.media.MediaCodecList
import android.os.Build
import com.snapconverter.engine.HardwareDecoderRequiredException
import com.snapconverter.engine.HardwareEncoderRequiredException
import com.snapconverter.engine.QualcommEncoderRequiredException
import com.snapconverter.engine.SoftwareCodecRejectedException

/**
 * Enumerates [MediaCodecList] and picks a codec by policy.
 *
 * Never use [MediaCodec.createEncoderByType] as the source of truth.
 * Finish with [MediaCodec.createByCodecName].
 */
class HardwareCodecSelector(
    private val flags: Flags = Flags(),
    private val codecInfos: () -> Array<android.media.MediaCodecInfo> = {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
    },
) {

    data class Flags(
        val requireHardware: Boolean = true,
        val requireVendor: Boolean = true,
        val preferQualcomm: Boolean = true,
        /** V1 product rule: video encode must be a Qualcomm codec. */
        val requireQualcommEncoder: Boolean = true,
        val allowSoftware: Boolean = false,
    )

    fun listCandidates(encoder: Boolean, mime: String? = null): List<CodecCandidate> {
        return codecInfos()
            .asSequence()
            .filter { it.isEncoder == encoder }
            .flatMap { info ->
                val types = info.supportedTypes
                val selected = if (mime == null) {
                    types.asList()
                } else {
                    types.filter { it.equals(mime, ignoreCase = true) }
                }
                selected.map { type -> CodecCandidate.from(info, type) }
            }
            .filter { candidate -> accept(candidate) }
            .sortedWith(compareByDescending<CodecCandidate> { score(it) })
            .toList()
    }

    fun selectEncoder(mime: String, width: Int = 0, height: Int = 0): CodecCandidate {
        val all = listCandidates(encoder = true, mime = mime)
        val sized = if (width > 0 && height > 0) {
            all.filter { it.supportsSize(width, height) }.ifEmpty { all }
        } else {
            all
        }
        val picked = sized.firstOrNull()
            ?: throw if (flags.requireQualcommEncoder) {
                QualcommEncoderRequiredException(
                    mime,
                    codecInfos()
                        .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, true) } }
                        .map { it.name },
                )
            } else {
                HardwareEncoderRequiredException(mime)
            }
        rejectSoftware(picked)
        if (flags.requireQualcommEncoder && !picked.isQualcomm) {
            throw QualcommEncoderRequiredException(mime, sized.map { it.name })
        }
        return picked
    }

    fun selectDecoder(mime: String): CodecCandidate {
        val picked = listCandidates(encoder = false, mime = mime).firstOrNull()
            ?: throw HardwareDecoderRequiredException(mime)
        rejectSoftware(picked)
        return picked
    }

    fun selectPreferredVideoEncoder(
        preferredMimes: List<String>,
        width: Int,
        height: Int,
    ): CodecCandidate {
        val errors = mutableListOf<String>()
        for (mime in preferredMimes) {
            try {
                return selectEncoder(mime, width, height)
            } catch (t: Exception) {
                errors += "${mime}: ${t.message}"
            }
        }
        throw HardwareEncoderRequiredException(
            preferredMimes.joinToString() + " (" + errors.joinToString() + ")",
        )
    }

    fun hasQualcommVideoEncoder(): Boolean =
        listCandidates(encoder = true).any { it.isQualcomm && it.mime.startsWith("video/") }

    fun hasHardwareHeicEncoder(): Boolean =
        listCandidates(encoder = true, mime = MimeTypes.HEVC).any { it.hardwareAccelerated } ||
            listCandidates(encoder = true, mime = MimeTypes.HEIC).any { it.hardwareAccelerated }

    fun findJpegHardwareEncoder(): CodecCandidate? {
        val mimes = listOf(MimeTypes.JPEG, "image/vnd.qcom.jpeg")
        return mimes.firstNotNullOfOrNull { mime ->
            listCandidates(encoder = true, mime = mime)
                .firstOrNull { it.hardwareAccelerated && !it.softwareOnly }
        }
    }

    fun createByName(candidate: CodecCandidate): MediaCodec {
        rejectSoftware(candidate)
        if (candidate.isEncoder && flags.requireQualcommEncoder && !candidate.isQualcomm) {
            throw QualcommEncoderRequiredException(candidate.mime, listOf(candidate.name))
        }
        return MediaCodec.createByCodecName(candidate.name)
    }

    fun probeVendorParameters(codec: MediaCodec): List<String> {
        if (Build.VERSION.SDK_INT < 31) return emptyList()
        return runCatching { codec.supportedVendorParameters.toList() }.getOrDefault(emptyList())
    }

    private fun accept(candidate: CodecCandidate): Boolean {
        if (!flags.allowSoftware) {
            if (candidate.softwareOnly) return false
            if (CodecName.isGoogleAosp(candidate.name)) return false
            if (CodecName.looksLikeSoftware(candidate.name)) return false
        }
        if (flags.requireHardware && !candidate.hardwareAccelerated) return false
        if (flags.requireVendor && !candidate.vendor) {
            // Qualcomm codecs are always vendor; keep this as a belt-and-suspenders filter.
            if (!candidate.isQualcomm) return false
        }
        if (candidate.isEncoder && flags.requireQualcommEncoder && !candidate.isQualcomm) {
            return false
        }
        return true
    }

    private fun score(candidate: CodecCandidate): Int {
        var s = 0
        if (candidate.hardwareAccelerated) s += 50
        if (candidate.vendor) s += 20
        if (candidate.isQualcomm && flags.preferQualcomm) s += 100
        if (candidate.name.contains("c2.qti", ignoreCase = true)) s += 15
        if (candidate.supportsBitrateMode(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)) {
            s += 8
        }
        if (candidate.softwareOnly) s -= 1000
        return s
    }

    private fun rejectSoftware(candidate: CodecCandidate) {
        if (flags.allowSoftware) return
        if (candidate.softwareOnly || CodecName.isGoogleAosp(candidate.name) ||
            CodecName.looksLikeSoftware(candidate.name)
        ) {
            throw SoftwareCodecRejectedException(candidate.name)
        }
        if (flags.requireHardware && !candidate.hardwareAccelerated) {
            throw SoftwareCodecRejectedException(candidate.name)
        }
    }
}
