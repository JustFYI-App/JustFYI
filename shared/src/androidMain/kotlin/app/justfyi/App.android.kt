package app.justfyi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.justfyi.di.AppGraphHolder
import app.justfyi.domain.repository.SettingsRepository
import app.justfyi.presentation.navigation.JustFyiNavHost

/**
 * Android-specific implementation of the App composable.
 * Sets up the theme and navigation for the Android platform.
 *
 * Uses Metro DI for dependency injection. The NavigationGraph is retrieved
 * from AppGraphHolder, which is initialized by JustFyiApplication during
 * application startup. This provides all ViewModels and their dependencies
 * to JustFyiNavHost.
 *
 * @param initialNotificationId Optional notification ID for deep linking from FCM notification tap
 * @param onNotificationConsumed Callback invoked after navigating to a notification, to clear the pending state
 */
@Composable
fun App(
    initialNotificationId: String? = null,
    onNotificationConsumed: () -> Unit = {},
) {
    // Get the NavigationGraph from the AppGraphHolder
    // This is the Metro-generated AppGraph that provides all ViewModels
    val navigationGraph = AppGraphHolder.getNavigationGraph()

    // Observe theme preference from SettingsRepository
    var themePreference by remember { mutableStateOf("system") }

    LaunchedEffect(Unit) {
        themePreference = navigationGraph.settingsRepository.getTheme()
    }

    // Observe theme changes reactively
    LaunchedEffect(Unit) {
        navigationGraph.settingsRepository
            .observeSetting(SettingsRepository.KEY_THEME_PREFERENCE)
            .collect { theme ->
                themePreference = theme ?: "system"
            }
    }

    JustFyiTheme(themePreference = themePreference) {
        JustFyiNavHost(
            graph = navigationGraph,
            initialNotificationId = initialNotificationId,
            onNotificationConsumed = onNotificationConsumed,
        )
    }
}

/**
 * Default App composable for use without FCM deep linking.
 */
@Composable
actual fun App() {
    App(initialNotificationId = null)
}
