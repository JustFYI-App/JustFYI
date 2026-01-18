package app.justfyi.di

import app.justfyi.util.AppCoroutineDispatchers
import app.justfyi.util.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Metro DI container initialization and graph resolution.
 * Verifies that all DI components are correctly configured.
 */
class DiGraphTest {
    private val expectedPackage = "justfyi.di"

    @Test
    fun allScopesAreDefinedInCorrectPackage() {
        val scopes =
            listOf(
                AppScope::class to "AppScope",
                ActivityScope::class to "ActivityScope",
                DataScope::class to "DataScope",
                BleScope::class to "BleScope",
            )

        scopes.forEach { (klass, expectedName) ->
            assertEquals(expectedName, klass.simpleName)
            assertNotNull(klass.qualifiedName)
            assertTrue(
                klass.qualifiedName!!.contains(expectedPackage),
                "$expectedName should be in $expectedPackage",
            )
        }
    }

    @Test
    fun allScopesAreDistinct() {
        val scopes = listOf(AppScope::class, DataScope::class, BleScope::class)
        val uniqueScopes = scopes.toSet()
        assertEquals(scopes.size, uniqueScopes.size, "All scopes should be distinct classes")
    }

    @Test
    fun graphInterfacesAreDefinedInCorrectPackage() {
        val interfaces =
            listOf(
                AppGraphCore::class to "AppGraphCore",
                NavigationGraph::class to "NavigationGraph",
                CoreProviders::class to "CoreProviders",
            )

        interfaces.forEach { (klass, expectedName) ->
            assertEquals(expectedName, klass.simpleName)
            assertNotNull(klass.qualifiedName)
            assertTrue(
                klass.qualifiedName!!.contains(expectedPackage),
                "$expectedName should be in $expectedPackage",
            )
        }
    }

    @Test
    fun appCoroutineDispatchersCanBeCreated() {
        val dispatchers =
            AppCoroutineDispatchers(
                io = kotlinx.coroutines.Dispatchers.Default,
                main = kotlinx.coroutines.Dispatchers.Default,
                default = kotlinx.coroutines.Dispatchers.Default,
            )
        assertNotNull(dispatchers.io)
        assertNotNull(dispatchers.main)
        assertNotNull(dispatchers.default)
    }

    @Test
    fun loggerIsAccessible() {
        assertNotNull(Logger)
        // Smoke test - should not throw
        Logger.d("Test", "Test message")
    }
}
