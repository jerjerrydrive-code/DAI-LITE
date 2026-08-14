package com.dai.flipper.ble

/**
 * Runtime status of BLE CLI/RPC automation capability.
 *
 * READY means command transport is responsive and can be used for automation.
 * UNAVAILABLE means transport did not respond or is currently unusable.
 */
enum class CliCapabilityLevel {
    UNKNOWN,
    READY,
    UNAVAILABLE
}

data class CliCapabilityStatus(
    val level: CliCapabilityLevel = CliCapabilityLevel.UNKNOWN,
    val checkedAtMs: Long = 0L,
    val supportsCli: Boolean = false,
    val supportsRpc: Boolean = false,
    val firmwareHint: String? = null,
    val details: String = "CLI capability has not been checked yet."
) {
    val isReady: Boolean
        get() = level == CliCapabilityLevel.READY
}
