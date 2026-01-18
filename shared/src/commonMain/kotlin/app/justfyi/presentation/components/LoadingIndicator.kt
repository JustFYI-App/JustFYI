package app.justfyi.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_loading
import org.jetbrains.compose.resources.stringResource

/**
 * A loading indicator component for the Just FYI app.
 * Shows a circular progress indicator with an optional message.
 *
 * Accessibility: This component provides:
 * - Content description for screen readers
 * - liveRegion semantics to announce loading state changes
 * - Uses polite announcement mode to avoid interrupting user actions
 *
 * @param modifier Modifier for the component
 * @param message Optional message to display below the indicator
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val loadingDescription = message ?: stringResource(Res.string.cd_loading)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = loadingDescription
                    liveRegion = LiveRegionMode.Polite
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
        )

        if (message != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
