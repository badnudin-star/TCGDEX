package com.pokemontcg.sammlung.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.model.SyncStage

@StringRes
internal fun syncStageLabelRes(stage: SyncStage): Int = when (stage) {
    SyncStage.CHECKING -> R.string.sync_stage_checking
    SyncStage.DOWNLOADING -> R.string.sync_stage_downloading
    SyncStage.VERIFYING -> R.string.sync_stage_verifying
    SyncStage.IMPORTING -> R.string.sync_stage_importing
    SyncStage.START_CATALOG -> R.string.sync_stage_start_catalog
    SyncStage.NAMES -> R.string.sync_stage_names
    SyncStage.PRICES -> R.string.sync_stage_prices
}

/** Shows the currently active update sub-step, for example the catalog download. */
@Composable
fun SyncStepText(
    stage: SyncStage?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (stage == null) return
    Text(
        text = stringResource(R.string.sync_step_label, stringResource(syncStageLabelRes(stage))),
        modifier = modifier,
        style = style,
        color = color
    )
}
