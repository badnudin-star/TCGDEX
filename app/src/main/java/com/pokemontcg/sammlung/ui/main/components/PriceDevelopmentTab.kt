package com.pokemontcg.sammlung.ui.main.components

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import com.pokemontcg.sammlung.ui.components.CollectionValueChart
import com.pokemontcg.sammlung.util.PriceFormatter

@Composable
fun PriceDevelopmentTab(
    totalValue: Double?,
    priceHistory: List<PriceHistoryEntity>,
    userCardQuantities: Map<String, Int>,
    totalCardsCount: Int,
    cardsWithoutPriceCount: Int,
    onExportTradeList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.main_collection_value_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.ui_current_total_value),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = PriceFormatter.formatPrice(context, totalValue),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                CollectionValueChart(
                    history = priceHistory,
                    quantities = userCardQuantities
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_collection_statistics),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.ui_gesammelte_karten_be34ab), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.ui_total_cards_short, totalCardsCount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.ui_karten_ohne_marktpreis_a57cf0), style = MaterialTheme.typography.bodyMedium)
                    Text(cardsWithoutPriceCount.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.ui_historische_datenpunkte_6c563a), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.ui_price_history_count, priceHistory.size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }

                if (cardsWithoutPriceCount > 0) {
                    Text(
                        text = androidx.compose.ui.res.pluralStringResource(R.plurals.ui_cards_without_price_hint, cardsWithoutPriceCount, cardsWithoutPriceCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                OutlinedButton(
                    onClick = onExportTradeList,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_tauschliste_teilen_537116))
                }
            }
        }
    }
}
