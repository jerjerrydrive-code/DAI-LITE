package com.dai.flipper.domain.model

import android.net.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

/**
 * Chat message in the conversation with the AI agent.
 * Supports multimodal input with text and images.
 */
@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_results")
    val toolResults: List<ToolResult>? = null,
    val status: MessageStatus = MessageStatus.COMPLETE,
    val metadata: MessageMetadata? = null,
    @SerialName("image_attachments")
    val imageAttachments: List<ImageAttachment>? = null
)

/**
 * Image attachment for multimodal messages.
 * Stores base64-encoded image data for API transmission.
 */
@Serializable
data class ImageAttachment(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("base64_data")
    val base64Data: String,
    @SerialName("mime_type")
    val mimeType: String = "image/jpeg",
    @Transient
    val localUri: Uri? = null,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
enum class MessageRole {
    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT,

    @SerialName("system")
    SYSTEM,

    @SerialName("tool")
    TOOL
}

@Serializable
enum class MessageStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("streaming")
    STREAMING,

    @SerialName("complete")
    COMPLETE,

    @SerialName("error")
    ERROR,

    @SerialName("awaiting_approval")
    AWAITING_APPROVAL
}

@Serializable
data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val arguments: String // JSON string of ExecuteCommand
)

@Serializable
data class ToolResult(
    @SerialName("tool_call_id")
    val toolCallId: String,
    val content: String, // JSON string of CommandResult
    val success: Boolean = true
)

@Serializable
data class MessageMetadata(
    @SerialName("model_used")
    val modelUsed: String? = null,
    @SerialName("tokens_used")
    val tokensUsed: Int? = null,
    @SerialName("latency_ms")
    val latencyMs: Long? = null,
    @SerialName("pending_approval_id")
    val pendingApprovalId: String? = null
)

/**
 * Conversation state for the chat
 */
data class ConversationState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val pendingApproval: PendingApproval? = null,
    val progress: AgentProgress? = null,
    val error: String? = null,
    val sessionId: String = UUID.randomUUID().toString()
)

data class AgentProgress(
    val stage: AgentProgressStage,
    val detail: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AgentProgressStage {
    MODEL_REQUEST,
    TOOL_PLANNED,
    TOOL_EXECUTING,
    TOOL_COMPLETED,
    WAITING_APPROVAL
}
