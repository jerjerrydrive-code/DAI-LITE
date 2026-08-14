package com.dai.flipper.domain.mode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Operation Modes
 *
 * Different operational modes that change app behavior:
 * - STANDARD: Normal operation, all features available
 * - RECON: Passive scanning and intelligence gathering
 * - INTEL: Analysis and research mode
 * - STEALTH: Minimal RF emissions, covert operation
 * - ASSAULT: Active testing mode (requires explicit unlock)
 */

enum class OperationMode(
    val displayName: String,
    val icon: String,
    val description: String,
    val color: Long
) {
    STANDARD(
        displayName = "Standard",
        icon = "⚡",
        description = "Normal operation with all features available",
        color = 0xFF2196F3
    ),
    RECON(
        displayName = "Recon",
        icon = "👁️",
        description = "Passive scanning and intelligence gathering. Listens only, no transmissions.",
        color = 0xFF4CAF50
    ),
    INTEL(
        displayName = "Intel",
        icon = "🔍",
        description = "Analysis mode for studying captured signals and protocols.",
        color = 0xFF9C27B0
    ),
    STEALTH(
        displayName = "Stealth",
        icon = "🥷",
        description = "Minimal RF footprint. Disables unnecessary wireless activity.",
        color = 0xFF607D8B
    ),
    ASSAULT(
        displayName = "Assault",
        icon = "⚔️",
        description = "Active testing mode. Enables transmission features. Use responsibly.",
        color = 0xFFF44336
    )
}

/**
 * Mode-specific configuration
 */
data class ModeConfig(
    val mode: OperationMode,
    val enableBleScanning: Boolean = true,
    val enableSubGhzRx: Boolean = true,
    val enableSubGhzTx: Boolean = true,
    val enableNfcRead: Boolean = true,
    val enableNfcWrite: Boolean = true,
    val enableInfrared: Boolean = true,
    val enableDeviceTracking: Boolean = true,
    val enableLogging: Boolean = true,
    val enableNotifications: Boolean = true,
    val bleAdvertising: Boolean = true,
    val wifiEnabled: Boolean = true,
    val autoConnect: Boolean = true,
    val confirmHighRiskOps: Boolean = true,
    val requireHoldToConfirm: Boolean = true
) {
    companion object {
        fun forMode(mode: OperationMode): ModeConfig = when (mode) {
            OperationMode.STANDARD -> ModeConfig(
                mode = mode
                // All defaults (everything enabled)
            )
            OperationMode.RECON -> ModeConfig(
                mode = mode,
                enableSubGhzTx = false,      // No transmitting
                enableNfcWrite = false,       // No writing
                enableInfrared = false,       // No IR (visible)
                bleAdvertising = false,       // Don't advertise
                autoConnect = false,          // Manual connect only
                enableLogging = true,         // Enhanced logging
                enableDeviceTracking = true   // Track everything seen
            )
            OperationMode.INTEL -> ModeConfig(
                mode = mode,
                enableSubGhzTx = false,       // Analysis only
                enableNfcWrite = false,
                autoConnect = false,
                enableNotifications = false,  // Focus mode
                confirmHighRiskOps = true
            )
            OperationMode.STEALTH -> ModeConfig(
                mode = mode,
                enableBleScanning = false,    // No BLE activity
                enableSubGhzTx = false,
                enableSubGhzRx = false,       // Radio silence
                enableNfcRead = false,
                enableNfcWrite = false,
                enableInfrared = false,
                enableDeviceTracking = false,
                enableLogging = false,        // No logs
                enableNotifications = false,
                bleAdvertising = false,
                wifiEnabled = false,          // Airplane mode
                autoConnect = false
            )
            OperationMode.ASSAULT -> ModeConfig(
                mode = mode,
                enableSubGhzTx = true,
                enableNfcWrite = true,
                enableInfrared = true,
                confirmHighRiskOps = true,    // Always confirm
                requireHoldToConfirm = true   // Hold to confirm
            )
        }
    }
}

/**
 * Mode restrictions - what's blocked in each mode
 */
data class ModeRestrictions(
    val mode: OperationMode,
    val blockedActions: List<String>,
    val warnings: List<String>,
    val requiredPermissions: List<String>
) {
    companion object {
        fun forMode(mode: OperationMode): ModeRestrictions = when (mode) {
            OperationMode.STANDARD -> ModeRestrictions(
                mode = mode,
                blockedActions = emptyList(),
                warnings = emptyList(),
                requiredPermissions = emptyList()
            )
            OperationMode.RECON -> ModeRestrictions(
                mode = mode,
                blockedActions = listOf(
                    "subghz_transmit",
                    "nfc_write",
                    "infrared_send",
                    "badusb_execute"
                ),
                warnings = listOf(
                    "Transmission capabilities are disabled",
                    "NFC write is blocked",
                    "IR is disabled (visible emissions)"
                ),
                requiredPermissions = emptyList()
            )
            OperationMode.INTEL -> ModeRestrictions(
                mode = mode,
                blockedActions = listOf(
                    "subghz_transmit",
                    "nfc_write",
                    "badusb_execute"
                ),
                warnings = listOf(
                    "Analysis mode - transmission disabled",
                    "Notifications are muted"
                ),
                requiredPermissions = emptyList()
            )
            OperationMode.STEALTH -> ModeRestrictions(
                mode = mode,
                blockedActions = listOf(
                    "subghz_transmit",
                    "subghz_receive",
                    "nfc_read",
                    "nfc_write",
                    "ble_scan",
                    "infrared_send",
                    "badusb_execute",
                    "wifi_scan"
                ),
                warnings = listOf(
                    "All RF features disabled",
                    "Flipper connection may be limited",
                    "Consider enabling airplane mode on phone"
                ),
                requiredPermissions = emptyList()
            )
            OperationMode.ASSAULT -> ModeRestrictions(
                mode = mode,
                blockedActions = emptyList(),
                warnings = listOf(
                    "⚠️ ASSAULT MODE ACTIVE",
                    "All transmission features enabled",
                    "Ensure you have authorization",
                    "Actions may be logged"
                ),
                requiredPermissions = listOf(
                    "explicit_user_consent",
                    "assault_mode_unlock"
                )
            )
        }
    }
}

