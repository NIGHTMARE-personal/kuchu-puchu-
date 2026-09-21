package com.example.llm

import android.util.Log
import com.example.model.ActionCommand
import com.example.model.ActionType
import com.example.model.CommandSource
import com.example.util.TimerParserHelper
import org.json.JSONObject

object ActionJsonParser {

    fun parse(
        rawOutput: String,
        source: CommandSource,
        rawTranscript: String,
        attachmentCount: Int = 0
    ): ActionCommand {
        try {
            val cleaned = cleanJsonString(rawOutput)
            val jsonObject = JSONObject(cleaned)

            val actionKey = jsonObject.optString("action", "none")
            val target = jsonObject.optString("target", "")
            val datetime = if (jsonObject.isNull("datetime")) null else jsonObject.optString("datetime", null)
            val confirmationText = if (jsonObject.has("confirmation_text") && !jsonObject.isNull("confirmation_text")) {
                jsonObject.optString("confirmation_text", "").trim().ifBlank { null }
            } else null
            val reply = if (jsonObject.has("reply") && !jsonObject.isNull("reply")) {
                jsonObject.optString("reply", "").trim().ifBlank { null }
            } else null

            var actionType = ActionType.fromKey(actionKey)

            if (actionType == ActionType.SET_TIMER) {
                var durationSeconds = if (jsonObject.has("duration_seconds") && !jsonObject.isNull("duration_seconds")) {
                    jsonObject.optInt("duration_seconds", 0)
                } else {
                    0
                }

                if (durationSeconds <= 0) {
                    val durationStr = jsonObject.optString("duration_seconds", "")
                        .ifBlank { jsonObject.optString("duration", "") }
                        .ifBlank { jsonObject.optString("time", "") }
                        .ifBlank { target }
                    durationSeconds = TimerParserHelper.parseDurationToSeconds(durationStr.ifBlank { rawTranscript })
                }

                val label = if (jsonObject.has("label") && !jsonObject.isNull("label")) {
                    jsonObject.optString("label", "").trim().takeIf { it.isNotBlank() && it != "null" }
                } else {
                    TimerParserHelper.extractDurationAndLabel(target.ifBlank { rawTranscript }).second
                }

                // Durations > 24 hours (86400 seconds)
                if (durationSeconds > TimerParserHelper.MAX_TIMER_SECONDS) {
                    return ActionCommand(
                        action = ActionType.NONE,
                        target = "Timers go up to 24 hours — use a calendar reminder instead",
                        reply = TimerParserHelper.OVER_24H_MESSAGE,
                        source = source,
                        rawTranscript = rawTranscript,
                        attachmentCount = attachmentCount
                    )
                }

                // If duration was not found or 0: ask "How long?"
                if (durationSeconds <= 0) {
                    return ActionCommand(
                        action = ActionType.ANSWER,
                        target = TimerParserHelper.EMPTY_DURATION_PROMPT,
                        reply = TimerParserHelper.EMPTY_DURATION_PROMPT,
                        source = source,
                        rawTranscript = rawTranscript,
                        attachmentCount = attachmentCount
                    )
                }

                val resolvedTarget = label ?: TimerParserHelper.formatDurationPrefix(durationSeconds)

                return ActionCommand(
                    action = ActionType.SET_TIMER,
                    target = resolvedTarget,
                    datetime = null,
                    confirmationText = null,
                    source = source,
                    rawTranscript = rawTranscript,
                    reply = reply,
                    attachmentCount = attachmentCount,
                    durationSeconds = durationSeconds,
                    label = label
                )
            }

            if (actionType == ActionType.ORGANIZE_DOWNLOADS) {
                val mode = if (jsonObject.has("mode") && !jsonObject.isNull("mode")) {
                    jsonObject.optString("mode", "").trim().lowercase()
                } else {
                    "default"
                }

                // Unknown modes → parser rejects into "answer"
                if (mode != "default" && mode != "custom") {
                    return ActionCommand(
                        action = ActionType.ANSWER,
                        target = "Unknown organize mode",
                        reply = reply ?: "I didn't understand the organize mode. Say 'organize my downloads' to tidy up your files.",
                        source = source,
                        rawTranscript = rawTranscript,
                        attachmentCount = attachmentCount
                    )
                }

                var parsedRules: List<com.example.model.OrganizeRule>? = null
                if (mode == "custom" && jsonObject.has("rules") && !jsonObject.isNull("rules")) {
                    val rulesArray = jsonObject.optJSONArray("rules")
                    if (rulesArray != null) {
                        val list = mutableListOf<com.example.model.OrganizeRule>()
                        for (i in 0 until rulesArray.length()) {
                            val rObj = rulesArray.optJSONObject(i) ?: continue
                            val dest = rObj.optString("destination", "").trim()
                            val extArray = rObj.optJSONArray("extensions")
                            val exts = mutableListOf<String>()
                            if (extArray != null) {
                                for (j in 0 until extArray.length()) {
                                    val ext = extArray.optString(j, "").trim().lowercase()
                                    if (ext.isNotBlank()) {
                                        exts.add(if (ext.startsWith(".")) ext else ".$ext")
                                    }
                                }
                            }
                            if (exts.isNotEmpty() && dest.isNotBlank()) {
                                list.add(
                                    com.example.model.OrganizeRule(
                                        label = "${exts.joinToString(" ")} → $dest",
                                        extensions = exts,
                                        destination = dest
                                    )
                                )
                            }
                        }
                        if (list.isNotEmpty()) {
                            parsedRules = list
                        }
                    }
                }

                return ActionCommand(
                    action = ActionType.ORGANIZE_DOWNLOADS,
                    target = "Downloads",
                    confirmationText = "Organize files in Downloads?",
                    source = source,
                    rawTranscript = rawTranscript,
                    reply = reply,
                    attachmentCount = attachmentCount,
                    organizeMode = mode,
                    organizeRules = parsedRules
                )
            }

            if (actionType == ActionType.NONE && !reply.isNullOrBlank()) {
                actionType = ActionType.ANSWER
            }

            return ActionCommand(
                action = actionType,
                target = if (actionType == ActionType.ANSWER && target.isBlank() && reply != null) reply.take(60) else target.trim(),
                datetime = datetime?.trim()?.ifBlank { null },
                confirmationText = confirmationText,
                source = source,
                rawTranscript = rawTranscript,
                reply = reply,
                attachmentCount = attachmentCount
            )
        } catch (e: Exception) {
            Log.e("ActionJsonParser", "Failed to parse LLM JSON: '$rawOutput'", e)
            val trimmed = rawOutput.trim()
            if (trimmed.isNotBlank()) {
                val firstLine = trimmed.lines().firstOrNull()?.take(80)?.trim() ?: "Answer"
                return ActionCommand(
                    action = ActionType.ANSWER,
                    target = firstLine,
                    reply = trimmed,
                    datetime = null,
                    source = source,
                    rawTranscript = rawTranscript,
                    attachmentCount = attachmentCount
                )
            }
            return ActionCommand(
                action = ActionType.NONE,
                target = rawOutput.take(100),
                datetime = null,
                source = source,
                rawTranscript = rawTranscript,
                attachmentCount = attachmentCount
            )
        }
    }

    private fun cleanJsonString(raw: String): String {
        var str = raw.trim()

        // Strip markdown ```json and ```
        if (str.startsWith("```json", ignoreCase = true)) {
            str = str.substring(7)
        } else if (str.startsWith("```")) {
            str = str.substring(3)
        }
        if (str.endsWith("```")) {
            str = str.substring(0, str.length - 3)
        }
        str = str.trim()

        // Extract substring between first { and last }
        val firstBrace = str.indexOf('{')
        val lastBrace = str.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            str = str.substring(firstBrace, lastBrace + 1)
        }

        return str
    }
}
