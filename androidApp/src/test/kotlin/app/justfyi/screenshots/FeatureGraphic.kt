package app.justfyi.screenshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.justfyi.R

// Brand colors
private val JustFyiPrimary = Color(0xFF6370D8)

// Light background that contrasts with the purple icon
private val BackgroundColor = Color(0xFFF5F5F7)

object FeatureGraphicDimensions {
    const val WIDTH_PX = 1024
    const val HEIGHT_PX = 500
}

/**
 * Feature Graphic for Play Store listing.
 * 1024x500 pixels with app icon, name, and tagline.
 */
@Composable
fun FeatureGraphic() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon from resources
            // The adaptive icon foreground has ~33% padding built-in for the safe zone,
            // so we use a larger size to compensate
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "JustFYI App Icon",
                modifier = Modifier.size(280.dp),
                contentScale = ContentScale.Fit
            )

            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "JustFYI",
                    color = JustFyiPrimary,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Stay Safe. Stay Private.",
                    color = Color.DarkGray,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
