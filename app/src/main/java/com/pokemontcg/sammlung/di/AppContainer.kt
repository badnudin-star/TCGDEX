package com.pokemontcg.sammlung.di

import android.content.Context
import com.pokemontcg.sammlung.data.local.AppDatabase
import com.pokemontcg.sammlung.data.local.user.UserDatabase
import com.pokemontcg.sammlung.data.preferences.AppPreferencesRepository
import com.pokemontcg.sammlung.data.repository.CatalogRepository
import com.pokemontcg.sammlung.data.repository.CollectionRepository
import com.pokemontcg.sammlung.data.repository.DataCheckRepository
import com.pokemontcg.sammlung.data.repository.PriceRepository
import com.pokemontcg.sammlung.data.repository.SyncRepository

class AppContainer(private val context: Context) {

    val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    val userDatabase: UserDatabase by lazy {
        UserDatabase.getInstance(context)
    }

    val catalogRepository: CatalogRepository by lazy {
        CatalogRepository(appDatabase)
    }

    val collectionRepository: CollectionRepository by lazy {
        CollectionRepository(userDatabase)
    }

    val priceRepository: PriceRepository by lazy {
        PriceRepository(userDatabase, collectionRepository, appDatabase)
    }

    val syncRepository: SyncRepository by lazy {
        SyncRepository(appDatabase, context)
    }

    val preferencesRepository: AppPreferencesRepository by lazy {
        AppPreferencesRepository(context)
    }

    val dataCheckRepository: DataCheckRepository by lazy {
        DataCheckRepository(appDatabase, userDatabase, collectionRepository, preferencesRepository, context)
    }
}
