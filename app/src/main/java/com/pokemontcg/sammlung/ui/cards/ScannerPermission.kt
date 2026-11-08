package com.pokemontcg.sammlung.ui.cards

/**
 * Decides whether a denied camera permission is permanent: after two denials Android
 * stops surfacing the system dialog, so the app must send the user to the settings.
 * Kept free of Android UI imports so it stays unit-testable without Compose.
 */
internal fun isScannerPermissionPermanentlyDenied(
    hasPermission: Boolean,
    permissionDenied: Boolean,
    hasActivity: Boolean,
    shouldShowRationale: Boolean
): Boolean =
    !hasPermission && permissionDenied && hasActivity && !shouldShowRationale
