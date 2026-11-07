package com.pokemontcg.sammlung.data.export

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import androidx.room.withTransaction
import com.pokemontcg.sammlung.BuildConfig
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.local.user.UserCardDaoV2
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.local.user.UserDatabase
import com.pokemontcg.sammlung.data.local.user.WishlistEntity
import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.user.CardCondition
import com.pokemontcg.sammlung.util.AppLogger
import java.time.Instant
import java.time.format.DateTimeFormatter

object CollectionExporter {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportToJsonString(
        userCards: List<UserCardEntityV2>,
        wishlist: List<WishlistEntity> = emptyList()
    ): String {
        AppLogger.log("CollectionExporter", "JSON Export gestartet: ${userCards.size} Karten, ${wishlist.size} Wunschliste")
        val exportItems = userCards.map { card ->
            ExportedUserCardItem(
                cardId = card.cardId,
                language = card.language,
                quantity = card.quantity,
                condition = normalizeCondition(card.condition),
                category = normalizeCategory(card.category),
                isPrimary = card.isPrimary,
                pokemonDexId = card.pokemonDexId,
                notes = card.notes,
                addedAt = card.addedAt
            )
        }
        val exportWishlist = wishlist.map { wish ->
            ExportedWishlistItem(
                cardId = wish.cardId,
                targetPrice = wish.targetPrice,
                notes = wish.notes,
                addedAt = wish.addedAt
            )
        }
        val exportData = CollectionExportData(
            schemaVersion = 2,
            exportedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            appVersion = BuildConfig.VERSION_NAME,
            items = exportItems,
            wishlist = exportWishlist
        )
        val json = gson.toJson(exportData)
        AppLogger.log("CollectionExporter", "JSON Export erfolgreich erstellt")
        return json
    }

    fun exportToCsvString(
        userCards: List<UserCardEntityV2>,
        wishlist: List<WishlistEntity> = emptyList()
    ): String {
        AppLogger.log("CollectionExporter", "CSV Export gestartet: ${userCards.size} Karten")
        val sb = StringBuilder()
        sb.append("cardId;language;quantity;condition;category;isPrimary;pokemonDexId;notes;addedAt\n")
        for (card in userCards) {
            listOf(
                card.cardId,
                card.language,
                card.quantity.toString(),
                normalizeCondition(card.condition).orEmpty(),
                normalizeCategory(card.category),
                card.isPrimary.toString(),
                card.pokemonDexId.toString(),
                card.notes.orEmpty(),
                card.addedAt.toString()
            ).joinTo(sb, separator = ";", postfix = "\n") { value -> csvField(value) }
        }
        return sb.toString()
    }

