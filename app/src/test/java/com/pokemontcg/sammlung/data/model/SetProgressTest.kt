package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.data.local.entities.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetProgressTest {

    @Test
    fun testComputeSetProgressDistinctCardsAndIncompleteCatalog() {
        val sets = listOf(
            SetEntity("base1", "Base Set", "Base", 102, 102, "1999-01-09"),
            SetEntity("xy1", "XY Base Set", "XY", 146, 146, "2014-02-05")
        )

        // Multiple copies of same cardId in base1
        val ownedCardIdsBySet = mapOf(
            "base1" to setOf("base1-1", "base1-2", "base1-3"), // 3 distinct cards
            "xy1" to setOf("xy1-1")                           // 1 card
        )

        val catalogCardCountBySet = mapOf(
            "base1" to 102,
            "xy1" to 100 // smaller than printedTotal (146) -> incomplete!
        )

        val progress = computeSetProgress(ownedCardIdsBySet, catalogCardCountBySet, sets)
        assertEquals(2, progress.size)

        val base1Progress = progress.find { it.set.id == "base1" }!!
        assertEquals(3, base1Progress.collectedCount)
        assertEquals(102, base1Progress.totalCount)
        assertFalse(base1Progress.isCatalogIncomplete)

        val xy1Progress = progress.find { it.set.id == "xy1" }!!
        assertEquals(1, xy1Progress.collectedCount)
        assertEquals(146, xy1Progress.totalCount)
        assertEquals(100, xy1Progress.catalogCount)
        assertTrue(xy1Progress.isCatalogIncomplete)
    }

    @Test
    fun testSortSetProgress() {
        val set1 = SetEntity("base1", "Base Set", "Base", 100, 100, "1999-01-09")
        val set2 = SetEntity("xy1", "XY", "XY", 100, 100, "2014-02-05")

        val p1 = SetProgress(set1, 50, 100, 100, 0.50f, false)
        val p2 = SetProgress(set2, 80, 100, 100, 0.80f, false)

        val list = listOf(p1, p2)

        // Progress desc -> p2 (80%) first
        val sortedProgress = sortSetProgress(list, SetSortOption.PROGRESS_DESC)
        assertEquals("xy1", sortedProgress[0].set.id)

        // Release date desc -> xy1 (2014) first
        val sortedRelease = sortSetProgress(list, SetSortOption.RELEASE_DATE_DESC)
        assertEquals("xy1", sortedRelease[0].set.id)

        // Name asc -> Base Set first
        val sortedName = sortSetProgress(list, SetSortOption.NAME_ASC)
        assertEquals("base1", sortedName[0].set.id)
    }
}
