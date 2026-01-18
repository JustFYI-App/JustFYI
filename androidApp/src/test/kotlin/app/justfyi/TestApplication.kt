package app.justfyi

import android.app.Application

/**
 * Test application that doesn't load native libraries.
 * Used by Robolectric tests to avoid SQLCipher initialization errors.
 */
class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Skip SQLCipher and Firebase initialization for tests
    }
}
