package app.justfyi.data.firebase

import app.justfyi.util.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.where
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.messaging.messaging
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * GitLive SDK implementation of FirebaseProvider.
 * This implementation uses the GitLive Firebase SDK which provides
 * multiplatform support for Firebase on both Android and iOS.
 *
 * The GitLive SDK wraps the native Firebase SDKs and exposes
 * a consistent Kotlin Multiplatform API.
 */
@Inject
class GitLiveFirebaseProvider : FirebaseProvider {
    private val auth by lazy { Firebase.auth }

    // Use default Firestore database
    private val firestore by lazy { Firebase.firestore }

    // Use EU region for Cloud Functions
    private val functions by lazy { Firebase.functions(Firebase.app, FUNCTIONS_REGION) }
    private val messaging by lazy { Firebase.messaging }

    private var initialized = false

    // Track active listeners for cleanup
    private val activeListeners = mutableMapOf<String, kotlinx.coroutines.Job>()

    companion object {
        private const val TAG = "GitLiveFirebase"

        /** Cloud Functions region (EU) */
        private const val FUNCTIONS_REGION = "europe-west1"

        /** Special field name for document ID in query results */
        const val FIELD_DOCUMENT_ID = "_documentId"
    }

    // ==================== Initialization ====================

    override suspend fun initialize() {
        if (initialized) return

        try {
            // GitLive SDK initializes automatically based on platform configuration
            // Android: Uses google-services.json
            // iOS: Uses GoogleService-Info.plist
            Logger.d(TAG, "Firebase initialized successfully")
            initialized = true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize Firebase", e)
            throw FirebaseException("Failed to initialize Firebase", e)
        }
    }

    override fun isInitialized(): Boolean = initialized

    // ==================== Authentication ====================

    override suspend fun signInAnonymously(): String? =
        try {
            val result = auth.signInAnonymously()
            val userId = result.user?.uid
            Logger.d(TAG, "Anonymous sign-in successful: ${Logger.truncateId(userId)}")
            userId
        } catch (e: Exception) {
            Logger.e(TAG, "Anonymous sign-in failed", e)
            null
        }

    override fun isAuthenticated(): Boolean = auth.currentUser != null

    override fun getCurrentUserId(): String? = auth.currentUser?.uid

    override suspend fun signOut() {
        try {
            auth.signOut()
            Logger.d(TAG, "Sign-out successful")
        } catch (e: Exception) {
            Logger.e(TAG, "Sign-out failed", e)
        }
    }

    override suspend fun signInWithCustomToken(customToken: String): String? =
        try {
            val result = auth.signInWithCustomToken(customToken)
            val userId = result.user?.uid
            Logger.d(TAG, "Custom token sign-in successful: ${Logger.truncateId(userId)}")
            userId
        } catch (e: Exception) {
            Logger.e(TAG, "Custom token sign-in failed", e)
            null
        }

    // ==================== Firestore Document Operations ====================

    override suspend fun setDocument(
        collection: String,
        documentId: String,
        data: Map<String, Any?>,
        merge: Boolean,
    ) {
        try {
            val docRef = firestore.collection(collection).document(documentId)
            if (merge) {
                docRef.set(data, merge = true)
            } else {
                docRef.set(data)
            }
            Logger.d(TAG, "Document set: $collection/$documentId")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set document: $collection/$documentId", e)
            throw FirebaseException("Failed to set document", e)
        }
    }

