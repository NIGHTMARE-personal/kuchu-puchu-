package com.example.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.modelmanager.OfflineModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TraceLogLevel {
    INFO, WARN, ERROR, SUCCESS
}

enum class TraceHealthStatus {
    READY,
    CONSTRAINED_RAM,
    INSUFFICIENT_RAM,
    LOW_STORAGE,
    MODEL_NOT_FOUND,
    UNVERIFIED
}

data class TraceLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: String,
    val level: TraceLogLevel,
    val tag: String,
    val message: String
)

data class SystemTraceState(
    val isExecuting: Boolean = false,
    val activeModelId: String = "gemma_3_1b",
    val activeModelName: String = "Gemma 3 1B",
    val healthStatus: TraceHealthStatus = TraceHealthStatus.READY,
    val diagnosisTitle: String = "System Ready",
    val diagnosisExplanation: String = "Hardware resources are within operational parameters for on-device inference.",
    val recommendation: String? = null,
    val totalRamMb: Long = 0,
    val availableRamMb: Long = 0,
    val ramUsagePercent: Int = 0,
    val isLowMemory: Boolean = false,
    val lowMemThresholdMb: Long = 0,
    val jvmHeapUsedMb: Long = 0,
    val jvmHeapMaxMb: Long = 0,
    val totalDiskMb: Long = 0,
    val freeDiskMb: Long = 0,
    val diskUsagePercent: Int = 0,
    val modelFileSizeBytes: Long = 0,
    val modelFileExists: Boolean = false,
    val modelFilePath: String? = null,
    val estimatedRamRequirementMb: Long = 1500,
    val hasSufficientRam: Boolean = true,
    val hasSufficientDisk: Boolean = true,
    val cpuCores: Int = 0,
    val abi: String = "",
    val lastExecutionDurationMs: Long = 0,
    val logs: List<TraceLogEntry> = emptyList()
)

object SystemTraceManager {

    private val _traceState = MutableStateFlow(SystemTraceState())
    val traceState: StateFlow<SystemTraceState> = _traceState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun estimateModelRamRequirement(modelId: String): Long {
        return when {
            modelId.contains("dolphin", ignoreCase = true) || modelId.contains("phi", ignoreCase = true) -> 2600L
            modelId.contains("2b", ignoreCase = true) -> 2200L
            modelId.contains("1b", ignoreCase = true) -> 1500L
            modelId.contains("1_5b", ignoreCase = true) || modelId.contains("1.5b", ignoreCase = true) -> 1600L
            modelId.contains("360m", ignoreCase = true) -> 650L
            else -> 1200L
        }
    }

