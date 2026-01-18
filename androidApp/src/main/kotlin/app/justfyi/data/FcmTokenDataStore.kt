package app.justfyi.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Plain DataStore for FCM token storage.
 *
 * FCM tokens are considered low-sensitivity data:
 * - They are temporary and auto-refreshed by Firebase
 * - They are only useful when paired with user credentials
 * - Encryption overhead is not warranted
 */
class FcmTokenDataStore(
    private val context: Context,
) {
    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    /**
     * Sets the pending FCM token.
     * Called when token is received but user is not yet authenticated.
     */
    suspend fun setPendingToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[PENDING_TOKEN_KEY] = token
        }
    }

    /**
     * Gets the pending FCM token, if any.
     * Returns null if no token is pending.
     */
    suspend fun getPendingToken(): String? =
        context.dataStore.data
            .map { prefs -> prefs[PENDING_TOKEN_KEY] }
            .first()

    /**
     * Clears the pending FCM token.
     * Called after token has been successfully uploaded to Firestore.
     */
    suspend fun clearPendingToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(PENDING_TOKEN_KEY)
        }
    }

    companion object {
        private const val DATASTORE_NAME = "justfyi_fcm_tokens"
        private val PENDING_TOKEN_KEY = stringPreferencesKey("pending_fcm_token")
    }
}
