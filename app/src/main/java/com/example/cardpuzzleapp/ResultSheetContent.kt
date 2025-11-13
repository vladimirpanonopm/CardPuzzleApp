package com.example.cardpuzzleapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ResultSheetContent(
    snapshot: RoundResultSnapshot,
    onContinueClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onTrackClick: () -> Unit
) {
    // --- ИЗМЕНЕНИЕ: Оборачиваем в Column ---
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally // <-- Выравниваем текст по центру
    ) {

        // --- ИЗМЕНЕНИЕ: Показываем русский текст, если он есть ---
        snapshot.translationText?.let { translation ->
            if (translation.isNotBlank()) {
                Text(
                    text = translation,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Start, // <-- ВЫРАВНИВАНИЕ ПО ЛЕВОМУ КРАЮ
                    modifier = Modifier
                        .fillMaxWidth() // <-- ЗАПОЛНЯЕМ ШИРИНУ
                        .padding(bottom = 24.dp)
                )
            }
        }
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        // Существующий Row с кнопками
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onTrackClick) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = stringResource(R.string.round_track_title, snapshot.levelId),
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onRepeatClick) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.button_repeat_round),
                    modifier = Modifier.size(32.dp)
                )
            }

            Button(
                onClick = onContinueClick,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(text = stringResource(R.string.button_continue), fontSize = 20.sp)
            }
        }
    }
}