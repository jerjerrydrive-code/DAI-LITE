package com.dai.flipper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dai.flipper.ble.FlipperFileSystem
import com.dai.flipper.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignalArsenalViewModel @Inject constructor(
    private val fileSystem: FlipperFileSystem
) : ViewModel() {

    private val _selectedType = MutableStateFlow(SignalType.SUBGHZ)
    val selectedType: StateFlow<SignalType> = _selectedType.asStateFlow()

    private val _signals = MutableStateFlow<List<SignalCapture>>(emptyList())
    val signals: StateFlow<List<SignalCapture>> = _signals.asStateFlow()

    private val _selectedSignal = MutableStateFlow<SignalCapture?>(null)
    val selectedSignal: StateFlow<SignalCapture?> = _selectedSignal.asStateFlow()

    private val _waveformData = MutableStateFlow<WaveformData?>(null)
    val waveformData: StateFlow<WaveformData?> = _waveformData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isReplaying = MutableStateFlow(false)
    val isReplaying: StateFlow<Boolean> = _isReplaying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _replaySuccess = MutableStateFlow<String?>(null)
    val replaySuccess: StateFlow<String?> = _replaySuccess.asStateFlow()

    // Filtered signals based on search
    val filteredSignals: StateFlow<List<SignalCapture>> = combine(
        _signals,
        _searchQuery
    ) { signals, query ->
        if (query.isBlank()) {
            signals
        } else {
            signals.filter { signal ->
                signal.name.contains(query, ignoreCase = true) ||
                getSignalSubtitle(signal).contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Signal counts per type
    private val _signalCounts = MutableStateFlow<Map<SignalType, Int>>(emptyMap())
    val signalCounts: StateFlow<Map<SignalType, Int>> = _signalCounts.asStateFlow()

    init {
        loadSignalCounts()
        loadSignals(SignalType.SUBGHZ)
    }

    fun selectType(type: SignalType) {
        _selectedType.value = type
        _selectedSignal.value = null
        _waveformData.value = null
        loadSignals(type)
    }

    fun selectSignal(signal: SignalCapture) {
        _selectedSignal.value = signal
        loadWaveform(signal)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSelection() {
        _selectedSignal.value = null
        _waveformData.value = null
    }

    fun refresh() {
        loadSignalCounts()
        loadSignals(_selectedType.value)
    }

    private fun loadSignalCounts() {
        viewModelScope.launch {
            val counts = mutableMapOf<SignalType, Int>()
            SignalType.entries.forEach { type ->
                val result = fileSystem.listDirectory(type.directory)
                if (result.isSuccess) {
                    counts[type] = result.getOrDefault(emptyList())
                        .count { it.name.endsWith(type.extension) }
                }
            }
            _signalCounts.value = counts
        }
    }

    private fun loadSignals(type: SignalType) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val result = fileSystem.listDirectory(type.directory)
                if (result.isFailure) {
                    _signals.value = emptyList()
                    return@launch
                }

                val files = result.getOrThrow()
                    .filter { !it.isDirectory && it.name.endsWith(type.extension) }

                val loadedSignals = files.mapNotNull { file ->
                    loadSignalFile(file.path, file.name, type)
                }

                _signals.value = loadedSignals.sortedByDescending { it.timestamp }
            } catch (e: Exception) {
                _error.value = "Failed to load signals: ${e.message}"
                _signals.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadSignalFile(path: String, name: String, type: SignalType): SignalCapture? {
        val contentResult = fileSystem.readFile(path)
        if (contentResult.isFailure) return null

        val content = contentResult.getOrThrow()
        val baseName = name.removeSuffix(type.extension)

        return when (type) {
            SignalType.SUBGHZ -> SignalParser.parseSubGhz(content, path, baseName)
            SignalType.INFRARED -> SignalParser.parseInfrared(content, path, baseName).firstOrNull()
            SignalType.NFC -> SignalParser.parseNfc(content, path, baseName)
            SignalType.RFID -> SignalParser.parseRfid(content, path, baseName)
            SignalType.IBUTTON -> SignalParser.parseIButton(content, path, baseName)
        }
    }

    private fun loadWaveform(signal: SignalCapture) {
        viewModelScope.launch {
            _waveformData.value = when (signal) {
                is SignalCapture.SubGhz -> {
                    signal.rawData?.let { SignalParser.rawToWaveform(it) }
                }
                is SignalCapture.Infrared -> {
                    signal.rawData?.let { SignalParser.rawToWaveform(it) }
                }
                else -> null
            }
        }
    }

    fun replaySignal(signal: SignalCapture) {
        viewModelScope.launch {
            _isReplaying.value = true
            _error.value = null

            try {
                // The replay would trigger the appropriate Flipper action
                // For now, we simulate the action
                when (signal) {
                    is SignalCapture.SubGhz -> {
                        // Trigger SubGHz transmit
                        _replaySuccess.value = "Transmitting ${signal.name} at ${formatFrequency(signal.frequency)}"
                    }
                    is SignalCapture.Infrared -> {
                        _replaySuccess.value = "Sending IR: ${signal.name} (${signal.protocol})"
                    }
                    is SignalCapture.Nfc -> {
                        _replaySuccess.value = "NFC emulation ready: ${signal.name}"
                    }
                    is SignalCapture.Rfid -> {
                        _replaySuccess.value = "RFID emulation ready: ${signal.name}"
                    }
                    is SignalCapture.IButton -> {
                        _replaySuccess.value = "iButton emulation ready: ${signal.name}"
                    }
                }

                // Simulate transmission delay
                kotlinx.coroutines.delay(1500)
            } catch (e: Exception) {
                _error.value = "Replay failed: ${e.message}"
            } finally {
                _isReplaying.value = false
            }
        }
    }

    fun deleteSignal(signal: SignalCapture) {
        viewModelScope.launch {
            try {
                val result = fileSystem.delete(signal.path)
                if (result.isSuccess) {
                    _signals.value = _signals.value.filter { it.path != signal.path }
                    if (_selectedSignal.value?.path == signal.path) {
                        _selectedSignal.value = null
                        _waveformData.value = null
                    }
                    loadSignalCounts()
                } else {
                    _error.value = "Failed to delete signal"
                }
            } catch (e: Exception) {
                _error.value = "Delete failed: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearReplaySuccess() {
        _replaySuccess.value = null
    }

    companion object {
        fun formatFrequency(hz: Long): String {
            return when {
                hz >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.3f GHz", hz / 1_000_000_000.0)
                hz >= 1_000_000 -> String.format(java.util.Locale.US, "%.3f MHz", hz / 1_000_000.0)
                hz >= 1_000 -> String.format(java.util.Locale.US, "%.3f kHz", hz / 1_000.0)
                else -> "$hz Hz"
            }
        }

        fun getSignalSubtitle(signal: SignalCapture): String {
            return when (signal) {
                is SignalCapture.SubGhz -> "${signal.protocol} @ ${formatFrequency(signal.frequency)}"
                is SignalCapture.Infrared -> "${signal.protocol} - ${signal.command}"
                is SignalCapture.Nfc -> "${signal.deviceType} - ${signal.uid}"
                is SignalCapture.Rfid -> "${signal.protocol} - ${signal.keyData}"
                is SignalCapture.IButton -> "${signal.protocol} - ${signal.keyData}"
            }
        }

        fun getSignalIcon(type: SignalType): String {
            return when (type) {
                SignalType.SUBGHZ -> "📡"
                SignalType.INFRARED -> "🔴"
                SignalType.NFC -> "📱"
                SignalType.RFID -> "🏷️"
                SignalType.IBUTTON -> "🔑"
            }
        }
    }
}
