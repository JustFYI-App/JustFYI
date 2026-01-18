package app.justfyi.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import app.justfyi.JustFyiSuccess
import app.justfyi.domain.model.ChainNode
import app.justfyi.domain.model.ChainVisualization
import app.justfyi.domain.model.STI
import app.justfyi.domain.model.TestStatus
import app.justfyi.util.DateTimeFormatter
import app.justfyi.util.currentTimeMillis
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.chain_negative_test_notice
import justfyi.shared.generated.resources.chain_no_test
import justfyi.shared.generated.resources.chain_not_available
import justfyi.shared.generated.resources.chain_someone
import justfyi.shared.generated.resources.chain_title
import justfyi.shared.generated.resources.chain_you
import justfyi.shared.generated.resources.sti_chlamydia
import justfyi.shared.generated.resources.sti_gonorrhea
import justfyi.shared.generated.resources.sti_herpes
import justfyi.shared.generated.resources.sti_hiv
import justfyi.shared.generated.resources.sti_hpv
import justfyi.shared.generated.resources.sti_other
import justfyi.shared.generated.resources.sti_syphilis
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlin.time.Instant

/**
 * Composable that displays the exposure chain visualization.
 * Shows a vertical timeline with colored dots and connecting lines.
 *
 * @param chainVisualization The chain data to visualize
 * @param stiType The STI type for this exposure (shown with test status)
 * @param modifier Modifier for the composable
 */
@Composable
fun ChainVisualizationView(
    chainVisualization: ChainVisualization,
    stiType: String? = null,
    modifier: Modifier = Modifier,
) {
    if (chainVisualization.nodes.isEmpty()) {
        EmptyChainView(modifier)
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.chain_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Vertical chain visualization
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                chainVisualization.nodes.forEachIndexed { index, node ->
                    val isLast = index == chainVisualization.nodes.lastIndex
                    val nextNode = if (!isLast) chainVisualization.nodes[index + 1] else null

                    VerticalChainNodeItem(
                        node = node,
                        nextNode = nextNode,
                        isLast = isLast,
                        stiType = stiType,
                    )
                }
            }

            // Show risk reduction notice only if someone OTHER than current user tested negative
            if (chainVisualization.hasOtherNegativeTestInChain()) {
                Spacer(modifier = Modifier.height(12.dp))
                NegativeTestNotice()
            }
        }
    }
}

/**
 * A single node in the vertical chain visualization with connecting line to next node.
 *
 * @param node The chain node data
 * @param nextNode The next node in the chain (for line color), null if last
 * @param isLast Whether this is the last node in the chain
 * @param stiType The STI type for this exposure (shown with test status)
 * @param modifier Modifier for the composable
 */
