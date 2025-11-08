package com.example.cardpuzzleapp

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onStartLevel: (levelId: Int) -> Unit,
    onShowTrack: (levelId: Int) -> Unit,
    onSettingsClick: () -> Unit,
    onAlefbetClick: () -> Unit
) {
    Log.d(AppDebug.TAG, "HomeScreen: Composing")
    val context = LocalContext.current
    val progressManager = remember {
        Log.d(AppDebug.TAG, "HomeScreen: Creating GameProgressManager")
        GameProgressManager(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_select_level),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = onAlefbetClick,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp)
            ) {
                // ИЗМЕНЕНИЕ: Используем новую короткую надпись
                Text(text = stringResource(R.string.alefbet_level_button_short), fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                maxItemsInEachRow = 3
            ) {
                Log.d(AppDebug.TAG, "HomeScreen: Calling LevelRepository.getLevelCount...")
                // --- ЭТО КРИТИЧЕСКАЯ ТОЧКА ---
                // Если крэш происходит, он будет здесь, т.к. 'context' может быть невалидным
                val levelCount = LevelRepository.getLevelCount(context)
                Log.d(AppDebug.TAG, "HomeScreen: LevelRepository.getLevelCount returned: $levelCount")

                for (levelId in 1..levelCount) {
                    Button(
                        onClick = {
                            Log.d(AppDebug.TAG, "HomeScreen: Level button $levelId clicked")
                            val levelData = LevelRepository.getLevelData(context, levelId)
                            val completedRounds = progressManager.getCompletedRounds(levelId)

                            if (levelData != null && completedRounds.size >= levelData.size) {
                                onShowTrack(levelId)
                            } else {
                                onStartLevel(levelId)
                            }
                        },
                        modifier = Modifier.sizeIn(minWidth = 80.dp, minHeight = 80.dp)
                    ) {
                        Text(text = "$levelId", fontSize = 24.sp)
                    }
                }
            }
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}