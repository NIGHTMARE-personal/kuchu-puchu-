package com.example.llm

import com.example.data.local.SecurePreferences
import com.example.model.ActionCommand
import com.example.util.ProcessedMultimodalPayload

class OnlineLlmEngine(
    private val preferences: SecurePreferences,
    private val geminiAdapter: GeminiAdapter = GeminiAdapter(),
    private val openAiAdapter: OpenAiAdapter = OpenAiAdapter(),
    private val anthropicAdapter: AnthropicAdapter = AnthropicAdapter()
) {

    suspend fun processTranscript(
        transcript: String,
        selectedLanguage: String = "SYSTEM",
        payload: ProcessedMultimodalPayload = ProcessedMultimodalPayload(),
        chatHistory: List<com.example.model.ChatTurn> = emptyList(),
        onChunk: ((String) -> Unit)? = null
    ): ActionCommand {
        val provider = preferences.onlineProvider
        val apiKey = preferences.getActiveApiKey()

        return when (provider) {
            "GEMINI" -> {
                geminiAdapter.execute(
                    transcript,
                    apiKey,
                    selectedLanguage,
                    payload,
                    configuredModel = preferences.geminiModel,
                    chatHistory = chatHistory,
                    onChunk = onChunk
                )
            }
            "OPENAI" -> {
                openAiAdapter.execute(
                    transcript,
                    apiKey,
                    selectedLanguage,
                    payload,
                    configuredModel = preferences.openaiModel,
                    chatHistory = chatHistory,
                    onChunk = onChunk
                )
            }
            "ANTHROPIC" -> {
                anthropicAdapter.execute(
                    transcript,
                    apiKey,
                    selectedLanguage,
                    payload,
                    configuredModel = preferences.anthropicModel,
                    chatHistory = chatHistory,
                    onChunk = onChunk
                )
            }
            else -> {
                geminiAdapter.execute(
                    transcript,
                    apiKey,
                    selectedLanguage,
                    payload,
                    configuredModel = preferences.geminiModel,
                    chatHistory = chatHistory,
                    onChunk = onChunk
                )
            }
        }
    }
}
