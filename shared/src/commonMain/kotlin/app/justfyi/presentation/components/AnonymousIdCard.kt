package app.justfyi.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justfyi.platform.rememberReduceMotionPreference
import app.justfyi.presentation.util.AnimationUtils
import app.justfyi.presentation.util.minimumTouchTargetHeight
import app.justfyi.presentation.util.reducedExpandEnterTransition
import app.justfyi.presentation.util.reducedShrinkExitTransition
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_hide_id
import justfyi.shared.generated.resources.cd_reveal_id
import justfyi.shared.generated.resources.profile_copy_id
import justfyi.shared.generated.resources.profile_tap_to_hide
import justfyi.shared.generated.resources.profile_tap_to_reveal
import justfyi.shared.generated.resources.profile_your_anonymous_id
import justfyi.shared.generated.resources.state_id_hidden
import justfyi.shared.generated.resources.state_id_revealed
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Reusable component for displaying and managing an anonymous ID.
 * Used in both ProfileScreen and Onboarding flow.
 *
 * Touch target compliance: The clickable ID reveal box meets WCAG 2.1 AA
 * minimum 48dp touch target through minimum height enforcement and full-width layout.
 *
 * Accessibility: This component provides:
 * - Heading semantics for the section title
 * - State descriptions for the reveal/hide toggle state
 * - Role.Button semantics for the clickable area
 * - Content descriptions for icons
 * - Reduce motion support for Crossfade and AnimatedVisibility animations
 * - Focusable modifier for keyboard navigation support
 *
 * @param formattedId The formatted anonymous ID to display
 * @param isRevealed Whether the ID is currently revealed or masked
 * @param onToggleReveal Callback when user wants to show/hide the ID
 * @param onCopyClick Callback when user wants to copy the ID
 * @param warningText Optional warning text to show below the ID
 * @param modifier Modifier for the component
 */
@Composable
fun AnonymousIdCard(
    formattedId: String,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopyClick: () -> Unit,
    warningText: String? = null,
    modifier: Modifier = Modifier,
) {
    // Check reduce motion preference
    val reduceMotion = rememberReduceMotionPreference()

    // Crossfade animation spec based on reduce motion preference
    val crossfadeAnimationSpec =
        if (reduceMotion.value) {
            snap<Float>()
        } else {
            tween<Float>(durationMillis = AnimationUtils.DEFAULT_ANIMATION_DURATION_MS)
        }

    // State description for screen readers
    val revealedStateDescription = stringResource(Res.string.state_id_revealed)
    val hiddenStateDescription = stringResource(Res.string.state_id_hidden)
    val currentStateDescription = if (isRevealed) revealedStateDescription else hiddenStateDescription

    JustFyiCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Section heading with heading semantics for screen reader navigation
            Text(
                text = stringResource(Res.string.profile_your_anonymous_id),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Animated hint text - use outline color for WCAG AA compliance
            // Crossfade respects reduce motion preference
            Crossfade(
                targetState = isRevealed,
                animationSpec = crossfadeAnimationSpec,
                label = "hintText",
            ) { revealed ->
                Text(
                    text =
                        if (revealed) {
                            stringResource(Res.string.profile_tap_to_hide)
                        } else {
                            stringResource(Res.string.profile_tap_to_reveal)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // ID Display Box with minimum touch target height, focusable, and accessibility semantics
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .minimumTouchTargetHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .focusable()
                        .clickable(onClick = onToggleReveal)
                        .padding(16.dp)
                        .semantics {
                            role = Role.Button
                            stateDescription = currentStateDescription
                        },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    // Animated ID text with crossfade - respects reduce motion
                    Crossfade(
                        targetState = isRevealed,
                        animationSpec = crossfadeAnimationSpec,
                        label = "idReveal",
                        modifier = Modifier.weight(1f),
                    ) { revealed ->
                        Text(
                            text =
                                if (revealed) {
                                    formattedId
                                } else {
                                    formattedId.replace(Regex("[A-Za-z0-9]"), "*")
                                },
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    // Toggle icon with content description
                    Icon(
                        imageVector =
                            if (isRevealed) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                        contentDescription =
                            if (isRevealed) {
                                stringResource(Res.string.cd_hide_id)
                            } else {
                                stringResource(Res.string.cd_reveal_id)
                            },
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Animated copy button (slides in when revealed) - respects reduce motion
            AnimatedVisibility(
                visible = isRevealed,
                enter = reducedExpandEnterTransition(reduceMotion.value),
                exit = reducedShrinkExitTransition(reduceMotion.value),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    JustFyiButton(
                        text = stringResource(Res.string.profile_copy_id),
                        onClick = onCopyClick,
                        variant = JustFyiButtonVariant.SECONDARY,
                        icon = Icons.Default.ContentCopy,
                        fullWidth = true,
                    )
                }
            }

            // Optional warning text
            if (warningText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = warningText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun AnonymousIdCardHiddenPreview() {
    MaterialTheme {
        AnonymousIdCard(
            formattedId = "ABC1-23DE-F456-GHI7-89JK-L012",
            isRevealed = false,
            onToggleReveal = {},
            onCopyClick = {},
            warningText = "Save this ID to recover your account if you reinstall the app.",
        )
    }
}

@Preview
@Composable
private fun AnonymousIdCardRevealedPreview() {
    MaterialTheme {
        AnonymousIdCard(
            formattedId = "ABC1-23DE-F456-GHI7-89JK-L012",
            isRevealed = true,
            onToggleReveal = {},
            onCopyClick = {},
            warningText = "Save this ID to recover your account if you reinstall the app.",
        )
    }
}

@Preview
@Composable
private fun AnonymousIdCardNoWarningPreview() {
    MaterialTheme {
        AnonymousIdCard(
            formattedId = "ABC1-23DE-F456-GHI7-89JK-L012",
            isRevealed = true,
            onToggleReveal = {},
            onCopyClick = {},
        )
    }
}
