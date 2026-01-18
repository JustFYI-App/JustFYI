package app.justfyi

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Root composable for the Just FYI application.
 * Sets up the Material theme and provides the main content area.
 */
@Composable
expect fun App()

/**
 * Brand colors from app icon - Purple/Blue gradient and Orange accent.
 * Primary: #6370D8 (darker purple/blue from icon gradient)
 * Primary Light: #8E97DF (lighter purple from icon gradient)
 * Accent: #F06236 (orange from notification dot)
 * Success: #4CAF50 (green for negative test results)
 */
private val JustFyiPrimary = Color(0xFF6370D8)
private val JustFyiPrimaryLight = Color(0xFF8E97DF)
private val JustFyiAccent = Color(0xFFF06236)
val JustFyiSuccess = Color(0xFF4CAF50)

/**
 * Light color scheme for the Just FYI application.
 * Uses brand colors from the app icon.
 */
private val JustFyiLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = JustFyiPrimary,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E0FF),
        onPrimaryContainer = Color(0xFF1A1B4B),
        secondary = JustFyiAccent,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDBD0),
        onSecondaryContainer = Color(0xFF3A0A00),
        tertiary = JustFyiPrimaryLight,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE8E8FF),
        onTertiaryContainer = Color(0xFF1A1B4B),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFFBFF),
        onBackground = Color(0xFF1B1B1F),
        surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF1B1B1F),
        surfaceVariant = Color(0xFFE4E1EC),
        onSurfaceVariant = Color(0xFF46464F),
        outline = Color(0xFF777680),
        outlineVariant = Color(0xFFC8C5D0),
        scrim = Color.Black,
        inverseSurface = Color(0xFF313034),
        inverseOnSurface = Color(0xFFF3EFF4),
        inversePrimary = JustFyiPrimaryLight,
    )

/**
 * Dark color scheme for the Just FYI application.
 * Uses brand colors from the app icon.
 */
private val JustFyiDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = JustFyiPrimaryLight,
        onPrimary = Color(0xFF2A2B5E),
        primaryContainer = Color(0xFF4A4C80),
        onPrimaryContainer = Color(0xFFE0E0FF),
        secondary = Color(0xFFFFB4A0),
        onSecondary = Color(0xFF5C1900),
        secondaryContainer = Color(0xFF832800),
        onSecondaryContainer = Color(0xFFFFDBD0),
        tertiary = Color(0xFFC5C3FF),
        onTertiary = Color(0xFF2E2F60),
        tertiaryContainer = Color(0xFF454678),
        onTertiaryContainer = Color(0xFFE4E0FF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF1B1B1F),
        onBackground = Color(0xFFE5E1E6),
        surface = Color(0xFF1B1B1F),
        onSurface = Color(0xFFE5E1E6),
        surfaceVariant = Color(0xFF46464F),
        onSurfaceVariant = Color(0xFFC8C5D0),
        outline = Color(0xFF918F9A),
        outlineVariant = Color(0xFF46464F),
        scrim = Color.Black,
        inverseSurface = Color(0xFFE5E1E6),
        inverseOnSurface = Color(0xFF313034),
        inversePrimary = JustFyiPrimary,
    )

/**
 * Common theme wrapper for the application.
 * Platform-specific implementations provide the actual navigation.
 *
 * @param themePreference The theme preference: "system", "light", or "dark"
 * @param content The content to display within the theme
 */
@Composable
fun JustFyiTheme(
    themePreference: String = "system",
    content: @Composable () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()

    val useDarkTheme =
        when (themePreference) {
            "dark" -> true
            "light" -> false
            else -> isSystemDark // "system" or any other value follows system
        }

    val colorScheme =
        if (useDarkTheme) {
            JustFyiDarkColorScheme
        } else {
            JustFyiLightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            content()
        }
    }
}
