package app.justfyi.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.justfyi.JustFyiTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Generates the Play Store feature graphic at 1024x500 pixels.
 * Uses Roborazzi with Robolectric for JVM-based screenshot generation.
 *
 * Run with: ./gradlew :androidApp:recordRoborazziDebug --tests "*.FeatureGraphicTest"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = app.justfyi.TestApplication::class)
class FeatureGraphicTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun featureGraphic() {
        // Set device qualifiers for 1024x500 landscape
        RuntimeEnvironment.setQualifiers("w1024dp-h500dp-land-mdpi")

        composeTestRule.setContent {
            JustFyiTheme(themePreference = "light") {
                FeatureGraphic()
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/snapshots/images/feature_graphic.png")
    }
}
