package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.ArunaNavShell
import com.example.ui.theme.ArunaTheme
import com.example.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AsistenApp).container

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val dynamicColor by container.secretDataStore.dynamicColorEnabled
                .collectAsStateWithLifecycle(initialValue = true)
            val themeMode by container.secretDataStore.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            ArunaTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
            ) {
                ArunaNavShell(
                    container = container,
                    widthSizeClass = windowSizeClass.widthSizeClass,
                )
            }
        }
    }
}
