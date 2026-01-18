package app.justfyi.presentation.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A styled snackbar host for the Just FYI app.
 * Provides consistent snackbar styling throughout the app.
 *
 * @param hostState The SnackbarHostState to manage snackbar display
 * @param modifier Modifier for the snackbar host
 */
@Composable
fun JustFyiSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { data ->
        Snackbar(snackbarData = data)
    }
}
