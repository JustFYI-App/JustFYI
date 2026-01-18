package app.justfyi.navigation

import app.justfyi.di.AppScope
import app.justfyi.di.BleScope
import app.justfyi.di.DataScope
import app.justfyi.di.NavigationGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for navigation DI integration.
 */
class NavigationDiTest {
    private val expectedPackage = "justfyi.di"

    @Test
    fun navigationGraphIsInCorrectPackage() {
        val graphClass = NavigationGraph::class
        assertNotNull(graphClass)
        assertEquals("NavigationGraph", graphClass.simpleName)
        assertTrue(
            graphClass.qualifiedName?.contains(expectedPackage) == true,
            "NavigationGraph should be in $expectedPackage",
        )
    }

    @Test
    fun allScopesAreInSamePackageAsNavigationGraph() {
        val scopes = listOf(AppScope::class, DataScope::class, BleScope::class)

        scopes.forEach { scope ->
            assertNotNull(scope.qualifiedName)
            assertTrue(
                scope.qualifiedName!!.contains(expectedPackage),
                "${scope.simpleName} should be in $expectedPackage",
            )
        }
    }
}
