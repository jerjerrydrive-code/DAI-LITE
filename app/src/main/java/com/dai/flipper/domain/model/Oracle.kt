package com.dai.flipper.domain.model

import kotlinx.serialization.Serializable

/**
 * Spectral Oracle - AI Signal Intelligence System
 *
 * Analyzes unknown RF signals using AI to:
 * - Identify protocols and manufacturers
 * - Detect security vulnerabilities
 * - Generate exploits and countermeasures
 * - Provide threat assessments
 */

/**
 * Signal intelligence analysis request
 */
data class SignalIntelRequest(
    val signalData: CapturedSignalData,
    val analysisType: AnalysisType,
    val context: AnalysisContext? = null
)

/**
 * Raw captured signal data for analysis
 */
data class CapturedSignalData(
    val frequency: Long,                    // Hz
    val rawTimings: List<Int>,              // Raw timing data (µs)
    val modulationType: String?,            // If known
    val captureTimestamp: Long,
    val signalStrength: Int? = null,        // dBm if available
    val sampleRate: Int? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class AnalysisType(val displayName: String, val icon: String) {
    FULL_ANALYSIS("Full Analysis", "🔮"),
    PROTOCOL_ID("Protocol Identification", "🔍"),
    VULNERABILITY_SCAN("Vulnerability Scan", "🛡️"),
    EXPLOIT_GEN("Exploit Generation", "⚔️"),
    MANUFACTURER_ID("Manufacturer ID", "🏭"),
    THREAT_ASSESSMENT("Threat Assessment", "⚠️"),
    COUNTER_MEASURES("Countermeasures", "🛑"),
    PATTERN_LEARN("Pattern Learning", "🧠")
}

/**
 * Additional context for analysis
 */
data class AnalysisContext(
    val location: String? = null,
    val targetDescription: String? = null,
    val previousCaptures: List<String>? = null,
    val knownDevices: List<String>? = null,
    val securityLevel: SecurityLevel = SecurityLevel.STANDARD
)

enum class SecurityLevel {
    BASIC,      // Quick scan
    STANDARD,   // Normal analysis
    DEEP,       // Thorough investigation
    PARANOID    // Maximum depth
}

/**
 * AI-generated signal intelligence report
 */
data class SignalIntelReport(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val signalFingerprint: SignalFingerprint,
    val protocolAnalysis: ProtocolAnalysis?,
    val vulnerabilities: List<Vulnerability>,
    val exploits: List<ExploitVector>,
    val manufacturer: ManufacturerInfo?,
    val threatAssessment: ThreatAssessment,
    val recommendations: List<Recommendation>,
    val rawAiResponse: String
)

/**
 * Unique fingerprint of the signal
 */
data class SignalFingerprint(
    val hash: String,
    val frequency: Long,
    val bandwidth: String,
    val modulationDetected: String,
    val bitRate: String?,
    val signaturePattern: String,
    val confidence: Float
)

/**
 * Protocol analysis results
 */
data class ProtocolAnalysis(
    val protocolName: String,
    val protocolFamily: String,
    val version: String?,
    val encoding: String,
    val packetStructure: PacketStructure?,
    val knownImplementations: List<String>,
    val isRollingCode: Boolean,
    val isEncrypted: Boolean,
    val encryptionType: String?,
    val confidence: Float
)

data class PacketStructure(
    val preambleLength: Int,
    val syncWord: String?,
    val payloadLength: Int,
    val checksumType: String?,
    val totalBits: Int,
    val fieldMap: Map<String, FieldInfo>
)

data class FieldInfo(
    val name: String,
    val startBit: Int,
    val length: Int,
    val description: String,
    val decodedValue: String?
)

/**
 * Security vulnerability
 */
data class Vulnerability(
    val id: String,
    val name: String,
    val severity: VulnerabilitySeverity,
    val category: VulnerabilityCategory,
    val description: String,
    val technicalDetails: String,
    val cveReference: String?,
    val exploitability: Float,  // 0-1
    val impact: String
)

enum class VulnerabilitySeverity(val color: Long) {
    CRITICAL(0xFFD32F2F),
    HIGH(0xFFFF5722),
    MEDIUM(0xFFFFC107),
    LOW(0xFF4CAF50),
    INFO(0xFF2196F3)
}

enum class VulnerabilityCategory {
    REPLAY_ATTACK,
    WEAK_ENCRYPTION,
    NO_ENCRYPTION,
    PREDICTABLE_CODES,
    TIMING_ATTACK,
    JAMMING_SUSCEPTIBLE,
    BRUTE_FORCE,
    SIGNAL_INJECTION,
    PROTOCOL_DOWNGRADE,
    KEY_EXTRACTION,
    SIDE_CHANNEL
}

/**
 * Exploit vector with generated payload
 */
data class ExploitVector(
    val id: String,
    val name: String,
    val targetVulnerability: String,
    val difficulty: ExploitDifficulty,
    val description: String,
    val steps: List<String>,
    val generatedPayload: GeneratedPayload?,
    val successProbability: Float,
    val detectionRisk: DetectionRisk,
    val legalWarning: String
)

enum class ExploitDifficulty {
    TRIVIAL,    // One-click
    EASY,       // Basic knowledge
    MODERATE,   // Some expertise
    HARD,       // Advanced skills
    EXPERT      // Research-level
}

enum class DetectionRisk {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CERTAIN
}

data class GeneratedPayload(
    val type: OraclePayloadType,
    val data: String,              // Base64 or hex
    val flipperFormat: String,     // Ready to save as .sub
    val timingData: List<Int>?,
    val instructions: String
)

enum class OraclePayloadType {
    REPLAY,
    CRAFTED,
    BRUTE_FORCE_SEQUENCE,
    JAMMING,
    INJECTION
}

/**
 * Manufacturer identification
 */
data class ManufacturerInfo(
    val name: String,
    val country: String?,
    val productLine: String?,
    val knownProducts: List<String>,
    val securityReputation: String,
    val commonVulnerabilities: List<String>,
    val confidence: Float
)

/**
 * Threat assessment
 */
data class ThreatAssessment(
    val overallRisk: OracleRiskLevel,
    val attackSurface: String,
    val mostLikelyAttack: String,
    val impactIfCompromised: String,
    val defensePosture: String,
    val priorityActions: List<String>
)

enum class OracleRiskLevel(val displayName: String, val color: Long) {
    CRITICAL("Critical", 0xFFD32F2F),
    HIGH("High", 0xFFFF5722),
    ELEVATED("Elevated", 0xFFFFA726),
    MODERATE("Moderate", 0xFFFFC107),
    LOW("Low", 0xFF4CAF50),
    MINIMAL("Minimal", 0xFF8BC34A)
}

/**
 * Security recommendation
 */
data class Recommendation(
    val priority: Int,
    val category: RecommendationCategory,
    val title: String,
    val description: String,
    val implementation: String?,
    val effort: String
)

enum class RecommendationCategory {
    IMMEDIATE_ACTION,
    MITIGATION,
    MONITORING,
    LONG_TERM,
    RESEARCH
}

/**
 * Spectral pattern for machine learning
 */
data class SpectralPattern(
    val id: String,
    val name: String,
    val category: String,
    val frequencyRange: LongRange,
    val signatureFeatures: List<Float>,
    val matchThreshold: Float,
    val knownDevices: List<String>,
    val learnedFrom: Int  // Number of samples
)

/**
 * AI Analysis prompts for different analysis types
 */
object OraclePrompts {

    fun buildAnalysisPrompt(request: SignalIntelRequest): String {
        val signal = request.signalData
        val waveformDescription = describeWaveform(signal.rawTimings)

        return """
You are Spectral Oracle, an elite RF signals intelligence analyst AI. Analyze this captured signal with the precision of a nation-state SIGINT operation.

## CAPTURED SIGNAL DATA
- Frequency: ${formatFrequency(signal.frequency)}
- Modulation: ${signal.modulationType ?: "Unknown - analyze from timing"}
- Capture Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(signal.captureTimestamp))}
- Signal Strength: ${signal.signalStrength?.let { "$it dBm" } ?: "Not measured"}
- Total Samples: ${signal.rawTimings.size}

## TIMING ANALYSIS
$waveformDescription

## RAW TIMING DATA (first 100 samples, µs)
${signal.rawTimings.take(100).joinToString(", ")}

## ANALYSIS TYPE: ${request.analysisType.displayName}

${getAnalysisInstructions(request.analysisType)}

${request.context?.let { buildContextSection(it) } ?: ""}

Provide your analysis in a structured format. Be specific, technical, and actionable.
For any exploits, include the exact timing data needed to reproduce.
Rate confidence levels honestly - if uncertain, say so.

Remember: This is for authorized security research and education only.
""".trimIndent()
    }

    private fun describeWaveform(timings: List<Int>): String {
        if (timings.isEmpty()) return "No timing data available"

        val positives = timings.filter { it > 0 }
        val negatives = timings.filter { it < 0 }.map { -it }

        val avgPulse = positives.average().takeIf { !it.isNaN() }?.toInt() ?: 0
        val avgGap = negatives.average().takeIf { !it.isNaN() }?.toInt() ?: 0
        val minPulse = positives.minOrNull() ?: 0
        val maxPulse = positives.maxOrNull() ?: 0

        // Detect patterns
        val pulseClusters = clusterValues(positives)
        val gapClusters = clusterValues(negatives)

        return """
### Waveform Statistics:
- Average pulse width: ${avgPulse}µs
- Average gap width: ${avgGap}µs
- Pulse range: ${minPulse}µs - ${maxPulse}µs
- Total duration: ${timings.sumOf { kotlin.math.abs(it) }}µs
- Pulse clusters detected: ${pulseClusters.size} (suggests ${if (pulseClusters.size <= 2) "simple OOK" else "complex encoding"})
- Distinct timing values: ${pulseClusters.map { "${it.first}µs (${it.second}x)" }.joinToString(", ")}

### Pattern Observations:
${detectPatterns(timings)}
""".trimIndent()
    }

    private fun clusterValues(values: List<Int>, tolerance: Int = 50): List<Pair<Int, Int>> {
        if (values.isEmpty()) return emptyList()

        val sorted = values.sorted()
        val clusters = mutableListOf<MutableList<Int>>()
        var currentCluster = mutableListOf(sorted.first())

        for (i in 1 until sorted.size) {
            if (sorted[i] - sorted[i-1] <= tolerance) {
                currentCluster.add(sorted[i])
            } else {
                clusters.add(currentCluster)
                currentCluster = mutableListOf(sorted[i])
            }
        }
        clusters.add(currentCluster)

        return clusters.map { cluster ->
            cluster.average().toInt() to cluster.size
        }.sortedByDescending { it.second }
    }

    private fun detectPatterns(timings: List<Int>): String {
        val patterns = mutableListOf<String>()

        // Check for preamble (alternating pattern at start)
        val start = timings.take(20)
        if (start.size >= 4) {
            val alternating = start.windowed(2).count { (a, b) ->
                (a > 0 && b < 0) || (a < 0 && b > 0)
            }
            if (alternating > start.size * 0.7) {
                patterns.add("- Preamble detected: Alternating pattern in first ${start.size} samples")
            }
        }

        // Check for sync word (longer pulse)
        val avgPulse = timings.filter { it > 0 }.average()
        val longPulses = timings.filter { it > avgPulse * 2 }
        if (longPulses.isNotEmpty()) {
            patterns.add("- Sync pulses detected: ${longPulses.size} pulses > 2x average width")
        }

        // Check for bit encoding patterns
        val uniquePositive = timings.filter { it > 0 }.distinct().size
        val uniqueNegative = timings.filter { it < 0 }.distinct().size

        when {
            uniquePositive <= 3 && uniqueNegative <= 3 ->
                patterns.add("- Encoding: Likely fixed-width (OOK or simple FSK)")
            uniquePositive > 5 || uniqueNegative > 5 ->
                patterns.add("- Encoding: Variable width detected (PWM, PPM, or Manchester)")
        }

        // Check for repeating sequences
        val repeatCheck = findRepeatingSequence(timings)
        if (repeatCheck != null) {
            patterns.add("- Repeating pattern: ${repeatCheck.first} samples, repeated ${repeatCheck.second}x")
        }

        return patterns.joinToString("\n").ifEmpty { "- No obvious patterns detected - may be encrypted or noise" }
    }

    private fun findRepeatingSequence(timings: List<Int>): Pair<Int, Int>? {
        if (timings.size < 20) return null

        for (len in 10..timings.size/2) {
            val pattern = timings.take(len)
            var matches = 0
            var i = len
            while (i + len <= timings.size) {
                val segment = timings.subList(i, i + len)
                if (patternsMatch(pattern, segment)) {
                    matches++
                    i += len
                } else {
                    break
                }
            }
            if (matches >= 2) {
                return len to (matches + 1)
            }
        }
        return null
    }

    private fun patternsMatch(a: List<Int>, b: List<Int>, tolerance: Float = 0.15f): Boolean {
        if (a.size != b.size) return false
        return a.zip(b).all { (x, y) ->
            val diff = kotlin.math.abs(x - y).toFloat() / kotlin.math.max(kotlin.math.abs(x), kotlin.math.abs(y))
            diff <= tolerance
        }
    }

    private fun getAnalysisInstructions(type: AnalysisType): String = when (type) {
        AnalysisType.FULL_ANALYSIS -> """
## FULL ANALYSIS REQUIRED
Provide complete signal intelligence:
1. Protocol identification with confidence level
2. Packet structure breakdown (preamble, sync, payload, checksum)
3. All detected vulnerabilities ranked by severity
4. Exploitable attack vectors with payloads
5. Manufacturer identification
6. Threat assessment
7. Defensive recommendations
"""
        AnalysisType.PROTOCOL_ID -> """
## PROTOCOL IDENTIFICATION
Focus on:
1. Match against known protocols (Princeton, KeeLoq, came, nice, etc.)
2. Encoding scheme (NRZ, Manchester, PWM, etc.)
3. Bit rate calculation
4. Packet structure
5. If unknown protocol, describe characteristics
"""
        AnalysisType.VULNERABILITY_SCAN -> """
## VULNERABILITY SCAN
Identify all security weaknesses:
1. Encryption analysis (present/absent, strength)
2. Rolling code implementation flaws
3. Replay attack susceptibility
4. Timing attack vectors
5. Jamming vulnerability
6. Side-channel possibilities
Rate each vulnerability: CRITICAL/HIGH/MEDIUM/LOW/INFO
"""
        AnalysisType.EXPLOIT_GEN -> """
## EXPLOIT GENERATION
For each vulnerability found:
1. Describe the exploit technique
2. Provide step-by-step instructions
3. Generate the exact payload (timing data for Flipper)
4. Estimate success probability
5. Note detection risks
6. Include legal warnings
"""
        AnalysisType.MANUFACTURER_ID -> """
## MANUFACTURER IDENTIFICATION
Determine the source:
1. Match signal characteristics to known manufacturers
2. Identify product line/model if possible
3. Note manufacturer's security history
4. List known CVEs for this manufacturer
5. Confidence level of identification
"""
        AnalysisType.THREAT_ASSESSMENT -> """
## THREAT ASSESSMENT
Evaluate the security posture:
1. Overall risk level
2. Attack surface analysis
3. Most likely attack scenarios
4. Impact if compromised
5. Priority remediation actions
"""
        AnalysisType.COUNTER_MEASURES -> """
## COUNTERMEASURES
For defensive purposes:
1. How to detect attacks on this protocol
2. Jamming countermeasures
3. Secure alternatives
4. Monitoring recommendations
5. Incident response procedures
"""
        AnalysisType.PATTERN_LEARN -> """
## PATTERN LEARNING
Extract learnable features:
1. Unique signal fingerprint
2. Distinguishing characteristics
3. Classification features for ML
4. Similar signals in database
5. Suggested classification category
"""
    }

    private fun buildContextSection(context: AnalysisContext): String = """
## ADDITIONAL CONTEXT
- Location: ${context.location ?: "Unknown"}
- Target: ${context.targetDescription ?: "Not specified"}
- Security Level: ${context.securityLevel}
${context.previousCaptures?.let { "- Previous captures: ${it.size} related signals" } ?: ""}
${context.knownDevices?.let { "- Known devices in area: ${it.joinToString(", ")}" } ?: ""}
"""

    private fun formatFrequency(hz: Long): String = when {
        hz >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.3f GHz", hz / 1_000_000_000.0)
        hz >= 1_000_000 -> String.format(java.util.Locale.US, "%.3f MHz", hz / 1_000_000.0)
        hz >= 1_000 -> String.format(java.util.Locale.US, "%.3f kHz", hz / 1_000.0)
        else -> "$hz Hz"
    }
}
