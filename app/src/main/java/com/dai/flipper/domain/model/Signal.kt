package com.dai.flipper.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Unified signal capture model for all Flipper protocols.
 */
sealed class SignalCapture {
    abstract val name: String
    abstract val path: String
    abstract val type: SignalType
    abstract val timestamp: Long
    abstract val isFavorite: Boolean

    data class SubGhz(
        override val name: String,
        override val path: String,
        override val timestamp: Long,
        override val isFavorite: Boolean = false,
        val frequency: Long,           // Hz
        val protocol: String,          // e.g., "Princeton", "KeeLoq", "RAW"
        val preset: String,            // e.g., "FuriHalSubGhzPresetOok650Async"
        val bitLength: Int?,
        val rawData: List<Int>?,       // Raw timing data for waveform
        val keyData: String?           // Decoded key if available
    ) : SignalCapture() {
        override val type = SignalType.SUBGHZ
    }

    data class Infrared(
        override val name: String,
        override val path: String,
        override val timestamp: Long,
        override val isFavorite: Boolean = false,
        val protocol: String,          // e.g., "NEC", "Samsung", "RC5"
        val address: String,
        val command: String,
        val rawData: List<Int>?        // Raw timing for waveform
    ) : SignalCapture() {
        override val type = SignalType.INFRARED
    }

    data class Nfc(
        override val name: String,
        override val path: String,
        override val timestamp: Long,
        override val isFavorite: Boolean = false,
        val deviceType: String,        // e.g., "Mifare Classic 1K", "NTAG215"
        val uid: String,
        val atqa: String?,
        val sak: String?,
        val dataBlocks: List<NfcBlock>?
    ) : SignalCapture() {
        override val type = SignalType.NFC
    }

    data class Rfid(
        override val name: String,
        override val path: String,
        override val timestamp: Long,
        override val isFavorite: Boolean = false,
        val protocol: String,          // e.g., "EM4100", "HID", "Indala"
        val keyData: String
    ) : SignalCapture() {
        override val type = SignalType.RFID
    }

    data class IButton(
        override val name: String,
        override val path: String,
        override val timestamp: Long,
        override val isFavorite: Boolean = false,
        val protocol: String,          // e.g., "Dallas", "Cyfral", "Metakom"
        val keyData: String
    ) : SignalCapture() {
        override val type = SignalType.IBUTTON
    }
}

data class NfcBlock(
    val blockNumber: Int,
    val data: String,
    val accessBits: String? = null
)

enum class SignalType(val displayName: String, val directory: String, val extension: String) {
    SUBGHZ("Sub-GHz", "/ext/subghz", ".sub"),
    INFRARED("Infrared", "/ext/infrared", ".ir"),
    NFC("NFC", "/ext/nfc", ".nfc"),
    RFID("RFID", "/ext/lfrfid", ".rfid"),
    IBUTTON("iButton", "/ext/ibutton", ".ibtn")
}

/**
 * Waveform data for visualization
 */
data class WaveformData(
    val samples: List<WaveformSample>,
    val duration: Float,    // Total duration in ms
    val sampleRate: Float   // Samples per second
)

data class WaveformSample(
    val time: Float,        // Time in ms
    val value: Float        // Normalized value 0-1
)

/**
 * Parser for Flipper signal files
 */
object SignalParser {

    fun parseSubGhz(content: String, path: String, name: String): SignalCapture.SubGhz? {
        val lines = content.lines()
        val props = parseProperties(lines)

        val frequency = props["Frequency"]?.toLongOrNull() ?: return null
        val protocol = props["Protocol"] ?: "Unknown"
        val preset = props["Preset"] ?: ""

        // Parse RAW data if present
        val rawData = if (protocol == "RAW") {
            lines.filter { it.startsWith("RAW_Data:") }
                .flatMap { line ->
                    line.removePrefix("RAW_Data:").trim()
                        .split(" ")
                        .mapNotNull { it.toIntOrNull() }
                }
        } else null

        val keyData = props["Key"]
        val bitLength = props["Bit"]?.toIntOrNull()

        return SignalCapture.SubGhz(
            name = name,
            path = path,
            timestamp = System.currentTimeMillis(),
            frequency = frequency,
            protocol = protocol,
            preset = preset,
            bitLength = bitLength,
            rawData = rawData,
            keyData = keyData
        )
    }

