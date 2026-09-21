package com.example.model

enum class ModelEngineType {
    ONLINE_PROVIDER,
    OFFLINE_LOCAL
}

data class ModelCapability(
    val id: String,
    val displayName: String,
    val engineType: ModelEngineType,
    val vision: Boolean,
    val usable: Boolean,
    val description: String = ""
)

data class ImageUnsupportedState(
    val title: String = "This model can't process images",
    val message: String,
    val isOnline: Boolean,
    val pendingInput: String,
    val pendingAttachments: List<AttachmentItem>,
    val showSwitchSheet: Boolean = false
)
