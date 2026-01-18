package app.justfyi.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.justfyi.data.firebase.DocumentSnapshot
import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.data.local.NotificationQueries
import app.justfyi.data.model.FirestoreCollections
import app.justfyi.domain.model.ChainVisualization
import app.justfyi.domain.model.Notification
import app.justfyi.domain.model.TestStatus
import app.justfyi.domain.repository.NotificationRepository
import app.justfyi.util.AppCoroutineDispatchers
import app.justfyi.util.HashUtils
import app.justfyi.util.Logger
import app.justfyi.util.currentTimeMillis
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.justfyi.data.local.Notification as DbNotification

/**
 * Multiplatform implementation of NotificationRepository using GitLive Firebase SDK.
 * Listens to Firestore for real-time updates and caches locally for offline access.
 * Implements server-wins conflict resolution.
 *
 * This implementation uses the FirebaseProvider abstraction which works
 * on both Android and iOS through the GitLive SDK.
 *
 * DOMAIN-SEPARATED HASHING:
 * The userId is hashed using HashUtils.hashForNotification() before querying Firestore.
 * This matches how the backend stores recipientId with the "notification:" salt prefix.
 * This prevents cross-collection correlation if the database is breached.
 */
@Inject
class NotificationRepositoryImpl(
    private val notificationQueries: NotificationQueries,
    private val firebaseProvider: FirebaseProvider,
    private val dispatchers: AppCoroutineDispatchers,
) : NotificationRepository {
    // Scope for real-time sync operations
    private val syncScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var realtimeSyncJob: Job? = null
    private var currentSyncUserId: String? = null
    private var currentSyncHashedUserId: String? = null

    override fun getNotifications(): Flow<List<Notification>> =
        notificationQueries
            .getAllNotifications()
            .asFlow()
            .mapToList(dispatchers.io)
            .map { list -> list.map { it.toDomain() } }

    override fun getUnreadCount(): Flow<Int> =
        notificationQueries
            .getUnreadCount()
            .asFlow()
            .mapToOne(dispatchers.io)
            .map { it.toInt() }

    override suspend fun getNotificationById(notificationId: String): Notification? =
        withContext(dispatchers.io) {
            notificationQueries
                .getNotificationById(notificationId)
                .executeAsOneOrNull()
                ?.toDomain()
        }

    override suspend fun fetchNotificationFromCloud(notificationId: String): Notification? =
        withContext(dispatchers.io) {
            try {
                val document = firebaseProvider.getDocument(FirestoreCollections.NOTIFICATIONS, notificationId)
                if (document != null) {
                    val notification = documentToNotification(notificationId, document)
                    if (notification != null) {
                        notificationQueries.insertOrReplaceNotification(
                            id = notification.id,
                            type = notification.type,
                            sti_type = notification.stiType,
                            exposure_date = notification.exposureDate,
                            chain_data = notification.chainData,
                            is_read = if (notification.isRead) 1L else 0L,
                            received_at = notification.receivedAt,
                            updated_at = notification.updatedAt,
                            deleted_at = notification.deletedAt,
                        )
                    }
                    notification
                } else {
                    null
                }
            } catch (e: Exception) {
                Logger.e(TAG, "fetchNotificationFromCloud failed", e)
                null
            }
        }

    override suspend fun markAsRead(notificationId: String): Unit =
        withContext(dispatchers.io) {
            val now = currentTimeMillis()
            notificationQueries.markAsRead(updated_at = now, id = notificationId)
            try {
                firebaseProvider.updateDocument(
                    collection = FirestoreCollections.NOTIFICATIONS,
                    documentId = notificationId,
                    updates =
                        mapOf(
                            FirestoreCollections.NotificationFields.IS_READ to true,
                            FirestoreCollections.NotificationFields.UPDATED_AT to now,
                        ),
                )
            } catch (_: Exception) {
                // Local update succeeded, backend sync failed - not critical
            }
            Unit
        }

    override suspend fun markAllAsRead(): Unit =
        withContext(dispatchers.io) {
            val now = currentTimeMillis()
            notificationQueries.markAllAsRead(updated_at = now)
            Unit
        }

    override suspend fun updateChainData(
        notificationId: String,
        newChainData: String,
    ): Unit =
        withContext(dispatchers.io) {
            val now = currentTimeMillis()
            notificationQueries.updateChainData(
                chain_data = newChainData,
                updated_at = now,
                id = notificationId,
            )
            Unit
        }

    override suspend fun syncFromCloud(userId: String): Unit =
        withContext(dispatchers.io) {
            try {
                // Hash the userId with notification: prefix to match backend storage
                val hashedUserId = HashUtils.hashForNotification(userId)

                val documents =
                    firebaseProvider.queryCollection(
                        collection = FirestoreCollections.NOTIFICATIONS,
                        whereField = FirestoreCollections.NotificationFields.RECIPIENT_ID,
                        whereValue = hashedUserId,
                        orderByField = FirestoreCollections.NotificationFields.RECEIVED_AT,
                        descending = true,
                    )

                // Get IDs from Firestore to detect deleted notifications
                val firestoreIds = documents.mapNotNull { it["id"] as? String }.toSet()

                // Get local notification IDs
                val localIds =
                    notificationQueries
                        .getAllNotifications()
                        .executeAsList()
                        .map { it.id }
                        .toSet()

                // Delete notifications that no longer exist in Firestore
                val deletedIds = localIds - firestoreIds
                if (deletedIds.isNotEmpty()) {
                    Logger.d(TAG, "Initial sync: deleting ${deletedIds.size} notifications no longer in Firestore")
                    deletedIds.forEach { id ->
                        notificationQueries.deleteNotification(id)
                    }
                }

                // Insert/update notifications from Firestore
                documents.forEach { docData ->
                    val notificationId = docData["id"] as? String
                    if (notificationId != null) {
                        val notification = documentToNotification(notificationId, docData)
                        if (notification != null) {
                            // Server wins: insert or replace
                            notificationQueries.insertOrReplaceNotification(
                                id = notification.id,
                                type = notification.type,
                                sti_type = notification.stiType,
                                exposure_date = notification.exposureDate,
                                chain_data = notification.chainData,
                                is_read = if (notification.isRead) 1L else 0L,
                                received_at = notification.receivedAt,
                                updated_at = notification.updatedAt,
                                deleted_at = notification.deletedAt,
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Sync failed, user can still access locally cached notifications
            }
            Unit
        }

    override fun startRealtimeSync(userId: String) {
        stopRealtimeSync()
        val hashedUserId = HashUtils.hashForNotification(userId)
        currentSyncUserId = userId
        currentSyncHashedUserId = hashedUserId

        realtimeSyncJob =
            syncScope.launch {
                try {
                    firebaseProvider
                        .observeCollection(
                            collection = FirestoreCollections.NOTIFICATIONS,
                            whereField = FirestoreCollections.NotificationFields.RECIPIENT_ID,
                            whereValue = hashedUserId,
                        ).collect { snapshots ->
                            processSnapshotChanges(snapshots)
                        }
                } catch (e: Exception) {
                    Logger.e(TAG, "Realtime sync error", e)
                }
            }
    }

    override fun stopRealtimeSync() {
        realtimeSyncJob?.cancel()
        realtimeSyncJob = null

        // Use the hashed userId when removing the listener
        currentSyncHashedUserId?.let { hashedUserId ->
            firebaseProvider.removeCollectionListener(
                collection = FirestoreCollections.NOTIFICATIONS,
                whereField = FirestoreCollections.NotificationFields.RECIPIENT_ID,
                whereValue = hashedUserId,
            )
        }
        currentSyncUserId = null
        currentSyncHashedUserId = null
    }

    override suspend fun deleteAllNotifications(): Unit =
        withContext(dispatchers.io) {
            notificationQueries.deleteAllNotifications()
            Unit
        }

    override suspend fun syncFromCloudForCurrentUser(): Boolean =
        withContext(dispatchers.io) {
            val userId = firebaseProvider.getCurrentUserId() ?: return@withContext false
            syncFromCloud(userId)
            true
        }

    override fun startRealtimeSyncForCurrentUser(): Boolean {
        val userId = firebaseProvider.getCurrentUserId() ?: return false
        startRealtimeSync(userId)
        return true
    }

    override suspend fun submitNegativeResult(
        notificationId: String,
        stiType: String,
    ): Boolean =
        withContext(dispatchers.io) {
            try {
                Logger.d(TAG, "Submitting negative result for notification $notificationId, STI: $stiType")

                // Call the cloud function to report negative test
                val result =
                    firebaseProvider.callFunction(
                        functionName = "reportNegativeTest",
                        data =
                            mapOf(
                                "notificationId" to notificationId,
                                "stiType" to stiType,
                            ),
                    )

                val success = result?.get("success") as? Boolean ?: false
                if (success) {
                    // NOTE: The backend reportNegativeTest function should update chainData in Firestore.
                    // We update local SQLite for immediate UI feedback, but the real source of truth
                    // is Firestore. If backend doesn't update chainData, the realtime sync will overwrite.
                    val currentNotification =
                        notificationQueries
                            .getNotificationById(notificationId)
                            .executeAsOneOrNull()

                    if (currentNotification != null) {
                        val chain = ChainVisualization.fromJson(currentNotification.chain_data)
                        val updatedChain = chain.withCurrentUserStatus(TestStatus.NEGATIVE)
                        val updatedChainJson = with(ChainVisualization) { updatedChain.toJson() }
                        val now = currentTimeMillis()

                        notificationQueries.updateChainData(
                            chain_data = updatedChainJson,
                            updated_at = now,
                            id = notificationId,
                        )
                        Logger.d(TAG, "Updated local chainData - backend should update Firestore")
                    }
                } else {
                    val errorMsg = result?.get("error") as? String ?: "Unknown error"
                    Logger.w(TAG, "reportNegativeTest failed: $errorMsg")
                }
                success
            } catch (e: Exception) {
                Logger.e(TAG, "reportNegativeTest exception", e)
                false
            }
        }

    private fun processSnapshotChanges(snapshots: List<DocumentSnapshot>) {
        // Get IDs from Firestore to detect deleted notifications
        val firestoreIds = snapshots.mapNotNull { it.id }.toSet()

        // Get local notification IDs
        val localIds =
            notificationQueries
                .getAllNotifications()
                .executeAsList()
                .map { it.id }
                .toSet()

        // Delete notifications that no longer exist in Firestore
        val deletedIds = localIds - firestoreIds
        if (deletedIds.isNotEmpty()) {
            Logger.d(TAG, "Deleting ${deletedIds.size} notifications no longer in Firestore: $deletedIds")
            deletedIds.forEach { id ->
                notificationQueries.deleteNotification(id)
            }
        }

        // Insert/update notifications from Firestore
        snapshots.forEach { snapshot ->
            val notification = documentToNotification(snapshot.id, snapshot.data)
            if (notification != null) {
                // Log chainData from Firestore sync to debug negative test overwrite
                Logger.d(TAG, "SYNC from Firestore: id=${notification.id}, chainData=${notification.chainData}")
                notificationQueries.insertOrReplaceNotification(
                    id = notification.id,
                    type = notification.type,
                    sti_type = notification.stiType,
                    exposure_date = notification.exposureDate,
                    chain_data = notification.chainData,
                    is_read = if (notification.isRead) 1L else 0L,
                    received_at = notification.receivedAt,
                    updated_at = notification.updatedAt,
                    deleted_at = notification.deletedAt,
                )
            }
        }
    }

    private fun documentToNotification(
        id: String,
        data: Map<String, Any?>?,
    ): Notification? {
        if (data == null) return null
        return try {
            Notification(
                id = id,
                type = data[FirestoreCollections.NotificationFields.TYPE] as? String ?: "EXPOSURE",
                stiType = data[FirestoreCollections.NotificationFields.STI_TYPE] as? String,
                exposureDate = (data[FirestoreCollections.NotificationFields.EXPOSURE_DATE] as? Number)?.toLong(),
                chainData = data[FirestoreCollections.NotificationFields.CHAIN_DATA] as? String ?: "{}",
                isRead = (data[FirestoreCollections.NotificationFields.IS_READ] as? Boolean) ?: false,
                receivedAt =
                    (data[FirestoreCollections.NotificationFields.RECEIVED_AT] as? Number)?.toLong()
                        ?: currentTimeMillis(),
                updatedAt =
                    (data[FirestoreCollections.NotificationFields.UPDATED_AT] as? Number)?.toLong()
                        ?: currentTimeMillis(),
                deletedAt = (data[FirestoreCollections.NotificationFields.DELETED_AT] as? Number)?.toLong(),
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse notification", e)
            null
        }
    }

    private fun DbNotification.toDomain(): Notification =
        Notification(
            id = id,
            type = type,
            stiType = sti_type,
            exposureDate = exposure_date,
            chainData = chain_data,
            isRead = is_read == 1L,
            receivedAt = received_at,
            updatedAt = updated_at,
            deletedAt = deleted_at,
        )

    companion object {
        private const val TAG = "NotificationRepoImpl"
    }
}
