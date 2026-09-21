package com.example.util

import java.util.regex.Pattern

object TimerParserHelper {

    const val MAX_TIMER_SECONDS = 86400 // 24 hours
    const val OVER_24H_MESSAGE = "Timers go up to 24 hours — use a calendar reminder instead. Would you like me to create a calendar event instead?"
    const val EMPTY_DURATION_PROMPT = "How long?"
    const val CANCEL_TIMER_REPLY = "Timers cannot be cancelled from Kuchu Puchu yet. Please cancel or dismiss the timer in the Clock app."

    private val HOUR_REGEX = Pattern.compile(
        "(\\d+|[a-zA-Z-]+)\\s*(?:hours?|hrs?)",
        Pattern.CASE_INSENSITIVE
    )

    private val MINUTE_REGEX = Pattern.compile(
        "(\\d+|[a-zA-Z-]+)\\s*(?:minutes?|mins?)",
        Pattern.CASE_INSENSITIVE
    )

    private val SECOND_REGEX = Pattern.compile(
        "(\\d+|[a-zA-Z-]+)\\s*(?:seconds?|secs?)",
        Pattern.CASE_INSENSITIVE
    )

    fun parseDurationToSeconds(input: String): Int {
        val clean = input.lowercase().trim()
        if (clean.isBlank()) return 0

        // Handle pure numbers (e.g. "900")
        clean.toIntOrNull()?.let {
            if (it > 0) return it
        }

        var totalSeconds = 0

        val hourMatcher = HOUR_REGEX.matcher(clean)
        if (hourMatcher.find()) {
            val token = hourMatcher.group(1).orEmpty()
            totalSeconds += parseWordOrDigit(token) * 3600
        }

        val minMatcher = MINUTE_REGEX.matcher(clean)
        if (minMatcher.find()) {
            val token = minMatcher.group(1).orEmpty()
            totalSeconds += parseWordOrDigit(token) * 60
        }

        val secMatcher = SECOND_REGEX.matcher(clean)
        if (secMatcher.find()) {
            val token = secMatcher.group(1).orEmpty()
            totalSeconds += parseWordOrDigit(token)
        }

        if (totalSeconds == 0) {
            if (clean.contains("half an hour") || clean.contains("half hour")) {
                totalSeconds = 1800
            } else if (clean.contains("an hour") || clean.contains("one hour")) {
                totalSeconds = 3600
            } else if (clean.contains("a minute") || clean.contains("one minute")) {
                totalSeconds = 60
            }
        }

        return totalSeconds
    }

    fun extractDurationAndLabel(input: String): Pair<Int, String?> {
        var text = input.trim()
        var label: String? = null

        // Check for: "called <label>" or "named <label>"
        val calledRegex = Regex("""^(.*?)\s+(?:called|named|labeled|labelled)\s+(.+)$""", RegexOption.IGNORE_CASE)
        val calledMatch = calledRegex.find(text)
        if (calledMatch != null) {
            text = calledMatch.groupValues[1].trim()
            label = calledMatch.groupValues[2].trim()
        } else {
            // Check for: ", <label>"
            val commaIndex = text.indexOf(',')
            if (commaIndex != -1 && commaIndex < text.length - 1) {
                val beforeComma = text.substring(0, commaIndex).trim()
                val afterComma = text.substring(commaIndex + 1).trim()
                if (beforeComma.isNotBlank() && afterComma.isNotBlank()) {
                    text = beforeComma
                    label = afterComma.replace(Regex("""^(?:called|named|for)\s+""", RegexOption.IGNORE_CASE), "").trim()
                }
            } else {
                // Check for: "<duration> for <label>" where "for" is after time unit
                val forRegex = Regex("""^(.*?\b(?:hours?|hrs?|minutes?|mins?|seconds?|secs?))\s+for\s+(.+)$""", RegexOption.IGNORE_CASE)
                val forMatch = forRegex.find(text)
                if (forMatch != null) {
                    text = forMatch.groupValues[1].trim()
                    label = forMatch.groupValues[2].trim()
                }
            }
        }

        val seconds = parseDurationToSeconds(text)
        return Pair(seconds, label?.takeIf { it.isNotBlank() })
    }

    fun formatDurationPrefix(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 && minutes > 0 -> "$hours hour $minutes-minute"
            hours > 0 && secs > 0 -> "$hours hour $secs-second"
            hours > 0 -> "$hours-hour"
            minutes > 0 && secs > 0 -> "$minutes minute $secs-second"
            minutes > 0 -> "$minutes-minute"
            else -> "$secs-second"
        }
    }

    fun formatDurationFull(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("$hours ${if (hours == 1) "hour" else "hours"}")
        if (minutes > 0) parts.add("$minutes ${if (minutes == 1) "minute" else "minutes"}")
        if (secs > 0) parts.add("$secs ${if (secs == 1) "second" else "seconds"}")

        return if (parts.isEmpty()) "0 seconds" else parts.joinToString(" ")
    }

    private fun parseWordOrDigit(token: String): Int {
        token.toIntOrNull()?.let { return it }
        val t = token.lowercase().trim().replace("-", " ")
        return when (t) {
            "a", "an", "one" -> 1
            "two" -> 2
            "three" -> 3
            "four" -> 4
            "five" -> 5
            "six" -> 6
            "seven" -> 7
            "eight" -> 8
            "nine" -> 9
            "ten" -> 10
            "eleven" -> 11
            "twelve" -> 12
            "thirteen" -> 13
            "fourteen" -> 14
            "fifteen" -> 15
            "sixteen" -> 16
            "seventeen" -> 17
            "eighteen" -> 18
            "nineteen" -> 19
            "twenty" -> 20
            "twenty one" -> 21
            "twenty two" -> 22
            "twenty three" -> 23
            "twenty four" -> 24
            "twenty five" -> 25
            "thirty" -> 30
            "thirty five" -> 35
            "forty" -> 40
            "forty five" -> 45
            "fifty" -> 50
            "fifty five" -> 55
            "sixty" -> 60
            else -> 0
        }
    }
}
