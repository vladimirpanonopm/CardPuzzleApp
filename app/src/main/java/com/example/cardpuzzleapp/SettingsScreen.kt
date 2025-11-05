package com.example.cardpuzzleapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onResetProgress: () -> Unit
) {
    val context = LocalContext.current
    val progressManager = remember { GameProgressManager(context) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.dialog_reset_all_title)) },
            text = { Text(stringResource(R.string.dialog_reset_all_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetProgress()
                    }
                ) {
                    Text(stringResource(R.string.dialog_reset_all_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.dialog_reset_all_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings_title),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            LanguageSelector(
                currentLanguageCode = progressManager.getUserLanguage() ?: "ru",
                onLanguageSelected = onLanguageChange
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.settings_reset_all_progress))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector(
    currentLanguageCode: String,
    onLanguageSelected: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val currentLanguage = remember(currentLanguageCode) {
        supportedLanguages.find { it.code == currentLanguageCode }
    }

    Column {
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        ExposedDropdownMenuBox(
            expanded = isExpanded,
            onExpandedChange = { isExpanded = it }
        ) {
            OutlinedTextField(
                value = currentLanguage?.displayName ?: "",
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false }
            ) {
                supportedLanguages.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.displayName) },
                        onClick = {
                            onLanguageSelected(language.code)
                            isExpanded = false
                        }
                    )
                }
            }
        }
    }
}