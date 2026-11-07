package com.pokemontcg.sammlung.data.importer

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pokemontcg.sammlung.data.local.AppDatabase
import com.pokemontcg.sammlung.data.model.SyncStage
import com.pokemontcg.sammlung.data.preferences.AppPreferencesRepository
import com.pokemontcg.sammlung.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.security.MessageDigest

data class CatalogCoverage(
    val sets: Int,
    val cards: Int,
    val expectedCards: Int,
    val coveragePercent: Double?
)

data class CatalogManifest(
    val version: String,
    val schemaVersion: Int,
    val sha256: String,
    val size: Long,
    val builtAt: Long,
    val url: String = "",
    val coverage: Map<String, CatalogCoverage> = emptyMap()
)

sealed class CatalogImportResult {
    data class Success(val importedSets: Int, val importedCards: Int, val manifest: CatalogManifest? = null) : CatalogImportResult()
    data class Error(val message: String) : CatalogImportResult()
    data class RequiresAppUpdate(val remoteSchemaVersion: Int, val appSchemaVersion: Int) : CatalogImportResult()
    object AlreadyUpToDate : CatalogImportResult()
    /** The download was interrupted on purpose (time slice / worker stop); retry later. */
    object Deferred : CatalogImportResult()
}

enum class CatalogUpdateAction {
    DOWNLOAD,
    USE_CACHED,
    RETRY_WITHOUT_ETAG,
    ALREADY_UP_TO_DATE,
    REQUIRES_APP_UPDATE
}

/** Numeric version comparison so catalog versions such as 2026.10.31 sort correctly. */
fun compareCatalogVersions(left: String, right: String): Int {
    val leftParts = Regex("\\d+").findAll(left).mapNotNull { it.value.toLongOrNull() }.toList()
    val rightParts = Regex("\\d+").findAll(right).mapNotNull { it.value.toLongOrNull() }.toList()
    for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
        val comparison = (leftParts.getOrNull(index) ?: 0L)
            .compareTo(rightParts.getOrNull(index) ?: 0L)
        if (comparison != 0) return comparison
    }
    return 0
}

fun decideCatalogUpdate(
    notModified: Boolean,
    manifest: CatalogManifest?,
    installedVersion: String?,
    currentSchemaVersion: Int,
    localCatalogUsable: Boolean
): CatalogUpdateAction {
    if (notModified) {
        return if (localCatalogUsable) CatalogUpdateAction.USE_CACHED
        else CatalogUpdateAction.RETRY_WITHOUT_ETAG
    }
    requireNotNull(manifest) { "Manifest is required for a non-304 response" }
    if (manifest.schemaVersion != currentSchemaVersion) {
        return CatalogUpdateAction.REQUIRES_APP_UPDATE
    }
    if (localCatalogUsable && installedVersion != null &&
        compareCatalogVersions(manifest.version, installedVersion) <= 0
    ) {
        return CatalogUpdateAction.ALREADY_UP_TO_DATE
    }
    return CatalogUpdateAction.DOWNLOAD
}

