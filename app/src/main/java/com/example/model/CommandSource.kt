package com.example.model

enum class CommandSource(val displayName: String, val shortBadge: String) {
    ROUTER("Fast Router (0ms LLM)", "ROUTER"),
    GEMINI("Gemini Cloud", "GEMINI"),
    OPENAI("OpenAI Cloud", "OPENAI"),
    ANTHROPIC("Claude Cloud", "CLAUDE"),
    OFFLINE_LLM("MediaPipe On-Device", "LOCAL");
}
