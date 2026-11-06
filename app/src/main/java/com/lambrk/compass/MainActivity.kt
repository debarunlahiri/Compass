package com.lambrk.compass

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambrk.compass.data.ThemeMode
import com.lambrk.compass.ui.compass.CompassScreen
import com.lambrk.compass.ui.settings.SettingsScreen
import com.lambrk.compass.ui.theme.CompassTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CompassViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val ambientDark by viewModel.ambientDark.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                // Until the first light reading arrives (or with no light sensor), use the system.
                ThemeMode.AMBIENT -> ambientDark ?: systemDark
            }

            // Status/navigation bar icons follow the app theme, not the system theme.
            LaunchedEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            KeepScreenOn(settings.keepScreenOn)

            CompassTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showSettings) { showSettings = false }

                AnimatedContent(
                    targetState = showSettings,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
                        } else {
                            fadeIn() togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                        }
                    },
                    label = "screen",
                ) { settingsVisible ->
                    if (settingsVisible) {
                        SettingsScreen(viewModel, onBack = { showSettings = false })
                    } else {
                        CompassScreen(viewModel, onOpenSettings = { showSettings = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
