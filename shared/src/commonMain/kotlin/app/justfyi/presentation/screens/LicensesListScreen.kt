package app.justfyi.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.justfyi.presentation.components.ErrorMessage
import app.justfyi.presentation.components.JustFyiSnackbarHost
import app.justfyi.presentation.components.JustFyiTopAppBar
import app.justfyi.presentation.components.LoadingIndicator
import app.justfyi.presentation.feature.licenses.LicensesListViewModel
import app.justfyi.presentation.feature.licenses.LicensesUiState
import app.justfyi.presentation.navigation.NavigationActions
import com.mikepenz.aboutlibraries.entity.Library
import justfyi.shared.generated.resources.*
import justfyi.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Reusable top bar for the Licenses List screen.
 * Uses stringResource() for proper localization.
 */
@Composable
private fun LicensesListTopBar(onNavigationClick: () -> Unit = {}) {
    JustFyiTopAppBar(
        title = stringResource(Res.string.licenses_title),
        showNavigationIcon = true,
        onNavigationClick = onNavigationClick,
    )
}

/**
 * Licenses List screen composable.
 * Displays a list of open source libraries used in the app.
 *
 * @param viewModel The LicensesListViewModel instance
 * @param navigationActions Navigation actions for screen transitions
 * @param modifier Modifier for the screen
 */
@Composable
fun LicensesListScreen(
    viewModel: LicensesListViewModel,
    navigationActions: NavigationActions,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(Res.string.licenses_error)

    // Handle errors
    LaunchedEffect(uiState) {
        when (uiState) {
            is LicensesUiState.Error -> {
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
        topBar = { LicensesListTopBar(onNavigationClick = { navigationActions.navigateBack() }) },
        snackbarHost = { JustFyiSnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is LicensesUiState.Loading -> {
                    LoadingIndicator(message = stringResource(Res.string.licenses_loading))
                }

                is LicensesUiState.Error -> {
                    ErrorMessage(
                        message = stringResource(Res.string.licenses_error),
                        onRetry = { viewModel.refresh() },
                    )
                }

                is LicensesUiState.Success -> {
                    if (state.libraries.isEmpty()) {
                        EmptyLicensesContent()
                    } else {
                        LicensesListContent(
                            libraries = state.libraries,
                            onLibraryClick = { library ->
                                navigationActions.navigateToLicenseDetail(library.uniqueId)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Content for the licenses list.
 * Displays libraries in a scrollable list with animations.
 *
 * @param libraries The list of libraries to display (already sorted alphabetically)
 * @param onLibraryClick Callback when a library is clicked
 */
@Composable
private fun LicensesListContent(
    libraries: List<Library>,
    onLibraryClick: (Library) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = libraries,
            key = { it.uniqueId },
        ) { library ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            ) {
                LibraryListItem(
                    library = library,
                    onClick = { onLibraryClick(library) },
                )
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * A single library list item.
 * Displays library name, version, and license type with a navigation chevron.
 *
 * @param library The library to display
 * @param onClick Callback when the item is clicked
 */
@Composable
private fun LibraryListItem(
    library: Library,
    onClick: () -> Unit,
) {
    val licenseName =
        library.licenses.firstOrNull()?.name
            ?: library.licenses.firstOrNull()?.spdxId
            ?: "Unknown"

    val contentDesc = "${library.name}, version ${library.artifactVersion ?: "unknown"}, license $licenseName"

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = contentDesc
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Library info column
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // Library name (primary text)
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Version (secondary text)
                library.artifactVersion?.let { version ->
                    Text(
                        text = "v$version",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }

                // License type badge
                LicenseTypeBadge(licenseName = licenseName)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right chevron for navigation indication
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * A badge displaying the license type.
 *
 * @param licenseName The name of the license to display
 */
@Composable
private fun LicenseTypeBadge(licenseName: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = licenseName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Empty state content when no libraries are found.
 */
@Composable
private fun EmptyLicensesContent() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No Libraries Found",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No open source libraries were detected.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun LicensesListTopBarPreview() {
    MaterialTheme {
        LicensesListTopBar()
    }
}

@Preview
@Composable
private fun LibraryListItemPreview() {
    // Note: Cannot create real Library objects in preview, showing placeholder
    MaterialTheme {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kotlin Standard Library",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "v1.9.21",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LicenseTypeBadge(licenseName = "Apache-2.0")
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Preview
@Composable
private fun LicenseTypeBadgePreview() {
    MaterialTheme {
        LicenseTypeBadge(licenseName = "Apache-2.0")
    }
}

@Preview
@Composable
private fun EmptyLicensesContentPreview() {
    MaterialTheme {
        EmptyLicensesContent()
    }
}

@Preview
@Composable
private fun LoadingStatePreview() {
    MaterialTheme {
        LoadingIndicator(message = "Loading licenses...")
    }
}

@Preview
@Composable
private fun ErrorStatePreview() {
    MaterialTheme {
        ErrorMessage(
            message = "Failed to load licenses",
            onRetry = {},
        )
    }
}
