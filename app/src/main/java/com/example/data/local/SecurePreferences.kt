package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {

    private val standardPrefs: SharedPreferences =
        context.getSharedPreferences("nova_standard_app_prefs", Context.MODE_PRIVATE)

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "nova_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w("SecurePreferences", "Failed to initialize EncryptedSharedPreferences, falling back to standard prefs", e)
        context.getSharedPreferences("nova_fallback_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_ACTIVE_ENGINE = "active_engine" // "ONLINE" or "OFFLINE"
        private const val KEY_ONLINE_PROVIDER = "online_provider" // "GEMINI", "OPENAI", "ANTHROPIC"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
        private const val KEY_AUTO_CALL = "auto_call_enabled"
        private const val KEY_FAST_ROUTER = "fast_router_enabled"
        private const val KEY_ACTIVE_MODEL_ID = "active_model_id"
        private const val KEY_SELECTED_LANGUAGE = "selected_language"
        private const val KEY_OFFLINE_SPEECH_ENABLED = "offline_speech_enabled"
        private const val KEY_MIC_PERMANENTLY_DENIED = "mic_permanently_denied"
        private const val KEY_STORAGE_PERMANENTLY_DENIED = "storage_permanently_denied"
        private const val KEY_THEME_MODE = "theme_mode" // "SYSTEM", "LIGHT", "DARK"
        private const val KEY_VOICE_FEEDBACK = "voice_feedback_enabled"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_OPENAI_MODEL = "openai_model"
        private const val KEY_ANTHROPIC_MODEL = "anthropic_model"
        private const val KEY_ENGINE_MODE = "engine_mode" // "AUTO", "ONLINE_ONLY", "OFFLINE_ONLY"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_STREAMING_ANIM_ENABLED = "streaming_anim_enabled"
        private const val KEY_HIGH_FIDELITY_ANIM = "high_fidelity_anim"
        private const val KEY_IMAGE_ASPECT_RATIO = "image_aspect_ratio"
        private const val KEY_AUTO_COPY = "auto_copy_enabled"
        private const val KEY_SELECTED_TTS_VOICE = "selected_tts_voice"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_DOWNLOADED_TTS_VOICES = "downloaded_tts_voices"
        private const val KEY_HUGGINGFACE_TOKEN = "huggingface_token"
    }

    init {
        // Clean up legacy test key from storage if present
        val storedKey = prefs.getString(KEY_GEMINI_API_KEY, null)
        if (storedKey != null && storedKey.contains("AQ.Ab8RN6IeW9B5OpekbcYDqxr")) {
            prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
        }
    }

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var isVoiceFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_FEEDBACK, value).apply()

    var isOfflineSpeechEnabled: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_SPEECH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE_SPEECH_ENABLED, value).apply()

    var isMicPermanentlyDenied: Boolean
        get() = prefs.getBoolean(KEY_MIC_PERMANENTLY_DENIED, false)
        set(value) = prefs.edit().putBoolean(KEY_MIC_PERMANENTLY_DENIED, value).apply()

    var isStoragePermanentlyDenied: Boolean
        get() = prefs.getBoolean(KEY_STORAGE_PERMANENTLY_DENIED, false)
        set(value) = prefs.edit().putBoolean(KEY_STORAGE_PERMANENTLY_DENIED, value).apply()

    var selectedLanguage: String
        get() = prefs.getString(KEY_SELECTED_LANGUAGE, "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString(KEY_SELECTED_LANGUAGE, value).apply()

    var activeEngine: String
        get() = prefs.getString(KEY_ACTIVE_ENGINE, "ONLINE") ?: "ONLINE"
        set(value) = prefs.edit().putString(KEY_ACTIVE_ENGINE, value).apply()

    var onlineProvider: String
        get() = prefs.getString(KEY_ONLINE_PROVIDER, "GEMINI") ?: "GEMINI"
        set(value) = prefs.edit().putString(KEY_ONLINE_PROVIDER, value).apply()

    var geminiApiKey: String
        get() {
            val buildConfigKey = try {
                val field = com.example.BuildConfig::class.java.getField("GEMINI_API_KEY")
                val v = field.get(null) as? String
                if (!v.isNullOrBlank() && v != "MY_GEMINI_API_KEY" && v != "YOUR_GEMINI_API_KEY") v else null
            } catch (_: Exception) {
                null
            }
            if (!buildConfigKey.isNullOrBlank()) return buildConfigKey

            return prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        }
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()

    var openaiApiKey: String
        get() {
            val buildConfigKey = try {
                val field = com.example.BuildConfig::class.java.getField("OPENAI_API_KEY")
                val v = field.get(null) as? String
                if (!v.isNullOrBlank() && v != "YOUR_OPENAI_API_KEY") v else null
            } catch (_: Exception) {
                null
            }
            if (!buildConfigKey.isNullOrBlank()) return buildConfigKey

            return prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        }
        set(value) = prefs.edit().putString(KEY_OPENAI_API_KEY, value).apply()

    var anthropicApiKey: String
        get() {
            val buildConfigKey = try {
                val field = com.example.BuildConfig::class.java.getField("ANTHROPIC_API_KEY")
                val v = field.get(null) as? String
                if (!v.isNullOrBlank() && v != "YOUR_ANTHROPIC_API_KEY") v else null
            } catch (_: Exception) {
                null
            }
            if (!buildConfigKey.isNullOrBlank()) return buildConfigKey

            return prefs.getString(KEY_ANTHROPIC_API_KEY, "") ?: ""
        }
        set(value) = prefs.edit().putString(KEY_ANTHROPIC_API_KEY, value).apply()

    var huggingFaceToken: String
        get() {
            val buildConfigKey = try {
                val field = com.example.BuildConfig::class.java.getField("HUGGINGFACE_TOKEN")
                val v = field.get(null) as? String
                if (!v.isNullOrBlank() && v != "YOUR_HUGGINGFACE_TOKEN") v else null
            } catch (_: Exception) {
                null
            }
            if (!buildConfigKey.isNullOrBlank()) return buildConfigKey

            return prefs.getString(KEY_HUGGINGFACE_TOKEN, "") ?: ""
        }
        set(value) = prefs.edit().putString(KEY_HUGGINGFACE_TOKEN, value).apply()

    var geminiModel: String
        get() = prefs.getString(KEY_GEMINI_MODEL, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_GEMINI_MODEL, value).apply()

    var openaiModel: String
        get() = prefs.getString(KEY_OPENAI_MODEL, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_OPENAI_MODEL, value).apply()

    var anthropicModel: String
        get() = prefs.getString(KEY_ANTHROPIC_MODEL, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_ANTHROPIC_MODEL, value).apply()

    var isAutoCallEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CALL, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CALL, value).apply()

    var isFastRouterEnabled: Boolean
        get() = prefs.getBoolean(KEY_FAST_ROUTER, true)
        set(value) = prefs.edit().putBoolean(KEY_FAST_ROUTER, value).apply()

    var activeModelId: String
        get() = prefs.getString(KEY_ACTIVE_MODEL_ID, "gemma_3_1b") ?: "gemma_3_1b"
        set(value) = prefs.edit().putString(KEY_ACTIVE_MODEL_ID, value).apply()

    var engineMode: String
        get() = prefs.getString(KEY_ENGINE_MODE, "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString(KEY_ENGINE_MODE, value).apply()

    var isHapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, value).apply()

    var isStreamingAnimationEnabled: Boolean
        get() = prefs.getBoolean(KEY_STREAMING_ANIM_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_STREAMING_ANIM_ENABLED, value).apply()

    var isHighFidelityAnimationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HIGH_FIDELITY_ANIM, true)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_FIDELITY_ANIM, value).apply()

    var imageGenAspectRatio: String
        get() = prefs.getString(KEY_IMAGE_ASPECT_RATIO, "1:1") ?: "1:1"
        set(value) = prefs.edit().putString(KEY_IMAGE_ASPECT_RATIO, value).apply()

    var isAutoCopyEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_COPY, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_COPY, value).apply()

    var selectedTtsVoice: String
        get() = prefs.getString(KEY_SELECTED_TTS_VOICE, "en-US-AvaNeural") ?: "en-US-AvaNeural"
        set(value) = prefs.edit().putString(KEY_SELECTED_TTS_VOICE, value).apply()

    var ttsSpeed: Float
        get() = prefs.getFloat(KEY_TTS_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_TTS_SPEED, value).apply()

    var ttsPitch: Float
        get() = prefs.getFloat(KEY_TTS_PITCH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_TTS_PITCH, value).apply()

    var downloadedTtsVoices: Set<String>
        get() = prefs.getStringSet(KEY_DOWNLOADED_TTS_VOICES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_DOWNLOADED_TTS_VOICES, value).apply()

    var hasCompletedOnboarding: Boolean
        get() {
            val standard = standardPrefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
            if (standard) return true
            return try {
                prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
            } catch (e: Exception) {
                false
            }
        }
        set(value) {
            standardPrefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, value).commit()
            try {
                prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, value).commit()
            } catch (e: Exception) {
                Log.w("SecurePreferences", "Failed to commit hasCompletedOnboarding", e)
            }
        }

    fun getActiveApiKey(): String {
        return when (onlineProvider) {
            "GEMINI" -> geminiApiKey
            "OPENAI" -> openaiApiKey
            "ANTHROPIC" -> anthropicApiKey
            else -> geminiApiKey
        }
    }
}
