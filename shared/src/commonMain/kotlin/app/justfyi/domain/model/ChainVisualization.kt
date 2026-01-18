package app.justfyi.domain.model

import app.justfyi.util.Logger
import app.justfyi.util.currentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Test status of a user in the exposure chain.
 */
enum class TestStatus {
    /** User tested positive for the STI */
    POSITIVE,

    /** User tested negative for the STI */
    NEGATIVE,

    /** Test status unknown / not yet tested */
    UNKNOWN,
}

/**
 * A node in the exposure chain representing a user.
 *
 * @property username The username of the user at the time of interaction
 * @property testStatus The current test status of this user
 * @property date The date of interaction/exposure (millis since epoch)
 * @property isCurrentUser Whether this node represents the current user ("You")
 * @property testedPositiveFor Specific STI types the user tested positive for (subset of notification's STIs)
 */
@Serializable
data class ChainNode(
    val username: String,
    val testStatus: TestStatus = TestStatus.UNKNOWN,
    val date: Long? = null,
    val isCurrentUser: Boolean = false,
    val testedPositiveFor: List<String>? = null,
)

/**
 * Visualization data for an exposure notification chain.
 * Represents the path of potential exposure from the reporter to the current user.
 *
 * Example chain: "Someone (tested positive) -> User2 (no test) -> You"
 *
 * @property nodes List of nodes in the chain, ordered from reporter to current user (primary path)
 * @property paths All paths leading to this user (for multi-path visualization)
 * @property totalNodes Total number of users in the chain
 */
