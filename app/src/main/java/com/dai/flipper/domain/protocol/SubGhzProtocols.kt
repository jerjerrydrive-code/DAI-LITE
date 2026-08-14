package com.dai.flipper.domain.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Sub-GHz Protocol Parsers
 *
 * Parses and analyzes various Sub-GHz RF protocols:
 * - Garage door remotes (Chamberlain, LiftMaster, Linear)
 * - Car key fobs (rolling codes, static codes)
 * - TPMS sensors (tire pressure monitoring)
 * - Weather stations
 * - Doorbells
 * - Security sensors
 * - Generic OOK/FSK signals
 */

sealed class SubGhzProtocol(
    val name: String,
    val frequency: Long,
    val modulation: Modulation
) {
    abstract fun parse(rawData: String): ParsedSignal?
    abstract fun analyze(signal: ParsedSignal): SignalAnalysis
}

// ==================== Parsed Signal ====================

@Serializable
data class ParsedSignal(
    val protocol: String,
    val frequency: Long,
    val modulation: String,
    val data: Map<String, String>,
    val rawBits: String? = null,
    val isRollingCode: Boolean = false,
    val serialNumber: String? = null,
    val button: String? = null,
    val counter: Long? = null
)

@Serializable
data class SignalAnalysis(
    val protocol: String,
    val description: String,
    val deviceType: String,
    val manufacturer: String?,
    val securityLevel: SecurityLevel,
    val vulnerabilities: List<String>,
    val recommendations: List<String>,
    val canReplay: Boolean,
    val canBruteforce: Boolean,
    val estimatedBruteforceTime: String?
)

enum class SecurityLevel(val displayName: String, val color: Long) {
    NONE("No Security", 0xFFF44336),
    LOW("Low", 0xFFFF9800),
    MEDIUM("Medium", 0xFFFFEB3B),
    HIGH("High", 0xFF4CAF50),
    VERY_HIGH("Very High", 0xFF2196F3)
}

enum class Modulation(val displayName: String) {
    OOK("OOK (On-Off Keying)"),
    FSK("FSK (Frequency Shift Keying)"),
    GFSK("GFSK (Gaussian FSK)"),
    ASK("ASK (Amplitude Shift Keying)"),
    PSK("PSK (Phase Shift Keying)")
}

// ==================== Protocol Implementations ====================

/**
 * Princeton Protocol - Common for cheap 433MHz remotes
 */
object PrincetonProtocol : SubGhzProtocol(
    name = "Princeton",
    frequency = 433_920_000,
    modulation = Modulation.OOK
) {
    override fun parse(rawData: String): ParsedSignal? {
        // Princeton format: 24-bit code (20 address + 4 data)
        val match = Regex("Key:([A-F0-9]+)").find(rawData) ?: return null
        val key = match.groupValues[1]

        if (key.length < 6) return null

        val address = key.dropLast(1)
        val button = key.takeLast(1)

        return ParsedSignal(
            protocol = name,
            frequency = frequency,
            modulation = modulation.name,
            data = mapOf(
                "key" to key,
                "address" to address,
                "button" to button
            ),
            isRollingCode = false,
            button = when (button) {
                "E" -> "Button A"
                "D" -> "Button B"
                "B" -> "Button C"
                "7" -> "Button D"
                else -> "Button $button"
            }
        )
    }

    override fun analyze(signal: ParsedSignal): SignalAnalysis {
        return SignalAnalysis(
            protocol = name,
            description = "Princeton is a simple 24-bit static code protocol commonly used in cheap 433MHz remotes.",
            deviceType = "Generic Remote",
            manufacturer = null,
            securityLevel = SecurityLevel.NONE,
            vulnerabilities = listOf(
                "Static code - easily captured and replayed",
                "No encryption or rolling code",
                "24-bit keyspace is small"
            ),
            recommendations = listOf(
                "Upgrade to rolling code system",
                "Add time-based validation"
            ),
            canReplay = true,
            canBruteforce = true,
            estimatedBruteforceTime = "~2 hours at 10 codes/sec"
        )
    }
}

/**
 * Came protocol - European gate/garage remotes
 */
