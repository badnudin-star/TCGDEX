package com.pokemontcg.sammlung.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.pokemontcg.sammlung.BuildConfig
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.api.ApiService
import com.pokemontcg.sammlung.data.importer.CatalogImporter
import com.pokemontcg.sammlung.data.importer.CatalogImportResult
import com.pokemontcg.sammlung.data.local.AppDatabase
import com.pokemontcg.sammlung.data.local.entities.PokemonEntity
import com.pokemontcg.sammlung.data.local.entities.PokemonNameEntity
import com.pokemontcg.sammlung.worker.ResumableCatalogWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SyncRepository(
    private val db: AppDatabase,
    private val context: Context
) {

    private val workManager by lazy { WorkManager.getInstance(context.applicationContext) }
    private val catalogImporter = CatalogImporter(context, db)

    fun hasNetworkConnection(): Boolean {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    fun startResumableCatalogSync() {
        val request = OneTimeWorkRequestBuilder<ResumableCatalogWorker>()
            .addTag(ResumableCatalogWorker.SYNC_TAG)
            .build()
        workManager.enqueueUniqueWork(
            ResumableCatalogWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * All catalog sync requests (manual, periodic and continuation) share one tag so the
     * UI can observe the complete chain across the 8-minute work slices.
     */
    fun observeSyncWork(): Flow<List<WorkInfo>> =
        workManager.getWorkInfosByTagFlow(ResumableCatalogWorker.SYNC_TAG)

    suspend fun importStartCatalogIfNeeded(): CatalogImportResult {
        // Never replace an existing local catalog with the potentially older bundled snapshot.
        val hasSets = db.setDao().getAllSets().first().isNotEmpty()
        val hasCards = db.cardDao().getAllCardsOnce().isNotEmpty()
        if (hasSets && hasCards) return CatalogImportResult.AlreadyUpToDate
        return catalogImporter.importFromAsset("start_catalog.sqlite")
    }

    suspend fun ensurePokemonNamesLoaded(
        hasNetwork: () -> Boolean = { hasNetworkConnection() },
        loadPokemon: suspend () -> List<PokemonEntity> = { fetchPokemonList() }
    ): Boolean {
        if (db.pokemonDao().getPokemonCount() > 0) return true
        if (!hasNetwork()) return false
        return try {
            val pokemonList = loadPokemon()
            if (pokemonList.isNotEmpty()) {
                db.pokemonDao().insertAllIfAbsent(pokemonList)
                val names = pokemonList.map {
                    PokemonNameEntity(language = "en", name = it.name, dexId = it.dexId)
                }
                db.pokemonNameDao().insertAllIfAbsent(names)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun fetchPokemonList(): List<PokemonEntity> {
        val response = ApiService.pokeapi.getPokemonList()
        return response.results.mapIndexed { index, item ->
            val dexId = index + 1
            PokemonEntity(
                id = dexId.toString(),
                dexId = dexId,
                number = "%03d".format(dexId),
                name = item.name.replaceFirstChar { it.uppercase() },
                searchName = item.name.lowercase(),
                imageUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$dexId.png",
                primaryCardImageUrl = null
            )
        }
    }

    suspend fun importRemoteCatalog(
        protectedCardIds: Set<String> = emptySet(),
        protectedPokemonIds: Set<String> = emptySet(),
        onProgress: suspend (String, Int) -> Unit
    ): Boolean {
        val manifestUrl = BuildConfig.CATALOG_MANIFEST_URL
        if (manifestUrl.isBlank()) {
            com.pokemontcg.sammlung.util.AppLogger.log(
                "SyncRepository", "kein Katalog-Manifest konfiguriert"
            )
            onProgress(context.getString(R.string.ui_sync_catalog_manifest_disabled), 100)
            return true
        }

        onProgress(context.getString(R.string.ui_sync_catalog_downloading), 50)
        return when (
            val result = catalogImporter.importRemote(
                manifestUrl,
                protectedCardIds,
                protectedPokemonIds
            )
        ) {
            is CatalogImportResult.Success -> {
                onProgress(context.getString(R.string.ui_sync_catalog_updated), 100)
                true
            }
            CatalogImportResult.AlreadyUpToDate -> {
                onProgress(context.getString(R.string.ui_sync_catalog_current), 100)
                true
            }
            is CatalogImportResult.RequiresAppUpdate -> {
                onProgress(context.getString(R.string.ui_sync_catalog_requires_app_update), 100)
                com.pokemontcg.sammlung.util.AppLogger.log(
                    "SyncRepository",
                    "Katalog-Update benötigt App-Update (Schema ${result.remoteSchemaVersion} != ${result.appSchemaVersion})"
                )
                // Keep the rest of the sync running; diagnostics shows the blocking state.
                true
            }
            is CatalogImportResult.Error -> {
                com.pokemontcg.sammlung.util.AppLogger.log(
                    "SyncRepository", "Katalog-Update fehlgeschlagen: ${result.message}"
                )
                false
            }
        }
    }

    suspend fun getLastUpdateTimestamp(): Long {
        return db.lastUpdateDao().getLastUpdate()?.lastUpdateTimestamp ?: 0L
    }
}
