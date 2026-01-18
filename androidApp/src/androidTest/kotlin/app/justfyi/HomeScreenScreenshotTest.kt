package app.justfyi

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import app.justfyi.domain.model.Interaction
import app.justfyi.domain.model.NearbyUser
import app.justfyi.domain.model.Notification
import app.justfyi.presentation.components.JustFyiTopAppBar
import app.justfyi.presentation.feature.home.HomeUiState
import app.justfyi.presentation.feature.profile.ProfileUiState
import app.justfyi.presentation.screens.HomeContent
import app.justfyi.presentation.screens.InteractionDisplayItem
import app.justfyi.presentation.screens.InteractionHistoryContent
import app.justfyi.presentation.screens.InteractionHistoryTopBar
import app.justfyi.presentation.screens.NotificationListContent
import app.justfyi.presentation.screens.NotificationListTopBar
import app.justfyi.presentation.screens.ProfileContent
import app.justfyi.presentation.screens.ProfileTopBar
import app.justfyi.presentation.screens.formatDateHeader
import app.justfyi.presentation.screens.formatInteractionDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale

/**
 * Screenshot test for all app screens in all supported languages and themes.
 * Captures the full screen including navigation bar, exactly as it appears on device.
 *
 * Output structure (ready for website/public/screenshots/):
 * - {theme}/{language_code}/1.webp - Scanning state
 * - {theme}/{language_code}/2.webp - 3 users detected state
 * - {theme}/{language_code}/3.webp - Profile screen
 * - {theme}/{language_code}/4.webp - Notification list (herpes exposure)
 * - {theme}/{language_code}/5.webp - Interaction history (10 people)
 *
 * Themes: light, dark
 * Supported languages: English (en), German (de), Spanish (es), French (fr), Portuguese (pt)
 *
 * Run with: ./gradlew :androidApp:connectedAndroidTest
 * Screenshots are saved to device Pictures folder via MediaStore.
 */
