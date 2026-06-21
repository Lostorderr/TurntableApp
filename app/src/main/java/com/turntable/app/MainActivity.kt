package com.turntable.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.turntable.app.ui.TurntableApp
import com.turntable.app.ui.theme.TurntableTheme
import com.turntable.app.viewmodel.TurntableViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Turntable", "MainActivity onCreate start")
        enableEdgeToEdge()
        setContent {
            Log.d("Turntable", "MainActivity setContent")
            val viewModel: TurntableViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> null
            }
            TurntableTheme(darkTheme = darkTheme) {
                Log.d("Turntable", "MainActivity TurntableTheme")
                TurntableApp(viewModel)
            }
        }
        Log.d("Turntable", "MainActivity onCreate end")
    }
}
