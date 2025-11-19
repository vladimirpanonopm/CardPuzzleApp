package com.example.cardpuzzleapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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

        setContent {
            val navController = rememberNavController()
            val cardViewModel: CardViewModel = hiltViewModel()
            val alefbetViewModel: AlefbetViewModel = hiltViewModel()
            val journalViewModel: JournalViewModel = hiltViewModel()
            val matchingViewModel: MatchingViewModel = hiltViewModel()
            val dictionaryViewModel: DictionaryViewModel = hiltViewModel()

            CardPuzzleAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(
                        navController = navController,
                        cardViewModel = cardViewModel,
                        alefbetViewModel = alefbetViewModel,
                        journalViewModel = journalViewModel,
                        matchingViewModel = matchingViewModel,
                        dictionaryViewModel = dictionaryViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    cardViewModel: CardViewModel,
    alefbetViewModel: AlefbetViewModel,
    journalViewModel: JournalViewModel,
    matchingViewModel: MatchingViewModel,
    dictionaryViewModel: DictionaryViewModel
) {
    val coroutineScope = rememberCoroutineScope()

    // Всегда стартуем с главного меню
    val startDestination = "home"

    LaunchedEffect(Unit) {
        cardViewModel.navigationEvent.collectLatest { event ->
            val currentRoute = navController.currentDestination?.route
            when (event) {
                is NavigationEvent.ShowRoundTrack -> {
                    navController.navigate("round_track/${event.levelId}") {
                        if (currentRoute != null && (currentRoute.startsWith("game") || currentRoute.startsWith("matching_game"))) {
                            popUpTo(currentRoute) { inclusive = true }
                        }
                    }
                }
                is NavigationEvent.ShowRound -> {
                    val taskType = cardViewModel.getTaskTypeForRound(event.roundIndex)
                    val route = if (taskType == TaskType.MATCHING_PAIRS) {
                        "matching_game/${event.levelId}/${event.roundIndex}?uid=${System.currentTimeMillis()}"
                    } else {
                        "game/${event.levelId}/${event.roundIndex}"
                    }
                    navController.navigate(route) {
                        if (currentRoute != null) {
                            if (currentRoute.startsWith("game") && taskType == TaskType.MATCHING_PAIRS) {
                                popUpTo(currentRoute) { inclusive = true }
                            } else if (currentRoute.startsWith("matching_game") && taskType != TaskType.MATCHING_PAIRS) {
                                popUpTo(currentRoute) { inclusive = true }
                            }
                        }
                    }
                }
            }
        }
    }

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

        composable("home") {
            HomeScreen(
                viewModel = cardViewModel,
                onStartLevel = { levelId ->
                    coroutineScope.launch {
                        val isFullyCompleted = cardViewModel.loadLevel(levelId)
                        if (isFullyCompleted) {
                            navController.navigate("round_track/$levelId")
                        }
                    }
                },
                onSettingsClick = { navController.navigate("settings") },
                onAlefbetClick = { navController.navigate("alefbet") }
            )
        }

        composable("alefbet") {
            AlefbetScreen(
                viewModel = alefbetViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
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
            // --- ИСПРАВЛЕНИЕ: Удалена неиспользуемая переменная levelId ---
            // GameScreen получает уровень через ViewModel, который уже загружен
            val roundIndex = backStackEntry.arguments?.getInt("roundIndex") ?: 0

            GameScreen(
                viewModel = cardViewModel,
                routeRoundIndex = roundIndex,
                onHomeClick = {
                    navController.navigate("home") { popUpTo("home") { inclusive = true } }
                },
                onJournalClick = {
                    navController.navigate("journal/${cardViewModel.currentLevelId}?roundIndex=${cardViewModel.currentRoundIndex}")
                },
                onTrackClick = { levelIdNav -> navController.navigate("round_track/$levelIdNav") },
                onSkipClick = { cardViewModel.skipToNextAvailableRound() },
                onDictionaryClick = { navController.navigate("dictionary") }
            )
        }

        composable(
            route = "matching_game/{levelId}/{roundIndex}?uid={uid}",
            arguments = listOf(
                navArgument("levelId") { type = NavType.IntType },
                navArgument("roundIndex") { type = NavType.IntType },
                navArgument("uid") { type = NavType.LongType; defaultValue = 0L }
            ),
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(330)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(330)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(330)) }
        ) { backStackEntry ->
            val levelId = backStackEntry.arguments?.getInt("levelId") ?: 1
            val roundIndex = backStackEntry.arguments?.getInt("roundIndex") ?: 0
            val uid = backStackEntry.arguments?.getLong("uid") ?: 0L

            MatchingGameScreen(
                cardViewModel = cardViewModel,
                viewModel = matchingViewModel,
                routeLevelId = levelId,
                routeRoundIndex = roundIndex,
                routeUid = uid,
                onBackClick = { navController.popBackStack() },
                onJournalClick = {
                    navController.navigate("journal/${matchingViewModel.currentLevelId}?roundIndex=${matchingViewModel.currentRoundIndex}")
                },
                onTrackClick = { navController.navigate("round_track/${matchingViewModel.currentLevelId}") },
                onDictionaryClick = { navController.navigate("dictionary") }
            )
        }

        composable("round_track/{levelId}", arguments = listOf(navArgument("levelId") { type = NavType.IntType })) { backStackEntry ->
            val levelId = backStackEntry.arguments?.getInt("levelId") ?: 1
            RoundTrackScreen(
                levelId = levelId,
                viewModel = cardViewModel,
                onHomeClick = { navController.navigate("home") { popUpTo("home") { inclusive = true } } },
                onResetClick = { coroutineScope.launch { cardViewModel.resetCurrentLevelProgress() } },
                onJournalClick = { navController.navigate("journal/$levelId?roundIndex=-1") },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("journal/{levelId}?roundIndex={roundIndex}", arguments = listOf(
            navArgument("levelId") { type = NavType.IntType },
            navArgument("roundIndex") { type = NavType.IntType; defaultValue = -1 }
        )) { backStackEntry ->
            val levelId = backStackEntry.arguments?.getInt("levelId") ?: 1
            val initialRoundIndex = backStackEntry.arguments?.getInt("roundIndex") ?: -1
            JournalScreen(
                levelId = levelId,
                journalViewModel = journalViewModel,
                onBackClick = { navController.popBackStack() },
                onTrackClick = { navController.navigate("round_track/$levelId") },
                initialRoundIndex = initialRoundIndex
            )
        }

        composable("dictionary") {
            DictionaryScreen(
                viewModel = dictionaryViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}