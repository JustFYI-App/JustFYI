package app.justfyi.presentation.feature.licenses

import com.mikepenz.aboutlibraries.entity.Library
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for LicensesListViewModel.
 *
 * These tests verify the library exclusion logic that filters out
 * test dependencies and development tools from the license list.
 */
class LicensesListViewModelTest {
    private val viewModel = LicensesListViewModel()

    // =========================================================================
    // Library Exclusion Tests
    // =========================================================================

    @Test
    fun `isExcludedLibrary returns true for JUnit test library`() {
        val library = createLibrary(uniqueId = "junit:junit", name = "JUnit")
        assertTrue(viewModel.isExcludedLibrary(library), "JUnit should be excluded")
    }

    @Test
    fun `isExcludedLibrary returns true for MockK library`() {
        val library = createLibrary(uniqueId = "io.mockk:mockk", name = "MockK")
        assertTrue(viewModel.isExcludedLibrary(library), "MockK should be excluded")
    }

    @Test
    fun `isExcludedLibrary returns true for Turbine test library`() {
        val library = createLibrary(uniqueId = "app.cash.turbine:turbine", name = "Turbine")
        assertTrue(viewModel.isExcludedLibrary(library), "Turbine should be excluded")
    }

    @Test
    fun `isExcludedLibrary returns true for Kotest library`() {
        val library = createLibrary(uniqueId = "io.kotest:kotest-assertions-core", name = "Kotest Assertions")
        assertTrue(viewModel.isExcludedLibrary(library), "Kotest should be excluded")
    }

    @Test
    fun `isExcludedLibrary returns true for ktlint`() {
        val library = createLibrary(uniqueId = "com.pinterest:ktlint", name = "ktlint")
        assertTrue(viewModel.isExcludedLibrary(library), "ktlint should be excluded")
    }

    @Test
    fun `isExcludedLibrary returns true for detekt`() {
        val library = createLibrary(uniqueId = "io.gitlab.arturbosch.detekt:detekt-core", name = "Detekt")
        assertTrue(viewModel.isExcludedLibrary(library), "Detekt should be excluded")
    }

    @Test
    fun `isExcludedLibrary returns true for libraries with test in name`() {
        val library = createLibrary(uniqueId = "org.example:some-testing-lib", name = "Some Testing Library")
        assertTrue(viewModel.isExcludedLibrary(library), "Libraries with 'test' in name should be excluded")
    }

    @Test
    fun `isExcludedLibrary returns false for Kotlin stdlib`() {
        val library = createLibrary(uniqueId = "org.jetbrains.kotlin:kotlin-stdlib", name = "Kotlin Standard Library")
        assertFalse(viewModel.isExcludedLibrary(library), "Kotlin stdlib should not be excluded")
    }

    @Test
    fun `isExcludedLibrary returns false for Compose runtime`() {
        val library = createLibrary(uniqueId = "androidx.compose.runtime:runtime", name = "Compose Runtime")
        assertFalse(viewModel.isExcludedLibrary(library), "Compose Runtime should not be excluded")
    }

    @Test
    fun `isExcludedLibrary returns false for Firebase libraries`() {
        val library = createLibrary(uniqueId = "com.google.firebase:firebase-auth", name = "Firebase Auth")
        assertFalse(viewModel.isExcludedLibrary(library), "Firebase Auth should not be excluded")
    }

    @Test
    fun `isExcludedLibrary returns false for SQLDelight`() {
        val library = createLibrary(uniqueId = "app.cash.sqldelight:runtime", name = "SQLDelight Runtime")
        assertFalse(viewModel.isExcludedLibrary(library), "SQLDelight should not be excluded")
    }

    @Test
    fun `isExcludedLibrary returns false for AboutLibraries itself`() {
        val library = createLibrary(uniqueId = "com.mikepenz:aboutlibraries-core", name = "AboutLibraries")
        assertFalse(viewModel.isExcludedLibrary(library), "AboutLibraries should not be excluded")
    }

    @Test
    fun `isExcludedLibrary is case insensitive`() {
        val uppercaseLibrary = createLibrary(uniqueId = "JUNIT:JUNIT", name = "JUNIT")
        assertTrue(viewModel.isExcludedLibrary(uppercaseLibrary), "Case-insensitive match should work for uppercase")

        val mixedCaseLibrary = createLibrary(uniqueId = "Io.MockK:MockK", name = "MockK Framework")
        assertTrue(viewModel.isExcludedLibrary(mixedCaseLibrary), "Case-insensitive match should work for mixed case")
    }

    // =========================================================================
    // Initial State Test
    // =========================================================================

    @Test
    fun `uiState initial value is Loading`() {
        val newViewModel = LicensesListViewModel()
        assertTrue(
            newViewModel.uiState.value is LicensesUiState.Loading,
            "Initial UI state should be Loading",
        )
    }

    // =========================================================================
    // Helper Functions
    // =========================================================================

    /**
     * Creates a Library object for testing.
     * Only sets the fields needed for exclusion logic testing.
     */
    private fun createLibrary(
        uniqueId: String,
        name: String,
    ): Library =
        Library(
            uniqueId = uniqueId,
            artifactVersion = "1.0.0",
            name = name,
            description = null,
            website = null,
            developers = persistentListOf(),
            organization = null,
            scm = null,
            licenses = persistentSetOf(),
            funding = persistentSetOf(),
            tag = null,
        )
}
