package com.dai.flipper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dai.flipper.ai.OpenRouterClient
import com.dai.flipper.ble.BleServiceManager
import com.dai.flipper.ble.FlipperFileSystem
import com.dai.flipper.domain.model.ChimeraProject
import com.dai.flipper.domain.model.ChimeraSplicer
import com.dai.flipper.domain.model.FusionMode
import com.dai.flipper.domain.model.GeneType
import com.dai.flipper.domain.model.Mutation
import com.dai.flipper.domain.model.SignalGene
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import javax.inject.Inject

/**
 * RF Chimera - Signal Fusion Laboratory ViewModel
 *
 * This implementation is intentionally conservative: it keeps the UI contract stable
 * while delegating synthesis to the current Chimera domain model.
 */
@HiltViewModel
class ChimeraLabViewModel @Inject constructor(
    private val bleServiceManager: BleServiceManager,
    private val fileSystem: FlipperFileSystem,
    private val openRouterClient: OpenRouterClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChimeraUiState())
    val uiState: StateFlow<ChimeraUiState> = _uiState.asStateFlow()

    private val _availableSignals = MutableStateFlow<List<SignalSource>>(emptyList())
    val availableSignals: StateFlow<List<SignalSource>> = _availableSignals.asStateFlow()

    private val _genomePreview = MutableStateFlow<List<Float>>(emptyList())
    val genomePreview: StateFlow<List<Float>> = _genomePreview.asStateFlow()

    init {
        loadAvailableSignals()
    }

    private fun loadAvailableSignals() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val signals = mutableListOf<SignalSource>()

                // Sub-GHz captures
                val subghzEntries = fileSystem.listDirectory("/ext/subghz").getOrNull().orEmpty()
                subghzEntries
                    .filter { !it.isDirectory && it.name.endsWith(".sub", ignoreCase = true) }
                    .forEach { entry ->
                        val content = fileSystem.readFile(entry.path).getOrNull() ?: return@forEach
                        val timings = parseSubGhzTimings(content)
                        if (timings.isNotEmpty()) {
                            signals += SignalSource(
                                id = "subghz_${entry.name}",
                                name = entry.name.removeSuffix(".sub"),
                                type = SignalSourceType.SUBGHZ,
                                path = entry.path,
                                timings = timings,
                                frequency = parseFrequency(content)
                            )
                        }
                    }

                // IR captures
                val irEntries = fileSystem.listDirectory("/ext/infrared").getOrNull().orEmpty()
                irEntries
                    .filter { !it.isDirectory && it.name.endsWith(".ir", ignoreCase = true) }
                    .forEach { entry ->
                        val content = fileSystem.readFile(entry.path).getOrNull() ?: return@forEach
                        val timings = parseIrTimings(content)
                        if (timings.isNotEmpty()) {
                            signals += SignalSource(
                                id = "ir_${entry.name}",
                                name = entry.name.removeSuffix(".ir"),
                                type = SignalSourceType.INFRARED,
                                path = entry.path,
                                timings = timings,
                                frequency = 38_000L
                            )
                        }
                    }

                _availableSignals.value = signals
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load signals: ${e.message}")
                }
            }
        }
    }

    fun createNewProject(name: String) {
        _uiState.update {
            it.copy(
                currentProject = ChimeraProject(
                    name = name,
                    genes = emptyList(),
                    mutations = emptyList(),
                    fusionMode = FusionMode.ADAPTIVE
                ),
                resultTimings = emptyList(),
                synthesizedOutput = null,
                polymorphicVariants = emptyList(),
                aiSuggestions = emptyList(),
                error = null
            )
        }
        _genomePreview.value = emptyList()
    }

    fun selectSignalForSplicing(signal: SignalSource) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExtracting = true) }

            val project = _uiState.value.currentProject ?: ChimeraProject(
                name = "Untitled Chimera",
                genes = emptyList(),
                mutations = emptyList(),
                fusionMode = FusionMode.ADAPTIVE
            )

            val extractedGenes = extractGenes(signal)

            _uiState.update { state ->
                state.copy(
                    isExtracting = false,
                    currentProject = project.copy(genes = project.genes + extractedGenes),
                    selectedSignals = (state.selectedSignals + signal).distinctBy { it.id }
                )
            }

            updateGenomePreview()
        }
    }

    fun removeGene(geneId: String) {
        _uiState.update { state ->
            val project = state.currentProject ?: return@update state
            state.copy(currentProject = project.copy(genes = project.genes.filter { it.id != geneId }))
        }
        updateGenomePreview()
    }

    fun toggleGeneActive(geneId: String) {
        _uiState.update { state ->
            val project = state.currentProject ?: return@update state
            state.copy(
                currentProject = project.copy(
                    genes = project.genes.map { gene ->
                        if (gene.id == geneId) gene.copy(enabled = !gene.enabled) else gene
                    }
                )
            )
        }
        updateGenomePreview()
    }

    fun reorderGenes(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val project = state.currentProject ?: return@update state
            val genes = project.genes.toMutableList()
            if (fromIndex !in genes.indices || toIndex !in genes.indices) {
                return@update state
            }
            val moved = genes.removeAt(fromIndex)
            genes.add(toIndex, moved)
            state.copy(currentProject = project.copy(genes = genes))
        }
        updateGenomePreview()
    }

    fun addMutation(mutation: Mutation) {
        _uiState.update { state ->
            val project = state.currentProject ?: return@update state
            state.copy(currentProject = project.copy(mutations = project.mutations + mutation))
        }
        updateGenomePreview()
    }

    fun removeMutation(mutationId: String) {
        _uiState.update { state ->
            val project = state.currentProject ?: return@update state
            state.copy(
                currentProject = project.copy(
                    mutations = project.mutations.filter { it.id != mutationId }
                )
            )
        }
        updateGenomePreview()
    }

    fun setFusionMode(mode: FusionMode) {
        _uiState.update { state ->
            val project = state.currentProject ?: return@update state
            state.copy(currentProject = project.copy(fusionMode = mode))
        }
        updateGenomePreview()
    }

    fun updateFusionConfig(config: FusionConfig) {
        _uiState.update { it.copy(fusionConfig = config) }
        updateGenomePreview()
    }

    fun optimizeWithAI() {
        viewModelScope.launch {
            val project = _uiState.value.currentProject ?: return@launch
            val config = _uiState.value.fusionConfig

            _uiState.update { it.copy(isOptimizing = true, error = null) }

            val prompt = buildOptimizationPrompt(project, config)
            val response = openRouterClient.sendMessage(
                message = prompt,
                conversationHistory = emptyList()
            )

            response
                .onSuccess { text ->
                    _uiState.update {
                        it.copy(
                            isOptimizing = false,
                            aiSuggestions = parseAISuggestions(text)
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isOptimizing = false,
                            error = "AI optimization failed: ${err.message}"
                        )
                    }
                }
        }
    }

    fun synthesizeChimera() {
        viewModelScope.launch {
            val state = _uiState.value
            val project = state.currentProject ?: return@launch

            _uiState.update { it.copy(isSynthesizing = true, error = null) }

            try {
                val synthesisProject = hydrateProject(project, state.fusionConfig)
                val output = ChimeraSplicer.synthesize(synthesisProject)

                val variants = if (state.fusionConfig.polymorphicMode) {
                    generatePolymorphicVariants(
                        base = output.timing,
                        count = state.fusionConfig.variantCount,
                        seed = state.fusionConfig.polymorphicSeed
                    )
                } else {
                    listOf(output.timing)
                }

                val flipperCode = exportToFlipperFormat(
                    timings = output.timing,
                    frequency = synthesisProject.outputFrequency,
                    name = synthesisProject.name
                )

                _uiState.update {
                    it.copy(
                        isSynthesizing = false,
                        currentProject = synthesisProject,
                        resultTimings = output.timing,
                        synthesizedOutput = flipperCode,
                        polymorphicVariants = variants
                    )
                }
                _genomePreview.value = generateWaveformPreview(output.timing)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSynthesizing = false, error = "Synthesis failed: ${e.message}")
                }
            }
        }
    }

    fun saveChimera(filename: String) {
        viewModelScope.launch {
            val output = _uiState.value.synthesizedOutput ?: return@launch
            _uiState.update { it.copy(isSaving = true, error = null) }

            val path = "/ext/subghz/$filename.sub"
            val result = fileSystem.writeFile(path, output)

            _uiState.update {
                it.copy(
                    isSaving = false,
                    saveSuccess = result.isSuccess,
                    savedPath = if (result.isSuccess) path else null,
                    error = if (result.isSuccess) null else "Failed to save: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun transmitChimera() {
        viewModelScope.launch {
            val state = _uiState.value
            val project = state.currentProject ?: return@launch
            val timings = state.resultTimings
            if (timings.isEmpty()) return@launch

            _uiState.update { it.copy(isTransmitting = true, error = null) }

            try {
                val service = bleServiceManager.awaitConnectedService()
                    ?: run {
                        _uiState.update {
                            it.copy(
                                isTransmitting = false,
                                error = "No connected Flipper. Connect in Device and retry."
                            )
                        }
                        return@launch
                    }

                val output = exportToFlipperFormat(
                    timings = timings,
                    frequency = hydrateProject(project, state.fusionConfig).outputFrequency,
                    name = "chimera_tx"
                )
                val tempPath = "/ext/subghz/_chimera_temp.sub"
                val writeResult = fileSystem.writeFile(tempPath, output)
                if (writeResult.isFailure) {
                    throw IllegalStateException(
                        "Failed to write temporary tx file: ${writeResult.exceptionOrNull()?.message}"
                    )
                }

                val result = service.sendCommandWithOutput("subghz tx $tempPath")
                if (result.isFailure) {
                    throw IllegalStateException(
                        "Failed to start Sub-GHz transmission on Flipper: " +
                                "${result.exceptionOrNull()?.message}"
                    )
                }

                _uiState.update { it.copy(isTransmitting = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isTransmitting = false, error = "Transmission failed: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false, savedPath = null) }
    }

    private fun updateGenomePreview() {
        viewModelScope.launch {
            val state = _uiState.value
            val project = state.currentProject ?: return@launch
            val synthesisProject = hydrateProject(project, state.fusionConfig)

            if (synthesisProject.genes.none { it.enabled }) {
                _genomePreview.value = emptyList()
                _uiState.update { it.copy(resultTimings = emptyList()) }
                return@launch
            }

            val output = ChimeraSplicer.synthesize(synthesisProject)
            _genomePreview.value = generateWaveformPreview(output.timing)
            _uiState.update {
                it.copy(
                    currentProject = synthesisProject,
                    resultTimings = output.timing
                )
            }
        }
    }

    private fun hydrateProject(project: ChimeraProject, config: FusionConfig): ChimeraProject {
        return project.copy(
            outputFrequency = config.outputFrequency,
            polymorphicEnabled = config.polymorphicMode,
            polymorphicSeed = config.polymorphicSeed
        )
    }

    private fun extractGenes(signal: SignalSource): List<SignalGene> {
        if (signal.timings.isEmpty()) return emptyList()

        // Keep extraction deterministic and simple: split into up to 4 contiguous segments.
        val chunkSize = (signal.timings.size / 4).coerceAtLeast(1)
        val types = listOf(GeneType.PREAMBLE, GeneType.ADDRESS, GeneType.COMMAND, GeneType.PAYLOAD)

        return signal.timings
            .chunked(chunkSize)
            .take(4)
            .mapIndexed { index, chunk ->
                SignalGene(
                    name = "${signal.name} Part ${index + 1}",
                    sourceSignal = signal.name,
                    sourceType = types.getOrElse(index) { GeneType.CUSTOM },
                    timingData = chunk,
                    startOffset = index * chunkSize
                )
            }
    }

    private fun generateWaveformPreview(timings: List<Int>): List<Float> {
        if (timings.isEmpty()) return emptyList()

        val sampleCount = 500
        val preview = MutableList(sampleCount) { 0f }
        var timingIndex = 0
        var remaining = kotlin.math.abs(timings[0]).coerceAtLeast(1).toFloat()
        var signal = if (timings[0] >= 0) 1f else -1f

        for (i in 0 until sampleCount) {
            preview[i] = signal
            remaining -= timings.size.toFloat() / sampleCount
            while (remaining <= 0f && timingIndex < timings.lastIndex) {
                timingIndex++
                val next = timings[timingIndex]
                signal = if (next >= 0) 1f else -1f
                remaining += kotlin.math.abs(next).coerceAtLeast(1).toFloat()
            }
        }

        return preview
    }

    private fun buildOptimizationPrompt(project: ChimeraProject, config: FusionConfig): String {
        val geneDescriptions = project.genes.mapIndexed { index, gene ->
            "Gene $index: ${gene.sourceType.name} from ${gene.sourceSignal}, ${gene.timingData.size} timings"
        }.joinToString("\n")

        return """
You are an RF signal optimization AI. Analyze this chimera signal project and suggest practical improvements.

PROJECT: ${project.name}
FUSION MODE: ${project.fusionMode.name}
OUTPUT FREQUENCY: ${config.outputFrequency} Hz
POLYMORPHIC MODE: ${config.polymorphicMode}

GENES:
$geneDescriptions

CURRENT MUTATIONS:
${project.mutations.joinToString("\n") { "${it.type.name}: ${it.intensity}" }}

Provide:
1) Optimal gene ordering
2) Mutation recommendations
3) Timing compatibility notes
4) Risk/quality concerns
5) Actionable next steps
""".trimIndent()
    }

    private fun parseAISuggestions(response: String): List<AISuggestion> {
        val suggestions = mutableListOf<AISuggestion>()
        var currentCategory = "General"

        response.lines().forEach { line ->
            when {
                line.contains("order", ignoreCase = true) -> currentCategory = "Gene Ordering"
                line.contains("mutation", ignoreCase = true) -> currentCategory = "Mutations"
                line.contains("timing", ignoreCase = true) -> currentCategory = "Timing"
                line.contains("risk", ignoreCase = true) || line.contains("issue", ignoreCase = true) -> currentCategory = "Risks"
                line.startsWith("-") || line.matches(Regex("^\\d+\\..*")) -> {
                    val content = line
                        .removePrefix("-")
                        .replace(Regex("^\\d+\\.\\s*"), "")
                        .trim()
                    if (content.isNotBlank()) {
                        suggestions += AISuggestion(
                            category = currentCategory,
                            suggestion = content,
                            priority = when (currentCategory) {
                                "Risks" -> SuggestionPriority.HIGH
                                "Gene Ordering", "Timing" -> SuggestionPriority.MEDIUM
                                else -> SuggestionPriority.LOW
                            }
                        )
                    }
                }
            }
        }

        return suggestions
    }

    private fun generatePolymorphicVariants(base: List<Int>, count: Int, seed: Long): List<List<Int>> {
        if (base.isEmpty()) return emptyList()
        val random = Random(seed)
        return List(count.coerceAtLeast(1)) {
            base.map { timing ->
                val jitter = random.nextInt(-12, 13)
                if (timing >= 0) (timing + jitter).coerceAtLeast(1) else (timing - jitter).coerceAtMost(-1)
            }
        }
    }

    private fun exportToFlipperFormat(timings: List<Int>, frequency: Long, name: String): String {
        val rawData = timings.joinToString(" ")
        return buildString {
            appendLine("Filetype: Flipper SubGhz RAW File")
            appendLine("Version: 1")
            appendLine("# Generated by RF Chimera")
            appendLine("Frequency: $frequency")
            appendLine("Preset: FuriHalSubGhzPresetOok650Async")
            appendLine("Protocol: RAW")
            appendLine("Name: $name")
            appendLine("RAW_Data: $rawData")
        }
    }

    // Signal parsing helpers
    private fun parseSubGhzTimings(content: String): List<Int> {
        val timings = mutableListOf<Int>()
        content.lines().forEach { line ->
            if (line.startsWith("RAW_Data:")) {
                line.removePrefix("RAW_Data:")
                    .trim()
                    .split(" ")
                    .forEach { token -> token.toIntOrNull()?.let { timings += it } }
            }
        }
        return timings
    }

    private fun parseIrTimings(content: String): List<Int> {
        val timings = mutableListOf<Int>()
        content.lines().forEach { line ->
            if (line.startsWith("data:", ignoreCase = true)) {
                line.removePrefix("data:")
                    .trim()
                    .split(" ", ",")
                    .forEach { token -> token.toIntOrNull()?.let { timings += it } }
            }
        }
        return timings
    }

    private fun parseFrequency(content: String): Long {
        content.lines().forEach { line ->
            if (line.startsWith("Frequency:")) {
                return line.removePrefix("Frequency:").trim().toLongOrNull() ?: 433_920_000L
            }
        }
        return 433_920_000L
    }
}

// UI State
 data class ChimeraUiState(
    val isLoading: Boolean = false,
    val isExtracting: Boolean = false,
    val isOptimizing: Boolean = false,
    val isSynthesizing: Boolean = false,
    val isSaving: Boolean = false,
    val isTransmitting: Boolean = false,
    val error: String? = null,
    val currentProject: ChimeraProject? = null,
    val selectedSignals: List<SignalSource> = emptyList(),
    val resultTimings: List<Int> = emptyList(),
    val fusionConfig: FusionConfig = FusionConfig(),
    val synthesizedOutput: String? = null,
    val polymorphicVariants: List<List<Int>> = emptyList(),
    val aiSuggestions: List<AISuggestion> = emptyList(),
    val saveSuccess: Boolean = false,
    val savedPath: String? = null
)

 data class FusionConfig(
    val outputFrequency: Long = 433_920_000L,
    val polymorphicMode: Boolean = false,
    val polymorphicSeed: Long = System.currentTimeMillis(),
    val variantCount: Int = 3
)

// Supporting models
 data class SignalSource(
    val id: String,
    val name: String,
    val type: SignalSourceType,
    val path: String,
    val timings: List<Int>,
    val frequency: Long
)

enum class SignalSourceType(val icon: String, val color: Long) {
    SUBGHZ("📡", 0xFF4CAF50),
    INFRARED("🔴", 0xFFE91E63),
    NFC("💳", 0xFF2196F3),
    RFID("🏷️", 0xFFFF9800)
}

 data class AISuggestion(
    val category: String,
    val suggestion: String,
    val priority: SuggestionPriority
)

enum class SuggestionPriority(val color: Long) {
    HIGH(0xFFE91E63),
    MEDIUM(0xFFFFC107),
    LOW(0xFF4CAF50)
}
