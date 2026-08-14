package com.dai.flipper.ble

enum class ConnectionCheckLevel {
    PASS,
    WARN,
    FAIL,
    SKIPPED
}

data class ConnectionCheckResult(
    val name: String,
    val level: ConnectionCheckLevel,
    val detail: String,
    val elapsedMs: Long = 0L
)

data class ConnectionDiagnosticsReport(
    val startedAtMs: Long = 0L,
    val completedAtMs: Long = 0L,
    val checks: List<ConnectionCheckResult> = emptyList()
) {
    val durationMs: Long
        get() = if (startedAtMs > 0L && completedAtMs >= startedAtMs) {
            completedAtMs - startedAtMs
        } else {
            0L
        }

    val passedCount: Int
        get() = checks.count { it.level == ConnectionCheckLevel.PASS }

    val warningCount: Int
        get() = checks.count { it.level == ConnectionCheckLevel.WARN }

    val failedCount: Int
        get() = checks.count { it.level == ConnectionCheckLevel.FAIL }

    val hasFailures: Boolean
        get() = failedCount > 0

    val hasWarnings: Boolean
        get() = warningCount > 0

    val summary: String
        get() = when {
            checks.isEmpty() -> "No link diagnostics run yet."
            hasFailures -> "Link diagnostics found blocking failures ($failedCount/${checks.size})."
            hasWarnings -> "Link diagnostics found partial readiness ($warningCount warnings)."
            else -> "Link diagnostics passed ($passedCount/${checks.size})."
        }

    companion object {
        fun idle(): ConnectionDiagnosticsReport = ConnectionDiagnosticsReport()
    }
}

