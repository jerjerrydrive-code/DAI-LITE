package com.dai.flipper.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Scoped permission for path-based access control.
 * Permissions are time-limited and explicitly granted.
 */
@Serializable
data class Permission(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("path_pattern")
    val pathPattern: String,
    @SerialName("allowed_actions")
    val allowedActions: Set<CommandAction>,
    @SerialName("granted_at")
    val grantedAt: Long = System.currentTimeMillis(),
    @SerialName("expires_at")
    val expiresAt: Long,
    @SerialName("granted_by")
    val grantedBy: String = "user",
    val active: Boolean = true
) {
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt

    fun isValid(): Boolean = active && !isExpired()

    fun matches(path: String, action: CommandAction): Boolean {
        if (!isValid()) return false
        if (action !in allowedActions) return false
        return matchesPath(path)
    }

    private fun matchesPath(path: String): Boolean {
        // Support glob-style patterns
        val regex = pathPattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
            .toRegex()
        return regex.matches(path)
    }

    fun remainingTimeMs(): Long {
        return (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
    }

    companion object {
        const val DURATION_5_MINUTES = 5 * 60 * 1000L
        const val DURATION_15_MINUTES = 15 * 60 * 1000L
        const val DURATION_1_HOUR = 60 * 60 * 1000L
        const val DURATION_SESSION = 24 * 60 * 60 * 1000L

        fun createForPath(
            path: String,
            actions: Set<CommandAction>,
            duration: Long = DURATION_15_MINUTES
        ): Permission {
            return Permission(
                pathPattern = path,
                allowedActions = actions,
                expiresAt = System.currentTimeMillis() + duration
            )
        }

        fun createProjectScope(projectPath: String): Permission {
            return Permission(
                pathPattern = "$projectPath/*",
                allowedActions = setOf(
                    CommandAction.LIST_DIRECTORY,
                    CommandAction.READ_FILE,
                    CommandAction.WRITE_FILE,
                    CommandAction.CREATE_DIRECTORY
                ),
                expiresAt = System.currentTimeMillis() + DURATION_SESSION
            )
        }
    }
}

/**
 * Permission request shown to user
 */
data class PermissionRequest(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val action: CommandAction,
    val justification: String,
    val suggestedDuration: Long = Permission.DURATION_15_MINUTES,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Pending action waiting for user approval
 */
data class PendingApproval(
    val id: String = UUID.randomUUID().toString(),
    val command: ExecuteCommand,
    val riskAssessment: RiskAssessment,
    val diff: FileDiff? = null,
    val traceId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 5 * 60 * 1000 // 5 min expiry
)
