package app.justfyi.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_navigate_back
import org.jetbrains.compose.resources.stringResource

/**
 * A styled top app bar for the Just FYI app.
 * Provides consistent header styling throughout the app.
 *
 * Accessibility: The title uses heading() semantics to enable
 * screen reader navigation between headings (TalkBack/VoiceOver).
 * This allows users to quickly identify the current screen.
 *
 * @param title The title text to display
 * @param modifier Modifier for the app bar
 * @param showNavigationIcon Whether to show a back navigation icon
 * @param onNavigationClick Callback when navigation icon is clicked
 * @param navigationIcon Custom navigation icon (defaults to back arrow)
 * @param actions Optional action icons on the right side
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JustFyiTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    showNavigationIcon: Boolean = false,
    onNavigationClick: (() -> Unit)? = null,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    actions: @Composable () -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (showNavigationIcon && onNavigationClick != null) {
                IconButton(onClick = onNavigationClick) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = stringResource(Res.string.cd_navigate_back),
                    )
                }
            }
        },
        actions = { actions() },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
    )
}
