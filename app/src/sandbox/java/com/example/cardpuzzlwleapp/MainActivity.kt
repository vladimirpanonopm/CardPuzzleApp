package com.example.cardpuzzleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.cardpuzzleapp.ui.theme.CardPuzzleAppTheme

// Это специальная MainActivity ТОЛЬКО для sandbox-версии
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CardPuzzleAppTheme {
                PuzzleSandboxScreen()
            }
        }
    }
}