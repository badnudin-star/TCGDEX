package com.pokemontcg.sammlung.ui.cards

import android.graphics.Bitmap

/**
 * Perceptual image hash for card-image fallback (Variante 1: dHash 64-bit).
 *
 * OCR reads number+name well on clean prints, but fails on holo/glare/JP
 * where the artwork is still distinctive. The dHash survives cropping,
 * mild rotation and brightness better than exact pixel compare and is
 * cheap enough to run per frame (~9×8 grayscale + 64 comparisons).
 *
 * Stored in the `cards.imageHash` column as 16-char lowercase hex
 * (`0000000000000000` .. `ffffffffffffffff`). The catalog builder uses
 * the same Python implementation (`tools/catalog-builder/build_catalog.py`).
 *
 * Threshold: 10-12 bits Hamming distance for “same card” under our resize
 * (bilinear 9×8). The repo allows up to 12 for the fallback, exact match
 * is preferred but not required under glare.
 */
object ImageHash {
    const val DEFAULT_MAX_DISTANCE = 10
    const val FALLBACK_MAX_DISTANCE = 12
    const val HASH_HEX_LENGTH = 16

    /**
     * dHash horizontal 8×8 over 9×8 grayscale.
     * Returns 16-char hex or null on invalid bitmap.
     */
    fun computeDHash(bitmap: Bitmap?): String? {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
        // 9×8 so each of the 8 columns has a right neighbor to compare.
        val scaled = try {
            Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        } catch (_: Exception) {
            return null
        }
        try {
            val pixels = IntArray(9 * 8)
            scaled.getPixels(pixels, 0, 9, 0, 0, 9, 8)
            var hash = 0L
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val left = pixels[y * 9 + x]
                    val right = pixels[y * 9 + x + 1]
                    val gl = toGray(left)
                    val gr = toGray(right)
                    if (gl < gr) {
                        val bitPos = y * 8 + x // 0 = MSB
                        hash = hash or (1L shl (63 - bitPos))
                    }
                }
            }
            return hash.toHexString()
        } finally {
            if (scaled !== bitmap) {
                try { scaled.recycle() } catch (_: Exception) {}
            }
        }
    }

    fun toGray(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        // ITU-R 601-2 luma — same as PIL Image.convert('L')
        return ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt()
    }

    fun Long.toHexString(): String =
        java.lang.Long.toHexString(this).padStart(16, '0').lowercase()

    fun isValidHash(hash: String?): Boolean {
        if (hash == null) return false
        if (hash.length != 16) return false
        return hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    fun normalizeHash(hash: String): String = hash.lowercase()

    fun hexToLong(hash: String): Long {
        // Unsigned 64-bit stored as hex; Kotlin Long is signed but bit ops are identical.
        return java.lang.Long.parseUnsignedLong(hash.lowercase(), 16)
    }

    fun hammingDistance(a: String, b: String): Int {
        if (!isValidHash(a) || !isValidHash(b)) return Int.MAX_VALUE
        val la = hexToLong(a)
        val lb = hexToLong(b)
        return java.lang.Long.bitCount(la xor lb)
    }

    /** True when distance is within the allowed threshold for a fallback match. */
    fun isSimilar(queryHash: String, candidateHash: String, maxDistance: Int = DEFAULT_MAX_DISTANCE): Boolean =
        hammingDistance(queryHash, candidateHash) <= maxDistance
}
