package com.pokemontcg.sammlung.ui.cards

import com.pokemontcg.sammlung.data.local.entities.CardEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

private fun card(id: String, name: String = "Pikachu", number: String = "025/198", setName: String = "Base", language: String = "de", imageHash: String? = null) =
    CardEntity(
        id = id, name = name, supertype = "Pokémon", number = number, rarity = "Common",
        setId = "base1", setName = setName, series = "Base", imageUrl = null, largeImageUrl = null,
        marketPrice = null, lowPrice = null, trendPrice = null,
        nationalPokedexNumber = 25, subtypes = null, language = language,
        nationalPokedexNumbers = null, setLogoUrl = null, setSymbolUrl = null,
        setCardCountOfficial = null, setCardCountTotal = null, setReleaseDate = null,
        sortNumber = 0, detailLoaded = 1, setAbbreviation = null, imageHash = imageHash
    )

class ScannerImageLookupTest {

    @Test
    fun `resolve with image fallback - OCR hit wins`() = runTest {
        val ocrCard = card("c1", imageHash = "aaaaaaaaaaaaaaaa")
        val imageCard = card("c2", imageHash = "bbbbbbbbbbbbbbbb")
        val result = resolveScannerMatchesWithImageFallback(
            detectedNumber = "025/198", rawOcrText = "Pikachu 025/198", imageHash = "bbbbbbbbbbbbbbbb",
            lookupByNumber = { listOf(ocrCard) },
            lookupByOcrText = { listOf(ocrCard) },
            lookupByImageHash = { listOf(imageCard) }
        )
        assertEquals(listOf(ocrCard), result)
    }

    @Test
    fun `resolve with image fallback - OCR miss falls back to image`() = runTest {
        val imageCard = card("img1", imageHash = "1234567890abcdef")
        val result = resolveScannerMatchesWithImageFallback(
            detectedNumber = null, rawOcrText = "blurry holo", imageHash = "1234567890abcdef",
            lookupByNumber = { emptyList() },
            lookupByOcrText = { emptyList() },
            lookupByImageHash = { if (it == "1234567890abcdef") listOf(imageCard) else emptyList() }
        )
        assertEquals(listOf(imageCard), result)
    }

    @Test
    fun `resolve with image fallback - invalid hash no fallback`() = runTest {
        val result = resolveScannerMatchesWithImageFallback(
            detectedNumber = null, rawOcrText = "", imageHash = "not-a-hash",
            lookupByNumber = { emptyList() },
            lookupByOcrText = { emptyList() },
            lookupByImageHash = { error("should not be called") }
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `resolve with image fallback - null hash no fallback`() = runTest {
        val result = resolveScannerMatchesWithImageFallback(
            detectedNumber = null, rawOcrText = "", imageHash = null,
            lookupByNumber = { emptyList() },
            lookupByOcrText = { emptyList() },
            lookupByImageHash = { error("should not be called") }
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `merge OCR and image dedupes and caps`() {
        val a = card("a")
        val b = card("b")
        val c = card("c")
        val merged = mergeOcrAndImageMatches(listOf(a, b), listOf(b, c))
        assertEquals(listOf(a, b, c), merged)
    }

    @Test
    fun `merge caps at limit`() {
        val manyOcr = (1..20).map { card("ocr$it") }
        val manyImg = (1..20).map { card("img$it") }
        val merged = mergeOcrAndImageMatches(manyOcr, manyImg, limit = 30)
        assertEquals(30, merged.size)
    }
}
