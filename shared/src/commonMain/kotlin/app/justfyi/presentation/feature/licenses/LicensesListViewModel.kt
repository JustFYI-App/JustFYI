package app.justfyi.presentation.feature.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import dev.zacsweers.metro.Inject
import justfyi.shared.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * ViewModel for the Licenses List screen.
 * Manages the list of open source libraries used in the app.
 *
 * Dependencies (injected via Metro DI):
 * - None (uses AboutLibraries API directly)
 */
@Inject
class LicensesListViewModel : ViewModel() {
    private val _libraries = MutableStateFlow<List<Library>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    /**
     * Combined UI state using sealed interface pattern.
     * Uses stateIn with WhileSubscribed(5000) for proper lifecycle handling.
     */
    val uiState: StateFlow<LicensesUiState> =
        combine(
            _libraries,
            _isLoading,
            _error,
        ) { libraries, isLoading, error ->
            when {
                error != null -> LicensesUiState.Error(error)
                isLoading -> LicensesUiState.Loading
                else -> LicensesUiState.Success(libraries = libraries)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LicensesUiState.Loading,
        )

    init {
        loadLibraries()
    }

    /**
     * Loads libraries from AboutLibraries.
     * Libraries are filtered and sorted alphabetically by name.
     */
    @OptIn(ExperimentalResourceApi::class)
    internal fun loadLibraries() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val libraries =
                    withContext(Dispatchers.Default) {
                        // Load the JSON from Compose Resources
                        val jsonBytes = Res.readBytes("files/aboutlibraries.json")
                        val jsonString = jsonBytes.decodeToString()

                        // Build Libs from JSON
                        val libs =
                            Libs
                                .Builder()
                                .withJson(jsonString)
                                .build()

                        libs.libraries
                            .filter { library -> !isExcludedLibrary(library) }
                            // Deduplicate by name - keep the one with most info
                            .groupBy { it.name }
                            .map { (_, libraries) ->
                                libraries.maxByOrNull { lib ->
                                    (if (lib.licenses.isNotEmpty()) 10 else 0) +
                                        (if (lib.website != null) 5 else 0) +
                                        (if (lib.developers.isNotEmpty()) 3 else 0) +
                                        (if (lib.description != null) 1 else 0)
                                } ?: libraries.first()
                            }.sortedBy { it.name.lowercase() }
                    }
                _libraries.value = libraries
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message ?: "Failed to load libraries"
            }
        }
    }

    /**
     * Determines if a library should be excluded from the list.
     * Excludes test dependencies, development tools, and build plugins.
     */
    internal fun isExcludedLibrary(library: Library): Boolean {
        val id = library.uniqueId.lowercase()
        val name = library.name.lowercase()

        // Excluded patterns for test dependencies
        val testPatterns =
            listOf(
                "junit",
                "mockk",
                "turbine",
                "kotest",
                "test",
                "mock",
                "fake",
            )

        // Excluded patterns for development tools
        val devToolPatterns =
            listOf(
                "ktlint",
                "detekt",
                "lint",
                "spotless",
                "checkstyle",
            )

        // Check test patterns
        for (pattern in testPatterns) {
            if (id.contains(pattern) || name.contains(pattern)) {
                return true
            }
        }

        // Check dev tool patterns
        for (pattern in devToolPatterns) {
            if (id.contains(pattern) || name.contains(pattern)) {
                return true
            }
        }

        return false
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Refreshes the library list.
     */
    fun refresh() {
        loadLibraries()
    }

    companion object {
        private const val TAG = "LicensesListViewModel"
    }
}
