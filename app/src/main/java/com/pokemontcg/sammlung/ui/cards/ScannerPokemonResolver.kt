package com.pokemontcg.sammlung.ui.cards

import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.repository.CatalogRepository

/**
 * Helpers for the scanner's Pokémon resolution. Extracted for testability.
 */

/** Known card-name suffixes that are not part of the Pokémon name (TCGdex spec). */
private val CARD_NAME_SUFFIXES = listOf(
    " TAG TEAM-GX",
    " TAG TEAM GX",
    " TAG TEAM",
    " ex",
    " EX",
    " GX",
    " VMAX",
    " VSTAR",
    " V",
    " LEGEND",
    " Prime",
    " SP",
    " BREAK",
    " Star",
    " ex"
)

fun extractDexIds(card: CardEntity): List<Int> =
    (listOfNotNull(card.nationalPokedexNumber) +
        card.nationalPokedexNumbers.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() })
        .filter { it > 0 }.distinct()

/** Strips known TCG suffixes from a card name to obtain the Pokémon base name. */
fun stripCardNameSuffix(name: String): String {
    var result = name.trim()
    var changed: Boolean
    do {
        changed = false
        for (suffix in CARD_NAME_SUFFIXES) {
            if (result.endsWith(suffix, ignoreCase = false) && result.length > suffix.length) {
                // Ensure we don't strip if suffix is part of the base name (e.g. "Eevee" vs "V")
                // Heuristic: suffix must be preceded by space or be exactly the suffix case.
                result = result.removeSuffix(suffix).trim()
                changed = true
                break
            }
        }
        // Case-insensitive fallback for lower-case variants like " ex" vs " Ex"
        if (!changed) {
            for (suffix in CARD_NAME_SUFFIXES) {
                if (result.endsWith(suffix, ignoreCase = true) && result.length > suffix.length) {
                    // Only strip if the suffix length >1 to avoid single-letter false positives
                    if (suffix.trim().length > 1) {
                        result = result.substring(0, result.length - suffix.length).trim()
                        changed = true
                        break
                    }
                }
            }
        }
    } while (changed)
    return result
}

/** For Rotom forms and similar: "Heat Rotom" -> "Rotom" as last token fallback. */
fun lastToken(name: String): String? {
    val tokens = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return tokens.lastOrNull()
}

/**
 * Attempts to resolve a missing dexId via the pokemon_names table. Tries exact match
 * on stripped name, then last-token fallback, in both German and English.
 */
suspend fun resolveDexIdByCardName(
    cardName: String,
    cardLanguage: String,
    catalogRepo: CatalogRepository
): Int? {
    val candidates = linkedSetOf<String>()
    val stripped = stripCardNameSuffix(cardName)
    candidates.add(stripped)
    candidates.add(cardName.trim())
    lastToken(stripped)?.let { candidates.add(it) }

    // Try language-specific first, then case-insensitive global
    for (candidate in candidates) {
        if (candidate.isBlank()) continue
        catalogRepo.findDexIdByName(cardLanguage, candidate)?.let { return it }
        catalogRepo.findDexIdByName("de", candidate)?.let { return it }
        catalogRepo.findDexIdByName("en", candidate)?.let { return it }
        catalogRepo.findDexIdByNameCaseInsensitive(candidate)?.let { return it }
    }
    return null
}

/**
 * Full resolution used by the scanner: first tries the card's dex fields, then the
 * card_pokemon link table, then name-based fallback.
 */
suspend fun resolveDexIdsForScanner(
    card: CardEntity,
    catalogRepo: CatalogRepository
): List<Int> {
    val fromCard = extractDexIds(card)
    if (fromCard.isNotEmpty()) return fromCard

    // Fallback to card_pokemon join table (covers catalog where builder filled links but card fields empty)
    val fromJoin = runCatching { catalogRepo.getDexIdsForCard(card.id) }.getOrDefault(emptyList())
    if (fromJoin.isNotEmpty()) return fromJoin.distinct()

    // Final fallback: infer from card name
    val byName = resolveDexIdByCardName(card.name, card.language, catalogRepo)
    if (byName != null && byName > 0) return listOf(byName)

    return emptyList()
}
