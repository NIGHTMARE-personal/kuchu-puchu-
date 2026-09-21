package com.example.data.registry

import com.example.data.local.SecurePreferences
import com.example.data.modelmanager.OfflineModelManager
import com.example.model.ModelCapability
import com.example.model.ModelEngineType

/**
 * Single source of truth for AI model capabilities across online providers and on-device models.
 * Drives pre-flight checks and the model switch sheet without hardcoding model lists in UI code.
 */
class CapabilityRegistry(
    private val preferences: SecurePreferences,
    private val modelManager: OfflineModelManager
) {
    /**
     * Returns all registered models (online + offline) with capability states:
     * {displayName, vision, usable}
     * usable = local model downloaded OR provider API key present.
     */
    fun getAllModels(): List<ModelCapability> {
        val list = mutableListOf<ModelCapability>()

        // Online models
        val geminiKey = preferences.geminiApiKey
        val geminiModel = preferences.geminiModel
        val geminiName = if (geminiModel.isNotBlank() && geminiModel != "auto") "Gemini ($geminiModel)" else "Gemini (Auto-detect)"
        list.add(
            ModelCapability(
                id = "GEMINI",
                displayName = geminiName,
                engineType = ModelEngineType.ONLINE_PROVIDER,
                vision = true,
                usable = geminiKey.isNotBlank(),
                description = "Multimodal reasoning & vision"
            )
        )

        val openAiKey = preferences.openaiApiKey
        val openAiModel = preferences.openaiModel
        val openAiName = if (openAiModel.isNotBlank() && openAiModel != "auto") "OpenAI ($openAiModel)" else "OpenAI (Auto-detect)"
        list.add(
            ModelCapability(
                id = "OPENAI",
                displayName = openAiName,
                engineType = ModelEngineType.ONLINE_PROVIDER,
                vision = true,
                usable = openAiKey.isNotBlank(),
                description = "Fast vision & natural language"
            )
        )

        val anthropicKey = preferences.anthropicApiKey
        val anthropicModel = preferences.anthropicModel
        val anthropicName = if (anthropicModel.isNotBlank() && anthropicModel != "auto") "Claude ($anthropicModel)" else "Claude (Auto-detect)"
        list.add(
            ModelCapability(
                id = "ANTHROPIC",
                displayName = anthropicName,
                engineType = ModelEngineType.ONLINE_PROVIDER,
                vision = true,
                usable = anthropicKey.isNotBlank(),
                description = "High precision vision analysis"
            )
        )

        // Offline models from OfflineModelManager
        modelManager.modelsState.value.forEach { item ->
            list.add(
                ModelCapability(
                    id = item.id,
                    displayName = item.name,
                    engineType = ModelEngineType.OFFLINE_LOCAL,
                    vision = item.vision, // Currently false for on-device text LLMs
                    usable = item.isDownloaded,
                    description = item.description
                )
            )
        }

        return list
    }

    /**
     * Returns the currently active model capability based on preferences.
     */
    fun getActiveModel(): ModelCapability {
        val all = getAllModels()
        return if (preferences.activeEngine == "OFFLINE") {
            val activeId = preferences.activeModelId
            all.firstOrNull { it.id == activeId && it.engineType == ModelEngineType.OFFLINE_LOCAL }
                ?: all.firstOrNull { it.engineType == ModelEngineType.OFFLINE_LOCAL }
                ?: ModelCapability(
                    id = "offline_default",
                    displayName = "Offline Engine",
                    engineType = ModelEngineType.OFFLINE_LOCAL,
                    vision = false,
                    usable = false,
                    description = "Local on-device engine"
                )
        } else {
            val provider = preferences.onlineProvider
            all.firstOrNull { it.id.equals(provider, ignoreCase = true) && it.engineType == ModelEngineType.ONLINE_PROVIDER }
                ?: all.firstOrNull { it.id == "GEMINI" }
                ?: all.first()
        }
    }

    /**
     * Returns only usable vision-capable models (vision=true AND usable=true).
     */
    fun getUsableVisionModels(): List<ModelCapability> {
        return getAllModels().filter { it.vision && it.usable }
    }
}
