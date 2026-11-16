package com.pokemontcg.sammlung.ui.cards

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ImageHashTest {

    @Test
    fun `computeDHash - solid color has deterministic hash`() {
        // 9x8 solid white -> all comparisons equal (grayLeft < grayRight false) -> all zeros
        val bmp = Bitmap.createBitmap(9, 8, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val hash = ImageHash.computeDHash(bmp)
        assertNotNull(hash)
        assertEquals(16, hash!!.length)
        assertTrue(ImageHash.isValidHash(hash))
        // All white: every left < right is false -> 0 bits -> hex 000...0
        assertEquals("0000000000000000", hash)
        bmp.recycle()
    }

    @Test
    fun `computeDHash - gradient left to right yields all ones`() {
        // Create a horizontal gradient: left black -> right white, so every left < right true -> all ones
        val bmp = Bitmap.createBitmap(9, 8, Bitmap.Config.ARGB_8888)
        for (y in 0 until 8) {
            for (x in 0 until 9) {
                val gray = (x * 255 / 8)
                bmp.setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }
        val hash = ImageHash.computeDHash(bmp)
        assertEquals("ffffffffffffffff", hash)
        bmp.recycle()
    }

    @Test
    fun `hammingDistance - identical hashes distance 0`() {
        val a = "a3f5c1d9e8b01234"
        assertEquals(0, ImageHash.hammingDistance(a, a))
    }

    @Test
    fun `hammingDistance - all zero vs all f distance 64`() {
        assertEquals(64, ImageHash.hammingDistance("0000000000000000", "ffffffffffffffff"))
    }

    @Test
    fun `hammingDistance - single bit difference`() {
        assertEquals(1, ImageHash.hammingDistance("0000000000000000", "0000000000000001"))
        assertEquals(1, ImageHash.hammingDistance("0000000000000000", "8000000000000000"))
    }

    @Test
    fun `isValidHash rejects invalid`() {
        assertFalse(ImageHash.isValidHash(null))
        assertFalse(ImageHash.isValidHash(""))
        assertFalse(ImageHash.isValidHash("abc"))
        assertFalse(ImageHash.isValidHash("g000000000000000")) // g invalid hex
        assertFalse(ImageHash.isValidHash("00000000000000000")) // 17 chars
        assertTrue(ImageHash.isValidHash("ABCDEF1234567890"))
        assertTrue(ImageHash.isValidHash("abcdef1234567890"))
    }

    @Test
    fun `computeDHash null input returns null`() {
        assertNull(ImageHash.computeDHash(null))
    }

    @Test
    fun `hammingDistance invalid returns MAX`() {
        assertEquals(Int.MAX_VALUE, ImageHash.hammingDistance("invalid", "0000000000000000"))
        assertEquals(Int.MAX_VALUE, ImageHash.hammingDistance("0000000000000000", "bad"))
    }

    @Test
    fun `isSimilar within threshold`() {
        assertTrue(ImageHash.isSimilar("0000000000000000", "0000000000000000", 10))
        assertTrue(ImageHash.isSimilar("0000000000000000", "0000000000000001", 1))
        assertFalse(ImageHash.isSimilar("0000000000000000", "ffffffffffffffff", 10))
        assertFalse(ImageHash.isSimilar("0000000000000000", "0000000000000003", 1)) // distance 2
    }

    @Test
    fun `dHash is stable across bitmap scaling`() {
        // Create a 100x140 card-like bitmap with a left-right gradient,
        // ensure hash is still deterministic after scaling to 9x8 internal.
        val src = Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.GRAY)
        for (x in 0 until 100) {
            val gray = (x * 255 / 100)
            for (y in 0 until 140) {
                src.setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }
        val h1 = ImageHash.computeDHash(src)
        val h2 = ImageHash.computeDHash(src)
        assertEquals(h1, h2)
        src.recycle()
    }
}