class CatalogImporter(
    private val context: Context,
    private val appDatabase: AppDatabase,
    preferencesRepository: AppPreferencesRepository? = null
) {
    private val updateState: AppPreferencesRepository by lazy {
        preferencesRepository ?: AppPreferencesRepository(context)
    }
    private companion object {
        // Must match AppDatabase schema 17.json; change with a Room schema migration.
        const val EXPECTED_ROOM_IDENTITY_HASH = "545f4e1ec7d1f8ad7d5926ce320dabdb"
        const val CURRENT_CATALOG_SCHEMA_VERSION = 17
        const val MAX_MANIFEST_BYTES = 256 * 1024L
        const val MAX_CATALOG_BYTES = 512 * 1024 * 1024L
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        private const val PROTECTED_CARDS_TABLE = "protected_cards"
        private const val PROTECTED_POKEMON_TABLE = "protected_pokemon"
        private const val PROTECTED_ID_CHUNK_SIZE = 500

        private val CARD_COLUMNS = listOf(
            "name", "supertype", "number", "rarity", "setId", "setName", "series",
            "imageUrl", "largeImageUrl", "marketPrice", "lowPrice", "trendPrice",
            "nationalPokedexNumber", "subtypes", "language", "nationalPokedexNumbers",
            "setLogoUrl", "setSymbolUrl", "setCardCountOfficial", "setCardCountTotal",
            "setReleaseDate"
        )

        /** Columns a brief catalog row can refresh without erasing loaded details. */
        private val CATALOG_ONLY_CARD_COLUMNS = listOf(
            "name", "number", "setId", "setName", "series", "imageUrl",
            "setLogoUrl", "setSymbolUrl", "setCardCountOfficial", "setCardCountTotal",
            "setReleaseDate"
        )

        private val CATALOG_NULLABLE_COLUMNS = setOf(
            "imageUrl", "setLogoUrl", "setSymbolUrl",
            "setCardCountOfficial", "setCardCountTotal", "setReleaseDate"
        )
    }

    suspend fun importFromAsset(
        assetName: String = "start_catalog.sqlite",
        protectedCardIds: Set<String> = emptySet(),
        protectedPokemonIds: Set<String> = emptySet()
    ): CatalogImportResult = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_import_catalog.sqlite")
        try {
            val assetStream: InputStream = try {
                context.assets.open(assetName)
            } catch (_: Exception) {
                try {
                    context.assets.open("databases/$assetName")
                } catch (_: Exception) {
                    AppLogger.log("CatalogImporter", "Start-Katalog Asset '$assetName' nicht vorhanden.")
                    return@withContext CatalogImportResult.Error("Asset not found")
                }
            }

            // Asset streams must be closed even if copying fails. Apply the same size
            // ceiling as remote catalogs so packaged assets cannot bypass the limit.
            assetStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    copyStreamLimited(input, output, MAX_CATALOG_BYTES)
                }
            }

            val result = importCatalogFile(tempFile, protectedCardIds, protectedPokemonIds)
            tempFile.delete()
            result
        } catch (e: Exception) {
            tempFile.delete()
            AppLogger.log("CatalogImporter", "Import aus Asset fehlgeschlagen: ${e.message}")
            CatalogImportResult.Error(e.message ?: "Unknown asset import error")
        }
    }

    suspend fun importRemote(
        manifestUrl: String,
        protectedCardIds: Set<String> = emptySet(),
        protectedPokemonIds: Set<String> = emptySet(),
        onStage: suspend (SyncStage) -> Unit = {},
        onDownloadProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        shouldStop: suspend () -> Boolean = { false }
    ): CatalogImportResult =
        importRemoteInternal(
            manifestUrl = manifestUrl,
            allowUnconditionalRetry = true,
            protectedCardIds = protectedCardIds,
            protectedPokemonIds = protectedPokemonIds,
            onStage = onStage,
            onDownloadProgress = onDownloadProgress,
            shouldStop = shouldStop
        )

    private suspend fun importRemoteInternal(
        manifestUrl: String,
        allowUnconditionalRetry: Boolean,
        protectedCardIds: Set<String>,
        protectedPokemonIds: Set<String>,
        onStage: suspend (SyncStage) -> Unit,
        onDownloadProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit,
        shouldStop: suspend () -> Boolean
    ): CatalogImportResult = withContext(Dispatchers.IO) {
        if (manifestUrl.isBlank()) {
            AppLogger.log("CatalogImporter", "kein Katalog-Manifest konfiguriert")
            return@withContext CatalogImportResult.Error("kein Katalog-Manifest konfiguriert")
        }

        if (!isValidHttpsUrl(manifestUrl)) {
            AppLogger.log("CatalogImporter", "Manifest-URL muss eine absolute HTTPS-Adresse ohne Zugangsdaten sein.")
            return@withContext CatalogImportResult.Error("Manifest URL must be HTTPS")
        }

        onStage(SyncStage.CHECKING)
        val checkedAt = System.currentTimeMillis()
        try {
            // GitHub release URLs answer with a redirect to a signed asset host. Follow
            // redirects explicitly so every hop stays HTTPS and no downgrade can slip through.
            val etag = updateState.catalogEtagFlow.first()
            val requestHeaders = if (!etag.isNullOrBlank()) {
                mapOf("If-None-Match" to etag)
            } else {
                emptyMap()
            }
            val connection = openHttpsConnectionFollowingRedirects(
                initialUrl = manifestUrl,
                connectTimeout = 15000,
                readTimeout = 15000,
                requestHeaders = requestHeaders
            )

            // Always disconnect, including when responseCode/header/body reads throw.
            // Early returns from this block still execute the finally clause.
            val (manifestJson, newEtag) = try {
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    val storedSchemaVersion = updateState.catalogManifestSchemaVersionFlow.first()
                    val localDb = appDatabase.openHelper.writableDatabase
                    val localSetCount = localDb.queryLong("SELECT COUNT(*) FROM sets")
                    val localCardCount = localDb.queryLong("SELECT COUNT(*) FROM cards")
                    val cachedUsable = isCachedCatalogUsable(
                        storedSchemaVersion,
                        CURRENT_CATALOG_SCHEMA_VERSION,
                        localSetCount,
                        localCardCount
                    )
                    return@withContext when (decideCatalogUpdate(
                        notModified = true,
                        manifest = null,
                        installedVersion = null,
                        currentSchemaVersion = CURRENT_CATALOG_SCHEMA_VERSION,
                        localCatalogUsable = cachedUsable
                    )) {
                        CatalogUpdateAction.USE_CACHED -> {
                            updateState.setCatalogLastCheckAt(checkedAt)
                            updateState.setCatalogRequiresAppUpdate(false)
                            AppLogger.log("CatalogImporter", "Katalog-Manifest unverändert und lokaler Katalog vorhanden (304)")
                            CatalogImportResult.AlreadyUpToDate
                        }
                        CatalogUpdateAction.RETRY_WITHOUT_ETAG -> {
                            // A persisted ETag is only a valid shortcut if this app version has
                            // already imported the matching schema and the local catalog is present.
                            // Clear stale metadata and retry once without If-None-Match.
                            updateState.setCatalogEtag(null)
                            AppLogger.log("CatalogImporter", "304 ohne passenden lokalen Katalog; ETag wird verworfen.")
                            if (allowUnconditionalRetry) {
                                importRemoteInternal(
                                    manifestUrl = manifestUrl,
                                    allowUnconditionalRetry = false,
                                    protectedCardIds = protectedCardIds,
                                    protectedPokemonIds = protectedPokemonIds,
                                    onStage = onStage,
                                    onDownloadProgress = onDownloadProgress
                                )
                            } else {
                                CatalogImportResult.Error("Manifest returned 304 without a usable local catalog")
                            }
                        }
                        else -> CatalogImportResult.Error("Unexpected 304 update decision")
                    }
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    AppLogger.log("CatalogImporter", "Manifest Download fehlgeschlagen: HTTP $responseCode")
                    return@withContext CatalogImportResult.Error("HTTP $responseCode")
                }

                val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
                if (contentLength > MAX_MANIFEST_BYTES) {
                    AppLogger.log("CatalogImporter", "Manifest überschreitet die zulässige Größe.")
                    return@withContext CatalogImportResult.Error("Manifest too large")
                }

                val body = connection.inputStream.use { readTextLimited(it, MAX_MANIFEST_BYTES) }
                body to connection.getHeaderField("ETag")
            } finally {
                connection.disconnect()
            }

            val manifest = try {
                parseManifestJson(manifestJson)
            } catch (e: Exception) {
                AppLogger.log("CatalogImporter", "Manifest-Parsing fehlgeschlagen: ${e.message}")
                return@withContext CatalogImportResult.Error("Invalid manifest JSON")
            }

            // Cache the remote metadata even when no download is needed so the catalog
            // manager can show the available version and expected download size offline.
            updateState.setCatalogRemoteMetadata(manifest.version, manifest.size)
            val installedVersion = updateState.catalogInstalledVersionFlow.first()
            val localCatalogUsable = localCatalogUsable()
            when (decideCatalogUpdate(
                notModified = false,
                manifest = manifest,
                installedVersion = installedVersion,
                currentSchemaVersion = CURRENT_CATALOG_SCHEMA_VERSION,
                localCatalogUsable = localCatalogUsable
            )) {
                CatalogUpdateAction.REQUIRES_APP_UPDATE -> {
                    updateState.setCatalogRequiresAppUpdate(true)
                    updateState.setCatalogManifestSchemaVersion(manifest.schemaVersion)
                    updateState.setCatalogLastCheckAt(checkedAt)
                    AppLogger.log(
                        "CatalogImporter",
                        "Katalog-Update benötigt App-Update (Schema ${manifest.schemaVersion} != $CURRENT_CATALOG_SCHEMA_VERSION)"
                    )
                    return@withContext CatalogImportResult.RequiresAppUpdate(
                        remoteSchemaVersion = manifest.schemaVersion,
                        appSchemaVersion = CURRENT_CATALOG_SCHEMA_VERSION
                    )
                }
                CatalogUpdateAction.ALREADY_UP_TO_DATE -> {
                    updateState.setCatalogEtag(newEtag)
                    updateState.setCatalogManifestSchemaVersion(manifest.schemaVersion)
                    updateState.setCatalogRequiresAppUpdate(false)
                    updateState.setCatalogLastCheckAt(checkedAt)
                    AppLogger.log(
                        "CatalogImporter",
                        "Katalogversion ${manifest.version} ist nicht neuer als installiert ($installedVersion); Download übersprungen."
                    )
                    return@withContext CatalogImportResult.AlreadyUpToDate
                }
                CatalogUpdateAction.DOWNLOAD -> Unit
                CatalogUpdateAction.USE_CACHED,
                CatalogUpdateAction.RETRY_WITHOUT_ETAG -> {
                    return@withContext CatalogImportResult.Error("Unexpected catalog update decision")
                }
            }

            onStage(SyncStage.DOWNLOADING)
            val cacheFile = File(context.cacheDir, "remote_catalog.sqlite")
            try {
                // The manifest URL is validated as HTTPS, and every redirect hop is checked
                // again before the payload is fetched from the final asset host.
                val downloadConn = openHttpsConnectionFollowingRedirects(
                    initialUrl = manifest.url,
                    connectTimeout = 30000,
                    readTimeout = 30000
                )
                try {
                    // Do not accept an HTML error page as a catalog payload.
                    val downloadResponseCode = downloadConn.responseCode
                    if (downloadResponseCode != HttpURLConnection.HTTP_OK) {
                        cacheFile.delete()
                        AppLogger.log("CatalogImporter", "Katalog-Download fehlgeschlagen: HTTP $downloadResponseCode")
                        return@withContext CatalogImportResult.Error("Catalog HTTP $downloadResponseCode")
                    }

                    val downloadCompleted = FileOutputStream(cacheFile).use { output ->
                        downloadConn.inputStream.use { input ->
                            copyStreamLimitedWithProgress(
                                input = input,
                                output = output,
                                maxBytes = manifest.size,
                                shouldStop = shouldStop,
                                onProgress = onDownloadProgress
                            )
                        }
                    }
                    if (!downloadCompleted) {
                        // A partial file must never be validated or imported: it would fail the
                        // sha256 check and burn a full 60 MB download on the next attempt anyway.
                        cacheFile.delete()
                        AppLogger.log(
                            "CatalogImporter",
                            "Katalog-Download abgebrochen (Zeitscheibe oder Worker-Stopp). Teil-Datei verworfen."
                        )
                        return@withContext CatalogImportResult.Deferred
                    }
                } finally {
                    // Also release the connection when opening the response or reading headers fails.
                    downloadConn.disconnect()
                }

                onStage(SyncStage.VERIFYING)
                val validationError = validateDownloadedCatalog(cacheFile, manifest.size, manifest.sha256)
                if (validationError != null) {
                    cacheFile.delete()
                    AppLogger.log("CatalogImporter", "Katalog-Download ungültig: $validationError")
                    return@withContext CatalogImportResult.Error(validationError)
                }

                onStage(SyncStage.IMPORTING)
                val result = importCatalogFile(cacheFile, protectedCardIds, protectedPokemonIds)
                cacheFile.delete()

                if (result is CatalogImportResult.Success) {
                    updateState.setCatalogEtag(newEtag)
                    updateState.setCatalogInstalledVersion(manifest.version)
                    updateState.setCatalogInstalledAt(System.currentTimeMillis())
                    updateState.setCatalogManifestSchemaVersion(manifest.schemaVersion)
                    updateState.setCatalogRequiresAppUpdate(false)
                    updateState.setCatalogLastCheckAt(checkedAt)
                }

                result
            } catch (e: Exception) {
                cacheFile.delete()
                AppLogger.log("CatalogImporter", "Remote Catalog Import Fehler: ${e.message}")
                CatalogImportResult.Error(e.message ?: "Remote import error")
            }
        } catch (e: Exception) {
            AppLogger.log("CatalogImporter", "Manifest HTTP Fehler: ${e.message}")
            CatalogImportResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun importCatalogFile(
        sourceFile: File,
        protectedCardIds: Set<String> = emptySet(),
        protectedPokemonIds: Set<String> = emptySet()
    ): CatalogImportResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) {
            return@withContext CatalogImportResult.Error("Source catalog file does not exist")
        }
        if (!sourceFile.isFile || sourceFile.length() !in 1L..MAX_CATALOG_BYTES) {
            return@withContext CatalogImportResult.Error("Source catalog file size is invalid")
        }

        AppLogger.log("CatalogImporter", "Katalog-Import gestartet von ${sourceFile.name}")
        val db: SupportSQLiteDatabase = appDatabase.openHelper.writableDatabase
        val requiredTables = listOf("sets", "cards", "pokemon", "card_pokemon", "pokemon_names")
        var attached = false
        val alias = "attached_catalog"
        try {
            val source = android.database.sqlite.SQLiteDatabase.openDatabase(
                sourceFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            source.use { sourceDb ->
                val integrity = sourceDb.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
                }
                if (!integrity) {
                    AppLogger.log("CatalogImporter", "Integritätsprüfung der Quelldatei fehlgeschlagen!")
                    return@withContext CatalogImportResult.Error("Integrity check failed")
                }
                val sourceTables = sourceDb.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table'", null
                ).use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
                val missing = requiredTables.filterNot { it in sourceTables }
                if (missing.isNotEmpty()) {
                    AppLogger.log("CatalogImporter", "Katalogschema unvollständig: ${missing.joinToString()}")
                    return@withContext CatalogImportResult.Error("Catalog schema missing: ${missing.joinToString()}")
                }
                // A matching Room identity hash alone is not enough: a damaged or
                // hand-edited file can keep the old hash while changing column order
                // or shape. Compare the actual imported table layouts before ATTACH.
                val schemaMismatches = requiredTables.filter { table ->
                    readTableSignature(sourceDb, table) != readTableSignature(db, table)
                }
                if (schemaMismatches.isNotEmpty()) {
                    AppLogger.log("CatalogImporter", "Katalogtabellen weichen vom App-Schema ab: ${schemaMismatches.joinToString()}")
                    return@withContext CatalogImportResult.Error("Catalog table schema mismatch: ${schemaMismatches.joinToString()}")
                }
                val roomHash = if ("room_master_table" in sourceTables) {
                    sourceDb.rawQuery("SELECT identity_hash FROM room_master_table WHERE id=42", null).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                } else null
                if (roomHash != EXPECTED_ROOM_IDENTITY_HASH) {
                    AppLogger.log("CatalogImporter", "Room-Identity-Hash des Katalogs passt nicht zu Schema 17.")
                    return@withContext CatalogImportResult.Error("Catalog schema identity mismatch")
                }

                // These catalog entities intentionally do not declare SQLite foreign keys,
                // so integrity_check alone cannot detect orphaned relationships. Validate
                // the logical links before ATTACH or any live-table mutation.
                val relationshipErrors = validateCatalogRelationships(sourceDb)
                if (relationshipErrors.isNotEmpty()) {
                    AppLogger.log("CatalogImporter", "Katalog enthält ungültige Verknüpfungen: ${relationshipErrors.joinToString()}")
                    return@withContext CatalogImportResult.Error("Catalog relationship validation failed: ${relationshipErrors.joinToString()}")
                }

                // Snapshot replacement deletes the current catalog before inserting this
                // source. Reject obviously incomplete snapshots before any live-table write.
                val sourceSetCount = sourceDb.rawQuery("SELECT COUNT(*) FROM sets", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                }
                val sourceCardCount = sourceDb.rawQuery("SELECT COUNT(*) FROM cards", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                }
                if (sourceSetCount <= 0L || sourceCardCount <= 0L) {
                    AppLogger.log("CatalogImporter", "Katalog-Snapshot ist unvollständig: $sourceSetCount Sets, $sourceCardCount Karten.")
                    return@withContext CatalogImportResult.Error("Catalog snapshot is empty or incomplete")
                }
            }

            // ATTACH must happen outside the transaction. Only catalog tables are copied;
            // user_cards, legacy tables, and the separate UserDatabase are never touched.
            val escapedPath = sourceFile.absolutePath.replace("'", "''")
            db.execSQL("ATTACH DATABASE '$escapedPath' AS $alias")
            attached = true
            val importedSets = db.queryLong("SELECT COUNT(*) FROM $alias.sets").toInt()
            val importedCards = db.queryLong("SELECT COUNT(*) FROM $alias.cards").toInt()
            db.beginTransaction()
            try {
                mergeCatalogSnapshot(db, alias, protectedCardIds, protectedPokemonIds)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.execSQL("DETACH DATABASE $alias")
            attached = false
            AppLogger.log("CatalogImporter", "Katalog-Import erfolgreich: $importedSets Sets, $importedCards Karten.")
            CatalogImportResult.Success(importedSets = importedSets, importedCards = importedCards)
        } catch (e: Exception) {
            try {
                if (db.inTransaction()) db.endTransaction()
            } catch (_: Exception) {}
            AppLogger.log("CatalogImporter", "Katalog-Import abgebrochen, Rollback ausgeführt: ${e.message}")
            CatalogImportResult.Error(e.message ?: "Transaction import error")
        } finally {
            if (attached) {
                try { db.execSQL("DETACH DATABASE $alias") } catch (_: Exception) {}
            }
        }
    }

    /**
     * Merges a complete catalog snapshot without discarding local user state.
     *
     * Rows that disappeared upstream are removed, except cards/Pokémon still referenced by
     * the collection or wishlist (protected IDs). Pokémon categories and locally loaded card
     * details survive a snapshot that only contains brief rows (`detailLoaded = 0`).
     */
    private fun mergeCatalogSnapshot(
        db: SupportSQLiteDatabase,
        alias: String,
        protectedCardIds: Set<String>,
        protectedPokemonIds: Set<String>
    ) {
        replaceProtectedIds(db, PROTECTED_CARDS_TABLE, protectedCardIds)
        replaceProtectedIds(db, PROTECTED_POKEMON_TABLE, protectedPokemonIds)

        // A protected wishlist card may have no collection row, so protect the Pokémon
        // linked to it as well before stale Pokémon are removed.
        db.execSQL(
            "INSERT OR IGNORE INTO $PROTECTED_POKEMON_TABLE (id) " +
                "SELECT DISTINCT CAST(cp.dexId AS TEXT) FROM card_pokemon cp " +
                "WHERE cp.cardId IN (SELECT id FROM $PROTECTED_CARDS_TABLE)"
        )

        // Remove only rows that are gone upstream and are not still referenced locally.
        db.execSQL(
            "DELETE FROM card_pokemon " +
                "WHERE cardId NOT IN (SELECT id FROM $alias.cards) " +
                "AND cardId NOT IN (SELECT id FROM $PROTECTED_CARDS_TABLE)"
        )
        db.execSQL(
            "DELETE FROM cards " +
                "WHERE id NOT IN (SELECT id FROM $alias.cards) " +
                "AND id NOT IN (SELECT id FROM $PROTECTED_CARDS_TABLE)"
        )
        db.execSQL(
            "DELETE FROM pokemon " +
                "WHERE id NOT IN (SELECT id FROM $alias.pokemon) " +
                "AND id NOT IN (SELECT id FROM $PROTECTED_POKEMON_TABLE) " +
                "AND dexId NOT IN (SELECT CAST(id AS INTEGER) FROM $PROTECTED_POKEMON_TABLE)"
        )
        db.execSQL(
            "DELETE FROM sets " +
                "WHERE id NOT IN (SELECT id FROM $alias.sets) " +
                "AND id NOT IN (SELECT DISTINCT setId FROM cards)"
        )
        db.execSQL("DELETE FROM card_pokemon WHERE cardId NOT IN (SELECT id FROM cards)")
        db.execSQL("DELETE FROM card_pokemon WHERE dexId NOT IN (SELECT dexId FROM pokemon)")

        // Sets are catalog-only. Preserve stale sets only while a protected card uses them.
        db.execSQL("DELETE FROM sets WHERE id IN (SELECT id FROM $alias.sets)")
        db.execSQL("INSERT INTO sets SELECT * FROM $alias.sets")

        // Pokémon rows keep user-owned cardType, primary image and refresh timestamp.
        db.execSQL("INSERT OR IGNORE INTO pokemon SELECT * FROM $alias.pokemon")
        db.execSQL(
            "UPDATE pokemon SET " +
                "dexId = (SELECT s.dexId FROM $alias.pokemon s WHERE s.id = pokemon.id), " +
                "number = (SELECT s.number FROM $alias.pokemon s WHERE s.id = pokemon.id), " +
                "name = (SELECT s.name FROM $alias.pokemon s WHERE s.id = pokemon.id), " +
                "searchName = (SELECT s.searchName FROM $alias.pokemon s WHERE s.id = pokemon.id), " +
                "imageUrl = (SELECT s.imageUrl FROM $alias.pokemon s WHERE s.id = pokemon.id) " +
                "WHERE id IN (SELECT id FROM $alias.pokemon)"
        )

        // Names for protected Pokémon survive only if the snapshot no longer contains them.
        db.execSQL(
            "DELETE FROM pokemon_names " +
                "WHERE dexId NOT IN (SELECT CAST(id AS INTEGER) FROM $PROTECTED_POKEMON_TABLE) " +
                "OR dexId IN (SELECT dexId FROM $alias.pokemon)"
        )
        db.execSQL("INSERT OR IGNORE INTO pokemon_names SELECT * FROM $alias.pokemon_names")

        // New cards are inserted as-is; existing rows are updated below.
        db.execSQL("INSERT OR IGNORE INTO cards SELECT * FROM $alias.cards")

        // A full source row is authoritative for every card column.
        db.execSQL(
            "UPDATE cards SET " +
                cardAssignments(alias, CARD_COLUMNS) + ", " +
                "sortNumber = COALESCE(NULLIF((SELECT s.sortNumber FROM $alias.cards s WHERE s.id = cards.id), 0), cards.sortNumber), " +
                "detailLoaded = 1 " +
                "WHERE id IN (SELECT id FROM $alias.cards WHERE detailLoaded = 1)"
        )

        // A brief source row replaces another brief row completely.
        db.execSQL(
            "UPDATE cards SET " +
                cardAssignments(alias, CARD_COLUMNS) + ", " +
                "sortNumber = COALESCE(NULLIF((SELECT s.sortNumber FROM $alias.cards s WHERE s.id = cards.id), 0), cards.sortNumber), " +
                "detailLoaded = 0 " +
                "WHERE id IN (SELECT id FROM $alias.cards WHERE detailLoaded = 0) " +
                "AND cards.detailLoaded = 0"
        )

        // A brief source row must not erase locally loaded detail fields.
        db.execSQL(
            "UPDATE cards SET " +
                cardAssignments(
                    alias,
                    CATALOG_ONLY_CARD_COLUMNS,
                    preserveIfNull = CATALOG_NULLABLE_COLUMNS
                ) + ", " +
                "sortNumber = COALESCE(NULLIF((SELECT s.sortNumber FROM $alias.cards s WHERE s.id = cards.id), 0), cards.sortNumber) " +
                "WHERE id IN (SELECT id FROM $alias.cards WHERE detailLoaded = 0) " +
                "AND cards.detailLoaded = 1"
        )

        // Full source cards own their links. Brief source cards keep local links.
        db.execSQL(
            "DELETE FROM card_pokemon " +
                "WHERE cardId IN (SELECT id FROM $alias.cards WHERE detailLoaded = 1)"
        )
        db.execSQL("INSERT OR IGNORE INTO card_pokemon SELECT * FROM $alias.card_pokemon")
        db.execSQL("DELETE FROM card_pokemon WHERE cardId NOT IN (SELECT id FROM cards)")
        db.execSQL("DELETE FROM card_pokemon WHERE dexId NOT IN (SELECT dexId FROM pokemon)")
    }

    private fun replaceProtectedIds(
        db: SupportSQLiteDatabase,
        table: String,
        ids: Set<String>
    ) {
        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS $table (id TEXT NOT NULL PRIMARY KEY)")
        db.execSQL("DELETE FROM $table")
        ids.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .chunked(PROTECTED_ID_CHUNK_SIZE)
            .forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "(?)" }
                db.execSQL(
                    "INSERT OR IGNORE INTO $table (id) VALUES $placeholders",
                    chunk.toTypedArray()
                )
            }
    }

    private fun cardAssignments(
        alias: String,
        columns: List<String>,
        preserveIfNull: Set<String> = emptySet()
    ): String = columns.joinToString(", ") { column ->
        val source = "(SELECT s.$column FROM $alias.cards s WHERE s.id = cards.id)"
        val value = if (column in preserveIfNull) "COALESCE($source, cards.$column)" else source
        "$column = $value"
    }

    private fun validateCatalogRelationships(db: android.database.sqlite.SQLiteDatabase): List<String> {
        val checks = linkedMapOf(
            "cards without a set" to "SELECT COUNT(*) FROM cards c LEFT JOIN sets s ON s.id = c.setId WHERE c.setId IS NULL OR s.id IS NULL",
            "card_pokemon without a card" to "SELECT COUNT(*) FROM card_pokemon cp LEFT JOIN cards c ON c.id = cp.cardId WHERE cp.cardId IS NULL OR c.id IS NULL",
            "card_pokemon without a Pokémon" to "SELECT COUNT(*) FROM card_pokemon cp LEFT JOIN pokemon p ON p.dexId = cp.dexId WHERE cp.dexId IS NULL OR p.dexId IS NULL",
            "pokemon_names without a Pokémon" to "SELECT COUNT(*) FROM pokemon_names n LEFT JOIN pokemon p ON p.dexId = n.dexId WHERE n.dexId IS NULL OR p.dexId IS NULL"
        )
        return checks.mapNotNull { (description, sql) ->
            val invalidCount = db.rawQuery(sql, null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
            if (invalidCount > 0L) "$description ($invalidCount)" else null
        }
    }

    private fun SupportSQLiteDatabase.queryLong(sql: String): Long =
        query(sql).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }

    private fun readTableSignature(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val typeIndex = cursor.getColumnIndexOrThrow("type")
                val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
                val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
                val primaryKeyIndex = cursor.getColumnIndexOrThrow("pk")
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    val type = cursor.getString(typeIndex).uppercase()
                    val notNull = cursor.getInt(notNullIndex)
                    val defaultValue = if (cursor.isNull(defaultIndex)) "<null>" else cursor.getString(defaultIndex)
                    val primaryKey = cursor.getInt(primaryKeyIndex)
                    add("$name|$type|$notNull|$defaultValue|$primaryKey")
                }
            }
        }

    private fun readTableSignature(db: android.database.sqlite.SQLiteDatabase, table: String): List<String> =
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            buildList {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val typeIndex = cursor.getColumnIndexOrThrow("type")
                val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
                val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
                val primaryKeyIndex = cursor.getColumnIndexOrThrow("pk")
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    val type = cursor.getString(typeIndex).uppercase()
                    val notNull = cursor.getInt(notNullIndex)
                    val defaultValue = if (cursor.isNull(defaultIndex)) "<null>" else cursor.getString(defaultIndex)
                    val primaryKey = cursor.getInt(primaryKeyIndex)
                    add("$name|$type|$notNull|$defaultValue|$primaryKey")
                }
            }
        }

    private fun localCatalogUsable(): Boolean {
        val localDb = appDatabase.openHelper.writableDatabase
        return localDb.queryLong("SELECT COUNT(*) FROM sets") > 0L &&
            localDb.queryLong("SELECT COUNT(*) FROM cards") > 0L
    }

    internal fun validateDownloadedCatalog(
        file: File,
        expectedSize: Long,
        expectedSha256: String
    ): String? {
        if (!file.isFile || file.length() != expectedSize) return "Size mismatch"
        if (!computeSha256(file).equals(expectedSha256, ignoreCase = true)) return "SHA256 mismatch"
        return null
    }

    internal fun isCachedCatalogUsable(
        storedSchemaVersion: Int,
        expectedSchemaVersion: Int,
        setCount: Long,
        cardCount: Long
    ): Boolean = storedSchemaVersion == expectedSchemaVersion && setCount > 0L && cardCount > 0L

    fun parseManifestJson(jsonString: String): CatalogManifest {
        val obj = JsonParser.parseString(jsonString).asJsonObject
        // Never fill missing integrity fields with permissive defaults: a malformed manifest
        // must be rejected before any catalog download or database mutation. Gson's asInt/asLong
        // can coerce decimal JSON numbers; require integer JSON tokens and string fields explicitly.
        val version = requiredString(obj, "version")
            .takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Missing manifest version")
        val schemaLong = requiredLong(obj, "schemaVersion")
            .takeIf { it in 1L..Int.MAX_VALUE.toLong() }
            ?: throw IllegalArgumentException("Invalid manifest schemaVersion")
        val schemaVersion = schemaLong.toInt()
        val sha256 = requiredString(obj, "sha256")
            .takeIf { it.matches(Regex("^[0-9a-fA-F]{64}$")) }
            ?: throw IllegalArgumentException("Invalid manifest SHA-256")
        val size = requiredLong(obj, "size")
            .takeIf { it in 1L..MAX_CATALOG_BYTES } ?: throw IllegalArgumentException("Invalid manifest size")
        val builtAt = requiredLong(obj, "builtAt")
            .takeIf { it > 0L } ?: throw IllegalArgumentException("Invalid manifest builtAt")
        val url = requiredString(obj, "url")
            .takeIf(::isValidHttpsUrl)
            ?: throw IllegalArgumentException("Manifest URL must be an absolute HTTPS URL")
        val coverage = obj.get("coverage")?.let(::parseCoverage) ?: emptyMap()

        return CatalogManifest(version, schemaVersion, sha256, size, builtAt, url, coverage)
    }

    private fun parseCoverage(value: com.google.gson.JsonElement): Map<String, CatalogCoverage> {
        if (!value.isJsonObject) {
            throw IllegalArgumentException("Manifest coverage must be an object")
        }
        return value.asJsonObject.entrySet().associate { (language, element) ->
            if (!element.isJsonObject) {
                throw IllegalArgumentException("Manifest coverage '$language' must be an object")
            }
            val item = element.asJsonObject
            val sets = requiredLong(item, "sets")
                .takeIf { it in 0L..Int.MAX_VALUE.toLong() }?.toInt()
                ?: throw IllegalArgumentException("Invalid coverage sets for '$language'")
            val cards = requiredLong(item, "cards")
                .takeIf { it in 0L..Int.MAX_VALUE.toLong() }?.toInt()
                ?: throw IllegalArgumentException("Invalid coverage cards for '$language'")
            val expectedCards = requiredLong(item, "expectedCards")
                .takeIf { it in 0L..Int.MAX_VALUE.toLong() }?.toInt()
                ?: throw IllegalArgumentException("Invalid coverage expectedCards for '$language'")
            val percentElement = item.get("coveragePercent")
            val percent = when {
                percentElement == null || percentElement.isJsonNull -> null
                percentElement.isJsonPrimitive && percentElement.asJsonPrimitive.isNumber -> percentElement.asDouble
                else -> throw IllegalArgumentException("Invalid coveragePercent for '$language'")
            }
            language to CatalogCoverage(sets, cards, expectedCards, percent)
        }
    }

    private fun requiredString(obj: JsonObject, field: String): String {
        val value = obj.get(field)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            throw IllegalArgumentException("Manifest field '$field' must be a string")
        }
        return value.asString
    }

    private fun requiredLong(obj: JsonObject, field: String): Long {
        val value = obj.get(field)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
            throw IllegalArgumentException("Manifest field '$field' must be an integer")
        }
        // Long.parseLong rejects decimal and exponent forms instead of silently truncating them.
        return value.asString.toLongOrNull()
            ?: throw IllegalArgumentException("Manifest field '$field' must be an integer")
    }

    internal fun readTextLimited(input: InputStream, maxBytes: Long): String {
        val output = ByteArrayOutputStream()
        copyStreamLimited(input, output, maxBytes)
        return output.toString(StandardCharsets.UTF_8.name())
    }

    internal fun copyStreamLimited(input: InputStream, output: OutputStream, maxBytes: Long) {
        require(maxBytes >= 0L) { "maxBytes must not be negative" }
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (total + read > maxBytes) {
                throw IOException("Response exceeds permitted size of $maxBytes bytes")
            }
            output.write(buffer, 0, read)
            total += read
        }
    }

    /**
     * Copies a download while reporting exact byte progress. Callbacks are throttled to one
     * update per percent so WorkManager does not receive thousands of progress writes.
     */
    internal suspend fun copyStreamLimitedWithProgress(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long,
        shouldStop: suspend () -> Boolean = { false },
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Boolean {
        require(maxBytes >= 0L) { "maxBytes must not be negative" }
        val buffer = ByteArray(8192)
        var total = 0L
        var lastPercent = -1
        onProgress(0L, maxBytes)
        while (true) {
            // Checked once per 8 KB chunk so an 8-minute time slice or a cancelled worker can
            // stop a 60 MB download instead of letting it run to completion.
            if (shouldStop()) return false
            val read = input.read(buffer)
            if (read < 0) break
            if (total + read > maxBytes) {
                throw IOException("Response exceeds permitted size of $maxBytes bytes")
            }
            output.write(buffer, 0, read)
            total += read
            val percent = if (maxBytes == 0L) 100 else ((total * 100) / maxBytes).toInt()
                .coerceIn(0, 100)
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress(total, maxBytes)
            }
        }
        if (lastPercent != 100) onProgress(total, maxBytes)
        return true
    }

    /**
     * Opens a GET connection and follows redirects manually. Automatic redirect handling
     * is disabled because it can downgrade HTTPS to HTTP, and every hop must be validated.
     * The caller owns the returned connection and must disconnect it.
     */
    private fun openHttpsConnectionFollowingRedirects(
        initialUrl: String,
        connectTimeout: Int,
        readTimeout: Int,
        requestHeaders: Map<String, String> = emptyMap()
    ): HttpURLConnection {
        if (!isValidHttpsUrl(initialUrl)) {
            throw IOException("URL must be an absolute HTTPS address")
        }

        var currentUrl = initialUrl
        var redirectCount = 0
        while (true) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeout
            connection.readTimeout = readTimeout
            requestHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in REDIRECT_CODES) return connection
                if (redirectCount >= MAX_REDIRECTS) {
                    throw IOException("Too many redirects")
                }
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                currentUrl = resolveRedirectUrl(currentUrl, location)
                redirectCount++
            } catch (e: Exception) {
                connection.disconnect()
                throw e
            }
        }
    }

    /** Resolves a redirect Location and rejects anything that is not a plain HTTPS URL. */
    internal fun resolveRedirectUrl(currentUrl: String, location: String?): String {
        if (location.isNullOrBlank()) {
            throw IOException("Redirect response without Location header")
        }
        val target = try {
            URL(URL(currentUrl), location).toString()
        } catch (e: Exception) {
            throw IOException("Redirect Location is not a valid URL", e)
        }
        if (!isValidHttpsUrl(target)) {
            throw IOException("Redirect target must be an absolute HTTPS URL")
        }
        return target
    }

    private fun isValidHttpsUrl(candidate: String): Boolean {
        return try {
            val uri = URI(candidate)
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.fragment == null
        } catch (_: Exception) {
            false
        }
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
