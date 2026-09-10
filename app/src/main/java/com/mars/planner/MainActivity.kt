package com.mars.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.mars.planner.data.prefs.AppSettings
import com.mars.planner.screens.MarsApp
import com.mars.planner.ui.theme.MarsTheme
import com.mars.planner.ui.theme.appTheme
import com.mars.planner.ui.theme.effects

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val container = (application as MarsApplication).container
        setContent {
            val settings by container.settings.settings.collectAsState(initial = AppSettings())
            MarsTheme(theme = settings.appTheme, intensity = settings.effects) {
                MarsApp()
            }
        }
    }
}
