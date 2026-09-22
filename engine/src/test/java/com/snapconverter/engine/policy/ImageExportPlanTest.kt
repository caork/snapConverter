package com.snapconverter.engine.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageExportPlanTest {

    private val policy = CompressionPolicy()
    private val source = ImageSourceInfo(
        width = 4000,
        height = 3000,
        rotation = 0,
        mime = "image/jpeg",
    )

    @Test
    fun customSizeOverridesPresetAndAlignsEven() {
        val request = CompressionRequest(
            kind = MediaKind.IMAGE,
            mode = CompressionMode.QUALITY,
            imageCodec = OutputImageCodec.AVIF,
            resolution = OutputResolution.FHD_1080,
            imageCustomWidth = 1234,
            imageCustomHeight = 567,
        )
        val plan = policy.planImage(source, request)
        assertEquals(1234, plan.width)
        assertEquals(566, plan.height)
        assertEquals(OutputImageCodec.AVIF, plan.codec)
    }

    @Test
    fun presetUsedWhenCustomSizeAbsent() {
        val request = CompressionRequest(
            kind = MediaKind.IMAGE,
            mode = CompressionMode.QUALITY,
            resolution = OutputResolution.FHD_1080,
        )
        val plan = policy.planImage(source, request)
        assertEquals(1920, plan.width)
        assertEquals(1440, plan.height)
    }

    @Test
    fun customSizeFloorsToEvenAndStaysPositive() {
        val request = CompressionRequest(
            kind = MediaKind.IMAGE,
            mode = CompressionMode.QUALITY,
            imageCustomWidth = 3,
            imageCustomHeight = 5,
        )
        val plan = policy.planImage(source, request)
        // Odd sizes floor to the nearest even value (encoder requirement).
        assertEquals(2, plan.width)
        assertEquals(4, plan.height)
    }
}
