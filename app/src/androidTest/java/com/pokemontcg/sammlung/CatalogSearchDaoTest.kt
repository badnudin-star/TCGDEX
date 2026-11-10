package com.pokemontcg.sammlung

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokemontcg.sammlung.data.local.AppDatabase
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.entities.CardPokemonEntity
import com.pokemontcg.sammlung.data.repository.CatalogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the wishlist catalog search. The query mixes an optional rarity
 * multi-select, an optional price ceiling and a dex-id ordering, which only a real SQLite engine
 * can verify.
 */
@RunWith(AndroidJUnit4::class)
class CatalogSearchDaoTest {

    private lateinit var appDb: AppDatabase
    private lateinit var repository: CatalogRepository

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = CatalogRepository(appDb)
    }

    @After
    fun closeDb() {
        appDb.close()
    }

    private fun card(
        id: String,
        name: String = id,
        setName: String = "Set",
        number: String = "1",
        rarity: String? = "Common",
        price: Double? = 5.0,
        releaseDate: String = "2020/01/01",
        sortNumber: Int = 1
    ) = CardEntity(
        id = id,
        name = name,
        supertype = "Pokémon",
        number = number,
        rarity = rarity,
        setId = "set-1",
        setName = setName,
        series = "Series",
        imageUrl = null,
        largeImageUrl = null,
        marketPrice = price,
        lowPrice = null,
        trendPrice = null,
        nationalPokedexNumber = null,
        subtypes = null,
        language = "de",
        setReleaseDate = releaseDate,
        sortNumber = sortNumber
    )

    private suspend fun seed(cards: List<CardEntity>, links: List<CardPokemonEntity> = emptyList()) {
        appDb.cardDao().insertAll(cards)
        if (links.isNotEmpty()) appDb.cardPokemonDao().insertAll(links)
    }

    @Test
    fun rarityMultiSelectAndPriceCeilingFilterTogether(): Unit = runBlocking {
        seed(
            listOf(
                card("common-cheap", rarity = "Common", price = 2.0),
                card("common-expensive", rarity = "Common", price = 40.0),
                card("ultra-cheap", rarity = "Ultra Rare", price = 9.0),
                card("rare-cheap", rarity = "Rare", price = 3.0),
                card("no-rarity", rarity = null, price = 1.0),
                card("no-price", rarity = "Common", price = null)
            )
        )

        val hits = repository.searchCatalogCards(
            query = "",
            rarities = setOf("Common", "Ultra Rare"),
            maxPrice = 10.0
        ).first()

        assertEquals(listOf("common-cheap", "ultra-cheap"), hits.map { it.cardId }.sorted())
    }

    @Test
    fun emptyRaritySelectionDoesNotBreakTheQuery(): Unit = runBlocking {
        seed(listOf(card("a"), card("b", rarity = "Rare")))

        val hits = repository.searchCatalogCards(query = "", rarities = emptySet(), maxPrice = null).first()

        assertEquals(2, hits.size)
    }

    @Test
    fun resultsAreOrderedByDexIdAndCardsWithoutLinkComeLast(): Unit = runBlocking {
        seed(
            cards = listOf(
                card("trainer", name = "Professor"),
                card("charizard", name = "Charizard"),
                card("pikachu", name = "Pikachu")
            ),
            links = listOf(
                CardPokemonEntity(cardId = "pikachu", dexId = 25),
                CardPokemonEntity(cardId = "charizard", dexId = 6)
            )
        )

        val hits = repository.searchCatalogCards(query = "", rarities = emptySet(), maxPrice = null).first()

        assertEquals(listOf("charizard", "pikachu", "trainer"), hits.map { it.cardId })
        assertEquals(listOf(6, 25, null), hits.map { it.dexId })
    }

    @Test
    fun textQueryMatchesNameSetAndNumber(): Unit = runBlocking {
        seed(
            listOf(
                card("by-name", name = "Pikachu ex", setName = "Base", number = "25/102"),
                card("by-set", name = "Rattfratz", setName = "Paldea Evolved", number = "100/198"),
                card("by-number", name = "Taubsi", setName = "Base", number = "025/102"),
                card("unrelated", name = "Karpador", setName = "Base", number = "129/102")
            )
        )

        val byName = repository.searchCatalogCards("pikachu", emptySet(), null).first()
        assertEquals(listOf("by-name"), byName.map { it.cardId })

        val bySet = repository.searchCatalogCards("paldea", emptySet(), null).first()
        assertEquals(listOf("by-set"), bySet.map { it.cardId })

        val byNumber = repository.searchCatalogCards("129", emptySet(), null).first()
        assertEquals(listOf("unrelated"), byNumber.map { it.cardId })

        assertTrue(repository.searchCatalogCards("gibt-es-nicht", emptySet(), null).first().isEmpty())
    }

    @Test
    fun rarityListIsCompleteAndSorted(): Unit = runBlocking {
        seed(
            listOf(
                card("a", rarity = "Ultra Rare"),
                card("b", rarity = "Common"),
                card("c", rarity = "Common"),
                card("d", rarity = null),
                card("e", rarity = "   ")
            )
        )

        assertEquals(listOf("Common", "Ultra Rare"), repository.getAllRarities().first())
    }

    @Test
    fun prefixedScannerNumberMatchesAsianPromoSetIdAndPlainNumber(): Unit = runBlocking {
        // Japanese promo cards live in set "SV-P" with a plain digit number, while the
        // scanner reads the printed "SV-P 001".
        seed(
            listOf(
                card("ja-promo", name = "ピカチュウ", setName = "プロモカード", number = "001", rarity = "Promo").copy(
                    id = "ja:sv-p-001", setId = "SV-P", language = "ja"
                ),
                card("de-normal", name = "Pikachu", setName = "Karmesin & Purpur", number = "001").copy(
                    id = "sv01-1", setId = "sv01", language = "de"
                )
            )
        )

        val hits = repository.getCardsByNumber("SV-P001")
        assertEquals(listOf("ja:sv-p-001"), hits.map { it.id })

        // Leading zeros in the printed number must not break the match either.
        val shortTail = repository.getCardsByNumber("SV-P1")
        assertEquals(listOf("ja:sv-p-001"), shortTail.map { it.id })
    }

    @Test
    fun ocrTextWithoutAccentsStillFindsAccentedCardNames(): Unit = runBlocking {
        seed(
            listOf(
                card("fr-evoli", name = "Évoli", setName = "Épée et Bouclier", number = "057/202").copy(
                    id = "fr:swh01-57", setId = "swh01", language = "fr"
                ),
                card("de-pikachu", name = "Pikachu", setName = "Karmesin & Purpur", number = "025/198").copy(
                    id = "sv01-25", setId = "sv01", language = "de"
                )
            )
        )

        // OCR dropped the accent: "EVOLI" must still find "Évoli" via the folded retry.
        val byFoldedName = repository.getCardsByOcrText("EVOLI")
        assertEquals(listOf("fr:swh01-57"), byFoldedName.map { it.id })

        // The accented set name is reachable the same way.
        val byFoldedSet = repository.getCardsByOcrText("EPEE")
        assertEquals(listOf("fr:swh01-57"), byFoldedSet.map { it.id })
    }
}
