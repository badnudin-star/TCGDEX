package com.pokemontcg.sammlung.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_checkpoint")
data class SyncCheckpointEntity(
    @PrimaryKey val unit: String,
    val runId: Long,
    val state: String,
    val attempts: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val error: String? = null
)
