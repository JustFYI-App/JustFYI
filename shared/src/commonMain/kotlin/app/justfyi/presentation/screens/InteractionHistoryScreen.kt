package app.justfyi.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justfyi.domain.model.Interaction
import app.justfyi.domain.usecase.InteractionHistoryUseCase
import app.justfyi.presentation.components.ErrorMessage
import app.justfyi.presentation.components.JustFyiSnackbarHost
import app.justfyi.presentation.components.JustFyiTopAppBar
import app.justfyi.presentation.components.LoadingIndicator
import app.justfyi.presentation.feature.history.InteractionDateTime
import app.justfyi.presentation.feature.history.InteractionHistoryUiState
import app.justfyi.presentation.feature.history.InteractionHistoryViewModel
import app.justfyi.presentation.navigation.NavigationActions
import justfyi.shared.generated.resources.*
import justfyi.shared.generated.resources.Res
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Data class holding an interaction with pre-computed display data.
 */
data class InteractionDisplayItem(
    val interaction: Interaction,
    val dateTime: InteractionDateTime,
    val daysUntilExpiry: Int,
)

/**
 * Reusable top bar for the Interaction History screen.
 * Uses stringResource() internally for proper localization.
 */
@Composable
fun InteractionHistoryTopBar(onNavigationClick: () -> Unit = {}) {
    JustFyiTopAppBar(
        title = stringResource(Res.string.history_title),
        showNavigationIcon = true,
        onNavigationClick = onNavigationClick,
    )
}

/**
 * Interaction History screen composable.
 * Displays a chronological list of recorded interactions.
 *
 * @param viewModel The InteractionHistoryViewModel instance
 * @param navigationActions Navigation actions for screen transitions
 * @param modifier Modifier for the screen
 */
