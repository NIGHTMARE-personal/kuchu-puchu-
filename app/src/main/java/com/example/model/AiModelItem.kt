package com.example.model

enum class ModelCategory {
    LLM,
    STT_LANGUAGE_PACK
}

enum class VerificationState {
    NOT_VERIFIED,
    VERIFYING,
    VERIFIED,
    FAILED
}

data class AiModelItem(
    val id: String,
    val name: String,
    val description: String,
    val parameterSize: String,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long,
    val downloadUrl: String,
    val fileName: String,
    val category: ModelCategory = ModelCategory.LLM,
    val languageCode: String? = null,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val isActive: Boolean = false,
    val vision: Boolean = false,
    val verificationState: VerificationState = VerificationState.NOT_VERIFIED,
    val verificationError: String? = null,
    val isRecommended: Boolean = false,
    val willStruggle: Boolean = false
) {
    val displayName: String get() = name
    val isVerifiedWorking: Boolean get() = isDownloaded && verificationState == VerificationState.VERIFIED
    val usable: Boolean get() = isDownloaded && verificationState != VerificationState.FAILED
}
