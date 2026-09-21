package com.example.model

import android.net.Uri

enum class AttachmentKind {
    IMAGE, PDF, TEXT, FILE, VIDEO, AUDIO
}

data class AttachmentItem(
    val id: String,
    val kind: AttachmentKind,
    val uri: Uri,
    val mime: String,
    val name: String,
    val size: Long,
    val pageCount: Int? = null,
    val errorMessage: String? = null,
    val isInlineError: Boolean = false
)
