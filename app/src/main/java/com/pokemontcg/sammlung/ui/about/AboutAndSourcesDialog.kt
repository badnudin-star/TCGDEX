package com.pokemontcg.sammlung.ui.about

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutAndSourcesDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_ber_tcg_sammlung_quellen_6d887d)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.ui_app_about_description),
                    style = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider()

                Text(stringResource(R.string.ui_datenquellen_urheberrechte_674a73), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.ui_tcgdex_api_c9ff41), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_aktive_quelle_f_r_mehrsprachige_kart_2e291e), style = MaterialTheme.typography.bodySmall)

                    Text(stringResource(R.string.ui_pok_api_48d84a), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_erg_nzende_quelle_f_r_pok_mon_spezie_282151), style = MaterialTheme.typography.bodySmall)

                    Text(stringResource(R.string.ui_bulbapedia_catalog), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_bulbapedia_license), style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()

                Text(stringResource(R.string.ui_haftungsausschluss_767c5a), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.ui_trademark_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_schlie_en_44424b))
            }
        }
    )
}
