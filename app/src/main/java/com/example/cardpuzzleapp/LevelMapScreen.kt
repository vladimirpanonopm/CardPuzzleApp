package com.example.cardpuzzleapp

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.cardpuzzleapp.ui.theme.StickyNoteText
import com.example.cardpuzzleapp.ui.theme.StickyNoteYellow

private const val TAG = "MAP_DEBUG"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LevelMapScreen(
    viewModel: LevelMapViewModel = hiltViewModel(),
    onAlefbetClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onJournalClick: () -> Unit,
    onRoundClick: (Int, Int) -> Unit
) {
    LaunchedEffect(Unit) {
        Log.d(TAG, "LevelMapScreen: Loading map data...")
        viewModel.loadMap()
    }

    val levels = viewModel.levels
    val isLoading = viewModel.isLoading

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Карта занятий") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onJournalClick,
                containerColor = StickyNoteYellow,
                contentColor = StickyNoteText
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = stringResource(R.string.journal_title)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        AlefbetHeader(onClick = onAlefbetClick)
                    }

                    items(levels) { level ->
                        ExpandableLevelSection(
                            level = level,
                            onRoundClick = { lid, rid ->
                                onRoundClick(lid, rid)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlefbetHeader(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "א",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 16.dp)
            )
            Text(
                text = stringResource(R.string.alefbet_level_button_short),
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

// Хелпер для проверки, пройден ли раунд (любой успешный статус)
private fun isCompleted(status: RoundStatus): Boolean {
    return status == RoundStatus.PERFECT || status == RoundStatus.GOOD || status == RoundStatus.PASSED
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpandableLevelSection(
    level: LevelMapItem,
    onRoundClick: (Int, Int) -> Unit
) {
    val isLevelActive = !level.isLocked
    var isExpanded by rememberSaveable { mutableStateOf(isLevelActive) }

    // --- ИСПРАВЛЕНИЕ: Обновленная логика цветов заголовка ---
    val headerColor = when {
        level.isLocked -> Color.LightGray
        // Если все раунды имеют любой из статусов "пройдено"
        level.nodes.all { isCompleted(it.status) } -> Color(0xFF4CAF50)
        else -> Color(0xFFFF9800)
    }

    val contentColor = Color.White

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 20.dp, horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (level.isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = contentColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Text(
                        text = level.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = contentColor
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // --- ИСПРАВЛЕНИЕ: Считаем любые пройденные ---
                    val completedCount = level.nodes.count { isCompleted(it.status) }
                    val totalCount = level.nodes.size
                    val progressText = if (level.isLocked) "Закрыто" else "Пройдено: $completedCount из $totalCount"

                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        maxItemsInEachRow = 4
                    ) {
                        level.nodes.forEach { node ->
                            RoundNodeItem(node = node, onClick = {
                                if (node.status != RoundStatus.LOCKED) {
                                    onRoundClick(node.levelId, node.roundIndex)
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoundNodeItem(
    node: RoundNode,
    onClick: () -> Unit
) {
    // Активный кружок чуть больше
    val size = if (node.status == RoundStatus.ACTIVE) 70.dp else 60.dp
    val fontSize = if (node.status == RoundStatus.ACTIVE) 24.sp else 20.sp

    val backgroundColor = when (node.status) {
        RoundStatus.PERFECT -> Color(0xFFFF9800)   // Оранжевый (Золото)
        RoundStatus.GOOD -> Color(0xFF4CAF50)      // Зеленый
        RoundStatus.PASSED -> Color(0xFFAED581)    // Салатовый
        RoundStatus.ACTIVE -> Color(0xFFFFEB3B)    // Желтый (Текущий)
        RoundStatus.LOCKED -> Color.LightGray
    }

    val contentColor = when (node.status) {
        RoundStatus.PERFECT, RoundStatus.GOOD -> Color.White
        RoundStatus.ACTIVE, RoundStatus.PASSED -> StickyNoteText
        RoundStatus.LOCKED -> Color.White
    }

    val borderStroke = if (node.status == RoundStatus.ACTIVE) {
        BorderStroke(3.dp, Color(0xFFFBC02D))
    } else null

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(borderStroke ?: BorderStroke(0.dp, Color.Transparent), CircleShape)
            .clickable(enabled = node.status != RoundStatus.LOCKED) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (node.status == RoundStatus.LOCKED) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = node.label,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}