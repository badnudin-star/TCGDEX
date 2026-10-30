package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.ui.cards.isScannerPermissionPermanentlyDenied
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerPermissionTest {

    @Test
    fun onlyDeniedPermissionWithoutRationaleIsPermanent() {
        assertTrue(
            isScannerPermissionPermanentlyDenied(
                hasPermission = false,
                permissionDenied = true,
                hasActivity = true,
                shouldShowRationale = false
            )
        )
    }

    @Test
    fun firstDenialRemainsRequestable() {
        assertFalse(
            isScannerPermissionPermanentlyDenied(
                hasPermission = false,
                permissionDenied = true,
                hasActivity = true,
                shouldShowRationale = true
            )
        )
    }

    @Test
    fun grantedPermissionIsNeverPermanentDenial() {
        assertFalse(
            isScannerPermissionPermanentlyDenied(
                hasPermission = true,
                permissionDenied = true,
                hasActivity = true,
                shouldShowRationale = false
            )
        )
    }

    @Test
    fun missingActivityCannotOpenSettings() {
        assertFalse(
            isScannerPermissionPermanentlyDenied(
                hasPermission = false,
                permissionDenied = true,
                hasActivity = false,
                shouldShowRationale = false
            )
        )
    }
}
