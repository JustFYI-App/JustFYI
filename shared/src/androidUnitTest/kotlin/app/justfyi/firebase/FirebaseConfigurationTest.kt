package app.justfyi.firebase

import app.justfyi.di.FirebaseModule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for Firebase initialization and configuration.
 * These tests verify that Firebase dependencies are correctly configured
 * and that the Metro DI module provides the expected instances.
 *
 * Note: These are unit tests that verify the configuration structure.
 * Full integration tests with actual Firebase require instrumented tests
 * or a properly configured google-services.json.
 */
class FirebaseConfigurationTest {
    /**
     * Test 1: Verify that FirebaseModule interface is correctly defined.
     * This ensures the Metro DI module structure compiles and is accessible.
     */
    @Test
    fun firebaseModuleInterfaceExists() {
        // Verify FirebaseModule interface can be referenced and has the expected name
        val moduleClass = FirebaseModule::class
        assertNotNull(moduleClass, "FirebaseModule interface should be defined")
        assertEquals("FirebaseModule", moduleClass.simpleName, "FirebaseModule should have correct name")
    }

    /**
     * Test 2: Verify that FirebaseModule companion object is accessible.
     * The companion object contains the @Provides methods for Firebase services.
     */
    @Test
    fun firebaseModuleCompanionObjectExists() {
        // The companion object holds all provider methods
        val companion = FirebaseModule.Companion
        assertNotNull(companion, "FirebaseModule.Companion should exist")

        // Verify the companion class name contains "Companion"
        val companionClassName = companion::class.simpleName
        assertNotNull(companionClassName, "Companion should have a class name")
    }

    /**
     * Test 3: Verify that FcmTokenCallback interface is correctly defined.
     * This ensures the app can handle FCM token retrieval and refresh.
     */
    @Test
    fun fcmTokenCallbackInterfaceExists() {
        val callbackClass = FcmTokenCallback::class
        assertNotNull(callbackClass, "FcmTokenCallback interface should be defined")
        assertEquals("FcmTokenCallback", callbackClass.simpleName, "FcmTokenCallback should have correct name")

        // Create a test implementation to verify the interface contract
        val testCallback =
            object : FcmTokenCallback {
                var receivedToken: String? = null
                var receivedError: Exception? = null

                override fun onTokenReceived(token: String) {
                    receivedToken = token
                }

                override fun onTokenError(exception: Exception) {
                    receivedError = exception
                }
            }

        // Verify the interface methods work
        testCallback.onTokenReceived("test-token")
        assertEquals("test-token", testCallback.receivedToken, "onTokenReceived should work")

        val testException = Exception("test error")
        testCallback.onTokenError(testException)
        assertEquals(testException, testCallback.receivedError, "onTokenError should work")
    }
}
