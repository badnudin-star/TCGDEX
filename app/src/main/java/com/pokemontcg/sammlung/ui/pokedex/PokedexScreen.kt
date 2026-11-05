package com.pokemontcg.sammlung.ui.pokedex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.model.GenerationModel
import com.pokemontcg.sammlung.data.model.GenerationSection
import com.pokemontcg.sammlung.ui.components.TcgImage
import com.pokemontcg.sammlung.data.local.entities.PokemonEntity

@Composable
fun PokedexScreen(
    generationSections: List<GenerationSection>,
    collapsedGenerations: Set<Int>,
    onToggleGeneration: (Int) -> Unit,
    isLoading: Boolean,
    isCatalogEmpty: Boolean,
    isSyncing: Boolean,
    syncProgressMessage: String,
    syncProgressPercent: Int?,
    onLoadCatalog: () -> Unit,
    primaryImageByDexId: Map<Int, String>,
    onPokemonClick: (PokemonEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            isCatalogEmpty -> CatalogEmptyState(
                isSyncing = isSyncing,
                progressMessage = syncProgressMessage,
                progressPercent = syncProgressPercent,
                onLoadCatalog = onLoadCatalog
            )
            generationSections.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.generation_no_pokemon_found), style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                generationSections.forEach { section ->
                    val genInfo = GenerationModel.getGenerationInfo(section.generationId)
                    val isCollapsed = section.generationId in collapsedGenerations
                    item(key = "gen_header_${section.generationId}", span = { GridItemSpan(maxLineSpan) }) {
                        val collapseState = stringResource(
                            if (isCollapsed) R.string.a11y_state_collapsed else R.string.a11y_state_expanded
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable { onToggleGeneration(section.generationId) }
                                .semantics { stateDescription = collapseState },
                            color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(
                                            R.string.ui_generation_title_region,
                                            stringResource(genInfo.nameRes),
                                            stringResource(genInfo.regionRes)
                                        ),
                                        modifier = Modifier.semantics { heading() },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(stringResource(R.string.generation_collected_progress, section.collectedCount, section.totalCount), style = MaterialTheme.typography.labelMedium)
                                }
                                Text(if (isCollapsed) "▶" else "▼", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (!isCollapsed) {
                        items(section.pokemon, key = { it.id }) { pokemon ->
                            val primaryImage = primaryImageByDexId[pokemon.dexId]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.715f)
                                    .minimumInteractiveComponentSize()
                                    .clickable { onPokemonClick(pokemon) },
                                shape = MaterialTheme.shapes.medium, elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (!primaryImage.isNullOrBlank()) {
                                        TcgImage(model = primaryImage, contentDescription = stringResource(R.string.ui_pokemon_image_description, pokemon.name, pokemon.number), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                                    } else {
                                        Surface(
                                            modifier = Modifier.fillMaxSize().padding(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                            shape = MaterialTheme.shapes.small,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                        ) {
                                            Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                                                Text(stringResource(R.string.ui_pokemon_number, pokemon.number), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                TcgImage(model = pokemon.imageUrl, contentDescription = stringResource(R.string.ui_pokemon_image_description, pokemon.name, pokemon.number), modifier = Modifier.fillMaxWidth(0.85f).weight(1f, fill = false), contentScale = ContentScale.Fit)
                                                Text(pokemon.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}

@Composable
private fun CatalogEmptyState(
    isSyncing: Boolean,
    progressMessage: String,
    progressPercent: Int?,
    onLoadCatalog: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.catalog_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    stringResource(R.string.catalog_empty_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (isSyncing) {
                    if (progressPercent != null) {
                        LinearProgressIndicator(
                            progress = { progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        progressMessage.ifBlank { stringResource(R.string.catalog_empty_syncing) },
                        style = MaterialTheme.typography.bodySmall
                    )
                    progressPercent?.let { percent ->
                        Text(
                            stringResource(R.string.ui_sync_progress_percent, percent),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Button(onClick = onLoadCatalog) {
                        Text(stringResource(R.string.catalog_empty_load_button))
                    }
                }
            }
        }
    }
}
