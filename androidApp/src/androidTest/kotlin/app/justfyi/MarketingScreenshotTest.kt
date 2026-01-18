package app.justfyi

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Marketing screenshot test - generates Play Store screenshots with marketing frames.
 *
 * Output: 25 PNG screenshots (5 screens x 5 languages) at 1080x1920
 * Location: Pictures/marketing-screenshots/{language_code}/{number}.png
 */
@RunWith(Parameterized::class)
class MarketingScreenshotTest(
    private val languageCode: String,
    private val locale: Locale,
    private val names: List<String>,
    private val screenshotNumber: Int,
    private val screenType: String,
) {
    companion object {
        private val now = System.currentTimeMillis()
        private const val DAY_IN_MS = 24 * 60 * 60 * 1000L

        private val languageData = mapOf(
            "en" to Pair(
                Locale.ENGLISH,
                listOf("Alice", "Bob", "Charlie", "Diana", "Edward", "Fiona", "George", "Hannah", "Ivan", "Julia")
            ),
            "de" to Pair(
                Locale.GERMAN,
                listOf("Hans", "Anna", "Klaus", "Maria", "Fritz", "Greta", "Otto", "Liesel", "Wolfgang", "Ingrid")
            ),
            "es" to Pair(
                Locale.forLanguageTag("es"),
                listOf("Maria", "Carlos", "Elena", "Pablo", "Sofia", "Miguel", "Lucia", "Fernando", "Carmen", "Diego")
            ),
            "fr" to Pair(
                Locale.FRENCH,
                listOf("Pierre", "Marie", "Jean", "Sophie", "Louis", "Camille", "Nicolas", "Amelie", "Thomas", "Claire")
            ),
            "pt" to Pair(
                Locale.forLanguageTag("pt"),
                listOf("Joao", "Ana", "Pedro", "Beatriz", "Lucas", "Mariana", "Rafael", "Carolina", "Tiago", "Isabel")
            )
        )

        // Marketing messages in all languages
        private val marketingMessages = mapOf(
            "en" to mapOf(
                "scanning" to "Stay anonymous. Stay safe.",
                "users_detected" to "Connect with people around you",
                "profile" to "Your identity stays yours",
                "notification" to "Get notified. Take action.",
                "history" to "Track your encounters privately"
            ),
            "de" to mapOf(
                "scanning" to "Bleib anonym. Bleib sicher.",
                "users_detected" to "Verbinde dich mit Menschen um dich",
                "profile" to "Deine Identität bleibt deine",
                "notification" to "Werde informiert. Handle sofort.",
                "history" to "Verfolge deine Kontakte privat"
            ),
            "es" to mapOf(
                "scanning" to "Mantente anónimo. Mantente seguro.",
                "users_detected" to "Conecta con personas a tu alrededor",
                "profile" to "Tu identidad sigue siendo tuya",
                "notification" to "Recibe alertas. Actúa ya.",
                "history" to "Rastrea tus encuentros en privado"
            ),
            "fr" to mapOf(
                "scanning" to "Restez anonyme. Restez protégé.",
                "users_detected" to "Connectez-vous avec ceux qui vous entourent",
                "profile" to "Votre identité reste la vôtre",
                "notification" to "Soyez alerté. Agissez vite.",
                "history" to "Suivez vos rencontres en toute discrétion"
            ),
            "pt" to mapOf(
                "scanning" to "Fique anônimo. Fique seguro.",
                "users_detected" to "Conecte-se com pessoas ao seu redor",
                "profile" to "Sua identidade continua sua",
                "notification" to "Seja notificado. Tome uma atitude.",
                "history" to "Acompanhe seus encontros com privacidade"
            )
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}_{4}")
        fun data(): Collection<Array<Any>> {
            val testCases = mutableListOf<Array<Any>>()
            val screenTypes = listOf("scanning", "users_detected", "profile", "notification", "history")

            for ((code, data) in languageData) {
                val (locale, names) = data
                screenTypes.forEachIndexed { index, screenType ->
                    testCases.add(arrayOf(code, locale, names, index + 1, screenType))
                }
            }
            return testCases
        }
    }

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun captureMarketingScreenshot() {
        Locale.setDefault(locale)
        val localizedContext = createLocalizedContext(locale)
        val localizedConfig = localizedContext.resources.configuration
        val message = marketingMessages[languageCode]?.get(screenType) ?: marketingMessages["en"]!![screenType]!!

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig,
            ) {
                JustFyiTheme(themePreference = "light") {
                    MarketingFrame(
                        marketingMessage = message,
                        content = {
                            when (screenType) {
                                "scanning" -> HomeScreenContent(createScanningState(), 0)
                                "users_detected" -> HomeScreenContent(createThreeUsersState(names.take(3)), 0)
                                "profile" -> ProfileScreenContent(createProfileState(names[0]))
                                "notification" -> NotificationScreenContent(createNotificationState())
                                "history" -> HistoryScreenContent(createInteractionState(names))
                            }
                        }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val bitmap = composeTestRule.onRoot().captureToImage().asAndroidBitmap()
        saveScreenshot(bitmap, languageCode, screenshotNumber)
    }

    // ============== State Creation ==============

    private fun createScanningState() = HomeUiState.Success(
        nearbyUsers = emptyList(),
        selectedUsers = emptySet(),
        unreadNotificationCount = 0,
        isRecording = false,
        recordingSuccess = false,
        lastRecordedCount = 0
    )

    private fun createThreeUsersState(names: List<String>) = HomeUiState.Success(
        nearbyUsers = listOf(
            NearbyUser("hash1", names[0], -45, now),
            NearbyUser("hash2", names[1], -65, now),
            NearbyUser("hash3", names[2], -80, now)
        ),
        selectedUsers = setOf("hash1"),
        unreadNotificationCount = 0,
        isRecording = false,
        recordingSuccess = false,
        lastRecordedCount = 0
    )

    private fun createProfileState(username: String) = ProfileUiState.Success(
        anonymousId = "A1B2C3D4E5F6G7H8I9J0K1L2",
        formattedId = "A1B2-C3D4-E5F6-G7H8-I9J0-K1L2",
        isIdRevealed = true,
        username = username,
        isUpdatingUsername = false,
        isRecovering = false,
        showCopiedMessage = false,
        showUsernameUpdated = false,
        showRecoverySuccess = false
    )

    private fun createNotificationState() = listOf(
        Notification(
            id = "notif1",
            type = "EXPOSURE",
            stiType = "Herpes",
            exposureDate = now - (7 * DAY_IN_MS),
            chainData = "{}",
            isRead = false,
            receivedAt = now - (2 * 60 * 60 * 1000L),
            updatedAt = now
        )
    )

    private fun createInteractionState(names: List<String>) = names.mapIndexed { index, name ->
        Interaction(
            id = "interaction_$index",
            partnerAnonymousId = "hash_$index",
            partnerUsernameSnapshot = name,
            recordedAt = now - (index * DAY_IN_MS / 2),
            syncedToCloud = true
        )
    }

    // ============== Helpers ==============

    @Suppress("DEPRECATION")
    private fun createLocalizedContext(locale: Locale): Context {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
        return baseContext.createConfigurationContext(config)
    }

    private fun saveScreenshot(bitmap: Bitmap, languageCode: String, screenshotNumber: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver

        val filename = "$screenshotNumber.png"
        val relativePath = "Pictures/marketing-screenshots/$languageCode"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        println("Marketing screenshot saved: $relativePath/$filename")
    }
}

// ============== Marketing Frame Composable ==============

private val GradientTop = Color(0xFF6370D8)
private val GradientBottom = Color(0xFF8E97DF)

@Composable
private fun MarketingFrame(
    marketingMessage: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GradientTop, GradientBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Marketing text at top with proper padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = marketingMessage,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2
                )
            }

            // App screenshot content with rounded corners
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                content()
            }
        }
    }
}

