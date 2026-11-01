package com.pokemontcg.sammlung.ui.sets

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.ui.components.TcgImage
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.entities.SetEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.model.SetSortOption
import com.pokemontcg.sammlung.data.model.computeSetProgress
import com.pokemontcg.sammlung.data.model.ownedCardIdsBySet
import com.pokemontcg.sammlung.data.model.sortSetProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetProgressScreen(
    sets: List<SetEntity>,
    userCards: List<UserCardEntityV2>,
    cardsMap: Map<String, CardEntity>,
    catalogCardCountBySet: Map<String, Int>,
    isLoading: Boolean,
    onBackClick: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf("Alle") }
    var currentSort by remember { mutableStateOf(SetSortOption.PROGRESS_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }

    val availableLanguages = remember(userCards) {
        val langs = userCards.map { it.language }.distinct().filter { it.isNotBlank() }.sorted()
        listOf("Alle") + langs
    }

    val ownedBySet = remember(userCards, cardsMap, selectedLanguage) {
        val language = if (selectedLanguage == "Alle") null else selectedLanguage
        ownedCardIdsBySet(userCards, cardsMap, language)
    }

    val progressList = remember(ownedBySet, catalogCardCountBySet, sets, currentSort) {
        val computed = computeSetProgress(ownedBySet, catalogCardCountBySet, sets)
        sortSetProgress(computed, currentSort)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.ui_set_fortschritt_601613),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_icon_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.ui_icon_sort))
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_fortschritt_absteigend_b8ba6f)) },
                                onClick = {
                                    currentSort = SetSortOption.PROGRESS_DESC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_erscheinungsdatum_neueste_zuerst_7f2850)) },
                                onClick = {
                                    currentSort = SetSortOption.RELEASE_DATE_DESC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_set_name_4f4df4)) },
                                onClick = {
                                    currentSort = SetSortOption.NAME_ASC
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (availableLanguages.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableLanguages.forEach { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = { selectedLanguage = lang },
                            label = { Text(if (lang == "Alle") stringResource(R.string.ui_all_languages) else lang.uppercase()) }
                        )
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (progressList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.ui_keine_erweiterungen_vorhanden_0544ce))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(progressList, key = { it.set.id }) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val setImg = item.set.symbolUrl ?: item.set.logoUrl
                                    if (!setImg.isNullOrBlank()) {
                                        TcgImage(
                                            model = setImg,
                                            contentDescription = stringResource(
                                                R.string.ui_set_symbol_description,
                                                item.set.name
                                            ),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.set.name,
                                            modifier = Modifier.semantics { heading() },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(stringResource(R.string.ui_set_series_release, item.set.series, item.set.releaseDate.ifBlank { stringResource(R.string.ui_unbekannt) }), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        stringResource(R.string.ui_set_progress_count, item.collectedCount, item.totalCount),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { item.progressPercent },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                )

                                if (item.isCatalogIncomplete) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            stringResource(R.string.ui_set_catalog_count, item.catalogCount),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                stringResource(R.string.ui_katalog_unvollst_ndig_2a2190),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
