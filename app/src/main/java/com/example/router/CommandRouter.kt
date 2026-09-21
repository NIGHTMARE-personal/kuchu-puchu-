package com.example.router

import com.example.model.ActionCommand
import com.example.model.ActionType
import com.example.model.CommandSource
import com.example.util.TimerParserHelper
import java.util.regex.Pattern

sealed interface RouterResult {
    data class Match(val command: ActionCommand) : RouterResult
    object Unmatched : RouterResult
}

class CommandRouter {

    companion object {
        // Pattern 0: Timer
        // "set (a )?timer (for )?N minute(s)?(, ...)?"
        // "timer N minutes", "N minute timer"
        private val TIMER_PATTERN_PREFIX = Pattern.compile(
            "^(?:set\\s+(?:a\\s+)?|start\\s+(?:a\\s+)?|create\\s+(?:a\\s+)?|add\\s+(?:a\\s+)?)?timer(?:\\s+for)?\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )
        private val TIMER_PATTERN_SUFFIX = Pattern.compile(
            "^(?:set\\s+(?:a\\s+)?|start\\s+(?:a\\s+)?)?(.+?)\\s+timer(?:\\s+(?:called|named|for|,)\\s*(.+))?$",
            Pattern.CASE_INSENSITIVE
        )

        // Pattern 1: YouTube Search / Play
        // "play X on youtube", "open X on youtube", "search X on youtube", "watch X on youtube", "listen to X on youtube"
        private val YOUTUBE_PATTERN_1 = Pattern.compile(
            "^(?:open|play|search|watch|listen\\s+to|find|show|stream|start)\\s+(.+?)\\s+(?:on|in)\\s+(?:youtube|yt)$",
            Pattern.CASE_INSENSITIVE
        )
        private val YOUTUBE_PATTERN_2 = Pattern.compile(
            "^(?:youtube|yt)\\s+(?:search\\s+|for\\s+|play\\s+|open\\s+)?(.+)$",
            Pattern.CASE_INSENSITIVE
        )
        private val YOUTUBE_PATTERN_3 = Pattern.compile(
            "^(?:search|look\\s+up)\\s+(?:youtube|yt)\\s+for\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )
        // "open youtube and play/search X", "launch youtube and play X"
        private val YOUTUBE_PATTERN_OPEN_AND = Pattern.compile(
            "^(?:open|launch)\\s+(?:youtube|yt)\\s+(?:and|&|to)?\\s*(?:search|play|watch|find|show|listen\\s+to|look\\s+for)?\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )
        // "X on youtube", "X in youtube", "X on yt" (e.g. "me tera ho gaya on youtube")
        private val YOUTUBE_PATTERN_SUFFIX = Pattern.compile(
            "^(.+?)\\s+(?:on|in)\\s+(?:youtube|yt)$",
            Pattern.CASE_INSENSITIVE
        )
        // Hindi / Hinglish YouTube patterns:
        // "youtube pe me tera ho gaya chalao", "youtube par arijit singh play karo", "youtube mein video open karo"
        private val HINGLISH_YT_PATTERN_1 = Pattern.compile(
            "^(?:youtube|yt|युट्यूब|यूट्यूब)\\s+(?:pe|par|me|mein|per|पर|में)\\s+(.+?)(?:\\s+(?:chalao|chala\\s+do|play\\s+karo|open\\s+karo|bajao|baja\\s+do|search\\s+karo|dekho|lagao|चलाओ|बजाओ|दिखाओ|खोजो))?$",
            Pattern.CASE_INSENSITIVE
        )
        // "me tera ho gaya youtube pe chalao", "song youtube par play karo"
        private val HINGLISH_YT_PATTERN_2 = Pattern.compile(
            "^(.+?)\\s+(?:youtube|yt|युट्यूब|यूट्यूब)\\s+(?:pe|par|me|mein|per|पर|में)\\s*(?:chalao|chala\\s+do|play\\s+karo|open\\s+karo|bajao|baja\\s+do|search\\s+karo|dekho|lagao|चलाओ|बजाओ|दिखाओ|खोजो)?$",
            Pattern.CASE_INSENSITIVE
        )

        // Pattern 2: Call / Dial
        // "call X", "dial X", "phone X"
        private val CALL_PATTERN = Pattern.compile(
            "^(?:call|dial|phone)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )

        // Multilingual Call Patterns
        // Hindi: "कॉल राहुल", "राहुल को कॉल करो", "राहुल को फोन करो"
        private val HINDI_CALL_PATTERN_1 = Pattern.compile("^(?:कॉल|फोन)\\s+(?:करो\\s+)?(.+)$", Pattern.CASE_INSENSITIVE)
        private val HINDI_CALL_PATTERN_2 = Pattern.compile("^(.+?)\\s+को\\s+(?:कॉल|फ़ोन|फोन)\\s*(?:करो|लगाओ|मिलाओ)?$", Pattern.CASE_INSENSITIVE)
        // Chinese: "打电话给 张伟", "呼叫 张伟", "给张伟打电话"
        private val CHINESE_CALL_PATTERN_1 = Pattern.compile("^(?:打电话给|呼叫|致电)\\s*(.+)$")
        private val CHINESE_CALL_PATTERN_2 = Pattern.compile("^给\\s*(.+?)\\s*打电话$")
        // Japanese: "田中 に電話", "田中 に電話して"
        private val JAPANESE_CALL_PATTERN = Pattern.compile("^(.+?)\\s*(?:に|へ)?(?:電話|電話して)$")
        // Korean: "민수 에게 전화", "엄마 한테 전화해"
        private val KOREAN_CALL_PATTERN = Pattern.compile("^(.+?)\\s*(?:에게|한테)?\\s*전화(?:해| 걸어줘)?$")

        // Multilingual Open App Patterns
        // Hindi: "कैमरा खोलो", "यूट्यूब ओपन करो"
        private val HINDI_OPEN_PATTERN = Pattern.compile("^(.+?)\\s+(?:खोलो|ओपन करो)$", Pattern.CASE_INSENSITIVE)
        // Chinese: "打开 相机", "启动 微信"
        private val CHINESE_OPEN_PATTERN = Pattern.compile("^(?:打开|启动)\\s*(.+)$")
        // Japanese: "カメラ を開いて", "設定 を起動"
        private val JAPANESE_OPEN_PATTERN = Pattern.compile("^(.+?)\\s*(?:を)?(?:開いて|起動|起動して)$")
        // Korean: "카메라 열어줘", "설정 실행해줘"
        private val KOREAN_OPEN_PATTERN = Pattern.compile("^(.+?)\\s*(?:열어줘|실행해줘|켜줘)$")

        // Multilingual YouTube Search Patterns
        // Hindi: "यूट्यूब पर अरिजीत सिंह के गाने चलाओ"
        private val HINDI_YT_PATTERN = Pattern.compile("^(?:यूट्यूब|युट्यूब)\\s+पर\\s+(.+?)\\s*(?:चलाओ|बजाओ|सर्च करो|दिखाओ)?$", Pattern.CASE_INSENSITIVE)
        // Chinese: "在YouTube上播放 周杰伦"
        private val CHINESE_YT_PATTERN = Pattern.compile("^(?:在)?(?:YouTube|油管)(?:上)?(?:播放|搜索|找)\\s*(.+)$", Pattern.CASE_INSENSITIVE)
        // Japanese: "YouTubeで ジャズ を再生"
        private val JAPANESE_YT_PATTERN = Pattern.compile("^(?:YouTube|ユーチューブ)(?:で)?\\s*(.+?)\\s*(?:を)?(?:再生|検索|流して)$", Pattern.CASE_INSENSITIVE)
        // Korean: "유튜브에서 재즈 음악 틀어줘"
        private val KOREAN_YT_PATTERN = Pattern.compile("^(?:유튜브|YouTube)(?:에서)?\\s*(.+?)\\s*(?:틀어줘|재생|검색)$", Pattern.CASE_INSENSITIVE)

        // Pattern 3: Open / Launch App
        // "open X", "launch X", "start X", "open app X", or just "open", "open app", "open something"
        private val OPEN_APP_PATTERN = Pattern.compile(
            "^(?:open|launch|start)(?:\\s+(?:the|an|a))?(?:\\s+app)?(?:\\s+(.+))?$",
            Pattern.CASE_INSENSITIVE
        )

        // Pattern 4: Create / Schedule Calendar Event
        // "create event X [tomorrow at Y / at Y / on Y / for Y]", "schedule X [at Y]"
        private val EVENT_PATTERN_WITH_TIME = Pattern.compile(
            "^(?:create\\s+event|schedule|add\\s+event|new\\s+event)\\s+(.+?)\\s+((?:tomorrow|today|tonight|next\\s+\\w+)(?:\\s+(?:at|on|for)\\s+.+)?|(?:at|on|for)\\s+.+)$",
            Pattern.CASE_INSENSITIVE
        )
        private val EVENT_PATTERN_SIMPLE = Pattern.compile(
            "^(?:create\\s+event|schedule|add\\s+event|new\\s+event)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )

        // Pattern 5: Downloads Organizer Fast-Path
        // "organize (my )?downloads", "clean (up )?(my )?downloads", "tidy (my )?downloads"
        val ORGANIZE_FAST_PATH_PATTERN = Pattern.compile(
            "^(?:organize|clean\\s+up|clean|tidy)(?:\\s+my)?\\s+downloads(?:\\s+folder)?$",
            Pattern.CASE_INSENSITIVE
        )

        // Pattern 6: Image Generation
        val IMAGE_GEN_PATTERN = Pattern.compile(
            "^(?:generate|create|draw|paint|make)(?:\\s+(?:an?|me|a))?\\s+(?:image|picture|photo|wallpaper|art|drawing|illustration)(?:\\s+(?:of|showing|with))?\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )

        fun cleanTranscript(transcript: String): String {
            return transcript.trim()
                .replace(Regex("[.,!?;:]+$"), "")
                .trim()
        }

        fun isCustomDownloadsOrganizeRequest(transcript: String): Boolean {
            val clean = cleanTranscript(transcript).lowercase().trim()
            if (!clean.contains("download")) return false
            if (ORGANIZE_FAST_PATH_PATTERN.matcher(clean).matches()) return false

            val hasAction = clean.contains("put") || clean.contains("move") || clean.contains("organize") ||
                    clean.contains("sort") || clean.contains("clean") || clean.contains("tidy")
            val hasTargetIndicator = clean.contains("into") || clean.contains("to") || clean.contains("by") ||
                    clean.contains("invoices") || clean.contains("receipts") || clean.contains("folder") ||
                    clean.contains("pdf") || clean.contains("image") || clean.contains("video") || clean.contains("doc")
            return hasAction && hasTargetIndicator
        }
    }

    fun match(rawTranscript: String): RouterResult {
        val trimmed = cleanTranscript(rawTranscript)
        if (trimmed.isBlank()) return RouterResult.Unmatched

        val lower = trimmed.lowercase().trim()

        // 0. Downloads Organizer Fast-Path (Tier 1: Zero LLM, works offline)
        if (ORGANIZE_FAST_PATH_PATTERN.matcher(trimmed).matches()) {
            return RouterResult.Match(
                ActionCommand(
                    action = ActionType.ORGANIZE_DOWNLOADS,
                    target = "Downloads",
                    organizeMode = "default",
                    confirmationText = "Organize files in Downloads?",
                    source = CommandSource.ROUTER,
                    rawTranscript = rawTranscript
                )
            )
        }

        // 0a. Timer Cancellation / Deletion
        if (lower == "delete the timer" || lower == "delete timer" ||
            lower == "cancel the timer" || lower == "cancel timer" ||
            lower == "stop the timer" || lower == "stop timer" ||
            lower == "clear the timer" || lower == "clear timer") {
            return RouterResult.Match(
                ActionCommand(
                    action = ActionType.NONE,
                    target = "Timers cannot be cancelled from Kuchu Puchu yet",
                    reply = TimerParserHelper.CANCEL_TIMER_REPLY,
                    source = CommandSource.ROUTER,
                    rawTranscript = rawTranscript
                )
            )
        }

        // 0b. Bare timer command with no duration (e.g. "set a timer", "timer") -> ask "How long?"
        if (lower == "set a timer" || lower == "set timer" ||
            lower == "start a timer" || lower == "start timer" ||
            lower == "create a timer" || lower == "create timer" ||
            lower == "add a timer" || lower == "add timer" ||
            lower == "timer") {
            return RouterResult.Match(
                ActionCommand(
                    action = ActionType.ANSWER,
                    target = TimerParserHelper.EMPTY_DURATION_PROMPT,
                    reply = TimerParserHelper.EMPTY_DURATION_PROMPT,
                    source = CommandSource.ROUTER,
                    rawTranscript = rawTranscript
                )
            )
        }

        // 0c. Image Generation Fast-Path
        val imageGenMatcher = IMAGE_GEN_PATTERN.matcher(trimmed)
        if (imageGenMatcher.matches()) {
            val prompt = imageGenMatcher.group(1)?.trim() ?: trimmed
            return RouterResult.Match(
                ActionCommand(
                    action = ActionType.GENERATE_IMAGE,
                    target = prompt,
                    reply = "Creating an image of \"$prompt\" with AI...",
                    source = CommandSource.ROUTER,
                    rawTranscript = rawTranscript
                )
            )
        }

        // 0d. Timer with duration (e.g. "set a timer for 15 minutes", "timer 5 minutes", "N minute timer")
        var timerMatcher = TIMER_PATTERN_PREFIX.matcher(trimmed)
        if (timerMatcher.matches()) {
            val content = timerMatcher.group(1)?.trim().orEmpty()
            val (durationSeconds, label) = TimerParserHelper.extractDurationAndLabel(content)
            if (durationSeconds > 0) {
                if (durationSeconds > TimerParserHelper.MAX_TIMER_SECONDS) {
                    return RouterResult.Match(
                        ActionCommand(
                            action = ActionType.NONE,
                            target = "Timers go up to 24 hours — use a calendar reminder instead",
                            reply = TimerParserHelper.OVER_24H_MESSAGE,
                            source = CommandSource.ROUTER,
                            rawTranscript = rawTranscript
                        )
                    )
                }
                val resolvedTarget = label ?: TimerParserHelper.formatDurationPrefix(durationSeconds)
                return RouterResult.Match(
                    ActionCommand(
                        action = ActionType.SET_TIMER,
                        target = resolvedTarget,
                        durationSeconds = durationSeconds,
                        label = label,
                        source = CommandSource.ROUTER,
                        rawTranscript = rawTranscript
                    )
                )
            } else if (content.isBlank() || content.equals("please", ignoreCase = true)) {
                return RouterResult.Match(
                    ActionCommand(
                        action = ActionType.ANSWER,
                        target = TimerParserHelper.EMPTY_DURATION_PROMPT,
                        reply = TimerParserHelper.EMPTY_DURATION_PROMPT,
                        source = CommandSource.ROUTER,
                        rawTranscript = rawTranscript
                    )
                )
            }
        }

        timerMatcher = TIMER_PATTERN_SUFFIX.matcher(trimmed)
        if (timerMatcher.matches()) {
            val durationText = timerMatcher.group(1)?.trim().orEmpty()
            val explicitLabel = timerMatcher.group(2)?.trim()?.takeIf { it.isNotBlank() }
            val (durationSeconds, extractedLabel) = TimerParserHelper.extractDurationAndLabel(durationText)
            val label = explicitLabel ?: extractedLabel
            if (durationSeconds > 0) {
                if (durationSeconds > TimerParserHelper.MAX_TIMER_SECONDS) {
                    return RouterResult.Match(
                        ActionCommand(
                            action = ActionType.NONE,
                            target = "Timers go up to 24 hours — use a calendar reminder instead",
                            reply = TimerParserHelper.OVER_24H_MESSAGE,
                            source = CommandSource.ROUTER,
                            rawTranscript = rawTranscript
                        )
                    )
                }
                val resolvedTarget = label ?: TimerParserHelper.formatDurationPrefix(durationSeconds)
                return RouterResult.Match(
                    ActionCommand(
                        action = ActionType.SET_TIMER,
                        target = resolvedTarget,
                        durationSeconds = durationSeconds,
                        label = label,
                        source = CommandSource.ROUTER,
                        rawTranscript = rawTranscript
                    )
                )
            }
        }

        // 1. YouTube Search (check before generic open/play)
        for (pattern in listOf(
            YOUTUBE_PATTERN_1,
            YOUTUBE_PATTERN_2,
            YOUTUBE_PATTERN_3,
            YOUTUBE_PATTERN_OPEN_AND,
            YOUTUBE_PATTERN_SUFFIX,
            HINGLISH_YT_PATTERN_1,
            HINGLISH_YT_PATTERN_2,
            HINDI_YT_PATTERN,
            CHINESE_YT_PATTERN,
            JAPANESE_YT_PATTERN,
            KOREAN_YT_PATTERN
        )) {
            val m = pattern.matcher(trimmed)
            if (m.matches()) {
                val target = m.group(1)?.trim().orEmpty()
                val lowerTarget = target.lowercase()
                // Avoid matching just "app", "application", or empty
                if (target.isNotBlank() && lowerTarget != "app" && lowerTarget != "the app" && lowerTarget != "something") {
                    return RouterResult.Match(
                        ActionCommand(
                            action = ActionType.YOUTUBE_SEARCH,
                            target = target,
                            source = CommandSource.ROUTER,
                            rawTranscript = rawTranscript
                        )
                    )
                }
            }
        }

        // 2. Call / Phone
        var matcher = CALL_PATTERN.matcher(trimmed)
        if (matcher.matches()) {
            val target = matcher.group(1)?.trim().orEmpty()
            // Make sure it doesn't say "call it off" or "call me back"
            if (target.isNotBlank() && !target.equals("it off", ignoreCase = true)) {
                return RouterResult.Match(
                    ActionCommand(
                        action = ActionType.CALL,
                        target = target,
                        source = CommandSource.ROUTER,
                        rawTranscript = rawTranscript
                    )
                )
            }
        }

        // Multilingual Call matchers
        for (p in listOf(HINDI_CALL_PATTERN_1, HINDI_CALL_PATTERN_2, CHINESE_CALL_PATTERN_1, CHINESE_CALL_PATTERN_2, JAPANESE_CALL_PATTERN, KOREAN_CALL_PATTERN)) {
            val m = p.matcher(trimmed)
            if (m.matches()) {
                val target = m.group(1)?.trim().orEmpty()
                if (target.isNotBlank()) {
                    return RouterResult.Match(
                        ActionCommand(
                            action = ActionType.CALL,
                            target = target,
                            source = CommandSource.ROUTER,
                            rawTranscript = rawTranscript
                        )
                    )
                }
            }
        }

        // 3. Calendar Event
        matcher = EVENT_PATTERN_WITH_TIME.matcher(trimmed)
        if (matcher.matches()) {
            val target = matcher.group(1)?.trim().orEmpty()
            val datetime = matcher.group(2)?.trim().orEmpty()
            if (target.isNotBlank()) {
                return RouterResult.Match(
                    ActionCommand(
                        action = ActionType.CREATE_EVENT,
                        target = target,
                        datetime = datetime.ifBlank { null },
                        source = CommandSource.ROUTER,
                        rawTranscript = rawTranscript
                    )
                )
            }
        }

        matcher = EVENT_PATTERN_SIMPLE.matcher(trimmed)
        if (matcher.matches()) {
            val target = matcher.group(1)?.trim().orEmpty()
            if (target.isNotBlank()) {
                return RouterResult.Match(
                    ActionCommand(
                        action = ActionType.CREATE_EVENT,
                        target = target,
                        datetime = null,
                        source = CommandSource.ROUTER,
                        rawTranscript = rawTranscript
                    )
                )
            }
        }

        // 4. Open / Launch App
        matcher = OPEN_APP_PATTERN.matcher(trimmed)
        if (matcher.matches()) {
            val target = matcher.group(1)?.trim().orEmpty()
            val lowerTarget = target.lowercase()
            val genericKeywords = setOf("something", "anything", "app", "an app", "the app", "whatever", "application")

            // Intercept media queries directed to YouTube, e.g. "open me tera ho gaya on youtube"
            val ytSuffixMatch = YOUTUBE_PATTERN_SUFFIX.matcher(target)
            if (ytSuffixMatch.matches()) {
                val mediaQuery = ytSuffixMatch.group(1)?.trim().orEmpty()
                if (mediaQuery.isNotBlank() && mediaQuery.lowercase() !in genericKeywords) {
                    return RouterResult.Match(
                        ActionCommand(
                            action = ActionType.YOUTUBE_SEARCH,
                            target = mediaQuery,
                            source = CommandSource.ROUTER,
                            rawTranscript = rawTranscript
                        )
                    )
                }
            }

            if (target.isBlank() || lowerTarget in genericKeywords) {
                return RouterResult.Match(
                    ActionCommand(
                        action = ActionType.ANSWER,
                        target = "Which app would you like to open?",
                        reply = "Which app would you like to open? You can say 'open YouTube', 'open Camera', 'open Settings', or 'open Calculator'.",
                        source = CommandSource.ROUTER,
                        rawTranscript = rawTranscript
                    )
                )
            } else if (!target.equals("door", ignoreCase = true) && !target.equals("the door", ignoreCase = true)) {
                return RouterResult.Match(
                    ActionCommand(
                        action = ActionType.OPEN_APP,
                        target = target,
                        source = CommandSource.ROUTER,
                        rawTranscript = rawTranscript
                    )
                )
            }
        }

        // Multilingual Open App matchers
        for (p in listOf(HINDI_OPEN_PATTERN, CHINESE_OPEN_PATTERN, JAPANESE_OPEN_PATTERN, KOREAN_OPEN_PATTERN)) {
            val m = p.matcher(trimmed)
            if (m.matches()) {
                val target = m.group(1)?.trim().orEmpty()
                if (target.isNotBlank()) {
                    return RouterResult.Match(
                        ActionCommand(
                            action = ActionType.OPEN_APP,
                            target = target,
                            source = CommandSource.ROUTER,
                            rawTranscript = rawTranscript
                        )
                    )
                }
            }
        }

        return RouterResult.Unmatched
    }
}
