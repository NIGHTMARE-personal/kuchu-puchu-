package com.example

import com.example.executor.ContactMatcher
import com.example.llm.ActionJsonParser
import com.example.model.ActionType
import com.example.model.CommandSource
import com.example.router.CommandRouter
import com.example.router.RouterResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NovaAssistantUnitTest {

    private val router = CommandRouter()

    @Test
    fun testRouterCallCommand() {
        val result = router.match("call Mom")
        assertTrue(result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.CALL, cmd.action)
        assertEquals("Mom", cmd.target)
        assertEquals(CommandSource.ROUTER, cmd.source)
    }

    @Test
    fun testRouterOpenAppCommand() {
        val result = router.match("open YouTube")
        assertTrue(result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.OPEN_APP, cmd.action)
        assertEquals("YouTube", cmd.target)
    }

    @Test
    fun testRouterYouTubeSearchCommand() {
        val result = router.match("play Queen Bohemian Rhapsody on YouTube")
        assertTrue(result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.YOUTUBE_SEARCH, cmd.action)
        assertEquals("Queen Bohemian Rhapsody", cmd.target)
    }

    @Test
    fun testRouterOpenMediaOnYouTubeCommand() {
        val result = router.match("open me tera ho gaya on youtube")
        assertTrue("Expected match for open media on youtube", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.YOUTUBE_SEARCH, cmd.action)
        assertEquals("me tera ho gaya", cmd.target)
    }

    @Test
    fun testRouterHinglishYouTubeCommand() {
        val result = router.match("youtube pe me tera ho gaya chalao")
        assertTrue("Expected match for hinglish youtube", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.YOUTUBE_SEARCH, cmd.action)
        assertEquals("me tera ho gaya", cmd.target)
    }

    @Test
    fun testRouterCreateEventCommand() {
        val result = router.match("create event Doctor Appointment tomorrow at 3pm")
        assertTrue(result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.CREATE_EVENT, cmd.action)
        assertEquals("Doctor Appointment", cmd.target)
        assertEquals("tomorrow at 3pm", cmd.datetime)
    }

    @Test
    fun testRouterMultilingualHindiCall() {
        val result = router.match("राहुल को कॉल करो")
        assertTrue("Expected match for Hindi call", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.CALL, cmd.action)
        assertEquals("राहुल", cmd.target)
    }

    @Test
    fun testRouterMultilingualChineseOpenApp() {
        val result = router.match("打开 相机")
        assertTrue("Expected match for Chinese open app", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.OPEN_APP, cmd.action)
        assertEquals("相机", cmd.target)
    }

    @Test
    fun testRouterMultilingualJapaneseYouTube() {
        val result = router.match("YouTubeで ジャズ を再生")
        assertTrue("Expected match for Japanese YouTube search", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.YOUTUBE_SEARCH, cmd.action)
        assertEquals("ジャズ", cmd.target)
    }

    @Test
    fun testRouterMultilingualKoreanCall() {
        val result = router.match("민수 에게 전화해")
        assertTrue("Expected match for Korean call", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.CALL, cmd.action)
        assertEquals("민수", cmd.target)
    }

    @Test
    fun testContactMatcherTransliterationHindiToLatin() {
        val contacts = listOf(
            Pair("Rahul Sharma", "9876543210")
        )
        val match = ContactMatcher.findBestContact("राहुल", contacts)
        assertNotNull("Should match 'राहुल' to Latin contact 'Rahul Sharma'", match)
        assertEquals("Rahul Sharma", match?.name)
    }

    @Test
    fun testContactMatcherFuzzy() {
        val contacts = listOf(
            Pair("Alexander Smith", "1234567890")
        )
        val match = ContactMatcher.findBestContact("Alex", contacts)
        assertNotNull(match)
        assertEquals("Alexander Smith", match?.name)
    }

    @Test
    fun testActionJsonParserValid() {
        val json = """{"action": "call", "target": "John Smith", "datetime": null, "confirmation_text": "Calling John Smith..."}"""
        val cmd = ActionJsonParser.parse(json, CommandSource.GEMINI, "call John Smith")
        assertEquals(ActionType.CALL, cmd.action)
        assertEquals("John Smith", cmd.target)
        assertEquals(null, cmd.datetime)
        assertEquals("Calling John Smith...", cmd.confirmationText)
    }

    @Test
    fun testActionJsonParserWithMarkdownWrapper() {
        val json = """
            ```json
            {
              "action": "open_app",
              "target": "Spotify",
              "datetime": null,
              "confirmation_text": "Opening Spotify"
            }
            ```
        """.trimIndent()
        val cmd = ActionJsonParser.parse(json, CommandSource.OPENAI, "launch spotify")
        assertEquals(ActionType.OPEN_APP, cmd.action)
        assertEquals("Spotify", cmd.target)
        assertEquals("Opening Spotify", cmd.confirmationText)
    }

    @Test
    fun testActionJsonParserAnswerMultimodal() {
        val json = """
            {
              "action": "answer",
              "reply": "The attached image shows a receipt from Acme Groceries totaling $42.50."
            }
        """.trimIndent()
        val cmd = ActionJsonParser.parse(json, CommandSource.GEMINI, "What is this receipt?")
        assertEquals(ActionType.ANSWER, cmd.action)
        assertEquals("The attached image shows a receipt from Acme Groceries totaling $42.50.", cmd.reply)
        assertEquals("The attached image shows a receipt from Acme Groceries totaling $42.50.".take(60), cmd.target)
    }

    @Test
    fun testAttachmentProcessorFormatSize() {
        assertEquals("500 B", com.example.util.AttachmentProcessor.formatFileSize(500L))
        assertEquals("2.0 KB", com.example.util.AttachmentProcessor.formatFileSize(2048L))
        assertEquals("1.5 MB", com.example.util.AttachmentProcessor.formatFileSize(1572864L))
    }

    @Test
    fun testCapabilityRegistryVisionModels() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = com.example.data.local.SecurePreferences(app)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val modelManager = com.example.data.modelmanager.OfflineModelManager(app, scope)
        val registry = com.example.data.registry.CapabilityRegistry(prefs, modelManager)
        
        val models = registry.getAllModels()
        val gemini = models.first { it.id == "GEMINI" }
        val openAi = models.first { it.id == "OPENAI" }
        val anthropic = models.first { it.id == "ANTHROPIC" }
        
        assertTrue("Gemini should have vision", gemini.vision)
        assertTrue("OpenAI should have vision", openAi.vision)
        assertTrue("Anthropic should have vision", anthropic.vision)
        
        val offlineModels = models.filter { it.engineType == com.example.model.ModelEngineType.OFFLINE_LOCAL }
        offlineModels.forEach {
            org.junit.Assert.assertFalse("Offline text LLM ${it.displayName} should not have vision", it.vision)
        }
    }

    @Test
    fun testCommandHistoryEntityTelemetry() {
        val entity = com.example.data.local.CommandHistoryEntity(
            id = 1,
            transcript = "What is this photo?",
            timestamp = System.currentTimeMillis(),
            action = "ANSWER",
            target = "Photo analysis result",
            source = "GEMINI",
            outcome = "SUCCESS",
            details = "Processed successfully",
            engineName = "Gemini 3.6 Flash",
            routeType = "LLM",
            rawIntentJson = "{\"action\":\"answer\"}",
            latencyMs = 450L,
            confidence = 0.98f
        )
        assertEquals("Gemini 3.6 Flash", entity.engineName)
        assertEquals("LLM", entity.routeType)
        assertEquals(450L, entity.latencyMs)
        assertEquals(0.98f, entity.confidence ?: 0f, 0.001f)
    }

    @Test
    fun testRouterSetTimer15Minutes() {
        val result = router.match("set a timer for 15 minutes")
        assertTrue("Should match 'set a timer for 15 minutes'", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.SET_TIMER, cmd.action)
        assertEquals(900, cmd.durationSeconds)
        assertEquals(null, cmd.label)
        assertEquals(CommandSource.ROUTER, cmd.source)
    }

    @Test
    fun testRouterTimer5Minutes() {
        val result = router.match("timer 5 minutes")
        assertTrue("Should match 'timer 5 minutes'", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.SET_TIMER, cmd.action)
        assertEquals(300, cmd.durationSeconds)
        assertEquals(null, cmd.label)
    }

    @Test
    fun testRouterTimerSuffixMinutes() {
        val result = router.match("5 minute timer")
        assertTrue("Should match '5 minute timer'", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.SET_TIMER, cmd.action)
        assertEquals(300, cmd.durationSeconds)
        assertEquals(null, cmd.label)
    }

    @Test
    fun testRouterSetTimerWithLabelCombinedDuration() {
        val result = router.match("set timer for 1 hour 30 minutes called laundry")
        assertTrue("Should match 'set timer for 1 hour 30 minutes called laundry'", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.SET_TIMER, cmd.action)
        assertEquals(5400, cmd.durationSeconds)
        assertEquals("laundry", cmd.label)
        assertEquals("laundry", cmd.target)
    }

    @Test
    fun testRouterTimerFor10MinutesCalledPasta() {
        val result = router.match("timer for 10 minutes called pasta")
        assertTrue("Should match 'timer for 10 minutes called pasta'", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.SET_TIMER, cmd.action)
        assertEquals(600, cmd.durationSeconds)
        assertEquals("pasta", cmd.label)
    }

    @Test
    fun testRouterSetTimerNoDuration() {
        val result = router.match("set a timer")
        assertTrue("Should match 'set a timer'", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.ANSWER, cmd.action)
        assertEquals("How long?", cmd.target)
        assertEquals("How long?", cmd.reply)
    }

    @Test
    fun testRouterDeleteTimer() {
        val result = router.match("delete the timer")
        assertTrue("Should match 'delete the timer'", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.NONE, cmd.action)
        assertTrue("Reply should explain timers cannot be cancelled yet", cmd.reply?.contains("cannot be cancelled from Nova yet", ignoreCase = true) == true)
    }

    @Test
    fun testRouterRejectTimerOver24Hours() {
        val result = router.match("set a timer for 25 hours")
        assertTrue("Should match 'set a timer for 25 hours'", result is RouterResult.Match)
        val cmd = (result as RouterResult.Match).command
        assertEquals(ActionType.NONE, cmd.action)
        assertTrue("Reply should explain 24 hours limit and calendar fallback", cmd.reply?.contains("Timers go up to 24 hours", ignoreCase = true) == true)
        assertTrue("Reply should suggest calendar reminder", cmd.reply?.contains("calendar", ignoreCase = true) == true)
    }

    @Test
    fun testActionJsonParserSetTimerValid() {
        val json = """{"action": "set_timer", "duration_seconds": 900, "label": null}"""
        val cmd = ActionJsonParser.parse(json, CommandSource.GEMINI, "set a timer for 15 minutes")
        assertEquals(ActionType.SET_TIMER, cmd.action)
        assertEquals(900, cmd.durationSeconds)
        assertEquals(null, cmd.label)
    }

    @Test
    fun testActionJsonParserSetTimerWithLabel() {
        val json = """{"action": "set_timer", "duration_seconds": 600, "label": "pasta"}"""
        val cmd = ActionJsonParser.parse(json, CommandSource.OPENAI, "timer for 10 minutes called pasta")
        assertEquals(ActionType.SET_TIMER, cmd.action)
        assertEquals(600, cmd.durationSeconds)
        assertEquals("pasta", cmd.label)
        assertEquals("pasta", cmd.target)
    }

    @Test
    fun testActionJsonParserSetTimerOver24Hours() {
        val json = """{"action": "set_timer", "duration_seconds": 90000, "label": null}"""
        val cmd = ActionJsonParser.parse(json, CommandSource.ANTHROPIC, "set a timer for 25 hours")
        assertEquals(ActionType.NONE, cmd.action)
        assertTrue(cmd.reply?.contains("Timers go up to 24 hours") == true)
    }

    @Test
    fun testActionJsonParserSetTimerNoDuration() {
        val json = """{"action": "set_timer", "duration_seconds": 0}"""
        val cmd = ActionJsonParser.parse(json, CommandSource.GEMINI, "set a timer")
        assertEquals(ActionType.ANSWER, cmd.action)
        assertEquals("How long?", cmd.reply)
    }

    @Test
    fun testThemeModeFromCode() {
        assertEquals(com.example.ui.theme.ThemeMode.LIGHT, com.example.ui.theme.ThemeMode.fromCode("LIGHT"))
        assertEquals(com.example.ui.theme.ThemeMode.LIGHT, com.example.ui.theme.ThemeMode.fromCode("light"))
        assertEquals(com.example.ui.theme.ThemeMode.DARK, com.example.ui.theme.ThemeMode.fromCode("DARK"))
        assertEquals(com.example.ui.theme.ThemeMode.DARK, com.example.ui.theme.ThemeMode.fromCode("dark"))
        assertEquals(com.example.ui.theme.ThemeMode.SYSTEM, com.example.ui.theme.ThemeMode.fromCode("SYSTEM"))
        assertEquals(com.example.ui.theme.ThemeMode.SYSTEM, com.example.ui.theme.ThemeMode.fromCode("invalid_mode"))
    }
}
