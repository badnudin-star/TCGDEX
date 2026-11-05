package com.pokemontcg.sammlung.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncStageTest {

    @Test
    fun everyStageRoundTripsThroughItsStorageKey() {
        SyncStage.values().forEach { stage ->
            assertEquals(stage, SyncStage.fromKey(stage.key))
        }
    }

    @Test
    fun storageKeysAreUnique() {
        val keys = SyncStage.values().map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun unknownOrMissingKeysAreIgnored() {
        assertNull(SyncStage.fromKey(null))
        assertNull(SyncStage.fromKey(""))
        assertNull(SyncStage.fromKey("not-a-stage"))
    }
}
