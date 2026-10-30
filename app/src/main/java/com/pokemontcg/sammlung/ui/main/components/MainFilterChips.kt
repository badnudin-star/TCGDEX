package com.pokemontcg.sammlung.ui.main.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pokemontcg.sammlung.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainFilterChips(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit
) {
    val filters = listOf(
        "Alle" to stringResource(R.string.ui_filter_all),
        "Common" to stringResource(R.string.ui_common_7de90a),
        "Fullart" to stringResource(R.string.ui_full_art_952d8e),
        "NONE" to stringResource(R.string.ui_filter_no_category)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { (filterKey, label) ->
            FilterChip(
                selected = selectedFilter.equals(filterKey, ignoreCase = true),
                onClick = { onFilterSelected(filterKey) },
                label = { Text(label) }
            )
        }
    }
}
