package com.pokemontcg.sammlung.ui.calendar

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.local.entities.SetEntity
import com.pokemontcg.sammlung.data.model.UpcomingSetRelease
import com.pokemontcg.sammlung.data.model.allDayReleaseEventBounds
import com.pokemontcg.sammlung.ui.components.TcgImage
import com.pokemontcg.sammlung.ui.sets.SetBadge

/** Upcoming releases embedded in the set progress screen. */
@Composable
fun ReleaseCalendarSection(
    releases: List<UpcomingSetRelease>,
    modifier: Modifier = Modifier
) {
    if (releases.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.ui_coming_releases),
            modifier = Modifier.semantics { heading() },
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.ui_coming_releases_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        releases.forEach { release ->
            ReleaseCalendarEntry(release)
        }
    }
}

@Composable
private fun ReleaseCalendarEntry(release: UpcomingSetRelease) {
    val context = LocalContext.current
    val set = release.set

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val setImage = set.symbolUrl ?: set.logoUrl
            if (!setImage.isNullOrBlank()) {
                TcgImage(
                    model = setImage,
                    contentDescription = stringResource(
                        R.string.ui_set_symbol_description,
                        set.name
                    ),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
            } else {
                // Upcoming sets often have no images yet; a badge keeps the row identifiable.
                SetBadge(
                    setAbbreviation = set.setAbbreviation,
                    setName = set.name,
                    setId = set.id,
                    size = 40.dp,
                    contentDescription = stringResource(
                        R.string.ui_set_symbol_description,
                        set.name
                    )
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    set.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(stringResource(R.string.ui_set_series, set.series), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.ui_set_card_counts, set.printedTotal, set.total), style = MaterialTheme.typography.labelSmall)
                Text(
                    stringResource(R.string.ui_release_date, release.releaseDate.toString()),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(R.string.ui_days_until_release, release.daysUntilRelease),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedButton(
                onClick = { addCalendarReminder(context, set, release.releaseDate) }
            ) {
                Text(stringResource(R.string.ui_erinnerung_d95298))
            }
        }
    }
}

private fun addCalendarReminder(
    context: android.content.Context,
    set: SetEntity,
    releaseDate: java.time.LocalDate
) {
    val (startMillis, endMillis) = allDayReleaseEventBounds(releaseDate)
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, context.getString(R.string.ui_calendar_event_title, set.name))
        putExtra(CalendarContract.Events.DESCRIPTION, context.getString(R.string.ui_calendar_event_description, set.name, set.series))
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        putExtra(CalendarContract.Events.ALL_DAY, true)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.ui_calendar_unavailable),
            Toast.LENGTH_LONG
        ).show()
    } catch (_: SecurityException) {
        Toast.makeText(
            context,
            context.getString(R.string.ui_calendar_unavailable),
            Toast.LENGTH_LONG
        ).show()
    }
}
