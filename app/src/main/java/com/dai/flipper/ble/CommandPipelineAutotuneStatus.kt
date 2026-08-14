package com.dai.flipper.ble

enum class CommandPipelineProfile {
    FAST,
    BALANCED,
    RESILIENT
}

data class CommandPipelineAutotuneStatus(
    val profile: CommandPipelineProfile = CommandPipelineProfile.BALANCED,
    val cliRetryAttempts: Int = 3,
    val cliRetryDelayMs: Long = 300L,
    val rpcRetryAttempts: Int = 3,
    val rpcRetryDelayMs: Long = 320L,
    val fileWriteRetryAttempts: Int = 3,
    val fileWriteRetryDelayMs: Long = 350L,
    val successRate: Float = 1f,
    val averageLatencyMs: Long = 0L,
    val busySignals: Int = 0,
    val disconnectSignals: Int = 0,
    val sampleSize: Int = 0,
    val lastUpdatedMs: Long = 0L
) {
    val profileLabel: String
        get() = when (profile) {
            CommandPipelineProfile.FAST -> "Fast"
            CommandPipelineProfile.BALANCED -> "Balanced"
            CommandPipelineProfile.RESILIENT -> "Resilient"
        }
}
