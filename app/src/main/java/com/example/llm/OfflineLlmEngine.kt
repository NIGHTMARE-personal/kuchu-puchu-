package com.example.llm

import android.content.Context
import android.util.Log
import com.example.data.local.SecurePreferences
import com.example.data.modelmanager.OfflineModelManager
import com.example.model.ActionCommand
import com.example.model.ActionType
import com.example.model.CommandSource
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class OfflineLlmEngine(
    private val context: Context,
    private val preferences: SecurePreferences,
    private val modelManager: OfflineModelManager
) {
    private var cachedModelPath: String? = null
    private var llmInferenceInstance: LlmInference? = null

    @Synchronized
    private fun getOrCreateLlmInference(modelFile: File): LlmInference {
        val path = modelFile.absolutePath
        val existing = llmInferenceInstance
        if (existing != null && cachedModelPath == path) {
            return existing
        }

        try {
            existing?.close()
        } catch (ignored: Throwable) {}

        Log.i("OfflineLlmEngine", "Initializing real on-device model weights from: $path (size: ${modelFile.length() / (1024 * 1024)} MB)")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(512)
            .build()

        val newInstance = LlmInference.createFromOptions(context, options)
        llmInferenceInstance = newInstance
        cachedModelPath = path
        return newInstance
    }

    suspend fun processTranscript(
        transcript: String,
        selectedLanguage: String = "SYSTEM",
        chatHistory: List<com.example.model.ChatTurn> = emptyList(),
        onChunk: ((String) -> Unit)? = null
    ): ActionCommand = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        var activeModelId = preferences.activeModelId

        val effectiveModel = modelManager.getActiveOrAnyDownloadedModel(activeModelId)
        if (effectiveModel != null && effectiveModel.id != activeModelId) {
            activeModelId = effectiveModel.id
            preferences.activeModelId = activeModelId
        }

        val activeModelName = effectiveModel?.name ?: com.example.diagnostics.SystemTraceManager.getModelReadableName(activeModelId)
        com.example.diagnostics.SystemTraceManager.startExecutionTrace(context, activeModelId, transcript, modelManager)

        val modelFile = modelManager.getActiveOrAnyDownloadedFile(activeModelId)

        // Case 1: No valid real on-device model loaded (>50MB)
        if (modelFile == null || !modelFile.exists() || modelFile.length() < 50 * 1024 * 1024L) {
            com.example.diagnostics.SystemTraceManager.logWarn(
                "NO_LOCAL_WEIGHTS",
                "No validated on-device model weights loaded for '$activeModelName'."
            )

            // Check if user requested a direct device action (alarm, timer, open app, call)
            val action = tryParseDeviceAction(transcript)
            if (action != null) {
                if (onChunk != null) {
                    val reply = action.reply ?: action.target
                    for (c in reply.chunked(8)) {
                        onChunk(c)
                        kotlinx.coroutines.delay(10)
                    }
                }
                val duration = System.currentTimeMillis() - startTime
                com.example.diagnostics.SystemTraceManager.recordExecutionComplete(duration, "Device Action Parser", true)
                return@withContext action
            }

            // General query without loaded weights: check if download is active or start it
            val downloadingModel = modelManager.modelsState.value.firstOrNull { it.isDownloading }
            val failedModel = modelManager.modelsState.value.firstOrNull { it.verificationError != null }

            val noModelMessage = when {
                downloadingModel != null -> {
                    val percent = (downloadingModel.downloadProgress * 100).toInt()
                    val downloadedMB = downloadingModel.downloadedBytes / (1024 * 1024)
                    val totalMB = (downloadingModel.fileSizeBytes / (1024 * 1024)).coerceAtLeast(1)
                    "⏳ **${downloadingModel.name}** is downloading in the background:\n\n" +
                            "• Progress: **$percent%** ($downloadedMB MB / $totalMB MB)\n" +
                            "• File: `${downloadingModel.fileName}`\n\n" +
                            "Please wait a moment for the download to complete (~1.07GB). Once finished, offline AI will activate automatically!\n\n" +
                            "💡 Tip: Switch to **Online AI (Gemini)** in the top bar to get immediate answers right now without waiting."
                }
                failedModel != null && failedModel.verificationError != null -> {
                    "⚠️ Model download stopped:\n\n" +
                            "• Reason: ${failedModel.verificationError}\n\n" +
                            "Go to the **Models** tab to retry the download, or tap **Import** to pick a model file saved on your phone.\n\n" +
                            "💡 Tip: Switch to **Online AI (Gemini)** in the top bar for instant responses."
                }
                else -> {
                    // Automatically kick off TinyLlama download if not started
                    modelManager.startDownload("tinyllama_1b")
                    "⏳ No on-device model was loaded yet.\n\n" +
                            "Initiated background download of **TinyLlama 1.1B** (~1.07GB). You can track progress in the **Models** tab.\n\n" +
                            "💡 **To get an instant answer right now**: Switch to **Online AI (Gemini)** using the engine switch in the top bar!"
                }
            }

            if (onChunk != null) {
                for (chunk in noModelMessage.chunked(12)) {
                    onChunk(chunk)
                    kotlinx.coroutines.delay(12)
                }
            }

            val duration = System.currentTimeMillis() - startTime
            com.example.diagnostics.SystemTraceManager.recordExecutionComplete(duration, "Local Notice", true)
            return@withContext ActionCommand(
                action = ActionType.ANSWER,
                target = "No local model weights loaded",
                reply = noModelMessage,
                source = CommandSource.OFFLINE_LLM,
                rawTranscript = transcript
            )
        }

        // Case 2: Real on-device model weights are loaded! Run MediaPipe LlmInference
        val historyBuilder = StringBuilder()
        if (chatHistory.isNotEmpty()) {
            historyBuilder.append("\nRecent conversation context:\n")
            for (turn in chatHistory.takeLast(4)) {
                historyBuilder.append("User: ${turn.userPrompt}\nAssistant: ${turn.assistantReply}\n")
            }
        }

        val prompt = "${LlmPromptHelper.getSystemPrompt(selectedLanguage)}$historyBuilder\nUser instruction: $transcript\nStrict JSON:"

        try {
            val cores = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
            com.example.diagnostics.SystemTraceManager.recordMediaPipeAttempt(threads = cores)

            Log.i("OfflineLlmEngine", "Running real MediaPipe inference with model: ${modelFile.name}")
            val output = runMediaPipeInference(modelFile, prompt)

            if (output.isNotBlank()) {
                if (onChunk != null) {
                    for (chunk in output.chunked(6)) {
                        onChunk(chunk)
                        kotlinx.coroutines.delay(12)
                    }
                }
                val duration = System.currentTimeMillis() - startTime
                com.example.diagnostics.SystemTraceManager.recordExecutionComplete(duration, "$activeModelName (${modelFile.name})", true)
                ActionJsonParser.parse(output, CommandSource.OFFLINE_LLM, transcript)
            } else {
                throw RuntimeException("MediaPipe produced an empty output token sequence.")
            }
        } catch (e: Throwable) {
            Log.e("OfflineLlmEngine", "MediaPipe local model execution error: ${e.message}", e)
            com.example.diagnostics.SystemTraceManager.recordMediaPipeFailure(e, "On-device inference exception: ${e.message}")

            val errorMessage = "❌ Local LLM inference failed: ${e.message ?: e.javaClass.simpleName}.\n\n" +
                    "The model could not run on this device's memory or hardware. Try closing other apps or importing a 4-bit quantized model."

            if (onChunk != null) {
                for (chunk in errorMessage.chunked(8)) {
                    onChunk(chunk)
                    kotlinx.coroutines.delay(12)
                }
            }

            val duration = System.currentTimeMillis() - startTime
            com.example.diagnostics.SystemTraceManager.recordExecutionComplete(duration, "$activeModelName (Failed)", false)
            ActionCommand(
                action = ActionType.ANSWER,
                target = "Local inference error",
                reply = errorMessage,
                source = CommandSource.OFFLINE_LLM,
                rawTranscript = transcript
            )
        }
    }

    private fun runMediaPipeInference(modelFile: File, prompt: String): String {
        val inference = getOrCreateLlmInference(modelFile)
        val response = inference.generateResponse(prompt)
        return response.orEmpty()
    }

    /**
     * Honest Device Intent Parser: only dispatches real system intents (alarms, timers, apps).
     * Clearly marked as CommandSource.ROUTER so users know it's a device tool, not a fake LLM.
     */
    private fun tryParseDeviceAction(transcript: String): ActionCommand? {
        val lower = transcript.lowercase().trim()

        when {
            lower.contains("youtube") || lower.contains("video") || lower.contains("song") || lower.contains("play") -> {
                val target = lower.replace("on youtube", "")
                    .replace("search youtube for", "")
                    .replace("play", "")
                    .replace("watch", "")
                    .replace("youtube", "")
                    .trim()
                return ActionCommand(
                    action = ActionType.YOUTUBE_SEARCH,
                    target = target,
                    reply = "[Device Action] Searching YouTube for '$target'",
                    source = CommandSource.ROUTER,
                    rawTranscript = transcript
                )
            }
            lower.contains("call") || lower.contains("dial") || lower.contains("ring") || lower.contains("phone") -> {
                val target = lower.replace("call", "").replace("dial", "").replace("phone", "").trim()
                return ActionCommand(
                    action = ActionType.CALL,
                    target = target,
                    confirmationText = "Call $target?",
                    reply = "[Device Action] Calling $target",
                    source = CommandSource.ROUTER,
                    rawTranscript = transcript
                )
            }
            lower.contains("open") || lower.contains("launch") || lower.contains("start") -> {
                val target = lower.replace("open app", "").replace("open an app", "").replace("open the app", "")
                    .replace("open", "").replace("launch", "").replace("start", "").trim()
                if (target.isNotBlank() && target !in setOf("something", "anything", "app", "an app", "the app", "whatever")) {
                    return ActionCommand(
                        action = ActionType.OPEN_APP,
                        target = target,
                        reply = "[Device Action] Opening $target",
                        source = CommandSource.ROUTER,
                        rawTranscript = transcript
                    )
                }
            }
            lower.contains("timer") -> {
                val (seconds, label) = com.example.util.TimerParserHelper.extractDurationAndLabel(
                    lower.replace("set a timer for", "")
                        .replace("set timer for", "")
                        .replace("timer for", "")
                        .replace("timer", "")
                        .trim()
                )
                return ActionCommand(
                    action = ActionType.SET_TIMER,
                    target = "Timer: ${seconds}s",
                    durationSeconds = seconds,
                    label = label,
                    reply = "[Device Action] Setting timer for ${seconds / 60} minutes",
                    source = CommandSource.ROUTER,
                    rawTranscript = transcript
                )
            }
            lower.contains("alarm") -> {
                return ActionCommand(
                    action = ActionType.OPEN_APP,
                    target = "Clock",
                    reply = "[Device Action] Opening Clock for alarm",
                    source = CommandSource.ROUTER,
                    rawTranscript = transcript
                )
            }
            lower.contains("organize") && lower.contains("download") -> {
                return ActionCommand(
                    action = ActionType.ORGANIZE_DOWNLOADS,
                    target = "Downloads",
                    organizeMode = "default",
                    confirmationText = "Organize files in Downloads?",
                    reply = "[Device Action] Organizing downloads folder",
                    source = CommandSource.ROUTER,
                    rawTranscript = transcript
                )
            }
        }
        return null
    }

    fun close() {
        try {
            llmInferenceInstance?.close()
            llmInferenceInstance = null
            cachedModelPath = null
        } catch (e: Exception) {
            Log.e("OfflineLlmEngine", "Error closing LlmInference", e)
        }
    }
}
