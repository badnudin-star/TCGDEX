package com.pokemontcg.sammlung.ui.cards

import org.junit.Assert.*
import org.junit.Test

class ImageHashTest {

    private fun solidPixels(color: Int): IntArray = IntArray(72) { color }

    private fun gradientPixels(): IntArray {
        val arr = IntArray(72)
        for (y in 0 until 8) {
            for (x in 0 until 9) {
                val gray = (x * 255 / 8)
                val col = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                arr[y * 9 + x] = col
            }
        }
        return arr
    }

    @Test
    fun `computeDHashFromPixels - solid color is deterministic`() {
        // 9x8 solid white -> all comparisons equal -> all zeros
        val white = (0xFF shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF
        val hash = ImageHash.computeDHashFromPixels(solidPixels(white))
        assertNotNull(hash)
        assertEquals(16, hash!!.length)
        assertTrue(ImageHash.isValidHash(hash))
        assertEquals("0000000000000000", hash)
    }

    @Test
    fun `computeDHashFromPixels - gradient left to right yields all ones`() {
        val hash = ImageHash.computeDHashFromPixels(gradientPixels())
        assertEquals("ffffffffffffffff", hash)
    }

    @Test
    fun `computeDHash - null input returns null`() {
        assertNull(ImageHash.computeDHash(null))
        assertNull(ImageHash.computeDHashFromPixels(IntArray(10)))
        assertNull(ImageHash.computeDHashFromPixels(IntArray(0)))
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
        assertFalse(ImageHash.isValidHash("g000000000000000"))
        assertFalse(ImageHash.isValidHash("00000000000000000"))
        assertTrue(ImageHash.isValidHash("ABCDEF1234567890"))
        assertTrue(ImageHash.isValidHash("abcdef1234567890"))
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
        assertFalse(ImageHash.isSimilar("0000000000000000", "0000000000000003", 1))
    }

    @Test
    fun `dHash stable across same pixels`() {
        val h1 = ImageHash.computeDHashFromPixels(gradientPixels())
        val h2 = ImageHash.computeDHashFromPixels(gradientPixels())
        assertEquals(h1, h2)
    }

    @Test
    fun `toGray - known values`() {
        assertEquals(0, ImageHash.toGray(0xFF000000.toInt()))
        assertEquals(255, ImageHash.toGray(0xFFFFFFFF.toInt()))
        // Pure red ~76, green ~149, blue ~29 by luma
        assertEquals(76, ImageHash.toGray(0xFFFF0000.toInt()))
        assertEquals(149, ImageHash.toGray(0xFF00FF00.toInt()))
        assertEquals(29, ImageHash.toGray(0xFF0000FF.toInt()))
    }
}