@RunWith(Parameterized::class)
class HomeScreenScreenshotTest(
    private val languageCode: String,
    private val themeMode: String,
    private val locale: Locale,
    private val names: List<String>,
    private val screenshotNumber: Int,
    private val stateName: String,
) {
    companion object {
        private val now = System.currentTimeMillis()
        private const val DAY_IN_MS = 24 * 60 * 60 * 1000L

        // Extended name lists (10 per language for interaction history)
        private val languageData =
            mapOf(
                "en" to
                    Pair(
                        Locale.ENGLISH,
                        listOf(
                            "Alice",
                            "Bob",
                            "Charlie",
                            "Diana",
                            "Edward",
                            "Fiona",
                            "George",
                            "Hannah",
                            "Ivan",
                            "Julia",
                        ),
                    ),
                "de" to
                    Pair(
                        Locale.GERMAN,
                        listOf(
                            "Hans",
                            "Anna",
                            "Klaus",
                            "Maria",
                            "Fritz",
                            "Greta",
                            "Otto",
                            "Liesel",
                            "Wolfgang",
                            "Ingrid",
                        ),
                    ),
                "es" to
                    Pair(
                        Locale.forLanguageTag("es"),
                        listOf(
                            "Maria",
                            "Carlos",
                            "Elena",
                            "Pablo",
                            "Sofia",
                            "Miguel",
                            "Lucia",
                            "Fernando",
                            "Carmen",
                            "Diego",
                        ),
                    ),
                "fr" to
                    Pair(
                        Locale.FRENCH,
                        listOf(
                            "Pierre",
                            "Marie",
                            "Jean",
                            "Sophie",
                            "Louis",
                            "Camille",
                            "Nicolas",
                            "Amelie",
                            "Thomas",
                            "Claire",
                        ),
                    ),
                "pt" to
                    Pair(
                        Locale.forLanguageTag("pt"),
                        listOf(
                            "Joao",
                            "Ana",
                            "Pedro",
                            "Beatriz",
                            "Lucas",
                            "Mariana",
                            "Rafael",
                            "Carolina",
                            "Tiago",
                            "Isabel",
                        ),
                    ),
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}_{1}_{5}")
        fun data(): Collection<Array<Any>> {
            val testCases = mutableListOf<Array<Any>>()

            for ((code, data) in languageData) {
                val (locale, names) = data
                for (theme in listOf("light", "dark")) {
                    // Screenshot 1: Scanning state
                    testCases.add(arrayOf(code, theme, locale, names, 1, "scanning"))
                    // Screenshot 2: 3 users detected
                    testCases.add(arrayOf(code, theme, locale, names, 2, "users_detected"))
                    // Screenshot 3: Profile
                    testCases.add(arrayOf(code, theme, locale, names, 3, "profile"))
                    // Screenshot 4: Notification (herpes exposure)
                    testCases.add(arrayOf(code, theme, locale, names, 4, "notification"))
                    // Screenshot 5: Interaction history (10 people)
                    testCases.add(arrayOf(code, theme, locale, names, 5, "history"))
                }
            }

            return testCases
        }
    }

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun captureScreenshot() {
        // Set the JVM default locale - this affects Compose Multiplatform resources
        Locale.setDefault(locale)

        // Create a properly localized context
        val localizedContext = createLocalizedContext(locale)
        val localizedConfig = localizedContext.resources.configuration

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig,
            ) {
                JustFyiTheme(themePreference = themeMode) {
                    when (stateName) {
                        "scanning" ->
                            HomeScreenForScreenshot(
                                state = createScanningState(),
                                unreadNotificationCount = 0,
                            )
                        "users_detected" ->
                            HomeScreenForScreenshot(
                                state = createThreeUsersState(names.take(3)),
                                unreadNotificationCount = 0,
                            )
                        "profile" ->
                            ProfileScreenForScreenshot(
                                state = createProfileState(names[0]),
                            )
                        "notification" ->
                            NotificationListScreenForScreenshot(
                                notifications = createNotificationState(),
                            )
                        "history" ->
                            InteractionHistoryScreenForScreenshot(
                                interactions = createInteractionState(names),
                            )
                        else -> throw IllegalArgumentException("Unknown state: $stateName")
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Capture and save the screenshot
        val bitmap = composeTestRule.onRoot().captureToImage().asAndroidBitmap()
        saveScreenshotViaMediaStore(bitmap, themeMode, languageCode, screenshotNumber)
    }

    // ============== State Creation Functions ==============

    private fun createScanningState(): HomeUiState.Success =
        HomeUiState.Success(
            nearbyUsers = emptyList(),
            selectedUsers = emptySet(),
            unreadNotificationCount = 0,
            isRecording = false,
            recordingSuccess = false,
            lastRecordedCount = 0,
        )

    private fun createThreeUsersState(names: List<String>): HomeUiState.Success =
        HomeUiState.Success(
            nearbyUsers =
                listOf(
                    NearbyUser(
                        anonymousIdHash = "hash1",
                        username = names[0],
                        signalStrength = -45, // Excellent signal
                        lastSeen = now,
                    ),
                    NearbyUser(
                        anonymousIdHash = "hash2",
                        username = names[1],
                        signalStrength = -65, // Good signal
                        lastSeen = now,
                    ),
                    NearbyUser(
                        anonymousIdHash = "hash3",
                        username = names[2],
                        signalStrength = -80, // Fair signal
                        lastSeen = now,
                    ),
                ),
            selectedUsers = setOf("hash1"), // First user selected for interaction
            unreadNotificationCount = 0,
            isRecording = false,
            recordingSuccess = false,
            lastRecordedCount = 0,
        )

    private fun createProfileState(username: String): ProfileUiState.Success =
        ProfileUiState.Success(
            anonymousId = "A1B2C3D4E5F6G7H8I9J0K1L2",
            formattedId = "A1B2-C3D4-E5F6-G7H8-I9J0-K1L2",
            isIdRevealed = true,
            username = username,
            isUpdatingUsername = false,
            isRecovering = false,
            showCopiedMessage = false,
            showUsernameUpdated = false,
            showRecoverySuccess = false,
        )

    private fun createNotificationState(): List<Notification> =
        listOf(
            Notification(
                id = "notif1",
                type = "EXPOSURE",
                stiType = "Herpes",
                exposureDate = now - (7 * DAY_IN_MS), // 7 days ago
                chainData = "{}",
                isRead = false,
                receivedAt = now - (2 * 60 * 60 * 1000L), // 2 hours ago
                updatedAt = now,
            ),
        )

    private fun createInteractionState(names: List<String>): List<Interaction> =
        names.mapIndexed { index, name ->
            Interaction(
                id = "interaction_$index",
                partnerAnonymousId = "hash_$index",
                partnerUsernameSnapshot = name,
                // Stagger over several days for realistic grouping
                recordedAt = now - (index * DAY_IN_MS / 2),
                syncedToCloud = true,
            )
        }

    // ============== Helper Functions ==============

    @Suppress("DEPRECATION")
    private fun createLocalizedContext(locale: Locale): Context {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)

        // Also update the resources configuration directly
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)

        return baseContext.createConfigurationContext(config)
    }

    private fun saveScreenshotViaMediaStore(
        bitmap: Bitmap,
        themeMode: String,
        languageCode: String,
        screenshotNumber: Int,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver

        // Organize by folder: screenshots/{theme}/{lang}/{number}.webp
        val filename = "$screenshotNumber.webp"
        val relativePath = "Pictures/screenshots/$themeMode/$languageCode"

        val contentValues =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

        val uri =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException(
                    "Failed to create MediaStore entry for $themeMode/$languageCode/$filename",
                )

        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, outputStream)
        } ?: throw IllegalStateException("Failed to open output stream for $themeMode/$languageCode/$filename")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        println("Screenshot saved: $relativePath/$filename")
    }
}

