package com.example.model

data class ActionCommand(
    val action: ActionType,
    val target: String,
    val datetime: String? = null,
    val confirmationText: String? = null,
    val source: CommandSource = CommandSource.ROUTER,
    val rawTranscript: String = "",
    val reply: String? = null,
    val attachmentCount: Int = 0,
    val engineName: String? = null,
    val durationSeconds: Int? = null,
    val label: String? = null,
    val organizeMode: String? = null, // "default" or "custom"
    val organizeRules: List<OrganizeRule>? = null
) {
    fun requiresConfirmation(): Boolean {
        return action == ActionType.CALL || action == ActionType.CREATE_EVENT || action == ActionType.ORGANIZE_DOWNLOADS
    }
}
