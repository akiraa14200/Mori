package com.rykocollection.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.rykocollection.app.ui.RykoCollectionApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(12, 13, 16)
        window.navigationBarColor = android.graphics.Color.rgb(12, 13, 16)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFE8C547),
                    secondary = Color(0xFFD7B94A),
                    background = Color(0xFF0C0D10),
                    surface = Color(0xFF17191F),
                    onPrimary = Color(0xFF171717),
                    onBackground = Color(0xFFF4F1E8),
                    onSurface = Color(0xFFF4F1E8)
                )
            ) {
                RykoCollectionApp()
            }
        }
    }
}