    override suspend fun getDocument(
        collection: String,
        documentId: String,
    ): Map<String, Any?>? =
        try {
            val snapshot = firestore.collection(collection).document(documentId).get()
            if (snapshot.exists) {
                extractDocumentData(snapshot)
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get document: $collection/$documentId", e)
            null
        }

    override suspend fun updateDocument(
        collection: String,
        documentId: String,
        updates: Map<String, Any?>,
    ) {
        try {
            firestore.collection(collection).document(documentId).update(updates)
            Logger.d(TAG, "Document updated: $collection/$documentId")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update document: $collection/$documentId", e)
            throw FirebaseException("Failed to update document", e)
        }
    }

    override suspend fun deleteDocument(
        collection: String,
        documentId: String,
    ) {
        try {
            firestore.collection(collection).document(documentId).delete()
            Logger.d(TAG, "Document deleted: $collection/$documentId")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to delete document: $collection/$documentId", e)
            throw FirebaseException("Failed to delete document", e)
        }
    }

    // ==================== Firestore Collection Queries ====================

    override suspend fun queryCollection(
        collection: String,
        whereField: String,
        whereValue: Any,
        orderByField: String?,
        descending: Boolean,
    ): List<Map<String, Any?>> =
        try {
            var query =
                firestore
                    .collection(collection)
                    .where { whereField equalTo whereValue }

            if (orderByField != null) {
                query =
                    query.orderBy(
                        orderByField,
                        if (descending) Direction.DESCENDING else Direction.ASCENDING,
                    )
            }

            val snapshot = query.get()
            snapshot.documents.mapNotNull { doc ->
                // Include document ID in the returned data for recovery scenarios
                extractDocumentData(doc)?.plus(FIELD_DOCUMENT_ID to doc.id)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to query collection: $collection", e)
            emptyList()
        }

    override fun observeCollection(
        collection: String,
        whereField: String,
        whereValue: Any,
    ): Flow<List<DocumentSnapshot>> {
        val query =
            firestore
                .collection(collection)
                .where { whereField equalTo whereValue }

        return query.snapshots
            .map { snapshot ->
                snapshot.documents.map { doc ->
                    DocumentSnapshot(
                        id = doc.id,
                        data = extractDocumentData(doc) ?: emptyMap(),
                    )
                }
            }.catch { e ->
                Logger.e(TAG, "Error observing collection: $collection", e)
                emit(emptyList())
            }
    }

    override fun removeCollectionListener(
        collection: String,
        whereField: String,
        whereValue: Any,
    ) {
        val key = "$collection:$whereField:$whereValue"
        activeListeners[key]?.cancel()
        activeListeners.remove(key)
    }

    // ==================== Cloud Functions ====================

    override suspend fun callFunction(
        functionName: String,
        data: Map<String, Any?>,
    ): Map<String, Any?>? =
        try {
            val result = functions.httpsCallable(functionName).invoke(data)
            // GitLive SDK's HttpsCallableResult.data() requires explicit type
            // Try different response types based on function name
            when (functionName) {
                "exportUserData" -> {
                    try {
                        val exportData = result.data<ExportUserDataResponse>()
                        exportData.toMap()
                    } catch (e: Exception) {
                        Logger.e(TAG, "Failed to parse exportUserData response", e)
                        throw e
                    }
                }
                "recoverAccount" -> {
                    try {
                        val recoveryData = result.data<RecoverAccountResponse>()
                        recoveryData.toMap()
                    } catch (e: Exception) {
                        Logger.e(TAG, "Failed to parse recoverAccount response", e)
                        throw e
                    }
                }
                else -> {
                    // For other functions, try FunctionResultData
                    try {
                        val resultData = result.data<FunctionResultData>()
                        resultData.toMap()
                    } catch (e: Exception) {
                        // Fallback: try to get as simple String result
                        try {
                            val stringResult = result.data<String>()
                            mapOf("result" to stringResult)
                        } catch (e2: Exception) {
                            mapOf("error" to "Could not parse function result")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to call function: $functionName", e)
            throw FirebaseException("Failed to call function: $functionName", e)
        }

    // ==================== Cloud Messaging ====================

    override suspend fun getFcmToken(): String? =
        try {
            messaging.getToken()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get FCM token", e)
            null
        }

    // ==================== Helper Methods ====================

    /**
     * Extracts document data from a GitLive DocumentSnapshot.
     * GitLive SDK's data property requires explicit type handling.
     *
     * Note: In GitLive SDK 2.x, the data() function requires a serializer.
     * For flexible document extraction, we manually build a map from known fields
     * or use the get() method for specific field access.
     */
    private fun extractDocumentData(doc: dev.gitlive.firebase.firestore.DocumentSnapshot): Map<String, Any?>? =
        try {
            // Try to decode as a generic DocumentData wrapper
            val docData = doc.data<DocumentData>()
            docData.toMap()
        } catch (e: Exception) {
            // Fallback: return empty map - specific repositories will access fields directly
            Logger.w(TAG, "Failed to extract document data: ${e.message}")
            emptyMap()
        }
}

/**
 * Serializable wrapper for simple function result data.
 * Used to decode Cloud Function results with GitLive SDK.
 */
@Serializable
private data class FunctionResultData(
    val success: Boolean? = null,
    val result: String? = null,
    val error: String? = null,
    val data: Map<String, String>? = null,
) {
    fun toMap(): Map<String, Any?> =
        buildMap {
            success?.let { put("success", it) }
            result?.let { put("result", it) }
            error?.let { put("error", it) }
            data?.let { putAll(it) }
        }
}

/**
 * Serializable wrapper for recoverAccount function response.
 */
@Serializable
private data class RecoverAccountResponse(
    val success: Boolean = false,
    val customToken: String? = null,
    val error: String? = null,
) {
    fun toMap(): Map<String, Any?> =
        buildMap {
            put("success", success)
            customToken?.let { put("customToken", it) }
            error?.let { put("error", it) }
        }
}

/**
 * Serializable wrapper for exportUserData function response.
 */
@Serializable
private data class ExportUserDataResponse(
    val user: ExportedUserData? = null,
    val interactions: List<ExportedInteraction> = emptyList(),
    val notifications: List<ExportedNotification> = emptyList(),
    val reports: List<ExportedReport> = emptyList(),
) {
    fun toMap(): Map<String, Any?> =
        buildMap {
            put("user", user?.toMap())
            put("interactions", interactions.map { it.toMap() })
            put("notifications", notifications.map { it.toMap() })
            put("reports", reports.map { it.toMap() })
        }
}

@Serializable
private data class ExportedUserData(
    val anonymousId: String? = null,
    val username: String? = null,
    val createdAt: Long? = null,
) {
    fun toMap(): Map<String, Any?> =
        buildMap {
            anonymousId?.let { put("anonymousId", it) }
            username?.let { put("username", it) }
            createdAt?.let { put("createdAt", it) }
        }
}

@Serializable
private data class ExportedInteraction(
    val partnerAnonymousId: String? = null,
    val partnerUsernameSnapshot: String? = null,
    val recordedAt: Long? = null,
    val ownerId: String? = null,
) {
    fun toMap(): Map<String, Any?> =
        buildMap {
            partnerAnonymousId?.let { put("partnerAnonymousId", it) }
            partnerUsernameSnapshot?.let { put("partnerUsernameSnapshot", it) }
            recordedAt?.let { put("recordedAt", it) }
            ownerId?.let { put("ownerId", it) }
        }
}

@Serializable
private data class ExportedNotification(
    val recipientId: String? = null,
    val type: String? = null,
    val stiType: String? = null,
    val exposureDate: Long? = null,
    val chainData: String? = null,
    val isRead: Boolean? = null,
    val receivedAt: Long? = null,
    val updatedAt: Long? = null,
) {
    fun toMap(): Map<String, Any?> =
        buildMap {
            recipientId?.let { put("recipientId", it) }
            type?.let { put("type", it) }
            stiType?.let { put("stiType", it) }
            exposureDate?.let { put("exposureDate", it) }
            chainData?.let { put("chainData", it) }
            isRead?.let { put("isRead", it) }
            receivedAt?.let { put("receivedAt", it) }
            updatedAt?.let { put("updatedAt", it) }
        }
}

@Serializable
private data class ExportedReport(
    val reporterId: String? = null,
    val stiTypes: String? = null,
    val testDate: Long? = null,
    val privacyLevel: String? = null,
    val affectedInteractionIds: List<String> = emptyList(),
    val reportedAt: Long? = null,
) {
    fun toMap(): Map<String, Any?> =
        buildMap {
            reporterId?.let { put("reporterId", it) }
            stiTypes?.let { put("stiTypes", it) }
            testDate?.let { put("testDate", it) }
            privacyLevel?.let { put("privacyLevel", it) }
            put("affectedInteractionIds", affectedInteractionIds)
            reportedAt?.let { put("reportedAt", it) }
        }
}

/**
 * Serializable wrapper for document data.
 * Used to decode Firestore documents with GitLive SDK.
 * Supports both User and Notification document types.
 */
@Serializable
private data class DocumentData(
    // User fields
    val id: String? = null,
    val anonymousId: String? = null,
    val username: String? = null,
    val fcmToken: String? = null,
    val createdAt: Long? = null,
    val idBackupConfirmed: Boolean? = null,
    // Notification fields
    val recipientId: String? = null,
    val type: String? = null,
    val stiType: String? = null,
    val exposureDate: Long? = null,
    val chainData: String? = null,
    val isRead: Boolean? = null,
    val receivedAt: Long? = null,
    val updatedAt: Long? = null,
    val deletedAt: Long? = null,
    // Report fields
    val reporterId: String? = null,
    val reporterInteractionHashedId: String? = null,
    val reporterNotificationHashedId: String? = null,
    val stiTypes: String? = null,
    val testDate: Long? = null,
    val privacyLevel: String? = null,
    val reportedAt: Long? = null,
    val status: String? = null,
    val processedAt: Long? = null,
    val testResult: String? = null,
    val linkedReportId: String? = null,
    val notificationId: String? = null,
) {
    fun toMap(): Map<String, Any?> =
        buildMap {
            // User fields
            id?.let { put("id", it) }
            anonymousId?.let { put("anonymousId", it) }
            username?.let { put("username", it) }
            fcmToken?.let { put("fcmToken", it) }
            createdAt?.let { put("createdAt", it) }
            idBackupConfirmed?.let { put("idBackupConfirmed", it) }
            // Notification fields
            recipientId?.let { put("recipientId", it) }
            type?.let { put("type", it) }
            stiType?.let { put("stiType", it) }
            exposureDate?.let { put("exposureDate", it) }
            chainData?.let { put("chainData", it) }
            isRead?.let { put("isRead", it) }
            receivedAt?.let { put("receivedAt", it) }
            updatedAt?.let { put("updatedAt", it) }
            deletedAt?.let { put("deletedAt", it) }
            // Report fields
            reporterId?.let { put("reporterId", it) }
            reporterInteractionHashedId?.let { put("reporterInteractionHashedId", it) }
            reporterNotificationHashedId?.let { put("reporterNotificationHashedId", it) }
            stiTypes?.let { put("stiTypes", it) }
            testDate?.let { put("testDate", it) }
            privacyLevel?.let { put("privacyLevel", it) }
            reportedAt?.let { put("reportedAt", it) }
            status?.let { put("status", it) }
            processedAt?.let { put("processedAt", it) }
            testResult?.let { put("testResult", it) }
            linkedReportId?.let { put("linkedReportId", it) }
            notificationId?.let { put("notificationId", it) }
        }
}
