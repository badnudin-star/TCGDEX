package com.pokemontcg.sammlung

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokemontcg.sammlung.data.local.AppDatabase
import com.pokemontcg.sammlung.data.local.entities.PokemonEntity
import com.pokemontcg.sammlung.data.repository.SyncRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PokemonNameSyncTest {

    private lateinit var appDb: AppDatabase
    private lateinit var repository: SyncRepository

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = SyncRepository(appDb, context)
    }

    @After
    fun closeDatabase() {
        appDb.close()
    }

    private fun pokemon(name: String, searchName: String) = PokemonEntity(
        id = "1",
        dexId = 1,
        number = "001",
        name = name,
        searchName = searchName,
        imageUrl = null,
        primaryCardImageUrl = null
    )

    @Test
    fun existingGermanPokemonNameSurvivesNameSync() = runBlocking {
        appDb.pokemonDao().insertAllIfAbsent(listOf(pokemon("Bisasam", "bisasam")))

        val result = repository.ensurePokemonNamesLoaded()

        assertTrue(result)
        assertEquals("Bisasam", appDb.pokemonDao().getPokemonByDexId(1)?.name)
        assertTrue(appDb.pokemonNameDao().getAllPokemonNames().first().isEmpty())
    }

    @Test
    fun emptyPokemonTableReceivesEnglishNames() = runBlocking {
        val result = repository.ensurePokemonNamesLoaded(
            hasNetwork = { true },
            loadPokemon = { listOf(pokemon("Bulbasaur", "bulbasaur")) }
        )

        assertTrue(result)
        assertEquals("Bulbasaur", appDb.pokemonDao().getPokemonByDexId(1)?.name)
        val names = appDb.pokemonNameDao().getAllPokemonNames().first()
        assertEquals(1, names.size)
        assertEquals("en", names.single().language)
        assertEquals("Bulbasaur", names.single().name)
    }
}
