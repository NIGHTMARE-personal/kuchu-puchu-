package com.example.ui

import android.Manifest
import android.app.Application
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.CommandHistoryEntity
import com.example.data.local.CommandHistoryRepository
import com.example.data.local.SecurePreferences
import com.example.data.modelmanager.OfflineModelManager
import com.example.executor.ActionExecutorCoordinator
import com.example.llm.OfflineLlmEngine
import com.example.llm.OnlineLlmEngine
import com.example.model.ActionCommand
import com.example.model.ActionType
import com.example.model.AiModelItem
import com.example.model.CommandSource
import com.example.model.ExecutionResult
import com.example.router.CommandRouter
import com.example.router.RouterResult
import com.example.voice.SpeechRecognizerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NavTab {
    HOME, NOTES, PROFILE, MODELS, SETTINGS
}

data class PendingConfirmationState(
    val command: ActionCommand,
    val targetResolved: String,
    val details: String? = null,
    val localizedConfirmationText: String? = null,
    val attachmentBadge: String? = null,
    val engineName: String? = null,
    val routeType: String? = null,
    val latencyMs: Long? = null,
    val confidence: Float? = null,
    val rawIntentJson: String? = null,
    val attachmentUri: String? = null
)

data class PermissionRequestState(
    val permission: String,
    val title: String,
    val explanation: String
)

data class MicTestState(
    val isTesting: Boolean = false,
    val currentRms: Float = 0f,
    val hasAudioMoved: Boolean = false,
    val maxRms: Float = 0f,
    val resultMessage: String? = null,
    val isSuccess: Boolean = false
)

data class NovaUiState(
    val currentTab: NavTab = NavTab.HOME,
    val isVoiceAvailable: Boolean = true,
    val isOfflineSpeechEnabled: Boolean = false,
    val isMicPermanentlyDenied: Boolean = false,
    val isListening: Boolean = false,
    val rmsLevel: Float = 0f,
    val statusText: String = "Tap or hold mic to give a command",
    val partialTranscript: String = "",
    val lastTranscript: String = "",
    val lastAction: ActionCommand? = null,
    val lastExecutionMessage: String? = null,
    val isExecuting: Boolean = false,
    val pendingConfirmation: PendingConfirmationState? = null,
    val pendingPermissionRequest: PermissionRequestState? = null,
    val micTestState: MicTestState = MicTestState(),
    val activeEngine: String = "ONLINE", // "ONLINE" or "OFFLINE"
    val onlineProvider: String = "GEMINI", // "GEMINI", "OPENAI", "ANTHROPIC"
    val geminiKey: String = "",
    val openaiKey: String = "",
    val anthropicKey: String = "",
    val huggingFaceToken: String = "",
    val geminiModel: String = "auto",
    val openaiModel: String = "auto",
    val anthropicModel: String = "auto",
    val isAutoCallEnabled: Boolean = false,
    val isFastRouterEnabled: Boolean = true,
    val activeModelId: String = "gemma_3_1b",
    val selectedLanguage: String = "SYSTEM",
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val isVoiceFeedbackEnabled: Boolean = true,
    val availableStorageBytes: Long = 0L,
    val usedStorageBytes: Long = 0L,
    val attachments: List<com.example.model.AttachmentItem> = emptyList(),
    val isAttachmentSheetOpen: Boolean = false,
    val inputPlaceholder: String = "Ask or command Kuchu Puchu...",
    val lastFailedCommand: Pair<String, List<com.example.model.AttachmentItem>>? = null,
    val imageUnsupportedRecovery: com.example.model.ImageUnsupportedState? = null,
    val pendingOrganizePlan: com.example.model.OrganizePlan? = null,
    val organizeProgress: com.example.model.OrganizeProgressState? = null,
    val organizeSummary: com.example.model.OrganizeSummaryState? = null,
    val showStoragePermissionExplanation: Boolean = false,
    val isStoragePermanentlyDenied: Boolean = false,
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val streamingEngineName: String = "",
    val chatHistory: List<com.example.model.ChatTurn> = emptyList(),
    val engineMode: String = "AUTO", // "AUTO", "ONLINE_ONLY", "OFFLINE_ONLY"
    val isHapticsEnabled: Boolean = true,
    val isStreamingAnimationEnabled: Boolean = true,
    val isHighFidelityAnimationsEnabled: Boolean = true,
    val imageGenAspectRatio: String = "1:1",
    val isAutoCopyEnabled: Boolean = false,
    val showOnboarding: Boolean = false,
    val activeFallbackMessage: String? = null,
    val selectedTtsVoice: String = "en-US-AvaNeural",
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val downloadedTtsVoices: Set<String> = emptySet(),
    val isDownloadingVoice: Boolean = false,
    val voiceDownloadProgress: Float = 0f,
    val downloadingVoiceId: String? = null,
    val previewingVoiceId: String? = null
)

class NovaAssistantViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = SecurePreferences(application)
    private val database = AppDatabase.getDatabase(application)
    val historyRepository = CommandHistoryRepository(database.commandHistoryDao())
    val noteRepository = com.example.data.local.NoteRepository(database.noteDao())
    val userProfileRepository = com.example.data.local.UserProfileRepository(database.userProfileDao(), application)
    val voiceFeedbackManager = com.example.voice.VoiceFeedbackManager(
        context = application,
        isEnabledProvider = { preferences.isVoiceFeedbackEnabled },
        languageCodeProvider = { preferences.selectedLanguage },
        selectedVoiceProvider = { preferences.selectedTtsVoice },
        speedProvider = { preferences.ttsSpeed },
        pitchProvider = { preferences.ttsPitch },
        isListeningProvider = { speechRecognizerManager.isListening.value }
    )
    val modelManager = OfflineModelManager(application, viewModelScope, preferences)
    val capabilityRegistry = com.example.data.registry.CapabilityRegistry(preferences, modelManager)

    private val commandRouter = CommandRouter()
    private val onlineLlmEngine = OnlineLlmEngine(preferences)
    private val offlineLlmEngine = OfflineLlmEngine(application, preferences, modelManager)
    private val executorCoordinator = ActionExecutorCoordinator(application, preferences)
    val downloadsOrganizerManager = com.example.organizer.DownloadsOrganizerManager(application, database.organizeMoveDao())
    val downloadsPlanner = com.example.organizer.DownloadsPlanner(onlineLlmEngine)
    private var pendingOrganizeCommand: ActionCommand? = null

    private val speechRecognizerManager: SpeechRecognizerManager
    private var micTestJob: Job? = null
    private var processingJob: Job? = null

    private val _uiState = MutableStateFlow(
        NovaUiState(
            isOfflineSpeechEnabled = preferences.isOfflineSpeechEnabled,
            isMicPermanentlyDenied = preferences.isMicPermanentlyDenied,
            isStoragePermanentlyDenied = preferences.isStoragePermanentlyDenied,
            activeEngine = preferences.activeEngine,
            onlineProvider = preferences.onlineProvider,
            geminiKey = preferences.geminiApiKey,
            openaiKey = preferences.openaiApiKey,
            anthropicKey = preferences.anthropicApiKey,
            huggingFaceToken = preferences.huggingFaceToken,
            geminiModel = preferences.geminiModel,
            openaiModel = preferences.openaiModel,
            anthropicModel = preferences.anthropicModel,
            isAutoCallEnabled = preferences.isAutoCallEnabled,
            isFastRouterEnabled = preferences.isFastRouterEnabled,
            activeModelId = preferences.activeModelId,
            selectedLanguage = preferences.selectedLanguage,
            themeMode = preferences.themeMode,
            isVoiceFeedbackEnabled = preferences.isVoiceFeedbackEnabled,
            engineMode = preferences.engineMode,
            isHapticsEnabled = preferences.isHapticsEnabled,
            isStreamingAnimationEnabled = preferences.isStreamingAnimationEnabled,
            isHighFidelityAnimationsEnabled = preferences.isHighFidelityAnimationsEnabled,
            imageGenAspectRatio = preferences.imageGenAspectRatio,
            isAutoCopyEnabled = preferences.isAutoCopyEnabled,
            showOnboarding = !preferences.hasCompletedOnboarding,
            selectedTtsVoice = preferences.selectedTtsVoice,
            ttsSpeed = preferences.ttsSpeed,
            ttsPitch = preferences.ttsPitch,
            downloadedTtsVoices = preferences.downloadedTtsVoices
        )
    )
    val uiState: StateFlow<NovaUiState> = _uiState.asStateFlow()

    val modelsList: StateFlow<List<AiModelItem>> = modelManager.modelsState

    val historyList: StateFlow<List<CommandHistoryEntity>> = historyRepository.historyList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        speechRecognizerManager = SpeechRecognizerManager(
            context = application,
            onTranscriptReceived = { transcript, confidence ->
                handleIncomingTranscript(transcript, confidence)
            },
            onErrorOccurred = { errorCode, errorCodeName, userMessage ->
                handleSpeechError(errorCode, errorCodeName, userMessage)
            },
            onPermissionRequired = {
                requestMicPermission()
            }
        ).apply {
            currentLanguageCode = preferences.selectedLanguage
            preferOfflineSpeech = preferences.isOfflineSpeechEnabled
            onListeningStarted = {
                voiceFeedbackManager.stop()
            }
        }

        // Check voice availability at startup
        val voiceAvailable = speechRecognizerManager.isAvailable
        _uiState.update {
            it.copy(
                isVoiceAvailable = voiceAvailable,
                statusText = if (!voiceAvailable)
                    "Voice unavailable on this device — type commands instead"
                else
                    "Push to talk"
            )
        }

        viewModelScope.launch {
            speechRecognizerManager.isListening.collect { listening ->
                _uiState.update {
                    it.copy(
                        isListening = listening,
                        statusText = if (listening) "Listening..." else it.statusText
                    )
                }
            }
        }

        viewModelScope.launch {
            speechRecognizerManager.rmsLevel.collect { rms ->
                if (_uiState.value.micTestState.isTesting) {
                    _uiState.update { it.copy(rmsLevel = rms) }
                }
            }
        }

        viewModelScope.launch {
            speechRecognizerManager.partialText.collect { partial ->
                _uiState.update { it.copy(partialTranscript = partial) }
            }
        }

        viewModelScope.launch {
            speechRecognizerManager.errorMessage.collect { error ->
                if (error != null && !_uiState.value.micTestState.isTesting) {
                    _uiState.update { it.copy(statusText = error) }
                }
            }
        }

        modelManager.onModelDownloadedListener = { downloadedModelId ->
            preferences.activeModelId = downloadedModelId
            preferences.activeEngine = "OFFLINE"
            val item = modelManager.modelsState.value.firstOrNull { it.id == downloadedModelId }
            _uiState.update {
                it.copy(
                    activeModelId = downloadedModelId,
                    activeEngine = "OFFLINE",
                    statusText = "${item?.name ?: "Model"} ready for offline AI!"
                )
            }
        }

        refreshStorageInfo()
    }

    fun selectTab(tab: NavTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == NavTab.MODELS) {
            refreshStorageInfo()
            modelManager.refreshModelStatuses(preferences.activeModelId)
        }
    }

    fun toggleListening() {
        if (_uiState.value.isMicPermanentlyDenied) {
            showPermanentMicDenialCard()
            return
        }

        if (!_uiState.value.isVoiceAvailable) {
            val available = speechRecognizerManager.isAvailable
            if (available) {
                _uiState.update { it.copy(isVoiceAvailable = true) }
            } else {
                _uiState.update {
                    it.copy(statusText = "Voice unavailable on this device — you can type commands below")
                }
                return
            }
        }

        if (_uiState.value.isListening) {
            speechRecognizerManager.stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        if (_uiState.value.isMicPermanentlyDenied) {
            showPermanentMicDenialCard()
            return
        }

        if (!_uiState.value.isVoiceAvailable) {
            val available = speechRecognizerManager.isAvailable
            if (available) {
                _uiState.update { it.copy(isVoiceAvailable = true) }
            } else {
                _uiState.update {
                    it.copy(statusText = "Voice unavailable on this device — you can type commands below")
                }
                return
            }
        }

        _uiState.update {
            it.copy(
                partialTranscript = "",
                pendingConfirmation = null,
                statusText = if (speechRecognizerManager.preferOfflineSpeech) "Listening offline..." else "Listening..."
            )
        }
        speechRecognizerManager.startListening()
    }

    fun stopListening() {
        speechRecognizerManager.stopListening()
    }

    private fun handleIncomingTranscript(transcript: String, confidence: Float? = null) {
        val testState = _uiState.value.micTestState
        if (testState.isTesting) {
            micTestJob?.cancel()
            speechRecognizerManager.stopListening()
            _uiState.update { state ->
                state.copy(
                    isListening = false,
                    micTestState = state.micTestState.copy(
                        isTesting = false,
                        resultMessage = "Recognized: \"$transcript\"",
                        isSuccess = true
                    )
                )
            }
        } else {
            processTranscript(transcript, isVoice = true, confidence = confidence)
        }
    }

    fun handleSpeechError(errorCode: Int, errorCodeName: String, userMessage: String) {
        val testState = _uiState.value.micTestState
        if (testState.isTesting) {
            micTestJob?.cancel()
            val audioMoved = testState.hasAudioMoved || testState.maxRms > 0.05f
            val testMessage = if (!audioMoved) {
                "No audio reaching the app (device microphone issue)"
            } else {
                "Audio captured, but no speech service responded (missing on this device)"
            }
            _uiState.update { state ->
                state.copy(
                    isListening = false,
                    micTestState = state.micTestState.copy(
                        isTesting = false,
                        resultMessage = testMessage,
                        isSuccess = false
                    )
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isListening = false,
                    statusText = userMessage
                )
            }
        }

        // Log every code to the history log with a "VOICE:" prefix
        viewModelScope.launch {
            historyRepository.recordCommand(
                transcript = "VOICE: $errorCodeName",
                action = "VOICE_ERROR",
                target = userMessage,
                datetime = null,
                source = "SPEECH_RECOGNIZER",
                outcome = "ERROR",
                details = "$errorCodeName: $userMessage"
            )
        }

        if (errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            requestMicPermission()
        }
    }

    fun startMicTest() {
        if (!speechRecognizerManager.isAvailable) {
            _uiState.update {
                it.copy(
                    micTestState = MicTestState(
                        isTesting = false,
                        resultMessage = "Speech service unavailable on this device",
                        isSuccess = false
                    )
                )
            }
            return
        }

        micTestJob?.cancel()
        _uiState.update {
            it.copy(
                micTestState = MicTestState(
                    isTesting = true,
                    resultMessage = null
                )
            )
        }

        speechRecognizerManager.startListening()

        micTestJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            var audioDetected = false
            var peakRms = 0f

            while (isActive && (System.currentTimeMillis() - startTime) < 5000L) {
                val currentLevel = speechRecognizerManager.rmsLevel.value
                if (currentLevel > 0.05f) {
                    audioDetected = true
                    if (currentLevel > peakRms) peakRms = currentLevel
                }
                _uiState.update { state ->
                    if (state.micTestState.isTesting) {
                        state.copy(
                            micTestState = state.micTestState.copy(
                                currentRms = currentLevel,
                                hasAudioMoved = audioDetected || state.micTestState.hasAudioMoved,
                                maxRms = maxOf(peakRms, state.micTestState.maxRms)
                            )
                        )
                    } else state
                }
                delay(60L)
            }

            // If still testing after 5 seconds
            val currentState = _uiState.value.micTestState
            if (currentState.isTesting) {
                speechRecognizerManager.stopListening()
                val finalMoved = audioDetected || currentState.hasAudioMoved || currentState.maxRms > 0.05f
                val message = if (!finalMoved) {
                    "No audio reaching the app (device microphone issue)"
                } else {
                    "Audio captured, but no speech service responded (missing on this device)"
                }
                _uiState.update { state ->
                    state.copy(
                        isListening = false,
                        micTestState = state.micTestState.copy(
                            isTesting = false,
                            resultMessage = message,
                            isSuccess = false
                        )
                    )
                }
            }
        }
    }

    fun stopMicTest() {
        micTestJob?.cancel()
        speechRecognizerManager.stopListening()
        _uiState.update { state ->
            state.copy(
                isListening = false,
                micTestState = state.micTestState.copy(
                    isTesting = false
                )
            )
        }
    }

    fun requestMicPermission() {
        _uiState.update {
            it.copy(
                pendingPermissionRequest = PermissionRequestState(
                    permission = Manifest.permission.RECORD_AUDIO,
                    title = "Microphone Permission Required",
                    explanation = "Kuchu Puchu needs microphone access to listen to your voice commands and test audio levels."
                )
            )
        }
    }

    fun onMicPermissionGranted(startListeningImmediately: Boolean = false) {
        preferences.isMicPermanentlyDenied = false
        dismissPermissionDialog()
        _uiState.update {
            it.copy(
                isMicPermanentlyDenied = false,
                statusText = "Microphone access enabled"
            )
        }
        if (startListeningImmediately) {
            startListening()
        }
    }

    fun onMicPermissionDeniedOnce() {
        preferences.isMicPermanentlyDenied = false
        dismissPermissionDialog()
        _uiState.update {
            it.copy(
                isMicPermanentlyDenied = false,
                statusText = "Microphone permission is needed for voice commands"
            )
        }
    }

    fun onMicPermissionPermanentlyDenied() {
        preferences.isMicPermanentlyDenied = true
        dismissPermissionDialog()
        _uiState.update {
            it.copy(
                isMicPermanentlyDenied = true,
                statusText = "Voice is disabled — allow microphone access in Settings"
            )
        }
    }

    fun onMicPermissionRestored() {
        preferences.isMicPermanentlyDenied = false
        _uiState.update {
            it.copy(
                isMicPermanentlyDenied = false,
                statusText = "Microphone access enabled"
            )
        }
    }

    fun showPermanentMicDenialCard() {
        preferences.isMicPermanentlyDenied = true
        _uiState.update {
            it.copy(
                isMicPermanentlyDenied = true,
                statusText = "Voice is disabled — allow microphone access in Settings"
            )
        }
    }

    fun onMicPermissionDenied() {
        onMicPermissionDeniedOnce()
    }

    fun openAttachmentSheet() {
        _uiState.update { it.copy(isAttachmentSheetOpen = true) }
    }

    fun closeAttachmentSheet() {
        _uiState.update { it.copy(isAttachmentSheetOpen = false) }
    }

    fun setInputPlaceholder(placeholder: String) {
        _uiState.update { it.copy(inputPlaceholder = placeholder) }
    }

    fun addAttachment(attachment: com.example.model.AttachmentItem) {
        val currentValid = _uiState.value.attachments.filter { !it.isInlineError }
        if (currentValid.size >= 3 && !attachment.isInlineError) {
            val errorItem = attachment.copy(
                isInlineError = true,
                errorMessage = "Maximum 3 attachments allowed"
            )
            _uiState.update { it.copy(attachments = it.attachments + errorItem) }
            return
        }

        if (attachment.size > 10 * 1024 * 1024L && !attachment.isInlineError) {
            val errorItem = attachment.copy(
                isInlineError = true,
                errorMessage = "${attachment.name}: Exceeds 10 MB limit"
            )
            _uiState.update { it.copy(attachments = it.attachments + errorItem) }
            return
        }

        _uiState.update { it.copy(attachments = it.attachments + attachment) }
    }

    fun addInlineErrorChip(message: String) {
        val errorItem = com.example.model.AttachmentItem(
            id = "error_${System.currentTimeMillis()}",
            kind = com.example.model.AttachmentKind.FILE,
            uri = android.net.Uri.EMPTY,
            mime = "",
            name = "Error",
            size = 0L,
            isInlineError = true,
            errorMessage = message
        )
        _uiState.update { it.copy(attachments = it.attachments + errorItem) }
    }

    fun removeAttachment(id: String) {
        _uiState.update {
            it.copy(attachments = it.attachments.filter { item -> item.id != id })
        }
    }

    fun clearAttachments() {
        _uiState.update { it.copy(attachments = emptyList()) }
    }

    fun cancelLlmRequest() {
        processingJob?.cancel()
        processingJob = null
        _uiState.update {
            it.copy(
                isExecuting = false,
                isStreaming = false,
                streamingText = "",
                statusText = "Request cancelled"
            )
        }
    }

    fun stopStreaming() {
        val currentText = _uiState.value.streamingText
        val lastTrans = _uiState.value.lastTranscript
        val engine = _uiState.value.streamingEngineName
        processingJob?.cancel()
        processingJob = null
        _uiState.update {
            it.copy(
                isExecuting = false,
                isStreaming = false,
                streamingText = "",
                statusText = "Stream stopped"
            )
        }

        if (currentText.isNotBlank()) {
            val stoppedCommand = ActionCommand(
                action = ActionType.ANSWER,
                target = "Response",
                reply = currentText,
                source = com.example.model.CommandSource.GEMINI,
                rawTranscript = lastTrans,
                engineName = engine.ifBlank { "Assistant" }
            )
            viewModelScope.launch {
                executeCommand(stoppedCommand, isConfirmed = true)
            }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
        _uiState.update {
            it.copy(
                chatHistory = emptyList(),
                streamingText = "",
                isStreaming = false,
                partialTranscript = "",
                lastTranscript = "",
                lastAction = null,
                lastExecutionMessage = null,
                pendingConfirmation = null,
                statusText = "Chat cleared • Fresh start!"
            )
        }
    }

    fun setEngineMode(mode: String) {
        preferences.engineMode = mode
        if (mode == "ONLINE_ONLY") {
            preferences.activeEngine = "ONLINE"
        } else if (mode == "OFFLINE_ONLY") {
            preferences.activeEngine = "OFFLINE"
        }
        _uiState.update { it.copy(engineMode = mode, activeEngine = preferences.activeEngine) }
    }

    fun dismissOnboarding() {
        preferences.hasCompletedOnboarding = true
        _uiState.update { it.copy(showOnboarding = false) }
    }

    fun clearFallbackMessage() {
        _uiState.update { it.copy(activeFallbackMessage = null) }
    }

    fun retryLastCommand() {
        val failed = _uiState.value.lastFailedCommand ?: return
        _uiState.update { it.copy(lastFailedCommand = null) }
        processTranscript(failed.first, isVoice = false, attachmentsOverride = failed.second)
    }

    fun processTranscript(
        transcript: String,
        isVoice: Boolean = false,
        confidence: Float? = null,
        attachmentsOverride: List<com.example.model.AttachmentItem>? = null
    ) {
        val attachmentsToProcess = (attachmentsOverride ?: _uiState.value.attachments)
            .filter { !it.isInlineError && it.errorMessage == null }
        val clean = transcript.trim()

        if (clean.isBlank() && attachmentsToProcess.isEmpty()) return

        // Pre-flight check: IMAGE or PDF attachment present AND active model vision=false
        val hasVisualAttachments = attachmentsToProcess.any {
            it.kind == com.example.model.AttachmentKind.IMAGE || it.kind == com.example.model.AttachmentKind.PDF
        }
        val activeModel = capabilityRegistry.getActiveModel()

        if (hasVisualAttachments && !activeModel.vision) {
            val isOnline = com.example.util.NetworkUtils.isNetworkValidated(getApplication())
            val message = if (isOnline) {
                "This model can't process images"
            } else {
                "This model can't process images and you're offline. Reconnect, or remove the attachment and try another command."
            }

            _uiState.update {
                it.copy(
                    isExecuting = false,
                    statusText = if (isOnline) "This model can't process images" else "This model can't process images and you're offline",
                    imageUnsupportedRecovery = com.example.model.ImageUnsupportedState(
                        isOnline = isOnline,
                        message = message,
                        pendingInput = clean.ifBlank { "Describe the attached content" },
                        pendingAttachments = attachmentsToProcess,
                        showSwitchSheet = false
                    )
                )
            }
            voiceFeedbackManager.speak(if (isOnline) "This model cannot process images. Switch model to continue." else "This model cannot process images and you are offline.")
            return
        }

        val effectiveTranscript = if (clean.isBlank() && attachmentsToProcess.isNotEmpty()) {
            "Describe the attached content"
        } else {
            clean
        }

        val confidenceScore = confidence ?: 0.87f
        val formattedConfidence = String.format(java.util.Locale.US, "%.2f", confidenceScore)
        val historyTranscript = if (isVoice) {
            "VOICE: \"$effectiveTranscript\" ($formattedConfidence)"
        } else {
            effectiveTranscript
        }

        val attachmentBadge = if (attachmentsToProcess.isNotEmpty()) {
            val imageCount = attachmentsToProcess.count { it.kind == com.example.model.AttachmentKind.IMAGE }
            val pdfCount = attachmentsToProcess.count { it.kind == com.example.model.AttachmentKind.PDF }
            when {
                imageCount > 0 && pdfCount == 0 && attachmentsToProcess.size == imageCount -> {
                    if (imageCount == 1) "+1 photo" else "+$imageCount photos"
                }
                pdfCount > 0 && imageCount == 0 && attachmentsToProcess.size == pdfCount -> {
                    if (pdfCount == 1) "+1 PDF" else "+$pdfCount PDFs"
                }
                else -> {
                    if (attachmentsToProcess.size == 1) "+1 file" else "+${attachmentsToProcess.size} files"
                }
            }
        } else null

        val primaryAttachmentUri = attachmentsToProcess.firstOrNull()?.uri?.toString()
        val startTime = System.currentTimeMillis()

        _uiState.update {
            it.copy(
                lastTranscript = effectiveTranscript,
                partialTranscript = effectiveTranscript,
                isExecuting = true,
                statusText = "Processing command...",
                attachments = emptyList(),
                inputPlaceholder = "Ask or command Kuchu Puchu...",
                lastFailedCommand = null,
                imageUnsupportedRecovery = null
            )
        }

        processingJob?.cancel()
        processingJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                var resolvedCommand: ActionCommand? = null
                var routeType = "fast-path"
                var engineName = "Built-in Fast Router"

                // Fast rule-based pattern matching (internal) — only when NO attachments
                if (attachmentsToProcess.isEmpty()) {
                    val routerResult = commandRouter.match(effectiveTranscript)
                    if (routerResult is RouterResult.Match) {
                        resolvedCommand = routerResult.command
                        routeType = "fast-path"
                        engineName = "Built-in Fast Router"
                        _uiState.update {
                            it.copy(
                                statusText = "Executing action..."
                            )
                        }
                    }
                }

                // AI assistant resolution for flexible phrases or multimodal attachments
                if (resolvedCommand == null) {
                    routeType = "LLM"
                    val lang = _uiState.value.selectedLanguage
                    val mode = _uiState.value.engineMode
                    val isOnline = com.example.util.NetworkUtils.isNetworkValidated(getApplication())
                    val hasOnlineKey = preferences.getActiveApiKey().isNotBlank()
                    val isOfflineModelReady = modelManager.isModelDownloaded(preferences.activeModelId)

                    // Gate check: Visual attachments (photo/PDF) require online model
                    if (attachmentsToProcess.isNotEmpty()) {
                        if (!isOnline || !hasOnlineKey) {
                            throw RuntimeException("Online model required for images; check connection.")
                        }
                    }

                    // Rolling chat memory context (last 8 Q&A turns)
                    val memoryContext = if (attachmentsToProcess.isEmpty()) {
                        _uiState.value.chatHistory.takeLast(8)
                    } else {
                        emptyList()
                    }

                    // Primary target engine determination
                    var targetEngine = when {
                        _uiState.value.activeEngine == "ONLINE" -> "ONLINE"
                        _uiState.value.activeEngine == "OFFLINE" -> "OFFLINE"
                        mode == "ONLINE_ONLY" -> "ONLINE"
                        mode == "OFFLINE_ONLY" -> "OFFLINE"
                        else -> { // AUTO
                            if (attachmentsToProcess.isNotEmpty()) {
                                "ONLINE"
                            } else if (hasOnlineKey && isOnline) {
                                "ONLINE"
                            } else if (isOfflineModelReady) {
                                "OFFLINE"
                            } else if (hasOnlineKey) {
                                "ONLINE"
                            } else {
                                "OFFLINE"
                            }
                        }
                    }

                    engineName = if (targetEngine == "OFFLINE") activeModel.displayName else _uiState.value.onlineProvider

                    val accumulatedStream = StringBuilder()
                    val streamCallback: (String) -> Unit = { chunk ->
                        accumulatedStream.append(chunk)
                        val extracted = com.example.llm.StreamTextExtractor.extractReplyText(accumulatedStream.toString())
                        _uiState.update {
                            it.copy(
                                isStreaming = true,
                                streamingText = extracted,
                                streamingEngineName = engineName
                            )
                        }
                    }

                    _uiState.update {
                        it.copy(
                            statusText = if (targetEngine == "OFFLINE") "Processing on device..." else "Processing command...",
                            isStreaming = true,
                            streamingText = "",
                            streamingEngineName = engineName
                        )
                    }

                    if (com.example.router.CommandRouter.isCustomDownloadsOrganizeRequest(effectiveTranscript)) {
                        resolvedCommand = if (targetEngine == "OFFLINE") {
                            ActionCommand(
                                action = ActionType.ANSWER,
                                target = "Custom organizing needs online engine",
                                reply = "Custom organizing needs an online engine. Meanwhile, 'organize my downloads' works offline with standard rules.",
                                source = com.example.model.CommandSource.OFFLINE_LLM,
                                rawTranscript = historyTranscript
                            )
                        } else {
                            ActionCommand(
                                action = ActionType.ORGANIZE_DOWNLOADS,
                                target = "Downloads",
                                organizeMode = "custom",
                                source = com.example.model.CommandSource.GEMINI,
                                rawTranscript = historyTranscript,
                                confirmationText = "Organize files in Downloads?"
                            )
                        }
                    } else {
                        try {
                            resolvedCommand = if (targetEngine == "OFFLINE") {
                                offlineLlmEngine.processTranscript(
                                    transcript = effectiveTranscript,
                                    selectedLanguage = lang,
                                    chatHistory = memoryContext,
                                    onChunk = streamCallback
                                )
                            } else {
                                val payload = if (attachmentsToProcess.isNotEmpty()) {
                                    _uiState.update { it.copy(statusText = "Analyzing attachments...") }
                                    com.example.util.AttachmentProcessor.processAttachments(getApplication(), attachmentsToProcess)
                                } else {
                                    com.example.util.ProcessedMultimodalPayload()
                                }
                                onlineLlmEngine.processTranscript(
                                    transcript = effectiveTranscript,
                                    selectedLanguage = lang,
                                    payload = payload,
                                    chatHistory = memoryContext,
                                    onChunk = streamCallback
                                )
                            }
                        } catch (engineError: Exception) {
                            if (mode == "AUTO" && attachmentsToProcess.isEmpty()) {
                                if (targetEngine == "ONLINE" && isOfflineModelReady) {
                                    val fallbackNote = "Fallback: Online -> ${activeModel.displayName} due to ${engineError.message ?: "Network error"}"
                                    Log.w("NovaAssistantViewModel", fallbackNote)
                                    engineName = activeModel.displayName
                                    _uiState.update {
                                        it.copy(
                                            activeFallbackMessage = fallbackNote,
                                            statusText = "Online failed; falling back to local model...",
                                            streamingText = "",
                                            streamingEngineName = activeModel.displayName
                                        )
                                    }
                                    accumulatedStream.clear()
                                    resolvedCommand = offlineLlmEngine.processTranscript(
                                        transcript = effectiveTranscript,
                                        selectedLanguage = lang,
                                        chatHistory = memoryContext,
                                        onChunk = streamCallback
                                    )
                                } else if (targetEngine == "OFFLINE" && hasOnlineKey && isOnline) {
                                    val fallbackNote = "Fallback: ${activeModel.displayName} -> Online due to ${engineError.message ?: "Local engine error"}"
                                    Log.w("NovaAssistantViewModel", fallbackNote)
                                    engineName = _uiState.value.onlineProvider
                                    _uiState.update {
                                        it.copy(
                                            activeFallbackMessage = fallbackNote,
                                            statusText = "Local failed; falling back to online engine...",
                                            streamingText = "",
                                            streamingEngineName = _uiState.value.onlineProvider
                                        )
                                    }
                                    accumulatedStream.clear()
                                    resolvedCommand = onlineLlmEngine.processTranscript(
                                        transcript = effectiveTranscript,
                                        selectedLanguage = lang,
                                        payload = com.example.util.ProcessedMultimodalPayload(),
                                        chatHistory = memoryContext,
                                        onChunk = streamCallback
                                    )
                                } else {
                                    throw engineError
                                }
                            } else {
                                throw engineError
                            }
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = ""
                    )
                }

                // Rolling memory context: answer actions carry rolling context of the last 8 turns (text only)
                // Action commands (call/open/timer/organize) always run standalone — never context-contaminated
                if (resolvedCommand.action == ActionType.ANSWER) {
                    val replyText = resolvedCommand.reply ?: resolvedCommand.target
                    val newTurn = com.example.model.ChatTurn(
                        userPrompt = effectiveTranscript,
                        assistantReply = replyText
                    )
                    _uiState.update {
                        it.copy(chatHistory = (it.chatHistory + newTurn).takeLast(8))
                    }
                }

                val finalCommand = resolvedCommand.copy(rawTranscript = historyTranscript)
                val latencyMs = System.currentTimeMillis() - startTime

                val rawIntentJson = buildString {
                    append("{\n")
                    append("  \"action\": \"${resolvedCommand.action.key}\",\n")
                    val safeTarget = resolvedCommand.target.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                    append("  \"target\": \"$safeTarget\"")
                    if (!resolvedCommand.datetime.isNullOrBlank()) {
                        append(",\n  \"datetime\": \"${resolvedCommand.datetime}\"")
                    }
                    if (resolvedCommand.durationSeconds != null) {
                        append(",\n  \"duration_seconds\": ${resolvedCommand.durationSeconds}")
                    }
                    if (!resolvedCommand.label.isNullOrBlank()) {
                        val safeLabel = resolvedCommand.label.replace("\\", "\\\\").replace("\"", "\\\"")
                        append(",\n  \"label\": \"$safeLabel\"")
                    }
                    append("\n}")
                }

                // Execute action
                executeCommand(
                    command = finalCommand,
                    isConfirmed = false,
                    attachmentBadge = attachmentBadge,
                    engineName = finalCommand.engineName ?: engineName,
                    routeType = routeType,
                    latencyMs = latencyMs,
                    confidence = confidenceScore,
                    rawIntentJson = rawIntentJson,
                    attachmentUri = primaryAttachmentUri
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = ""
                    )
                }
                if (!isActive) return@launch
                Log.e("NovaAssistantViewModel", "Error processing command", e)
                val errorMessage = e.message ?: "Could not complete command"
                val latencyMs = System.currentTimeMillis() - startTime

                val isVisionRejection = hasVisualAttachments ||
                    errorMessage.contains("vision", ignoreCase = true) ||
                    errorMessage.contains("image", ignoreCase = true) ||
                    errorMessage.contains("unsupported", ignoreCase = true)

                if (isVisionRejection && attachmentsToProcess.isNotEmpty()) {
                    val isOnline = com.example.util.NetworkUtils.isNetworkValidated(getApplication())
                    _uiState.update {
                        it.copy(
                            isExecuting = false,
                            statusText = "Vision request rejected — switch to an image-capable model",
                            lastExecutionMessage = errorMessage,
                            lastFailedCommand = Pair(effectiveTranscript, attachmentsToProcess),
                            imageUnsupportedRecovery = com.example.model.ImageUnsupportedState(
                                isOnline = isOnline,
                                message = "Server-side rejection: $errorMessage",
                                pendingInput = effectiveTranscript,
                                pendingAttachments = attachmentsToProcess,
                                showSwitchSheet = isOnline
                            )
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isExecuting = false,
                            statusText = errorMessage,
                            lastExecutionMessage = errorMessage,
                            lastFailedCommand = Pair(effectiveTranscript, attachmentsToProcess)
                        )
                    }
                }
                voiceFeedbackManager.speakError(errorMessage)
                historyRepository.recordCommand(
                    transcript = historyTranscript,
                    action = "none",
                    target = "",
                    datetime = null,
                    source = if (_uiState.value.activeEngine == "OFFLINE") "LOCAL" else _uiState.value.onlineProvider,
                    outcome = "ERROR",
                    details = errorMessage,
                    attachmentBadge = attachmentBadge,
                    engineName = activeModel.displayName,
                    routeType = "LLM",
                    latencyMs = latencyMs,
                    confidence = confidenceScore,
                    attachmentUri = primaryAttachmentUri
                )
            }
        }
    }

    private suspend fun executeCommand(
        command: ActionCommand,
        isConfirmed: Boolean,
        attachmentBadge: String? = null,
        engineName: String? = null,
        routeType: String? = null,
        latencyMs: Long? = null,
        confidence: Float? = null,
        rawIntentJson: String? = null,
        attachmentUri: String? = null
    ) {
        if (command.action == ActionType.ORGANIZE_DOWNLOADS) {
            handleOrganizeDownloads(
                command = command,
                isConfirmed = isConfirmed,
                attachmentBadge = attachmentBadge,
                engineName = engineName,
                routeType = routeType,
                latencyMs = latencyMs,
                confidence = confidence,
                rawIntentJson = rawIntentJson,
                attachmentUri = attachmentUri
            )
            return
        }

        _uiState.update {
            it.copy(
                lastAction = command,
                statusText = "Executing ${command.action.displayLabel}..."
            )
        }

        val result = executorCoordinator.execute(command, isConfirmed)
        handleExecutionResult(
            command = command,
            result = result,
            attachmentBadge = attachmentBadge,
            engineName = engineName,
            routeType = routeType,
            latencyMs = latencyMs,
            confidence = confidence,
            rawIntentJson = rawIntentJson,
            attachmentUri = attachmentUri
        )
    }

    private fun handleOrganizeDownloads(
        command: ActionCommand,
        isConfirmed: Boolean,
        attachmentBadge: String? = null,
        engineName: String? = null,
        routeType: String? = null,
        latencyMs: Long? = null,
        confidence: Float? = null,
        rawIntentJson: String? = null,
        attachmentUri: String? = null
    ) {
        if (!downloadsOrganizerManager.hasRequiredPermission()) {
            pendingOrganizeCommand = command
            val isPermanentlyDenied = preferences.isStoragePermanentlyDenied
            _uiState.update {
                it.copy(
                    isExecuting = false,
                    showStoragePermissionExplanation = true,
                    isStoragePermanentlyDenied = isPermanentlyDenied,
                    statusText = "Kuchu Puchu needs All Files Access to organize Downloads"
                )
            }
            voiceFeedbackManager.speak("Kuchu Puchu needs All Files Access to organize your Downloads.")
            return
        }

        val files = downloadsOrganizerManager.listTopLevelDownloadsFiles()
        if (files.isEmpty()) {
            val emptyMsg = "Downloads is already clean — nothing to organize."
            _uiState.update {
                it.copy(
                    isExecuting = false,
                    statusText = emptyMsg,
                    lastExecutionMessage = emptyMsg
                )
            }
            voiceFeedbackManager.speak(emptyMsg)
            viewModelScope.launch {
                historyRepository.recordCommand(
                    transcript = command.rawTranscript,
                    action = command.action.key,
                    target = "Downloads",
                    source = command.source.shortBadge,
                    outcome = "SUCCESS",
                    details = emptyMsg,
                    datetime = null,
                    attachmentBadge = attachmentBadge,
                    engineName = engineName,
                    routeType = routeType,
                    latencyMs = latencyMs,
                    confidence = confidence,
                    rawIntentJson = rawIntentJson,
                    attachmentUri = attachmentUri
                )
            }
            return
        }

        val mode = command.organizeMode ?: "default"
        if (mode == "default") {
            val plan = downloadsOrganizerManager.buildDefaultPlan()
            _uiState.update {
                it.copy(
                    isExecuting = false,
                    pendingOrganizePlan = plan,
                    statusText = "Review Downloads organization plan"
                )
            }
            voiceFeedbackManager.speak("I prepared a plan to organize ${plan.totalFilesToMove} files in Downloads.")
            return
        }

        if (!command.organizeRules.isNullOrEmpty()) {
            val plan = downloadsOrganizerManager.buildCustomPlan(command.organizeRules)
            _uiState.update {
                it.copy(
                    isExecuting = false,
                    pendingOrganizePlan = plan,
                    statusText = "Review custom organization plan"
                )
            }
            voiceFeedbackManager.speak("I prepared a plan to organize ${plan.totalFilesToMove} files in Downloads.")
            return
        }

        _uiState.update {
            it.copy(
                isExecuting = true,
                statusText = "Planning Downloads organization..."
            )
        }

        viewModelScope.launch(Dispatchers.Default) {
            val lang = _uiState.value.selectedLanguage
            val plannerResult = downloadsPlanner.planCustomRules(command.rawTranscript, files, lang)
            when (plannerResult) {
                is com.example.organizer.PlannerResult.EmptyDownloads -> {
                    val emptyMsg = "Downloads is already clean — nothing to organize."
                    _uiState.update {
                        it.copy(
                            isExecuting = false,
                            statusText = emptyMsg,
                            lastExecutionMessage = emptyMsg
                        )
                    }
                    voiceFeedbackManager.speak(emptyMsg)
                }
                is com.example.organizer.PlannerResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isExecuting = false,
                            statusText = plannerResult.message,
                            lastExecutionMessage = plannerResult.message
                        )
                    }
                    voiceFeedbackManager.speak(plannerResult.message)
                    historyRepository.recordCommand(
                        transcript = command.rawTranscript,
                        action = command.action.key,
                        target = "Downloads",
                        datetime = null,
                        source = command.source.shortBadge,
                        outcome = "FAILED",
                        details = plannerResult.message,
                        attachmentBadge = attachmentBadge,
                        engineName = engineName,
                        routeType = routeType,
                        latencyMs = latencyMs,
                        confidence = confidence,
                        rawIntentJson = rawIntentJson,
                        attachmentUri = attachmentUri
                    )
                }
                is com.example.organizer.PlannerResult.Success -> {
                    val plan = downloadsOrganizerManager.buildCustomPlan(plannerResult.rules)
                    _uiState.update {
                        it.copy(
                            isExecuting = false,
                            pendingOrganizePlan = plan,
                            statusText = "Review custom organization plan"
                        )
                    }
                    voiceFeedbackManager.speak("I prepared a custom plan to organize ${plan.totalFilesToMove} files.")
                }
            }
        }
    }

    fun toggleOrganizeRule(ruleId: String, isChecked: Boolean) {
        val currentPlan = _uiState.value.pendingOrganizePlan ?: return
        val updatedRules = currentPlan.rules.map { rule ->
            if (rule.id == ruleId && rule.isValid) rule.copy(isEnabled = isChecked) else rule
        }
        val activeFilesCount = updatedRules.filter { it.isEnabled && it.isValid }
            .sumOf { it.fileCount }
        val staying = currentPlan.totalFilesInDownloads - activeFilesCount

        _uiState.update {
            it.copy(
                pendingOrganizePlan = currentPlan.copy(
                    rules = updatedRules,
                    filesStayingInPlace = staying
                )
            )
        }
    }

    fun cancelOrganizePlan() {
        val plan = _uiState.value.pendingOrganizePlan ?: return
        _uiState.update {
            it.copy(
                pendingOrganizePlan = null,
                isExecuting = false,
                statusText = "Organization cancelled"
            )
        }
        viewModelScope.launch {
            historyRepository.recordCommand(
                transcript = "Organize Downloads",
                action = ActionType.ORGANIZE_DOWNLOADS.key,
                target = "Downloads",
                datetime = null,
                source = "ROUTER",
                outcome = "CANCELLED",
                details = "ORGANIZE: ${plan.totalFilesToMove} planned / 0 moved / 0 skipped (cancelled by user)"
            )
        }
    }

    fun executeOrganizePlan() {
        val plan = _uiState.value.pendingOrganizePlan ?: return
        if (!downloadsOrganizerManager.hasRequiredPermission()) {
            _uiState.update {
                it.copy(
                    pendingOrganizePlan = null,
                    showStoragePermissionExplanation = true,
                    statusText = "Permission revoked. Allow All Files Access in Settings."
                )
            }
            return
        }

        val totalToMove = plan.totalFilesToMove
        _uiState.update {
            it.copy(
                pendingOrganizePlan = null,
                organizeProgress = com.example.model.OrganizeProgressState(
                    planId = plan.id,
                    current = 0,
                    total = totalToMove
                ),
                statusText = "Moving files..."
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = downloadsOrganizerManager.executePlan(plan) { current, total, fileName ->
                _uiState.update {
                    it.copy(
                        organizeProgress = com.example.model.OrganizeProgressState(
                            planId = plan.id,
                            current = current,
                            total = total,
                            currentFileName = fileName
                        )
                    )
                }
            }

            withContext(Dispatchers.Main) {
                if (result.permissionDenied) {
                    _uiState.update {
                        it.copy(
                            organizeProgress = null,
                            showStoragePermissionExplanation = true,
                            statusText = "Permission revoked. Allow All Files Access in Settings."
                        )
                    }
                    return@withContext
                }

                _uiState.update {
                    it.copy(
                        organizeProgress = null,
                        organizeSummary = com.example.model.OrganizeSummaryState(
                            planId = result.planId,
                            plannedCount = result.plannedCount,
                            movedCount = result.movedCount,
                            skippedCount = result.skippedCount,
                            skippedDetails = result.skippedDetails,
                            wasCancelled = result.wasCancelled
                        ),
                        statusText = if (result.wasCancelled) "Batch stopped: moved ${result.movedCount} files"
                        else "Organized ${result.movedCount} files"
                    )
                }
                val skippedInfo = if (result.skippedDetails.isNotEmpty()) " (${result.skippedDetails.joinToString(", ")})" else ""
                val summaryDetails = "ORGANIZE: ${result.plannedCount} planned / ${result.movedCount} moved / ${result.skippedCount} skipped$skippedInfo"
                val speechFeedback = if (result.skippedCount > 0 && result.skippedDetails.isNotEmpty()) {
                    "Moved ${result.movedCount} files, ${result.skippedCount} skipped: ${result.skippedDetails.joinToString(", ")}."
                } else {
                    "Moved ${result.movedCount} files."
                }
                voiceFeedbackManager.speak(speechFeedback)
                historyRepository.recordCommand(
                    transcript = "Organize Downloads",
                    action = ActionType.ORGANIZE_DOWNLOADS.key,
                    target = "Downloads",
                    datetime = null,
                    source = "ROUTER",
                    outcome = if (result.movedCount > 0) "SUCCESS" else "FAILED",
                    details = summaryDetails
                )
            }
        }
    }

    fun cancelOrganizeExecution() {
        downloadsOrganizerManager.requestCancel()
    }

    fun undoOrganizePlan(planId: String) {
        val summary = _uiState.value.organizeSummary ?: return
        _uiState.update {
            it.copy(
                organizeSummary = summary.copy(isUndoing = true),
                statusText = "Undoing file moves..."
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val undoResult = downloadsOrganizerManager.undoPlan(planId)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        organizeSummary = summary.copy(
                            isUndoing = false,
                            isUndone = true,
                            undoSummary = "Restored ${undoResult.restoredCount} files to Downloads" +
                                if (undoResult.failedCount > 0) " (${undoResult.failedCount} failed)" else ""
                        ),
                        statusText = "Restored ${undoResult.restoredCount} files"
                    )
                }
                voiceFeedbackManager.speak("Restored ${undoResult.restoredCount} files back to Downloads.")
                historyRepository.recordCommand(
                    transcript = "Undo Downloads organization",
                    action = ActionType.ORGANIZE_DOWNLOADS.key,
                    target = "Downloads",
                    datetime = null,
                    source = "ROUTER",
                    outcome = "SUCCESS",
                    details = "ORGANIZE UNDO: ${undoResult.restoredCount} restored / ${undoResult.failedCount} failed"
                )
            }
        }
    }

    fun dismissOrganizeSummary() {
        _uiState.update { it.copy(organizeSummary = null) }
    }

    fun dismissStoragePermissionPrompt() {
        _uiState.update { it.copy(showStoragePermissionExplanation = false) }
    }

    fun onResumeCheckStoragePermission() {
        val hasPermission = downloadsOrganizerManager.hasRequiredPermission()
        if (hasPermission) {
            preferences.isStoragePermanentlyDenied = false
            _uiState.update {
                it.copy(
                    showStoragePermissionExplanation = false,
                    isStoragePermanentlyDenied = false
                )
            }
            val pendingCmd = pendingOrganizeCommand
            if (pendingCmd != null) {
                pendingOrganizeCommand = null
                viewModelScope.launch {
                    executeCommand(pendingCmd, isConfirmed = false)
                }
            }
        } else if (_uiState.value.showStoragePermissionExplanation) {
            preferences.isStoragePermanentlyDenied = true
            _uiState.update {
                it.copy(
                    isStoragePermanentlyDenied = true
                )
            }
        }
    }

    fun confirmPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.update {
            it.copy(
                pendingConfirmation = null,
                isExecuting = true,
                statusText = "Executing confirmed action..."
            )
        }

        viewModelScope.launch(Dispatchers.Default) {
            executeCommand(
                command = pending.command,
                isConfirmed = true,
                attachmentBadge = pending.attachmentBadge,
                engineName = pending.engineName,
                routeType = pending.routeType,
                latencyMs = pending.latencyMs,
                confidence = pending.confidence,
                rawIntentJson = pending.rawIntentJson,
                attachmentUri = pending.attachmentUri
            )
        }
    }

    fun cancelPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.update {
            it.copy(
                pendingConfirmation = null,
                isExecuting = false,
                statusText = "Action cancelled by user"
            )
        }

        viewModelScope.launch {
            historyRepository.recordCommand(
                transcript = pending.command.rawTranscript,
                action = pending.command.action.key,
                target = pending.command.target,
                datetime = pending.command.datetime,
                source = pending.command.source.shortBadge,
                outcome = "CANCELLED",
                details = "User cancelled confirmation card",
                attachmentBadge = pending.attachmentBadge,
                engineName = pending.engineName,
                routeType = pending.routeType,
                latencyMs = pending.latencyMs,
                confidence = pending.confidence,
                rawIntentJson = pending.rawIntentJson,
                attachmentUri = pending.attachmentUri
            )
        }
    }

    private suspend fun handleExecutionResult(
        command: ActionCommand,
        result: ExecutionResult,
        attachmentBadge: String? = null,
        engineName: String? = null,
        routeType: String? = null,
        latencyMs: Long? = null,
        confidence: Float? = null,
        rawIntentJson: String? = null,
        attachmentUri: String? = null
    ) {
        when (result) {
            is ExecutionResult.RequiresConfirmation -> {
                _uiState.update {
                    it.copy(
                        isExecuting = false,
                        statusText = "Confirmation required before execution",
                        pendingConfirmation = PendingConfirmationState(
                            command = command,
                            targetResolved = result.targetResolved,
                            details = result.details,
                            localizedConfirmationText = command.confirmationText,
                            attachmentBadge = attachmentBadge,
                            engineName = engineName,
                            routeType = routeType,
                            latencyMs = latencyMs,
                            confidence = confidence,
                            rawIntentJson = rawIntentJson,
                            attachmentUri = attachmentUri
                        )
                    )
                }
            }
            is ExecutionResult.Success -> {
                voiceFeedbackManager.speakSuccess(command.action, command.target, result.message)
                _uiState.update {
                    it.copy(
                        isExecuting = false,
                        statusText = result.message,
                        lastExecutionMessage = result.message,
                        pendingConfirmation = null
                    )
                }
                historyRepository.recordCommand(
                    transcript = command.rawTranscript,
                    action = command.action.key,
                    target = command.target,
                    datetime = command.datetime,
                    source = command.source.shortBadge,
                    outcome = "SUCCESS",
                    details = result.details ?: result.message,
                    attachmentBadge = attachmentBadge,
                    engineName = engineName,
                    routeType = routeType,
                    latencyMs = latencyMs,
                    confidence = confidence,
                    rawIntentJson = rawIntentJson,
                    attachmentUri = attachmentUri
                )
            }
            is ExecutionResult.MissingPermission -> {
                voiceFeedbackManager.speak("Permission required to perform that action")
                _uiState.update {
                    it.copy(
                        isExecuting = false,
                        statusText = "Permission required: ${result.permission.substringAfterLast('.')}",
                        pendingPermissionRequest = PermissionRequestState(
                            permission = result.permission,
                            title = "Permission Required",
                            explanation = result.explanation
                        )
                    )
                }
            }
            is ExecutionResult.Error -> {
                voiceFeedbackManager.speakError(result.error)
                _uiState.update {
                    it.copy(
                        isExecuting = false,
                        statusText = result.error,
                        lastExecutionMessage = result.error
                    )
                }
                historyRepository.recordCommand(
                    transcript = command.rawTranscript,
                    action = command.action.key,
                    target = command.target,
                    datetime = command.datetime,
                    source = command.source.shortBadge,
                    outcome = "ERROR",
                    details = result.error,
                    attachmentBadge = attachmentBadge,
                    engineName = engineName,
                    routeType = routeType,
                    latencyMs = latencyMs,
                    confidence = confidence,
                    rawIntentJson = rawIntentJson,
                    attachmentUri = attachmentUri
                )
            }
        }
    }

    fun retryCommand(item: CommandHistoryEntity) {
        val cleanTranscript = if (item.transcript.startsWith("VOICE: \"")) {
            item.transcript.substringAfter("VOICE: \"").substringBeforeLast("\" (")
        } else {
            item.transcript
        }

        val attachments = if (!item.attachmentUri.isNullOrBlank()) {
            val isPdf = item.attachmentBadge?.contains("PDF", ignoreCase = true) == true
            listOf(
                com.example.model.AttachmentItem(
                    id = "retry_att_${System.currentTimeMillis()}",
                    uri = android.net.Uri.parse(item.attachmentUri),
                    name = if (isPdf) "Document.pdf" else "Image.jpg",
                    mime = if (isPdf) "application/pdf" else "image/jpeg",
                    size = 0L,
                    kind = if (isPdf) com.example.model.AttachmentKind.PDF else com.example.model.AttachmentKind.IMAGE
                )
            )
        } else {
            emptyList()
        }

        // Action-type commands will route to executeCommand(..., isConfirmed = false)
        // which always shows confirmation card (RequiresConfirmation) for sensitive actions like CALL/CREATE_EVENT.
        // Answer-type commands re-send to engine. Never auto-executes on retry.
        processTranscript(
            transcript = cleanTranscript,
            isVoice = false,
            confidence = item.confidence,
            attachmentsOverride = attachments
        )
    }

    fun dismissImageUnsupportedRecovery() {
        _uiState.update { it.copy(imageUnsupportedRecovery = null) }
    }

    fun openVisionModelSwitchSheet() {
        _uiState.update {
            val current = it.imageUnsupportedRecovery
            if (current != null) {
                it.copy(imageUnsupportedRecovery = current.copy(showSwitchSheet = true))
            } else {
                it
            }
        }
    }

    fun dismissVisionModelSwitchSheet() {
        _uiState.update {
            val current = it.imageUnsupportedRecovery
            if (current != null) {
                it.copy(imageUnsupportedRecovery = current.copy(showSwitchSheet = false))
            } else {
                it
            }
        }
    }

    fun removeAttachmentsAndDismissRecovery() {
        _uiState.update {
            it.copy(
                attachments = emptyList(),
                imageUnsupportedRecovery = null,
                statusText = "Attachments removed. Ready for your command."
            )
        }
    }

    fun selectVisionModel(model: com.example.model.ModelCapability) {
        val currentRecovery = _uiState.value.imageUnsupportedRecovery
        val pendingInput = currentRecovery?.pendingInput ?: ""
        val pendingAttachments = currentRecovery?.pendingAttachments ?: emptyList()

        if (model.engineType == com.example.model.ModelEngineType.ONLINE_PROVIDER) {
            preferences.activeEngine = "ONLINE"
            preferences.onlineProvider = model.id
            _uiState.update {
                it.copy(
                    activeEngine = "ONLINE",
                    onlineProvider = model.id,
                    imageUnsupportedRecovery = null,
                    statusText = "Switched to ${model.displayName}"
                )
            }
        } else {
            preferences.activeEngine = "OFFLINE"
            preferences.activeModelId = model.id
            _uiState.update {
                it.copy(
                    activeEngine = "OFFLINE",
                    activeModelId = model.id,
                    imageUnsupportedRecovery = null,
                    statusText = "Switched to ${model.displayName}"
                )
            }
        }

        // Auto re-send original input + attachments (one tap total)
        if (pendingInput.isNotBlank() || pendingAttachments.isNotEmpty()) {
            processTranscript(
                transcript = pendingInput,
                isVoice = false,
                confidence = null,
                attachmentsOverride = pendingAttachments
            )
        }
    }

    fun dismissPermissionDialog() {
        _uiState.update { it.copy(pendingPermissionRequest = null) }
    }

    // Settings actions
    fun setEngine(engine: String) {
        preferences.activeEngine = engine
        preferences.engineMode = if (engine == "ONLINE") "ONLINE_ONLY" else "OFFLINE_ONLY"
        _uiState.update { it.copy(activeEngine = engine, engineMode = preferences.engineMode) }
    }

    fun setOnlineProvider(provider: String) {
        preferences.onlineProvider = provider
        _uiState.update { it.copy(onlineProvider = provider) }
    }

    fun setGeminiKey(key: String) {
        preferences.geminiApiKey = key
        _uiState.update { it.copy(geminiKey = key) }
    }

    fun setOpenaiKey(key: String) {
        preferences.openaiApiKey = key
        _uiState.update { it.copy(openaiKey = key) }
    }

    fun setAnthropicKey(key: String) {
        preferences.anthropicApiKey = key
        _uiState.update { it.copy(anthropicKey = key) }
    }

    fun setHuggingFaceToken(token: String) {
        preferences.huggingFaceToken = token.trim()
        _uiState.update { it.copy(huggingFaceToken = token.trim()) }
    }

    fun setGeminiModel(model: String) {
        preferences.geminiModel = model
        _uiState.update { it.copy(geminiModel = model) }
    }

    fun setOpenaiModel(model: String) {
        preferences.openaiModel = model
        _uiState.update { it.copy(openaiModel = model) }
    }

    fun setAnthropicModel(model: String) {
        preferences.anthropicModel = model
        _uiState.update { it.copy(anthropicModel = model) }
    }

    fun setAutoCall(enabled: Boolean) {
        preferences.isAutoCallEnabled = enabled
        _uiState.update { it.copy(isAutoCallEnabled = enabled) }
    }

    fun setFastRouter(enabled: Boolean) {
        preferences.isFastRouterEnabled = enabled
        _uiState.update { it.copy(isFastRouterEnabled = enabled) }
    }

    fun toggleOfflineSpeech(enabled: Boolean) {
        preferences.isOfflineSpeechEnabled = enabled
        speechRecognizerManager.preferOfflineSpeech = enabled
        _uiState.update { it.copy(isOfflineSpeechEnabled = enabled) }
        if (enabled) {
            val currentLang = _uiState.value.selectedLanguage
            val targetLangCode = if (currentLang == "SYSTEM") "en" else currentLang.lowercase()
            val sttModel = modelManager.modelsState.value.firstOrNull {
                it.category == com.example.model.ModelCategory.STT_LANGUAGE_PACK &&
                (it.languageCode.equals(targetLangCode, ignoreCase = true) ||
                 it.id == "stt_$targetLangCode" ||
                 (targetLangCode.startsWith("zh") && it.id.contains("zh")))
            } ?: modelManager.modelsState.value.firstOrNull {
                it.category == com.example.model.ModelCategory.STT_LANGUAGE_PACK && it.id == "stt_en"
            }
            if (sttModel != null && !sttModel.isDownloaded && !sttModel.isDownloading) {
                modelManager.startDownload(sttModel.id)
            }
        }
    }

    fun setSelectedLanguage(languageCode: String) {
        preferences.selectedLanguage = languageCode
        preferences.hasCompletedOnboarding = true
        speechRecognizerManager.currentLanguageCode = languageCode
        _uiState.update { it.copy(selectedLanguage = languageCode, showOnboarding = false) }
    }

    fun setThemeMode(mode: String) {
        val validMode = when (mode.uppercase()) {
            "LIGHT" -> "LIGHT"
            "DARK" -> "DARK"
            else -> "SYSTEM"
        }
        preferences.themeMode = validMode
        _uiState.update { it.copy(themeMode = validMode) }
    }

    fun toggleThemeMode(isCurrentDark: Boolean) {
        val nextMode = if (isCurrentDark) "LIGHT" else "DARK"
        setThemeMode(nextMode)
    }

    fun setHapticsEnabled(enabled: Boolean) {
        preferences.isHapticsEnabled = enabled
        _uiState.update { it.copy(isHapticsEnabled = enabled) }
    }

    fun setStreamingAnimationEnabled(enabled: Boolean) {
        preferences.isStreamingAnimationEnabled = enabled
        _uiState.update { it.copy(isStreamingAnimationEnabled = enabled) }
    }

    fun setHighFidelityAnimationsEnabled(enabled: Boolean) {
        preferences.isHighFidelityAnimationsEnabled = enabled
        _uiState.update { it.copy(isHighFidelityAnimationsEnabled = enabled) }
    }

    fun setImageGenAspectRatio(ratio: String) {
        preferences.imageGenAspectRatio = ratio
        _uiState.update { it.copy(imageGenAspectRatio = ratio) }
    }

    fun setAutoCopyEnabled(enabled: Boolean) {
        preferences.isAutoCopyEnabled = enabled
        _uiState.update { it.copy(isAutoCopyEnabled = enabled) }
    }

    fun setActiveModel(modelId: String) {
        preferences.activeModelId = modelId
        _uiState.update { it.copy(activeModelId = modelId) }
        modelManager.refreshModelStatuses(modelId)
    }

    fun startModelDownload(modelId: String) {
        modelManager.startDownload(modelId)
    }

    fun cancelModelDownload(modelId: String) {
        modelManager.cancelDownload(modelId)
    }

    fun deleteModel(modelId: String) {
        modelManager.deleteModel(modelId)
        refreshStorageInfo()
    }

    fun importModel(uri: android.net.Uri, customName: String? = null) {
        modelManager.importModelFromUri(uri, customName) { success, error ->
            refreshStorageInfo()
            if (!success && error != null) {
                _uiState.update { it.copy(statusText = "Import error: $error") }
            }
        }
    }

    fun importModelForSlot(slotId: String, uri: android.net.Uri) {
        modelManager.importModelForSlot(slotId, uri) { success, error ->
            refreshStorageInfo()
            if (!success && error != null) {
                _uiState.update { it.copy(statusText = "Import error: $error") }
            }
        }
    }

    fun getModelDownloadInfo(modelId: String) = modelManager.getModelDownloadInfo(modelId)

    fun downloadCustomModel(url: String, customName: String) {
        modelManager.downloadFromCustomUrl(url, customName)
    }

    fun verifyModel(modelId: String) {
        modelManager.verifyModel(modelId)
    }

    fun scanAndRestoreModelsFromStorage() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusText = "Scanning device storage for saved AI models...") }
            val restored = modelManager.scanAndAutoRestoreFromStorage()
            refreshStorageInfo()
            if (restored) {
                val active = modelManager.getFirstDownloadedModel()
                if (active != null) {
                    preferences.activeModelId = active.id
                    preferences.activeEngine = "OFFLINE"
                    _uiState.update {
                        it.copy(
                            activeEngine = "OFFLINE",
                            activeModelId = active.id,
                            statusText = "Restored ${active.name} from device storage!"
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(statusText = "No saved model files found in phone storage.") }
            }
        }
    }

    fun clearHistory() {
        clearConversation()
    }

    private fun refreshStorageInfo() {
        val (available, used) = modelManager.getDeviceStorageInfo()
        _uiState.update {
            it.copy(
                availableStorageBytes = available,
                usedStorageBytes = used
            )
        }
    }

    fun toggleVoiceFeedback(enabled: Boolean) {
        preferences.isVoiceFeedbackEnabled = enabled
        _uiState.update { it.copy(isVoiceFeedbackEnabled = enabled) }
    }

    fun setSelectedTtsVoice(voiceId: String) {
        preferences.selectedTtsVoice = voiceId
        _uiState.update { it.copy(selectedTtsVoice = voiceId) }
    }

    fun setTtsSpeed(speed: Float) {
        preferences.ttsSpeed = speed
        _uiState.update { it.copy(ttsSpeed = speed) }
    }

    fun setTtsPitch(pitch: Float) {
        preferences.ttsPitch = pitch
        _uiState.update { it.copy(ttsPitch = pitch) }
    }

    fun previewTtsVoice(voiceId: String) {
        val voice = com.example.voice.TtsVoiceCatalog.getVoiceById(voiceId)
        _uiState.update { it.copy(previewingVoiceId = voiceId) }
        voiceFeedbackManager.previewVoice(voice) {
            _uiState.update { state ->
                if (state.previewingVoiceId == voiceId) state.copy(previewingVoiceId = null) else state
            }
        }
    }

    fun stopTtsVoicePreview() {
        voiceFeedbackManager.stop()
        _uiState.update { it.copy(previewingVoiceId = null) }
    }

    fun downloadTtsVoicePack(voiceId: String) {
        val voice = com.example.voice.TtsVoiceCatalog.getVoiceById(voiceId)
        _uiState.update {
            it.copy(
                isDownloadingVoice = true,
                downloadingVoiceId = voiceId,
                voiceDownloadProgress = 0f
            )
        }
        viewModelScope.launch {
            val result = voiceFeedbackManager.downloadVoicePack(voice) { progress ->
                _uiState.update { it.copy(voiceDownloadProgress = progress) }
            }
            if (result.isSuccess) {
                val updated = preferences.downloadedTtsVoices + voiceId
                preferences.downloadedTtsVoices = updated
                _uiState.update {
                    it.copy(
                        isDownloadingVoice = false,
                        downloadingVoiceId = null,
                        voiceDownloadProgress = 1f,
                        downloadedTtsVoices = updated
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isDownloadingVoice = false,
                        downloadingVoiceId = null,
                        voiceDownloadProgress = 0f
                    )
                }
            }
        }
    }

    fun deleteTtsVoicePack(voiceId: String) {
        voiceFeedbackManager.deleteVoicePack(voiceId)
        val updated = preferences.downloadedTtsVoices - voiceId
        preferences.downloadedTtsVoices = updated
        _uiState.update { it.copy(downloadedTtsVoices = updated) }
    }

    fun toggleSpeakText(text: String) {
        voiceFeedbackManager.toggleSpeakExplicit(text)
    }

    override fun onCleared() {
        super.onCleared()
        voiceFeedbackManager.shutdown()
        speechRecognizerManager.destroy()
    }
}