// ============== Screen Content Composables ==============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(state: HomeUiState.Success, unreadCount: Int) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            JustFyiTopAppBar(
                title = "Just FYI",
                actions = {
                    IconButton(onClick = { }) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                        }
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Person, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        HomeContent(
            state = state,
            bluetoothPermissionGranted = true,
            modifier = Modifier.padding(padding),
            onRequestBluetoothPermission = {},
            onStartScan = {},
            onUserClick = {},
            onRecordClick = {},
            onReportPositiveTest = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreenContent(state: ProfileUiState.Success) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { ProfileTopBar() }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            ProfileContent(
                state = state,
                warningText = "Save your Anonymous ID!",
                onEditUsernameClick = {},
                onToggleIdReveal = {},
                onCopyIdClick = {},
                onHistoryClick = {},
                onSubmittedReportsClick = {},
                onSettingsClick = {},
                onRecoverClick = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationScreenContent(notifications: List<Notification>) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { NotificationListTopBar() }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NotificationListContent(notifications = notifications, onNotificationClick = {})
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreenContent(interactions: List<Interaction>) {
    val displayItems = interactions.map { interaction ->
        InteractionDisplayItem(
            interaction = interaction,
            dateTime = formatInteractionDateTime(interaction.recordedAt),
            daysUntilExpiry = 60
        )
    }
    val grouped = displayItems.groupBy { formatDateHeader(it.interaction.recordedAt) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { InteractionHistoryTopBar() }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            InteractionHistoryContent(groupedInteractions = grouped, onInteractionClick = {})
        }
    }
}