/**
 * Operation Mode Manager
 * Singleton that manages the current operation mode
 */
object OperationModeManager {
    private val _currentMode = MutableStateFlow(OperationMode.STANDARD)
    val currentMode: StateFlow<OperationMode> = _currentMode.asStateFlow()

    private val _currentConfig = MutableStateFlow(ModeConfig.forMode(OperationMode.STANDARD))
    val currentConfig: StateFlow<ModeConfig> = _currentConfig.asStateFlow()

    private val _assaultModeUnlocked = MutableStateFlow(false)
    val assaultModeUnlocked: StateFlow<Boolean> = _assaultModeUnlocked.asStateFlow()

    private val _modeHistory = mutableListOf<ModeChange>()

    fun setMode(mode: OperationMode): Result<Unit> {
        // Check if assault mode requires unlock
        if (mode == OperationMode.ASSAULT && !_assaultModeUnlocked.value) {
            return Result.failure(SecurityException("Assault mode requires explicit unlock"))
        }

        val previousMode = _currentMode.value
        _currentMode.value = mode
        _currentConfig.value = ModeConfig.forMode(mode)

        // Log mode change
        _modeHistory.add(ModeChange(
            from = previousMode,
            to = mode,
            timestamp = System.currentTimeMillis()
        ))

        return Result.success(Unit)
    }

    fun unlockAssaultMode(confirmation: String): Boolean {
        // Require explicit confirmation
        if (confirmation == "I UNDERSTAND THE RISKS") {
            _assaultModeUnlocked.value = true
            return true
        }
        return false
    }

    fun lockAssaultMode() {
        _assaultModeUnlocked.value = false
        if (_currentMode.value == OperationMode.ASSAULT) {
            setMode(OperationMode.STANDARD)
        }
    }

    fun isActionAllowed(action: String): Boolean {
        val restrictions = ModeRestrictions.forMode(_currentMode.value)
        return action !in restrictions.blockedActions
    }

    fun getBlockedReason(action: String): String? {
        val restrictions = ModeRestrictions.forMode(_currentMode.value)
        return if (action in restrictions.blockedActions) {
            "Action '$action' is blocked in ${_currentMode.value.displayName} mode"
        } else {
            null
        }
    }

    fun getModeHistory(): List<ModeChange> = _modeHistory.toList()

    data class ModeChange(
        val from: OperationMode,
        val to: OperationMode,
        val timestamp: Long
    )
}

/**
 * Quick actions available per mode
 */
data class ModeQuickAction(
    val id: String,
    val name: String,
    val icon: String,
    val action: String
)

object ModeQuickActions {
    fun getActionsForMode(mode: OperationMode): List<ModeQuickAction> = when (mode) {
        OperationMode.STANDARD -> listOf(
            ModeQuickAction("scan_ble", "BLE Scan", "🔵", "ble_scan"),
            ModeQuickAction("scan_subghz", "Sub-GHz Scan", "📡", "subghz_receive"),
            ModeQuickAction("read_nfc", "Read NFC", "💳", "nfc_read"),
            ModeQuickAction("browse_files", "Files", "📁", "file_browse")
        )
        OperationMode.RECON -> listOf(
            ModeQuickAction("passive_ble", "Passive BLE", "👁️", "ble_passive"),
            ModeQuickAction("listen_subghz", "Listen RF", "📻", "subghz_listen"),
            ModeQuickAction("track_devices", "Track", "📍", "device_track"),
            ModeQuickAction("export_intel", "Export", "💾", "export_data")
        )
        OperationMode.INTEL -> listOf(
            ModeQuickAction("analyze_signal", "Analyze", "🔬", "signal_analyze"),
            ModeQuickAction("decode_proto", "Decode", "🔓", "protocol_decode"),
            ModeQuickAction("compare_signals", "Compare", "⚖️", "signal_compare"),
            ModeQuickAction("generate_report", "Report", "📊", "generate_report")
        )
        OperationMode.STEALTH -> listOf(
            ModeQuickAction("check_status", "Status", "📊", "status_check"),
            ModeQuickAction("view_cache", "Cached Data", "💾", "view_cache"),
            ModeQuickAction("offline_analyze", "Offline Analysis", "🔍", "offline_analyze")
        )
        OperationMode.ASSAULT -> listOf(
            ModeQuickAction("transmit", "Transmit", "📡", "subghz_transmit"),
            ModeQuickAction("replay", "Replay", "🔄", "signal_replay"),
            ModeQuickAction("bruteforce", "Bruteforce", "💪", "bruteforce"),
            ModeQuickAction("write_nfc", "Write NFC", "✏️", "nfc_write")
        )
    }
}
