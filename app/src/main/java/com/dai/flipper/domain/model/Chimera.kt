package com.dai.flipper.domain.model

import kotlin.math.abs
import kotlin.random.Random

/**
 * RF Chimera - Signal Fusion & Morphing Laboratory
 *
 * Splice signals like DNA to create hybrid attacks,
 * morph protocols to evade detection, and generate
 * polymorphic payloads that mutate on each transmission.
 */

/**
 * A Chimera project - fusion of multiple signal "genes"
 */
data class ChimeraProject(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val genes: List<SignalGene>,
    val mutations: List<Mutation>,
    val fusionMode: FusionMode,
    val polymorphicEnabled: Boolean = false,
    val polymorphicSeed: Long = System.currentTimeMillis(),
    val outputFrequency: Long = 433_920_000,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A signal "gene" - a segment from a captured signal
 */
data class SignalGene(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val sourceSignal: String,           // Original signal name
    val sourceType: GeneType,
    val timingData: List<Int>,          // Raw timing data
    val startOffset: Int = 0,           // Where this gene starts in the sequence
    val color: Long = generateGeneColor(),
    val enabled: Boolean = true,
    val dominance: Float = 1.0f         // How strongly this gene expresses (0-1)
)

enum class GeneType(val displayName: String, val icon: String) {
    PREAMBLE("Preamble", "🧬"),
    SYNC("Sync Word", "🔗"),
    ADDRESS("Address", "📍"),
    COMMAND("Command", "⚡"),
    PAYLOAD("Payload", "📦"),
    CHECKSUM("Checksum", "✓"),
    NOISE("Noise", "〰️"),
    CUSTOM("Custom", "✂️")
}

/**
 * Mutation to apply to the chimera
 */
data class Mutation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: MutationType,
    val targetGeneId: String? = null,   // null = apply to all
    val intensity: Float = 0.5f,        // 0-1
    val parameters: Map<String, Any> = emptyMap()
)

enum class MutationType(val displayName: String, val icon: String, val description: String) {
    TIME_STRETCH("Time Stretch", "⏱️", "Stretch or compress timing"),
    TIME_JITTER("Time Jitter", "📊", "Add random timing variations"),
    AMPLITUDE_MOD("Amplitude Mod", "📈", "Modulate signal strength"),
    BIT_FLIP("Bit Flip", "🔄", "Randomly flip bits"),
    SEQUENCE_SHUFFLE("Sequence Shuffle", "🔀", "Shuffle gene order"),
    MIRROR("Mirror", "🪞", "Reverse the signal"),
    FREQUENCY_DRIFT("Freq Drift", "📻", "Gradual frequency shift"),
    BURST_INSERT("Burst Insert", "💥", "Insert random bursts"),
    SILENCE_INSERT("Silence Insert", "🔇", "Insert gaps"),
    ECHO("Echo", "🔊", "Add signal echo/repeat"),
    PHASE_SHIFT("Phase Shift", "↔️", "Shift signal phase"),
    CROSSOVER("Crossover", "✖️", "Swap segments between genes")
}

enum class FusionMode(val displayName: String, val description: String) {
    SEQUENTIAL("Sequential", "Genes play one after another"),
    INTERLEAVED("Interleaved", "Genes interleave bit by bit"),
    LAYERED("Layered", "Genes overlay (combine timings)"),
    DOMINANT("Dominant", "Strongest gene wins conflicts"),
    RANDOM("Random", "Random selection per segment"),
    ADAPTIVE("Adaptive", "AI-optimized fusion")
}

/**
 * The Chimera synthesis engine
 */
object ChimeraSplicer {

