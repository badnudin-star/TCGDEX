package com.pokemontcg.sammlung.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogManagerStateTest {

    @Test
    fun appUpdateRequirementOverridesMissingCatalog() {
        assertEquals(
            CatalogAvailability.APP_UPDATE_REQUIRED,
            catalogAvailability(
                catalogIsEmpty = true,
                installedVersion = null,
                remoteVersion = "2026.11.01",
                requiresAppUpdate = true
            )
        )
    }

    @Test
    fun missingCatalogRequiresEmptyCounts() {
        assertEquals(
            CatalogAvailability.MISSING,
            catalogAvailability(
                catalogIsEmpty = true,
                installedVersion = null,
                remoteVersion = null,
                requiresAppUpdate = false
            )
        )
    }

    @Test
    fun newerRemoteVersionIsReportedAsUpdate() {
        assertEquals(
            CatalogAvailability.UPDATE_AVAILABLE,
            catalogAvailability(
                catalogIsEmpty = false,
                installedVersion = "2026.10.31",
                remoteVersion = "2026.11.01",
                requiresAppUpdate = false
            )
        )
    }

    @Test
    fun sameVersionIsReady() {
        assertEquals(
            CatalogAvailability.READY,
            catalogAvailability(
                catalogIsEmpty = false,
                installedVersion = "2026.10.31",
                remoteVersion = "2026.10.31",
                requiresAppUpdate = false
            )
        )
    }

    @Test
    fun missingRemoteMetadataDoesNotFakeAnUpdate() {
        assertEquals(
            CatalogAvailability.READY,
            catalogAvailability(
                catalogIsEmpty = false,
                installedVersion = "2026.10.31",
                remoteVersion = null,
                requiresAppUpdate = false
            )
        )
    }
}