@Composable
private fun VerticalChainNodeItem(
    node: ChainNode,
    nextNode: ChainNode?,
    isLast: Boolean,
    stiType: String? = null,
    modifier: Modifier = Modifier,
) {
    val dotColor = getNodeDotColor(node.testStatus)
    val lineColor = if (nextNode != null) getLineColor(node.testStatus, nextNode.testStatus) else dotColor

    ConstraintLayout(
        modifier = modifier.fillMaxWidth(),
    ) {
        val (dot, line, username, chips, bottomSpacer) = createRefs()

        // Dot - centered vertically with username
        Box(
            modifier =
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .constrainAs(dot) {
                        start.linkTo(parent.start, margin = 8.dp)
                        top.linkTo(username.top)
                        bottom.linkTo(username.bottom)
                    },
        )

        // Bottom spacer - creates gap between nodes (only when not last)
        Spacer(
            modifier =
                Modifier
                    .height(if (isLast) 0.dp else 8.dp)
                    .constrainAs(bottomSpacer) {
                        top.linkTo(chips.bottom)
                        bottom.linkTo(parent.bottom)
                    },
        )

        // Line - from dot bottom to parent bottom, extended slightly to connect with next node's dot
        // The next node's dot is centered with its username text, so it's offset down from the top.
        // We use a negative margin to extend the line into the next node's space.
        if (!isLast) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .background(lineColor)
                        .constrainAs(line) {
                            start.linkTo(dot.start)
                            end.linkTo(dot.end)
                            top.linkTo(dot.bottom)
                            bottom.linkTo(parent.bottom, margin = (-4).dp)
                            height = Dimension.fillToConstraints
                        },
            )
        }

        // Username - replace localization markers with translated strings
        // Note: @@chain_someone@@ and @@chain_you@@ are markers from backend for localization
        val displayUsername =
            when {
                node.isCurrentUser -> stringResource(Res.string.chain_you)
                node.username == "@@chain_you@@" -> stringResource(Res.string.chain_you)
                node.username == "@@chain_someone@@" -> stringResource(Res.string.chain_someone)
                else -> node.username
            }
        Text(
            text = displayUsername,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (node.isCurrentUser) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier.constrainAs(username) {
                    start.linkTo(dot.end, margin = 12.dp)
                    top.linkTo(parent.top)
                },
        )

        // Status chips and date row
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier =
                Modifier.constrainAs(chips) {
                    start.linkTo(username.start)
                    end.linkTo(parent.end)
                    top.linkTo(username.bottom, margin = 4.dp)
                    width = Dimension.fillToConstraints
                },
        ) {
            // Status chips with STI type
            StatusChips(
                status = node.testStatus,
                stiType = stiType,
                testedPositiveFor = node.testedPositiveFor,
                modifier = Modifier.weight(1f),
            )

            // Date if available - pushed to far right
            // Use outline color for secondary text to ensure WCAG AA compliance
            node.date?.let { date ->
                Text(
                    text = formatDate(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/**
 * Status chips showing STI types with colored background based on test status.
 * Red chip for positive, green chip for negative, gray for unknown.
 * Uses same styling as StiTypeChips (RoundedCornerShape 16dp).
 * Uses FlowRow to wrap chips to multiple lines when needed.
 *
 * @param status The test status for this node
 * @param stiType The full STI types from the notification (JSON array)
 * @param testedPositiveFor Specific STI types this user tested positive for (subset of notification STIs)
 * @param modifier Modifier for the composable
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusChips(
    status: TestStatus,
    stiType: String?,
    testedPositiveFor: List<String>? = null,
    modifier: Modifier = Modifier,
) {
    when (status) {
        TestStatus.POSITIVE -> {
            // For positive: Only show the specific STIs the user tested positive for
            val stiList =
                if (!testedPositiveFor.isNullOrEmpty()) {
                    // Convert string names to STI enum, filtering valid ones
                    testedPositiveFor.mapNotNull { stiName ->
                        try {
                            STI.valueOf(stiName)
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else {
                    // Fallback to all STIs if testedPositiveFor not set (backwards compatibility)
                    stiType?.let { STI.fromJsonArray(it) } ?: emptyList()
                }

            if (stiList.isNotEmpty()) {
                FlowRow(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    stiList.forEach { sti ->
                        StatusStiChip(sti = sti, status = status)
                    }
                }
            } else {
                // Fallback if no STI parsed
                StatusTextChip(
                    text = stringResource(Res.string.chain_no_test),
                    status = status,
                )
            }
        }
        TestStatus.NEGATIVE -> {
            // For negative: Show all STIs from the notification
            val stiList = stiType?.let { STI.fromJsonArray(it) } ?: emptyList()
            if (stiList.isNotEmpty()) {
                FlowRow(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    stiList.forEach { sti ->
                        StatusStiChip(sti = sti, status = status)
                    }
                }
            } else {
                // Fallback if no STI parsed
                StatusTextChip(
                    text = stringResource(Res.string.chain_no_test),
                    status = status,
                )
            }
        }
        TestStatus.UNKNOWN -> {
            StatusTextChip(
                text = stringResource(Res.string.chain_no_test),
                status = status,
            )
        }
    }
}

/**
 * Single STI chip with status-based color.
 */
@Composable
private fun StatusStiChip(
    sti: STI,
    status: TestStatus,
) {
    val backgroundColor =
        when (status) {
            TestStatus.POSITIVE -> MaterialTheme.colorScheme.error
            TestStatus.NEGATIVE -> JustFyiSuccess // Green
            TestStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
        }
    val textColor =
        when (status) {
            TestStatus.POSITIVE -> MaterialTheme.colorScheme.onError
            TestStatus.NEGATIVE -> Color.White
            TestStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val stiName =
        when (sti) {
            STI.HIV -> stringResource(Res.string.sti_hiv)
            STI.SYPHILIS -> stringResource(Res.string.sti_syphilis)
            STI.GONORRHEA -> stringResource(Res.string.sti_gonorrhea)
            STI.CHLAMYDIA -> stringResource(Res.string.sti_chlamydia)
            STI.HPV -> stringResource(Res.string.sti_hpv)
            STI.HERPES -> stringResource(Res.string.sti_herpes)
            STI.OTHER -> stringResource(Res.string.sti_other)
        }

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = stiName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}

/**
 * Text chip for status display.
 * For UNKNOWN status: Uses an outlined style with a schedule icon for a cleaner look.
 * For POSITIVE/NEGATIVE: Uses filled style matching STI chips.
 */
@Composable
private fun StatusTextChip(
    text: String,
    status: TestStatus,
) {
    when (status) {
        TestStatus.UNKNOWN -> {
            // Outlined style with icon for "No test" - looks cleaner than plain gray fill
            // Use outline color directly for WCAG AA compliance
            val outlineColor = MaterialTheme.colorScheme.outline
            val textColor = MaterialTheme.colorScheme.onSurfaceVariant

            Row(
                modifier =
                    Modifier
                        .border(
                            width = 1.dp,
                            color = outlineColor,
                            shape = RoundedCornerShape(16.dp),
                        ).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = textColor,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                )
            }
        }
        else -> {
            // Filled style for positive/negative (fallback case)
            val backgroundColor =
                when (status) {
                    TestStatus.POSITIVE -> MaterialTheme.colorScheme.error
                    TestStatus.NEGATIVE -> JustFyiSuccess
                    TestStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                }
            val textColor =
                when (status) {
                    TestStatus.POSITIVE -> MaterialTheme.colorScheme.onError
                    TestStatus.NEGATIVE -> Color.White
                    TestStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                }

            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(backgroundColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                )
            }
        }
    }
}

/**
 * Get dot color based on test status.
 * Red for positive, green for negative, gray for unknown.
 */
@Composable
private fun getNodeDotColor(status: TestStatus): Color =
    when (status) {
        TestStatus.POSITIVE -> MaterialTheme.colorScheme.error
        TestStatus.NEGATIVE -> JustFyiSuccess // Green
        TestStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
    }

/**
 * Get line color between two nodes.
 * Green if the next node tested negative (risk reduced), otherwise follows current node status.
 */
@Composable
private fun getLineColor(
    currentStatus: TestStatus,
    nextStatus: TestStatus,
): Color =
    when {
        nextStatus == TestStatus.NEGATIVE -> JustFyiSuccess // Green - risk reduced
        currentStatus == TestStatus.POSITIVE -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

/**
 * Notice displayed when someone in the chain tested negative.
 */
@Composable
private fun NegativeTestNotice(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.chain_negative_test_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * Empty state when no chain data is available.
 */
@Composable
private fun EmptyChainView(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.chain_not_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDate(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return DateTimeFormatter.formatDateShort(localDateTime.date)
}

// ============== PREVIEWS ==============

/**
 * Preview: Direct exposure chain (0 hops - just reporter and You)
 */
@Preview
@Composable
private fun ChainVisualizationDirectExposurePreview() {
    MaterialTheme {
        ChainVisualizationView(
            chainVisualization =
                ChainVisualization.createPreviewChain(
                    hopCount = 0,
                    directContactUsername = "Alex",
                ),
            stiType = "[\"CHLAMYDIA\"]",
        )
    }
}

/**
 * Preview: One hop chain (Reporter -> Intermediary -> You)
 */
@Preview
@Composable
private fun ChainVisualizationOneHopPreview() {
    MaterialTheme {
        ChainVisualizationView(
            chainVisualization =
                ChainVisualization.createPreviewChain(
                    hopCount = 1,
                    directContactUsername = "Jordan",
                ),
            stiType = "[\"GONORRHEA\"]",
        )
    }
}

/**
 * Preview: Two hops chain with multiple STIs
 */
@Preview
@Composable
private fun ChainVisualizationTwoHopsPreview() {
    MaterialTheme {
        ChainVisualizationView(
            chainVisualization =
                ChainVisualization.createPreviewChain(
                    hopCount = 2,
                    directContactUsername = "Taylor",
                ),
            stiType = "[\"HIV\", \"SYPHILIS\"]",
        )
    }
}

/**
 * Preview: Three hops chain
 */
@Preview
@Composable
private fun ChainVisualizationThreeHopsPreview() {
    MaterialTheme {
        ChainVisualizationView(
            chainVisualization =
                ChainVisualization.createPreviewChain(
                    hopCount = 3,
                    directContactUsername = "Morgan",
                ),
            stiType = "[\"SYPHILIS\"]",
        )
    }
}

/**
 * Preview: Five hops chain (longer chain)
 */
@Preview
@Composable
private fun ChainVisualizationFiveHopsPreview() {
    MaterialTheme {
        ChainVisualizationView(
            chainVisualization =
                ChainVisualization.createPreviewChain(
                    hopCount = 5,
                    directContactUsername = "Casey",
                ),
            stiType = "[\"HERPES\"]",
        )
    }
}

/**
 * Preview: Maximum depth chain (10 hops)
 */
@Preview
@Composable
private fun ChainVisualizationMaxDepthPreview() {
    MaterialTheme {
        ChainVisualizationView(
            chainVisualization =
                ChainVisualization.createPreviewChain(
                    hopCount = ChainVisualization.MAX_CHAIN_DEPTH,
                    directContactUsername = "Your Contact",
                ),
            stiType = "[\"HPV\"]",
        )
    }
}

/**
 * Preview: Chain with negative test result (risk reduction notice)
 */
@Preview
@Composable
private fun ChainVisualizationWithNegativeTestPreview() {
    val now = currentTimeMillis()
    val dayInMillis = 24 * 60 * 60 * 1000L
    MaterialTheme {
        ChainVisualizationView(
            chainVisualization =
                ChainVisualization(
                    nodes =
                        listOf(
                            ChainNode(
                                username = "@@chain_someone@@",
                                testStatus = TestStatus.POSITIVE,
                                date = now - 7 * dayInMillis,
                            ),
                            ChainNode(
                                username = "Alex",
                                testStatus = TestStatus.NEGATIVE, // Tested negative!
                                date = now - 5 * dayInMillis,
                            ),
                            ChainNode(
                                username = "Jordan",
                                testStatus = TestStatus.UNKNOWN,
                                date = now - 3 * dayInMillis,
                            ),
                            ChainNode(
                                username = "You",
                                testStatus = TestStatus.UNKNOWN,
                                date = now,
                                isCurrentUser = true,
                            ),
                        ),
                ),
            stiType = "[\"CHLAMYDIA\"]",
        )
    }
}

/**
 * Preview: Empty chain (no data)
 */
@Preview
@Composable
private fun ChainVisualizationEmptyPreview() {
    MaterialTheme {
        ChainVisualizationView(
            chainVisualization = ChainVisualization(emptyList()),
        )
    }
}