    private fun csvField(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun normalizeCondition(condition: String?): String? =
        condition?.takeIf { candidate -> CardCondition.entries.any { it.name == candidate } }

    private fun normalizeCategory(category: String): String =
        category.takeIf { candidate -> CardCategory.entries.any { it.name == candidate } }
            ?: CardCategory.NONE.name

    fun parseExportJson(content: String): CollectionExportData {
        val clean = if (content.startsWith("\uFEFF")) content.substring(1) else content
        val trimmed = clean.trim()
        if (trimmed.isEmpty()) {
            throw CollectionExportException(R.string.import_error_invalid_format)
        }

        if (trimmed.startsWith("{")) {
            try {
                val jsonObject = JsonParser.parseString(trimmed).asJsonObject
                val schemaVersion = if (jsonObject.has("schemaVersion")) jsonObject.get("schemaVersion").asInt else 1
                if (schemaVersion > 2) {
                    throw CollectionExportException(R.string.import_error_newer_version)
                }
                if (schemaVersion < 1) throw CollectionExportException(R.string.import_error_invalid_format)
                if (!jsonObject.has("items") || !jsonObject.get("items").isJsonArray) {
                    throw CollectionExportException(R.string.import_error_invalid_format)
                }
                val data = gson.fromJson(trimmed, CollectionExportData::class.java)
                if (data != null) {
                    val normalizedItems = (data.items ?: emptyList()).map { item ->
                        item.copy(
                            cardId = item.cardId ?: "",
                            language = (item.language ?: "").ifBlank { "de" },
                            quantity = if (item.quantity < 1) 1 else item.quantity,
                            category = (item.category ?: "").ifBlank { "NONE" },
                            pokemonDexId = if (item.pokemonDexId < 0) 0 else item.pokemonDexId,
                            addedAt = if (item.addedAt < 0) 0L else item.addedAt
                        )
                    }
                    val normalized = data.copy(
                        schemaVersion = schemaVersion,
                        items = normalizedItems,
                        wishlist = data.wishlist ?: emptyList()
                    )
                    validateExportData(normalized)
                    return normalized
                }
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (_: Exception) {
                // Try CSV fallback
            }
        } else if (trimmed.startsWith("[")) {
            // Older app versions exported a bare JSON array without the CollectionExportData
            // envelope. Such a file is still a valid backup of a real collection, so it stays
            // readable instead of being rejected as an unknown format.
            try {
                val legacyArray = gson.fromJson(trimmed, Array<ExportedUserCardItem>::class.java)
                if (legacyArray != null) {
                    val normalizedItems = legacyArray.map { item ->
                        item.copy(
                            cardId = item.cardId ?: "",
                            language = (item.language ?: "").ifBlank { "de" },
                            quantity = if (item.quantity < 1) 1 else item.quantity,
                            category = (item.category ?: "").ifBlank { "NONE" },
                            pokemonDexId = if (item.pokemonDexId < 0) 0 else item.pokemonDexId,
                            addedAt = if (item.addedAt < 0) 0L else item.addedAt
                        )
                    }
                    val data = CollectionExportData(
                        schemaVersion = 1,
                        exportedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                        appVersion = BuildConfig.VERSION_NAME,
                        items = normalizedItems,
                        wishlist = emptyList()
                    )
                    validateExportData(data)
                    return data
                }
            } catch (_: Exception) {
                // Try CSV fallback
            }
        }

        return parseCsvString(clean)
    }

    private fun parseCsvString(csvContent: String): CollectionExportData {
        val records = parseCsvRecords(csvContent.removePrefix("\uFEFF"))
            .filter { row -> row.any { it.isNotBlank() } }
        if (records.isEmpty()) {
            throw CollectionExportException(R.string.import_error_invalid_format)
        }

        val headerCols = records.first().map { it.trim().lowercase() }

        val cardIdIdx = headerCols.indexOf("cardid")
        val requiredHeaders = listOf("cardid", "language", "quantity", "condition", "category", "isprimary", "pokemondexid", "notes", "addedat")
        if (requiredHeaders.any { it !in headerCols }) {
            throw CollectionExportException(R.string.import_error_invalid_format)
        }

        val langIdx = headerCols.indexOf("language")
        val qtyIdx = headerCols.indexOf("quantity")
        val condIdx = headerCols.indexOf("condition")
        val catIdx = headerCols.indexOf("category")
        val primIdx = headerCols.indexOf("isprimary")
        val dexIdx = headerCols.indexOf("pokemondexid")
        val notesIdx = headerCols.indexOf("notes")
        val addedIdx = headerCols.indexOf("addedat")

        val items = mutableListOf<ExportedUserCardItem>()

        for (cols in records.drop(1)) {
            if (cols.size != headerCols.size) throw CollectionExportException(R.string.import_error_invalid_format)

            val cardId = cols[cardIdIdx].trim()
            if (cardId.isBlank()) throw CollectionExportException(R.string.import_error_invalid_format)

            val language = if (langIdx != -1 && langIdx < cols.size) cols[langIdx].trim() else ""
            val quantity = if (qtyIdx != -1 && qtyIdx < cols.size) cols[qtyIdx].trim().toIntOrNull() ?: 0 else 0
            val condition = if (condIdx != -1 && condIdx < cols.size) cols[condIdx].trim().ifBlank { null } else null
            val category = if (catIdx != -1 && catIdx < cols.size) cols[catIdx].trim().ifBlank { "NONE" } else "NONE"
            val isPrimaryVal = if (primIdx != -1 && primIdx < cols.size) cols[primIdx].trim() else "0"
            val isPrimary = isPrimaryVal == "1" || isPrimaryVal.equals("true", ignoreCase = true)
            val dexId = if (dexIdx != -1 && dexIdx < cols.size) cols[dexIdx].trim().toIntOrNull() ?: -1 else -1
            val notes = if (notesIdx != -1 && notesIdx < cols.size) cols[notesIdx].trim().ifBlank { null } else null
            val addedAt = if (addedIdx != -1 && addedIdx < cols.size) cols[addedIdx].trim().toLongOrNull() ?: -1 else -1

            items.add(
                ExportedUserCardItem(
                    cardId = cardId,
                    language = language,
                    quantity = quantity,
                    condition = condition,
                    category = category,
                    isPrimary = isPrimary,
                    pokemonDexId = dexId,
                    notes = notes,
                    addedAt = addedAt
                )
            )
        }

        if (items.isEmpty()) {
            throw CollectionExportException(R.string.import_error_invalid_format)
        }

        val data = CollectionExportData(
            schemaVersion = 2,
            exportedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            appVersion = BuildConfig.VERSION_NAME,
            items = items,
            wishlist = emptyList()
        )
        validateExportData(data)
        return data
    }

    private fun parseCsvRecords(content: String): List<List<String>> {
        val result = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < content.length) {
            val c = content[i]
            if (c == '"') {
                if (inQuotes && i + 1 < content.length && content[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ';' && !inQuotes) {
                row.add(current.toString())
                current.clear()
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                row.add(current.toString())
                if (row.any { it.isNotEmpty() }) result.add(row)
                row = mutableListOf()
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        if (inQuotes) throw CollectionExportException(R.string.import_error_invalid_format)
        row.add(current.toString())
        if (row.any { it.isNotEmpty() }) result.add(row)
        return result
    }

    private fun validateExportData(data: CollectionExportData) {
        if (data.schemaVersion > 2) throw CollectionExportException(R.string.import_error_newer_version)
        if (data.schemaVersion < 1) throw CollectionExportException(R.string.import_error_invalid_format)
        data.items.forEach { item ->
            if (item.cardId.isBlank() || item.language.isBlank() || item.quantity < 1 || item.pokemonDexId < 0 || item.addedAt < 0 ||
                item.category !in CardCategory.entries.map { it.name } ||
                (item.condition != null && item.condition !in CardCondition.entries.map { it.name })
            ) throw CollectionExportException(R.string.import_error_invalid_format)
        }
        data.wishlist.forEach { wish ->
            if (wish.cardId.isBlank() || wish.addedAt < 0 || (wish.targetPrice != null && wish.targetPrice < 0)) {
                throw CollectionExportException(R.string.import_error_invalid_format)
            }
        }
    }

    fun generatePreview(
        exportData: CollectionExportData,
        existingCards: List<UserCardEntityV2>,
        validCatalogCardIds: Set<String>
    ): ImportPreview {
        val existingMap = existingCards.associateBy { it.cardId to it.language }
        var newCount = 0
        var existingCount = 0
        val unknownCardIds = mutableSetOf<String>()

        for (item in exportData.items) {
            val key = item.cardId to item.language
            if (existingMap.containsKey(key)) {
                existingCount++
            } else {
                newCount++
            }
            // An empty catalog means no card can be resolved; keep the import and report it.
            if (!validCatalogCardIds.contains(item.cardId)) {
                unknownCardIds.add(item.cardId)
            }
        }

        return ImportPreview(
            totalRead = exportData.items.size + exportData.wishlist.size,
            newCardsCount = newCount,
            existingCardsCount = existingCount,
            unknownCardIds = unknownCardIds.toList()
        )
    }

    suspend fun executeImport(
        exportData: CollectionExportData,
        userDb: UserDatabase,
        validCatalogCardIds: Set<String>,
        strategy: ExportMergeStrategy
    ): ImportResult = userDb.withTransaction {
        AppLogger.log(
            "CollectionExporter",
            "Import gestartet: ${exportData.items.size} Karten, ${exportData.wishlist.size} Wunschliste, Strategie: $strategy"
        )

        val userCardDao = userDb.userCardDao()
        val wishlistDao = userDb.wishlistDao()

        val existingCards = userCardDao.getAllUserCardsOnce()
        val existingMap = existingCards.associateBy { it.cardId to it.language }.toMutableMap()

        val primaryCardsByDex = existingCards.filter { it.isPrimary }
            .associateBy { it.pokemonDexId }.toMutableMap()

        var insertedCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var unknownPreservedCount = 0
        val unknownCardIds = mutableSetOf<String>()

        val toInsert = mutableListOf<UserCardEntityV2>()
        val pendingIndexByKey = mutableMapOf<Pair<String, String>, Int>()

        for (item in exportData.items) {
            val isUnknown = !validCatalogCardIds.contains(item.cardId)
            if (isUnknown) {
                unknownPreservedCount++
                unknownCardIds.add(item.cardId)
            }

            val existingPrimary = primaryCardsByDex[item.pokemonDexId]
            val isPrimaryEffective = shouldKeepImportedPrimary(item, existingPrimary)
            if (item.isPrimary && !isPrimaryEffective) {
                AppLogger.log(
                    "CollectionExporter",
                    "Lieblingskarten-Konflikt für Dex-ID ${item.pokemonDexId}: Bestehende Lieblingskarte behalten."
                )
            }

            val key = item.cardId to item.language
            val existing = existingMap[key]

            if (existing != null) {
                when (strategy) {
                    ExportMergeStrategy.SKIP_EXISTING -> {
                        skippedCount++
                    }
                    ExportMergeStrategy.OVERWRITE_EXISTING -> {
                        val updated = mergeExistingCard(existing, item, strategy, isPrimaryEffective)!!
                        persistUpdatedCard(updated, key, pendingIndexByKey, toInsert, userCardDao)
                        existingMap[key] = updated
                        if (isPrimaryEffective && item.pokemonDexId > 0) {
                            primaryCardsByDex[item.pokemonDexId] = updated
                        }
                        updatedCount++
                    }
                    ExportMergeStrategy.MERGE_ADD -> {
                        val updated = mergeExistingCard(existing, item, strategy, isPrimaryEffective)!!
                        persistUpdatedCard(updated, key, pendingIndexByKey, toInsert, userCardDao)
                        existingMap[key] = updated
                        if (isPrimaryEffective && item.pokemonDexId > 0) {
                            primaryCardsByDex[item.pokemonDexId] = updated
                        }
                        updatedCount++
                    }
                }
            } else {
                val newEntity = UserCardEntityV2(
                    cardId = item.cardId,
                    // Detail screens, the scanner and the Pokédex progress count all expect the
                    // Pokédex id here; an empty value made imported cards look uncatalogued.
                    pokemonId = item.pokemonDexId.takeIf { it > 0 }?.toString().orEmpty(),
                    pokemonDexId = item.pokemonDexId,
                    language = item.language.ifBlank { "de" },
                    quantity = item.quantity.coerceAtLeast(1),
                    condition = item.condition,
                    notes = item.notes,
                    category = if (item.category.isNotBlank()) item.category else "NONE",
                    isPrimary = isPrimaryEffective,
                    addedAt = item.addedAt
                )
                pendingIndexByKey[key] = toInsert.size
                toInsert.add(newEntity)
                existingMap[key] = newEntity
                if (isPrimaryEffective && item.pokemonDexId > 0) {
                    primaryCardsByDex[item.pokemonDexId] = newEntity
                }
                insertedCount++
            }
        }

        if (toInsert.isNotEmpty()) {
            userCardDao.insertAll(toInsert)
        }

        val existingWishlist = wishlistDao.getWishlistOnce().associateBy { it.cardId }
        for (wish in exportData.wishlist) {
            if (!existingWishlist.containsKey(wish.cardId)) {
                wishlistDao.insert(
                    WishlistEntity(
                        cardId = wish.cardId,
                        targetPrice = wish.targetPrice,
                        notes = wish.notes,
                        addedAt = wish.addedAt
                    )
                )
            }
        }

        AppLogger.log(
            "CollectionExporter",
            "Import beendet: $insertedCount neu, $updatedCount aktualisiert, $skippedCount übersprungen, $unknownPreservedCount nicht zugeordnet"
        )

        ImportResult(
            totalProcessed = exportData.items.size + exportData.wishlist.size,
            insertedCount = insertedCount,
            updatedCount = updatedCount,
            unknownCardsPreservedCount = unknownPreservedCount,
            skippedCount = skippedCount,
            unknownCardIds = unknownCardIds.toList()
        )
    }

    private suspend fun persistUpdatedCard(
        updated: UserCardEntityV2,
        key: Pair<String, String>,
        pendingIndexByKey: Map<Pair<String, String>, Int>,
        toInsert: MutableList<UserCardEntityV2>,
        userCardDao: UserCardDaoV2
    ) {
        val pendingIndex = pendingIndexByKey[key]
        if (pendingIndex != null) {
            toInsert[pendingIndex] = updated
        } else {
            userCardDao.updateUserCard(updated)
        }
    }

    internal fun mergeExistingCard(
        existing: UserCardEntityV2,
        item: ExportedUserCardItem,
        strategy: ExportMergeStrategy,
        isPrimaryEffective: Boolean
    ): UserCardEntityV2? = when (strategy) {
        ExportMergeStrategy.SKIP_EXISTING -> null
        ExportMergeStrategy.OVERWRITE_EXISTING -> existing.copy(
            quantity = item.quantity.coerceAtLeast(1),
            condition = item.condition ?: existing.condition,
            category = item.category.ifBlank { existing.category },
            notes = item.notes ?: existing.notes,
            isPrimary = isPrimaryEffective
        )
        ExportMergeStrategy.MERGE_ADD -> existing.copy(
            quantity = existing.quantity + item.quantity,
            condition = item.condition ?: existing.condition,
            notes = item.notes ?: existing.notes,
            isPrimary = isPrimaryEffective || existing.isPrimary
        )
    }

    internal fun shouldKeepImportedPrimary(
        item: ExportedUserCardItem,
        existingPrimary: UserCardEntityV2?
    ): Boolean {
        // A card without a known dex id cannot be a favorite, and an imported
        // non-favorite must never become primary just because it is new.
        if (!item.isPrimary || item.pokemonDexId <= 0) return false
        return existingPrimary == null ||
            (existingPrimary.cardId == item.cardId && existingPrimary.language == item.language)
    }
}
