package com.example.data.local

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val speechTag: String
)

object AppLanguageManager {

    val SUPPORTED_LANGUAGES = listOf(
        SupportedLanguage(
            code = "SYSTEM",
            displayName = "Device Default",
            nativeName = "System",
            speechTag = Locale.getDefault().toLanguageTag()
        ),
        SupportedLanguage(
            code = "en",
            displayName = "English",
            nativeName = "English (US)",
            speechTag = "en-US"
        ),
        SupportedLanguage(
            code = "hi",
            displayName = "Hindi",
            nativeName = "हिन्दी",
            speechTag = "hi-IN"
        ),
        SupportedLanguage(
            code = "zh",
            displayName = "Chinese (Simplified)",
            nativeName = "中文 (简体)",
            speechTag = "zh-CN"
        ),
        SupportedLanguage(
            code = "zh-TW",
            displayName = "Chinese (Traditional)",
            nativeName = "中文 (繁體)",
            speechTag = "zh-TW"
        ),
        SupportedLanguage(
            code = "ja",
            displayName = "Japanese",
            nativeName = "日本語",
            speechTag = "ja-JP"
        ),
        SupportedLanguage(
            code = "ko",
            displayName = "Korean",
            nativeName = "한국어",
            speechTag = "ko-KR"
        )
    )

    fun getLanguage(code: String): SupportedLanguage {
        return SUPPORTED_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: SUPPORTED_LANGUAGES.first()
    }

    fun getLocale(code: String): Locale {
        return when (code.trim().lowercase()) {
            "hi" -> Locale("hi", "IN")
            "zh", "zh-cn" -> Locale.SIMPLIFIED_CHINESE
            "zh-tw", "zh-rtw", "zh-hant" -> Locale.TRADITIONAL_CHINESE
            "ja" -> Locale.JAPAN
            "ko" -> Locale.KOREA
            "en" -> Locale.US
            else -> Locale.getDefault()
        }
    }

    fun getSpeechTag(code: String): String {
        val found = SUPPORTED_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }
        return found?.speechTag ?: getLocale(code).toLanguageTag()
    }

    fun getLocalizedContext(baseContext: Context, languageCode: String): Context {
        if (languageCode == "SYSTEM") {
            return baseContext
        }
        val targetLocale = getLocale(languageCode)
        Locale.setDefault(targetLocale)

        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(targetLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(targetLocale))
        }

        return baseContext.createConfigurationContext(config)
    }
}
