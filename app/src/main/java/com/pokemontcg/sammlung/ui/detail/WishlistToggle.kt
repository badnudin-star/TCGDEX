package com.pokemontcg.sammlung.ui.detail

import com.pokemontcg.sammlung.data.local.user.WishlistEntity

internal enum class WishlistToggleAction {
    ADD,
    REMOVE
}

internal fun wishlistToggleAction(cardId: String, wishlistCardIds: Set<String>): WishlistToggleAction =
    if (cardId in wishlistCardIds) WishlistToggleAction.REMOVE else WishlistToggleAction.ADD

internal fun wishlistEntry(cardId: String, addedAt: Long): WishlistEntity =
    WishlistEntity(
        cardId = cardId,
        targetPrice = null,
        notes = "",
        addedAt = addedAt
    )

/**
 * Applies the add/remove decision against injected wishlist operations. Keeping the
 * operations as lambdas makes the toggle path testable without a Room database.
 */
internal suspend fun applyWishlistToggle(
    cardId: String,
    loadWishlist: suspend () -> List<WishlistEntity>,
    add: suspend (WishlistEntity) -> Unit,
    remove: suspend (String) -> Unit,
    now: Long = System.currentTimeMillis()
) {
    val action = wishlistToggleAction(cardId, loadWishlist().map { it.cardId }.toSet())
    when (action) {
        WishlistToggleAction.ADD -> add(wishlistEntry(cardId, now))
        WishlistToggleAction.REMOVE -> remove(cardId)
    }
}
