package com.example.cardpuzzleapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LanguageSelectionScreen(
    onLanguageSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp), // Добавляем отступы по бокам
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        supportedLanguages.forEach { lang ->
            Button(
                onClick = { onLanguageSelected(lang.code) },
                // ИЗМЕНЕНИЕ: Кнопка теперь занимает всю доступную ширину
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = lang.displayName)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}