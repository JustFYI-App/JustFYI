package app.justfyi.presentation.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justfyi.domain.usecase.UsernameConstants
import app.justfyi.presentation.components.JustFyiCard
import justfyi.shared.generated.resources.*
import justfyi.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Step 4: Username Setup (Optional)
 * User can set display username visible to other Just FYI users.
 */
@Composable
fun UsernameStep(
    username: String,
    usernameError: String?,
    isLoading: Boolean,
    onUsernameChange: (String) -> Unit,
    onSetUsername: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(Res.string.onboarding_username_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.onboarding_username_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Username input card
        UsernameInputCard(
            username = username,
            usernameError = usernameError,
            isLoading = isLoading,
            onUsernameChange = onUsernameChange,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Info about anonymous usage
        UsernameInfoCard()
    }
}

@Composable
private fun UsernameInputCard(
    username: String,
    usernameError: String?,
    isLoading: Boolean,
    onUsernameChange: (String) -> Unit,
) {
    JustFyiCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = username,
                onValueChange = { newValue ->
                    // Filter to ASCII only and enforce max length
                    val filteredValue = UsernameConstants.filterUsernameInput(newValue)
                    onUsernameChange(filteredValue)
                },
                label = { Text(stringResource(Res.string.onboarding_username_label)) },
                placeholder = { Text(stringResource(Res.string.onboarding_username_placeholder)) },
                isError = usernameError != null,
                supportingText =
                    usernameError?.let { error ->
                        { Text(error, color = MaterialTheme.colorScheme.error) }
                    },
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.onboarding_username_requirements),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UsernameInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "i",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.onboarding_username_skip_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun UsernameStepPreview() {
    MaterialTheme {
        UsernameStep(
            username = "",
            usernameError = null,
            isLoading = false,
            onUsernameChange = {},
            onSetUsername = {},
            onSkip = {},
        )
    }
}

@Preview
@Composable
private fun UsernameStepWithErrorPreview() {
    MaterialTheme {
        UsernameStep(
            username = "Test User!",
            usernameError = "Username contains invalid characters",
            isLoading = false,
            onUsernameChange = {},
            onSetUsername = {},
            onSkip = {},
        )
    }
}
