package com.pokemontcg.sammlung.data.export

import androidx.annotation.StringRes
import com.google.gson.annotations.SerializedName
import com.pokemontcg.sammlung.BuildConfig

class CollectionExportException(
    @StringRes val messageRes: Int,
    vararg val formatArgs: Any
) : IllegalArgumentException()

data class CollectionExportData(
    @SerializedName("schemaVersion") val schemaVersion: Int = 2,
    @SerializedName("exportedAt") val exportedAt: String = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now()),
    @SerializedName("appVersion") val appVersion: String = BuildConfig.VERSION_NAME,
    @SerializedName("items") val items: List<ExportedUserCardItem> = emptyList(),
    @SerializedName("wishlist") val wishlist: List<ExportedWishlistItem> = emptyList()
)

data class ExportedUserCardItem(
    @SerializedName("cardId") val cardId: String,
    @SerializedName("language") val language: String = "de",
    @SerializedName("quantity") val quantity: Int = 1,
    @SerializedName("condition") val condition: String? = null,
    @SerializedName("category") val category: String = "NONE",
    @SerializedName("isPrimary") val isPrimary: Boolean = false,
    @SerializedName("pokemonDexId") val pokemonDexId: Int = 0,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("gradingCompany") val gradingCompany: String? = null,
    @SerializedName("grade") val grade: String? = null,
    @SerializedName("certNumber") val certNumber: String? = null,
    @SerializedName("addedAt") val addedAt: Long = System.currentTimeMillis()
)

data class ExportedWishlistItem(
    @SerializedName("cardId") val cardId: String,
    @SerializedName("targetPrice") val targetPrice: Double? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("addedAt") val addedAt: Long = System.currentTimeMillis()
)

enum class ExportMergeStrategy {
    SKIP_EXISTING,       // "Nur Neues hinzufügen" (Standard)
    OVERWRITE_EXISTING,  // "Vorhandene überschreiben"
    MERGE_ADD            // "Vorhandene zusammenführen (Anzahl addieren)"
}

data class ImportPreview(
    val totalRead: Int,
    val newCardsCount: Int,
    val existingCardsCount: Int,
    val unknownCardIds: List<String>
)

data class ImportResult(
    val totalProcessed: Int,
    val insertedCount: Int,
    val updatedCount: Int,
    val unknownCardsPreservedCount: Int,
    val skippedCount: Int,
    val unknownCardIds: List<String>
)
