package com.snapconverter.engine.codec

object MimeTypes {
    const val AVC = "video/avc"
    const val HEVC = "video/hevc"
    const val AV1 = "video/av01"
    const val VP9 = "video/x-vnd.on2.vp9"
    const val MPEG4 = "video/mp4v-es"
    const val JPEG = "image/jpeg"
    const val HEIC = "image/vnd.android.heic"
    const val HEIF = "image/heif"
    const val AVIF = "image/avif"
    const val AAC = "audio/mp4a-latm"

    val VIDEO_ENCODE_PRIORITY = listOf(HEVC, AVC, AV1)
}
