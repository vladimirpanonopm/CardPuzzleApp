package com.example.cardpuzzleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cardpuzzleapp.ui.theme.CardPuzzleAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val progressManager = remember { GameProgressManager(this) }
            var languageCode by remember { mutableStateOf(progressManager.getUserLanguage()) }

            key(languageCode) {
                val context = languageCode?.let { LocaleHelper.wrap(this, it) } ?: this
                CompositionLocalProvider(LocalContext provides context) {
                    CardPuzzleAppTheme {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            AppNavigation(
                                initialLanguage = languageCode,
                                onLanguageChange = { newLangCode ->
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

@Composable
fun AppNavigation(
    initialLanguage: String?,
    onLanguageChange: (String) -> Unit
) {
    val navController = rememberNavController()
    val cardViewModel: CardViewModel = viewModel()
    val context = LocalContext.current

    val startDestination = if (initialLanguage != null) "home" else "language_selection"

    NavHost(navController = navController, startDestination = startDestination) {

        composable("language_selection") {
            LanguageSelectionScreen(
                onLanguageSelected = onLanguageChange
            )
        }

        composable("home") {
            HomeScreen(
                onStartLevel = { levelId ->
                    cardViewModel.loadLevel(context, levelId)
                    navController.navigate("game")
                },
                onShowTrack = { levelId ->
                    navController.navigate("round_track/$levelId")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                // ИСПРАВЛЕНИЕ: Добавлен недостающий параметр
                onAlefbetClick = {
                    navController.navigate("alefbet")
                }
            )
        }

        composable("alefbet") {
            val alefbetViewModel: AlefbetViewModel = viewModel()
            AlefbetScreen(
                viewModel = alefbetViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("settings") {
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

        composable("game") {
            GameScreen(
                viewModel = cardViewModel,
                onHomeClick = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onJournalClick = {
                    navController.navigate("journal/${cardViewModel.currentLevelId}?roundIndex=${cardViewModel.currentRoundIndex}")
                },
                onTrackClick = { levelId ->
                    navController.navigate("round_track/$levelId")
                },
                onSkipClick = {
                    cardViewModel.skipToNextAvailableRound()
                }
            )
        }

        composable(
            route = "round_track/{levelId}",
            arguments = listOf(navArgument("levelId") { type = NavType.IntType })
        ) { backStackEntry ->
            val levelId = backStackEntry.arguments?.getInt("levelId") ?: 1
            RoundTrackScreen(
                levelId = levelId,
                viewModel = cardViewModel,
                onHomeClick = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onResetClick = {
                    cardViewModel.loadLevel(context, levelId)
                    cardViewModel.resetCurrentLevelProgress()
                    navController.navigate("game") {
                        popUpTo("home")
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
            val journalViewModel: JournalViewModel = viewModel()

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