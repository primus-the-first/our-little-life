package com.ourlittlelife.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ourlittlelife.game.day.DayScreen
import com.ourlittlelife.game.ui.theme.OurLittleLifeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OurLittleLifeTheme {
                DayScreen()
            }
        }
    }
}
