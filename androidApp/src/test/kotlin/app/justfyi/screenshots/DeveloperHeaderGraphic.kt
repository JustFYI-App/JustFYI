package app.justfyi.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.sqrt
import kotlin.random.Random

// Brand colors
private val JustFyiPrimary = Color(0xFF6370D8)
private val JustFyiSecondary = Color(0xFF8B95E8)
private val JustFyiTertiary = Color(0xFFB8BEF5)

// Gradient background colors
private val GradientStart = Color(0xFFF8F9FF)
private val GradientEnd = Color(0xFFE8EBFF)

object DeveloperHeaderDimensions {
    const val WIDTH_PX = 4096
    const val HEIGHT_PX = 2304
}

private data class Node(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float
)

/**
 * Developer Page Header Graphic for Play Store.
 * 4096x2304 pixels with soft nodes/dots connected by lines,
 * representing anonymous connections in the Just FYI network.
 */
@Composable
fun DeveloperHeaderGraphic() {
    val nodes = remember { generateNodes() }
    val connections = remember { generateConnections(nodes) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientEnd)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / DeveloperHeaderDimensions.WIDTH_PX
            val scaleY = size.height / DeveloperHeaderDimensions.HEIGHT_PX

            // Draw connections first (behind nodes)
            connections.forEach { (from, to, alpha) ->
                val startX = from.x * scaleX
                val startY = from.y * scaleY
                val endX = to.x * scaleX
                val endY = to.y * scaleY

                drawLine(
                    color = JustFyiSecondary.copy(alpha = alpha * 0.4f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3f * scaleX,
                    cap = StrokeCap.Round
                )
            }

            // Draw nodes
            nodes.forEach { node ->
                val centerX = node.x * scaleX
                val centerY = node.y * scaleY
                val radius = node.radius * scaleX

                // Outer glow
                drawCircle(
                    color = node.color.copy(alpha = node.alpha * 0.3f),
                    radius = radius * 1.8f,
                    center = Offset(centerX, centerY)
                )

                // Main node
                drawCircle(
                    color = node.color.copy(alpha = node.alpha * 0.7f),
                    radius = radius,
                    center = Offset(centerX, centerY)
                )

                // Inner highlight
                drawCircle(
                    color = Color.White.copy(alpha = node.alpha * 0.5f),
                    radius = radius * 0.4f,
                    center = Offset(centerX - radius * 0.2f, centerY - radius * 0.2f)
                )
            }
        }
    }
}

private fun generateNodes(): List<Node> {
    val random = Random(42) // Fixed seed for reproducibility
    val nodes = mutableListOf<Node>()
    val colors = listOf(JustFyiPrimary, JustFyiSecondary, JustFyiTertiary)

    // Generate nodes with some clustering for organic feel
    val clusterCenters = listOf(
        Offset(800f, 600f),
        Offset(2048f, 1152f),  // Center
        Offset(3300f, 700f),
        Offset(1200f, 1800f),
        Offset(2900f, 1700f),
        Offset(600f, 1200f),
        Offset(3500f, 1200f)
    )

    // Add clustered nodes
    clusterCenters.forEach { center ->
        val nodesInCluster = random.nextInt(8, 15)
        repeat(nodesInCluster) {
            val offsetX = random.nextFloat() * 600f - 300f
            val offsetY = random.nextFloat() * 400f - 200f
            nodes.add(
                Node(
                    x = (center.x + offsetX).coerceIn(100f, 3996f),
                    y = (center.y + offsetY).coerceIn(100f, 2204f),
                    radius = random.nextFloat() * 30f + 20f,
                    color = colors[random.nextInt(colors.size)],
                    alpha = random.nextFloat() * 0.4f + 0.5f
                )
            )
        }
    }

    // Add some scattered nodes for variety
    repeat(25) {
        nodes.add(
            Node(
                x = random.nextFloat() * 3800f + 148f,
                y = random.nextFloat() * 2100f + 102f,
                radius = random.nextFloat() * 25f + 15f,
                color = colors[random.nextInt(colors.size)],
                alpha = random.nextFloat() * 0.3f + 0.4f
            )
        )
    }

    return nodes
}

private fun generateConnections(nodes: List<Node>): List<Triple<Node, Node, Float>> {
    val connections = mutableListOf<Triple<Node, Node, Float>>()
    val maxDistance = 450f

    // Connect nearby nodes
    for (i in nodes.indices) {
        for (j in i + 1 until nodes.size) {
            val dx = nodes[i].x - nodes[j].x
            val dy = nodes[i].y - nodes[j].y
            val distance = sqrt(dx * dx + dy * dy)

            if (distance < maxDistance) {
                // Alpha based on distance (closer = more visible)
                val alpha = 1f - (distance / maxDistance)
                connections.add(Triple(nodes[i], nodes[j], alpha))
            }
        }
    }

    return connections
}
