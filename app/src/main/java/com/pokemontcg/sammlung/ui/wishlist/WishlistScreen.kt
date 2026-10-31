package com.pokemontcg.sammlung.ui.wishlist

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.ui.components.TcgImage
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.WishlistEntity
import com.pokemontcg.sammlung.util.PriceFormatter
import kotlinx.coroutines.launch

enum class WishlistSortOption {
    ADDED_DESC,
    PRICE_ASC,
    TARGET_REACHED_FIRST
}

fun isTargetReached(price: Double?, targetPrice: Double?): Boolean {
    return price != null && targetPrice != null && price > 0.0 && targetPrice > 0.0 && price <= targetPrice
}

fun sortWishlist(
    wishlist: List<WishlistEntity>,
    cardMap: Map<String, CardEntity>,
    option: WishlistSortOption
): List<WishlistEntity> {
    return when (option) {
        WishlistSortOption.ADDED_DESC -> wishlist.sortedByDescending { it.addedAt }
        WishlistSortOption.PRICE_ASC -> wishlist.sortedBy { cardMap[it.cardId]?.marketPrice ?: Double.MAX_VALUE }
        WishlistSortOption.TARGET_REACHED_FIRST -> wishlist.sortedByDescending { isTargetReached(cardMap[it.cardId]?.marketPrice, it.targetPrice) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    wishlist: List<WishlistEntity>,
    cardMap: Map<String, CardEntity>,
    onRemoveWish: (String) -> Unit,
    onUpdateWish: (WishlistEntity) -> Unit = {},
    onBackClick: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var currentSort by remember { mutableStateOf(WishlistSortOption.ADDED_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }

    var editingWishTarget by remember { mutableStateOf<WishlistEntity?>(null) }
    var editingWishNotes by remember { mutableStateOf<WishlistEntity?>(null) }

    val sortedList = remember(wishlist, cardMap, currentSort) {
        sortWishlist(wishlist, cardMap, currentSort)
    }

    if (editingWishTarget != null) {
        val wish = editingWishTarget!!
        var targetText by remember { mutableStateOf(wish.targetPrice?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editingWishTarget = null },
            title = { Text(stringResource(R.string.ui_zielpreis_festlegen_99510b)) },
            text = {
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text(stringResource(R.string.ui_zielpreis_in_leer_kein_ziel_fe22b8)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newPrice = targetText.replace(",", ".").toDoubleOrNull()
                        onUpdateWish(wish.copy(targetPrice = newPrice))
                        editingWishTarget = null
                    }
                ) {
                    Text(stringResource(R.string.ui_speichern_70b73b))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingWishTarget = null }) {
                    Text(stringResource(R.string.ui_abbrechen_07af7c))
                }
            }
        )
    }

    if (editingWishNotes != null) {
        val wish = editingWishNotes!!
        var notesText by remember { mutableStateOf(wish.notes ?: "") }
        AlertDialog(
            onDismissRequest = { editingWishNotes = null },
            title = { Text(stringResource(R.string.ui_notiz_bearbeiten_a47793)) },
            text = {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text(stringResource(R.string.ui_notiz_b6c441)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateWish(wish.copy(notes = notesText))
                        editingWishNotes = null
                    }
                ) {
                    Text(stringResource(R.string.ui_speichern_70b73b))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingWishNotes = null }) {
                    Text(stringResource(R.string.ui_abbrechen_07af7c))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_wishlist_title_count, wishlist.size)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_icon_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.ui_icon_sort))
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_zuletzt_hinzugef_gt_040f67)) },
                                onClick = {
                                    currentSort = WishlistSortOption.ADDED_DESC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_preis_aufsteigend_30e797)) },
                                onClick = {
                                    currentSort = WishlistSortOption.PRICE_ASC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_ziel_erreicht_zuerst_a3e976)) },
                                onClick = {
                                    currentSort = WishlistSortOption.TARGET_REACHED_FIRST
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (wishlist.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.ui_deine_wunschliste_ist_leer_ef1608))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedList, key = { it.cardId }) { wish ->
                        val card = cardMap[wish.cardId]
                        val marketPrice = card?.marketPrice
                        val targetReached = isTargetReached(marketPrice, wish.targetPrice)

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TcgImage(
                                        model = card?.imageUrl,
                                        contentDescription = card?.let { stringResource(R.string.ui_card_image_description, it.name, it.number, it.setName) } ?: wish.cardId,
                                        modifier = Modifier.size(60.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            card?.name ?: wish.cardId,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (card != null) {
                                            Text(stringResource(R.string.ui_card_set_number_compact, card.setName, card.number), style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            PriceFormatter.formatPriceShort(context, marketPrice),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (wish.targetPrice != null) {
                                            Text(
                                                stringResource(R.string.ui_wishlist_target_price, PriceFormatter.formatPriceShort(context, wish.targetPrice)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!wish.notes.isNullOrBlank()) {
                                            Text(stringResource(R.string.ui_wishlist_note, wish.notes), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        val removedMessage = stringResource(R.string.ui_wishlist_removed)
                                        val undoLabel = stringResource(R.string.ui_undo)
                                        IconButton(onClick = {
                                            val removedWish = wish
                                            onRemoveWish(wish.cardId)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = removedMessage,
                                                    actionLabel = undoLabel
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    onUpdateWish(removedWish)
                                                }
                                            }
                                        }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.ui_icon_remove),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AssistChip(
                                            onClick = { editingWishTarget = wish },
                                            label = { Text(stringResource(R.string.ui_zielpreis_848291)) },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        )
                                        AssistChip(
                                            onClick = { editingWishNotes = wish },
                                            label = { Text(stringResource(R.string.ui_notiz_b6c441)) },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        )
                                    }

                                    if (targetReached) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(stringResource(R.string.ui_ziel_erreicht_d2e169), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