    /**
     * Synthesize a chimera into raw timing data
     */
    fun synthesize(project: ChimeraProject): ChimeraOutput {
        var genes = project.genes.filter { it.enabled }

        if (genes.isEmpty()) {
            return ChimeraOutput(emptyList(), emptyList(), "No genes enabled")
        }

        // Apply mutations
        genes = applyMutations(genes, project.mutations)

        // Fuse genes based on mode
        val fusedTiming = when (project.fusionMode) {
            FusionMode.SEQUENTIAL -> fuseSequential(genes)
            FusionMode.INTERLEAVED -> fuseInterleaved(genes)
            FusionMode.LAYERED -> fuseLayered(genes)
            FusionMode.DOMINANT -> fuseDominant(genes)
            FusionMode.RANDOM -> fuseRandom(genes, project.polymorphicSeed)
            FusionMode.ADAPTIVE -> fuseAdaptive(genes)
        }

        // Apply polymorphic mutations if enabled
        val finalTiming = if (project.polymorphicEnabled) {
            applyPolymorphism(fusedTiming, project.polymorphicSeed)
        } else {
            fusedTiming
        }

        // Generate visualization data
        val visualization = generateVisualization(genes, finalTiming)

        return ChimeraOutput(
            timing = finalTiming,
            visualization = visualization,
            stats = generateStats(genes, finalTiming)
        )
    }

    private fun applyMutations(genes: List<SignalGene>, mutations: List<Mutation>): List<SignalGene> {
        var mutatedGenes = genes.toMutableList()

        mutations.forEach { mutation ->
            val targetGenes = if (mutation.targetGeneId != null) {
                mutatedGenes.filter { it.id == mutation.targetGeneId }
            } else {
                mutatedGenes
            }

            targetGenes.forEach { gene ->
                val index = mutatedGenes.indexOf(gene)
                if (index >= 0) {
                    mutatedGenes[index] = applyMutation(gene, mutation)
                }
            }
        }

        return mutatedGenes
    }

    private fun applyMutation(gene: SignalGene, mutation: Mutation): SignalGene {
        val intensity = mutation.intensity
        val timing = gene.timingData.toMutableList()

        when (mutation.type) {
            MutationType.TIME_STRETCH -> {
                val factor = 0.5f + intensity // 0.5x to 1.5x
                return gene.copy(timingData = timing.map { (it * factor).toInt() })
            }

            MutationType.TIME_JITTER -> {
                val maxJitter = (intensity * 100).toInt()
                return gene.copy(timingData = timing.map {
                    val jitter = Random.nextInt(-maxJitter, maxJitter + 1)
                    it + jitter
                })
            }

            MutationType.BIT_FLIP -> {
                val flipProbability = intensity * 0.3f
                return gene.copy(timingData = timing.map {
                    if (Random.nextFloat() < flipProbability) -it else it
                })
            }

            MutationType.MIRROR -> {
                return gene.copy(timingData = timing.reversed())
            }

            MutationType.BURST_INSERT -> {
                val burstCount = (intensity * 10).toInt().coerceAtLeast(1)
                val result = timing.toMutableList()
                repeat(burstCount) {
                    val pos = Random.nextInt(result.size)
                    val burstWidth = Random.nextInt(50, 200)
                    result.add(pos, burstWidth)
                    result.add(pos + 1, -burstWidth)
                }
                return gene.copy(timingData = result)
            }

            MutationType.SILENCE_INSERT -> {
                val silenceCount = (intensity * 5).toInt().coerceAtLeast(1)
                val result = timing.toMutableList()
                repeat(silenceCount) {
                    val pos = Random.nextInt(result.size)
                    val silenceWidth = Random.nextInt(500, 2000)
                    result.add(pos, -silenceWidth)
                }
                return gene.copy(timingData = result)
            }

            MutationType.ECHO -> {
                val echoDelay = (1000 * intensity).toInt()
                val echoTiming = timing.map { (it * 0.7f).toInt() }
                return gene.copy(timingData = timing + listOf(-echoDelay) + echoTiming)
            }

            else -> return gene
        }
    }

    private fun fuseSequential(genes: List<SignalGene>): List<Int> {
        val result = mutableListOf<Int>()
        val sortedGenes = genes.sortedBy { it.startOffset }

        sortedGenes.forEach { gene ->
            result.addAll(gene.timingData.map { (it * gene.dominance).toInt() })
            result.add(-500) // Gap between genes
        }

        return result
    }

    private fun fuseInterleaved(genes: List<SignalGene>): List<Int> {
        val result = mutableListOf<Int>()
        val maxLength = genes.maxOfOrNull { it.timingData.size } ?: 0

        for (i in 0 until maxLength) {
            genes.forEach { gene ->
                if (i < gene.timingData.size) {
                    val value = (gene.timingData[i] * gene.dominance).toInt()
                    result.add(value)
                }
            }
        }

        return result
    }

