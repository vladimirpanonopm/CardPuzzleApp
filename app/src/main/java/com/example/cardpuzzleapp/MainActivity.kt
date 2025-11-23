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
        Log.d(AppDebug.TAG, "MainActivity: onCreate")

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
    val startDestination = "home"

    LaunchedEffect(Unit) {
        cardViewModel.navigationEvent.collectLatest { event ->
            val currentRoute = navController.currentDestination?.route
            when (event) {
                is NavigationEvent.ShowRoundTrack -> {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
                is NavigationEvent.ShowRound -> {
                    val route = "game/${event.levelId}/${event.roundIndex}"
                    navController.navigate(route) {
                        if (currentRoute != null && currentRoute.startsWith("game")) {
                            popUpTo(currentRoute) { inclusive = true }
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
            LevelMapScreen(
                onAlefbetClick = { navController.navigate("alefbet") },
                onSettingsClick = { navController.navigate("settings") },
                onJournalClick = { navController.navigate("journal") },

                onRoundClick = { levelId, roundIndex ->
                    coroutineScope.launch {
                        cardViewModel.ensureLevelLoaded(levelId)
                        val taskType = cardViewModel.getTaskTypeForRound(roundIndex)

                        if (taskType != TaskType.UNKNOWN) {
                            navController.navigate("game/$levelId/$roundIndex")
                        } else {
                            Log.e(AppDebug.TAG, "Nav: Error - Unknown task type")
                        }
                    }
                }
                // onRewardClick УДАЛЕН
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
            val roundIndex = backStackEntry.arguments?.getInt("roundIndex") ?: 0

            GameScreen(
                viewModel = cardViewModel,
                routeRoundIndex = roundIndex,
                onHomeClick = {
                    navController.navigate("home") { popUpTo("home") { inclusive = true } }
                },
                onJournalClick = { navController.navigate("journal") },
                onTrackClick = { levelIdNav -> navController.navigate("round_track/$levelIdNav") },
                onSkipClick = { cardViewModel.skipToNextAvailableRound() },
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
                onJournalClick = { navController.navigate("journal") },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("journal") {
            JournalScreen(
                journalViewModel = journalViewModel,
                onBackClick = { navController.popBackStack() }
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