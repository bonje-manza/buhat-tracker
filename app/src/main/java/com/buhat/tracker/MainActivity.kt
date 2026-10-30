package com.buhat.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.buhat.tracker.ui.BuhatApp
import com.buhat.tracker.ui.theme.BuhatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuhatTheme {
                BuhatApp()
            }
        }
    }
}