    private fun fuseLayered(genes: List<SignalGene>): List<Int> {
        val result = mutableListOf<Int>()
        val maxLength = genes.maxOfOrNull { it.timingData.size } ?: 0

        for (i in 0 until maxLength) {
            var combinedValue = 0f
            var totalDominance = 0f

            genes.forEach { gene ->
                if (i < gene.timingData.size) {
                    combinedValue += gene.timingData[i] * gene.dominance
                    totalDominance += gene.dominance
                }
            }

            if (totalDominance > 0) {
                result.add((combinedValue / totalDominance).toInt())
            }
        }

        return result
    }

    private fun fuseDominant(genes: List<SignalGene>): List<Int> {
        val result = mutableListOf<Int>()
        val maxLength = genes.maxOfOrNull { it.timingData.size } ?: 0

        for (i in 0 until maxLength) {
            val dominantGene = genes
                .filter { i < it.timingData.size }
                .maxByOrNull { it.dominance }

            dominantGene?.let {
                result.add((it.timingData[i] * it.dominance).toInt())
            }
        }

        return result
    }

    private fun fuseRandom(genes: List<SignalGene>, seed: Long): List<Int> {
        val random = Random(seed)
        val result = mutableListOf<Int>()
        val maxLength = genes.maxOfOrNull { it.timingData.size } ?: 0

        for (i in 0 until maxLength) {
            val availableGenes = genes.filter { i < it.timingData.size }
            if (availableGenes.isNotEmpty()) {
                val selected = availableGenes[random.nextInt(availableGenes.size)]
                result.add((selected.timingData[i] * selected.dominance).toInt())
            }
        }

        return result
    }

    private fun fuseAdaptive(genes: List<SignalGene>): List<Int> {
        // AI-optimized fusion - use weighted combination based on signal strength patterns
        val result = mutableListOf<Int>()
        val maxLength = genes.maxOfOrNull { it.timingData.size } ?: 0

        // Analyze patterns to determine optimal fusion
        val genePatterns = genes.map { gene ->
            val avgPulse = gene.timingData.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 500.0
            gene to avgPulse
        }

        for (i in 0 until maxLength) {
            var bestValue = 0
            var bestScore = 0.0

            genePatterns.forEach { (gene, avgPulse) ->
                if (i < gene.timingData.size) {
                    val value = gene.timingData[i]
                    // Score based on how "typical" this pulse is for the gene
                    val score = gene.dominance * (1.0 - abs(abs(value) - avgPulse) / avgPulse)
                    if (score > bestScore) {
                        bestScore = score
                        bestValue = (value * gene.dominance).toInt()
                    }
                }
            }

            result.add(bestValue)
        }

        return result
    }

    private fun applyPolymorphism(timing: List<Int>, seed: Long): List<Int> {
        // Apply subtle random variations that change each generation
        val random = Random(seed + System.currentTimeMillis() / 1000)
        return timing.map { value ->
            val jitter = (value * 0.05 * random.nextFloat()).toInt()
            value + if (random.nextBoolean()) jitter else -jitter
        }
    }

    private fun generateVisualization(genes: List<SignalGene>, finalTiming: List<Int>): List<ChimeraVisualization> {
        return genes.mapIndexed { index, gene ->
            ChimeraVisualization(
                geneId = gene.id,
                geneName = gene.name,
                color = gene.color,
                startPosition = gene.startOffset.toFloat() / (genes.sumOf { it.timingData.size } + 1),
                width = gene.timingData.size.toFloat() / (genes.sumOf { it.timingData.size } + 1),
                dominance = gene.dominance
            )
        }
    }

    private fun generateStats(genes: List<SignalGene>, timing: List<Int>): String {
        val totalPulses = timing.size
        val duration = timing.sumOf { abs(it) }
        val avgPulse = timing.filter { it > 0 }.average().takeIf { !it.isNaN() }?.toInt() ?: 0

        return buildString {
            appendLine("Genes: ${genes.size}")
            appendLine("Total pulses: $totalPulses")
            appendLine("Duration: ${duration}µs (${duration / 1000.0}ms)")
            appendLine("Avg pulse: ${avgPulse}µs")
        }
    }