@Composable
fun InteractionHistoryScreen(
    viewModel: InteractionHistoryViewModel,
    navigationActions: NavigationActions,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle UI state side effects
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is InteractionHistoryUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long,
                )
                viewModel.clearError()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { InteractionHistoryTopBar(onNavigationClick = { navigationActions.navigateBack() }) },
        snackbarHost = { JustFyiSnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is InteractionHistoryUiState.Loading -> {
                    LoadingIndicator(message = stringResource(Res.string.history_loading))
                }

                is InteractionHistoryUiState.Error -> {
                    ErrorMessage(
                        message = state.message,
                        onRetry = { viewModel.refresh() },
                    )
                }

                is InteractionHistoryUiState.Success -> {
                    if (state.interactions.isEmpty()) {
                        EmptyHistoryContent()
                    } else {
                        // Pre-compute grouped interactions with display data
                        val groupedInteractions =
                            state.interactions
                                .map { interaction ->
                                    InteractionDisplayItem(
                                        interaction = interaction,
                                        dateTime = formatInteractionDateTime(interaction.recordedAt),
                                        daysUntilExpiry = getDaysUntilExpiry(interaction.recordedAt),
                                    )
                                }.groupBy { formatDateHeader(it.interaction.recordedAt) }

                        InteractionHistoryContent(
                            groupedInteractions = groupedInteractions,
                            onInteractionClick = { interactionId ->
                                navigationActions.navigateToInteractionDetail(interactionId)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InteractionHistoryContent(
    groupedInteractions: Map<String, List<InteractionDisplayItem>>,
    onInteractionClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Retention notice
        item {
            RetentionNoticeCard()
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Grouped interactions by date
        groupedInteractions.forEach { (dateHeader, dayInteractions) ->
            // Date header
            item {
                Text(
                    text = dateHeader,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            // Interactions for this date
            items(
                items = dayInteractions,
                key = { it.interaction.id },
            ) { item ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                ) {
                    InteractionCard(
                        interaction = item.interaction,
                        dateTime = item.dateTime,
                        daysUntilExpiry = item.daysUntilExpiry,
                        onClick = { onInteractionClick(item.interaction.id) },
                    )
                }
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RetentionNoticeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.history_retention_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun InteractionCard(
    interaction: Interaction,
    dateTime: InteractionDateTime,
    daysUntilExpiry: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = interaction.partnerUsernameSnapshot.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // User info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = interaction.partnerUsernameSnapshot,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateTime.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Text(
                        text = " ${stringResource(Res.string.history_at)} ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = dateTime.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            // Expiry indicator
            Column(
                horizontalAlignment = Alignment.End,
            ) {
                if (daysUntilExpiry <= 7) {
                    Text(
                        text = stringResource(Res.string.history_days_left, daysUntilExpiry),
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (daysUntilExpiry <= 3) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                    )
                }
                if (!interaction.syncedToCloud) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.history_pending_sync),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryContent() {
    // Floating animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "floatOffset",
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier =
                Modifier
                    .size(80.dp)
                    .graphicsLayer { translationY = offsetY },
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.history_no_interactions),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.history_no_interactions_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ============== Formatting Functions ==============

/**
 * Formats the interaction timestamp for display.
 */
fun formatInteractionDateTime(timestamp: Long): InteractionDateTime {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    val date =
        buildString {
            append(localDateTime.day)
            append(" ")
            append(getMonthName(localDateTime.month.ordinal + 1))
            append(" ")
            append(localDateTime.year)
        }

    val time =
        buildString {
            append(localDateTime.hour.toString().padStart(2, '0'))
            append(":")
            append(localDateTime.minute.toString().padStart(2, '0'))
        }

    return InteractionDateTime(date, time)
}

/**
 * Calculates days until interaction expires (120-day retention).
 */
fun getDaysUntilExpiry(recordedAt: Long): Int {
    val now = Clock.System.now().toEpochMilliseconds()
    val retentionPeriodMs = InteractionHistoryUseCase.RETENTION_DAYS.toLong() * 24 * 60 * 60 * 1000
    val expiryTime = recordedAt + retentionPeriodMs
    val remainingMs = expiryTime - now
    return (remainingMs / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
}

/**
 * Formats the date for section headers.
 */
fun formatDateHeader(timestamp: Long): String {
    val now = Clock.System.now()
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val today =
        Instant
            .fromEpochMilliseconds(now.toEpochMilliseconds())
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    val interactionDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date

    return when {
        interactionDate == today -> "Today"
        interactionDate.toEpochDays() == today.toEpochDays() - 1 -> "Yesterday"
        else ->
            buildString {
                append(interactionDate.day)
                append(" ")
                append(getMonthName(interactionDate.month.ordinal + 1))
                append(" ")
                append(interactionDate.year)
            }
    }
}

private fun getMonthName(month: Int): String =
    when (month) {
        1 -> "Jan"
        2 -> "Feb"
        3 -> "Mar"
        4 -> "Apr"
        5 -> "May"
        6 -> "Jun"
        7 -> "Jul"
        8 -> "Aug"
        9 -> "Sep"
        10 -> "Oct"
        11 -> "Nov"
        12 -> "Dec"
        else -> ""
    }

// ============== Previews ==============

@Preview
@Composable
private fun EmptyHistoryContentPreview() {
    MaterialTheme {
        EmptyHistoryContent()
    }
}

@Preview
@Composable
private fun RetentionNoticeCardPreview() {
    MaterialTheme {
        RetentionNoticeCard()
    }
}

@Preview
@Composable
private fun InteractionCardPreview() {
    MaterialTheme {
        InteractionCard(
            interaction =
                Interaction(
                    id = "1",
                    partnerAnonymousId = "ABC123DEF456",
                    partnerUsernameSnapshot = "Alice",
                    recordedAt = Clock.System.now().toEpochMilliseconds(),
                    syncedToCloud = true,
                ),
            dateTime =
                InteractionDateTime(
                    date = "Dec 26, 2025",
                    time = "10:30 AM",
                ),
            daysUntilExpiry = 14,
            onClick = {},
        )
    }
}
