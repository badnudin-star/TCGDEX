package com.pokemontcg.sammlung

import androidx.compose.ui.res.stringResource

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.entities.SetEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.local.user.UserMigrationHelper
import com.pokemontcg.sammlung.data.local.user.WishlistEntity
import com.pokemontcg.sammlung.ui.about.AboutAndSourcesDialog
import com.pokemontcg.sammlung.ui.calendar.ReleaseCalendarScreen
import com.pokemontcg.sammlung.ui.cards.CardScannerScreen
import com.pokemontcg.sammlung.ui.cards.CardMatchCandidate
import com.pokemontcg.sammlung.ui.cards.isCurrentScannerRequest
import com.pokemontcg.sammlung.ui.cards.rankCardMatches
import com.pokemontcg.sammlung.ui.cards.resolveScannerMatches
import com.pokemontcg.sammlung.ui.detail.PokemonDetailScreen
import com.pokemontcg.sammlung.ui.detail.PokemonDetailViewModel
import com.pokemontcg.sammlung.ui.diagnostics.DiagnosticsScreen
import com.pokemontcg.sammlung.ui.main.MainScreen
import com.pokemontcg.sammlung.ui.main.MainViewModel
import com.pokemontcg.sammlung.ui.sets.SetProgressScreen
import com.pokemontcg.sammlung.ui.settings.SettingsScreen
import com.pokemontcg.sammlung.ui.settings.SettingsViewModel
import com.pokemontcg.sammlung.ui.theme.TCGSammlungTheme
import com.pokemontcg.sammlung.ui.wishlist.WishlistScreen
import com.pokemontcg.sammlung.worker.UpdateScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val appContainer = (application as TCGApp).container
        val catalogRepo = appContainer.catalogRepository
        val collectionRepo = appContainer.collectionRepository
        val priceRepo = appContainer.priceRepository
        val syncRepo = appContainer.syncRepository
        val prefRepo = appContainer.preferencesRepository
        val dataCheckRepo = appContainer.dataCheckRepository

        setContent {
            TCGSammlungTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val scope = rememberCoroutineScope()

                    var wishlist by remember { mutableStateOf<List<WishlistEntity>>(emptyList()) }
                    var userCards by remember { mutableStateOf<List<UserCardEntityV2>>(emptyList()) }
                    var cardMap by remember { mutableStateOf<Map<String, CardEntity>>(emptyMap()) }
                    var allSets by remember { mutableStateOf<List<SetEntity>>(emptyList()) }
                    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
                    var migrationAttempt by remember { mutableIntStateOf(0) }
                    var migrationState by remember { mutableStateOf<MigrationUiState>(MigrationUiState.Running) }
                    var migrationError by remember { mutableStateOf("") }
                    var scannedCardMatches by remember { mutableStateOf<List<CardEntity>>(emptyList()) }
                    var showScanResults by remember { mutableStateOf(false) }
                    var selectedScannedCard by remember { mutableStateOf<CardEntity?>(null) }
                    var scanLookupFailed by remember { mutableStateOf(false) }
                    // A newer OCR event invalidates any slower lookup started by an earlier frame.
                    var scannerRequestSequence by remember { mutableLongStateOf(0L) }
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route

                    // Scanner dialogs belong only to the scanner destination; never leave them
                    // visible over another screen after back navigation.
                    LaunchedEffect(currentBackStackEntry?.id) {
                        if (currentRoute != "scanner") {
                            scannerRequestSequence += 1L
                            scannedCardMatches = emptyList()
                            showScanResults = false
                            selectedScannedCard = null
                            scanLookupFailed = false
                        }
                    }

                    val legacyExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("text/csv")
                    ) { uri ->
                        if (uri != null) {
                            scope.launch {
                                runCatching { UserMigrationHelper.exportLegacyCsv(applicationContext) }
                                    .onSuccess { csv ->
                                        runCatching {
                                            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
                                                ?: error("Datei konnte nicht geöffnet werden")
                                        }.onSuccess {
                                            Toast.makeText(applicationContext, getString(R.string.ui_legacy_export_created), Toast.LENGTH_LONG).show()
                                        }.onFailure { error ->
                                            Toast.makeText(applicationContext, getString(R.string.ui_export_failed, error.message.orEmpty()), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(applicationContext, getString(R.string.ui_legacy_export_failed, error.message.orEmpty()), Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                    }

                    LaunchedEffect(migrationAttempt) {
                        migrationState = MigrationUiState.Running
                        when (val result = UserMigrationHelper.migrateLegacyUserCardsIfNeeded(
                            applicationContext, appContainer.userDatabase, appContainer.appDatabase
                        )) {
                            is UserMigrationHelper.MigrationResult.Error -> {
                                migrationError = result.message
                                migrationState = MigrationUiState.Failed
                            }
                            else -> migrationState = MigrationUiState.Ready
                        }
                    }

                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(prefRepo, catalogRepo, applicationContext)
                    )

                    val mainViewModel: MainViewModel = viewModel(
                        factory = MainViewModel.Factory(catalogRepo, collectionRepo, priceRepo, syncRepo, prefRepo)
                    )

                    when (migrationState) {
                        MigrationUiState.Running -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        MigrationUiState.Failed -> AlertDialog(
                            onDismissRequest = {},
                            title = { Text(stringResource(R.string.ui_sammlung_konnte_nicht_bernommen_werd_74969d)) },
                            text = { Text(stringResource(R.string.ui_migration_blocked_error, migrationError)) },
                            confirmButton = {
                                Button(onClick = { migrationAttempt++ }) { Text(stringResource(R.string.ui_erneut_versuchen_3234b7)) }
                            },
                            dismissButton = {
                                Button(onClick = { legacyExportLauncher.launch("tcg-legacy-export.csv") }) { Text(stringResource(R.string.ui_legacy_export_3c790d)) }
                            }
                        )
                        MigrationUiState.Ready -> {
                    if (showAboutDialog) {
                        AboutAndSourcesDialog(onDismiss = { showAboutDialog = false })
                    }

                    LaunchedEffect(Unit) {
                        val wifi = prefRepo.wifiOnlyFlow.first()
                        val hour = prefRepo.scheduledUpdateHourFlow.first()
                        UpdateScheduler.schedulePeriodicSync(applicationContext, wifi, hour)
                    }

                    LaunchedEffect(Unit) {
                        collectionRepo.getAllUserCards().collect { list ->
                            userCards = list
                        }
                    }

                    LaunchedEffect(Unit) {
                        collectionRepo.getWishlist().collect { list ->
                            wishlist = list
                            val ids = list.map { it.cardId }
                            if (ids.isNotEmpty()) {
                                val cards = catalogRepo.getCardsByIds(ids)
                                cardMap = cards.associateBy { it.id }
                            }
                        }
                    }

                    LaunchedEffect(Unit) {
                        catalogRepo.getAllSets().collect { sets ->
                            allSets = sets
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "main"
                    ) {
                        composable("main") {
                            MainScreen(
                                viewModel = mainViewModel,
                                onNavigateToPokemonDetail = { dexId ->
                                    navController.navigate("pokemon/$dexId")
                                },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToWishlist = { navController.navigate("wishlist") },
                                onNavigateToDiagnostics = { navController.navigate("diagnose") },
                                onNavigateToCalendar = { navController.navigate("calendar") },
                                onNavigateToScanner = { navController.navigate("scanner") },
                                onNavigateToSets = { navController.navigate("sets") }
                            )
                        }

                        composable(
                            route = "pokemon/{dexId}",
                            arguments = listOf(navArgument("dexId") { type = NavType.IntType })
                        ) { _ ->
                            val pokemonDetailViewModel: PokemonDetailViewModel = viewModel(
                                factory = PokemonDetailViewModel.Factory(catalogRepo, collectionRepo, priceRepo)
                            )
                            val pokemon by pokemonDetailViewModel.pokemon.collectAsState()
                            val cards by pokemonDetailViewModel.cards.collectAsState()
                            val uCards by pokemonDetailViewModel.userCards.collectAsState()
                            val totalPrice by pokemonDetailViewModel.totalPrice.collectAsState()

                            if (pokemon != null) {
                                PokemonDetailScreen(
                                    pokemon = pokemon!!,
                                    cards = cards,
                                    userCards = uCards,
                                    totalPrice = totalPrice,
                                    onBackClick = { navController.popBackStack() },
                                    onSelectPrimary = { card -> pokemonDetailViewModel.setPrimaryCard(card) },
                                    onRemovePrimary = { pokemonDetailViewModel.removePrimaryCard() },
                                    onAddSecondary = { card, category ->
                                        pokemonDetailViewModel.addSecondaryCard(card, category)
                                    },
                                    onRemoveSecondary = { cardId -> pokemonDetailViewModel.removeSecondaryCard(cardId) },
                                    onClassificationChange = { _ -> }
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        composable("settings") {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                collectionRepo = collectionRepo,
                                catalogRepo = catalogRepo,
                                onNavigateToDiagnostics = { navController.navigate("diagnose") },
                                onTriggerSync = { mainViewModel.triggerSync() },
                                onShowAbout = { showAboutDialog = true },
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable("diagnose") {
                            DiagnosticsScreen(
                                dataCheckRepository = dataCheckRepo,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable("wishlist") {
                            WishlistScreen(
                                wishlist = wishlist,
                                cardMap = cardMap,
                                onRemoveWish = { cardId ->
                                    scope.launch {
                                        collectionRepo.removeFromWishlist(cardId)
                                    }
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable("calendar") {
                            ReleaseCalendarScreen(
                                sets = allSets,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable("scanner") {
                            CardScannerScreen(
                                onCardDetected = { detectedNumber, rawOcrText ->
                                    // Bind this asynchronous lookup to the exact scanner entry that
                                    // started it, so a late result cannot reopen a dialog later.
                                    val scannerEntryId = navController.currentBackStackEntry?.id
                                    scannerRequestSequence += 1L
                                    val requestedSequence = scannerRequestSequence
                                    // Hide previous results while the latest scan is being resolved.
                                    showScanResults = false
                                    selectedScannedCard = null
                                    scope.launch {
                                        val matches = runCatching {
                                            resolveScannerMatches(
                                                detectedNumber = detectedNumber,
                                                rawOcrText = rawOcrText,
                                                lookupByNumber = catalogRepo::getCardsByNumber,
                                                lookupByOcrText = catalogRepo::getCardsByOcrText
                                            )
                                        }.getOrElse { emptyList() }
                                        if (!isCurrentScannerRequest(
                                                currentRoute = navController.currentDestination?.route,
                                                currentEntryId = navController.currentBackStackEntry?.id,
                                                requestedEntryId = scannerEntryId,
                                                currentRequestSequence = scannerRequestSequence,
                                                requestedRequestSequence = requestedSequence
                                            )
                                        ) {
                                            return@launch
                                        }
                                        val rankedIds = rankCardMatches(
                                            candidates = matches.map { card ->
                                                CardMatchCandidate(card.id, card.name, card.setName, card.number)
                                            },
                                            detectedNumber = detectedNumber,
                                            rawOcrText = rawOcrText
                                        ).map { it.id }
                                        val matchesById = matches.associateBy { it.id }
                                        scannedCardMatches = rankedIds.mapNotNull(matchesById::get)
                                        scanLookupFailed = matches.isEmpty()
                                        showScanResults = true
                                    }
                                },
                                onDismiss = { navController.popBackStack() }
                            )
                        }

                        composable("sets") {
                            SetProgressScreen(
                                sets = allSets,
                                userCards = userCards,
                                cardsMap = cardMap,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }

                    if (showScanResults) {
                        AlertDialog(
                            onDismissRequest = { showScanResults = false },
                            title = { Text(stringResource(R.string.ui_scanner_matches_title)) },
                            text = {
                                if (scanLookupFailed) {
                                    Text(stringResource(R.string.ui_scanner_no_match))
                                } else {
                                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                                        items(scannedCardMatches, key = { it.id }) { card ->
                                            androidx.compose.material3.TextButton(
                                                onClick = {
                                                    selectedScannedCard = card
                                                    showScanResults = false
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                androidx.compose.foundation.layout.Column {
                                                    Text(card.name, style = MaterialTheme.typography.bodyLarge)
                                                    Text(stringResource(R.string.ui_scanner_card_candidate, card.setName, card.number, card.language.uppercase()), style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = { showScanResults = false }) {
                                    Text(stringResource(R.string.ui_close))
                                }
                            }
                        )
                    }

                    selectedScannedCard?.let { card ->
                        AlertDialog(
                            onDismissRequest = { selectedScannedCard = null },
                            title = { Text(stringResource(R.string.ui_scanner_confirm_title)) },
                            text = { Text(stringResource(R.string.ui_scanner_confirm_text, card.name, card.setName, card.number)) },
                            confirmButton = {
                                Button(onClick = {
                                    scope.launch {
                                        val dexIds = (
                                            listOfNotNull(card.nationalPokedexNumber) +
                                                card.nationalPokedexNumbers.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
                                            ).filter { it > 0 }.distinct()
                                        if (dexIds.size > 1) {
                                            Toast.makeText(applicationContext, getString(R.string.ui_scanner_multiple_pokemon), Toast.LENGTH_LONG).show()
                                            selectedScannedCard = null
                                            return@launch
                                        }
                                        val pokemon = dexIds.singleOrNull()?.let { catalogRepo.getPokemonByDexId(it) }
                                        if (pokemon == null) {
                                            Toast.makeText(applicationContext, getString(R.string.ui_scanner_pokemon_missing), Toast.LENGTH_LONG).show()
                                        } else {
                                            val alreadyCollected = collectionRepo.getAllUserCardsOnce().any {
                                                it.cardId == card.id && (it.pokemonId == pokemon.id || it.pokemonDexId == pokemon.dexId)
                                            }
                                            if (alreadyCollected) {
                                                Toast.makeText(applicationContext, getString(R.string.ui_scanner_already_collected), Toast.LENGTH_LONG).show()
                                            } else {
                                                collectionRepo.addUserCard(
                                                    UserCardEntityV2(
                                                        cardId = card.id,
                                                        pokemonId = pokemon.id,
                                                        pokemonDexId = pokemon.dexId,
                                                        language = card.language,
                                                        quantity = 1,
                                                        category = pokemon.cardType.name,
                                                        isPrimary = false
                                                    )
                                                )
                                                Toast.makeText(applicationContext, getString(R.string.ui_scanner_added, card.name), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        selectedScannedCard = null
                                    }
                                }) { Text(stringResource(R.string.ui_scanner_add_to_collection)) }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { selectedScannedCard = null }) {
                                    Text(stringResource(R.string.ui_cancel))
                                }
                            }
                        )
                    }
                        }
                    }
                }
            }
        }
    }

    private enum class MigrationUiState { Running, Ready, Failed }
}
