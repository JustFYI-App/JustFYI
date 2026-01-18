package app.justfyi.di

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for ViewModel DI injection configuration.
 * Verifies that the DI scope structure supports ViewModel creation.
 */
class ViewModelInjectionTest {
    @Test
    fun allScopesAreDefinedInSamePackage() {
        val appScope = AppScope::class
        val dataScope = DataScope::class
        val bleScope = BleScope::class
        val navigationGraph = NavigationGraph::class

        // All must be in the same package for Metro to resolve dependencies
        val expectedPackage = "justfyi.di"
        listOf(appScope, dataScope, bleScope, navigationGraph).forEach { klass ->
            assertNotNull(klass.qualifiedName)
            assertTrue(
                klass.qualifiedName!!.contains(expectedPackage),
                "${klass.simpleName} should be in $expectedPackage",
            )
        }
    }

    @Test
    fun coreProvidersAndNavigationGraphAreInSamePackage() {
        val coreProviders = CoreProviders::class
        val navigationGraph = NavigationGraph::class

        assertNotNull(coreProviders.qualifiedName)
        assertNotNull(navigationGraph.qualifiedName)
        assertTrue(
            coreProviders.qualifiedName!!.contains("justfyi.di") &&
                navigationGraph.qualifiedName!!.contains("justfyi.di"),
            "CoreProviders and NavigationGraph should be in the same package",
        )
    }
}
