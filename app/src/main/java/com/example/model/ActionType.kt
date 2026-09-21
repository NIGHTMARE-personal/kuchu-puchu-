package com.example.model

enum class ActionType(val key: String, val displayLabel: String) {
    CALL("call", "Phone Call"),
    OPEN_APP("open_app", "Launch App"),
    YOUTUBE_SEARCH("youtube_search", "YouTube Search"),
    CREATE_EVENT("create_event", "Calendar Event"),
    SET_TIMER("set_timer", "Set Timer"),
    ORGANIZE_DOWNLOADS("organize_downloads", "Organize Downloads"),
    ANSWER("answer", "Answer"),
    GENERATE_IMAGE("generate_image", "Generate Image"),
    NONE("none", "Unrecognized Action");

    companion object {
        fun fromKey(key: String?): ActionType {
            if (key == null) return NONE
            val normalized = key.lowercase().trim().replace(" ", "_").replace("-", "_")
            return entries.firstOrNull { it.key == normalized } ?: NONE
        }
    }
}