object CameProtocol : SubGhzProtocol(
    name = "CAME",
    frequency = 433_920_000,
    modulation = Modulation.OOK
) {
    override fun parse(rawData: String): ParsedSignal? {
        val keyMatch = Regex("Key:([A-F0-9]+)").find(rawData) ?: return null
        val key = keyMatch.groupValues[1]

        return ParsedSignal(
            protocol = name,
            frequency = frequency,
            modulation = modulation.name,
            data = mapOf("key" to key),
            isRollingCode = false,
            serialNumber = key.take(10)
        )
    }

    override fun analyze(signal: ParsedSignal): SignalAnalysis {
        return SignalAnalysis(
            protocol = name,
            description = "CAME is a 12-bit protocol used in European gate and garage door openers.",
            deviceType = "Gate/Garage Remote",
            manufacturer = "CAME",
            securityLevel = SecurityLevel.NONE,
            vulnerabilities = listOf(
                "12-bit code = only 4096 combinations",
                "Static code - no rolling code protection",
                "Can be bruteforced in minutes"
            ),
            recommendations = listOf(
                "Upgrade to CAME TOP or CAME TWIN rolling code"
            ),
            canReplay = true,
            canBruteforce = true,
            estimatedBruteforceTime = "~7 minutes at 10 codes/sec"
        )
    }
}

/**
 * KeeLoq - Rolling code protocol by Microchip
 * Used by Chamberlain, LiftMaster, and many automotive key fobs
 */
object KeeLoqProtocol : SubGhzProtocol(
    name = "KeeLoq",
    frequency = 433_920_000,
    modulation = Modulation.OOK
) {
    override fun parse(rawData: String): ParsedSignal? {
        val fixMatch = Regex("Fix:([A-F0-9]+)").find(rawData)
        val hopMatch = Regex("Hop:([A-F0-9]+)").find(rawData)
        val cntMatch = Regex("Cnt:([A-F0-9]+)").find(rawData)
        val btnMatch = Regex("Btn:([A-F0-9]+)").find(rawData)

        val fix = fixMatch?.groupValues?.get(1) ?: return null
        val hop = hopMatch?.groupValues?.get(1)

        return ParsedSignal(
            protocol = name,
            frequency = frequency,
            modulation = modulation.name,
            data = buildMap {
                put("fix", fix)
                hop?.let { put("hop", it) }
                cntMatch?.groupValues?.get(1)?.let { put("counter", it) }
                btnMatch?.groupValues?.get(1)?.let { put("button", it) }
            },
            isRollingCode = true,
            serialNumber = fix.takeLast(8),
            counter = cntMatch?.groupValues?.get(1)?.toLongOrNull(16),
            button = btnMatch?.groupValues?.get(1)
        )
    }

    override fun analyze(signal: ParsedSignal): SignalAnalysis {
        return SignalAnalysis(
            protocol = name,
            description = "KeeLoq is a rolling code protocol using a 32-bit NLFSR and 64-bit manufacturer key.",
            deviceType = "Garage/Gate Remote or Key Fob",
            manufacturer = "Microchip (Chamberlain/LiftMaster)",
            securityLevel = SecurityLevel.MEDIUM,
            vulnerabilities = listOf(
                "Vulnerable to 'rolljam' attack (jam + capture)",
                "Some manufacturer keys have been leaked",
                "Replay attack possible with 2+ captured codes"
            ),
            recommendations = listOf(
                "Use latest firmware with updated keys",
                "Implement jamming detection",
                "Consider dual-band systems"
            ),
            canReplay = false,
            canBruteforce = false,
            estimatedBruteforceTime = "Days to weeks without manufacturer key"
        )
    }
}

/**
 * TPMS - Tire Pressure Monitoring System
 */
object TpmsProtocol : SubGhzProtocol(
    name = "TPMS",
    frequency = 433_920_000,
    modulation = Modulation.FSK
) {
    override fun parse(rawData: String): ParsedSignal? {
        val idMatch = Regex("Id:([A-F0-9]+)").find(rawData)
        val pressMatch = Regex("Pressure:([0-9.]+)").find(rawData)
        val tempMatch = Regex("Temp:([0-9.-]+)").find(rawData)
        val battMatch = Regex("Battery:([A-Za-z]+)").find(rawData)

        val id = idMatch?.groupValues?.get(1) ?: return null

        return ParsedSignal(
            protocol = name,
            frequency = frequency,
            modulation = modulation.name,
            data = buildMap {
                put("sensor_id", id)
                pressMatch?.groupValues?.get(1)?.let { put("pressure_kpa", it) }
                tempMatch?.groupValues?.get(1)?.let { put("temperature_c", it) }
                battMatch?.groupValues?.get(1)?.let { put("battery", it) }
            },
            isRollingCode = false,
            serialNumber = id
        )
    }

    override fun analyze(signal: ParsedSignal): SignalAnalysis {
        val pressure = signal.data["pressure_kpa"]?.toFloatOrNull()
        val temp = signal.data["temperature_c"]?.toFloatOrNull()

        return SignalAnalysis(
            protocol = name,
            description = "TPMS sensors transmit tire pressure and temperature wirelessly to the vehicle's ECU.",
            deviceType = "Tire Pressure Sensor",
            manufacturer = null,
            securityLevel = SecurityLevel.NONE,
            vulnerabilities = listOf(
                "No authentication - sensor IDs can be spoofed",
                "Can trigger false low-pressure warnings",
                "Can be used for vehicle tracking"
            ),
            recommendations = listOf(
                "Be aware of privacy implications",
                "Some vehicles allow disabling TPMS display"
            ),
            canReplay = true,
            canBruteforce = false,
            estimatedBruteforceTime = null
        )
    }
}

