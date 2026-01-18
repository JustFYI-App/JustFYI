package app.justfyi.data.firebase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Test implementation of FirebaseProvider for unit testing.
 * Stores data in memory and allows mock configuration for testing scenarios.
 *
 * Usage in tests:
 * ```
 * val provider = TestFirebaseProvider()
 * provider.setMockAnonymousUserId("test-user-id")
 * val userId = provider.signInAnonymously()
 * ```
 */
class TestFirebaseProvider : FirebaseProvider {
    // ==================== Internal State ====================

    private var initialized = false
    private var authenticated = false
    private var currentUserId: String? = null
    private var mockAnonymousUserId: String = "default-anonymous-id"
    private var mockFcmToken: String? = null

    // In-memory document storage: collection -> (documentId -> data)
    private val documents = mutableMapOf<String, MutableMap<String, Map<String, Any?>>>()

    // Collection listeners
    private val collectionFlows = mutableMapOf<String, MutableStateFlow<List<DocumentSnapshot>>>()

    // Function call tracking
    private val functionResults = mutableMapOf<String, Map<String, Any?>?>()
    private var lastFunctionCall: Pair<String, Map<String, Any?>>? = null

    // ==================== Mock Configuration ====================

    /**
     * Sets the user ID that will be returned by signInAnonymously().
     */
    fun setMockAnonymousUserId(userId: String) {
        mockAnonymousUserId = userId
    }

    /**
     * Sets the FCM token that will be returned by getFcmToken().
     */
    fun setMockFcmToken(token: String) {
        mockFcmToken = token
    }

    /**
     * Configures the result for a Cloud Function call.
     */
    fun setMockFunctionResult(
        functionName: String,
        result: Map<String, Any?>?,
    ) {
        functionResults[functionName] = result
    }

    /**
     * Gets the last function call made (for verification in tests).
     */
    fun getLastFunctionCall(): Pair<String, Map<String, Any?>>? = lastFunctionCall

    /**
     * Clears all stored data (useful between tests).
     */
    fun clear() {
        documents.clear()
        collectionFlows.clear()
        functionResults.clear()
        lastFunctionCall = null
        authenticated = false
        currentUserId = null
    }

    // ==================== Initialization ====================

    override suspend fun initialize() {
        initialized = true
    }

    override fun isInitialized(): Boolean = initialized

    // ==================== Authentication ====================

    override suspend fun signInAnonymously(): String? {
        currentUserId = mockAnonymousUserId
        authenticated = true
        return currentUserId
    }

    override fun isAuthenticated(): Boolean = authenticated

    override fun getCurrentUserId(): String? = currentUserId

    override suspend fun signOut() {
        authenticated = false
        currentUserId = null
    }

    override suspend fun signInWithCustomToken(customToken: String): String? {
        // For testing, extract user ID from the token (mock behavior)
        // In tests, the custom token is typically the savedId itself
        currentUserId = customToken
        authenticated = true
        return currentUserId
    }

    // ==================== Firestore Document Operations ====================

    override suspend fun setDocument(
        collection: String,
        documentId: String,
        data: Map<String, Any?>,
        merge: Boolean,
    ) {
        val collectionDocs = documents.getOrPut(collection) { mutableMapOf() }

        if (merge && collectionDocs.containsKey(documentId)) {
            val existing = collectionDocs[documentId]?.toMutableMap() ?: mutableMapOf()
            existing.putAll(data)
            collectionDocs[documentId] = existing
        } else {
            collectionDocs[documentId] = data
        }

        // Update any active listeners
        notifyCollectionListeners(collection)
    }

    override suspend fun getDocument(
        collection: String,
        documentId: String,
    ): Map<String, Any?>? = documents[collection]?.get(documentId)

    override suspend fun updateDocument(
        collection: String,
        documentId: String,
        updates: Map<String, Any?>,
    ) {
        val collectionDocs =
            documents[collection]
                ?: throw FirebaseException("Collection '$collection' not found")

        val existing =
            collectionDocs[documentId]?.toMutableMap()
                ?: throw FirebaseException("Document '$documentId' not found in '$collection'")

        existing.putAll(updates)
        collectionDocs[documentId] = existing

        // Update any active listeners
        notifyCollectionListeners(collection)
    }

    override suspend fun deleteDocument(
        collection: String,
        documentId: String,
    ) {
        documents[collection]?.remove(documentId)
        notifyCollectionListeners(collection)
    }

    // ==================== Firestore Collection Queries ====================

    override suspend fun queryCollection(
        collection: String,
        whereField: String,
        whereValue: Any,
        orderByField: String?,
        descending: Boolean,
    ): List<Map<String, Any?>> {
        val collectionDocs = documents[collection] ?: return emptyList()

        // Filter and include document ID in results (matching GitLiveFirebaseProvider behavior)
        var results =
            collectionDocs.entries
                .filter { (_, doc) ->
                    doc[whereField] == whereValue
                }.map { (docId, doc) ->
                    doc + (FIELD_DOCUMENT_ID to docId)
                }

        if (orderByField != null) {
            @Suppress("UNCHECKED_CAST")
            results =
                if (descending) {
                    results.sortedByDescending { (it[orderByField] as? Comparable<Any>)?.hashCode() ?: 0 }
                } else {
                    results.sortedBy { (it[orderByField] as? Comparable<Any>)?.hashCode() ?: 0 }
                }
        }

        return results
    }

    companion object {
        /** Special field name for document ID in query results (matches GitLiveFirebaseProvider) */
        const val FIELD_DOCUMENT_ID = "_documentId"
    }

    override fun observeCollection(
        collection: String,
        whereField: String,
        whereValue: Any,
    ): Flow<List<DocumentSnapshot>> {
        val key = "$collection:$whereField:$whereValue"
        val flow =
            collectionFlows.getOrPut(key) {
                MutableStateFlow(emptyList())
            }

        // Initialize with current data
        val collectionDocs = documents[collection] ?: emptyMap()
        val snapshots =
            collectionDocs
                .filter { (_, data) -> data[whereField] == whereValue }
                .map { (id, data) -> DocumentSnapshot(id, data) }
        flow.value = snapshots

        return flow
    }

    override fun removeCollectionListener(
        collection: String,
        whereField: String,
        whereValue: Any,
    ) {
        val key = "$collection:$whereField:$whereValue"
        collectionFlows.remove(key)
    }

    private fun notifyCollectionListeners(collection: String) {
        collectionFlows.forEach { (key, flow) ->
            if (key.startsWith("$collection:")) {
                val parts = key.split(":")
                if (parts.size >= 3) {
                    val whereField = parts[1]
                    val whereValue = parts[2]

                    val collectionDocs = documents[collection] ?: emptyMap()
                    val snapshots =
                        collectionDocs
                            .filter { (_, data) -> data[whereField]?.toString() == whereValue }
                            .map { (id, data) -> DocumentSnapshot(id, data) }
                    flow.value = snapshots
                }
            }
        }
    }

    // ==================== Cloud Functions ====================

    override suspend fun callFunction(
        functionName: String,
        data: Map<String, Any?>,
    ): Map<String, Any?>? {
        lastFunctionCall = functionName to data
        return functionResults[functionName]
    }

    // ==================== Cloud Messaging ====================

    override suspend fun getFcmToken(): String? = mockFcmToken
}
