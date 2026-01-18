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
 * Generates the Play Store Developer Page Header graphic at 4096x2304 pixels.
 * Uses Roborazzi with Robolectric for JVM-based screenshot generation.
 *
 * Run with: ./gradlew generateDeveloperHeader
 * Output: src/test/snapshots/images/developer_header.jpg (JPEG, <1MB)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = app.justfyi.TestApplication::class)
class DeveloperHeaderGraphicTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun developerHeaderGraphic() {
        // Set device qualifiers for 4096x2304 landscape
        RuntimeEnvironment.setQualifiers("w4096dp-h2304dp-land-mdpi")

        composeTestRule.setContent {
            JustFyiTheme(themePreference = "light") {
                DeveloperHeaderGraphic()
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/snapshots/images/developer_header.png")
    }
}
