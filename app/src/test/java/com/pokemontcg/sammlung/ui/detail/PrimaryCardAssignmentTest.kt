package com.pokemontcg.sammlung.ui.detail

import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.repository.PrimaryCardPersistence
import com.pokemontcg.sammlung.data.repository.planPrimaryCardAssignment
import com.pokemontcg.sammlung.data.repository.primaryCardPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryCardAssignmentTest {

    private val now = 1_700_000_000_000L

    private fun owned(
        id: Long = 7L,
        quantity: Int = 4,
        category: String = "FULLART",
        notes: String? = "Erstausgabe",
        isPrimary: Boolean = false
    ) = UserCardEntityV2(
        id = id,
        cardId = "base1-4",
        pokemonId = "6",
        pokemonDexId = 6,
        language = "de",
        quantity = quantity,
        condition = "NEAR_MINT",
        notes = notes,
        category = category,
        isPrimary = isPrimary,
        addedAt = 1_000L
    )

    @Test
    fun ownedCardIsUpdatedInPlaceAndKeepsItsDetails() {
        val existing = owned()

        val resolved = planPrimaryCardAssignment(
            cardId = "base1-4",
            language = "de",
            pokemonDexId = 6,
            pokemonId = "6",
            existingCard = existing,
            now = now
        )

        assertTrue(resolved.isPrimary)
        assertEquals(existing.id, resolved.id)
        assertEquals(4, resolved.quantity)
        assertEquals("FULLART", resolved.category)
        assertEquals("Erstausgabe", resolved.notes)
        assertEquals("NEAR_MINT", resolved.condition)
        assertEquals(1_000L, resolved.addedAt)

        val persistence = primaryCardPersistence(existing, resolved)
        assertTrue(persistence is PrimaryCardPersistence.Update)
        assertEquals(7L, (persistence as PrimaryCardPersistence.Update).card.id)
    }

    @Test
    fun newCardIsInsertedWithQuantityOne() {
        val resolved = planPrimaryCardAssignment(
            cardId = "base1-4",
            language = "de",
            pokemonDexId = 6,
            pokemonId = "6",
            existingCard = null,
            now = now
        )

        assertTrue(resolved.isPrimary)
        assertEquals(1, resolved.quantity)
        assertEquals(now, resolved.addedAt)
        assertEquals(0L, resolved.id)
        assertTrue(primaryCardPersistence(null, resolved) is PrimaryCardPersistence.Insert)
    }

    @Test
    fun missingDexIdAndPokemonIdDoNotOverwriteKnownValues() {
        val existing = owned()

        val resolved = planPrimaryCardAssignment(
            cardId = "base1-4",
            language = "de",
            pokemonDexId = 0,
            pokemonId = "",
            existingCard = existing,
            now = now
        )

        assertEquals(6, resolved.pokemonDexId)
        assertEquals("6", resolved.pokemonId)
        assertFalse(resolved.isPrimary.not())
    }
}
