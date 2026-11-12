package com.pokemontcg.sammlung.ui.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScannerPokemonResolverTest {

    @Test
    fun stripCardNameSuffix_removesKnownSuffixes() {
        assertEquals("Pikachu", stripCardNameSuffix("Pikachu ex"))
        assertEquals("Pikachu", stripCardNameSuffix("Pikachu EX"))
        assertEquals("Charizard", stripCardNameSuffix("Charizard VMAX"))
        assertEquals("Charizard", stripCardNameSuffix("Charizard VSTAR"))
        assertEquals("Pikachu & Zekrom", stripCardNameSuffix("Pikachu & Zekrom GX"))
        assertEquals("Arceus & Dialga & Palkia", stripCardNameSuffix("Arceus & Dialga & Palkia GX"))
        assertEquals("Darkrai & Cresselia", stripCardNameSuffix("Darkrai & Cresselia LEGEND"))
    }

    @Test
    fun stripCardNameSuffix_leavesPlainNamesUntouched() {
        assertEquals("Pikachu", stripCardNameSuffix("Pikachu"))
        assertEquals("Bulbasaur", stripCardNameSuffix("Bulbasaur"))
    }

    @Test
    fun stripCardNameSuffix_handlesTagTeam() {
        assertEquals("Pikachu & Zekrom", stripCardNameSuffix("Pikachu & Zekrom TAG TEAM-GX"))
        // Without suffix it stays
        assertEquals("Pikachu & Zekrom", stripCardNameSuffix("Pikachu & Zekrom"))
    }

    @Test
    fun lastToken_extractsLastWord() {
        assertEquals("Rotom", lastToken("Heat Rotom"))
        assertEquals("Pikachu", lastToken("Pikachu"))
        assertNull(lastToken(""))
        assertNull(lastToken("   "))
    }

    @Test
    fun stripCardNameSuffix_preservesEeveeVsV() {
        // \"Eevee\" should not be stripped to \"Eevee\"? Actually \"Eevee\" ends with \"ee\" not \" V\" with space
        // so it is safe. Test that \"Eevee V\" strips correctly.
        assertEquals("Eevee", stripCardNameSuffix("Eevee V"))
        assertEquals("Eevee", stripCardNameSuffix("Eevee"))
    }
}
