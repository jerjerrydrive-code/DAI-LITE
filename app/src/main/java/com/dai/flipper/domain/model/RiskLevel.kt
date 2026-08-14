package com.dai.flipper.domain.model

/**
 * Risk classification for Flipper operations.
 * Android computes the real risk level, ignoring the AI's assessment.
 */
enum class RiskLevel {
    /**
     * Safe operations that execute automatically.
     * Examples: list, read
     */
    LOW,

    /**
     * Modifications within project scope.
     * Shows diff before applying.
     * Examples: write inside project scope
     */
    MEDIUM,

    /**
     * Dangerous operations requiring explicit confirmation.
     * Examples: delete, move, overwrite, mass operations
     */
    HIGH,

    /**
     * Protected paths that require settings unlock.
     * Examples: system files, firmware areas
     */
    BLOCKED
}

/**
 * Risk assessment result with detailed information
 */
data class RiskAssessment(
    val level: RiskLevel,
    val reason: String,
    val affectedPaths: List<String>,
    val requiresDiff: Boolean,
    val requiresConfirmation: Boolean,
    val blockedReason: String? = null
)

/**
 * Protected path patterns that require elevated permissions
 */
object ProtectedPaths {
    val SYSTEM_PATHS = listOf(
        "/int/",
        "/int/.region",
        "/int/manifest.txt",
        "/ext/.region"
    )

    val FIRMWARE_PATHS = listOf(
        "/int/update/",
        "/ext/update/"
    )

    val SENSITIVE_EXTENSIONS = listOf(
        ".key",
        ".priv",
        ".secret"
    )

    fun isProtected(path: String): Boolean {
        return SYSTEM_PATHS.any { path.startsWith(it) } ||
               FIRMWARE_PATHS.any { path.startsWith(it) } ||
               SENSITIVE_EXTENSIONS.any { path.endsWith(it) }
    }

    fun isSystemPath(path: String): Boolean {
        return SYSTEM_PATHS.any { path.startsWith(it) }
    }

    fun isFirmwarePath(path: String): Boolean {
        return FIRMWARE_PATHS.any { path.startsWith(it) }
    }
}