/**
 * Weather Station protocols (Oregon Scientific, Acurite, etc.)
 */
object WeatherStationProtocol : SubGhzProtocol(
    name = "Weather Station",
    frequency = 433_920_000,
    modulation = Modulation.OOK
) {
    override fun parse(rawData: String): ParsedSignal? {
        val tempMatch = Regex("Temp:([0-9.-]+)").find(rawData)
        val humMatch = Regex("Humidity:([0-9]+)").find(rawData)
        val idMatch = Regex("Id:([A-F0-9]+)").find(rawData)
        val chanMatch = Regex("Ch:([0-9]+)").find(rawData)

        return ParsedSignal(
            protocol = name,
            frequency = frequency,
            modulation = modulation.name,
            data = buildMap {
                idMatch?.groupValues?.get(1)?.let { put("sensor_id", it) }
                chanMatch?.groupValues?.get(1)?.let { put("channel", it) }
                tempMatch?.groupValues?.get(1)?.let { put("temperature_c", it) }
                humMatch?.groupValues?.get(1)?.let { put("humidity", it) }
            },
            isRollingCode = false,
            serialNumber = idMatch?.groupValues?.get(1)
        )
    }

    override fun analyze(signal: ParsedSignal): SignalAnalysis {
        return SignalAnalysis(
            protocol = name,
            description = "Wireless weather station sensor transmitting temperature and humidity data.",
            deviceType = "Weather Sensor",
            manufacturer = "Various (Oregon Scientific, Acurite, etc.)",
            securityLevel = SecurityLevel.NONE,
            vulnerabilities = listOf(
                "Unencrypted transmission",
                "Can be spoofed to show false readings"
            ),
            recommendations = listOf(
                "Use for monitoring only, not critical decisions"
            ),
            canReplay = true,
            canBruteforce = false,
            estimatedBruteforceTime = null
        )
    }
}

/**
 * Nice FLO - European gate remotes
 */
object NiceFloProtocol : SubGhzProtocol(
    name = "Nice FLO",
    frequency = 433_920_000,
    modulation = Modulation.OOK
) {
    override fun parse(rawData: String): ParsedSignal? {
        val keyMatch = Regex("Key:([A-F0-9]+)").find(rawData) ?: return null
        val key = keyMatch.groupValues[1]

        return ParsedSignal(
            protocol = name,
            frequency = frequency,
            modulation = modulation.name,
            data = mapOf("key" to key),
            isRollingCode = false,
            serialNumber = key
        )
    }

    override fun analyze(signal: ParsedSignal): SignalAnalysis {
        return SignalAnalysis(
            protocol = name,
            description = "Nice FLO is a 12-bit static code protocol used in European gate systems.",
            deviceType = "Gate Remote",
            manufacturer = "Nice",
            securityLevel = SecurityLevel.NONE,
            vulnerabilities = listOf(
                "12-bit code = only 4096 combinations",
                "Static code - easily captured",
                "Can be bruteforced in under 10 minutes"
            ),
            recommendations = listOf(
                "Upgrade to Nice FLOR/SMILO rolling code remotes"
            ),
            canReplay = true,
            canBruteforce = true,
            estimatedBruteforceTime = "~7 minutes at 10 codes/sec"
        )
    }
}

/**
 * Honeywell Security Sensors
 */
