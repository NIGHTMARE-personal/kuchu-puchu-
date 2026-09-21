# Kuchu Puchu 🐾

**Kuchu Puchu** is an intelligent AI companion for Android built with Kotlin and Jetpack Compose. It executes real device actions, smart voice controls, on-device local LLM inferences, and cloud models (Google Gemini, OpenAI / ChatGPT, Anthropic Claude).

---

## 🌟 Key Features

- **Voice & Device Actions**: Fast regex and LLM intent routing for calls, alarms, apps, reminders, timers, flashlight, and volume controls.
- **Multi-Cloud Models**: Seamlessly switch between **Google Gemini**, **ChatGPT / OpenAI**, and **Anthropic Claude** with resilient automatic model failover.
- **On-Device Offline LLM**: Support for local GGUF models on device without requiring an active internet connection.
- **Smart Notes**: Voice-to-text note taking, automatic summarization, keyword extraction, and checklist generation.
- **Privacy & Security First**: Zero tracking, local preferences encrypted on device, and secure API key management via `.env` / Secrets Gradle Plugin.

---

## 🔒 Security & Environment Setup

This project uses the **Secrets Gradle Plugin** to ensure API keys are never hardcoded in source code or committed to version control.

### 1. Configure Environment Variables
Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

Open `.env` and configure your API keys:

```ini
# Required for Gemini models (gemini-3.6-flash, gemini-3.5-flash, etc.)
GEMINI_API_KEY=your_gemini_api_key_here

# Optional: OpenAI / ChatGPT
# OPENAI_API_KEY=your_openai_api_key_here

# Optional: Anthropic Claude
# ANTHROPIC_API_KEY=your_anthropic_api_key_here
```

> **Security Note**: `.env`, keystore files (`*.jks`, `*.keystore`), and `google-services.json` are in `.gitignore` and must never be checked into Git.

---

## 🛠️ Building & Running

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17
- Android SDK 36 (Minimum SDK 24)

### Build with Gradle
To build the debug APK:
```bash
./gradlew assembleDebug
```

To run unit tests:
```bash
./gradlew testDebugUnitTest
```

---

## 📄 License
This project is open source and available under the [Apache 2.0 License](LICENSE).
