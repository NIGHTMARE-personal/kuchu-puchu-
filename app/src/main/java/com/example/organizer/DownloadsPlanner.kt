package com.example.organizer

import android.util.Log
import com.example.llm.OnlineLlmEngine
import com.example.model.OrganizeRule
import org.json.JSONObject
import java.io.File
import java.util.Locale

sealed class PlannerResult {
    data class Success(val rules: List<OrganizeRule>) : PlannerResult()
    data class Failure(val message: String) : PlannerResult()
    object EmptyDownloads : PlannerResult()
}

class DownloadsPlanner(
    private val onlineLlmEngine: OnlineLlmEngine
) {
    companion object {
        private const val TAG = "DownloadsPlanner"
        const val MAX_FILE_ENTRIES = 200
        const val SYSTEM_PROMPT = """You are a file organization planner. Input: the user's request and a list of file names. Output ONLY JSON:
{"rules": [{"extensions": [".pdf"], "destination": "Documents/Invoices"}]}
Max 8 rules. Destinations must be relative subfolders under Pictures/, Documents/, Music/, Movies/, or Downloads/<name>/. Never delete.
Extensions not covered by the request must not appear in rules."""
    }

    /**
     * Plans custom organization rules using the online LLM engine.
     * File contents NEVER leave the device; only filenames and formatted sizes are provided.
     */
    suspend fun planCustomRules(
        userRequest: String,
        files: List<File>,
        selectedLanguage: String = "SYSTEM"
    ): PlannerResult {
        if (files.isEmpty()) {
            return PlannerResult.EmptyDownloads
        }

        val totalFiles = files.size
        val cappedFiles = files.take(MAX_FILE_ENTRIES)

        val fileListString = buildString {
            append("Available files in Downloads:\n")
            for (f in cappedFiles) {
                append("- ${f.name} (${formatFileSize(f.length())})\n")
            }
            if (totalFiles > MAX_FILE_ENTRIES) {
                append("[Truncation note: only first $MAX_FILE_ENTRIES of $totalFiles files listed]\n")
            }
        }

        val primaryPrompt = """
$SYSTEM_PROMPT

User Request: "$userRequest"

$fileListString

Output JSON only:
""".trimIndent()

        // First attempt
        var parsedRules = tryQueryAndParse(primaryPrompt, selectedLanguage)

        // If first attempt failed, retry ONCE
        if (parsedRules == null) {
            Log.w(TAG, "First planner output invalid or unparseable. Retrying once...")
            val retryPrompt = """
$SYSTEM_PROMPT

IMPORTANT: Your previous output was not valid JSON. Return ONLY JSON matching:
{"rules": [{"extensions": [".ext"], "destination": "Folder/Subfolder"}]}

User Request: "$userRequest"
$fileListString
""".trimIndent()
            parsedRules = tryQueryAndParse(retryPrompt, selectedLanguage)
        }

        return if (parsedRules != null && parsedRules.isNotEmpty()) {
            PlannerResult.Success(parsedRules)
        } else {
            PlannerResult.Failure("Couldn't build a custom plan — try 'organize my downloads' for the standard cleanup.")
        }
    }

    private suspend fun tryQueryAndParse(prompt: String, selectedLanguage: String): List<OrganizeRule>? {
        return try {
            val response = onlineLlmEngine.processTranscript(prompt, selectedLanguage)
            val rawReply = response.reply ?: response.target
            parseRulesJson(rawReply)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying online engine for planner", e)
            null
        }
    }

    fun parseRulesJson(rawOutput: String): List<OrganizeRule>? {
        try {
            val cleaned = cleanJsonString(rawOutput)
            val root = JSONObject(cleaned)
            val rulesArray = root.optJSONArray("rules") ?: return null

            val result = mutableListOf<OrganizeRule>()
            for (i in 0 until rulesArray.length()) {
                val obj = rulesArray.optJSONObject(i) ?: continue
                val destination = obj.optString("destination", "").trim()
                val extArray = obj.optJSONArray("extensions")
                val exts = mutableListOf<String>()
                if (extArray != null) {
                    for (j in 0 until extArray.length()) {
                        val ext = extArray.optString(j, "").trim().lowercase(Locale.ROOT)
                        if (ext.isNotBlank()) {
                            exts.add(if (ext.startsWith(".")) ext else ".$ext")
                        }
                    }
                }
                if (exts.isNotEmpty() && destination.isNotBlank()) {
                    result.add(
                        OrganizeRule(
                            label = "${exts.joinToString(" ")} → $destination",
                            extensions = exts,
                            destination = destination
                        )
                    )
                }
            }
            return if (result.isNotEmpty()) result else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse rules JSON: '$rawOutput'", e)
            return null
        }
    }

    private fun cleanJsonString(raw: String): String {
        var str = raw.trim()
        if (str.startsWith("```json", ignoreCase = true)) {
            str = str.substring(7)
        } else if (str.startsWith("```")) {
            str = str.substring(3)
        }
        if (str.endsWith("```")) {
            str = str.substring(0, str.length - 3)
        }
        str = str.trim()

        val firstBrace = str.indexOf('{')
        val lastBrace = str.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            str = str.substring(firstBrace, lastBrace + 1)
        }
        return str
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${kb.toInt()} KB"
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
