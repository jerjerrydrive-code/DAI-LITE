package com.dai.flipper.domain.protocol

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pwnagotchi / Brucegotchi Protocol Support
 *
 * Integrates with Pwnagotchi-compatible devices for:
 * - WiFi handshake capture monitoring
 * - Device status and statistics
 * - Peer discovery and communication
 * - Training data and AI personality
 *
 * Brucegotchi is a Flipper Zero implementation of the Pwnagotchi concept.
 */

object PwnagotchiProtocol {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * Parse Pwnagotchi advertisement frame
     */
    private const val TAG = "PwnagotchiProtocol"

    fun parseAdvertisement(data: ByteArray): PwnagotchiPeer? {
        return try {
            // Pwnagotchi uses a specific format in its WiFi beacon
            val jsonStr = extractJsonFromBeacon(data) ?: return null
            json.decodeFromString<PwnagotchiPeer>(jsonStr)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse Pwnagotchi beacon (${data.size} bytes): ${e.message}")
            null
        }
    }

    private fun extractJsonFromBeacon(data: ByteArray): String? {
        // Look for JSON structure in beacon
        val str = String(data, Charsets.UTF_8)
        val start = str.indexOf('{')
        val end = str.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            str.substring(start, end + 1)
        } else {
            null
        }
    }

    /**
     * Generate a friendly message based on Pwnagotchi mood
     */
    fun getMoodMessage(mood: PwnagotchiMood): String {
        return when (mood) {
            PwnagotchiMood.HAPPY -> listOf(
                "Yay! Got a handshake!",
                "Pwning is fun!",
                "I love WiFi!",
                "Nom nom nom, handshakes!"
            ).random()
            PwnagotchiMood.EXCITED -> listOf(
                "SO MANY NETWORKS!",
                "THIS IS AMAZING!",
                "CAPTURE ALL THE THINGS!"
            ).random()
            PwnagotchiMood.BORED -> listOf(
                "Nothing to pwn here...",
                "Where are all the networks?",
                "I'm booored"
            ).random()
            PwnagotchiMood.SAD -> listOf(
                "No handshakes for a while...",
                "I miss pwning things",
                "Why is WiFi so quiet?"
            ).random()
            PwnagotchiMood.ANGRY -> listOf(
                "Stupid protected networks!",
                "WPA3 is annoying!",
                "Let me in!"
            ).random()
            PwnagotchiMood.INTENSE -> listOf(
                "DEAUTHING INTENSIFIES",
                "Maximum pwn mode!",
                "Going ham!"
            ).random()
            PwnagotchiMood.SLEEPING -> listOf(
                "Zzz...",
                "*snore*",
                "Dreaming of handshakes..."
            ).random()
            PwnagotchiMood.LONELY -> listOf(
                "No friends nearby...",
                "Looking for other pwnagotchis",
                "Hello? Anyone there?"
            ).random()
            PwnagotchiMood.GRATEFUL -> listOf(
                "Thanks for the handshakes!",
                "You're a great owner!",
                "I appreciate you!"
            ).random()
        }
    }

    /**
     * Face expressions for different moods
     */
    fun getFace(mood: PwnagotchiMood): String {
        return when (mood) {
            PwnagotchiMood.HAPPY -> "(•‿•)"
            PwnagotchiMood.EXCITED -> "(ᵔ◡ᵔ)"
            PwnagotchiMood.BORED -> "(╥﹏╥)"
            PwnagotchiMood.SAD -> "(ಥ﹏ಥ)"
            PwnagotchiMood.ANGRY -> "(ಠ_ಠ)"
            PwnagotchiMood.INTENSE -> "(⌐■_■)"
            PwnagotchiMood.SLEEPING -> "(‐‿‐)"
            PwnagotchiMood.LONELY -> "(´;︵;`)"
            PwnagotchiMood.GRATEFUL -> "(♥‿♥)"
        }
    }
}

// ==================== Data Models ====================

