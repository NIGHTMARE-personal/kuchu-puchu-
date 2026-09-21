package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AttachmentItem
import com.example.model.AttachmentKind
import com.example.util.AttachmentProcessor

@Composable
fun AttachmentChipsRow(
    attachments: List<AttachmentItem>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        attachments.forEach { item ->
            val isError = item.isInlineError || item.errorMessage != null

            val containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }

            val borderColor = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.outline
            }

            val contentColor = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(containerColor)
                    .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(16.dp))
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    .testTag("attachment_chip_${item.id}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val icon = when {
                        isError -> Icons.Outlined.ErrorOutline
                        item.kind == AttachmentKind.IMAGE -> Icons.Outlined.Image
                        item.kind == AttachmentKind.VIDEO -> Icons.Outlined.PlayArrow
                        item.kind == AttachmentKind.AUDIO -> Icons.Outlined.Mic
                        item.kind == AttachmentKind.PDF -> Icons.Outlined.PictureAsPdf
                        item.kind == AttachmentKind.TEXT -> Icons.Outlined.Description
                        else -> Icons.Outlined.InsertDriveFile
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )

                    val label = if (isError) {
                        item.errorMessage ?: "Attachment error"
                    } else {
                        val displayName = if (item.name.length > 18) {
                            "${item.name.take(15)}..."
                        } else item.name

                        val extra = if (item.kind == AttachmentKind.PDF) {
                            val count = item.pageCount ?: 1
                            val pagesToShow = minOf(5, count)
                            " (pages 1–$pagesToShow of $count) • ${AttachmentProcessor.formatFileSize(item.size)}"
                        } else {
                            " • ${AttachmentProcessor.formatFileSize(item.size)}"
                        }

                        "$displayName$extra"
                    }

                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                        onClick = { onRemove(item.id) },
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("remove_attachment_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Remove",
                            tint = contentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
