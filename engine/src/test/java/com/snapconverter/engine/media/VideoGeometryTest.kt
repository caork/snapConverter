package com.snapconverter.engine.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoGeometryTest {

    @Test
    fun phoneCameraPortraitIs90OnLandscapeBuffers() {
        val rot = VideoGeometry.detectRotation(
            extractorRotation = 0,
            retrieverRotation = 90,
            codedWidth = 1920,
            codedHeight = 1080,
            retrieverWidth = 1080,
            retrieverHeight = 1920,
        )
        assertEquals(90, rot)
        assertEquals(1080, VideoGeometry.displayWidth(1920, 1080, rot))
        assertEquals(1920, VideoGeometry.displayHeight(1920, 1080, rot))
    }

    @Test
    fun infers90WhenRetrieverSizeIsSwappedAndMetadataIsZero() {
        val rot = VideoGeometry.detectRotation(
            extractorRotation = 0,
            retrieverRotation = 0,
            codedWidth = 1920,
            codedHeight = 1080,
            retrieverWidth = 1080,
            retrieverHeight = 1920,
        )
        assertEquals(90, rot)
    }

    @Test
    fun truePortraitCodedStaysPortrait() {
        val rot = VideoGeometry.detectRotation(
            extractorRotation = 0,
            retrieverRotation = 0,
            codedWidth = 1080,
            codedHeight = 1920,
            retrieverWidth = 1080,
            retrieverHeight = 1920,
        )
        assertEquals(0, rot)
        assertTrue(VideoGeometry.displayHeight(1080, 1920, rot) > VideoGeometry.displayWidth(1080, 1920, rot))
    }

    @Test
    fun landscapeUnchanged() {
        val rot = VideoGeometry.detectRotation(
            extractorRotation = 0,
            retrieverRotation = 0,
            codedWidth = 1920,
            codedHeight = 1080,
            retrieverWidth = 1920,
            retrieverHeight = 1080,
        )
        assertEquals(0, rot)
    }

    @Test
    fun glRotationIsClockwiseForAndroidMetadata() {
        assertEquals(-90, VideoGeometry.glRotationDegrees(90))
        assertEquals(-270, VideoGeometry.glRotationDegrees(270))
        assertEquals(0, VideoGeometry.glRotationDegrees(0))
    }
}