@Serializable
data class PwnagotchiPeer(
    val name: String,
    val version: String? = null,
    val identity: String? = null,
    val face: String? = null,
    val pwnd_run: Int = 0,
    val pwnd_tot: Int = 0,
    val uptime: Long = 0,
    val epoch: Int = 0,
    val channel: Int? = null,
    val associated: Int? = null,
    @SerialName("policy")
    val policy: PwnagotchiPolicy? = null
) {
    val mood: PwnagotchiMood
        get() = when {
            pwnd_run > 10 -> PwnagotchiMood.EXCITED
            pwnd_run > 5 -> PwnagotchiMood.HAPPY
            pwnd_run > 0 -> PwnagotchiMood.GRATEFUL
            uptime > 3600 && pwnd_run == 0 -> PwnagotchiMood.SAD
            channel == null -> PwnagotchiMood.SLEEPING
            else -> PwnagotchiMood.BORED
        }
}

@Serializable
data class PwnagotchiPolicy(
    val advertise: Boolean = true,
    val deauth: Boolean = true,
    @SerialName("assoc")
    val associate: Boolean = true,
    @SerialName("sta_ttl")
    val staTtl: Int = 300,
    @SerialName("recon_time")
    val reconTime: Int = 30,
    @SerialName("max_inactive_scale")
    val maxInactiveScale: Int = 2,
    @SerialName("recon_inactive_multiplier")
    val reconInactiveMultiplier: Int = 2,
    @SerialName("channels")
    val channels: List<Int> = emptyList()
)

enum class PwnagotchiMood {
    HAPPY,
    EXCITED,
    BORED,
    SAD,
    ANGRY,
    INTENSE,
    SLEEPING,
    LONELY,
    GRATEFUL
}

// ==================== Brucegotchi Integration ====================

/**
 * Brucegotchi-specific features for Flipper Zero
 */
@Serializable
data class BrucegotchiStatus(
    val name: String,
    val channel: Int,
    val apCount: Int,
    val staCount: Int,
    val handshakes: Int,
    val deauths: Int,
    val uptime: Long,
    val mode: BrucegotchiMode,
    val face: String
)

enum class BrucegotchiMode {
    MANUAL,
    AUTO,
    AI,
    MANU_DEAUTH,
    MANU_ASSOC
}

/**
 * Flipper Zero Brucegotchi file format parser
 */
object BrucegotchiFileParser {
    /**
     * Parse a .pcap handshake file and extract info
     */
    fun parseHandshakeInfo(filename: String): HandshakeInfo? {
        // Brucegotchi names files like: SSID_BSSID.pcap
        val parts = filename.removeSuffix(".pcap").split("_")
        if (parts.size < 2) return null

        return HandshakeInfo(
            ssid = parts.dropLast(1).joinToString("_"),
            bssid = parts.last().uppercase(),
            filename = filename,
            capturedAt = System.currentTimeMillis()
        )
    }

    /**
     * Parse session stats from log file
     */
    fun parseSessionStats(content: String): SessionStats {
        val lines = content.lines()
        var handshakes = 0
        var deauths = 0
        var networks = 0

        lines.forEach { line ->
            when {
                line.contains("Handshake captured") -> handshakes++
                line.contains("Deauth sent") -> deauths++
                line.contains("AP found") -> networks++
            }
        }

        return SessionStats(
            handshakesCapture = handshakes,
            deauthsSent = deauths,
            networksFound = networks
        )
    }
}

data class HandshakeInfo(
    val ssid: String,
    val bssid: String,
    val filename: String,
    val capturedAt: Long,
    val isFourWay: Boolean = true
)

data class SessionStats(
    val handshakesCapture: Int,
    val deauthsSent: Int,
    val networksFound: Int
)

// ==================== UI Data ====================

/**
 * Display data for Pwnagotchi-style UI
 */
data class PwnagotchiDisplay(
    val name: String,
    val face: String,
    val mood: PwnagotchiMood,
    val message: String,
    val stats: PwnagotchiStats,
    val channel: Int?,
    val peers: List<PwnagotchiPeer>
)

data class PwnagotchiStats(
    val handshakesThisRun: Int,
    val handshakesTotal: Int,
    val networksInRange: Int,
    val clientsInRange: Int,
    val uptime: String,
    val epoch: Int
)

fun formatUptime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}
