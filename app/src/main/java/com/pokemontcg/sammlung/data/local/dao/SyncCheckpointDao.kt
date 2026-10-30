package com.pokemontcg.sammlung.data.local.dao

import androidx.room.*
import com.pokemontcg.sammlung.data.local.entities.SyncCheckpointEntity

@Dao
interface SyncCheckpointDao {

    @Query("SELECT * FROM sync_checkpoint WHERE unit = :unit LIMIT 1")
    suspend fun getCheckpoint(unit: String): SyncCheckpointEntity?

    @Query("SELECT * FROM sync_checkpoint WHERE runId = :runId")
    suspend fun getCheckpointsForRun(runId: Long): List<SyncCheckpointEntity>

    @Query("SELECT * FROM sync_checkpoint")
    suspend fun getAllCheckpointsOnce(): List<SyncCheckpointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCheckpoint(checkpoint: SyncCheckpointEntity)

    @Query("DELETE FROM sync_checkpoint WHERE runId != :currentRunId")
    suspend fun clearOldRunCheckpoints(currentRunId: Long)

    @Query("DELETE FROM sync_checkpoint")
    suspend fun clearAllCheckpoints()
}
