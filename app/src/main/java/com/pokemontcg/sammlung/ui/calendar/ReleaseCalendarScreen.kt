package com.pokemontcg.sammlung.ui.calendar

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
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
import com.pokemontcg.sammlung.data.model.allDayReleaseEventBounds
import com.pokemontcg.sammlung.data.model.upcomingSetReleases
import com.pokemontcg.sammlung.ui.components.TcgImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseCalendarScreen(
    sets: List<SetEntity>,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val upcomingReleases = upcomingSetReleases(sets)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.ui_ver_ffentlichungskalender_7b0d91),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.ui_icon_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (upcomingReleases.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.ui_keine_erweiterungen_im_kalender_vorh_003e00))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(upcomingReleases, key = { it.set.id }) { release ->
                        val set = release.set
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
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
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(set.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                                    onClick = {
                                        addCalendarReminder(context, set, release.releaseDate)
                                    }
                                ) {
                                    Text(stringResource(R.string.ui_erinnerung_d95298))
                                }
                            }
                        }
                    }
                }
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
