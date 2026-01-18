package app.justfyi.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.justfyi.presentation.components.ErrorMessage
import app.justfyi.presentation.components.JustFyiSnackbarHost
import app.justfyi.presentation.components.JustFyiTopAppBar
import app.justfyi.presentation.components.LoadingIndicator
import app.justfyi.presentation.feature.licenses.LicenseDetailUiState
import app.justfyi.presentation.feature.licenses.LicenseDetailViewModel
import app.justfyi.presentation.navigation.NavigationActions
import justfyi.shared.generated.resources.*
import justfyi.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * License Detail screen composable.
 * Displays the full license information for a specific library.
 *
 * @param viewModel The LicenseDetailViewModel instance
 * @param navigationActions Navigation actions for screen transitions
 * @param modifier Modifier for the screen
 */
@Composable
fun LicenseDetailScreen(
    viewModel: LicenseDetailViewModel,
    navigationActions: NavigationActions,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(Res.string.licenses_error)

    // Get the title from the state
    val title =
        when (val state = uiState) {
            is LicenseDetailUiState.Success -> state.name
            else -> stringResource(Res.string.licenses_title)
        }

    // Handle errors
    LaunchedEffect(uiState) {
        when (uiState) {
            is LicenseDetailUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    duration = SnackbarDuration.Long,
                )
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            JustFyiTopAppBar(
                title = title,
                showNavigationIcon = true,
                onNavigationClick = { navigationActions.navigateBack() },
            )
        },
        snackbarHost = { JustFyiSnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is LicenseDetailUiState.Loading -> {
                    LoadingIndicator(message = stringResource(Res.string.licenses_loading))
                }

                is LicenseDetailUiState.Error -> {
                    ErrorMessage(
                        message = stringResource(Res.string.licenses_error),
                        onRetry = { viewModel.refresh() },
                    )
                }

                is LicenseDetailUiState.Success -> {
                    LicenseDetailContent(
                        name = state.name,
                        version = state.version,
                        author = state.author,
                        licenseType = state.licenseType,
                        licenseText = state.licenseText,
                        website = state.website,
                    )
                }
            }
        }
    }
}

/**
 * Content for the license detail screen.
 * Displays library information and full license text in a scrollable view.
 *
 * @param name The library name
 * @param version The library version
 * @param author The author or organization
 * @param licenseType The license type (e.g., Apache-2.0)
 * @param licenseText The full license text
 * @param website The library website URL
 */
@Composable
private fun LicenseDetailContent(
    name: String,
    version: String?,
    author: String?,
    licenseType: String?,
    licenseText: String?,
    website: String?,
) {
    val uriHandler = LocalUriHandler.current
    val versionLabel = stringResource(Res.string.license_version, version ?: "")
    val authorLabel = stringResource(Res.string.license_author, author ?: "")
    val licenseLabel = stringResource(Res.string.license_type, licenseType ?: "")
    val websiteLabel = stringResource(Res.string.license_website)
    val noTextLabel = stringResource(Res.string.license_no_text)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header Card with library name and version
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                version?.let { v ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.license_version, v),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }

        // Author/Organization section (if available)
        author?.let { authorName ->
            SectionCard(title = authorLabel.substringBefore(":")) {
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // License Type section
        licenseType?.let { type ->
            SectionCard(title = licenseLabel.substringBefore(":")) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = type,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Website section (if available) - tappable link
        website?.let { url ->
            SectionCard(title = websiteLabel) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier =
                        Modifier.clickable {
                            try {
                                uriHandler.openUri(url)
                            } catch (e: Exception) {
                                // URL opening failed - handled silently
                            }
                        },
                )
            }
        }

        // Full License Text section
        SectionCard(title = "License Text") {
            if (licenseText.isNullOrBlank()) {
                Text(
                    text = noTextLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            } else {
                Text(
                    text = licenseText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Bottom spacing
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * A reusable section card with a title and content.
 * Follows the pattern from NotificationDetailScreen.
 *
 * @param title The section title
 * @param content The section content
 */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun LicenseDetailContentPreview() {
    MaterialTheme {
        LicenseDetailContent(
            name = "Kotlin Standard Library",
            version = "1.9.21",
            author = "JetBrains",
            licenseType = "Apache-2.0",
            licenseText =
                """
                Apache License
                Version 2.0, January 2004
                http://www.apache.org/licenses/

                TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

                1. Definitions.

                "License" shall mean the terms and conditions for use, reproduction,
                and distribution as defined by Sections 1 through 9 of this document.

                "Licensor" shall mean the copyright owner or entity authorized by
                the copyright owner that is granting the License.

                ...
                """.trimIndent(),
            website = "https://kotlinlang.org",
        )
    }
}

@Preview
@Composable
private fun LicenseDetailContentNoLicenseTextPreview() {
    MaterialTheme {
        LicenseDetailContent(
            name = "Some Library",
            version = "1.0.0",
            author = null,
            licenseType = "MIT",
            licenseText = null,
            website = null,
        )
    }
}

@Preview
@Composable
private fun SectionCardPreview() {
    MaterialTheme {
        SectionCard(title = "Section Title") {
            Text("Section content goes here")
        }
    }
}

@Preview
@Composable
private fun LicenseDetailLoadingPreview() {
    MaterialTheme {
        LoadingIndicator(message = "Loading license details...")
    }
}

@Preview
@Composable
private fun LicenseDetailErrorPreview() {
    MaterialTheme {
        ErrorMessage(
            message = "Library not found",
            onRetry = {},
        )
    }
}
