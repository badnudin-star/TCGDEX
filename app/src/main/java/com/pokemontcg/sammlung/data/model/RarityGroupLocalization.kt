package com.pokemontcg.sammlung.data.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pokemontcg.sammlung.R

/**
 * Localized labels for the canonical rarity groups. The catalog stores canonical English
 * values, but the UI (German default) must show German labels instead of raw English.
 */
object RarityGroupLocalization {

    private val germanLabels: Map<String, Int> = mapOf(
        "None" to R.string.rarity_group_none,
        "Common" to R.string.rarity_group_common,
        "Uncommon" to R.string.rarity_group_uncommon,
        "Rare" to R.string.rarity_group_rare,
        "Holo Rare" to R.string.rarity_group_holo_rare,
        "Ultra Rare" to R.string.rarity_group_ultra_rare,
        "Illustration Rare" to R.string.rarity_group_illustration_rare,
        "Promo" to R.string.rarity_group_promo
    )

    /** Returns the localized label for a canonical rarity group, falling back to the raw value. */
    @Composable
    fun localizedLabel(group: String): String {
        val res = germanLabels[group] ?: return group
        return stringResource(res)
    }

    /** Non-composable variant for cases where a Context is available (e.g. ViewModel helper). */
    fun labelResId(group: String): Int? = germanLabels[group]
}