    fun parseInfrared(content: String, path: String, name: String): List<SignalCapture.Infrared> {
        val signals = mutableListOf<SignalCapture.Infrared>()
        val lines = content.lines()

        var currentName = ""
        var currentProtocol = ""
        var currentAddress = ""
        var currentCommand = ""

        for (line in lines) {
            when {
                line.startsWith("name:") -> currentName = line.substringAfter(":").trim()
                line.startsWith("type:") -> { /* parsed or raw */ }
                line.startsWith("protocol:") -> currentProtocol = line.substringAfter(":").trim()
                line.startsWith("address:") -> currentAddress = line.substringAfter(":").trim()
                line.startsWith("command:") -> {
                    currentCommand = line.substringAfter(":").trim()
                    signals.add(
                        SignalCapture.Infrared(
                            name = currentName.ifEmpty { name },
                            path = path,
                            timestamp = System.currentTimeMillis(),
                            protocol = currentProtocol,
                            address = currentAddress,
                            command = currentCommand,
                            rawData = null
                        )
                    )
                }
            }
        }

        return signals
    }

    fun parseNfc(content: String, path: String, name: String): SignalCapture.Nfc? {
        val lines = content.lines()
        val props = parseProperties(lines)

        val deviceType = props["Device type"] ?: return null
        val uid = props["UID"] ?: return null

        val blocks = mutableListOf<NfcBlock>()
        lines.filter { it.startsWith("Block ") }.forEach { line ->
            val blockNum = line.substringAfter("Block ").substringBefore(":").toIntOrNull()
            val data = line.substringAfter(":").trim()
            if (blockNum != null) {
                blocks.add(NfcBlock(blockNum, data))
            }
        }

        return SignalCapture.Nfc(
            name = name,
            path = path,
            timestamp = System.currentTimeMillis(),
            deviceType = deviceType,
            uid = uid,
            atqa = props["ATQA"],
            sak = props["SAK"],
            dataBlocks = blocks.takeIf { it.isNotEmpty() }
        )
    }

    fun parseRfid(content: String, path: String, name: String): SignalCapture.Rfid? {
        val lines = content.lines()
        val props = parseProperties(lines)

        val protocol = props["Protocol"] ?: return null
        val keyData = props["Data"] ?: return null

        return SignalCapture.Rfid(
            name = name,
            path = path,
            timestamp = System.currentTimeMillis(),
            protocol = protocol,
            keyData = keyData
        )
    }

    fun parseIButton(content: String, path: String, name: String): SignalCapture.IButton? {
        val lines = content.lines()
        val props = parseProperties(lines)

        val protocol = props["Protocol"] ?: return null
        val keyData = props["Data"] ?: props["Key"] ?: return null

        return SignalCapture.IButton(
            name = name,
            path = path,
            timestamp = System.currentTimeMillis(),
            protocol = protocol,
            keyData = keyData
        )
    }

    private fun parseProperties(lines: List<String>): Map<String, String> {
        return lines.filter { it.contains(":") && !it.startsWith("#") }
            .associate { line ->
                val key = line.substringBefore(":").trim()
                val value = line.substringAfter(":").trim()
                key to value
            }
    }

    /**
     * Convert raw timing data to waveform samples
     */
    fun rawToWaveform(rawData: List<Int>): WaveformData {
        val samples = mutableListOf<WaveformSample>()
        var currentTime = 0f
        var isHigh = true

        for (duration in rawData) {
            val absDuration = kotlin.math.abs(duration).toFloat() / 1000f // Convert µs to ms
            samples.add(WaveformSample(currentTime, if (isHigh) 1f else 0f))
            currentTime += absDuration
            samples.add(WaveformSample(currentTime, if (isHigh) 1f else 0f))
            isHigh = !isHigh
        }

        return WaveformData(
            samples = samples,
            duration = currentTime,
            sampleRate = samples.size / currentTime
        )
    }
}

/**
 * Represents a signal ready for replay
 */
data class ReplayRequest(
    val signal: SignalCapture,
    val repeatCount: Int = 1
)
