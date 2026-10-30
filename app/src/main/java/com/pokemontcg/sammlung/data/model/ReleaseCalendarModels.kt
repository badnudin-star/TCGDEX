package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.data.local.entities.SetEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.ZoneOffset

/** A catalog set with a valid release date strictly after [today]. */
data class UpcomingSetRelease(
    val set: SetEntity,
    val releaseDate: LocalDate,
    val daysUntilRelease: Long
)

/** Returns only future releases, ordered from the nearest date to the farthest. */
fun upcomingSetReleases(
    sets: Iterable<SetEntity>,
    today: LocalDate = LocalDate.now()
): List<UpcomingSetRelease> = sets.mapNotNull { set ->
    val date = runCatching { LocalDate.parse(set.releaseDate.trim()) }.getOrNull()
        ?: return@mapNotNull null
    if (!date.isAfter(today)) return@mapNotNull null
    UpcomingSetRelease(
        set = set,
        releaseDate = date,
        daysUntilRelease = ChronoUnit.DAYS.between(today, date)
    )
}.sortedWith(compareBy<UpcomingSetRelease> { it.releaseDate }.thenBy { it.set.name })

/** Returns UTC epoch-millisecond boundaries for an all-day calendar event. */
fun allDayReleaseEventBounds(date: LocalDate): Pair<Long, Long> {
    val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    return start to end
}
