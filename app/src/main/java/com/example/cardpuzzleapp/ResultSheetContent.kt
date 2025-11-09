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
    // val scrollState = rememberScrollState() // <-- Больше не нужен

    Column(
        // --- ИЗМЕНЕНИЕ: Обновляем padding ---
        modifier = Modifier
            .fillMaxWidth() // <-- Добавлено
            .padding(vertical = 24.dp, horizontal = 16.dp), // <-- Изменено
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- ИЗМЕНЕНИЕ: Блок с переводом ПОЛНОСТЬЮ УДАЛЕН ---
        // Box(
        //     modifier = Modifier
        //         .fillMaxWidth()
        //         .weight(1f, fill = false)
        //         .verticalScroll(scrollState),
        //     contentAlignment = Alignment.CenterStart
        // ) {
        //     Text(
        //         text = snapshot.translation,
        //         style = MaterialTheme.typography.headlineSmall,
        //         textAlign = TextAlign.Start
        //     )
        // }
        // Spacer(modifier = Modifier.height(16.dp))
        // ----------------------------------------------------

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
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