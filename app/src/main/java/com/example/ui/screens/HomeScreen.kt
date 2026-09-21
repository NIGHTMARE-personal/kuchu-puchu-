package com.example.ui.screens

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.AppLanguageManager
import com.example.data.local.CommandHistoryEntity
import com.example.model.AttachmentItem
import com.example.model.AttachmentKind
import com.example.ui.NovaUiState
import com.example.ui.components.AttachmentChipsRow
import com.example.ui.components.AttachmentPickerBottomSheet
import com.example.ui.components.ConfirmationCard
import com.example.ui.components.DownloadsPlanCard
import com.example.ui.components.DownloadsProgressCard
import com.example.ui.components.DownloadsSummaryCard
import com.example.ui.components.HistoryItemCard
import com.example.ui.components.ModernChatBubble
import com.example.ui.components.StoragePermissionCard
import com.example.ui.theme.LocalNovaCustomColors
import com.example.ui.theme.NovaGreen
import com.example.util.AttachmentProcessor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: NovaUiState,
    historyList: List<CommandHistoryEntity>,
    onToggleMic: () -> Unit,
    onSubmitManualText: (String) -> Unit,
    onConfirmAction: () -> Unit,
    onCancelAction: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenAttachmentSheet: () -> Unit,
    onCloseAttachmentSheet: () -> Unit,
    onAddAttachment: (AttachmentItem) -> Unit,
    onAddInlineErrorChip: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onCancelRequest: () -> Unit,
    onRetryCommand: () -> Unit,
    onSetInputPlaceholder: (String) -> Unit,
    onSetEngine: (String) -> Unit,
    onRetryItem: ((CommandHistoryEntity) -> Unit)? = null,
    onOpenVisionModelSwitch: (() -> Unit)? = null,
    onDismissVisionModelSwitch: (() -> Unit)? = null,
    onSelectVisionModel: ((com.example.model.ModelCapability) -> Unit)? = null,
    onRemoveAttachmentsAndDismissRecovery: (() -> Unit)? = null,
    onDismissImageUnsupportedRecovery: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    usableVisionModels: List<com.example.model.ModelCapability> = emptyList(),
    modelsList: List<com.example.model.AiModelItem> = emptyList(),
    onToggleOrganizeRule: ((ruleId: String, isChecked: Boolean) -> Unit)? = null,
    onExecuteOrganizePlan: (() -> Unit)? = null,
    onCancelOrganizePlan: (() -> Unit)? = null,
    onCancelOrganizeProgress: (() -> Unit)? = null,
    onUndoOrganizePlan: ((planId: String) -> Unit)? = null,
    onDismissOrganizeSummary: (() -> Unit)? = null,
    onRequestStorageAccess: (() -> Unit)? = null,
    onDismissStoragePermission: (() -> Unit)? = null,
    onStopStreaming: (() -> Unit)? = null,
    onClearConversation: (() -> Unit)? = null,
    onDismissOnboarding: (() -> Unit)? = null,
    onDismissFallbackMessage: (() -> Unit)? = null,
    onSetLanguage: ((String) -> Unit)? = null,
    onNavigateToModels: (() -> Unit)? = null,
    onSpeakText: ((String) -> Unit)? = null,
    onToggleTheme: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDark = com.example.ui.theme.LocalIsDarkTheme.current
    var inputText by remember { mutableStateOf("") }
    var isSystemTraceOpen by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val currentLang = AppLanguageManager.getLanguage(uiState.selectedLanguage)
    val greetingText = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning ~ Kuchu Puchu"
            in 12..16 -> "Good afternoon ~ Kuchu Puchu"
            else -> "Good evening ~ Kuchu Puchu"
        }
    }

    // Camera launcher with FileProvider URI (No android.permission.CAMERA required)
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isFromTopBarCamera by remember { mutableStateOf(false) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUri?.let { uri ->
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val cameraDir = File(context.cacheDir, "camera")
                val files = cameraDir.listFiles()
                val newest = files?.maxByOrNull { it.lastModified() }
                val fileSize = newest?.length() ?: 0L

                onAddAttachment(
                    AttachmentItem(
                        id = "cam_${System.currentTimeMillis()}",
                        kind = AttachmentKind.IMAGE,
                        uri = uri,
                        mime = "image/jpeg",
                        name = "Photo $timeStr.jpg",
                        size = fileSize
                    )
                )

                if (isFromTopBarCamera) {
                    onSetInputPlaceholder("Ask about this photo or give a command")
                    try {
                        focusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }
        }
        isFromTopBarCamera = false
    }

    fun launchCamera(fromTopBar: Boolean = false) {
        isFromTopBarCamera = fromTopBar
        try {
            val cameraDir = File(context.cacheDir, "camera")
            if (!cameraDir.exists()) cameraDir.mkdirs()
            val photoFile = File(cameraDir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleSelectedUri(uri: Uri) {
        val contentResolver = context.contentResolver
        val mime = contentResolver.getType(uri) ?: ""
        var name = "Attachment"
        var size = 0L

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        // Validate supported file type at selection time
        val isSupported = mime.startsWith("image/") ||
                mime.startsWith("video/") ||
                mime.startsWith("audio/") ||
                mime == "application/pdf" ||
                mime.startsWith("text/") ||
                mime == "application/json" ||
                name.endsWith(".pdf", ignoreCase = true) ||
                name.endsWith(".txt", ignoreCase = true) ||
                name.endsWith(".json", ignoreCase = true) ||
                name.endsWith(".mp4", ignoreCase = true) ||
                name.endsWith(".mkv", ignoreCase = true) ||
                name.endsWith(".mov", ignoreCase = true) ||
                name.endsWith(".mp3", ignoreCase = true) ||
                name.endsWith(".wav", ignoreCase = true) ||
                name.endsWith(".m4a", ignoreCase = true) ||
                name.endsWith(".doc", ignoreCase = true) ||
                name.endsWith(".docx", ignoreCase = true) ||
                name.endsWith(".csv", ignoreCase = true)

        if (!isSupported) {
            onAddInlineErrorChip("$name: Unsupported file format (${mime.ifBlank { "unknown" }})")
            return
        }

        val kind = when {
            mime.startsWith("image/") -> AttachmentKind.IMAGE
            mime.startsWith("video/") || name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".mkv", ignoreCase = true) || name.endsWith(".mov", ignoreCase = true) -> AttachmentKind.VIDEO
            mime.startsWith("audio/") || name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".wav", ignoreCase = true) || name.endsWith(".m4a", ignoreCase = true) -> AttachmentKind.AUDIO
            mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> AttachmentKind.PDF
            mime.startsWith("text/") || mime == "application/json" || name.endsWith(".txt", ignoreCase = true) || name.endsWith(".json", ignoreCase = true) -> AttachmentKind.TEXT
            else -> AttachmentKind.FILE
        }

        val pageCount = if (kind == AttachmentKind.PDF) {
            AttachmentProcessor.getPdfPageCount(context, uri)
        } else null

        onAddAttachment(
            AttachmentItem(
                id = "file_${System.currentTimeMillis()}",
                kind = kind,
                uri = uri,
                mime = mime,
                name = name,
                size = size,
                pageCount = pageCount
            )
        )
    }

    // Gallery Picker
    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { handleSelectedUri(it) }
    }

    val openDocFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleSelectedUri(it) }
    }

    fun launchGallery() {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            pickVisualMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            openDocFallbackLauncher.launch(arrayOf("image/*"))
        }
    }

    fun launchVideo() {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            pickVisualMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        } else {
            openDocFallbackLauncher.launch(arrayOf("video/*"))
        }
    }

    fun launchAudio() {
        openDocFallbackLauncher.launch(arrayOf("audio/*"))
    }

    // Files Picker
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleSelectedUri(it) }
    }

    fun launchFiles() {
        openDocumentLauncher.launch(arrayOf("image/*", "video/*", "audio/*", "application/pdf", "text/*", "application/json"))
    }

    // Clipboard Reader (explicit tap only)
    fun handlePasteClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) {
                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                return
            }

            val description = clip.description
            val isSensitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
            } else false

            if (isSensitive) {
                Toast.makeText(context, "Clipboard content is protected — paste manually", Toast.LENGTH_SHORT).show()
                return
            }

            val item = clip.getItemAt(0)
            val text = item.coerceToText(context)?.toString()
            if (text.isNullOrBlank()) {
                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                return
            }

            val attachDir = File(context.cacheDir, "attachments")
            if (!attachDir.exists()) attachDir.mkdirs()
            val textFile = File(attachDir, "clipboard_${System.currentTimeMillis()}.txt")
            textFile.writeText(text)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", textFile)

            onAddAttachment(
                AttachmentItem(
                    id = "clip_${System.currentTimeMillis()}",
                    kind = AttachmentKind.TEXT,
                    uri = uri,
                    mime = "text/plain",
                    name = "Pasted text.txt",
                    size = textFile.length()
                )
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Clipboard content is protected — paste manually", Toast.LENGTH_SHORT).show()
        }
    }

    val suggestionChips = when (uiState.selectedLanguage) {
        "hi" -> listOf(
            "राहुल को कॉल करो",
            "कैमरा खोलो",
            "यूट्यूब पर गाने चलाओ",
            "सेटिंग्स खोलो"
        )
        "zh" -> listOf(
            "打开 相机",
            "呼叫 张伟",
            "在YouTube上播放 周杰伦",
            "打开 设置"
        )
        "zh-TW" -> listOf(
            "打開 相機",
            "打電話給 小明",
            "在YouTube上播放 音樂",
            "打開 設定"
        )
        "ja" -> listOf(
            "カメラ を開いて",
            "田中 に電話して",
            "YouTubeで ジャズ を再生",
            "設定 を開いて"
        )
        "ko" -> listOf(
            "카메라 열어줘",
            "민수 에게 전화해",
            "유튜브에서 음악 틀어줘",
            "설정 열어줘"
        )
        else -> listOf(
            "Organize my downloads",
            "Open YouTube",
            "Call Alex",
            "Play jazz music",
            "Open Camera",
            "Set a 15 min timer"
        )
    }

    LaunchedEffect(uiState.isListening) {
        if (uiState.isListening) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Clear Chat & History?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This will delete all previous messages and transcripts for a fresh start with Kuchu Puchu.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearConversation?.invoke()
                        onClearHistory()
                    }
                ) {
                    Text(
                        text = "Clear All",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = greetingText,
                    fontFamily = com.example.ui.theme.CuteDisplay,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable {
                        val next = if (uiState.activeEngine == "OFFLINE") "ONLINE" else "OFFLINE"
                        onSetEngine(next)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isListening) MaterialTheme.colorScheme.primary else NovaGreen
                                )
                        )
                        Text(
                            text = when {
                                uiState.isListening -> "Listening..."
                                uiState.isExecuting -> "Processing..."
                                uiState.activeEngine == "OFFLINE" -> "Offline AI • Tap to switch"
                                else -> "Cloud ${when (uiState.onlineProvider) { "OPENAI" -> "ChatGPT"; "ANTHROPIC" -> "Claude"; else -> "Gemini" }} • Tap to switch"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Top-right actions: Theme toggle + System Trace + Clear History
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top-bar Theme toggle button (Light / Dark mode) with smooth fade animation
                IconButton(
                    onClick = { onToggleTheme?.invoke(isDark) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            CircleShape
                        )
                        .testTag("top_bar_theme_toggle_button")
                ) {
                    AnimatedContent(
                        targetState = isDark,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.75f, animationSpec = tween(300)))
                                .togetherWith(fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.75f, animationSpec = tween(200)))
                        },
                        label = "theme_toggle_icon_fade"
                    ) { currentDark ->
                        Icon(
                            imageVector = if (currentDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (currentDark) "Switch to Light Mode" else "Switch to Dark Mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // System Trace & Diagnostics Overlay Button
                IconButton(
                    onClick = { isSystemTraceOpen = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            CircleShape
                        )
                        .testTag("top_bar_system_trace_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = "System Trace & Diagnostics",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (historyList.isNotEmpty() || uiState.chatHistory.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                CircleShape
                            )
                            .testTag("clear_conversation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Clear conversation",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Auto Engine Fallback Banner (Trace)
        val downloadingModel = modelsList.firstOrNull { it.isDownloading }
        val hasAnyModelReady = modelsList.any { it.isDownloaded }

        if (uiState.activeEngine == "OFFLINE") {
            if (downloadingModel != null) {
                val percent = (downloadingModel.downloadProgress * 100).toInt()
                val downloadedMB = downloadingModel.downloadedBytes / (1024 * 1024)
                val totalMB = (downloadingModel.fileSizeBytes / (1024 * 1024)).coerceAtLeast(1)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clickable { onNavigateToSettings?.invoke() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadingModel.downloadProgress },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Downloading ${downloadingModel.name}: $percent%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$downloadedMB MB / $totalMB MB • Tap to manage in Models",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (!hasAnyModelReady) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Offline mode: No model loaded",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onSetEngine("ONLINE") },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Use Online", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { onNavigateToSettings?.invoke() },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Get Model", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        uiState.activeFallbackMessage?.let { fallbackMsg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .testTag("fallback_trace_banner"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LocalNovaCustomColors.current.goldLabel)
                        )
                        Text(
                            text = fallbackMsg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onDismissFallbackMessage?.invoke() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Center Feed / Workspace
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Mic Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HeroMicButton(
                        isListening = uiState.isListening,
                        onClick = onToggleMic,
                        modifier = Modifier.testTag("hero_mic_button")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (uiState.isListening && uiState.partialTranscript.isNotBlank()) {
                            "\"${uiState.partialTranscript}\""
                        } else {
                            uiState.statusText
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            // Waiting on LLM / Active Streaming or Processing Card
            if (uiState.isStreaming) {
                item {
                    com.example.ui.components.StreamingAnswerCard(
                        streamingText = uiState.streamingText,
                        engineName = uiState.streamingEngineName,
                        onStopStreaming = { (onStopStreaming ?: onCancelRequest)() }
                    )
                }
            } else if (uiState.isExecuting) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                RoundedCornerShape(16.dp)
                            )
                            .testTag("active_processing_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = uiState.statusText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = onCancelRequest,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("cancel_llm_request_button")
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Permanent Microphone Denial Card
            if (uiState.isMicPermanentlyDenied) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                RoundedCornerShape(16.dp)
                            )
                            .testTag("permanent_denial_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "PERMISSIONS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.08.em,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Voice is disabled",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Allow microphone access in Settings to use voice commands.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                    context.startActivity(intent)
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("open_settings_button"),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Text(
                                    text = "Open Settings",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Confirmation Card (when confirmation is pending)
            if (uiState.pendingConfirmation != null) {
                item {
                    ConfirmationCard(
                        confirmation = uiState.pendingConfirmation,
                        onConfirm = onConfirmAction,
                        onCancel = onCancelAction
                    )
                }
            }

            // Storage Permission Card
            if (uiState.showStoragePermissionExplanation) {
                item {
                    StoragePermissionCard(
                        isPermanentlyDenied = uiState.isStoragePermanentlyDenied,
                        onRequestAccess = { onRequestStorageAccess?.invoke() },
                        onDismiss = { onDismissStoragePermission?.invoke() }
                    )
                }
            }

            // Downloads Organizer Plan Confirmation Card
            if (uiState.pendingOrganizePlan != null) {
                item {
                    DownloadsPlanCard(
                        plan = uiState.pendingOrganizePlan!!,
                        onRuleToggle = { ruleId, isChecked -> onToggleOrganizeRule?.invoke(ruleId, isChecked) },
                        onExecute = { onExecuteOrganizePlan?.invoke() },
                        onCancel = { onCancelOrganizePlan?.invoke() }
                    )
                }
            }

            // Downloads Organizer Progress Card
            if (uiState.organizeProgress != null) {
                item {
                    DownloadsProgressCard(
                        progressState = uiState.organizeProgress!!,
                        onCancel = { onCancelOrganizeProgress?.invoke() }
                    )
                }
            }

            // Downloads Organizer Summary Card
            if (uiState.organizeSummary != null) {
                item {
                    DownloadsSummaryCard(
                        summaryState = uiState.organizeSummary!!,
                        onUndo = { onUndoOrganizePlan?.invoke(uiState.organizeSummary!!.planId) },
                        onDismiss = { onDismissOrganizeSummary?.invoke() }
                    )
                }
            }

            // Image-Unsupported Recovery Card
            if (uiState.imageUnsupportedRecovery != null) {
                item {
                    com.example.ui.components.ImageUnsupportedRecoveryCard(
                        recoveryState = uiState.imageUnsupportedRecovery,
                        onSwitchModelClick = { onOpenVisionModelSwitch?.invoke() },
                        onRemoveAttachments = { onRemoveAttachmentsAndDismissRecovery?.invoke() },
                        onDismiss = { onDismissImageUnsupportedRecovery?.invoke() }
                    )
                }
            }

            // Clean ChatGPT / Gemini Style Response Area
            if (historyList.isNotEmpty() && !uiState.isListening) {
                item(key = historyList.first().id) {
                    ModernChatBubble(
                        item = historyList.first(),
                        onRetry = {
                            val first = historyList.first()
                            onRetryItem?.invoke(first) ?: onRetryCommand?.invoke()
                        },
                        onSpeak = onSpeakText
                    )
                }
            }

            // Suggestion chips: surfaceVariant pills, shown only before first-ever command
            if (historyList.isEmpty()) {
                item {
                    // Sweet & Cute Welcome Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🐾",
                                    fontSize = 24.sp
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Kuchu Puchu is ready!",
                                    fontFamily = com.example.ui.theme.CuteDisplay,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Ask me anything, execute device actions, or switch between Gemini, ChatGPT, Claude & Offline AI!",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "SUGGESTIONS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.08.em,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestionChips.forEach { prompt ->
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val scale = if (isPressed) 0.96f else 1f

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scale(scale)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            RoundedCornerShape(24.dp)
                                        )
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) { onSubmitManualText(prompt) }
                                        .padding(horizontal = 18.dp, vertical = 14.dp)
                                ) {
                                    Text(
                                        text = prompt,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (historyList.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PREVIOUS CONVERSATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.08.em,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { showClearConfirmDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear Chat",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // History Conversation Feed
                items(historyList.drop(1), key = { it.id }) { item ->
                    ModernChatBubble(
                        item = item,
                        onRetry = { onRetryItem?.invoke(item) ?: onRetryCommand?.invoke() },
                        onSpeak = onSpeakText
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Offline Engine Warning Banner (when attachments are present but offline engine is active)
        val hasValidAttachments = uiState.attachments.any { !it.isInlineError && it.errorMessage == null }
        if (uiState.activeEngine == "OFFLINE" && hasValidAttachments) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Images and files need an online engine — switch in Settings",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { onSetEngine("ONLINE") },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("switch_to_online_button"),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = "Switch",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Attachment chips row above the input field
        AttachmentChipsRow(
            attachments = uiState.attachments,
            onRemove = onRemoveAttachment
        )

        // Command Input: Compact sleek bar ("+" button -> text field -> send icon)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "+" Button to open Attachment Bottom Sheet
                IconButton(
                    onClick = onOpenAttachmentSheet,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("add_attachment_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add attachment",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isBlank()) {
                        Text(
                            text = uiState.inputPlaceholder,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.5.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() || uiState.attachments.any { !it.isInlineError }) {
                                    onSubmitManualText(inputText.trim())
                                    inputText = ""
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("command_text_input")
                    )
                }

                val canSend = inputText.isNotBlank() || uiState.attachments.any { !it.isInlineError }
                IconButton(
                    onClick = {
                        if (canSend) {
                            onSubmitManualText(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send",
                        tint = if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Attachment Picker Bottom Sheet
    if (uiState.isAttachmentSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        AttachmentPickerBottomSheet(
            sheetState = sheetState,
            onDismiss = onCloseAttachmentSheet,
            onTakePhoto = { launchCamera() },
            onChooseImage = { launchGallery() },
            onChooseVideo = { launchVideo() },
            onChooseAudio = { launchAudio() },
            onChooseFile = { launchFiles() },
            onPasteClipboard = { handlePasteClipboard() }
        )
    }

    // Vision-capable Model Switch Bottom Sheet
    if (uiState.imageUnsupportedRecovery?.showSwitchSheet == true) {
        com.example.ui.components.VisionModelSwitchSheet(
            usableModels = usableVisionModels,
            onSelectModel = { model ->
                onSelectVisionModel?.invoke(model)
            },
            onDismiss = {
                onDismissVisionModelSwitch?.invoke()
            },
            onNavigateToSettings = {
                onDismissVisionModelSwitch?.invoke()
                onNavigateToSettings?.invoke()
            }
        )
    }

    // First-Run Onboarding Dialog
    if (uiState.showOnboarding) {
        com.example.ui.components.FirstRunOnboardingDialog(
            selectedLanguage = uiState.selectedLanguage,
            onSelectLanguage = { lang -> onSetLanguage?.invoke(lang) },
            onDismissOnboarding = { onDismissOnboarding?.invoke() },
            onNavigateToModels = {
                onDismissOnboarding?.invoke()
                onNavigateToModels?.invoke()
            }
        )
    }

    // System Trace & Real-time Resource Diagnostics Overlay
    if (isSystemTraceOpen) {
        com.example.ui.components.SystemTraceBottomSheet(
            onDismiss = { isSystemTraceOpen = false }
        )
    }
}

/**
 * 72dp Hero Mic Button:
 * Idle: surfaceVariant circle, primary-color icon, hairline border.
 * Listening: accent circle + expanding rings (1s pulse) + haptic feedback.
 */
@Composable
fun HeroMicButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (isPressed) 0.96f else 1f

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_rings")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_scale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_alpha"
    )

    Box(
        modifier = modifier
            .size(108.dp)
            .scale(pressScale),
        contentAlignment = Alignment.Center
    ) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(ringScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha))
            )
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    BorderStroke(
                        1.dp,
                        if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = if (isListening) "Stop Listening" else "Start Voice Command",
                tint = if (isListening) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private val VOICE_TRANSCRIPT_REGEX = Regex("""VOICE:\s*"([^"]+)"(?:\s*\([\d.]+\))?""")

/**
 * Editorial chat panel turn:
 * "YOU" micro-label above right-aligned user bubble.
 * "NOVA" micro-label above left-aligned result card.
 * Displays attachment badge (+1 photo marker) if present.
 */
@Composable
fun EditorialChatTurn(
    item: CommandHistoryEntity,
    isDark: Boolean,
    onRetry: (() -> Unit)? = null,
    onRetryItem: ((CommandHistoryEntity) -> Unit)? = null,
    onSpeak: ((String) -> Unit)? = null,
    onOpenSystemTrace: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cleanTranscript = remember(item.transcript) {
        val match = VOICE_TRANSCRIPT_REGEX.find(item.transcript)
        match?.groupValues?.get(1) ?: item.transcript
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // User turn
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 3.dp, end = 4.dp)
            ) {
                if (!item.attachmentBadge.isNullOrBlank()) {
                    Text(
                        text = item.attachmentBadge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "YOU",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.08.em,
                    color = LocalNovaCustomColors.current.goldLabel
                )
            }

            // Right-aligned user bubble
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(LocalNovaCustomColors.current.userBubble)
                    .border(
                        BorderStroke(1.dp, LocalNovaCustomColors.current.userBubbleBorder),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = cleanTranscript,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Assistant turn
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "NOVA",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp, start = 4.dp)
            )

            // Left-aligned result card with message action row (copy/share/retry/speak)
            HistoryItemCard(
                item = item,
                onRetry = onRetry,
                onRetryItem = onRetryItem,
                onSpeak = onSpeak,
                onOpenSystemTrace = onOpenSystemTrace
            )
        }
    }
}
