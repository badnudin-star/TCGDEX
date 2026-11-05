package com.pokemontcg.sammlung.ui.catalog

import com.pokemontcg.sammlung.data.importer.compareCatalogVersions
import com.pokemontcg.sammlung.ui.main.SyncUiState

enum class CatalogAvailability {
    MISSING,
    READY,
    UPDATE_AVAILABLE,
    APP_UPDATE_REQUIRED
}

fun catalogAvailability(
    catalogIsEmpty: Boolean,
    installedVersion: String?,
    remoteVersion: String?,
    requiresAppUpdate: Boolean
): CatalogAvailability = when {
    requiresAppUpdate -> CatalogAvailability.APP_UPDATE_REQUIRED
    catalogIsEmpty -> CatalogAvailability.MISSING
    !remoteVersion.isNullOrBlank() &&
        !installedVersion.isNullOrBlank() &&
        compareCatalogVersions(remoteVersion, installedVersion) > 0 -> CatalogAvailability.UPDATE_AVAILABLE
    else -> CatalogAvailability.READY
}

data class CatalogManagerUiState(
    val isLoaded: Boolean = false,
    val catalogIsEmpty: Boolean = false,
    val pokemonCount: Int = 0,
    val cardCount: Int = 0,
    val setCount: Int = 0,
    val installedVersion: String? = null,
    val remoteVersion: String? = null,
    val installedAt: Long = 0L,
    val lastCheckAt: Long = 0L,
    val remoteSizeBytes: Long? = null,
    val requiresAppUpdate: Boolean = false,
    val sync: SyncUiState = SyncUiState()
) {
    val availability: CatalogAvailability
        get() = catalogAvailability(
            catalogIsEmpty = catalogIsEmpty,
            installedVersion = installedVersion,
            remoteVersion = remoteVersion,
            requiresAppUpdate = requiresAppUpdate
        )
}
