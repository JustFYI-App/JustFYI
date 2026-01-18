package app.justfyi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.justfyi.di.IosAppGraph
import app.justfyi.domain.repository.SettingsRepository
import app.justfyi.presentation.navigation.JustFyiNavHost

/**
 * iOS-specific implementation of the App composable.
 *
 * This connects the Compose UI to the Metro DI graph and navigation system.
 * The IosAppGraph provides all dependencies including ViewModels.
 */
@Composable
actual fun App() {
    // Get the navigation graph from the DI graph
    // The DI graph must be initialized before App() is called (in MainViewController)
    if (IosAppGraph.isInitialized()) {
        val graph = IosAppGraph.getInstance()

        // Observe theme preference from SettingsRepository
        var themePreference by remember { mutableStateOf("system") }

        LaunchedEffect(Unit) {
            themePreference = graph.settingsRepository.getTheme()
        }

        // Observe theme changes reactively
        LaunchedEffect(Unit) {
            graph.settingsRepository
                .observeSetting(SettingsRepository.KEY_THEME_PREFERENCE)
                .collect { theme ->
                    themePreference = theme ?: "system"
                }
        }

        JustFyiTheme(themePreference = themePreference) {
            JustFyiNavHost(graph = graph)
        }
    } else {
        // Fallback if DI is not initialized (should not happen in normal flow)
        JustFyiTheme {
            IosAppNotReady()
        }
    }
}

/**
 * Fallback UI shown when the DI graph is not initialized.
 * This should not happen in normal operation as MainViewController
 * initializes the DI graph before creating the Compose UI.
 */
@Composable
private fun IosAppNotReady() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("Initializing Just FYI...")
    }
}
