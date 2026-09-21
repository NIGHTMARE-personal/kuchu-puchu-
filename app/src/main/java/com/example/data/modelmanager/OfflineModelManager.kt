package com.example.data.modelmanager

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.local.SecurePreferences
import com.example.model.AiModelItem
import com.example.model.ModelCategory
import com.example.model.VerificationState
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Configuration for automated test phone environment setup.
 *
 * When developing or testing, device data wipes/reinstalls wipe internal sandbox storage.
 * When AUTO_RESTORE_OR_DOWNLOAD_FOR_TESTING is true:
 * 1. The app auto-checks persistent phone storage (e.g. /sdcard/Download/NovaModels) for existing model weights.
 *    If found, it auto-restores in 1 second without waiting for redownloads.
 * 2. If no file is on device, it auto-starts background download of TinyLlama 1.1B on first launch.
 *
 * IN SHIPPING / RELEASE TIME:
 * Simply set AUTO_RESTORE_OR_DOWNLOAD_FOR_TESTING = false or delete this object.
 */
object TestingModelConfig {
    const val AUTO_RESTORE_OR_DOWNLOAD_FOR_TESTING = true
    const val DEFAULT_TESTING_MODEL_ID = "tinyllama_1b"
}

data class ModelDownloadInfo(
    val modelId: String,
    val modelName: String,
    val downloadUrl: String,
    val expectedFileName: String,
    val expectedSize: String,
    val requiresHfToken: Boolean,
    val instructions: String
)

class OfflineModelManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val preferences: SecurePreferences? = null
) {
    val modelsDir: File by lazy {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    var onModelDownloadedListener: ((String) -> Unit)? = null

    // Base supported MediaPipe on-device architectures
    private val standardModels = listOf(
        AiModelItem(
            id = "tinyllama_1b",
            name = "TinyLlama 1.1B (INT4)",
            description = "Ultra-compact memory footprint (~620MB). Suitable for devices with 4GB RAM or less. Fast token generation and open community weights.",
            parameterSize = "1.1B Parameters (INT4)",
            fileSizeFormatted = "1.07 GB",
            fileSizeBytes = 1_148_331_545L,
            downloadUrl = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
            fileName = "TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
            category = ModelCategory.LLM
        ),
        AiModelItem(
            id = "phi_2",
            name = "Microsoft Phi-2 (INT4)",
            description = "Compact 2.7B reasoning model trained on textbook-quality data. Excels at coding and logic reasoning offline on device.",
            parameterSize = "2.7B Parameters (INT4)",
            fileSizeFormatted = "2.60 GB",
            fileSizeBytes = 2_786_910_080L,
            downloadUrl = "https://huggingface.co/siddhantchalke/phi2-cpu-mediapipe-llm-inference/resolve/main/phi2_cpu.bin",
            fileName = "phi2_cpu.bin",
            category = ModelCategory.LLM
        ),
        AiModelItem(
            id = "gemma_2b_cpu",
            name = "Gemma 2B IT (CPU INT4)",
            description = "Google's lightweight instruction-tuned model. Runs on CPU with MediaPipe GenAI. True on-device neural token generation.",
            parameterSize = "2.0B Parameters (INT4)",
            fileSizeFormatted = "1.25 GB",
            fileSizeBytes = 1_346_559_040L,
            downloadUrl = "https://huggingface.co/a8nova/gemma-2b-it-cpu-int4/resolve/main/gemma-2b-it-cpu-int4.bin",
            fileName = "gemma-2b-it-cpu-int4.bin",
            category = ModelCategory.LLM
        ),
        AiModelItem(
            id = "gemma_2b_gpu",
            name = "Gemma 2B IT (GPU INT4)",
            description = "Hardware accelerated GPU execution via MediaPipe OpenCL/Vulkan backend. High token throughput on compatible Adreno/Mali GPUs.",
            parameterSize = "2.0B Parameters (INT4)",
            fileSizeFormatted = "1.26 GB",
            fileSizeBytes = 1_354_301_440L,
            downloadUrl = "https://huggingface.co/vba01/gemma-2b-it-gpu-int4/resolve/main/gemma-2b-it-gpu-int4.bin",
            fileName = "gemma-2b-it-gpu-int4.bin",
            category = ModelCategory.LLM
        )
    )

    private val customModelsFile: File by lazy { File(modelsDir, "custom_models.json") }
    private val _modelsState = MutableStateFlow<List<AiModelItem>>(emptyList())
    val modelsState: StateFlow<List<AiModelItem>> = _modelsState.asStateFlow()

    init {
        cleanupStaleDummyFiles()
        refreshModelStatuses()
        checkAndAutoPrepareTestingModel()
    }

    /**
     * Remove any old dummy or corrupted files (< 10MB) so the user never sees fake model records.
     */
    private fun cleanupStaleDummyFiles() {
        try {
            modelsDir.listFiles()?.forEach { file ->
                if (file.isFile && !file.name.endsWith(".json") && file.length() < 10 * 1024 * 1024L) {
                    Log.w("OfflineModelManager", "Removing invalid/dummy file: ${file.name} (${file.length()} bytes)")
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineModelManager", "Error cleaning up stale files", e)
        }
    }

    private fun getDeviceTotalRamGb(): Double {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        } catch (e: Exception) {
            4.0
        }
    }

    private fun loadCustomModels(): List<AiModelItem> {
        val list = mutableListOf<AiModelItem>()
        if (!customModelsFile.exists()) return list
        try {
            val content = customModelsFile.readText()
            val array = JSONArray(content)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val file = File(modelsDir, obj.getString("fileName"))
                val sizeBytes = if (file.exists()) file.length() else obj.optLong("fileSizeBytes", 0L)
                val sizeMb = sizeBytes / (1024.0 * 1024.0)
                val sizeStr = if (sizeMb >= 1024) String.format("%.2f GB", sizeMb / 1024.0) else String.format("%.0f MB", sizeMb)
                list.add(
                    AiModelItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.optString("description", "Imported local model file (.bin/.task)."),
                        parameterSize = obj.optString("parameterSize", "Custom On-Device Model"),
                        fileSizeFormatted = sizeStr,
                        fileSizeBytes = sizeBytes,
                        downloadUrl = obj.optString("downloadUrl", ""),
                        fileName = obj.getString("fileName"),
                        category = ModelCategory.LLM
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("OfflineModelManager", "Error loading custom models", e)
        }
        return list
    }

    private fun saveCustomModels(models: List<AiModelItem>) {
        try {
            val array = JSONArray()
            for (m in models) {
                val obj = JSONObject()
                obj.put("id", m.id)
                obj.put("name", m.name)
                obj.put("description", m.description)
                obj.put("parameterSize", m.parameterSize)
                obj.put("fileName", m.fileName)
                obj.put("downloadUrl", m.downloadUrl)
                obj.put("fileSizeBytes", m.fileSizeBytes)
                array.put(obj)
            }
            customModelsFile.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e("OfflineModelManager", "Error saving custom models", e)
        }
    }

    fun refreshModelStatuses(activeModelId: String = "tinyllama_1b") {
        val ramGb = getDeviceTotalRamGb()
        val customList = loadCustomModels()
        val allCatalog = (customList + standardModels).distinctBy { it.id }

        val updated = allCatalog.map { item ->
            val targetFile = File(modelsDir, item.fileName)
            val exists = targetFile.exists() && targetFile.length() > 50 * 1024 * 1024L // Must be real model (>50MB)
            val currentLen = if (targetFile.exists()) targetFile.length() else 0L

            val existingState = _modelsState.value.firstOrNull { it.id == item.id }?.verificationState
                ?: if (exists) VerificationState.NOT_VERIFIED else VerificationState.NOT_VERIFIED
            val existingError = _modelsState.value.firstOrNull { it.id == item.id }?.verificationError

            val isRec = when {
                ramGb < 4.0 -> item.id == "tinyllama_1b"
                ramGb in 4.0..6.5 -> item.id == "tinyllama_1b" || item.id == "gemma_2b_cpu"
                else -> item.id == "gemma_2b_gpu" || item.id == "phi_2"
            }
            val struggle = when {
                ramGb < 4.0 -> item.id == "phi_2" || item.id == "gemma_2b_gpu"
                ramGb < 6.0 -> item.id == "phi_2"
                else -> false
            }

            val sizeMb = currentLen / (1024.0 * 1024.0)
            val formattedSize = if (exists) {
                if (sizeMb >= 1024) String.format("%.2f GB", sizeMb / 1024.0) else String.format("%.0f MB", sizeMb)
            } else {
                item.fileSizeFormatted
            }

            item.copy(
                isDownloaded = exists,
                downloadedBytes = currentLen,
                fileSizeFormatted = formattedSize,
                isRecommended = isRec,
                willStruggle = struggle,
                verificationState = if (exists) existingState else VerificationState.NOT_VERIFIED,
                verificationError = if (exists) existingError else null
            )
        }
        _modelsState.value = updated
    }

    /**
     * Verifies the model by running a real on-device token inference smoke test via MediaPipe.
     */
    fun verifyModel(modelId: String) {
        val targetItem = _modelsState.value.firstOrNull { it.id == modelId } ?: return
        val destinationFile = File(modelsDir, targetItem.fileName)

        if (!destinationFile.exists() || destinationFile.length() < 50 * 1024 * 1024L) {
            _modelsState.update { list ->
                list.map {
                    if (it.id == modelId) it.copy(
                        verificationState = VerificationState.FAILED,
                        verificationError = "Model weights missing or smaller than 50 MB"
                    ) else it
                }
            }
            return
        }

        _modelsState.update { list ->
            list.map {
                if (it.id == modelId) it.copy(
                    verificationState = VerificationState.VERIFYING,
                    verificationError = null
                ) else it
            }
        }

        scope.launch(Dispatchers.Default) {
            try {
                Log.i("OfflineModelManager", "Running on-device smoke test for ${targetItem.name} at ${destinationFile.absolutePath}")
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(destinationFile.absolutePath)
                    .setMaxTokens(16)
                    .build()

                val testInference = LlmInference.createFromOptions(context, options)
                val testResponse = testInference.generateResponse("Say ready")
                testInference.close()

                Log.i("OfflineModelManager", "Model smoke test passed! Output: $testResponse")
                _modelsState.update { list ->
                    list.map {
                        if (it.id == modelId) it.copy(
                            verificationState = VerificationState.VERIFIED,
                            verificationError = null
                        ) else it
                    }
                }
            } catch (e: Throwable) {
                Log.e("OfflineModelManager", "Model smoke test failed for $modelId", e)
                val errMsg = e.message ?: e.javaClass.simpleName
                _modelsState.update { list ->
                    list.map {
                        if (it.id == modelId) it.copy(
                            verificationState = VerificationState.FAILED,
                            verificationError = "Inference test failed: $errMsg"
                        ) else it
                    }
                }
            }
        }
    }

    /**
     * Dedicated import for a specific catalog slot (e.g. "phi_2", "tinyllama_1b", "gemma_2b_cpu").
     * Copies the picked .bin / .task directly to that model's assigned filename.
     */
    fun importModelForSlot(
        slotId: String,
        uri: Uri,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        val targetSlot = _modelsState.value.firstOrNull { it.id == slotId } ?: standardModels.firstOrNull { it.id == slotId }
        if (targetSlot == null) {
            importModelFromUri(uri, onComplete = onComplete)
            return
        }

        val targetFileName = targetSlot.fileName
        val finalFile = File(modelsDir, targetFileName)

        var resolvedSize = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) {
                        resolvedSize = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineModelManager", "Failed to query URI metadata", e)
        }

        _modelsState.update { list ->
            list.map {
                if (it.id == slotId) it.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    verificationError = null
                ) else it
            }
        }

        val job = scope.launch(Dispatchers.IO) {
            val tempFile = File(modelsDir, "$targetFileName.importing")
            var bytesCopied = 0L
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open input stream for selected file")

                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            val progress = if (resolvedSize > 0) (bytesCopied.toFloat() / resolvedSize).coerceIn(0f, 1f) else 0.5f
                            _modelsState.update { list ->
                                list.map { if (it.id == slotId) it.copy(downloadProgress = progress, downloadedBytes = bytesCopied) else it }
                            }
                        }
                    }
                }

                if (tempFile.length() < 10 * 1024 * 1024L) {
                    tempFile.delete()
                    throw IllegalStateException("Selected file is smaller than 10MB; please select a valid MediaPipe model (.bin or .task).")
                }

                if (finalFile.exists()) finalFile.delete()
                tempFile.renameTo(finalFile)

                refreshModelStatuses(slotId)
                verifyModel(slotId)
                backupModelToExternalStorage(finalFile, targetSlot.fileName)
                onModelDownloadedListener?.invoke(slotId)

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true, null)
                }
            } catch (e: Exception) {
                tempFile.delete()
                Log.e("OfflineModelManager", "Error importing file for slot $slotId", e)
                _modelsState.update { list ->
                    list.map {
                        if (it.id == slotId) it.copy(
                            isDownloading = false,
                            downloadProgress = 0f,
                            verificationState = VerificationState.FAILED,
                            verificationError = "Import failed: ${e.message}"
                        ) else it
                    }
                }
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, e.message ?: "Failed to import model")
                }
            } finally {
                activeJobs.remove(slotId)
            }
        }
        activeJobs[slotId] = job
    }

    /**
     * Import a custom model file (.bin / .task) from local storage via Storage Access Framework.
     */
    fun importModelFromUri(
        uri: Uri,
        customName: String? = null,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        val tempId = "imported_${System.currentTimeMillis()}"

        // Query file name and size from content resolver
        var resolvedFileName = "imported_model_${System.currentTimeMillis()}.bin"
        var resolvedSize = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) resolvedFileName = name
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) {
                        resolvedSize = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineModelManager", "Failed to query URI metadata", e)
        }

        val displayName = customName?.ifBlank { null } ?: resolvedFileName.removeSuffix(".bin").removeSuffix(".task")
        val finalFile = File(modelsDir, resolvedFileName)

        val initialItem = AiModelItem(
            id = tempId,
            name = displayName,
            description = "Imported from device storage: $resolvedFileName",
            parameterSize = "On-Device Neural Model",
            fileSizeFormatted = if (resolvedSize > 0) "${resolvedSize / (1024 * 1024)} MB" else "Calculating...",
            fileSizeBytes = resolvedSize,
            downloadUrl = "",
            fileName = resolvedFileName,
            isDownloading = true,
            downloadProgress = 0f,
            category = ModelCategory.LLM
        )

        _modelsState.update { listOf(initialItem) + it }

        val job = scope.launch(Dispatchers.IO) {
            val tempFile = File(modelsDir, "$resolvedFileName.importing")
            var bytesCopied = 0L
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open input stream for selected file")

                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            val progress = if (resolvedSize > 0) (bytesCopied.toFloat() / resolvedSize).coerceIn(0f, 1f) else 0.5f
                            _modelsState.update { list ->
                                list.map { if (it.id == tempId) it.copy(downloadProgress = progress, downloadedBytes = bytesCopied) else it }
                            }
                        }
                    }
                }

                if (tempFile.length() < 10 * 1024 * 1024L) {
                    tempFile.delete()
                    throw IllegalStateException("File is smaller than 10MB; not a valid LLM weight file.")
                }

                if (finalFile.exists()) finalFile.delete()
                tempFile.renameTo(finalFile)

                // Save to custom models
                val customModels = loadCustomModels().toMutableList()
                customModels.removeAll { it.fileName == resolvedFileName }
                val newItem = initialItem.copy(
                    isDownloading = false,
                    isDownloaded = true,
                    downloadProgress = 1f,
                    downloadedBytes = finalFile.length(),
                    fileSizeBytes = finalFile.length(),
                    fileSizeFormatted = "${finalFile.length() / (1024 * 1024)} MB"
                )
                customModels.add(0, newItem)
                saveCustomModels(customModels)

                refreshModelStatuses(tempId)
                verifyModel(tempId)
                backupModelToExternalStorage(finalFile, resolvedFileName)
                onModelDownloadedListener?.invoke(tempId)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true, null)
                }
            } catch (e: Exception) {
                tempFile.delete()
                Log.e("OfflineModelManager", "Error importing file", e)
                _modelsState.update { list -> list.filterNot { it.id == tempId } }
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, e.message ?: "Failed to import model")
                }
            } finally {
                activeJobs.remove(tempId)
            }
        }
        activeJobs[tempId] = job
    }

    /**
     * Real HTTP download of model weights with progress reporting and Hugging Face token support.
     */
    fun startDownload(modelId: String) {
        val targetItem = _modelsState.value.firstOrNull { it.id == modelId } ?: return
        if (targetItem.isDownloading || targetItem.isDownloaded) return

        if (targetItem.downloadUrl.isBlank()) {
            _modelsState.update { list ->
                list.map {
                    if (it.id == modelId) it.copy(
                        verificationState = VerificationState.FAILED,
                        verificationError = "No direct URL configured. Please tap 'Import Weights' to select file from device."
                    ) else it
                }
            }
            return
        }

        val job = scope.launch(Dispatchers.IO) {
            val destinationFile = File(modelsDir, targetItem.fileName)
            val tempFile = File(modelsDir, "${targetItem.fileName}.download")

            _modelsState.update { list ->
                list.map { if (it.id == modelId) it.copy(isDownloading = true, downloadProgress = 0f, verificationError = null) else it }
            }

            var success = false
            var errorMessage: String? = null
            try {
                var downloaded = 0L
                val requestBuilder = Request.Builder().url(targetItem.downloadUrl)

                val hfToken = preferences?.huggingFaceToken
                if (!hfToken.isNullOrBlank() && targetItem.downloadUrl.contains("huggingface.co")) {
                    requestBuilder.header("Authorization", "Bearer ${hfToken.trim()}")
                }

                val request = requestBuilder.build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful || response.body == null) {
                        if (response.code == 401 || response.code == 403) {
                            throw IllegalStateException(
                                "HTTP ${response.code}: Hugging Face requires authentication for ${targetItem.name}. " +
                                "Either enter your free Hugging Face token (hf_...) in settings, or tap the info icon to download in browser and tap 'Import Weights'."
                            )
                        } else {
                            throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                        }
                    }
                    val body = response.body!!
                    val totalLength = if (body.contentLength() > 0) body.contentLength() else targetItem.fileSizeBytes
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var read: Int
                            var lastProgressTime = 0L
                            var lastProgressPercent = -1

                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                val progress = (downloaded.toFloat() / totalLength).coerceIn(0f, 1f)
                                val currentPercent = (progress * 100).toInt()
                                val now = System.currentTimeMillis()

                                if (currentPercent != lastProgressPercent && (now - lastProgressTime > 250 || currentPercent == 100)) {
                                    lastProgressTime = now
                                    lastProgressPercent = currentPercent
                                    _modelsState.update { list ->
                                        list.map { if (it.id == modelId) it.copy(downloadProgress = progress, downloadedBytes = downloaded) else it }
                                    }
                                }
                            }
                        }
                    }
                }

                if (tempFile.length() < 10 * 1024 * 1024L) {
                    tempFile.delete()
                    throw IllegalStateException("Downloaded file is too small (<10MB); likely an error page or empty response.")
                }

                if (destinationFile.exists()) destinationFile.delete()
                tempFile.renameTo(destinationFile)

                success = true
                Log.i("OfflineModelManager", "Downloaded model weights: ${destinationFile.absolutePath} (${destinationFile.length()} bytes)")
            } catch (e: CancellationException) {
                tempFile.delete()
                errorMessage = "Download cancelled"
            } catch (e: Exception) {
                tempFile.delete()
                Log.e("OfflineModelManager", "Download failed for $modelId", e)
                errorMessage = e.message ?: "Network error during model download"
            } finally {
                activeJobs.remove(modelId)
                val finalError = errorMessage
                _modelsState.update { list ->
                    list.map {
                        if (it.id == modelId) {
                            it.copy(
                                isDownloading = false,
                                isDownloaded = success,
                                downloadProgress = if (success) 1f else 0f,
                                downloadedBytes = if (success) destinationFile.length() else 0L,
                                verificationState = if (success) VerificationState.NOT_VERIFIED else VerificationState.FAILED,
                                verificationError = finalError
                            )
                        } else it
                    }
                }
                if (success) {
                    refreshModelStatuses(modelId)
                    verifyModel(modelId)
                    backupModelToExternalStorage(destinationFile, targetItem.fileName)
                    onModelDownloadedListener?.invoke(modelId)
                }
            }
        }
        activeJobs[modelId] = job
    }

    /**
     * Download from arbitrary user-provided direct HTTP/HTTPS URL.
     */
    fun downloadFromCustomUrl(url: String, customName: String) {
        val fileName = url.substringAfterLast("/").substringBefore("?").ifBlank { "custom_model_${System.currentTimeMillis()}.bin" }
        val id = "custom_${System.currentTimeMillis()}"
        val name = customName.ifBlank { fileName.removeSuffix(".bin").removeSuffix(".task") }

        val customItem = AiModelItem(
            id = id,
            name = name,
            description = "Direct URL: $url",
            parameterSize = "Custom Neural Model",
            fileSizeFormatted = "Direct Download",
            fileSizeBytes = 1_000_000_000L,
            downloadUrl = url,
            fileName = fileName,
            category = ModelCategory.LLM
        )

        val customModels = loadCustomModels().toMutableList()
        customModels.add(0, customItem)
        saveCustomModels(customModels)
        refreshModelStatuses(id)
        startDownload(id)
    }

    fun cancelDownload(modelId: String) {
        activeJobs[modelId]?.cancel()
        activeJobs.remove(modelId)
        val targetItem = _modelsState.value.firstOrNull { it.id == modelId } ?: return
        val tempFile = File(modelsDir, "${targetItem.fileName}.download")
        if (tempFile.exists()) tempFile.delete()

        _modelsState.update { list ->
            list.map {
                if (it.id == modelId) it.copy(isDownloading = false, downloadProgress = 0f) else it
            }
        }
    }

    fun deleteModel(modelId: String): Boolean {
        val item = _modelsState.value.firstOrNull { it.id == modelId } ?: return false
        val file = File(modelsDir, item.fileName)
        val deleted = if (file.exists()) file.delete() else true

        // If custom, also remove from custom_models.json
        val customModels = loadCustomModels().toMutableList()
        if (customModels.any { it.id == modelId }) {
            customModels.removeAll { it.id == modelId }
            saveCustomModels(customModels)
        }

        _modelsState.update { list ->
            list.map {
                if (it.id == modelId) it.copy(
                    isDownloaded = false,
                    downloadedBytes = 0L,
                    verificationState = VerificationState.NOT_VERIFIED,
                    verificationError = null
                ) else it
            }
        }
        refreshModelStatuses()
        return deleted
    }

    fun isModelDownloaded(modelId: String): Boolean {
        val inState = _modelsState.value.firstOrNull { it.id == modelId }?.isDownloaded == true
        if (inState) return true
        val targetItem = _modelsState.value.firstOrNull { it.id == modelId } ?: return false
        val file = File(modelsDir, targetItem.fileName)
        return file.exists() && file.length() > 50 * 1024 * 1024L
    }

    fun getFirstDownloadedModel(): AiModelItem? {
        return _modelsState.value.firstOrNull { it.isDownloaded }
    }

    fun getActiveOrAnyDownloadedModel(preferredId: String): AiModelItem? {
        val preferred = _modelsState.value.firstOrNull { it.id == preferredId && it.isDownloaded }
        if (preferred != null) return preferred
        return getFirstDownloadedModel()
    }

    fun getActiveOrAnyDownloadedFile(preferredId: String): File? {
        val model = getActiveOrAnyDownloadedModel(preferredId) ?: return null
        val file = File(modelsDir, model.fileName)
        return if (file.exists() && file.length() > 50 * 1024 * 1024L) file else null
    }

    fun getModelFile(modelId: String): File? {
        val item = _modelsState.value.firstOrNull { it.id == modelId } ?: return null
        val file = File(modelsDir, item.fileName)
        return if (file.exists() && file.length() > 50 * 1024 * 1024L) file else null
    }

    fun hasAnyDownloadedModel(): Boolean {
        return _modelsState.value.any { it.isDownloaded }
    }

    fun getDeviceStorageInfo(): Pair<Long, Long> {
        return try {
            val stat = StatFs(context.filesDir.path)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            val total = stat.blockCountLong * stat.blockSizeLong
            Pair(available, total)
        } catch (e: Exception) {
            Pair(0L, 0L)
        }
    }

    fun getModelDownloadInfo(modelId: String): ModelDownloadInfo? {
        val item = _modelsState.value.firstOrNull { it.id == modelId } ?: standardModels.firstOrNull { it.id == modelId } ?: return null
        val requiresToken = false
        val instructions = when (item.id) {
            "tinyllama_1b" -> "TinyLlama 1.1B open community weights. Direct public download in-app or via browser. Tap 'Import Weights' if downloading externally."
            "phi_2" -> "Microsoft Phi-2 2.7B INT4 weights. Open community model. Direct public download in-app or via browser without any login. Tap 'Import Weights' to load manual downloads."
            "gemma_2b_cpu" -> "Gemma 2B CPU INT4 weights (MediaPipe compatible). Direct public download in-app or via browser without authentication. Tap 'Import Weights' to load manual downloads."
            "gemma_2b_gpu" -> "Gemma 2B GPU INT4 weights (MediaPipe compatible). Direct public download in-app or via browser without authentication. Tap 'Import Weights' to load manual downloads."
            else -> "Download the .bin or .task model file from the link and tap 'Import Weights'."
        }
        return ModelDownloadInfo(
            modelId = item.id,
            modelName = item.name,
            downloadUrl = item.downloadUrl,
            expectedFileName = item.fileName,
            expectedSize = item.fileSizeFormatted,
            requiresHfToken = requiresToken,
            instructions = instructions
        )
    }

    /**
     * Persist a backup copy in persistent external storage (/sdcard/Download/NovaModels/).
     * When testing/developing and the phone or app data is wiped, internal filesDir is cleared
     * but persistent external storage is kept! This allows restoring in ~1 second without re-downloading 1GB.
     */
    private fun backupModelToExternalStorage(sourceFile: File, fileName: String) {
        if (!sourceFile.exists() || sourceFile.length() < 50 * 1024 * 1024L) return
        scope.launch(Dispatchers.IO) {
            try {
                val searchDirs = mutableListOf<File>()
                try {
                    val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (pubDownloads != null) {
                        searchDirs.add(File(pubDownloads, "NovaModels"))
                    }
                } catch (ignored: Exception) {}
                searchDirs.add(File("/sdcard/Download/NovaModels"))
                searchDirs.add(File("/storage/emulated/0/Download/NovaModels"))

                for (dir in searchDirs) {
                    try {
                        if (!dir.exists()) dir.mkdirs()
                        val target = File(dir, fileName)
                        if (!target.exists() || target.length() != sourceFile.length()) {
                            Log.i("OfflineModelManager", "Backing up model to persistent storage: ${target.absolutePath}")
                            sourceFile.inputStream().use { input ->
                                FileOutputStream(target).use { output ->
                                    input.copyTo(output, bufferSize = 256 * 1024)
                                }
                            }
                            Log.i("OfflineModelManager", "Persistent model backup completed: ${target.absolutePath}")
                            break
                        }
                    } catch (e: Exception) {
                        Log.w("OfflineModelManager", "Could not backup to ${dir.absolutePath}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w("OfflineModelManager", "Failed to backup model to external storage", e)
            }
        }
    }

    /**
     * Scans phone storage for previously downloaded or transferred model files:
     * - /sdcard/Download/NovaModels/
     * - /sdcard/Download/
     * - Public Downloads directory
     * - Assets directory
     * If a valid weights file (>50MB) is found, it is copied into internal modelsDir,
     * verified, and activated immediately.
     */
    suspend fun scanAndAutoRestoreFromStorage(): Boolean = withContext(Dispatchers.IO) {
        val searchDirs = mutableListOf<File>()
        try {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (pubDownloads != null && pubDownloads.exists()) {
                searchDirs.add(File(pubDownloads, "NovaModels"))
                searchDirs.add(pubDownloads)
            }
        } catch (ignored: Exception) {}

        searchDirs.add(File("/sdcard/Download/NovaModels"))
        searchDirs.add(File("/sdcard/Download"))
        searchDirs.add(File("/storage/emulated/0/Download/NovaModels"))
        searchDirs.add(File("/storage/emulated/0/Download"))

        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { searchDirs.add(it) }
        context.getExternalFilesDir("models")?.let { searchDirs.add(it) }

        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles() ?: continue
            for (candidate in files) {
                if (!candidate.isFile || candidate.length() < 50 * 1024 * 1024L) continue
                val nameLower = candidate.name.lowercase()

                // Check TinyLlama
                if (nameLower.contains("tinyllama") || candidate.name.endsWith(".task")) {
                    val target = standardModels.first { it.id == "tinyllama_1b" }
                    val dest = File(modelsDir, target.fileName)
                    if (!dest.exists() || dest.length() != candidate.length()) {
                        Log.i("OfflineModelManager", "Restoring TinyLlama from ${candidate.absolutePath} to ${dest.absolutePath}")
                        candidate.inputStream().use { input ->
                            FileOutputStream(dest).use { output ->
                                input.copyTo(output, bufferSize = 256 * 1024)
                            }
                        }
                    }
                    refreshModelStatuses("tinyllama_1b")
                    verifyModel("tinyllama_1b")
                    return@withContext true
                }

                // Check Phi-2
                if (nameLower.contains("phi") || nameLower.contains("phi2")) {
                    val target = standardModels.first { it.id == "phi_2" }
                    val dest = File(modelsDir, target.fileName)
                    if (!dest.exists() || dest.length() != candidate.length()) {
                        Log.i("OfflineModelManager", "Restoring Phi-2 from ${candidate.absolutePath}")
                        candidate.inputStream().use { input ->
                            FileOutputStream(dest).use { output ->
                                input.copyTo(output, bufferSize = 256 * 1024)
                            }
                        }
                    }
                    refreshModelStatuses("phi_2")
                    verifyModel("phi_2")
                    return@withContext true
                }

                // Check Gemma
                if (nameLower.contains("gemma")) {
                    val target = standardModels.first { it.id == "gemma_2b_cpu" }
                    val dest = File(modelsDir, target.fileName)
                    if (!dest.exists() || dest.length() != candidate.length()) {
                        Log.i("OfflineModelManager", "Restoring Gemma from ${candidate.absolutePath}")
                        candidate.inputStream().use { input ->
                            FileOutputStream(dest).use { output ->
                                input.copyTo(output, bufferSize = 256 * 1024)
                            }
                        }
                    }
                    refreshModelStatuses("gemma_2b_cpu")
                    verifyModel("gemma_2b_cpu")
                    return@withContext true
                }
            }
        }

        // Check bundled assets if any
        try {
            val assetModels = context.assets.list("models")
            if (assetModels != null && assetModels.isNotEmpty()) {
                for (assetName in assetModels) {
                    if (assetName.endsWith(".bin") || assetName.endsWith(".task")) {
                        val dest = File(modelsDir, assetName)
                        Log.i("OfflineModelManager", "Extracting bundled model from assets: $assetName")
                        context.assets.open("models/$assetName").use { input ->
                            FileOutputStream(dest).use { output ->
                                input.copyTo(output, bufferSize = 256 * 1024)
                            }
                        }
                        refreshModelStatuses()
                        val model = getFirstDownloadedModel()
                        if (model != null) {
                            verifyModel(model.id)
                            return@withContext true
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}

        false
    }

    /**
     * Automated model preparation for testing.
     * When developing or testing, if the phone/emulator is reset or app data wiped:
     * 1. Checks if a model already exists in the app.
     * 2. If not, auto-scans phone storage and restores TinyLlama/Phi-2 in ~1 sec.
     * 3. If no file is on device, automatically starts background download of TinyLlama.
     *
     * In shipping time, simply toggle TestingModelConfig.AUTO_RESTORE_OR_DOWNLOAD_FOR_TESTING = false.
     */
    fun checkAndAutoPrepareTestingModel() {
        if (!TestingModelConfig.AUTO_RESTORE_OR_DOWNLOAD_FOR_TESTING) return

        scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(1200)

            // If app already has a model, ensure it is set as active
            if (hasAnyDownloadedModel()) {
                val active = getFirstDownloadedModel()
                if (active != null && preferences != null) {
                    if (preferences.activeModelId.isBlank() || preferences.activeModelId == "none") {
                        preferences.activeModelId = active.id
                    }
                }
                return@launch
            }

            // Attempt auto-restore from persistent storage (takes ~1 second)
            val restored = scanAndAutoRestoreFromStorage()
            if (restored) {
                Log.i("OfflineModelManager", "Auto-restored model from phone storage!")
                withContext(Dispatchers.Main) {
                    val active = getFirstDownloadedModel()
                    if (active != null && preferences != null) {
                        preferences.activeModelId = active.id
                        preferences.activeEngine = "OFFLINE"
                    }
                }
                return@launch
            }

            // If not found anywhere on storage, automatically initiate test download in background
            val targetTestModel = TestingModelConfig.DEFAULT_TESTING_MODEL_ID
            Log.i("OfflineModelManager", "Auto-initiating test download for $targetTestModel")
            withContext(Dispatchers.Main) {
                startDownload(targetTestModel)
            }
        }
    }
}
