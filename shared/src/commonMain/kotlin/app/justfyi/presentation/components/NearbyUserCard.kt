package app.justfyi.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justfyi.platform.rememberReduceMotionPreference
import app.justfyi.presentation.util.minimumTouchTargetHeight
import app.justfyi.presentation.util.reducedAnimationSpec
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_nearby_user
import justfyi.shared.generated.resources.cd_signal_strength
import justfyi.shared.generated.resources.cd_user_not_selected
import justfyi.shared.generated.resources.cd_user_selected
import justfyi.shared.generated.resources.signal_excellent
import justfyi.shared.generated.resources.signal_fair
import justfyi.shared.generated.resources.signal_good
import justfyi.shared.generated.resources.signal_weak
import justfyi.shared.generated.resources.state_not_selected
import justfyi.shared.generated.resources.state_selected
import org.jetbrains.compose.resources.stringResource

/**
 * A card component for displaying a nearby user in the Just FYI app.
 * Shows the user's username and signal strength.
 *
 * Touch target compliance: This card meets WCAG 2.1 AA minimum 48dp touch target
 * through its full-width layout and minimum height enforcement.
 *
 * Accessibility: This component provides:
 * - Content description with username and signal strength
 * - State description for selection status (using localized strings)
 * - Merged descendants for single screen reader focus
 * - Role.Button semantics for the clickable card
 * - Reduce motion support for selection animations
 * - Focusable modifier for keyboard navigation support
 *
 * @param username The nearby user's display name
 * @param signalStrength The Bluetooth signal strength (RSSI value)
 * @param onClick Callback when the card is clicked
 * @param modifier Modifier for the card
 * @param isSelected Whether this user is currently selected
 */
@Composable
fun NearbyUserCard(
    username: String,
    signalStrength: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    // Check reduce motion preference for animations
    val reduceMotion = rememberReduceMotionPreference()

    // Animated card properties with reduce motion support
    val backgroundColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        animationSpec = reducedAnimationSpec(reduceMotion.value),
        label = "cardBackground",
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = reducedAnimationSpec(reduceMotion.value),
        label = "borderWidth",
    )

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 2.dp,
        animationSpec = reducedAnimationSpec(reduceMotion.value),
        label = "cardElevation",
    )

    val textColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = reducedAnimationSpec(reduceMotion.value),
        label = "textColor",
    )

    // Accessibility descriptions
    val signalLabel = getSignalStrengthLabel(signalStrength)
    val signalBars = getSignalBars(signalStrength)
    val signalDescription = stringResource(Res.string.cd_signal_strength, signalLabel, signalBars)

    // State description for selection using localized strings
    val selectedStateDescription = stringResource(Res.string.state_selected)
    val notSelectedStateDescription = stringResource(Res.string.state_not_selected)
    val selectionStateDescription = if (isSelected) selectedStateDescription else notSelectedStateDescription

    // Selection icon content description
    val selectionIconDescription =
        if (isSelected) {
            stringResource(Res.string.cd_user_selected)
        } else {
            stringResource(Res.string.cd_user_not_selected)
        }
    val cardDescription = stringResource(Res.string.cd_nearby_user, username, signalLabel)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .minimumTouchTargetHeight()
                .clip(RoundedCornerShape(12.dp))
                .focusable()
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = cardDescription
                    stateDescription = selectionStateDescription
                    role = Role.Button
                },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border =
            if (borderWidth > 0.dp) {
                BorderStroke(borderWidth, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar placeholder
            Surface(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
            ) {
                // Display first letter of username
                val initial = username.firstOrNull()?.uppercaseChar() ?: '?'
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                )
                Text(
                    text = signalLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = getSignalStrengthColor(signalStrength),
                )
            }

            // Selection/signal indicator - fixed size to prevent layout shifts
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    // Check icon when selected
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = selectionIconDescription,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    // Signal strength when not selected
                    SignalStrengthIndicator(
                        signalStrength = signalStrength,
                        contentDescription = signalDescription,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalStrengthIndicator(
    signalStrength: Int,
    contentDescription: String,
) {
    val bars = getSignalBars(signalStrength)

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier =
            Modifier.semantics {
                this.contentDescription = contentDescription
            },
    ) {
        repeat(4) { index ->
            val barHeight = 6 + (index * 4)
            Surface(
                modifier =
                    Modifier
                        .size(width = 4.dp, height = barHeight.dp)
                        .padding(horizontal = 1.dp),
                color =
                    if (index < bars) {
                        getSignalStrengthColor(signalStrength)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                shape = RoundedCornerShape(2.dp),
            ) {}
        }
    }
}

/**
 * Returns the number of signal bars based on RSSI value.
 */
private fun getSignalBars(signalStrength: Int): Int =
    when {
        signalStrength > -50 -> 4
        signalStrength > -60 -> 3
        signalStrength > -70 -> 2
        else -> 1
    }

@Composable
private fun getSignalStrengthColor(signalStrength: Int) =
    when {
        signalStrength > -50 -> MaterialTheme.colorScheme.primary
        signalStrength > -60 -> MaterialTheme.colorScheme.tertiary
        signalStrength > -70 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }

@Composable
private fun getSignalStrengthLabel(signalStrength: Int) =
    when {
        signalStrength > -50 -> stringResource(Res.string.signal_excellent)
        signalStrength > -60 -> stringResource(Res.string.signal_good)
        signalStrength > -70 -> stringResource(Res.string.signal_fair)
        else -> stringResource(Res.string.signal_weak)
    }
