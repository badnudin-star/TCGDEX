package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.data.local.entities.SetEntity
import com.pokemontcg.sammlung.data.model.allDayReleaseEventBounds
import com.pokemontcg.sammlung.data.model.upcomingSetReleases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Instant

class ReleaseCalendarModelsTest {
    private val today = LocalDate.parse("2026-10-29")

    @Test
    fun returnsOnlyFutureReleasesInDateOrderAndCalculatesDays() {
        val releases = upcomingSetReleases(
            listOf(
                set("later", "Later Set", "2026-12-01"),
                set("today", "Today Set", "2026-10-29"),
                set("soon", "Soon Set", "2026-10-31"),
                set("past", "Past Set", "2026-10-01")
            ),
            today
        )

        assertEquals(listOf("soon", "later"), releases.map { it.set.id })
        assertEquals(listOf(2L, 33L), releases.map { it.daysUntilRelease })
    }

    @Test
    fun invalidOrBlankReleaseDatesAreSkipped() {
        val releases = upcomingSetReleases(
            listOf(set("blank", "Blank", ""), set("invalid", "Invalid", "TBA")),
            today
        )
        assertTrue(releases.isEmpty())
    }


    @Test
    fun allDayEventBoundsCoverExactlyTheReleaseDateInUtc() {
        val (start, end) = allDayReleaseEventBounds(LocalDate.parse("2026-11-02"))

        assertEquals(Instant.parse("2026-11-02T00:00:00Z").toEpochMilli(), start)
        assertEquals(Instant.parse("2026-11-03T00:00:00Z").toEpochMilli(), end)
        assertEquals(24L * 60L * 60L * 1000L, end - start)
    }

    private fun set(id: String, name: String, date: String) = SetEntity(
        id = id,
        name = name,
        series = "Test Series",
        printedTotal = 10,
        total = 10,
        releaseDate = date
    )
}
