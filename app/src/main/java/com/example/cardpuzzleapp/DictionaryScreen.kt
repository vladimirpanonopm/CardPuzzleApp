package com.example.cardpuzzleapp

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
import androidx.compose.runtime.CompositionLocalProvider // Добавлен импорт
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection // Добавлен импорт
import androidx.compose.ui.unit.LayoutDirection // Добавлен импорт
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
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    viewModel: DictionaryViewModel,
    onBackClick: () -> Unit
) {
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
                DictionaryContent(
                    dictionary = dictionary,
                    onHebrewClick = { viewModel.onHebrewWordClicked(it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictionaryTopBar(
    searchText: String,
    onSearchTextChanged: (String) -> Unit,
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChanged,
                placeholder = { Text(stringResource(R.string.dictionary_search_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
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

@Composable
private fun DictionaryContent(
    dictionary: List<List<String>>,
    onHebrewClick: (String) -> Unit
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
                onHebrewClick = onHebrewClick
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun DictionaryRow(
    hebrew: String,
    translation: String,
    onHebrewClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                    .padding(horizontal = 8.dp, vertical = 8.dp), // Малый отступ
                fontSize = 18.sp,
                textAlign = TextAlign.Start
            )
        }

        // --- Карточка 2: Иврит (справа) ---
        // Принудительно задаем RTL контекст для этой карточки
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                onClick = { onHebrewClick(hebrew) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                val hebrewTextStyle = TextStyle(
                    fontFamily = FontFamily(Font(R.font.noto_sans_hebrew_variable, variationSettings = FontVariation.Settings(
                        FontVariation.weight(styleConfig.fontWeight.roundToInt()),
                        FontVariation.width(styleConfig.fontWidth)
                    ))),
                    // Даже внутри RTL контейнера явно указываем Right для надежности
                    textAlign = TextAlign.Right,
                    fontSize = 26.sp, // УВЕЛИЧЕННЫЙ ШРИФТ
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = hebrew,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp), // МИНИМАЛЬНЫЙ ОТСТУП
                    style = hebrewTextStyle
                )
            }
        }
    }
}