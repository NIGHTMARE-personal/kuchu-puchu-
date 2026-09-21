package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.local.AppLanguageManager
import com.example.model.ActionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class VoiceFeedbackManager(
    private val context: Context,
    private val isEnabledProvider: () -> Boolean = { true },
    private val languageCodeProvider: () -> String = { "SYSTEM" },
    private val selectedVoiceProvider: () -> String = { "en-US-AvaNeural" },
    private val speedProvider: () -> Float = { 1.0f },
    private val pitchProvider: () -> Float = { 1.0f },
    private val isListeningProvider: () -> Boolean = { false }
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceFeedbackManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val synthesizer = EdgeTtsSynthesizer()

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var pendingSpeech: String? = null

    private var mediaPlayer: MediaPlayer? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val cacheDir = File(context.cacheDir, "tts_audio_cache").apply { mkdirs() }
    private val voicePacksDir = File(context.filesDir, "tts_voice_packs").apply { mkdirs() }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            stop()
        }
    }

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to construct TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val targetLocale = AppLanguageManager.getLocale(languageCodeProvider())
            val result = tts?.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            tts?.setSpeechRate(speedProvider())
            tts?.setPitch(pitchProvider())
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    abandonAudioFocus()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    abandonAudioFocus()
                }
            })
            isTtsInitialized = true
            pendingSpeech?.let {
                speak(it)
                pendingSpeech = null
            }
        } else {
            Log.w(TAG, "TTS initialization failed with status $status")
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                focusRequest = request
                am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error requesting audio focus: ${e.message}")
            false
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error abandoning audio focus: ${e.message}")
        }
    }

    private fun md5Hash(text: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(text.trim().toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            text.hashCode().toString()
        }
    }

    private fun getCachedAudioFile(voiceId: String, text: String): File? {
        val hash = md5Hash(text)
        // Check voice pack directory first (pre-downloaded pack)
        val packFile = File(File(voicePacksDir, voiceId), "$hash.mp3")
        if (packFile.exists() && packFile.length() > 0) {
            return packFile
        }
        // Check local dynamic cache
        val cacheFile = File(cacheDir, "${voiceId}_$hash.mp3")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile
        }
        return null
    }

    private fun playFile(file: File, onComplete: () -> Unit = {}) {
        stopPlaybackOnly()
        requestAudioFocus()
        try {
            val player = MediaPlayer()
            mediaPlayer = player
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                stopPlaybackOnly()
                abandonAudioFocus()
                onComplete()
            }
            player.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error ($what, $extra)")
                stopPlaybackOnly()
                abandonAudioFocus()
                onComplete()
                true
            }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file: ${file.name}", e)
            abandonAudioFocus()
            onComplete()
        }
    }

    private fun stopPlaybackOnly() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    /**
     * Synthesize and play speech with Neural Edge TTS, using local cache and
     * graceful fallback to on-device TTS if offline or upon error.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!isEnabledProvider()) return
        val cleanText = text
            .replace(Regex("""```[\s\S]*?```"""), "Code block omitted.")
            .replace(Regex("""[*_`#]"""), "")
            .trim()
        if (cleanText.isBlank()) return

        if (isListeningProvider()) {
            Log.i(TAG, "SpeechRecognizer is listening — skipping TTS speech")
            return
        }

        val voice = TtsVoiceCatalog.getVoiceById(selectedVoiceProvider())
        val speed = speedProvider()
        val pitch = pitchProvider()

        // 1. Check local cache / pre-downloaded pack
        val cached = getCachedAudioFile(voice.id, cleanText)
        if (cached != null) {
            playFile(cached)
            return
        }

        // 2. Synthesize using Neural Voice in background
        val hash = md5Hash(cleanText)
        val destinationFile = File(cacheDir, "${voice.id}_$hash.mp3")

        scope.launch {
            val synthResult = synthesizer.synthesizeToMp3(
                text = cleanText,
                voice = voice,
                speedRate = speed,
                pitchRate = pitch,
                destinationFile = destinationFile
            )

            if (synthResult.isSuccess && destinationFile.exists() && destinationFile.length() > 0) {
                withContext(Dispatchers.Main) {
                    if (!isListeningProvider()) {
                        playFile(destinationFile)
                    }
                }
            } else {
                Log.w(TAG, "Falling back to on-device TextToSpeech for voice ${voice.name}")
                withContext(Dispatchers.Main) {
                    speakViaOnDeviceTts(cleanText, voice, speed, pitch, queueMode)
                }
            }
        }
    }

    private fun speakViaOnDeviceTts(
        text: String,
        voice: TtsVoice,
        speed: Float,
        pitch: Float,
        queueMode: Int
    ) {
        if (!isTtsInitialized) {
            pendingSpeech = text
            return
        }

        try {
            val targetLocale = Locale.forLanguageTag(voice.localeTag)
            tts?.setLanguage(targetLocale)

            // Select best matching system voice
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val matchingVoice = tts?.voices?.find { v ->
                    v.locale.language == targetLocale.language &&
                            (v.name.contains(voice.gender, ignoreCase = true) ||
                                    v.name.contains(voice.name, ignoreCase = true))
                } ?: tts?.voices?.find { v -> v.locale.language == targetLocale.language }

                matchingVoice?.let { tts?.voice = it }
            }

            tts?.setSpeechRate(speed)
            tts?.setPitch(pitch)
            requestAudioFocus()
            val utteranceId = "utterance_${System.currentTimeMillis()}"
            tts?.speak(text, queueMode, null, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error in on-device TTS fallback", e)
            abandonAudioFocus()
        }
    }

    /**
     * Preview a voice sample so the user can test how Ava, Andrew, Swara, etc. sound.
     */
    fun previewVoice(voice: TtsVoice, onComplete: () -> Unit = {}) {
        stop()
        val speed = speedProvider()
        val pitch = pitchProvider()
        val cached = getCachedAudioFile(voice.id, voice.previewSample)
        if (cached != null) {
            playFile(cached, onComplete)
            return
        }

        val hash = md5Hash(voice.previewSample)
        val destinationFile = File(cacheDir, "${voice.id}_$hash.mp3")

        scope.launch {
            val res = synthesizer.synthesizeToMp3(
                text = voice.previewSample,
                voice = voice,
                speedRate = speed,
                pitchRate = pitch,
                destinationFile = destinationFile
            )
            withContext(Dispatchers.Main) {
                if (res.isSuccess && destinationFile.exists()) {
                    playFile(destinationFile, onComplete)
                } else {
                    speakViaOnDeviceTts(voice.previewSample, voice, speed, pitch, TextToSpeech.QUEUE_FLUSH)
                    mainHandler.postDelayed({ onComplete() }, 3000)
                }
            }
        }
    }

    /**
     * Pre-downloads and stores the core offline phrases and preview for a voice pack
     * onto local storage so they are 100% available offline with zero latency.
     */
    suspend fun downloadVoicePack(
        voice: TtsVoice,
        onProgress: (Float) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val packFolder = File(voicePacksDir, voice.id).apply { mkdirs() }
            val phrasesToDownload = TtsVoiceCatalog.CORE_OFFLINE_PHRASES + voice.previewSample
            val total = phrasesToDownload.size
            var downloadedCount = 0

            phrasesToDownload.forEachIndexed { index, phrase ->
                val hash = md5Hash(phrase)
                val targetFile = File(packFolder, "$hash.mp3")
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    val res = synthesizer.synthesizeToMp3(
                        text = phrase,
                        voice = voice,
                        speedRate = 1.0f,
                        pitchRate = 1.0f,
                        destinationFile = targetFile
                    )
                    if (res.isSuccess) {
                        downloadedCount++
                    }
                } else {
                    downloadedCount++
                }
                withContext(Dispatchers.Main) {
                    onProgress((index + 1).toFloat() / total.toFloat())
                }
            }

            Result.success(downloadedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading voice pack for ${voice.id}", e)
            Result.failure(e)
        }
    }

    fun isVoiceDownloaded(voiceId: String): Boolean {
        val packFolder = File(voicePacksDir, voiceId)
        if (!packFolder.exists() || !packFolder.isDirectory) return false
        val files = packFolder.listFiles() ?: return false
        return files.isNotEmpty()
    }

    fun deleteVoicePack(voiceId: String): Boolean {
        val packFolder = File(voicePacksDir, voiceId)
        return if (packFolder.exists()) {
            packFolder.deleteRecursively()
        } else false
    }

    fun getVoicePackSizeFormatted(voiceId: String): String {
        val packFolder = File(voicePacksDir, voiceId)
        if (!packFolder.exists()) return "0 KB"
        val totalBytes = packFolder.listFiles()?.sumOf { it.length() } ?: 0L
        return if (totalBytes > 1024 * 1024) {
            "%.1f MB".format(totalBytes / (1024.0 * 1024.0))
        } else {
            "${totalBytes / 1024} KB"
        }
    }

    /**
     * Provides brief auditory confirmation after an action is executed.
     */
    fun speakSuccess(action: ActionType, target: String, message: String) {
        val speechText = when (action) {
            ActionType.OPEN_APP -> {
                val app = target.ifBlank { "app" }
                "Opening $app"
            }
            ActionType.CALL -> {
                val contact = target.ifBlank { "contact" }
                "Calling $contact"
            }
            ActionType.CREATE_EVENT -> "Event created"
            ActionType.SET_TIMER -> "Timer ready"
            ActionType.YOUTUBE_SEARCH -> {
                val query = target.ifBlank { "video" }
                "Searching YouTube for $query"
            }
            ActionType.ANSWER, ActionType.NONE -> ""
            else -> ""
        }
        if (speechText.isNotBlank()) {
            speak(speechText)
        }
    }

    fun isSpeaking(): Boolean {
        return try {
            (mediaPlayer?.isPlaying == true) || (tts?.isSpeaking == true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Explicit on-demand speech when user taps the Speak icon on an answer card.
     */
    fun toggleSpeakExplicit(rawText: String) {
        if (isSpeaking()) {
            stop()
            return
        }
        speak(rawText)
    }

    fun speakError(errorMessage: String) {
        val lower = errorMessage.lowercase()
        val speechText = when {
            lower.contains("contact") || lower.contains("phone") -> "I couldn't find that contact."
            lower.contains("app") || lower.contains("application") -> "I couldn't find that application."
            lower.contains("calendar") || lower.contains("event") -> "I couldn't schedule the event."
            lower.contains("permission") -> "Permission is required to perform that action."
            lower.contains("internet") || lower.contains("offline") || lower.contains("network") -> "Network connection is required."
            errorMessage.length <= 60 -> errorMessage
            else -> "Sorry, I couldn't complete that request."
        }
        speak(speechText)
    }

    fun stop() {
        stopPlaybackOnly()
        try {
            tts?.stop()
        } catch (_: Exception) {}
        abandonAudioFocus()
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        scope.cancel()
    }
}
