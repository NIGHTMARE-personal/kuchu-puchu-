package com.example.llm

object StreamTextExtractor {

    fun extractReplyText(accumulatedRaw: String): String = extractStreamingReply(accumulatedRaw)

    /**
     * Extracts progressive user-facing reply text from partial streaming LLM outputs.
     * Handles both structured JSON ("reply": "...") and direct plain/markdown text streams.
     */
    fun extractStreamingReply(accumulatedRaw: String): String {
        val trimmed = accumulatedRaw.trimStart()
        if (trimmed.isEmpty()) return ""

        // If it doesn't start with JSON brace, it's streaming raw Markdown/text directly
        if (!trimmed.startsWith("{")) {
            return accumulatedRaw
        }

        // Look for "reply": " pattern inside JSON
        val replyKeyPatterns = listOf("\"reply\": \"", "\"reply\":\"", "\"reply\" : \"")
        var startIndex = -1

        for (pattern in replyKeyPatterns) {
            val idx = trimmed.indexOf(pattern)
            if (idx != -1) {
                startIndex = idx + pattern.length
                break
            }
        }

        if (startIndex == -1) {
            // "reply" key not reached yet (e.g. streaming "action" or "target")
            return ""
        }

        // Extract everything after startIndex up to unescaped closing quote
        val sb = StringBuilder()
        var i = startIndex
        var isEscaped = false

        while (i < trimmed.length) {
            val c = trimmed[i]
            if (isEscaped) {
                when (c) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '\"' -> sb.append('\"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        // Unicode escape \uXXXX if enough characters remaining
                        if (i + 4 < trimmed.length) {
                            val hex = trimmed.substring(i + 1, i + 5)
                            val codePoint = hex.toIntOrNull(16)
                            if (codePoint != null) {
                                sb.append(codePoint.toChar())
                                i += 4
                            } else {
                                sb.append(c)
                            }
                        } else {
                            sb.append(c)
                        }
                    }
                    else -> sb.append(c)
                }
                isEscaped = false
            } else {
                if (c == '\\') {
                    isEscaped = true
                } else if (c == '\"') {
                    // Reached closing quote of reply field
                    break
                } else {
                    sb.append(c)
                }
            }
            i++
        }

        return sb.toString()
    }
}
