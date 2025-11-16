package com.example.cardpuzzleapp

import androidx.compose.foundation.clickable // <-- Импорт
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
// --- ИЗМЕНЕНИЕ: 'hiltViewModel' УДАЛЕН ---
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import kotlin.math.roundToInt


/**
 * Новый, полноэкранный режим "Глобального Словаря".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    viewModel: DictionaryViewModel,
    onBackClick: () -> Unit
    // --- ИЗМЕНЕНИЕ: TtsPlayer УДАЛЕН ---
) {
    // "forceRefresh = true" гарантирует, что словарь
    // всегда будет актуальным, включая только что пройденные раунды.
    LaunchedEffect(Unit) {
        viewModel.loadDictionary(forceRefresh = true)
    }

    Scaffold(
        topBar = {
            DictionaryTopBar(
                searchText = viewModel.searchText,
                onSearchTextChanged = { viewModel.onSearchTextChanged(it) },
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->

        val dictionary = viewModel.filteredDictionary
        val isLoading = viewModel.isLoading

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (dictionary.isEmpty()) {
                Text(
                    text = if (viewModel.searchText.isBlank()) {
                        stringResource(R.string.dictionary_empty)
                    } else {
                        stringResource(R.string.dictionary_no_results)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                // --- ИЗМЕНЕНИЕ: Передаем лямбду из ViewModel ---
                DictionaryContent(
                    dictionary = dictionary,
                    onHebrewClick = { viewModel.onHebrewWordClicked(it) }
                )
            }
        }
    }
}

/**
 * Верхняя панель с кнопкой "Назад" и Поиском.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictionaryTopBar(
    searchText: String,
    onSearchTextChanged: (String) -> Unit,
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = {
            // Поле поиска
            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChanged,
                placeholder = { Text(stringResource(R.string.dictionary_search_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp), // Отступ от 'actions'
                singleLine = true,
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { onSearchTextChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                        }
                    }
                }
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.button_back)
                )
            }
        }
    )
}

/**
 * Вертикальный скроллинг (LazyColumn) для пар.
 */
@Composable
private fun DictionaryContent(
    dictionary: List<List<String>>,
    onHebrewClick: (String) -> Unit // <-- ИЗМЕНЕНИЕ
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(dictionary, key = { it.getOrNull(0) ?: "" }) { pair ->
            DictionaryRow(
                hebrew = pair.getOrNull(0) ?: "??",
                translation = pair.getOrNull(1) ?: "??",
                onHebrewClick = onHebrewClick // <-- Передаем лямбду
            )
        }
    }
}

/**
 * Одна строка в Словаре.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun DictionaryRow(
    hebrew: String,
    translation: String,
    onHebrewClick: (String) -> Unit // <-- ИЗМЕНЕНИЕ
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        val styleConfig = CardStyles.getStyle(FontStyle.REGULAR)

        // --- Карточка 1: Перевод (слева) ---
        Card(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = translation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                fontSize = 18.sp,
                textAlign = TextAlign.Start
            )
        }

        // --- Карточка 2: Иврит (справа) + TTS ---
        Card(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            // --- ИЗМЕНЕНИЕ: Вызываем лямбду ---
            onClick = { onHebrewClick(hebrew) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            val hebrewTextStyle = TextStyle(
                fontFamily = FontFamily(Font(R.font.noto_sans_hebrew_variable, variationSettings = FontVariation.Settings(
                    FontVariation.weight(styleConfig.fontWeight.roundToInt()),
                    FontVariation.width(styleConfig.fontWidth)
                ))),
                textAlign = TextAlign.End,
                textDirection = TextDirection.Rtl,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = hebrew,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = hebrewTextStyle
            )
        }
    }
}