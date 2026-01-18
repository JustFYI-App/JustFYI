package app.justfyi.presentation.feature.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.aboutlibraries.Libs
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
 * ViewModel for the License Detail screen.
 * Manages the details of a specific library including license text.
 *
 * This ViewModel requires a runtime parameter (libraryId) that cannot be
 * injected via Metro DI. It uses the Factory pattern to combine DI dependencies
 * with runtime parameters.
 *
 * Runtime Parameters (passed to Factory.create):
 * - libraryId: The unique ID of the library to display
 */
class LicenseDetailViewModel(
    private val libraryId: String,
) : ViewModel() {
    /**
     * Factory for creating LicenseDetailViewModel instances with runtime parameters.
     */
    @Inject
    class Factory {
        fun create(libraryId: String): LicenseDetailViewModel = LicenseDetailViewModel(libraryId = libraryId)
    }

    // Internal state flows
    private val _name = MutableStateFlow("")
    private val _version = MutableStateFlow<String?>(null)
    private val _author = MutableStateFlow<String?>(null)
    private val _licenseType = MutableStateFlow<String?>(null)
    private val _licenseText = MutableStateFlow<String?>(null)
    private val _website = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    /**
     * Combined UI state using sealed interface pattern.
     */
    val uiState: StateFlow<LicenseDetailUiState> =
        combine(
            combine(_name, _version, _author) { name, version, author ->
                Triple(name, version, author)
            },
            combine(_licenseType, _licenseText, _website) { licenseType, licenseText, website ->
                Triple(licenseType, licenseText, website)
            },
            combine(_isLoading, _error) { isLoading, error ->
                Pair(isLoading, error)
            },
        ) { nameData, licenseData, stateData ->
            val (name, version, author) = nameData
            val (licenseType, licenseText, website) = licenseData
            val (isLoading, error) = stateData

            when {
                error != null && name.isEmpty() -> LicenseDetailUiState.Error(error)
                isLoading -> LicenseDetailUiState.Loading
                name.isNotEmpty() ->
                    LicenseDetailUiState.Success(
                        name = name,
                        version = version,
                        author = author,
                        licenseType = licenseType,
                        licenseText = licenseText,
                        website = website,
                    )
                else -> LicenseDetailUiState.Error("Library not found")
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LicenseDetailUiState.Loading,
        )

    init {
        loadLibraryDetails()
    }

    /**
     * Loads library details from AboutLibraries by ID.
     */
    @OptIn(ExperimentalResourceApi::class)
    private fun loadLibraryDetails() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                withContext(Dispatchers.Default) {
                    // Load the JSON from Compose Resources
                    val jsonBytes = Res.readBytes("files/aboutlibraries.json")
                    val jsonString = jsonBytes.decodeToString()

                    val libs =
                        Libs
                            .Builder()
                            .withJson(jsonString)
                            .build()

                    val library = libs.libraries.find { it.uniqueId == libraryId }

                    if (library != null) {
                        _name.value = library.name
                        _version.value = library.artifactVersion

                        // Get author from developers or organization
                        _author.value = library.developers.firstOrNull()?.name
                            ?: library.organization?.name

                        // Get license info
                        val license = library.licenses.firstOrNull()
                        _licenseType.value = license?.name ?: license?.spdxId
                        _licenseText.value = license?.licenseContent

                        // Get website
                        _website.value = library.website
                    } else {
                        _error.value = "Library not found"
                    }
                }
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message ?: "Failed to load library details"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun refresh() {
        loadLibraryDetails()
    }
}
