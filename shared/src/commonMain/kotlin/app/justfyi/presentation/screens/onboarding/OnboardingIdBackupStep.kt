package app.justfyi.presentation.screens.onboarding

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justfyi.presentation.components.AnonymousIdCard
import justfyi.shared.generated.resources.*
import justfyi.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Step 2: ID Backup
 * User confirms they've saved their ID for recovery.
 */
@Composable
fun IdBackupStep(
    formattedId: String,
    isIdRevealed: Boolean,
    isBackupConfirmed: Boolean,
    onToggleReveal: () -> Unit,
    onCopyId: () -> Unit,
    onConfirmBackup: () -> Unit,
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
            text = stringResource(Res.string.onboarding_backup_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.onboarding_backup_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ID Display Card
        AnonymousIdCard(
            formattedId = formattedId,
            isRevealed = isIdRevealed,
            onToggleReveal = onToggleReveal,
            onCopyClick = onCopyId,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Warning text
        IdBackupWarningCard()

        Spacer(modifier = Modifier.height(24.dp))

        // Confirmation checkbox
        BackupConfirmationCheckbox(
            isBackupConfirmed = isBackupConfirmed,
            onConfirmBackup = onConfirmBackup,
        )
    }
}

@Composable
private fun IdBackupWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.onboarding_backup_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun BackupConfirmationCheckbox(
    isBackupConfirmed: Boolean,
    onConfirmBackup: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { if (!isBackupConfirmed) onConfirmBackup() }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isBackupConfirmed,
            onCheckedChange = { if (it) onConfirmBackup() },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.onboarding_backup_confirm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun IdBackupStepPreview() {
    MaterialTheme {
        IdBackupStep(
            formattedId = "ABC1-23DE-F456-GHI7-89JK-L012",
            isIdRevealed = false,
            isBackupConfirmed = false,
            onToggleReveal = {},
            onCopyId = {},
            onConfirmBackup = {},
        )
    }
}

@Preview
@Composable
private fun IdBackupStepRevealedPreview() {
    MaterialTheme {
        IdBackupStep(
            formattedId = "ABC1-23DE-F456-GHI7-89JK-L012",
            isIdRevealed = true,
            isBackupConfirmed = true,
            onToggleReveal = {},
            onCopyId = {},
            onConfirmBackup = {},
        )
    }
}
