package com.example.cardpuzzleapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// --- ИЗМЕНЕНИЕ: ЭТИ ИМПОРТЫ ОСТАЮТСЯ (для MatchingGame) ---
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
// --- КОНЕЦ ИЗМЕНЕНИЯ ---
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cardpuzzleapp.ui.theme.CardPuzzleAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(AppDebug.TAG, "MainActivity: onCreate")

        setContent {
            val progressManager = remember { GameProgressManager(applicationContext) }
            var languageCode by remember { mutableStateOf(progressManager.getUserLanguage()) }
            Log.d(AppDebug.TAG, "MainActivity: setContent. Initial languageCode: $languageCode")

            val navController = rememberNavController()
            val cardViewModel: CardViewModel = hiltViewModel()
            val alefbetViewModel: AlefbetViewModel = hiltViewModel()
            val journalViewModel: JournalViewModel = hiltViewModel()
            val matchingViewModel: MatchingViewModel = hiltViewModel()

            key(languageCode) {
                Log.d(AppDebug.TAG, "MainActivity: key(languageCode) recomposing. languageCode: $languageCode")

                val wrappedContext = languageCode?.let { LocaleHelper.wrap(this, it) } ?: this

                CompositionLocalProvider(LocalContext provides wrappedContext) {
                    CardPuzzleAppTheme {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                            AppNavigation(
                                navController = navController,
                                cardViewModel = cardViewModel,
                                alefbetViewModel = alefbetViewModel,
                                journalViewModel = journalViewModel,
                                matchingViewModel = matchingViewModel,
                                initialLanguage = languageCode,
                                onLanguageChange = { newLangCode ->
                                    Log.d(AppDebug.TAG, "MainActivity: onLanguageChange lambda triggered with: $newLangCode")
                                    progressManager.saveUserLanguage(newLangCode)
                                    languageCode = newLangCode
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val NAV_DEBUG_TAG = "MATCHING_CARDS_DEBUG"

@Composable
fun AppNavigation(
    navController: NavHostController,
    cardViewModel: CardViewModel,
    alefbetViewModel: AlefbetViewModel,
    journalViewModel: JournalViewModel,
    matchingViewModel: MatchingViewModel,
    initialLanguage: String?,
    onLanguageChange: (String) -> Unit
) {
    Log.d(AppDebug.TAG, "AppNavigation: Composing. initialLanguage: $initialLanguage")

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val startDestination = remember {
        val dest = if (initialLanguage != null) "home" else "language_selection"
        Log.d(AppDebug.TAG, "AppNavigation: 'remember' startDestination calculated: $dest")
        dest
    }

    LaunchedEffect(initialLanguage) {
        Log.d(AppDebug.TAG, "AppNavigation: LaunchedEffect triggered. initialLanguage: $initialLanguage, startDestination: $startDestination")
        if (initialLanguage != null && startDestination == "language_selection") {
            Log.d(AppDebug.TAG, "AppNavigation: LaunchedEffect -> Navigating to 'home'...")
            navController.navigate("home") {
                popUpTo("language_selection") { inclusive = true }
            }
            Log.d(AppDebug.TAG, "AppNavigation: LaunchedEffect -> Navigation command sent.")
        }
    }

    // --- LaunchedEffect для cardViewModel (Исправлен) ---
    LaunchedEffect(Unit) {
        cardViewModel.navigationEvent.collectLatest { event ->
            Log.e(AppDebug.TAG, ">>> AppNavigation RECV CardView EVENT: '$event'. Current dest: ${navController.currentDestination?.route}")
            val currentRoute = navController.currentDestination?.route

            when (event) {
                is NavigationEvent.ShowRoundTrack -> {
                    navController.navigate("round_track/${event.levelId}") {
                        // --- ИСПРАВЛЕНИЕ: Явная проверка на null ---
                        if (currentRoute != null && (currentRoute.startsWith("game") || currentRoute.startsWith("matching_game"))) {
                            popUpTo(currentRoute) { inclusive = true }
                        }
                        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---
                    }
                }

                is NavigationEvent.ShowRound -> {
                    val taskType = cardViewModel.getTaskTypeForRound(event.roundIndex)

                    val route = if (taskType == TaskType.MATCHING_PAIRS) {
                        "matching_game/${event.levelId}/${event.roundIndex}?uid=${System.currentTimeMillis()}"
                    } else {
                        "game/${event.levelId}/${event.roundIndex}"
                    }

                    Log.e(AppDebug.TAG, ">>> AppNavigation NAVIGATING to '$route'")
                    navController.navigate(route) {
                        // --- ИСПРАВЛЕНИЕ: Явная проверка на null ---
                        if (currentRoute != null) {
                            if (currentRoute.startsWith("game") && taskType == TaskType.MATCHING_PAIRS) {
                                popUpTo(currentRoute) { inclusive = true }
                            } else if (currentRoute.startsWith("matching_game") && taskType != TaskType.MATCHING_PAIRS) {
                                popUpTo(currentRoute) { inclusive = true }
                            }
                        }
                        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---
                    }
                }
            }
        }
    }
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    // --- LaunchedEffect для matchingViewModel (Без изменений) ---
    LaunchedEffect(Unit) {
        matchingViewModel.completionEvents.collectLatest { event ->
            when (event) {
                MatchingCompletionEvent.Win -> cardViewModel.proceedToNextRound()
                MatchingCompletionEvent.Track -> navController.navigate("round_track/${matchingViewModel.currentLevelId}")
                MatchingCompletionEvent.Skip -> cardViewModel.skipToNextAvailableRound()
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable("language_selection") {
            Log.d(AppDebug.TAG, "NavHost: Composing 'language_selection'")
            LanguageSelectionScreen(
                onLanguageSelected = { selectedLangCode ->
                    Log.d(AppDebug.TAG, "NavHost: 'language_selection' onLanguageSelected triggered with: $selectedLangCode")
                    onLanguageChange(selectedLangCode)
                }
            )
        }

        composable("home") {
            Log.d(AppDebug.TAG, "NavHost: Composing 'home'")
            HomeScreen(
                viewModel = cardViewModel,
                onStartLevel = { levelId ->
                    Log.d(AppDebug.TAG, "NavHost: 'home' onStartLevel($levelId)")
                    coroutineScope.launch {
                        val isFullyCompleted = cardViewModel.loadLevel(levelId)
                        if (isFullyCompleted) {
                            navController.navigate("round_track/$levelId")
                        }
                    }
                },
                onShowTrack = { levelId ->
                    Log.d(AppDebug.TAG, "NavHost: 'home' onShowTrack($levelId)")
                    coroutineScope.launch {
                        cardViewModel.loadLevel(levelId)
                        navController.navigate("round_track/$levelId")
                    }
                },
                onSettingsClick = {
                    Log.d(AppDebug.TAG, "NavHost: 'home' onSettingsClick")
                    navController.navigate("settings")
                },
                onAlefbetClick = {
                    Log.d(AppDebug.TAG, "NavHost: 'home' onAlefbetClick")
                    navController.navigate("alefbet")
                }
            )
        }

        composable("alefbet") {
            Log.d(AppDebug.TAG, "NavHost: Composing 'alefbet'")
            AlefbetScreen(
                viewModel = alefbetViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("settings") {
            Log.d(AppDebug.TAG, "NavHost: Composing 'settings'")
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onLanguageChange = onLanguageChange,
                onResetProgress = {
                    cardViewModel.resetAllProgress()
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "game/{levelId}/{roundIndex}",
            arguments = listOf(
                navArgument("levelId") { type = NavType.IntType },
                navArgument("roundIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val levelId = backStackEntry.arguments?.getInt("levelId") ?: 1
            val roundIndex = backStackEntry.arguments?.getInt("roundIndex") ?: 0
            Log.d(AppDebug.TAG, "NavHost: Composing 'game/$levelId/$roundIndex'")

            GameScreen(
                viewModel = cardViewModel,
                routeRoundIndex = roundIndex,
                onHomeClick = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onJournalClick = {
                    navController.navigate("journal/${cardViewModel.currentLevelId}?roundIndex=${cardViewModel.currentRoundIndex}")
                },
                onTrackClick = { levelIdNav ->
                    navController.navigate("round_track/$levelIdNav")
                },
                onSkipClick = {
                    cardViewModel.skipToNextAvailableRound()
                }
            )
        }

        composable(
            route = "matching_game/{levelId}/{roundIndex}?uid={uid}",
            arguments = listOf(
                navArgument("levelId") { type = NavType.IntType },
                navArgument("roundIndex") { type = NavType.IntType },
                navArgument("uid") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            ),
            // --- ИЗМЕНЕНИЕ СКОРОСТИ АНИМАЦИИ ---
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(330) // <-- БЫЛО 300
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(330) // <-- БЫЛО 300
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(330) // <-- БЫЛО 300
                )
            }
            // --- КОНЕЦ ИЗМЕНЕНИЯ ---
        ) { backStackEntry ->
            val levelId = backStackEntry.arguments?.getInt("levelId") ?: 1
            val roundIndex = backStackEntry.arguments?.getInt("roundIndex") ?: 0
            val uid = backStackEntry.arguments?.getLong("uid") ?: 0L
            Log.d(AppDebug.TAG, "NavHost: Composing 'matching_game/$levelId/$roundIndex' (UID: $uid)")

            MatchingGameScreen(
                cardViewModel = cardViewModel,
                viewModel = matchingViewModel,
                routeLevelId = levelId,
                routeRoundIndex = roundIndex,
                routeUid = uid,
                onBackClick = {
                    navController.popBackStack()
                },
                onJournalClick = {
                    navController.navigate("journal/${matchingViewModel.currentLevelId}?roundIndex=${matchingViewModel.currentRoundIndex}")
                },
                onTrackClick = {
                    navController.navigate("round_track/${matchingViewModel.currentLevelId}")
                }
            )
        }


        composable(
            route = "round_track/{levelId}",
            arguments = listOf(navArgument("levelId") { type = NavType.IntType })
        ) { backStackEntry ->
            val levelId = backStackEntry.arguments?.getInt("levelId") ?: 1
            Log.d(AppDebug.TAG, "NavHost: Composing 'round_track/$levelId'")
            RoundTrackScreen(
                levelId = levelId,
                viewModel = cardViewModel,
                onHomeClick = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onResetClick = {
                    coroutineScope.launch {
                        cardViewModel.resetCurrentLevelProgress()
                    }
                },
                onJournalClick = {
                    navController.navigate("journal/$levelId?roundIndex=-1")
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "journal/{levelId}?roundIndex={roundIndex}",
            arguments = listOf(
                navArgument("levelId") { type = NavType.IntType },
                navArgument("roundIndex") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val levelId = backStackEntry.arguments?.getInt("levelId") ?: 1
            val initialRoundIndex = backStackEntry.arguments?.getInt("roundIndex") ?: -1
            Log.d(AppDebug.TAG, "NavHost: Composing 'journal/$levelId/$initialRoundIndex'")

            JournalScreen(
                levelId = levelId,
                journalViewModel = journalViewModel,
                onBackClick = { navController.popBackStack() },
                onTrackClick = {
                    navController.navigate("round_track/$levelId")
                },
                initialRoundIndex = initialRoundIndex
            )
        }
    }
}