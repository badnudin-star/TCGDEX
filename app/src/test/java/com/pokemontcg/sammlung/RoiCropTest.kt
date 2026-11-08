package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.ui.cards.computeRoiCropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoiCropTest {

    @Test
    fun centeredFrameMapsToCenteredCropWithCoverScaling() {
        // View 1080x2400, bitmap (already rotated upright) 1080x1440: cover scale is
        // 2400/1440 = 1.667, so one view pixel maps to 0.6 bitmap pixels.
        val crop = computeRoiCropRect(
            frameWidthPx = 300,
            frameHeightPx = 420,
            viewWidthPx = 1080,
            viewHeightPx = 2400,
            bitmapWidth = 1080,
            bitmapHeight = 1440
        )
        val scale = 2400f / 1440f
        val expectedLeft = ((1080 - 300) / 2f / scale).toInt()
        val expectedTop = ((2400 - 420) / 2f / scale).toInt()
        assertEquals(expectedLeft, crop.left)
        assertEquals(expectedTop, crop.top)
        assertEquals(((1080 + 300) / 2f / scale).toInt(), crop.right)
        assertEquals(((2400 + 420) / 2f / scale).toInt(), crop.bottom)
        assertTrue(crop.width > 0 && crop.height > 0)
    }

    @Test
    fun narrowerBitmapThanViewScalesUp() {
        // Bitmap narrower than the view: cover scale is driven by width (1080/540 = 2.0).
        val crop = computeRoiCropRect(
            frameWidthPx = 600,
            frameHeightPx = 800,
            viewWidthPx = 1080,
            viewHeightPx = 1000,
            bitmapWidth = 540,
            bitmapHeight = 1000
        )
        // Frame spans 240..840 / 100..900 on screen -> halved bitmap coordinates.
        assertEquals(120, crop.left)
        assertEquals(420, crop.right)
        assertEquals(50, crop.top)
        assertEquals(450, crop.bottom)
    }

    @Test
    fun invalidSizesFallBackToTheFullBitmap() {
        val full = computeRoiCropRect(
            frameWidthPx = 0,
            frameHeightPx = 420,
            viewWidthPx = 1080,
            viewHeightPx = 2400,
            bitmapWidth = 1080,
            bitmapHeight = 1440
        )
        assertEquals(0, full.left)
        assertEquals(0, full.top)
        assertEquals(1080, full.right)
        assertEquals(1440, full.bottom)
    }

    @Test
    fun cropIsClampedToBitmapBoundsWhenTheFrameIsLargerThanTheView() {
        val crop = computeRoiCropRect(
            frameWidthPx = 2000,
            frameHeightPx = 3000,
            viewWidthPx = 1080,
            viewHeightPx = 2400,
            bitmapWidth = 1080,
            bitmapHeight = 1440
        )
        assertTrue(crop.left >= 0 && crop.right <= 1080)
        assertTrue(crop.top >= 0 && crop.bottom <= 1440)
        // Vertically the frame covers the whole view, so the full bitmap height is kept.
        assertEquals(1440, crop.height)
        assertTrue(crop.width > 0)
    }
}
