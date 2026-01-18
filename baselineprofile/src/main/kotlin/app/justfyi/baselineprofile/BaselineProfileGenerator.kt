package app.justfyi.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline profile generator for Just FYI app.
 *
 * Generates baseline profiles to optimize app startup performance by pre-compiling
 * critical code paths, reducing Time-to-Initial-Display (TTID) through Ahead-of-Time
 * (AOT) compilation.
 *
 * Run with: ./gradlew :androidApp:generateReleaseBaselineProfile
 *
 * Requirements:
 * - Android 13+ (API 33+) physical device OR rooted device
 * - USB debugging enabled
 * - Device connected and recognized by `adb devices`
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    private val packageName = "app.justfyi"

    /**
     * Generates startup profile for new user path.
     * Profiles cold start to onboarding screen (for users who haven't completed onboarding).
     */
    @Test
    fun generateNewUserStartupProfile() {
        rule.collect(
            packageName = packageName,
            includeInStartupProfile = true,
        ) {
            // Start the app - for new users, this will show the onboarding screen
            startActivityAndWait()

            // Wait for the UI to settle
            device.waitForIdle()

            // Wait for onboarding content to load
            device.wait(Until.hasObject(By.textContains("Welcome")), 5000)
            device.waitForIdle()
        }
    }

    /**
     * Generates startup profile for returning user path.
     * Profiles cold start to Home screen (for users who have completed onboarding).
     */
    @Test
    fun generateReturningUserStartupProfile() {
        rule.collect(
            packageName = packageName,
            includeInStartupProfile = true,
        ) {
            // Start the app - for returning users, this will show the Home screen
            startActivityAndWait()

            // Wait for the UI to settle
            device.waitForIdle()

            // Wait for home screen content to potentially load
            device.wait(Until.hasObject(By.res(packageName, "home_screen")), 5000)
            device.waitForIdle()
        }
    }

    /**
     * Generates navigation profile for main screens.
     * Profiles navigation between Home, Profile, InteractionHistory, NotificationList, and Settings.
     */
    @Test
    fun generateNavigationProfile() {
        rule.collect(
            packageName = packageName,
            includeInStartupProfile = false,
        ) {
            // Start the app
            startActivityAndWait()
            device.waitForIdle()

            // Navigate to Profile screen
            val profileButton =
                device.findObject(By.desc("Profile"))
                    ?: device.findObject(By.text("Profile"))
            profileButton?.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.textContains("ID")), 3000)

            // Navigate to Interaction History screen
            device.pressBack()
            device.waitForIdle()
            val historyButton =
                device.findObject(By.desc("Interaction History"))
                    ?: device.findObject(By.text("History"))
                    ?: device.findObject(By.textContains("History"))
            historyButton?.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.textContains("Interaction")), 3000)

            // Navigate to Notification List screen
            device.pressBack()
            device.waitForIdle()
            val notificationButton =
                device.findObject(By.desc("Notifications"))
                    ?: device.findObject(By.text("Notifications"))
            notificationButton?.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.textContains("Notification")), 3000)

            // Navigate to Settings screen
            device.pressBack()
            device.waitForIdle()
            val settingsButton =
                device.findObject(By.desc("Settings"))
                    ?: device.findObject(By.text("Settings"))
            settingsButton?.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.textContains("Settings")), 3000)
        }
    }

    /**
     * Generates scrolling profile for list screens.
     * Profiles scrolling in InteractionHistory and NotificationList LazyColumns.
     */
    @Test
    fun generateScrollingProfile() {
        rule.collect(
            packageName = packageName,
            includeInStartupProfile = false,
        ) {
            // Start the app
            startActivityAndWait()
            device.waitForIdle()

            // Navigate to Interaction History screen
            val historyButton =
                device.findObject(By.desc("Interaction History"))
                    ?: device.findObject(By.text("History"))
                    ?: device.findObject(By.textContains("History"))
            historyButton?.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.textContains("Interaction")), 3000)

            // Find and scroll the list
            val interactionList = device.findObject(By.scrollable(true))
            interactionList?.let {
                it.fling(Direction.DOWN)
                device.waitForIdle()
                it.fling(Direction.UP)
                device.waitForIdle()
            }

            // Navigate to Notification List screen
            device.pressBack()
            device.waitForIdle()
            val notificationButton =
                device.findObject(By.desc("Notifications"))
                    ?: device.findObject(By.text("Notifications"))
            notificationButton?.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.textContains("Notification")), 3000)

            // Find and scroll the notification list
            val notificationList = device.findObject(By.scrollable(true))
            notificationList?.let {
                it.fling(Direction.DOWN)
                device.waitForIdle()
                it.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }

    /**
     * Generates onboarding flow profile.
     * Profiles all 4 onboarding steps: Welcome, IdBackup, Permissions, and Username.
     */
    @Test
    fun generateOnboardingProfile() {
        rule.collect(
            packageName = packageName,
            includeInStartupProfile = false,
        ) {
            // Start the app
            startActivityAndWait()
            device.waitForIdle()

            // Wait for Welcome step to load
            device.wait(Until.hasObject(By.textContains("Welcome")), 5000)
            device.waitForIdle()

            // Step 1: Welcome - Click "Start" or "Create New Identity" button
            val startButton =
                device.findObject(By.text("Create New Identity"))
                    ?: device.findObject(By.text("Start"))
                    ?: device.findObject(By.textContains("Start"))
            startButton?.click()
            device.waitForIdle()

            // Wait for ID generation and transition
            Thread.sleep(1000)
            device.waitForIdle()

            // Step 2: IdBackup - Wait for ID to render, then proceed
            device.wait(Until.hasObject(By.textContains("Backup")), 3000)
                ?: device.wait(Until.hasObject(By.textContains("ID")), 3000)
            device.waitForIdle()

            // Toggle reveal ID to exercise that code path
            val revealButton =
                device.findObject(By.desc("Reveal ID"))
                    ?: device.findObject(By.text("Show"))
                    ?: device.findObject(By.textContains("Reveal"))
            revealButton?.click()
            device.waitForIdle()

            // Confirm backup checkbox
            val confirmCheckbox =
                device.findObject(By.textContains("backed up"))
                    ?: device.findObject(By.checkable(true))
            confirmCheckbox?.click()
            device.waitForIdle()

            // Click Next to go to Step 3
            val nextButton1 =
                device.findObject(By.text("Next"))
                    ?: device.findObject(By.desc("Next"))
            nextButton1?.click()
            device.waitForIdle()

            // Step 3: Permissions - Wait for permissions step
            device.wait(Until.hasObject(By.textContains("Permission")), 3000)
            device.waitForIdle()

            // Try to enable Bluetooth permission (button might show dialog)
            val bluetoothButton = device.findObject(By.textContains("Bluetooth"))
            bluetoothButton?.click()
            device.waitForIdle()
            Thread.sleep(500)

            // Try notification permission
            val notificationPermButton = device.findObject(By.textContains("Notification"))
            notificationPermButton?.click()
            device.waitForIdle()
            Thread.sleep(500)

            // Click Next or Skip to go to Step 4
            val nextButton2 =
                device.findObject(By.text("Next"))
                    ?: device.findObject(By.text("Skip"))
                    ?: device.findObject(By.desc("Next"))
            nextButton2?.click()
            device.waitForIdle()

            // Step 4: Username - Wait for username step
            device.wait(Until.hasObject(By.textContains("Username")), 3000)
                ?: device.wait(Until.hasObject(By.textContains("name")), 3000)
            device.waitForIdle()

            // Try to interact with text field
            val usernameField =
                device.findObject(By.clazz("android.widget.EditText"))
                    ?: device.findObject(By.text("Enter username"))
            usernameField?.click()
            device.waitForIdle()

            // Complete the flow
            val completeButton =
                device.findObject(By.text("Complete"))
                    ?: device.findObject(By.text("Finish"))
                    ?: device.findObject(By.text("Skip"))
            completeButton?.click()
            device.waitForIdle()
        }
    }
}
