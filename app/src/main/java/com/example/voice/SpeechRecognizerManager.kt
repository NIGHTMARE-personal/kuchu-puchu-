package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.data.local.AppLanguageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeechRecognizerManager(
    private val context: Context,
    private val onTranscriptReceived: (transcript: String, confidence: Float?) -> Unit,
    private val onErrorOccurred: (errorCode: Int, errorCodeName: String, userMessage: String) -> Unit,
    private val onPermissionRequired: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasRetriedBusy = false
    private var isSessionActive = false
    private var isCurrentSessionOnDevice = false

    var currentLanguageCode: String = "SYSTEM"
    var preferOfflineSpeech: Boolean = false
    var onListeningStarted: (() -> Unit)? = null

    val isCurrentSessionOffline: Boolean
        get() = isCurrentSessionOnDevice

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Checks if speech recognition is available on the device,
     * either via default system recognizer or on-device recognizer.
     */
    val isAvailable: Boolean
        get() {
            val defaultAvail = try {
                SpeechRecognizer.isRecognitionAvailable(context)
            } catch (e: Exception) {
                false
            }
            val onDeviceAvail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                } catch (e: Exception) {
                    false
                }
            } else false

            return defaultAvail || onDeviceAvail
        }

    /**
     * Checks if on-device offline recognition is specifically available.
     */
    val isOnDeviceAvailable: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                } catch (e: Exception) {
                    false
                }
            } else false
        }

    /**
     * Creates speech recognizer instance.
     * When forceOnDevice or preferOfflineSpeech is true, prioritizes
     * createOnDeviceSpeechRecognizer() on API 31+; otherwise tries default and falls back.
     */
    private fun createRecognizer(forceOnDevice: Boolean = false): SpeechRecognizer? {
        val shouldTryOnDeviceFirst = forceOnDevice || preferOfflineSpeech

        if (shouldTryOnDeviceFirst && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    val onDevice = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    if (onDevice != null) {
                        isCurrentSessionOnDevice = true
                        Log.i("SpeechRecognizerManager", "Created on-device speech recognizer")
                        return onDevice
                    }
                }
            } catch (e: Exception) {
                Log.w("SpeechRecognizerManager", "createOnDeviceSpeechRecognizer preferred attempt failed", e)
            }
        }

        // If forceOnDevice was specifically requested and failed, don't fall back to online
        if (forceOnDevice) {
            return null
        }

        val defaultAvail = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            false
        }

        if (defaultAvail) {
            try {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                if (recognizer != null) {
                    isCurrentSessionOnDevice = false
                    return recognizer
                }
            } catch (e: Exception) {
                Log.w("SpeechRecognizerManager", "createSpeechRecognizer failed, trying fallback", e)
            }
        }

        // Fall back to on-device speech recognizer if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    val onDevice = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    if (onDevice != null) {
                        isCurrentSessionOnDevice = true
                        Log.i("SpeechRecognizerManager", "Using on-device speech recognizer fallback")
                        return onDevice
                    }
                }
            } catch (e: Exception) {
                Log.w("SpeechRecognizerManager", "createOnDeviceSpeechRecognizer fallback failed", e)
            }
        }

        return null
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
            _errorMessage.value = null
            _partialText.value = ""
            onListeningStarted?.invoke()
        }

        override fun onBeginningOfSpeech() {
            _isListening.value = true
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize RMS dB (-2dB to ~10dB) to 0f..1f for ripple animations and testing
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _rmsLevel.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _isListening.value = false
            _rmsLevel.value = 0f
            isSessionActive = false
        }

        override fun onError(error: Int) {
            isSessionActive = false
            _isListening.value = false
            _rmsLevel.value = 0f

            val (codeName, userMessage) = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH ->
                    Pair("ERROR_NO_MATCH", "I didn't catch any speech — try again")
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    Pair("ERROR_SPEECH_TIMEOUT", "No speech detected")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    Pair("ERROR_INSUFFICIENT_PERMISSIONS", "Microphone permission required")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    Pair("ERROR_RECOGNIZER_BUSY", "Voice recognizer is busy")
                SpeechRecognizer.ERROR_SERVER ->
                    Pair("ERROR_SERVER", "Voice needs a connection — use typed input")
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    Pair("ERROR_NETWORK_TIMEOUT", "Voice needs a connection — use typed input")
                SpeechRecognizer.ERROR_NETWORK ->
                    Pair("ERROR_NETWORK", "Voice needs a connection — use typed input")
                SpeechRecognizer.ERROR_AUDIO ->
                    Pair("ERROR_AUDIO", "Microphone error — check device audio")
                SpeechRecognizer.ERROR_CLIENT ->
                    Pair("ERROR_CLIENT", "Voice client error — try again")
                else ->
                    Pair("ERROR_$error", "Voice recognition error ($error)")
            }

            Log.w("SpeechRecognizerManager", "Speech recognition error: $codeName ($error) -> $userMessage")

            val isNetworkOrServerError = error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                error == SpeechRecognizer.ERROR_SERVER

            // Fallback to on-device offline recognition when online recognition fails
            if (!isCurrentSessionOnDevice && isNetworkOrServerError && isOnDeviceAvailable) {
                Log.i("SpeechRecognizerManager", "Online recognition failed ($codeName), falling back to on-device speech recognizer")
                val fallbackMsg = "Connection issue — falling back to offline speech..."
                _errorMessage.value = fallbackMsg
                onErrorOccurred(error, codeName, "Online speech failed ($codeName). Falling back to offline speech recognition.")
                mainHandler.postDelayed({
                    startListening(forceOnDevice = true, isRetry = true)
                }, 300L)
                return
            }

            _errorMessage.value = userMessage

            // Notify ViewModel to log to command history with "VOICE:" prefix
            onErrorOccurred(error, codeName, userMessage)

            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    onPermissionRequired()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    // Auto-retry once after 400ms
                    if (!hasRetriedBusy) {
                        hasRetriedBusy = true
                        Log.i("SpeechRecognizerManager", "Auto-retrying once after 400ms for ERROR_RECOGNIZER_BUSY")
                        mainHandler.postDelayed({
                            destroy()
                            startListening(isRetry = true)
                        }, 400L)
                    }
                }
                SpeechRecognizer.ERROR_CLIENT -> {
                    if (!hasRetriedBusy) {
                        hasRetriedBusy = true
                        Log.i("SpeechRecognizerManager", "Auto-retrying once after 350ms for ERROR_CLIENT")
                        mainHandler.postDelayed({
                            destroy()
                            startListening(isRetry = true)
                        }, 350L)
                    }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isSessionActive = false
            _isListening.value = false
            _rmsLevel.value = 0f
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            val recognizedText = matches?.firstOrNull()?.trim().orEmpty()
            val confidence = scores?.firstOrNull()

            _partialText.value = recognizedText
            if (recognizedText.isNotBlank()) {
                onTranscriptReceived(recognizedText, confidence)
            } else {
                val msg = "I didn't catch any speech — try again"
                _errorMessage.value = msg
                onErrorOccurred(SpeechRecognizer.ERROR_NO_MATCH, "ERROR_NO_MATCH", msg)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull().orEmpty()
            if (partial.isNotBlank()) {
                _partialText.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening(forceOnDevice: Boolean = false, isRetry: Boolean = false) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { startListening(forceOnDevice, isRetry) }
            return
        }

        if (!isRetry) {
            hasRetriedBusy = false
        }

        // Mutual exclusion: stop TTS before listening session starts
        onListeningStarted?.invoke()

        // Guard against double starts while a session is already active
        if (isSessionActive || _isListening.value) {
            Log.w("SpeechRecognizerManager", "Session already active, cancelling previous session")
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                // Ignore
            }
            isSessionActive = false
            _isListening.value = false
        }

        if (!isAvailable) {
            val msg = "Voice unavailable on this device — type commands instead"
            _errorMessage.value = msg
            onErrorOccurred(-1, "ERROR_UNAVAILABLE", msg)
            return
        }

        try {
            // Recreate recognizer if null, retrying, or switching on-device mode
            if (speechRecognizer == null || isRetry || (forceOnDevice != isCurrentSessionOnDevice)) {
                destroyInternal()
                val recognizer = createRecognizer(forceOnDevice = forceOnDevice)
                if (recognizer == null) {
                    val msg = if (forceOnDevice)
                        "Offline speech model not available on this device"
                    else
                        "Voice unavailable on this device — type commands instead"
                    _errorMessage.value = msg
                    onErrorOccurred(-1, "ERROR_UNAVAILABLE", msg)
                    return
                }
                speechRecognizer = recognizer.apply {
                    setRecognitionListener(recognitionListener)
                }
            }

            val speechTag = AppLanguageManager.getSpeechTag(currentLanguageCode)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true)
                if (forceOnDevice || preferOfflineSpeech || isCurrentSessionOnDevice) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            isSessionActive = true
            speechRecognizer?.startListening(intent)
            _isListening.value = true
            _errorMessage.value = null
            _partialText.value = ""
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Failed to start listening", e)
            isSessionActive = false
            _isListening.value = false
            val msg = e.localizedMessage ?: "Failed to start speech recognizer"
            _errorMessage.value = msg
            onErrorOccurred(-1, "ERROR_START_FAILED", msg)
        }
    }

    fun stopListening() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { stopListening() }
            return
        }
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Error stopping listening", e)
        }
        isSessionActive = false
        _isListening.value = false
        _rmsLevel.value = 0f
    }

    fun cancel() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { cancel() }
            return
        }
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Error cancelling speech recognizer", e)
        }
        isSessionActive = false
        _isListening.value = false
        _rmsLevel.value = 0f
    }

    private fun destroyInternal() {
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Error cancelling speech recognizer", e)
        }
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Error destroying speech recognizer", e)
        }
        speechRecognizer = null
    }

    fun destroy() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { destroy() }
            return
        }
        isSessionActive = false
        _isListening.value = false
        _rmsLevel.value = 0f
        mainHandler.removeCallbacksAndMessages(null)
        destroyInternal()
    }
}
