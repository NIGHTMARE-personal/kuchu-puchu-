package com.example.llm

import android.util.Log
import com.example.model.ActionCommand
import com.example.model.CommandSource
import com.example.util.ProcessedMultimodalPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AnthropicAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "AnthropicAdapter"

        val DEFAULT_CANDIDATES = listOf(
            "claude-3-5-haiku-latest",
            "claude-3-5-sonnet-latest",
            "claude-3-7-sonnet-latest",
            "claude-3-haiku-20240307",
            "claude-3-5-sonnet-20241022",
            "claude-3-sonnet-20240229"
        )

        private val resolvedModelCache = ConcurrentHashMap<String, String>()
    }

    private fun resolveCandidateModels(apiKey: String, configuredModel: String?): List<String> {
        val candidates = mutableListOf<String>()
        val clean = configuredModel?.trim()
        if (!clean.isNullOrBlank() && clean != "auto") {
            candidates.add(clean)
        }
        val cached = resolvedModelCache[apiKey]
        if (!cached.isNullOrBlank() && !candidates.contains(cached)) {
            candidates.add(cached)
        }
        for (m in DEFAULT_CANDIDATES) {
            if (!candidates.contains(m)) {
                candidates.add(m)
            }
        }
        return candidates
    }

    suspend fun execute(
        transcript: String,
        apiKey: String,
        selectedLanguage: String = "SYSTEM",
        payload: ProcessedMultimodalPayload = ProcessedMultimodalPayload(),
        configuredModel: String = "auto",
        chatHistory: List<com.example.model.ChatTurn> = emptyList(),
        onChunk: ((String) -> Unit)? = null
    ): ActionCommand = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Anthropic API key is not configured. Please enter your key in Settings.")
        }

        val hasAttachments = payload.images.isNotEmpty() || payload.textSnippets.isNotEmpty()
        val systemPrompt = LlmPromptHelper.getSystemPrompt(selectedLanguage, hasAttachments = hasAttachments)

        val candidateQueue = resolveCandidateModels(apiKey, configuredModel).toMutableList()
        var lastErrorMessage = ""

        while (candidateQueue.isNotEmpty()) {
            val currentModel = candidateQueue.removeAt(0)
            val useStreaming = onChunk != null

            val jsonBody = JSONObject().apply {
                put("model", currentModel)
                put("max_tokens", 1000)
                put("temperature", 0.1)
                put("system", systemPrompt)
                if (useStreaming) {
                    put("stream", true)
                }

                val messages = JSONArray()

                // Chat Memory rolling turns
                for (turn in chatHistory) {
                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put("content", turn.userPrompt)
                    })
                    messages.put(JSONObject().apply {
                        put("role", "assistant")
                        val safeReply = turn.assistantReply.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        put("content", "{\"action\": \"answer\", \"target\": \"Response\", \"reply\": \"$safeReply\"}")
                    })
                }

                val contentArray = JSONArray()

                // Images
                for (image in payload.images) {
                    contentArray.put(JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().apply {
                            put("type", "base64")
                            put("media_type", image.mimeType)
                            put("data", image.base64Data)
                        })
                    })
                }

                // Text
                val textBuilder = StringBuilder()
                for (snippet in payload.textSnippets) {
                    textBuilder.append("Attached document content:\n").append(snippet).append("\n\n")
                }
                val promptText = if (transcript.isBlank() && hasAttachments) {
                    "Analyze the attached content and extract any action or describe what it contains."
                } else {
                    transcript
                }
                textBuilder.append(promptText)

                contentArray.put(JSONObject().apply {
                    put("type", "text")
                    put("text", textBuilder.toString())
                })

                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentArray)
                })

                put("messages", messages)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val responseBody = response.body?.string().orEmpty()
                        val apiErrorMsg = try {
                            JSONObject(responseBody).optJSONObject("error")?.optString("message")
                        } catch (_: Exception) { null }
                        val fullErr = apiErrorMsg ?: "HTTP ${response.code}"
                        lastErrorMessage = fullErr

                        val isKeyInvalid = (response.code == 401 || response.code == 403) &&
                                (fullErr.contains("invalid_api_key", ignoreCase = true) ||
                                 fullErr.contains("authentication_error", ignoreCase = true) ||
                                 fullErr.contains("unauthorized", ignoreCase = true))

                        val shouldFallback = !isKeyInvalid && (
                            response.code in listOf(400, 404, 429, 500, 502, 503, 504, 529) ||
                            fullErr.contains("not_found_error", ignoreCase = true) ||
                            (fullErr.contains("model", ignoreCase = true) && fullErr.contains("not found", ignoreCase = true)) ||
                            fullErr.contains("overloaded", ignoreCase = true) ||
                            fullErr.contains("capacity", ignoreCase = true) ||
                            fullErr.contains("rate_limit", ignoreCase = true)
                        )

                        if (shouldFallback && candidateQueue.isNotEmpty()) {
                            Log.w(TAG, "Claude model $currentModel unavailable ($fullErr). Trying next candidate...")
                            if (resolvedModelCache[apiKey] == currentModel) {
                                resolvedModelCache.remove(apiKey)
                            }
                            return@use
                        } else {
                            Log.e(TAG, "Anthropic API error ($currentModel): ${response.code} -> $responseBody")
                            throw RuntimeException("Anthropic API request failed (${response.code}): $fullErr")
                        }
                    }

                    var text = ""
                    if (useStreaming) {
                        val accumulated = StringBuilder()
                        val reader = java.io.BufferedReader(response.body!!.charStream())
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val l = line!!.trim()
                            if (l.startsWith("data: ")) {
                                val data = l.substring(6).trim()
                                try {
                                    val obj = JSONObject(data)
                                    val type = obj.optString("type")
                                    if (type == "content_block_delta") {
                                        val deltaObj = obj.optJSONObject("delta")
                                        val deltaText = deltaObj?.optString("text")
                                        if (!deltaText.isNullOrEmpty()) {
                                            accumulated.append(deltaText)
                                            onChunk?.invoke(deltaText)
                                        }
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                        text = accumulated.toString()
                    } else {
                        val responseBody = response.body?.string().orEmpty()
                        val json = JSONObject(responseBody)
                        val contentJsonArray = json.getJSONArray("content")
                        if (contentJsonArray.length() == 0) {
                            throw RuntimeException("No response content from Anthropic ($currentModel)")
                        }
                        val firstBlock = contentJsonArray.getJSONObject(0)
                        text = firstBlock.getString("text")
                    }

                    resolvedModelCache[apiKey] = currentModel
                    Log.i(TAG, "Successfully executed Claude request with model: $currentModel")

                    val action = ActionJsonParser.parse(
                        text,
                        CommandSource.ANTHROPIC,
                        transcript,
                        attachmentCount = payload.images.size + payload.textSnippets.size
                    )
                    return@withContext action.copy(engineName = "Claude Cloud ($currentModel)")
                }
            } catch (e: Exception) {
                if (e is RuntimeException && e.message?.contains("Anthropic API request failed") == true) {
                    throw e
                }
                Log.w(TAG, "Exception contacting Claude $currentModel: ${e.message}")
                lastErrorMessage = e.message ?: "Connection error"
            }
        }

        throw RuntimeException("Anthropic API failed on all candidate models: $lastErrorMessage")
    }
}
