# KUCHU PUCHU

```text
 _  ___   _  ____ _   _ _   _   ____  _   _  ____ _   _ _   _ 
| |/ / | | |/ ___| | | | | | | |  _ \| | | |/ ___| | | | | | |
| ' /| | | | |   | |_| | | | | | |_) | | | | |   | |_| | | | |
| . \| |_| | |___|  _  | |_| | |  __/| |_| | |___|  _  | |_| |
|_|\_\\___/ \____|_| |_|\___/  |_|    \___/ \____|_| |_|\___/ 
```

<div align="center">

[![Platform](https://img.shields.io/badge/Platform-Android%2014%2B%20(API%2034%2F36)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack%20Compose%20Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Local Inference](https://img.shields.io/badge/On--Device-MediaPipe%20GenAI%20(Gemma)-FF6F00?style=for-the-badge&logo=google&logoColor=white)](https://developers.google.com/mediapipe)
[![Multi-Cloud](https://img.shields.io/badge/Cloud%20LLM-Gemini%20%7C%20GPT--4o%20%7C%20Claude-black?style=for-the-badge)](https://ai.google.dev)
[![License](https://img.shields.io/badge/License-MIT-000000?style=for-the-badge)](LICENSE)
[![Architect](https://img.shields.io/badge/Architect-NIGHTMARE-990000?style=for-the-badge)](https://github.com/NIGHTMARE-personal)

**Next-Generation Autonomous On-Device & Multi-Cloud Android Action Companion**

*An execution-first assistant engineered to bypass conversational friction and execute real-world Android device actions with zero-latency deterministic routing, offline neural intelligence, and multi-cloud LLM synthesis.*

</div>

---

## 1. Architectural Philosophy

Traditional voice assistants waste compute cycles and introduce latency by passing deterministic device actions through remote LLM chat loops. **Kuchu Puchu** enforces a dual-tier execution hierarchy:

1. **Tier 1: Sub-Millisecond Deterministic Action Router (0ms LLM Overhead)**
   High-frequency hardware intents (phone calls, alarms, timers, calendar entries, application launches, media searches) are evaluated against compiled multilingual regular expression engines. When matched, execution is routed directly to native Android providers without hitting external APIs or loading on-device weights.
2. **Tier 2: Agentic Multi-Model Fallback Pipeline**
   Complex generative queries, conversational analysis, multimodal attachments (images/text files), and smart notes fall back to either:
   - **On-Device Local SLMs**: Google MediaPipe GenAI running quantized Gemma models entirely offline on local NPU/GPU hardware.
   - **Multi-Cloud BYOK Providers**: Zero-latency streaming via Google Gemini, OpenAI (GPT-4o / GPT-4o-mini), or Anthropic Claude (Claude 3.5 Sonnet) secured via hardware-backed encryption.

```
                              [USER VOICE / TEXT INPUT]
                                          │
                                          ▼
                      ┌───────────────────────────────────────┐
                      │    CommandRouter (Regex Engine)       │
                      │   English / Hindi / 中文 / 日本語 / 한국어 │
                      └───────────────────┬───────────────────┘
                                          │
                 ┌────────────────────────┴────────────────────────┐
                 │ MATCH (0ms)                                     │ UNMATCHED (Neural)
                 ▼                                                 ▼
   ┌───────────────────────────┐                     ┌───────────────────────────┐
   │ ActionExecutorCoordinator │                     │    Inference Kernel       │
   ├───────────────────────────┤                     ├───────────────────────────┤
   │ • CallExecutor (Contacts) │                     │ [Offline Mode]            │
   │ • OpenAppExecutor (PM)    │                     │  MediaPipe GenAI (Gemma)  │
   │ • TimerExecutor (Alarms)  │                     ├───────────────────────────┤
   │ • CreateEventExecutor     │                     │ [Online Mode - BYOK]      │
   │ • YouTubeSearchExecutor   │                     │  • Google Gemini          │
   │ • DownloadsOrganizer      │                     │  • OpenAI (GPT-4o)        │
   └─────────────┬─────────────┘                     │  • Anthropic Claude       │
                 │                                   └─────────────┬─────────────┘
                 │                                                 │
                 ▼                                                 ▼
   ┌───────────────────────────┐                     ┌───────────────────────────┐
   │ Native Android Execution  │                     │ Streaming Answer & TTS    │
   │  (Intents / Content Prov) │                     │ (Edge TTS / System Synth) │
   └───────────────────────────┘                     └───────────────────────────┘
```

---

## 2. Core Subsystems

### Subsystem 1: Dual-Tier Command Pipeline
- **Zero-Latency Router (`CommandRouter.kt`)**: Implements strict pre-compiled regex automata handling natural patterns across 5 languages:
  - **Phone Dialing**: `call <name>`, `dial <number>`, `<name> को कॉल करो`, `给<name>打电话`, `<name>に電話`, `<name>에게 전화`.
  - **Timers & Alarms**: `timer for 5 minutes`, `10 min timer`, `5 मिनट का टाइमर लगाओ`.
  - **Media & YouTube**: `play <query> on youtube`, `youtube pe <query> chalao`, `在YouTube上播放 <query>`.
  - **App Introspection**: Dynamic package queries matching spoken app names (`QUERY_ALL_PACKAGES` / intent filters).
  - **Calendar Events**: Natural date/time extraction with automatic calendar sync.
  - **Storage Triage**: Instant trigger for the Downloads folder organizer.

### Subsystem 2: Sovereign Multi-Provider Engine
- **Local On-Device Engine (`OfflineLlmEngine.kt`)**: Direct integration with Google MediaPipe GenAI SDK. Supports downloading, verifying, and running quantized `.bin` models on-device with zero telemetry.
- **Cloud BYOK Engine (`OnlineLlmEngine.kt`)**: Unified REST/WebSocket client adapters for:
  - **Google Gemini**: Gemini 1.5 Flash, Gemini 1.5 Pro, Gemini 2.0 Flash.
  - **OpenAI**: GPT-4o, GPT-4o-mini.
  - **Anthropic**: Claude 3.5 Sonnet.
- **Multimodal Attachment Processor (`AttachmentProcessor.kt`)**: Supports real-time camera captures via `FileProvider`, gallery images, and raw text documents passed directly into vision-capable models.

### Subsystem 3: Android Native Intent & Hardware Execution Layer
- **`CallExecutor.kt` & `ContactMatcher.kt`**: Queries Android `ContactsContract` with fuzzy matching. Offers two operational modes: Direct Auto-Dial (`ACTION_CALL`) or Dial Confirmation preview (`ACTION_DIAL`).
- **`OpenAppExecutor.kt`**: Scans device launcher intents, indexing package metadata to match colloquial application names with zero hardcoding.
- **`YouTubeSearchExecutor.kt`**: Dispatches explicit video search or playback intents to the native YouTube application with intelligent browser fallback.
- **`TimerExecutor.kt`**: Integrates with Android `AlarmClock.ACTION_SET_TIMER` for native countdown tracking.
- **`CreateEventExecutor.kt`**: Parses event titles, dates, and times, injecting events via `CalendarContract.Events`.

### Subsystem 4: Autonomous Local Storage Organizer (`DownloadsOrganizerManager.kt`)
- Scans `Environment.DIRECTORY_DOWNLOADS` for unstructured clutter.
- Automatically analyzes MIME types and file extensions, categorizing files into designated directories:
  - `Documents/` (`.pdf`, `.docx`, `.xlsx`, `.pptx`, `.txt`, `.epub`)
  - `Images/` (`.jpg`, `.png`, `.webp`, `.svg`, `.gif`)
  - `Audio/` (`.mp3`, `.wav`, `.m4a`, `.aac`, `.flac`)
  - `Video/` (`.mp4`, `.mkv`, `.mov`, `.avi`)
  - `Archives/` (`.zip`, `.rar`, `.7z`, `.tar`, `.gz`)
  - `Installers/` (`.apk`, `.xapk`, `.apks`)
  - `Code/` (`.py`, `.json`, `.xml`, `.kt`, `.java`, `.cpp`, `.html`)
- Generates a preview plan before execution, requesting explicit user authorization.

### Subsystem 5: Multi-Modal Audio & Voice Pipeline
- **Speech-to-Text (`SpeechRecognizerManager.kt`)**: Native Android speech recognition integration with support for downloadable offline language packs (English, Hindi, Chinese, Japanese, Korean).
- **Text-to-Speech Engine (`EdgeTtsSynthesizer.kt` & `VoiceFeedbackManager.kt`)**: Dual-engine architecture featuring high-fidelity Microsoft Edge TTS streaming synthesis paired with Android System TTS fallback.
- **Real-Time Visualizer (`WaveformVisualization.kt` & `MicRippleButton.kt`)**: Dynamic audio amplitude feedback during push-to-talk recording sessions.

### Subsystem 6: Hardware-Backed Cryptographic Isolation
- **EncryptedSharedPreferences (`SecurePreferences.kt`)**: API keys and sensitive tokens are encrypted using **AES-256-GCM** keys managed by the **Android Keystore system**.
- **Zero Cloud Exfiltration**: Credentials are never transmitted to third-party tracking services or proxy servers. All API calls originate directly from the host device.

---

## 3. Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.0+ (100% pure Kotlin codebase) |
| **Build System** | Gradle Kotlin DSL (`build.gradle.kts`), Version Catalogs (`libs.versions.toml`) |
| **UI Framework** | Jetpack Compose, Material 3, Custom Sniglet rounded typography |
| **Reactive State** | Kotlin Coroutines, StateFlow, Compose `collectAsStateWithLifecycle` |
| **Local Database** | Room Database (SQLite), Android Keystore AES-256-GCM |
| **On-Device AI** | Google MediaPipe GenAI (Gemma SLMs) |
| **Cloud AI** | Google Gemini API, OpenAI API, Anthropic Claude API |
| **Networking** | OkHttp 4, Retrofit 2, Moshi Codegen |
| **Image Loading** | Coil Compose |
| **Voice & Audio** | Android `SpeechRecognizer`, Android `TextToSpeech`, Microsoft Edge TTS REST |

---

## 4. Supported Command Matrix

| Intent Category | English Command Example | Multilingual Command Example | Target Subsystem |
| :--- | :--- | :--- | :--- |
| **Phone Call** | `call Mom` | `राहुल को कॉल करो` / `给张伟打电话` | `CallExecutor` |
| **Set Timer** | `timer for 15 minutes` | `10 मिनट का टाइमर लगाओ` | `TimerExecutor` |
| **YouTube Media** | `play lofi hip hop on youtube` | `यूट्यूब पर गाने चलाओ` / `在YouTube上播放 周杰伦` | `YouTubeSearchExecutor` |
| **Launch App** | `open Camera` | `कैमरा खोलो` / `打开微信` / `カメラを開いて` | `OpenAppExecutor` |
| **Schedule Event** | `schedule team sync tomorrow at 3pm` | `कल दोपहर 3 बजे मीटिंग शेड्यूल करो` | `CreateEventExecutor` |
| **Triage Storage** | `organize my downloads` | `डाउनलोड्स फ़ोल्डर व्यवस्थित करो` | `DownloadsOrganizerManager` |
| **Multimodal Query** | `what is in this picture?` *(attach photo)* | *(Multimodal vision prompt)* | `OnlineLlmEngine` / `OfflineLlmEngine` |
| **Smart Notes** | `take a note: buy groceries tomorrow` | `नोट लिखो: कल राशन लाना है` | `UserProfileRepository` / Room |

---

## 5. Repository Structure

```
kuchu-puchu-/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/              # Room DB, UserProfile, SecurePreferences
│   │   │   │   │   ├── modelmanager/       # OfflineModelManager (MediaPipe storage)
│   │   │   │   │   └── registry/           # CapabilityRegistry
│   │   │   │   ├── executor/               # Hardware executors (Call, Timer, App, YouTube, Event)
│   │   │   │   ├── llm/                    # Adapters (Gemini, OpenAI, Anthropic, MediaPipe)
│   │   │   │   ├── model/                  # Domain entities & command models
│   │   │   │   ├── organizer/              # DownloadsOrganizerManager & Planner
│   │   │   │   ├── router/                 # CommandRouter (Regex NLP automata)
│   │   │   │   ├── ui/
│   │   │   │   │   ├── NovaAssistantViewModel.kt
│   │   │   │   │   ├── components/         # M3 Compose UI widgets & dialogs
│   │   │   │   │   ├── screens/            # Home, Models, Profile, Settings, Notes
│   │   │   │   │   └── theme/              # Color, Theme, Sniglet Typography
│   │   │   │   ├── util/                   # Haptics, Attachments, Network telemetry
│   │   │   │   └── voice/                  # Edge TTS, SpeechRecognizer, Voice Catalog
│   │   │   └── res/                        # Drawables, layouts, fonts, localized strings
│   │   └── test/                           # Unit & Robolectric tests
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── .env.example
├── build.gradle.kts
├── settings.gradle.kts
├── LICENSE
└── README.md
```

---

## 6. Build & Setup Instructions

### Prerequisites
- **Android Studio Ladybug (2024.2.1+)** or command-line Android SDK.
- **JDK 17+** configured in environment.
- Android device or emulator running **API 24+** (Android 7.0+). Recommended: API 34+ for complete permission & intent coverage.

### Configuration
1. Clone the repository:
   ```bash
   git clone https://github.com/NIGHTMARE-personal/kuchu-puchu-.git
   cd kuchu-puchu-
   ```
2. Set up environment variables for API providers:
   ```bash
   cp .env.example .env
   ```
3. Edit `.env` to include your provider keys (all optional if using offline on-device SLMs):
   ```env
   GEMINI_API_KEY=your_gemini_api_key
   OPENAI_API_KEY=your_openai_api_key
   ANTHROPIC_API_KEY=your_anthropic_api_key
   ```

### Compilation & Installation
- Assemble Debug APK:
  ```bash
  ./gradlew assembleDebug
  ```
- Install onto connected Android device:
  ```bash
  ./gradlew installDebug
  ```
- Run Local Unit Tests:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 7. Security & Telemetry Invariants

- **Attribution & Provenance**: Designed, architected, and maintained by **NIGHTMARE** under **NIGHTMARE-PROJECTS**.
- **Zero Exfiltration**: No telemetry, analytics pings, or cloud relay services are active.
- **Local Key Storage**: API tokens reside exclusively in hardware-backed `EncryptedSharedPreferences`.

---

## 8. License

This project is open-source software licensed under the **MIT License**. See the [LICENSE](LICENSE) file for complete terms.

```text
Copyright (c) 2026 NIGHTMARE (NIGHTMARE-PROJECTS)
```
