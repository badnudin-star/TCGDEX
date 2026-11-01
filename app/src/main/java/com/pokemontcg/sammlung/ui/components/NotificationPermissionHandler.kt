package com.pokemontcg.sammlung.ui.components

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat

@Composable
fun NotificationPermissionHandler(
    onPermissionResult: (Boolean) -> Unit = {}
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return
    }

    val context = LocalContext.current
    var showRationaleDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onPermissionResult(isGranted)
    }

    fun checkAndRequestPermission(context: Context) {
        val permissionState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            showRationaleDialog = true
        } else {
            onPermissionResult(true)
        }
    }

    LaunchedEffect(Unit) {
        checkAndRequestPermission(context)
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            title = {
                Text(
                    stringResource(R.string.ui_benachrichtigungen_aktivieren_4e1b17),
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Text(
                    stringResource(R.string.ui_notification_rationale)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationaleDialog = false
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                ) {
                    Text(stringResource(R.string.ui_zulassen_60a72b))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRationaleDialog = false
                        onPermissionResult(false)
                    }
                ) {
                    Text(stringResource(R.string.ui_sp_ter_a1ec84))
                }
            }
        )
    }
}
