package com.example.voice

data class TtsVoice(
    val id: String,
    val name: String,
    val regionLabel: String,
    val localeTag: String,
    val gender: String,
    val accentDescription: String,
    val previewSample: String
)

object TtsVoiceCatalog {
    val ALL_VOICES: List<TtsVoice> = listOf(
        TtsVoice(
            id = "en-US-AvaNeural",
            name = "Ava",
            regionLabel = "(US)",
            localeTag = "en-US",
            gender = "Female",
            accentDescription = "Warm, natural & conversational American English",
            previewSample = "Hello! I'm Ava, your AI assistant. How can I help you today?"
        ),
        TtsVoice(
            id = "en-US-AndrewNeural",
            name = "Andrew",
            regionLabel = "(US)",
            localeTag = "en-US",
            gender = "Male",
            accentDescription = "Clear, articulate & confident American English",
            previewSample = "Hey there! I'm Andrew, ready to assist you with any task."
        ),
        TtsVoice(
            id = "en-US-EmmaNeural",
            name = "Emma",
            regionLabel = "(CIS / US)",
            localeTag = "en-US",
            gender = "Female",
            accentDescription = "Friendly, expressive & cheerful English voice",
            previewSample = "Hi! I'm Emma. Let's make today productive and great."
        ),
        TtsVoice(
            id = "en-GB-SoniaNeural",
            name = "Sonia",
            regionLabel = "(UK)",
            localeTag = "en-GB",
            gender = "Female",
            accentDescription = "Polite, elegant & refined British English",
            previewSample = "Greetings! I'm Sonia, your British voice assistant."
        ),
        TtsVoice(
            id = "en-GB-RyanNeural",
            name = "Ryan",
            regionLabel = "(UK)",
            localeTag = "en-GB",
            gender = "Male",
            accentDescription = "Engaging, calm & natural British English",
            previewSample = "Hello, I'm Ryan. How may I be of service to you today?"
        ),
        TtsVoice(
            id = "en-AU-NatashaNeural",
            name = "Natasha",
            regionLabel = "(AU)",
            localeTag = "en-AU",
            gender = "Female",
            accentDescription = "Bright, friendly Australian English",
            previewSample = "G'day! I'm Natasha, glad to help you out with your tasks."
        ),
        TtsVoice(
            id = "hi-IN-SwaraNeural",
            name = "Swara",
            regionLabel = "(Hindi)",
            localeTag = "hi-IN",
            gender = "Female",
            accentDescription = "Sweet, expressive Hindi & Hinglish natural voice",
            previewSample = "Namaste! Main Swara hoon, aapki AI voice assistant."
        ),
        TtsVoice(
            id = "hi-IN-MadhurNeural",
            name = "Madhur",
            regionLabel = "(Hindi)",
            localeTag = "hi-IN",
            gender = "Male",
            accentDescription = "Deep, warm & articulate Hindi voice",
            previewSample = "Namaste! Main Madhur hoon. Batayein main aapki kya madad kar sakta hoon?"
        ),
        TtsVoice(
            id = "el-GR-NestorasNeural",
            name = "Nestoras",
            regionLabel = "(Greek)",
            localeTag = "el-GR",
            gender = "Male",
            accentDescription = "Fluent, natural & expressive Greek voice",
            previewSample = "Geia sas! Eimai o Nestoras, o prosopikos sas voithos."
        ),
        TtsVoice(
            id = "el-GR-AthinaNeural",
            name = "Athina",
            regionLabel = "(Greek)",
            localeTag = "el-GR",
            gender = "Female",
            accentDescription = "Melodic, modern & articulate Greek voice",
            previewSample = "Geia sas! Eimai i Athina. Pos boro na sas voithiso simera?"
        ),
        TtsVoice(
            id = "ja-JP-NanamiNeural",
            name = "Nanami",
            regionLabel = "(JP)",
            localeTag = "ja-JP",
            gender = "Female",
            accentDescription = "Gentle, polite & clear Japanese voice",
            previewSample = "Konnichiwa! Nanami desu. Nani ka otetsudai dekimashou ka?"
        ),
        TtsVoice(
            id = "ja-JP-KeitaNeural",
            name = "Keita",
            regionLabel = "(JP)",
            localeTag = "ja-JP",
            gender = "Male",
            accentDescription = "Energetic, modern Japanese male voice",
            previewSample = "Konnichiwa! Keita desu. Kyou mo ganbarimashou!"
        ),
        TtsVoice(
            id = "es-ES-ElviraNeural",
            name = "Elvira",
            regionLabel = "(ES)",
            localeTag = "es-ES",
            gender = "Female",
            accentDescription = "Warm, vibrant Castilian Spanish voice",
            previewSample = "Hola! Soy Elvira, tu asistente virtual. En qué puedo ayudarte?"
        ),
        TtsVoice(
            id = "fr-FR-DeniseNeural",
            name = "Denise",
            regionLabel = "(FR)",
            localeTag = "fr-FR",
            gender = "Female",
            accentDescription = "Melodic, sophisticated French voice",
            previewSample = "Bonjour! Je suis Denise, votre assistante vocale."
        ),
        TtsVoice(
            id = "de-DE-KatjaNeural",
            name = "Katja",
            regionLabel = "(DE)",
            localeTag = "de-DE",
            gender = "Female",
            accentDescription = "Professional, precise & articulate German voice",
            previewSample = "Guten Tag! Ich bin Katja, deine persönliche Assistentin."
        )
    )

    fun getVoiceById(id: String): TtsVoice {
        return ALL_VOICES.find { it.id.equals(id, ignoreCase = true) } ?: ALL_VOICES.first()
    }

    /**
     * Core phrases pre-synthesized into offline voice packs.
     */
    val CORE_OFFLINE_PHRASES = listOf(
        "Opening app",
        "Calling contact",
        "Event created",
        "Timer ready",
        "Searching YouTube",
        "Permission is required to perform that action.",
        "Network connection is required.",
        "I'm ready. How can I help you?",
        "Sorry, I couldn't complete that request."
    )
}