// ============== Screen Composables for Screenshots ==============

/**
 * Full HomeScreen layout for screenshots, matching the real app appearance.
 * Includes the top app bar with notification and profile icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenForScreenshot(
    state: HomeUiState.Success,
    unreadNotificationCount: Int,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            JustFyiTopAppBar(
                title = "Just FYI",
                actions = {
                    IconButton(onClick = { }) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationCount > 0) {
                                    Badge {
                                        Text(
                                            text =
                                                if (unreadNotificationCount >
                                                    99
                                                ) {
                                                    "99+"
                                                } else {
                                                    unreadNotificationCount.toString()
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                            )
                        }
                    }
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        HomeContent(
            state = state,
            bluetoothPermissionGranted = true,
            modifier = Modifier.padding(paddingValues),
            onRequestBluetoothPermission = {},
            onStartScan = {},
            onUserClick = {},
            onRecordClick = {},
            onReportPositiveTest = {},
        )
    }
}

/**
 * Profile screen layout for screenshots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreenForScreenshot(state: ProfileUiState.Success) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { ProfileTopBar() },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            ProfileContent(
                state = state,
                warningText = "Save your Anonymous ID! You'll need it to recover your account.",
                onEditUsernameClick = {},
                onToggleIdReveal = {},
                onCopyIdClick = {},
                onHistoryClick = {},
                onSubmittedReportsClick = {},
                onSettingsClick = {},
                onRecoverClick = {},
            )
        }
    }
}

/**
 * Notification list screen layout for screenshots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationListScreenForScreenshot(notifications: List<Notification>) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { NotificationListTopBar() },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NotificationListContent(
                notifications = notifications,
                onNotificationClick = {},
            )
        }
    }
}

/**
 * Interaction history screen layout for screenshots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InteractionHistoryScreenForScreenshot(interactions: List<Interaction>) {
    // Build grouped interactions with display data
    val displayItems =
        interactions.map { interaction ->
            InteractionDisplayItem(
                interaction = interaction,
                dateTime = formatInteractionDateTime(interaction.recordedAt),
                daysUntilExpiry = 60, // Fixed for screenshots
            )
        }
    val groupedInteractions = displayItems.groupBy { formatDateHeader(it.interaction.recordedAt) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { InteractionHistoryTopBar() },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            InteractionHistoryContent(
                groupedInteractions = groupedInteractions,
                onInteractionClick = {},
            )
        }
    }
}
