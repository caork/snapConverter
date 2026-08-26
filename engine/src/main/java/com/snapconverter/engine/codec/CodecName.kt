package com.snapconverter.engine.codec

/**
 * Pure-Kotlin classification of [MediaCodec] names.
 * Intentionally has no Android imports so JVM unit tests can cover it.
 */
object CodecName {

    fun vendorFamily(codecName: String): VendorFamily {
        val n = codecName.lowercase()
        return when {
            n.contains("qti") || n.contains("qcom") || n.contains("qualcomm") ->
                VendorFamily.QUALCOMM
            n.contains("mtk") || n.contains("mediatek") || n.startsWith("c2.mtk") ->
                VendorFamily.MEDIATEK
            n.contains("exynos") || n.contains("hisi") || n.contains("c2.sec") ->
                VendorFamily.EXYNOS
            isGoogleAosp(codecName) -> VendorFamily.GOOGLE_SOFTWARE
            else -> VendorFamily.UNKNOWN
        }
    }

    fun isGoogleAosp(codecName: String): Boolean {
        val n = codecName.lowercase()
        return n.startsWith("c2.android.") ||
            n.startsWith("omx.google.") ||
            n.startsWith("c2.google.")
    }

    fun isQualcomm(codecName: String): Boolean =
        vendorFamily(codecName) == VendorFamily.QUALCOMM

    fun looksLikeSoftware(codecName: String): Boolean {
        val n = codecName.lowercase()
        return isGoogleAosp(codecName) ||
            n.contains(".sw.") ||
            n.contains("soft") ||
            n.contains("ffmpeg") ||
            n.contains("x264") ||
            n.contains("x265")
    }
}

enum class VendorFamily {
    QUALCOMM,
    MEDIATEK,
    EXYNOS,
    GOOGLE_SOFTWARE,
    UNKNOWN,
}
