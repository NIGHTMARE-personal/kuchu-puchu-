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

class OpenAiAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "OpenAiAdapter"

        val DEFAULT_CANDIDATES = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "chatgpt-4o-latest",
            "gpt-4.1-mini",
            "gpt-4-turbo"
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
            throw IllegalArgumentException("OpenAI API key is not configured. Please enter your key in Settings.")
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
                put("temperature", 0.1)
                put("max_tokens", 1000)
                if (useStreaming) {
                    put("stream", true)
                } else {
                    put("response_format", JSONObject().apply {
                        put("type", "json_object")
                    })
                }

                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })

                    // Chat Memory rolling turns
                    for (turn in chatHistory) {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", turn.userPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "assistant")
                            val safeReply = turn.assistantReply.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                            put("content", "{\"action\": \"answer\", \"target\": \"Response\", \"reply\": \"$safeReply\"}")
                        })
                    }

                    val userContentArray = JSONArray()

                    // Text attachments + prompt
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

                    userContentArray.put(JSONObject().apply {
                        put("type", "text")
                        put("text", textBuilder.toString())
                    })

                    // Images
                    for (image in payload.images) {
                        userContentArray.put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:${image.mimeType};base64,${image.base64Data}")
                            })
                        })
                    }

                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userContentArray)
                    })
                }
                put("messages", messages)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
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
                                 fullErr.contains("Incorrect API key", ignoreCase = true) ||
                                 fullErr.contains("unauthorized", ignoreCase = true))

                        val shouldFallback = !isKeyInvalid && (
                            response.code in listOf(400, 404, 429, 500, 502, 503, 504) ||
                            fullErr.contains("model_not_found", ignoreCase = true) ||
                            fullErr.contains("does not exist", ignoreCase = true) ||
                            fullErr.contains("not supported", ignoreCase = true) ||
                            fullErr.contains("response_format", ignoreCase = true) ||
                            fullErr.contains("rate limit", ignoreCase = true) ||
                            fullErr.contains("overloaded", ignoreCase = true)
                        )

                        if (shouldFallback && candidateQueue.isNotEmpty()) {
                            Log.w(TAG, "OpenAI model $currentModel unavailable ($fullErr). Trying next candidate...")
                            if (resolvedModelCache[apiKey] == currentModel) {
                                resolvedModelCache.remove(apiKey)
                            }
                            return@use
                        } else {
                            Log.e(TAG, "OpenAI API error ($currentModel): ${response.code} -> $responseBody")
                            throw RuntimeException("OpenAI API request failed (${response.code}): $fullErr")
                        }
                    }

                    var content = ""
                    if (useStreaming) {
                        val accumulated = StringBuilder()
                        val reader = java.io.BufferedReader(response.body!!.charStream())
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val l = line!!.trim()
                            if (l.startsWith("data: ")) {
                                val data = l.substring(6).trim()
                                if (data == "[DONE]") break
                                try {
                                    val obj = JSONObject(data)
                                    val choices = obj.optJSONArray("choices")
                                    if (choices != null && choices.length() > 0) {
                                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                                        val deltaContent = delta?.optString("content")
                                        if (!deltaContent.isNullOrEmpty()) {
                                            accumulated.append(deltaContent)
                                            onChunk?.invoke(deltaContent)
                                        }
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                        content = accumulated.toString()
                    } else {
                        val responseBody = response.body?.string().orEmpty()
                        val json = JSONObject(responseBody)
                        val choices = json.getJSONArray("choices")
                        if (choices.length() == 0) {
                            throw RuntimeException("No completion choices from OpenAI ($currentModel)")
                        }
                        val firstChoice = choices.getJSONObject(0)
                        val messageObj = firstChoice.getJSONObject("message")
                        content = messageObj.getString("content")
                    }

                    resolvedModelCache[apiKey] = currentModel
                    Log.i(TAG, "Successfully executed OpenAI request with model: $currentModel")

                    val action = ActionJsonParser.parse(
                        content,
                        CommandSource.OPENAI,
                        transcript,
                        attachmentCount = payload.images.size + payload.textSnippets.size
                    )
                    return@withContext action.copy(engineName = "OpenAI Cloud ($currentModel)")
                }
            } catch (e: Exception) {
                if (e is RuntimeException && e.message?.contains("OpenAI API request failed") == true) {
                    throw e
                }
                Log.w(TAG, "Exception contacting OpenAI $currentModel: ${e.message}")
                lastErrorMessage = e.message ?: "Connection error"
            }
        }

        throw RuntimeException("OpenAI API failed on all candidate models: $lastErrorMessage")
    }
}
