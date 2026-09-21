package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.data.local.CommandHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Editorial Result card for answer and action result cards.
 * Features an always-visible compact Message Action Row:
 * Copy, Share, Retry, "How this was handled".
 */
@Composable
fun HistoryItemCard(
    item: CommandHistoryEntity,
    onRetry: (() -> Unit)? = null,
    onRetryItem: ((CommandHistoryEntity) -> Unit)? = null,
    onShowHowHandled: ((CommandHistoryEntity) -> Unit)? = null,
    onSpeak: ((String) -> Unit)? = null,
    onOpenSystemTrace: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isHowHandledOpen by remember { mutableStateOf(false) }
    var isHeaderExpanded by remember { mutableStateOf(false) }
    var isShareDialogOpen by remember { mutableStateOf(false) }

    val isAnswer = item.action.equals("ANSWER", ignoreCase = true)
    val isError = item.outcome.equals("ERROR", ignoreCase = true)

    val microLabel = when (item.action.uppercase()) {
        "CALL" -> "CALL"
        "OPEN_APP" -> "APPLICATION"
        "YOUTUBE_SEARCH" -> "YOUTUBE"
        "CREATE_EVENT" -> "CALENDAR"
        "SET_TIMER" -> "TIMER"
        "SETTINGS" -> "SYSTEM"
        "VOICE_ERROR" -> "VOICE"
        "ANSWER" -> "ANSWER"
        else -> if (isError) "ERROR" else item.action.uppercase()
    }

    val title = remember(item) {
        when (item.action.uppercase()) {
            "CALL" -> "Call to ${item.target}"
            "OPEN_APP" -> "Opened ${item.target}"
            "YOUTUBE_SEARCH" -> "Played \"${item.target}\""
            "CREATE_EVENT" -> "Event: ${item.target}"
            "SET_TIMER" -> {
                if (!item.details.isNullOrBlank() && item.details.contains("timer ready", ignoreCase = true)) {
                    item.details
                } else if (item.target.isNotBlank()) {
                    "${item.target} timer ready — press start in Clock"
                } else {
                    "Timer ready — press start in Clock"
                }
            }
            "SETTINGS" -> "System Settings"
            "VOICE_ERROR" -> item.target
            "ANSWER" -> if (item.target.isNotBlank()) item.target else "Response"
            else -> if (item.target.isNotBlank()) item.target else item.transcript
        }
    }

    val secondary = remember(item) {
        if (!item.details.isNullOrBlank()) {
            item.details
        } else {
            val status = when (item.outcome) {
                "SUCCESS" -> "Success"
                "CANCELLED" -> "Cancelled"
                "ERROR" -> "Failed"
                else -> "Processed"
            }
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(item.timestamp))
            "$status • $time"
        }
    }

    // Copy: answers → full text; action results → the human-readable summary.
    fun copyToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textToCopy = if (isAnswer) {
            if (!item.details.isNullOrBlank()) {
                item.details
            } else if (item.target.isNotBlank()) {
                item.target
            } else {
                item.transcript
            }
        } else {
            val actionSummary = when (item.action.uppercase()) {
                "CALL" -> "Call to ${item.target}"
                "OPEN_APP" -> "Opened ${item.target}"
                "YOUTUBE_SEARCH" -> "Played \"${item.target}\" on YouTube"
                "CREATE_EVENT" -> "Created event: ${item.target}${if (!item.datetime.isNullOrBlank()) " at ${item.datetime}" else ""}"
                "SET_TIMER" -> "Timer: ${item.target}"
                "SETTINGS" -> "System Settings"
                "VOICE_ERROR" -> "Voice error: ${item.target}"
                else -> "${item.action.replace('_', ' ')}: ${item.target}"
            }
            if (!item.details.isNullOrBlank()) "$actionSummary — ${item.details}" else actionSummary
        }

        clipboard.setPrimaryClip(ClipData.newPlainText("Kuchu Puchu", textToCopy))
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // Share: ACTION_SEND text; attach original image only via explicit "include".
    val shareText = remember(item, title, secondary) {
        if (isAnswer) {
            "${title}\n\n${secondary}"
        } else {
            "${title} — ${secondary}"
        }
    }

    fun initiateShare() {
        if (!item.attachmentUri.isNullOrBlank()) {
            isShareDialogOpen = true
        } else {
            ShareHelper.executeShare(
                context = context,
                textToShare = shareText,
                attachmentUriString = null,
                includeImage = false
            )
        }
    }

    fun triggerRetry() {
        if (onRetryItem != null) {
            onRetryItem(item)
        } else {
            onRetry?.invoke()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                RoundedCornerShape(16.dp)
            )
            .testTag("history_item_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Collapsible Dropdown-Style Header Section (all metadata/status moved into downward chevron dropdown)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isHeaderExpanded) 0.5f else 0.25f))
                    .border(
                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                // Header Bar: Micro-label + Status Dot + Chevron Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isHeaderExpanded = !isHeaderExpanded }
                        .testTag("metadata_dropdown_toggle_${item.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        val actionIcon = when (item.action.uppercase()) {
                            "CALL" -> Icons.Outlined.Phone
                            "OPEN_APP" -> Icons.Outlined.Apps
                            "YOUTUBE_SEARCH" -> Icons.Outlined.PlayCircle
                            "CREATE_EVENT" -> Icons.Outlined.CalendarToday
                            "SET_TIMER" -> Icons.Outlined.Timer
                            else -> Icons.Outlined.AutoAwesome
                        }
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )

                        Text(
                            text = microLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.06.em,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Status dot: Green for success, Orange for fallback, Red for error
                        val statusDotColor = when (item.outcome.uppercase()) {
                            "SUCCESS" -> Color(0xFF00BA7C)
                            "FALLBACK" -> Color(0xFFFF9800)
                            "ERROR" -> MaterialTheme.colorScheme.error
                            else -> Color(0xFF00BA7C)
                        }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )

                        if (item.engineName != null) {
                            Text(
                                text = item.engineName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (item.latencyMs != null && item.latencyMs > 0) {
                            Text(
                                text = "• ${item.latencyMs}ms",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!item.attachmentBadge.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = item.attachmentBadge,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        val timeFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(item.timestamp))
                        Text(
                            text = timeFormatted,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Icon(
                            imageVector = if (isHeaderExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isHeaderExpanded) "Hide status metadata" else "Show status metadata",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Expanded Dropdown Panel: all metadata, routing, confidence, system trace inspection & raw JSON
                AnimatedVisibility(
                    visible = isHeaderExpanded,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("metadata_dropdown_expanded_${item.id}"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // Outcome & Route
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Status: ${item.outcome.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (item.routeType != null) {
                                Text(
                                    text = "Route: ${item.routeType}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Engine & Latency
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (item.engineName != null) {
                                Text(
                                    text = "Engine: ${item.engineName}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (item.latencyMs != null && item.latencyMs > 0) {
                                Text(
                                    text = "Latency: ${item.latencyMs} ms",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (item.confidence != null) {
                            Text(
                                text = "Confidence: ${"%.2f".format(item.confidence)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Real-time System Trace Trigger (RAM / Storage inspection)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .clickable { onOpenSystemTrace?.invoke() }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("view_system_trace_chip_${item.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Inspect System Trace (RAM & Disk)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Telemetry ›",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Raw intent JSON
                        if (!item.rawIntentJson.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(6.dp))
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = item.rawIntentJson,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body content
            if (isAnswer) {
                SelectionContainer {
                    MarkdownText(
                        markdown = if (item.details?.isNotBlank() == true) item.details!! else title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("answer_body_${item.id}"),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }
            } else {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = secondary,
                    fontSize = 13.sp,
                    lineHeight = 18.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Always-visible compact Message Action Row: Copy, Share, Retry, Speak (clean minimalist design)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("message_action_row_${item.id}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy Action
                IconButton(
                    onClick = { copyToClipboard() },
                    modifier = Modifier.testTag("action_copy_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Share Action
                IconButton(
                    onClick = { initiateShare() },
                    modifier = Modifier.testTag("action_share_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Retry Action
                IconButton(
                    onClick = { triggerRetry() },
                    modifier = Modifier.testTag("action_retry_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Retry",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Clean Minimalist Speak Action (replaces the info button)
                IconButton(
                    onClick = {
                        val textToSpeak = if (!item.details.isNullOrBlank()) item.details!! else title
                        onSpeak?.invoke(textToSpeak)
                    },
                    modifier = Modifier.testTag("action_speak_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VolumeUp,
                        contentDescription = "Read aloud",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }

    // Monospace "How this was handled" sheet
    if (isHowHandledOpen) {
        HowThisWasHandledSheet(
            item = item,
            onDismiss = { isHowHandledOpen = false }
        )
    }

    // Explicit image include share dialog (when attachment present)
    if (isShareDialogOpen) {
        ShareResultDialog(
            item = item,
            textToShare = shareText,
            onDismiss = { isShareDialogOpen = false }
        )
    }
}
