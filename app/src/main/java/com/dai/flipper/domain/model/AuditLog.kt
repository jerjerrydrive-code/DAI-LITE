package com.dai.flipper.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Audit log entry for tracking all agent actions.
 * Everything is logged for accountability and replay.
 */
@Serializable
data class AuditEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    @SerialName("action_type")
    val actionType: AuditActionType,
    val command: ExecuteCommand? = null,
    val result: CommandResult? = null,
    @SerialName("risk_level")
    val riskLevel: RiskLevel? = null,
    @SerialName("user_approved")
    val userApproved: Boolean? = null,
    @SerialName("approval_method")
    val approvalMethod: ApprovalMethod? = null,
    @SerialName("session_id")
    val sessionId: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class AuditActionType {
    @SerialName("command_received")
    COMMAND_RECEIVED,

    @SerialName("command_executed")
    COMMAND_EXECUTED,

    @SerialName("command_failed")
    COMMAND_FAILED,

    @SerialName("command_blocked")
    COMMAND_BLOCKED,

    @SerialName("approval_requested")
    APPROVAL_REQUESTED,

    @SerialName("approval_granted")
    APPROVAL_GRANTED,

    @SerialName("approval_denied")
    APPROVAL_DENIED,

    @SerialName("approval_timeout")
    APPROVAL_TIMEOUT,

    @SerialName("permission_granted")
    PERMISSION_GRANTED,

    @SerialName("permission_revoked")
    PERMISSION_REVOKED,

    @SerialName("permission_expired")
    PERMISSION_EXPIRED,

    @SerialName("device_connected")
    DEVICE_CONNECTED,

    @SerialName("device_disconnected")
    DEVICE_DISCONNECTED,

    @SerialName("session_started")
    SESSION_STARTED,

    @SerialName("session_ended")
    SESSION_ENDED,

    @SerialName("ai_request")
    AI_REQUEST,

    @SerialName("ai_response")
    AI_RESPONSE,

    @SerialName("error")
    ERROR
}

@Serializable
enum class ApprovalMethod {
    @SerialName("auto")
    AUTO,

    @SerialName("diff_review")
    DIFF_REVIEW,

    @SerialName("hold_confirm")
    HOLD_CONFIRM,

    @SerialName("settings_unlock")
    SETTINGS_UNLOCK
}

/**
 * Session tracking for audit grouping
 */
@Serializable
data class AuditSession(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("started_at")
    val startedAt: Long = System.currentTimeMillis(),
    @SerialName("ended_at")
    val endedAt: Long? = null,
    @SerialName("device_name")
    val deviceName: String? = null,
    @SerialName("command_count")
    val commandCount: Int = 0,
    @SerialName("approval_count")
    val approvalCount: Int = 0,
    @SerialName("rejection_count")
    val rejectionCount: Int = 0
)

/**
 * Summary statistics for audit display
 */
data class AuditSummary(
    val totalCommands: Int,
    val successfulCommands: Int,
    val failedCommands: Int,
    val blockedCommands: Int,
    val approvalRate: Float,
    val mostCommonActions: List<Pair<CommandAction, Int>>,
    val riskBreakdown: Map<RiskLevel, Int>,
    val sessionDuration: Long
)
