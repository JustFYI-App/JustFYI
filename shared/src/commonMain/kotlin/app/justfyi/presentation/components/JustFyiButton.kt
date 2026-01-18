package app.justfyi.presentation.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Variants for JustFyiButton styling.
 */
enum class JustFyiButtonVariant {
    PRIMARY,
    SECONDARY,
    TEXT,
    DANGER,
}

/**
 * A styled button component for the Just FYI app.
 * Supports primary, secondary, text, and danger variants.
 *
 * @param text The button label text
 * @param onClick Callback when the button is clicked
 * @param modifier Modifier for the button
 * @param variant The button styling variant
 * @param enabled Whether the button is enabled
 * @param isLoading Whether to show a loading indicator
 * @param icon Optional icon to display before the text
 * @param fullWidth Whether the button should fill the available width
 */
@Composable
fun JustFyiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: JustFyiButtonVariant = JustFyiButtonVariant.PRIMARY,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    fullWidth: Boolean = false,
) {
    val effectiveEnabled = enabled && !isLoading
    val buttonModifier =
        if (fullWidth) {
            modifier.fillMaxWidth()
        } else {
            modifier
        }

    when (variant) {
        JustFyiButtonVariant.PRIMARY -> {
            Button(
                onClick = onClick,
                enabled = effectiveEnabled,
                modifier = buttonModifier,
            ) {
                ButtonContent(text, isLoading, icon)
            }
        }
        JustFyiButtonVariant.SECONDARY -> {
            OutlinedButton(
                onClick = onClick,
                enabled = effectiveEnabled,
                modifier = buttonModifier,
            ) {
                ButtonContent(text, isLoading, icon)
            }
        }
        JustFyiButtonVariant.TEXT -> {
            TextButton(
                onClick = onClick,
                enabled = effectiveEnabled,
                modifier = buttonModifier,
            ) {
                ButtonContent(text, isLoading, icon)
            }
        }
        JustFyiButtonVariant.DANGER -> {
            Button(
                onClick = onClick,
                enabled = effectiveEnabled,
                modifier = buttonModifier,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                ButtonContent(text, isLoading, icon)
            }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    isLoading: Boolean,
    icon: ImageVector?,
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
    } else if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
    Text(text = text)
}