object HoneywellProtocol : SubGhzProtocol(
    name = "Honeywell",
    frequency = 345_000_000,
    modulation = Modulation.OOK
) {
    override fun parse(rawData: String): ParsedSignal? {
        val idMatch = Regex("Id:([A-F0-9]+)").find(rawData)
        val stateMatch = Regex("State:([A-Za-z]+)").find(rawData)
        val typeMatch = Regex("Type:([A-Za-z]+)").find(rawData)

        val id = idMatch?.groupValues?.get(1) ?: return null

        return ParsedSignal(
            protocol = name,
            frequency = frequency,
            modulation = modulation.name,
            data = buildMap {
                put("sensor_id", id)
                stateMatch?.groupValues?.get(1)?.let { put("state", it) }
                typeMatch?.groupValues?.get(1)?.let { put("sensor_type", it) }
            },
            isRollingCode = false,
            serialNumber = id
        )
    }

    override fun analyze(signal: ParsedSignal): SignalAnalysis {
        val sensorType = signal.data["sensor_type"] ?: "Unknown"

        return SignalAnalysis(
            protocol = name,
            description = "Honeywell wireless security sensor ($sensorType) used in home/commercial alarm systems.",
            deviceType = "Security Sensor",
            manufacturer = "Honeywell",
            securityLevel = SecurityLevel.LOW,
            vulnerabilities = listOf(
                "Sensors can be spoofed to show false all-clear",
                "Jamming can prevent alarm triggers",
                "Replay attacks possible in some configurations"
            ),
            recommendations = listOf(
                "Use supervision mode if available",
                "Add jamming detection",
                "Use wired sensors for critical points"
            ),
            canReplay = true,
            canBruteforce = false,
            estimatedBruteforceTime = null
        )
    }
}

// ==================== Protocol Parser Factory ====================

object SubGhzParser {
    private val protocols = listOf(
        PrincetonProtocol,
        CameProtocol,
        KeeLoqProtocol,
        TpmsProtocol,
        WeatherStationProtocol,
        NiceFloProtocol,
        HoneywellProtocol
    )

    /**
     * Auto-detect and parse a Sub-GHz signal
     */
    fun parse(fileContent: String): ParsedSignal? {
        // Extract protocol name from file
        val protocolMatch = Regex("Protocol:\\s*(.+)").find(fileContent)
        val protocolName = protocolMatch?.groupValues?.get(1)?.trim()

        // Find matching protocol
        val protocol = protocols.find { it.name.equals(protocolName, ignoreCase = true) }
            ?: protocols.find { fileContent.contains(it.name, ignoreCase = true) }

        return protocol?.parse(fileContent)
    }

    /**
     * Analyze a parsed signal
     */
    fun analyze(signal: ParsedSignal): SignalAnalysis {
        val protocol = protocols.find { it.name == signal.protocol }
        return protocol?.analyze(signal) ?: SignalAnalysis(
            protocol = signal.protocol,
            description = "Unknown protocol",
            deviceType = "Unknown",
            manufacturer = null,
            securityLevel = SecurityLevel.NONE,
            vulnerabilities = listOf("Unknown security properties"),
            recommendations = listOf("Research this protocol for vulnerabilities"),
            canReplay = true,
            canBruteforce = false,
            estimatedBruteforceTime = null
        )
    }

    /**
     * Parse a raw .sub file
     */
    fun parseSubFile(content: String): SubGhzFileInfo {
        val lines = content.lines()
        val info = mutableMapOf<String, String>()

        lines.forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                info[parts[0].trim()] = parts[1].trim()
            }
        }

        return SubGhzFileInfo(
            filetype = info["Filetype"],
            version = info["Version"]?.toIntOrNull(),
            frequency = info["Frequency"]?.toLongOrNull(),
            preset = info["Preset"],
            protocol = info["Protocol"],
            rawData = if (info["Protocol"] == "RAW") {
                lines.filter { it.startsWith("RAW_Data:") }
                    .joinToString("\n")
            } else null,
            parsedSignal = parse(content)
        )
    }

    fun getAllProtocols(): List<ProtocolInfo> {
        return protocols.map { protocol ->
            ProtocolInfo(
                name = protocol.name,
                frequency = protocol.frequency,
                modulation = protocol.modulation.displayName,
                description = when (protocol) {
                    is PrincetonProtocol -> "Simple 24-bit static code for cheap remotes"
                    is CameProtocol -> "12-bit European gate/garage protocol"
                    is KeeLoqProtocol -> "Rolling code for Chamberlain/LiftMaster/automotive"
                    is TpmsProtocol -> "Tire pressure monitoring sensors"
                    is WeatherStationProtocol -> "Oregon Scientific/Acurite weather sensors"
                    is NiceFloProtocol -> "12-bit European gate remote protocol"
                    is HoneywellProtocol -> "Home security sensors at 345MHz"
                    else -> "Unknown protocol"
                }
            )
        }
    }
}

data class SubGhzFileInfo(
    val filetype: String?,
    val version: Int?,
    val frequency: Long?,
    val preset: String?,
    val protocol: String?,
    val rawData: String?,
    val parsedSignal: ParsedSignal?
)

data class ProtocolInfo(
    val name: String,
    val frequency: Long,
    val modulation: String,
    val description: String
)
