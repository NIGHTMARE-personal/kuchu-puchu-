package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.NavTab
import com.example.ui.NovaAssistantViewModel
import com.example.ui.components.PermissionExplanationDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ModelsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmartNotesScreen
import com.example.ui.theme.NovaAccent
import com.example.ui.theme.NovaAssistantTheme
import com.example.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: NovaAssistantViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val themeMode = remember(uiState.themeMode) {
                ThemeMode.fromCode(uiState.themeMode)
            }
            NovaAssistantTheme(themeMode = themeMode) {
                NovaAssistantApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun NovaAssistantApp(
    viewModel: NovaAssistantViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val modelsList by viewModel.modelsList.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // isGranted = true -> start listening IMMEDIATELY (no second tap needed)
            viewModel.onMicPermissionGranted(startListeningImmediately = true)
        } else {
            val showRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: false

            if (showRationale) {
                // Denied once -> status: "Microphone permission is needed for voice commands", keep mic enabled for retry
                viewModel.onMicPermissionDeniedOnce()
            } else {
                // Permanently denied -> show card: "Voice is disabled — allow microphone access in Settings"
                viewModel.onMicPermissionPermanentlyDenied()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasRecordAudio = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasRecordAudio && uiState.isMicPermanentlyDenied) {
                    viewModel.onMicPermissionRestored()
                }
                viewModel.onResumeCheckStoragePermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onResumeCheckStoragePermission()
        } else {
            val showRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } ?: false
            if (!showRationale) {
                viewModel.preferences.isStoragePermanentlyDenied = true
            }
        }
    }

    val onRequestStorageAccess: () -> Unit = {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallback = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(fallback)
            }
        } else {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val onMicClick: () -> Unit = {
        if (uiState.isMicPermanentlyDenied) {
            // From then on, mic tap goes straight to this card — never call launch() again (it no-ops)
            viewModel.showPermanentMicDenialCard()
        } else {
            val hasRecordAudio = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasRecordAudio) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                viewModel.toggleListening()
            }
        }
    }

    val onStartMicTest: () -> Unit = {
        if (uiState.isMicPermanentlyDenied) {
            viewModel.showPermanentMicDenialCard()
        } else {
            val hasRecordAudio = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasRecordAudio) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                viewModel.startMicTest()
            }
        }
    }

    val outlineColor = MaterialTheme.colorScheme.outline
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .testTag("bottom_navigation_bar")
                    .drawBehind {
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
            ) {
                NavigationBarItem(
                    selected = uiState.currentTab == NavTab.HOME,
                    onClick = { viewModel.selectTab(NavTab.HOME) },
                    icon = {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = "Assistant",
                            modifier = Modifier.testTag("nav_item_home")
                        )
                    },
                    label = {
                        Text(
                            "Assistant",
                            fontSize = 11.sp,
                            fontWeight = if (uiState.currentTab == NavTab.HOME) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentTab == NavTab.NOTES,
                    onClick = { viewModel.selectTab(NavTab.NOTES) },
                    icon = {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = "Notes",
                            modifier = Modifier.testTag("nav_item_notes")
                        )
                    },
                    label = {
                        Text(
                            "Notes",
                            fontSize = 11.sp,
                            fontWeight = if (uiState.currentTab == NavTab.NOTES) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentTab == NavTab.PROFILE,
                    onClick = { viewModel.selectTab(NavTab.PROFILE) },
                    icon = {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = "Profile",
                            modifier = Modifier.testTag("nav_item_profile")
                        )
                    },
                    label = {
                        Text(
                            "Profile",
                            fontSize = 11.sp,
                            fontWeight = if (uiState.currentTab == NavTab.PROFILE) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentTab == NavTab.MODELS,
                    onClick = { viewModel.selectTab(NavTab.MODELS) },
                    icon = {
                        Icon(
                            Icons.Outlined.Memory,
                            contentDescription = "Models",
                            modifier = Modifier.testTag("nav_item_models")
                        )
                    },
                    label = {
                        Text(
                            "Models",
                            fontSize = 11.sp,
                            fontWeight = if (uiState.currentTab == NavTab.MODELS) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentTab == NavTab.SETTINGS,
                    onClick = { viewModel.selectTab(NavTab.SETTINGS) },
                    icon = {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.testTag("nav_item_settings")
                        )
                    },
                    label = {
                        Text(
                            "Settings",
                            fontSize = 11.sp,
                            fontWeight = if (uiState.currentTab == NavTab.SETTINGS) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                NavTab.HOME -> {
                    HomeScreen(
                        uiState = uiState,
                        historyList = historyList,
                        modelsList = modelsList,
                        onToggleMic = onMicClick,
                        onSubmitManualText = { text -> viewModel.processTranscript(text) },
                        onConfirmAction = { viewModel.confirmPendingAction() },
                        onCancelAction = { viewModel.cancelPendingAction() },
                        onClearHistory = { viewModel.clearHistory() },
                        onOpenAttachmentSheet = { viewModel.openAttachmentSheet() },
                        onCloseAttachmentSheet = { viewModel.closeAttachmentSheet() },
                        onAddAttachment = { attachment -> viewModel.addAttachment(attachment) },
                        onAddInlineErrorChip = { msg -> viewModel.addInlineErrorChip(msg) },
                        onRemoveAttachment = { id -> viewModel.removeAttachment(id) },
                        onCancelRequest = { viewModel.cancelLlmRequest() },
                        onRetryCommand = { viewModel.retryLastCommand() },
                        onSetInputPlaceholder = { placeholder -> viewModel.setInputPlaceholder(placeholder) },
                        onSetEngine = { engine -> viewModel.setEngine(engine) },
                        onRetryItem = { item -> viewModel.retryCommand(item) },
                        onOpenVisionModelSwitch = { viewModel.openVisionModelSwitchSheet() },
                        onDismissVisionModelSwitch = { viewModel.dismissVisionModelSwitchSheet() },
                        onSelectVisionModel = { model -> viewModel.selectVisionModel(model) },
                        onRemoveAttachmentsAndDismissRecovery = { viewModel.removeAttachmentsAndDismissRecovery() },
                        onDismissImageUnsupportedRecovery = { viewModel.dismissImageUnsupportedRecovery() },
                        onNavigateToSettings = { viewModel.selectTab(NavTab.MODELS) },
                        usableVisionModels = viewModel.capabilityRegistry.getUsableVisionModels(),
                        onToggleOrganizeRule = { ruleId, isChecked -> viewModel.toggleOrganizeRule(ruleId, isChecked) },
                        onExecuteOrganizePlan = { viewModel.executeOrganizePlan() },
                        onCancelOrganizePlan = { viewModel.cancelOrganizePlan() },
                        onCancelOrganizeProgress = { viewModel.cancelOrganizeExecution() },
                        onUndoOrganizePlan = { planId -> viewModel.undoOrganizePlan(planId) },
                        onDismissOrganizeSummary = { viewModel.dismissOrganizeSummary() },
                        onRequestStorageAccess = onRequestStorageAccess,
                        onDismissStoragePermission = { viewModel.dismissStoragePermissionPrompt() },
                        onStopStreaming = { viewModel.stopStreaming() },
                        onClearConversation = {
                            viewModel.clearConversation()
                            viewModel.clearHistory()
                        },
                        onDismissOnboarding = { viewModel.dismissOnboarding() },
                        onDismissFallbackMessage = { viewModel.clearFallbackMessage() },
                        onSetLanguage = { lang -> viewModel.setSelectedLanguage(lang) },
                        onNavigateToModels = { viewModel.selectTab(NavTab.MODELS) },
                        onSpeakText = { text -> viewModel.toggleSpeakText(text) },
                        onToggleTheme = { isDark -> viewModel.toggleThemeMode(isDark) }
                    )
                }
                NavTab.NOTES -> {
                    SmartNotesScreen(
                        noteRepository = viewModel.noteRepository,
                        geminiApiKey = uiState.geminiKey
                    )
                }
                NavTab.PROFILE -> {
                    ProfileScreen(
                        userProfileRepository = viewModel.userProfileRepository
                    )
                }
                NavTab.MODELS -> {
                    ModelsScreen(
                        uiState = uiState,
                        models = modelsList,
                        onToggleOfflineEngine = { enable ->
                            viewModel.setEngine(if (enable) "OFFLINE" else "ONLINE")
                        },
                        onToggleOfflineSpeech = { enable ->
                            viewModel.toggleOfflineSpeech(enable)
                        },
                        onDownloadModel = { modelId -> viewModel.startModelDownload(modelId) },
                        onCancelDownload = { modelId -> viewModel.cancelModelDownload(modelId) },
                        onDeleteModel = { modelId -> viewModel.deleteModel(modelId) },
                        onSetActiveModel = { modelId -> viewModel.setActiveModel(modelId) },
                        onImportModel = { uri -> viewModel.importModel(uri) },
                        onImportModelForSlot = { slotId, uri -> viewModel.importModelForSlot(slotId, uri) },
                        onDownloadCustomModel = { url, name -> viewModel.downloadCustomModel(url, name) },
                        onVerifyModel = { modelId -> viewModel.verifyModel(modelId) },
                        getModelDownloadInfo = { modelId -> viewModel.getModelDownloadInfo(modelId) },
                        onSetEngine = { engine -> viewModel.setEngine(engine) },
                        onSetOnlineProvider = { provider -> viewModel.setOnlineProvider(provider) },
                        onSaveGeminiKey = { key -> viewModel.setGeminiKey(key) },
                        onSaveOpenaiKey = { key -> viewModel.setOpenaiKey(key) },
                        onSaveAnthropicKey = { key -> viewModel.setAnthropicKey(key) },
                        onSaveHuggingFaceToken = { token -> viewModel.setHuggingFaceToken(token) },
                        onSetGeminiModel = { model -> viewModel.setGeminiModel(model) },
                        onSetOpenaiModel = { model -> viewModel.setOpenaiModel(model) },
                        onSetAnthropicModel = { model -> viewModel.setAnthropicModel(model) },
                        onScanStorageForModels = { viewModel.scanAndRestoreModelsFromStorage() }
                    )
                }
                NavTab.SETTINGS -> {
                    SettingsScreen(
                        uiState = uiState,
                        onSetEngine = { engine -> viewModel.setEngine(engine) },
                        onSetOnlineProvider = { provider -> viewModel.setOnlineProvider(provider) },
                        onSaveGeminiKey = { key -> viewModel.setGeminiKey(key) },
                        onSaveOpenaiKey = { key -> viewModel.setOpenaiKey(key) },
                        onSaveAnthropicKey = { key -> viewModel.setAnthropicKey(key) },
                        onSetAutoCall = { autoCall -> viewModel.setAutoCall(autoCall) },
                        onSetFastRouter = { fastRouter -> viewModel.setFastRouter(fastRouter) },
                        onSetLanguage = { lang -> viewModel.setSelectedLanguage(lang) },
                        onStartMicTest = onStartMicTest,
                        onStopMicTest = { viewModel.stopMicTest() },
                        onRequestPermission = { perm -> permissionLauncher.launch(perm) },
                        onClearHistory = { viewModel.clearHistory() },
                        onToggleVoiceFeedback = { viewModel.toggleVoiceFeedback(it) },
                        onSelectTtsVoice = { voiceId -> viewModel.setSelectedTtsVoice(voiceId) },
                        onSetTtsSpeed = { speed -> viewModel.setTtsSpeed(speed) },
                        onSetTtsPitch = { pitch -> viewModel.setTtsPitch(pitch) },
                        onPreviewTtsVoice = { voiceId -> viewModel.previewTtsVoice(voiceId) },
                        onStopTtsVoicePreview = { viewModel.stopTtsVoicePreview() },
                        onDownloadTtsVoicePack = { voiceId -> viewModel.downloadTtsVoicePack(voiceId) },
                        onDeleteTtsVoicePack = { voiceId -> viewModel.deleteTtsVoicePack(voiceId) },
                        onToggleHaptics = { viewModel.setHapticsEnabled(it) },
                        onToggleStreamingAnimation = { viewModel.setStreamingAnimationEnabled(it) },
                        onToggleHighFidelityAnimations = { viewModel.setHighFidelityAnimationsEnabled(it) },
                        onSetImageGenAspectRatio = { viewModel.setImageGenAspectRatio(it) },
                        onToggleAutoCopy = { viewModel.setAutoCopyEnabled(it) },
                        onNavigateToModels = { viewModel.selectTab(NavTab.MODELS) },
                        onSetEngineMode = { mode -> viewModel.setEngineMode(mode) },
                        onSetThemeMode = { mode -> viewModel.setThemeMode(mode) }
                    )
                }
            }

            // Permission Explanation Dialog if needed
            PermissionExplanationDialog(
                request = uiState.pendingPermissionRequest,
                onGrant = { perm ->
                    permissionLauncher.launch(perm)
                },
                onDismiss = {
                    viewModel.dismissPermissionDialog()
                }
            )
        }
    }
}
