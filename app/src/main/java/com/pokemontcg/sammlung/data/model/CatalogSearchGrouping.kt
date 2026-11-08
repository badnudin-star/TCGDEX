package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.data.local.dao.CardSearchRow
import com.pokemontcg.sammlung.data.local.entities.CardEntity

/**
 * One catalog card merged across its language prints: same artwork, same set.
 *
 * The catalog stores one row per language print (`de` ids are unprefixed, other languages carry
 * a `lang:` prefix). In the wishlist search these prints used to appear as separate entries;
 * they are now folded into one entry that lists the languages the card is available in.
 */
data class MergedCatalogCard(
    /** The print shown in the UI; the preferred language when available. */
    val representative: CardEntity,
    /** All language prints of this card, in search order. */
    val variants: List<CardEntity>
) {
    /** Distinct uppercase language codes of all prints, e.g. ["DE", "EN", "FR"]. */
    val languages: List<String>
        get() = variants.map { it.language.uppercase() }.distinct().sorted()
}

/**
 * Strips the language prefix from a catalog card id: `en:xy1-1` and `xy1-1` (German) both map to
 * `xy1-1`, which already contains set id and local id and therefore identifies the artwork.
 */
fun rawCardId(cardId: String): String = cardId.substringAfter(':')

/** One Pokémon with the merged catalog cards found for it. */
data class PokemonCardGroup(
    /** Null for cards without a Pokédex link, e.g. trainer and energy cards. */
    val dexId: Int?,
    val pokemonName: String,
    val cards: List<MergedCatalogCard>
)

/** A generation block of the search result, mirroring the main screen's structure. */
data class CatalogSearchSection(
    /** Null for the trailing block of cards without a Pokédex link. */
    val generationId: Int?,
    val groups: List<PokemonCardGroup>
)

/**
 * Turns flat catalog search hits into the Pokémon grouping used by the main screen:
 * generation blocks, inside them Pokémon ordered by Pokédex id, cards without a Pokédex link last.
 *
 * Cards of the same set with the same artwork (the same raw card id across language prints)
 * are merged into one [MergedCatalogCard]; the print matching `preferredLanguage` (or German,
 * or English, in that order) becomes the representative. Merging happens per Pokémon, so a card
 * that appears under two Pokémon keeps an entry in both groups.
 *
 * `excludedDexIds` drops every Pokémon the user already declared a full art favourite for, so the
 * wishlist search only offers Pokémon that are still missing one.
 */
fun groupCatalogSearchByPokemon(
    rows: List<CardSearchRow>,
    cardsById: Map<String, CardEntity>,
    pokemonNameByDexId: Map<Int, String>,
    excludedDexIds: Set<Int> = emptySet(),
    preferredLanguage: String = "de"
): List<CatalogSearchSection> {
    // Prints of the same artwork, in first-seen order; the key keeps the Pokémon so a card
    // linked to two Pokémon is merged once per Pokémon.
    val printsByKey = LinkedHashMap<Pair<Int?, String>, MutableList<CardEntity>>()
    for (row in rows) {
        if (row.dexId != null && row.dexId in excludedDexIds) continue
        val card = cardsById[row.cardId] ?: continue
        val key = row.dexId to rawCardId(card.id)
        printsByKey.getOrPut(key) { mutableListOf() }.add(card)
    }

    val groupsByDexId = LinkedHashMap<Int?, MutableList<MergedCatalogCard>>()
    for ((key, prints) in printsByKey) {
        val representative = prints.minByOrNull { representativeRank(it.language, preferredLanguage) }
            ?: continue
        groupsByDexId.getOrPut(key.first) { mutableListOf() }
            .add(MergedCatalogCard(representative = representative, variants = prints.toList()))
    }

    val groups = groupsByDexId.map { (dexId, cards) ->
        PokemonCardGroup(
            dexId = dexId,
            pokemonName = dexId?.let { pokemonNameByDexId[it] }.orEmpty(),
            cards = cards
        )
    }

    return groups
        .groupBy { group -> group.dexId?.let { GenerationModel.generationOf(it) } }
        .toSortedMap(compareBy(nullsLast<Int>()) { it })
        .map { (generationId, sectionGroups) ->
            CatalogSearchSection(
                generationId = generationId,
                groups = sectionGroups.sortedWith(
                    compareBy(nullsLast<Int>()) { it.dexId }
                )
            )
        }
        .filter { it.groups.isNotEmpty() }
}

/**
 * A print wins over the others when its language is closer to the preferred language: exact
 * match beats German (the app default) beats English beats any other print.
 */
fun representativeRank(language: String, preferredLanguage: String): Int = when {
    language.equals(preferredLanguage, ignoreCase = true) -> 0
    language.equals("de", ignoreCase = true) -> 1
    language.equals("en", ignoreCase = true) -> 2
    else -> 3
}

/**
 * Merges the language prints of a flat card list into one [MergedCatalogCard] per artwork
 * (same raw card id), keeping the first-seen order — e.g. all cards of one set for the set
 * detail list. The representative follows the preferred language like in the search grouping.
 */
fun mergeCardsByRawId(
    cards: List<CardEntity>,
    preferredLanguage: String = "de"
): List<MergedCatalogCard> {
    val printsByKey = LinkedHashMap<String, MutableList<CardEntity>>()
    for (card in cards) {
        printsByKey.getOrPut(rawCardId(card.id)) { mutableListOf() }.add(card)
    }
    return printsByKey.map { (_, prints) ->
        val representative = prints.minByOrNull { representativeRank(it.language, preferredLanguage) }
            ?: prints.first()
        MergedCatalogCard(representative = representative, variants = prints.toList())
    }
}

/** Counts how many Pokémon of the result were dropped because they already are full art. */
fun countExcludedFullartPokemon(rows: List<CardSearchRow>, excludedDexIds: Set<Int>): Int =
    rows.mapNotNull { it.dexId }
        .filter { it in excludedDexIds }
        .distinct()
        .size
