package com.pokemontcg.sammlung.ui.cards

/** Prevents an asynchronous scanner lookup from updating UI owned by a different navigation entry. */
internal fun isCurrentScannerRequest(
    currentRoute: String?,
    currentEntryId: String?,
    requestedEntryId: String?,
    currentRequestSequence: Long,
    requestedRequestSequence: Long
): Boolean =
    currentRoute == "scanner" &&
        !requestedEntryId.isNullOrBlank() &&
        currentEntryId == requestedEntryId &&
        requestedRequestSequence > 0L &&
        currentRequestSequence == requestedRequestSequence
