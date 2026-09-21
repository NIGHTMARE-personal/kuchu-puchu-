package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.NovaUiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiEngineAndKeysCard(
    uiState: NovaUiState,
    onSetEngine: (String) -> Unit,
    onSetOnlineProvider: (String) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onSaveOpenaiKey: (String) -> Unit,
    onSaveAnthropicKey: (String) -> Unit,
    onSetGeminiModel: (String) -> Unit = {},
    onSetOpenaiModel: (String) -> Unit = {},
    onSetAnthropicModel: (String) -> Unit = {},
    onSaveHuggingFaceToken: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var geminiKeyInput by remember(uiState.geminiKey) { mutableStateOf(uiState.geminiKey) }
    var openaiKeyInput by remember(uiState.openaiKey) { mutableStateOf(uiState.openaiKey) }
    var anthropicKeyInput by remember(uiState.anthropicKey) { mutableStateOf(uiState.anthropicKey) }
    var huggingFaceTokenInput by remember(uiState.huggingFaceToken) { mutableStateOf(uiState.huggingFaceToken) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var savedFeedbackMessage by remember { mutableStateOf<String?>(null) }

    var customModelInput by remember(uiState.onlineProvider, uiState.geminiModel, uiState.openaiModel, uiState.anthropicModel) {
        val currentModel = when (uiState.onlineProvider) {
            "GEMINI" -> uiState.geminiModel
            "OPENAI" -> uiState.openaiModel
            "ANTHROPIC" -> uiState.anthropicModel
            else -> uiState.geminiModel
        }
        mutableStateOf(if (currentModel == "auto") "" else currentModel)
    }

    LaunchedEffect(savedFeedbackMessage) {
        if (savedFeedbackMessage != null) {
            delay(2500)
            savedFeedbackMessage = null
        }
    }

    val providers = remember {
        listOf(
            Triple("GEMINI", "Gemini", "Google Gemini (Auto-Fallback)"),
            Triple("OPENAI", "OpenAI", "GPT-4o Mini (Auto-Fallback)"),
            Triple("ANTHROPIC", "Claude", "Claude (Auto-Fallback)")
        )
    }

    val isOnline = uiState.activeEngine == "ONLINE"
    val isOffline = uiState.activeEngine == "OFFLINE"

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "AI ENGINE & CLOUD MODELS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Engine Selector: ONLINE vs OFFLINE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Online Option
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSetEngine("ONLINE") }
                    .border(
                        BorderStroke(
                            if (isOnline) 2.dp else 1.dp,
                            if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOnline) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cloud,
                            contentDescription = null,
                            tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Online",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Cloud models",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Offline Option
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSetEngine("OFFLINE") }
                    .border(
                        BorderStroke(
                            if (isOffline) 2.dp else 1.dp,
                            if (isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOffline) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOffline) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = if (isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "On-Device",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Local models",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Cloud Model Provider, Version Selector & API Key Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Cloud Model Provider",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val currentModelName = when (uiState.onlineProvider) {
                        "GEMINI" -> if (uiState.geminiModel == "auto") "Auto-detect" else uiState.geminiModel
                        "OPENAI" -> if (uiState.openaiModel == "auto") "Auto-detect" else uiState.openaiModel
                        "ANTHROPIC" -> if (uiState.anthropicModel == "auto") "Auto-detect" else uiState.anthropicModel
                        else -> "Auto-detect"
                    }
                    Text(
                        text = currentModelName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Provider Choice Pills (Gemini, OpenAI, Claude)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    providers.forEach { (id, label, _) ->
                        val isSelected = uiState.onlineProvider == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline
                                    ),
                                    CircleShape
                                )
                                .clickable { onSetOnlineProvider(id) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val activeKey = when (uiState.onlineProvider) {
                    "GEMINI" -> geminiKeyInput
                    "OPENAI" -> openaiKeyInput
                    "ANTHROPIC" -> anthropicKeyInput
                    else -> geminiKeyInput
                }

                val providerName = providers.firstOrNull { it.first == uiState.onlineProvider }?.second ?: "Gemini"

                Text(
                    text = "$providerName API Key",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = activeKey,
                    onValueChange = { newKey ->
                        when (uiState.onlineProvider) {
                            "GEMINI" -> geminiKeyInput = newKey
                            "OPENAI" -> openaiKeyInput = newKey
                            "ANTHROPIC" -> anthropicKeyInput = newKey
                        }
                    },
                    placeholder = {
                        Text(
                            "Enter $providerName key...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Toggle Visibility",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input_${uiState.onlineProvider}"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Stored securely on device",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            when (uiState.onlineProvider) {
                                "GEMINI" -> onSaveGeminiKey(geminiKeyInput.trim())
                                "OPENAI" -> onSaveOpenaiKey(openaiKeyInput.trim())
                                "ANTHROPIC" -> onSaveAnthropicKey(anthropicKeyInput.trim())
                            }
                            savedFeedbackMessage = "$providerName key saved"
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("save_api_key_button"),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text("Save Key", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (savedFeedbackMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = savedFeedbackMessage!!,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(14.dp))

                // Model Version & Endpoint Section (No hardcoding)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Model Version / Endpoint",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select a specific model or keep Auto-detect. Auto-detect queries available models for your key and automatically cascades through fallback models if deprecated.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                val currentSelectedModel = when (uiState.onlineProvider) {
                    "GEMINI" -> uiState.geminiModel
                    "OPENAI" -> uiState.openaiModel
                    "ANTHROPIC" -> uiState.anthropicModel
                    else -> uiState.geminiModel
                }

                // Preset options based on active provider
                val modelPresets = when (uiState.onlineProvider) {
                    "GEMINI" -> listOf(
                        "auto" to "Auto (Dynamic)",
                        "gemini-3.6-flash" to "3.6 Flash",
                        "gemini-3.5-flash" to "3.5 Flash",
                        "gemini-flash-latest" to "Flash Latest",
                        "gemini-3.7-flash" to "3.7 Flash",
                        "gemini-3.8-flash" to "3.8 Flash"
                    )
                    "OPENAI" -> listOf(
                        "auto" to "Auto (Dynamic)",
                        "gpt-4o-mini" to "GPT-4o Mini",
                        "gpt-4o" to "GPT-4o",
                        "chatgpt-4o-latest" to "4o Latest"
                    )
                    "ANTHROPIC" -> listOf(
                        "auto" to "Auto (Dynamic)",
                        "claude-3-5-haiku-latest" to "3.5 Haiku",
                        "claude-3-5-sonnet-latest" to "3.5 Sonnet",
                        "claude-3-7-sonnet-latest" to "3.7 Sonnet"
                    )
                    else -> emptyList()
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modelPresets.forEach { (modelVal, modelLabel) ->
                        val isPresetActive = currentSelectedModel == modelVal
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isPresetActive) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isPresetActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    ),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    when (uiState.onlineProvider) {
                                        "GEMINI" -> onSetGeminiModel(modelVal)
                                        "OPENAI" -> onSetOpenaiModel(modelVal)
                                        "ANTHROPIC" -> onSetAnthropicModel(modelVal)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = modelLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isPresetActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isPresetActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Custom model chip
                    val isCustom = modelPresets.none { it.first == currentSelectedModel }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isCustom) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isCustom) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                if (!isCustom) {
                                    val fallbackModel = if (customModelInput.isNotBlank()) customModelInput else "custom-model"
                                    when (uiState.onlineProvider) {
                                        "GEMINI" -> onSetGeminiModel(fallbackModel)
                                        "OPENAI" -> onSetOpenaiModel(fallbackModel)
                                        "ANTHROPIC" -> onSetAnthropicModel(fallbackModel)
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Custom...",
                            fontSize = 11.sp,
                            fontWeight = if (isCustom) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // If Custom is selected, show manual input field
                val isCustomActive = modelPresets.none { it.first == currentSelectedModel }
                if (isCustomActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = { customModelInput = it },
                            placeholder = { Text("e.g. gemini-3.8-flash or gpt-4o", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        Button(
                            onClick = {
                                val target = customModelInput.trim().ifBlank { "auto" }
                                when (uiState.onlineProvider) {
                                    "GEMINI" -> onSetGeminiModel(target)
                                    "OPENAI" -> onSetOpenaiModel(target)
                                    "ANTHROPIC" -> onSetAnthropicModel(target)
                                }
                                savedFeedbackMessage = "Model set to $target"
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Apply", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(14.dp))

                // Hugging Face Access Token for Gated Models (e.g. Google Gemma)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Hugging Face Access Token (Optional)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Allows direct downloading of gated models like Google Gemma without authentication errors. Leave blank if importing files manually.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = huggingFaceTokenInput,
                        onValueChange = { huggingFaceTokenInput = it },
                        placeholder = { Text("hf_...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    Button(
                        onClick = {
                            onSaveHuggingFaceToken(huggingFaceTokenInput.trim())
                            savedFeedbackMessage = "Hugging Face token saved"
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Save", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
