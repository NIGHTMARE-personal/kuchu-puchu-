package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseImage: () -> Unit,
    onChooseFile: () -> Unit,
    onPasteClipboard: () -> Unit,
    onChooseVideo: (() -> Unit)? = null,
    onChooseAudio: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Micro-label
            Text(
                text = "ADD ATTACHMENT",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            AttachmentOptionRow(
                icon = Icons.Outlined.PhotoCamera,
                label = "Take photo",
                tag = "picker_take_photo",
                onClick = {
                    onDismiss()
                    onTakePhoto()
                }
            )

            AttachmentOptionRow(
                icon = Icons.Outlined.Image,
                label = "Choose image",
                tag = "picker_choose_image",
                onClick = {
                    onDismiss()
                    onChooseImage()
                }
            )

            if (onChooseVideo != null) {
                AttachmentOptionRow(
                    icon = Icons.Outlined.PlayArrow,
                    label = "Choose video",
                    tag = "picker_choose_video",
                    onClick = {
                        onDismiss()
                        onChooseVideo()
                    }
                )
            }

            if (onChooseAudio != null) {
                AttachmentOptionRow(
                    icon = Icons.Outlined.Mic,
                    label = "Audio / Voice file",
                    tag = "picker_choose_audio",
                    onClick = {
                        onDismiss()
                        onChooseAudio()
                    }
                )
            }

            AttachmentOptionRow(
                icon = Icons.Outlined.Description,
                label = "Choose file (PDF, TXT, DOC)",
                tag = "picker_choose_file",
                onClick = {
                    onDismiss()
                    onChooseFile()
                }
            )

            AttachmentOptionRow(
                icon = Icons.Outlined.ContentPaste,
                label = "Paste from clipboard",
                tag = "picker_paste_clipboard",
                onClick = {
                    onDismiss()
                    onPasteClipboard()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
