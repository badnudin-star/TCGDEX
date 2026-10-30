package com.pokemontcg.sammlung.data.local

/**
 * Deterministic card ordering shared by the database migrations.
 *
 * Ordinary numeric card numbers come first, followed by prefixed numbers
 * (promos, gallery cards, ...), then by numeric value, suffix and id.
 */
internal object CardSortOrder {

    private val NUMBER_PATTERN = Regex("^\\s*([A-Za-z]+)?\\s*(\\d+)")

    data class Entry(val id: String, val number: String)

    private data class Sortable(
        val id: String,
        val prefix: String,
        val numericPart: Int,
        val suffix: String,
    )

    fun sortedIds(entries: List<Entry>): List<String> =
        entries.map { entry ->
            val number = entry.number.trim()
            val match = NUMBER_PATTERN.find(number)
            val prefix = match?.groups?.get(1)?.value.orEmpty().uppercase()
            val numericPart = match?.groups?.get(2)?.value?.toIntOrNull() ?: Int.MAX_VALUE
            val suffix = if (match != null) {
                number.substring(match.range.last + 1).trim().uppercase()
            } else {
                number.uppercase()
            }
            Sortable(entry.id, prefix, numericPart, suffix)
        }.sortedWith(
            compareBy<Sortable> { if (it.prefix.isEmpty()) 0 else 1 }
                .thenBy { it.prefix }
                .thenBy { it.numericPart }
                .thenBy { it.suffix }
                .thenBy { it.id }
        ).map { it.id }
}
