package com.pokemontcg.sammlung.data.model

/** Fine-grained phase of a catalog update, shown by the sync UI. */
enum class SyncStage(val key: String) {
    CHECKING("checking"),
    DOWNLOADING("downloading"),
    VERIFYING("verifying"),
    IMPORTING("importing"),
    START_CATALOG("start_catalog"),
    NAMES("names"),
    PRICES("prices");

    companion object {
        fun fromKey(key: String?): SyncStage? = values().firstOrNull { it.key == key }
    }
}
