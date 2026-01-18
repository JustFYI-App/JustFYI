package app.justfyi.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brand colors from app icon for gradient background.
 * Primary: #6370D8 (darker purple/blue from icon gradient) - Top of gradient
 * Primary Light: #8E97DF (lighter purple from icon gradient) - Bottom of gradient
 */
object MarketingColors {
    val GradientTop = Color(0xFF6370D8)
    val GradientBottom = Color(0xFF8E97DF)
    val TextColor = Color.White
}

/**
 * Fixed dimensions for Play Store screenshots.
 * Standard portrait phone size: 1080x1920 pixels at xxhdpi (3x density)
 */
object ScreenshotDimensions {
    const val WIDTH_PX = 1080
    const val HEIGHT_PX = 1920

    // DP values for xxhdpi (3x density): 1080/3 = 360, 1920/3 = 640
    const val WIDTH_DP = 360
    const val HEIGHT_DP = 640

    /**
     * Convert pixel dimensions to Dp based on current density.
     */
    @Composable
    fun widthDp(): Dp {
        val density = LocalDensity.current
        return with(density) { WIDTH_PX.toDp() }
    }

    @Composable
    fun heightDp(): Dp {
        val density = LocalDensity.current
        return with(density) { HEIGHT_PX.toDp() }
    }
}

/**
 * Marketing screenshot frame that wraps app screen content with a gradient background
 * and marketing message text at the top.
 *
 * This composable creates a professional marketing-style screenshot for Play Store listings.
 * It features:
 * - Vertical gradient background from brand primary to brand primary light
 * - Marketing text positioned at the top with proper padding
 * - App screenshot content centered below the text with rounded corners
 *
 * @param content The app screen content to display (Composable lambda)
 * @param marketingMessage The marketing text to display at the top of the frame
 * @param languageCode The language code for potential locale-specific rendering (e.g., "en", "de")
 * @param modifier Optional modifier for the root container
 */
@Composable
fun MarketingScreenshotFrame(
    content: @Composable () -> Unit,
    marketingMessage: String,
    languageCode: String,
    modifier: Modifier = Modifier,
) {
    // Use fixed dimensions for Play Store screenshots
    val frameWidth = ScreenshotDimensions.widthDp()
    val frameHeight = ScreenshotDimensions.heightDp()

    // Vertical gradient from brand primary (top) to brand primary light (bottom)
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MarketingColors.GradientTop,
            MarketingColors.GradientBottom,
        ),
    )

    Box(
        modifier = modifier
            .width(frameWidth)
            .height(frameHeight)
            .background(brush = gradientBrush),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Marketing text section at the top
            MarketingTextSection(
                message = marketingMessage,
                modifier = Modifier.fillMaxWidth(),
            )

            // Screenshot content area with rounded corners
            ScreenshotContentArea(
                content = content,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
            )
        }
    }
}

/**
 * Marketing text section displayed at the top of the frame.
 * Features white bold text centered horizontally with appropriate padding.
 *
 * @param message The marketing message to display
 * @param modifier Modifier for the text section container
 */
@Composable
private fun MarketingTextSection(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(top = 100.dp, bottom = 40.dp)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MarketingColors.TextColor,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
        )
    }
}

/**
 * Screenshot content area with rounded corners for polish.
 * Centers the app screenshot content and clips it to rounded corners.
 *
 * @param content The app screen content composable
 * @param modifier Modifier for the content area container
 */
@Composable
private fun ScreenshotContentArea(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        // Clip the content to rounded corners
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp)),
        ) {
            content()
        }
    }
}
