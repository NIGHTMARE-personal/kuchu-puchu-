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

class GeminiAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "GeminiAdapter"

        // Default candidate models ordered for speed and high availability
        val DEFAULT_CANDIDATES = listOf(
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-flash-latest",
            "gemini-3.7-flash",
            "gemini-3.8-flash",
            "gemini-3.1-pro-preview"
        )

        // Cache of resolved working model per API key to prevent unnecessary retries
        private val resolvedModelCache = ConcurrentHashMap<String, String>()

        fun getCachedModel(apiKey: String): String? = resolvedModelCache[apiKey]
        fun setCachedModel(apiKey: String, model: String) {
            resolvedModelCache[apiKey] = model
        }
    }

    /**
     * Dynamically queries the Google AI Generative Language models API to discover
     * available models supported by this specific API key.
     */
    private fun fetchAvailableModelsFromApi(apiKey: String): List<String> {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val modelsArray = json.optJSONArray("models") ?: return emptyList()
                val result = mutableListOf<String>()

                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.optJSONObject(i) ?: continue
                    val name = m.optString("name").removePrefix("models/")
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var supportsGenerate = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.optString(j) == "generateContent") {
                                supportsGenerate = true
                                break
                            }
                        }
                    }
                    if (supportsGenerate) {
                        result.add(name)
                    }
                }

                // Prioritize flash, newer version numbers, avoid image/tts previews for general reasoning
                result.sortedWith { a, b ->
                    fun score(s: String): Int {
                        var sc = 0
                        if (s.contains("flash")) sc += 1000
                        if (s.contains("pro")) sc += 500
                        if (s.contains("preview") || s.contains("tts") || s.contains("image")) sc -= 200
                        val match = Regex("""(\d+)(?:\.(\d+))?""").find(s)
                        if (match != null) {
                            val major = match.groupValues[1].toIntOrNull() ?: 0
                            val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                            sc += major * 50 + minor * 5
                        }
                        return sc
                    }
                    score(b).compareTo(score(a))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dynamically discover models for key", e)
            emptyList()
        }
    }

    /**
     * Resolves candidate models to try in order.
     */
    private fun resolveCandidateModels(apiKey: String, configuredModel: String?): List<String> {
        val candidates = mutableListOf<String>()
        val cleanConfigured = configuredModel?.trim()?.removePrefix("models/")
        if (!cleanConfigured.isNullOrBlank() && cleanConfigured != "auto") {
            candidates.add(cleanConfigured)
        }

        // Add cached resolved model if known
        val cached = resolvedModelCache[apiKey]
        if (!cached.isNullOrBlank() && !candidates.contains(cached)) {
            candidates.add(cached)
        }

        // Add default candidate cascade
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
            throw IllegalArgumentException("Gemini API key is not configured. Please enter your key in Settings.")
        }

        val hasAttachments = payload.images.isNotEmpty() || payload.textSnippets.isNotEmpty()
        val systemPrompt = LlmPromptHelper.getSystemPrompt(selectedLanguage, hasAttachments = hasAttachments)

        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray()

            // Rolling Chat Memory (last turns)
            for (turn in chatHistory) {
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", "User instruction: ${turn.userPrompt}") })
                    })
                })
                contentsArray.put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            val safeReply = turn.assistantReply.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                            put("text", "{\"action\": \"answer\", \"target\": \"Response\", \"reply\": \"$safeReply\"}")
                        })
                    })
                })
            }

            val partsArray = JSONArray()

            // 1. Add attached images as inlineData
            for (image in payload.images) {
                partsArray.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", image.mimeType)
                        put("data", image.base64Data)
                    })
                })
            }

            // 2. Add text attachments
            for (textSnippet in payload.textSnippets) {
                partsArray.put(JSONObject().apply {
                    put("text", "Attached document content:\n$textSnippet")
                })
            }

            // 3. User prompt
            val promptText = if (transcript.isBlank() && hasAttachments) {
                "Analyze the attached content and extract any action or describe what it contains."
            } else {
                "User instruction: $transcript"
            }
            partsArray.put(JSONObject().apply {
                put("text", promptText)
            })

            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", partsArray)
            })

            put("contents", contentsArray)

            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemPrompt)
                    })
                })
            })

            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 1000)
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val candidateQueue = resolveCandidateModels(apiKey, configuredModel).toMutableList()
        var lastErrorMessage = ""
        var dynamicDiscoveryAttempted = false

        while (candidateQueue.isNotEmpty()) {
            val currentModel = candidateQueue.removeAt(0)
            val useStreaming = onChunk != null
            val endpoint = if (useStreaming) "streamGenerateContent?alt=sse&key=$apiKey" else "generateContent?key=$apiKey"
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$currentModel:$endpoint"

            val request = Request.Builder()
                .url(url)
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

                        val isKeyInvalid = (response.code == 400 || response.code == 403) &&
                                (fullErr.contains("API_KEY_INVALID", ignoreCase = true) ||
                                 fullErr.contains("API key not valid", ignoreCase = true) ||
                                 fullErr.contains("forbidden", ignoreCase = true))

                        val shouldFallback = !isKeyInvalid && (
                            response.code in listOf(404, 429, 500, 502, 503, 504) ||
                            fullErr.contains("no longer available", ignoreCase = true) ||
                            fullErr.contains("not found", ignoreCase = true) ||
                            fullErr.contains("unknown model", ignoreCase = true) ||
                            fullErr.contains("demand", ignoreCase = true) ||
                            fullErr.contains("unavailable", ignoreCase = true) ||
                            candidateQueue.isNotEmpty()
                        )

                        if (shouldFallback && candidateQueue.isNotEmpty()) {
                            Log.w(TAG, "Model $currentModel failed with ${response.code} ($fullErr). Falling back to next model...")
                            if (resolvedModelCache[apiKey] == currentModel) {
                                resolvedModelCache.remove(apiKey)
                            }
                            if (!dynamicDiscoveryAttempted) {
                                dynamicDiscoveryAttempted = true
                                val discovered = fetchAvailableModelsFromApi(apiKey)
                                for (m in discovered) {
                                    if (!candidateQueue.contains(m) && m != currentModel) {
                                        candidateQueue.add(m)
                                    }
                                }
                            }
                            return@use // continue loop to next candidate
                        } else {
                            Log.e(TAG, "Gemini API error ($currentModel): ${response.code} -> $responseBody")
                            throw RuntimeException("Gemini API request failed (${response.code}): $fullErr")
                        }
                    }

                    var fullText = ""
                    if (useStreaming) {
                        val accumulated = StringBuilder()
                        val reader = java.io.BufferedReader(response.body!!.charStream())
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val l = line!!.trim()
                            if (l.startsWith("data: ")) {
                                val dataJson = l.substring(6).trim()
                                if (dataJson.isNotEmpty() && dataJson != "[DONE]") {
                                    try {
                                        val json = JSONObject(dataJson)
                                        val candidates = json.optJSONArray("candidates")
                                        if (candidates != null && candidates.length() > 0) {
                                            val first = candidates.getJSONObject(0)
                                            val content = first.optJSONObject("content")
                                            val parts = content?.optJSONArray("parts")
                                            if (parts != null && parts.length() > 0) {
                                                val partText = parts.getJSONObject(0).optString("text")
                                                if (partText.isNotEmpty()) {
                                                    accumulated.append(partText)
                                                    onChunk?.invoke(partText)
                                                }
                                            }
                                        }
                                    } catch (_: Exception) { }
                                }
                            }
                        }
                        fullText = accumulated.toString()
                    } else {
                        val responseBody = response.body?.string().orEmpty()
                        val json = JSONObject(responseBody)
                        val candidates = json.optJSONArray("candidates")
                        if (candidates == null || candidates.length() == 0) {
                            throw RuntimeException("No response candidates from Gemini ($currentModel)")
                        }
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        fullText = parts.getJSONObject(0).getString("text")
                    }

                    if (fullText.isBlank()) {
                        throw RuntimeException("Empty response received from Gemini ($currentModel)")
                    }

                    // Cache this working model
                    resolvedModelCache[apiKey] = currentModel
                    Log.i(TAG, "Successfully executed request using model: $currentModel")

                    val action = ActionJsonParser.parse(
                        fullText,
                        CommandSource.GEMINI,
                        transcript,
                        attachmentCount = payload.images.size + payload.textSnippets.size
                    )
                    return@withContext action.copy(engineName = "Gemini Cloud ($currentModel)")
                }
            } catch (e: Exception) {
                if (e is RuntimeException && e.message?.contains("Gemini API request failed") == true) {
                    throw e
                }
                Log.w(TAG, "Exception contacting $currentModel: ${e.message}. Trying next candidate...")
                lastErrorMessage = e.message ?: "Connection error"
            }
        }

        throw RuntimeException("Gemini API failed on all candidate models: $lastErrorMessage")
    }

    suspend fun cleanAndStructurePdfPages(
        pageImages: List<com.example.util.ProcessedImage>,
        apiKey: String,
        configuredModel: String = "auto"
    ): String = withContext(Dispatchers.IO) {
        if (pageImages.isEmpty()) return@withContext ""
        val effectiveKey = apiKey.trim()
        if (effectiveKey.isBlank()) {
            throw IllegalArgumentException("Gemini API key is required to analyze document")
        }
        val systemPrompt = "You are an expert document assistant. Review the provided rendered document pages and generate clean, structured Markdown notes with proper headings (#, ##), bullet points, bold key terms, and concise summaries suitable for a rich-text editor."

        val jsonBody = JSONObject().apply {
            val partsArray = JSONArray()
            for (img in pageImages) {
                partsArray.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", img.mimeType)
                        put("data", img.base64Data)
                    })
                })
            }
            partsArray.put(JSONObject().apply {
                put("text", "Extract, clean, and structure the content from these rendered document pages into rich Markdown.")
            })

            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", partsArray)
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemPrompt)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val candidateQueue = resolveCandidateModels(effectiveKey, configuredModel).toMutableList()

        for (currentModel in candidateQueue) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$currentModel:generateContent?key=$effectiveKey"
            val request = Request.Builder().url(url).post(requestBody).build()
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val candidates = json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val resultText = parts.getJSONObject(0).optString("text", "")
                            if (resultText.isNotBlank()) {
                                resolvedModelCache[effectiveKey] = currentModel
                                return@withContext resultText
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error structuring pages with $currentModel", e)
            }
        }
        "Document imported (${pageImages.size} pages rendered)."
    }

    suspend fun cleanAndStructurePdfText(
        rawText: String,
        apiKey: String,
        configuredModel: String = "auto"
    ): String = withContext(Dispatchers.IO) {
        val effectiveKey = apiKey.trim()
        if (effectiveKey.isBlank()) {
            throw IllegalArgumentException("Gemini API key is required to analyze document")
        }
        val systemPrompt = "You are an expert document assistant. Clean, structure, and format the following extracted document text into clear, readable Markdown with proper headings (#, ##), bullet points, bold key terms, and summaries suitable for a rich-text editor. Correct spacing, line-break artifacts, and typos while preserving all factual content."

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Clean and structure this extracted document content:\n\n$rawText")
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemPrompt)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val candidateQueue = resolveCandidateModels(effectiveKey, configuredModel).toMutableList()

        for (currentModel in candidateQueue) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$currentModel:generateContent?key=$effectiveKey"
            val request = Request.Builder().url(url).post(requestBody).build()
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val candidates = json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val resultText = parts.getJSONObject(0).optString("text", "")
                            if (resultText.isNotBlank()) {
                                resolvedModelCache[effectiveKey] = currentModel
                                return@withContext resultText
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error structuring PDF text with $currentModel", e)
            }
        }
        rawText
    }
}