    fun getModelReadableName(modelId: String): String {
        return when (modelId) {
            "dolphin_phi" -> "Dolphin Phi-3 Mini"
            "gemma_3_1b" -> "Gemma 3 1B"
            "gemma_2_2b" -> "Gemma 2 2B"
            "tinyllama_1b" -> "TinyLlama 1.1B"
            "deepseek_r1_1_5b" -> "DeepSeek R1 1.5B"
            "smollm2_360m" -> "SmolLM2 360M"
            else -> modelId.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    fun inspectSystemResources(
        context: Context,
        modelId: String,
        modelManager: OfflineModelManager? = null
    ): SystemTraceState {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val thresholdMb = memInfo.threshold / (1024 * 1024)
        val isLowMem = memInfo.lowMemory
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(0)
        val ramUsagePct = if (totalRamMb > 0) ((usedRamMb.toDouble() / totalRamMb) * 100).toInt() else 0

        val runtime = Runtime.getRuntime()
        val heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val heapMaxMb = runtime.maxMemory() / (1024 * 1024)

        val filesDir = context.filesDir
        val freeDiskMb = filesDir.usableSpace / (1024 * 1024)
        val totalDiskMb = filesDir.totalSpace / (1024 * 1024)
        val usedDiskMb = (totalDiskMb - freeDiskMb).coerceAtLeast(0)
        val diskUsagePct = if (totalDiskMb > 0) ((usedDiskMb.toDouble() / totalDiskMb) * 100).toInt() else 0

        val modelName = getModelReadableName(modelId)
        val estimatedReqRam = estimateModelRamRequirement(modelId)

        val modelFile: File? = modelManager?.getModelFile(modelId)
            ?: File(File(context.filesDir, "models"), "$modelId.bin")
        val modelExists = modelFile != null && modelFile.exists() && modelFile.length() > 0
        val modelSizeBytes = if (modelExists) modelFile?.length() ?: 0L else 0L
        val modelPath = modelFile?.absolutePath

        val cpuCores = Runtime.getRuntime().availableProcessors()
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        val hasSufficientRam = availRamMb >= (estimatedReqRam - 200) && !isLowMem
        val hasSufficientDisk = freeDiskMb >= 500

        val healthStatus: TraceHealthStatus
        val title: String
        val explanation: String
        val recommendation: String?

        when {
            !modelExists -> {
                healthStatus = TraceHealthStatus.MODEL_NOT_FOUND
                title = "Model File Not Downloaded"
                explanation = "The weights file for '$modelName' is not present in local storage ($modelPath). The execution engine requires model weights before initializing on-device tensor graphs."
                recommendation = "Go to the Models tab and tap 'Download' for $modelName. Once downloaded, Kuchu Puchu will run directly on-device."
            }
            availRamMb < estimatedReqRam -> {
                healthStatus = TraceHealthStatus.INSUFFICIENT_RAM
                title = "Insufficient RAM for Model Loading"
                explanation = "Device has $availRamMb MB available RAM, but '$modelName' requires ~${estimatedReqRam} MB to load weights and allocate KV caches. MediaPipe GenAI native runtime cannot safely allocate buffers and will fail with OutOfMemory (OOM) or trigger system Low Memory Killer (LMK)."
                recommendation = "Close background apps to free RAM, or switch to the ultra-lightweight SmolLM2 360M (~650 MB RAM requirement) in the Models tab."
            }
            isLowMem -> {
                healthStatus = TraceHealthStatus.CONSTRAINED_RAM
                title = "System Under Low-Memory Pressure"
                explanation = "Android OS has flagged lowMemory = true (threshold: ${thresholdMb} MB). Large native model allocations risk being killed by the OS kernel."
                recommendation = "Free up memory by closing recent apps, or use Cloud AI / Rule mode."
            }
            !hasSufficientDisk -> {
                healthStatus = TraceHealthStatus.LOW_STORAGE
                title = "Low Storage Headroom"
                explanation = "Device has only $freeDiskMb MB free internal disk space. Model mmap operations, inference caches, and temp buffers require at least 500 MB free space."
                recommendation = "Delete unused files or caches from device storage."
            }
            else -> {
                healthStatus = TraceHealthStatus.READY
                title = "System Ready & Verified"
                explanation = "Hardware resources are healthy. Ample RAM ($availRamMb MB free vs ~${estimatedReqRam} MB needed) and storage ($freeDiskMb MB free) are available."
                recommendation = null
            }
        }

        val updated = _traceState.value.copy(
            activeModelId = modelId,
            activeModelName = modelName,
            healthStatus = healthStatus,
            diagnosisTitle = title,
            diagnosisExplanation = explanation,
            recommendation = recommendation,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            ramUsagePercent = ramUsagePct,
            isLowMemory = isLowMem,
            lowMemThresholdMb = thresholdMb,
            jvmHeapUsedMb = heapUsedMb,
            jvmHeapMaxMb = heapMaxMb,
            totalDiskMb = totalDiskMb,
            freeDiskMb = freeDiskMb,
            diskUsagePercent = diskUsagePct,
            modelFileSizeBytes = modelSizeBytes,
            modelFileExists = modelExists,
            modelFilePath = modelPath,
            estimatedRamRequirementMb = estimatedReqRam,
            hasSufficientRam = hasSufficientRam,
            hasSufficientDisk = hasSufficientDisk,
            cpuCores = cpuCores,
            abi = abi
        )
        _traceState.value = updated
        return updated
    }

    fun startExecutionTrace(
        context: Context,
        modelId: String,
        transcript: String,
        modelManager: OfflineModelManager? = null
    ) {
        val state = inspectSystemResources(context, modelId, modelManager)
        _traceState.update { it.copy(isExecuting = true) }

        logInfo("SYS", "Starting local execution trace for command: \"$transcript\"")
        logInfo("HARDWARE", "CPU: ${state.cpuCores} cores | ABI: ${state.abi} | Android SDK: ${Build.VERSION.SDK_INT}")
        logInfo("RAM", "Available: ${state.availableRamMb} MB / Total: ${state.totalRamMb} MB (Used: ${state.ramUsagePercent}%) | LowMem: ${state.isLowMemory}")
        logInfo("HEAP", "JVM Heap: ${state.jvmHeapUsedMb} MB / Max: ${state.jvmHeapMaxMb} MB")
        logInfo("DISK", "Free Storage: ${state.freeDiskMb} MB / Total: ${state.totalDiskMb} MB (Used: ${state.diskUsagePercent}%)")

        if (state.modelFileExists) {
            val sizeMb = state.modelFileSizeBytes / (1024 * 1024)
            logInfo("MODEL", "Target model: ${state.activeModelName} (${sizeMb} MB) at ${state.modelFilePath}")
        } else {
            logWarn("MODEL", "Model weights file not found on disk. Execution will require fallback.")
        }

        if (state.healthStatus == TraceHealthStatus.INSUFFICIENT_RAM) {
            logWarn("RAM_LIMIT", "RAM bottleneck detected: Device available RAM (${state.availableRamMb} MB) < Model working set (~${state.estimatedRamRequirementMb} MB)")
        } else if (state.healthStatus == TraceHealthStatus.LOW_STORAGE) {
            logWarn("DISK_LIMIT", "Disk headroom low: ${state.freeDiskMb} MB free.")
        }
    }

    fun recordMediaPipeAttempt(threads: Int = 4) {
        logInfo("MEDIAPIPE", "Initializing MediaPipe GenAI LlmInference graph (threads: $threads)...")
    }

    fun recordMediaPipeFailure(error: Throwable, diagnosticExplanation: String) {
        val rootMessage = error.message ?: error.javaClass.simpleName
        logError("MEDIAPIPE", "MediaPipe initialization failed: $rootMessage")
        logWarn("DIAGNOSIS", diagnosticExplanation)
        logInfo("ROUTER", "Engaging on-device deterministic reasoning engine with zero cloud leakage.")
    }

    fun recordExecutionComplete(
        durationMs: Long,
        engineLabel: String,
        isSuccess: Boolean
    ) {
        _traceState.update {
            it.copy(
                isExecuting = false,
                lastExecutionDurationMs = durationMs
            )
        }
        if (isSuccess) {
            logSuccess("EXECUTION", "Local execution completed in ${durationMs}ms via $engineLabel")
        } else {
            logError("EXECUTION", "Execution finished with error after ${durationMs}ms")
        }
    }

    fun logInfo(tag: String, message: String) = addEntry(TraceLogLevel.INFO, tag, message)
    fun logWarn(tag: String, message: String) = addEntry(TraceLogLevel.WARN, tag, message)
    fun logError(tag: String, message: String) = addEntry(TraceLogLevel.ERROR, tag, message)
    fun logSuccess(tag: String, message: String) = addEntry(TraceLogLevel.SUCCESS, tag, message)

    private fun addEntry(level: TraceLogLevel, tag: String, message: String) {
        val entry = TraceLogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        _traceState.update { current ->
            val updatedLogs = (current.logs + entry).takeLast(120)
            current.copy(logs = updatedLogs)
        }
        Log.d("SystemTrace", "[${entry.level}] [${entry.tag}] ${entry.message}")
    }

    fun clearLogs() {
        _traceState.update { it.copy(logs = emptyList()) }
    }

    fun getFormattedLogs(): String {
        val sb = StringBuilder()
        sb.append("=== NOVA SYSTEM TRACE & DIAGNOSTIC DUMP ===\n")
        val state = _traceState.value
        sb.append("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("Active Model: ${state.activeModelName} (${state.activeModelId})\n")
        sb.append("RAM: ${state.availableRamMb} MB free / ${state.totalRamMb} MB total (${state.ramUsagePercent}% used)\n")
        sb.append("JVM Heap: ${state.jvmHeapUsedMb} MB / ${state.jvmHeapMaxMb} MB max\n")
        sb.append("Disk: ${state.freeDiskMb} MB free / ${state.totalDiskMb} MB total\n")
        sb.append("Model Status: ${if (state.modelFileExists) "Present (${state.modelFileSizeBytes / (1024*1024)} MB)" else "Missing"}\n")
        sb.append("Diagnosis: [${state.healthStatus}] ${state.diagnosisTitle}\n")
        sb.append("Explanation: ${state.diagnosisExplanation}\n")
        if (!state.recommendation.isNullOrBlank()) {
            sb.append("Recommendation: ${state.recommendation}\n")
        }
        sb.append("\n--- LOG CHRONOLOGY ---\n")
        for (log in state.logs) {
            sb.append("[${log.timestamp}] [${log.level.name}] [${log.tag}] ${log.message}\n")
        }
        return sb.toString()
    }
}
