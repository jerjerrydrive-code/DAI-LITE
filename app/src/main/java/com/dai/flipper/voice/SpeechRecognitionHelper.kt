package com.dai.flipper.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Helper class for speech-to-text functionality.
 * Wraps Android's SpeechRecognizer API for voice input.
 */
class SpeechRecognitionHelper(
    private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult.asStateFlow()

    /**
     * Check if speech recognition is available on this device
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Start listening for speech input
     */
    fun startListening() {
        if (!isAvailable()) {
            _state.value = SpeechState.Error("Speech recognition not available")
            return
        }

        // Clean up any existing recognizer
        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Enable offline recognition if available
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }

        _state.value = SpeechState.Listening()
        _partialResult.value = ""
        speechRecognizer?.startListening(intent)
    }

    /**
     * Stop listening and clean up
     */
    fun stopListening() {
        speechRecognizer?.apply {
            stopListening()
            cancel()
            destroy()
        }
        speechRecognizer = null

        if (_state.value is SpeechState.Listening) {
            _state.value = SpeechState.Idle
        }
    }

    /**
     * Cancel current recognition without processing
     */
    fun cancel() {
        stopListening()
        _state.value = SpeechState.Idle
        _partialResult.value = ""
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = SpeechState.Listening()
        }

        override fun onBeginningOfSpeech() {
            _state.value = SpeechState.Listening()
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Could be used for audio level visualization
            if (_state.value is SpeechState.Listening) {
                _state.value = SpeechState.Listening(rmsdB)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Not used
        }

        override fun onEndOfSpeech() {
            _state.value = SpeechState.Processing
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error ($error)"
            }

            // For "no match" or "no speech", just return to idle without error
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                _state.value = SpeechState.Idle
            } else {
                _state.value = SpeechState.Error(errorMessage)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val result = matches?.firstOrNull() ?: ""

            _state.value = SpeechState.Result(result)
            _partialResult.value = ""

            // Auto-reset to idle after result
            stopListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull() ?: ""
            _partialResult.value = partial
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Not used
        }
    }

    /**
     * Clean up resources - call when done
     */
    fun destroy() {
        stopListening()
    }
}

/**
 * State of the speech recognition process
 */
sealed class SpeechState {
    /** Not listening */
    data object Idle : SpeechState()

    /** Actively listening for speech */
    data class Listening(val audioLevel: Float = 0f) : SpeechState()

    /** Processing the speech input */
    data object Processing : SpeechState()

    /** Got a successful result */
    data class Result(val text: String) : SpeechState()

    /** An error occurred */
    data class Error(val message: String) : SpeechState()
}
