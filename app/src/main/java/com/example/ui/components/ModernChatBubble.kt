package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.local.CommandHistoryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modern ChatGPT & Gemini style clean chat message item.
 * Replaces bulky technical cards with an elegant, streamlined conversation layout.
 * Supports:
 * - Rich text responses with Markdown
 * - Native AI Image generation with shimmer loading & fullscreen viewer
 * - Attached image, video, audio, and document previews
 * - One-tap Copy, Text-to-Speech (TTS), Share, and Retry actions
 */
@Composable
fun ModernChatBubble(
    item: CommandHistoryEntity,
    onRetry: (() -> Unit)? = null,
    onSpeak: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFullScreenImageOpen by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var copiedRecently by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isImageGen = item.action.equals("generate_image", ignoreCase = true) ||
            item.attachmentBadge.equals("IMAGE_GEN", ignoreCase = true) ||
            (item.details?.startsWith("http") == true && (item.details.contains(".pollinations.ai") || item.details.contains("image")))

    val imageUrlToDisplay = when {
        isImageGen -> item.attachmentUri ?: item.details ?: item.target
        item.attachmentUri?.startsWith("http") == true || item.attachmentUri?.startsWith("content://") == true -> item.attachmentUri
        else -> null
    }

    val assistantReplyText = when {
        !item.details.isNullOrBlank() && !item.details.startsWith("http") -> item.details
        !item.target.isBlank() && !isImageGen -> item.target
        isImageGen -> "Generated image for \"${item.transcript}\""
        else -> item.transcript
    }

    val timeFormatted = remember(item.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(item.timestamp))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. User Message Turn (Aligned to End)
        if (item.transcript.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 320.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Attachment preview if user provided one
                    if (!item.attachmentUri.isNullOrBlank() && !isImageGen) {
                        UserAttachmentPreview(
                            uriString = item.attachmentUri,
                            badge = item.attachmentBadge,
                            onOpenImage = {
                                fullScreenImageUrl = item.attachmentUri
                                isFullScreenImageOpen = true
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // User Text Bubble
                    Surface(
                        shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = item.transcript,
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        // 2. Assistant Response Turn (Clean Left-Aligned ChatGPT / Gemini Style)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            // Assistant Avatar
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, end = 10.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "Nova AI",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Message Body + Toolbar
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Header: Engine & Timestamp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    val modelName = item.engineName ?: "Kuchu Puchu"
                    Text(
                        text = modelName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = timeFormatted,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // If this was a device action (e.g. Timer, Call, YouTube, App, Downloads), show a clean status pill
                if (!item.action.equals("answer", ignoreCase = true) && !isImageGen && !item.action.equals("none", ignoreCase = true)) {
                    DeviceActionStatusPill(
                        action = item.action,
                        target = item.target
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // If this is an Image Generation response, show the native AI Image Card
                if (isImageGen && !imageUrlToDisplay.isNullOrBlank()) {
                    GeneratedImageCard(
                        imageUrl = imageUrlToDisplay,
                        prompt = item.target.ifBlank { item.transcript },
                        onExpand = {
                            fullScreenImageUrl = imageUrlToDisplay
                            isFullScreenImageOpen = true
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Assistant Text Message (Markdown Formatted)
                if (assistantReplyText.isNotBlank() && (!isImageGen || assistantReplyText != imageUrlToDisplay)) {
                    MarkdownText(
                        markdown = assistantReplyText,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Sleek Action Toolbar: Copy, Speak, Retry, Share
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy button
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Nova Message", assistantReplyText))
                            copiedRecently = true
                            scope.launch {
                                delay(2000)
                                copiedRecently = false
                            }
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (copiedRecently) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Copy message",
                            tint = if (copiedRecently) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Speak aloud TTS button
                    if (onSpeak != null && assistantReplyText.isNotBlank()) {
                        IconButton(
                            onClick = { onSpeak(assistantReplyText) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VolumeUp,
                                contentDescription = "Read aloud",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Share button
                    IconButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, assistantReplyText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share response"))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Retry button
                    if (onRetry != null) {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Retry",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Image Dialog
    if (isFullScreenImageOpen && fullScreenImageUrl != null) {
        Dialog(onDismissRequest = { isFullScreenImageOpen = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isFullScreenImageOpen = false },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(fullScreenImageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Full preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }
    }
}

/**
 * Sleek Native AI Generated Image Card with animated shimmer loading,
 * high-res display, and tap-to-expand preview.
 */
@Composable
private fun GeneratedImageCard(
    imageUrl: String,
    prompt: String,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "image_pulse")
    val shimmerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                RoundedCornerShape(18.dp)
            )
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onExpand() },
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = prompt,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(32.dp)
                                    .rotate(shimmerRotation)
                            )
                            Text(
                                text = "Crafting your image with AI...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                error = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Tap to load preview",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            // Top-right Expand icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { onExpand() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fullscreen,
                    contentDescription = "Expand",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Bottom Image Prompt info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = prompt,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "$prompt\n$imageUrl")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Open in browser",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * User attachment thumbnail preview for image, video, audio, or document.
 */
@Composable
private fun UserAttachmentPreview(
    uriString: String,
    badge: String?,
    onOpenImage: () -> Unit
) {
    val context = LocalContext.current
    val isImage = badge?.contains("IMAGE", ignoreCase = true) == true ||
            uriString.endsWith(".jpg", ignoreCase = true) ||
            uriString.endsWith(".png", ignoreCase = true) ||
            uriString.endsWith(".webp", ignoreCase = true)

    val isVideo = badge?.contains("VIDEO", ignoreCase = true) == true ||
            uriString.endsWith(".mp4", ignoreCase = true) ||
            uriString.endsWith(".mov", ignoreCase = true)

    val isAudio = badge?.contains("AUDIO", ignoreCase = true) == true ||
            uriString.endsWith(".mp3", ignoreCase = true) ||
            uriString.endsWith(".wav", ignoreCase = true) ||
            uriString.endsWith(".m4a", ignoreCase = true)

    when {
        isImage -> {
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 85.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
                    .clickable { onOpenImage() }
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context).data(uriString).crossfade(true).build(),
                    contentDescription = "Attached photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        isVideo -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Video",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Video attached", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        isAudio -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = "Audio",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Audio recording attached", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        else -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = "File",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Document attached", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * Subtle status pill for device actions (e.g. Timer, YouTube, App, Downloads)
 */
@Composable
private fun DeviceActionStatusPill(
    action: String,
    target: String
) {
    val (icon, label) = when (action.uppercase()) {
        "SET_TIMER" -> Pair(Icons.Outlined.Timer, "Timer set for $target")
        "OPEN_APP" -> Pair(Icons.Outlined.AutoAwesome, "Opened $target")
        "YOUTUBE_SEARCH" -> Pair(Icons.Outlined.PlayArrow, "Playing $target on YouTube")
        "ORGANIZE_DOWNLOADS" -> Pair(Icons.Outlined.Description, "Organized Downloads")
        else -> Pair(Icons.Outlined.Check, "Executed $action: $target")
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