@Serializable
data class ChainVisualization(
    val nodes: List<ChainNode> = emptyList(),
    val paths: List<List<ChainNode>>? = null,
) {
    /**
     * Total number of nodes in the chain.
     */
    val totalNodes: Int get() = nodes.size

    /**
     * Returns true if this is a direct exposure (only 2 nodes: reporter and current user).
     */
    val isDirectExposure: Boolean get() = nodes.size == 2

    /**
     * Gets the reporter (first node) in the chain.
     * Returns null if chain is empty.
     */
    val reporter: ChainNode? get() = nodes.firstOrNull()

    /**
     * Gets the current user (last node) in the chain.
     * Returns null if chain is empty.
     */
    val currentUserNode: ChainNode? get() = nodes.lastOrNull()

    /**
     * Gets intermediary nodes (all nodes between reporter and current user).
     * Returns empty list if chain has 2 or fewer nodes.
     */
    val intermediaries: List<ChainNode> get() =
        if (nodes.size > 2) nodes.subList(1, nodes.size - 1) else emptyList()

    /**
     * Gets the direct contact - the person who exposed you (node right before "You").
     * For direct exposure, this is the reporter.
     * For indirect exposure, this is the last intermediary.
     * Returns null if chain has less than 2 nodes.
     */
    val directContact: ChainNode? get() =
        if (nodes.size >= 2) nodes[nodes.size - 2] else null

    /**
     * The number of hops from the reporter to you (0 = direct contact).
     */
    val hopCount: Int get() = maxOf(0, nodes.size - 2)

    /**
     * Checks if the current user (last node with isCurrentUser=true) tested negative.
     */
    fun currentUserTestedNegative(): Boolean = nodes.lastOrNull { it.isCurrentUser }?.testStatus == TestStatus.NEGATIVE

    /**
     * Checks if the current user (last node with isCurrentUser=true) tested positive.
     */
    fun currentUserTestedPositive(): Boolean = nodes.lastOrNull { it.isCurrentUser }?.testStatus == TestStatus.POSITIVE

    /**
     * Checks if someone OTHER than the current user tested negative.
     * Used to show "reduced risk" notice only when relevant.
     */
    fun hasOtherNegativeTestInChain(): Boolean =
        nodes.drop(1).any { it.testStatus == TestStatus.NEGATIVE && !it.isCurrentUser }

    /**
     * Creates a new ChainVisualization with the current user's status updated.
     * @param newStatus The new test status for the current user
     * @return A new ChainVisualization with updated status
     */
    fun withCurrentUserStatus(newStatus: TestStatus): ChainVisualization {
        val updatedNodes =
            nodes.map { node ->
                if (node.isCurrentUser) {
                    node.copy(testStatus = newStatus)
                } else {
                    node
                }
            }
        return copy(nodes = updatedNodes)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parses chain visualization from JSON string.
         *
         * @param jsonString The JSON string containing chain data
         * @return ChainVisualization or empty visualization if parsing fails
         */
        fun fromJson(jsonString: String): ChainVisualization {
            Logger.d(TAG, "fromJson called with: '$jsonString'")

            if (jsonString.isBlank() || jsonString == "{}") {
                Logger.d(TAG, "Empty or blank chainData received")
                return ChainVisualization(emptyList())
            }

            return try {
                val result = json.decodeFromString<ChainVisualization>(jsonString)
                Logger.d(TAG, "Successfully parsed chain with ${result.nodes.size} nodes")
                if (result.nodes.isNotEmpty()) {
                    Logger.d(TAG, "First node: ${result.nodes.first()}")
                }
                result
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to parse chainData: ${e.message}")
                Logger.e(TAG, "Exception type: ${e::class.simpleName}")
                Logger.d(TAG, "Problematic JSON (first 500 chars): ${jsonString.take(500)}")
                ChainVisualization(emptyList())
            }
        }

        private const val TAG = "ChainVisualization"

        /**
         * Converts chain visualization to JSON string.
         *
         * @return JSON string representation
         */
        fun ChainVisualization.toJson(): String = json.encodeToString(serializer(), this)

        /**
         * Maximum chain depth supported (number of hops from reporter).
         */
        const val MAX_CHAIN_DEPTH = 10

        /**
         * Sample usernames for preview chains.
         */
        private val sampleUsernames =
            listOf(
                "Alex",
                "Jordan",
                "Taylor",
                "Morgan",
                "Casey",
                "Riley",
                "Quinn",
                "Avery",
                "Skyler",
                "Drew",
            )

        /**
         * Creates a preview chain with the specified number of hops.
         *
         * @param hopCount Number of intermediaries (0 = direct contact, 1 = one hop, etc.)
         * @param directContactUsername The username to show for your direct contact
         * @param showDates Whether to include dates in the preview
         * @return ChainVisualization for preview purposes
         */
        fun createPreviewChain(
            hopCount: Int,
            directContactUsername: String = "Your Contact",
            showDates: Boolean = true,
        ): ChainVisualization {
            val clampedHops = hopCount.coerceIn(0, MAX_CHAIN_DEPTH)
            val now = currentTimeMillis()
            val dayInMillis = 24 * 60 * 60 * 1000L

            val nodes = mutableListOf<ChainNode>()

            // First node: Reporter (uses localization marker for client-side translation)
            nodes.add(
                ChainNode(
                    username = "@@chain_someone@@",
                    testStatus = TestStatus.POSITIVE,
                    date = if (showDates) now - (clampedHops + 1) * dayInMillis else null,
                    isCurrentUser = false,
                ),
            )

            // Intermediary nodes (if any)
            for (i in 0 until clampedHops) {
                val isDirectContact = i == clampedHops - 1
                nodes.add(
                    ChainNode(
                        username =
                            if (isDirectContact) {
                                directContactUsername
                            } else {
                                sampleUsernames[
                                    i %
                                        sampleUsernames.size,
                                ]
                            },
                        testStatus = TestStatus.UNKNOWN,
                        date = if (showDates) now - (clampedHops - i) * dayInMillis else null,
                        isCurrentUser = false,
                    ),
                )
            }

            // Last node: You
            nodes.add(
                ChainNode(
                    username = "You",
                    testStatus = TestStatus.UNKNOWN,
                    date = if (showDates) now else null,
                    isCurrentUser = true,
                ),
            )

            return ChainVisualization(nodes = nodes)
        }
    }
}