    /**
     * Export chimera to Flipper .sub format
     */
    fun exportToFlipper(project: ChimeraProject): String {
        val output = synthesize(project)

        return buildString {
            appendLine("Filetype: Flipper SubGhz RAW File")
            appendLine("Version: 1")
            appendLine("# Chimera: ${project.name}")
            appendLine("# Genes: ${project.genes.map { it.name }.joinToString(", ")}")
            appendLine("# Fusion: ${project.fusionMode.displayName}")
            appendLine("# Polymorphic: ${project.polymorphicEnabled}")
            appendLine("Frequency: ${project.outputFrequency}")
            appendLine("Preset: FuriHalSubGhzPresetOok650Async")
            appendLine("Protocol: RAW")

            output.timing.chunked(50).forEach { chunk ->
                appendLine("RAW_Data: ${chunk.joinToString(" ")}")
            }
        }
    }

    /**
     * Extract genes from a signal
     */
    fun extractGenes(signal: SignalCapture.SubGhz): List<SignalGene> {
        val genes = mutableListOf<SignalGene>()
        val timing = signal.rawData ?: return genes

        if (timing.isEmpty()) return genes

        // Detect preamble (alternating pattern at start)
        val preambleEnd = detectPreambleEnd(timing)
        if (preambleEnd > 4) {
            genes.add(SignalGene(
                name = "Preamble",
                sourceSignal = signal.name,
                sourceType = GeneType.PREAMBLE,
                timingData = timing.take(preambleEnd),
                color = 0xFF4CAF50
            ))
        }

        // Detect sync word (longer pulse after preamble)
        val syncEnd = detectSyncEnd(timing, preambleEnd)
        if (syncEnd > preambleEnd) {
            genes.add(SignalGene(
                name = "Sync",
                sourceSignal = signal.name,
                sourceType = GeneType.SYNC,
                timingData = timing.subList(preambleEnd, syncEnd),
                startOffset = preambleEnd,
                color = 0xFF2196F3
            ))
        }

        // The rest is payload
        val payloadStart = syncEnd.coerceAtLeast(preambleEnd)
        if (payloadStart < timing.size) {
            genes.add(SignalGene(
                name = "Payload",
                sourceSignal = signal.name,
                sourceType = GeneType.PAYLOAD,
                timingData = timing.subList(payloadStart, timing.size),
                startOffset = payloadStart,
                color = 0xFFFF6B00
            ))
        }

        return genes
    }

    private fun detectPreambleEnd(timing: List<Int>): Int {
        if (timing.size < 4) return 0

        // Look for alternating pattern break
        for (i in 4 until timing.size - 2 step 2) {
            val currentPair = abs(timing[i]) to abs(timing[i + 1])
            val prevPair = abs(timing[i - 2]) to abs(timing[i - 1])

            // If timing changes significantly, preamble ended
            val diff = abs(currentPair.first - prevPair.first) + abs(currentPair.second - prevPair.second)
            if (diff > (currentPair.first + currentPair.second) * 0.5) {
                return i
            }
        }

        return minOf(20, timing.size)
    }

    private fun detectSyncEnd(timing: List<Int>, start: Int): Int {
        if (start >= timing.size - 2) return start

        val avgBefore = timing.take(start).filter { it > 0 }.average().takeIf { !it.isNaN() } ?: return start

        // Look for pulse significantly longer than preamble
        for (i in start until minOf(start + 10, timing.size)) {
            if (abs(timing[i]) > avgBefore * 2) {
                return i + 2 // Include the long pulse and its gap
            }
        }

        return start
    }
}

data class ChimeraOutput(
    val timing: List<Int>,
    val visualization: List<ChimeraVisualization>,
    val stats: String
)

data class ChimeraVisualization(
    val geneId: String,
    val geneName: String,
    val color: Long,
    val startPosition: Float,
    val width: Float,
    val dominance: Float
)

private fun generateGeneColor(): Long {
    val colors = listOf(
        0xFFFF6B00, 0xFF4CAF50, 0xFF2196F3, 0xFF9C27B0,
        0xFFE91E63, 0xFF00BCD4, 0xFFFFEB3B, 0xFF795548
    )
    return colors[Random.nextInt(colors.size)]
}
