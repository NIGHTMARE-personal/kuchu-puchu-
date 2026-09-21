package com.example.organizer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.OrganizeMoveDao
import com.example.data.local.OrganizeMoveEntity
import com.example.model.OrganizeExecutionResult
import com.example.model.OrganizePlan
import com.example.model.OrganizeRule
import com.example.model.UndoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class DownloadsOrganizerManager(
    private val context: Context,
    private val organizeMoveDao: OrganizeMoveDao
) {

    companion object {
        private const val TAG = "DownloadsOrganizer"
        const val MAX_FILES_PER_PLAN = 300
        const val MAX_PLANNER_ENTRIES = 200
        const val MAX_RULES = 8

        // Default ruleset constants (rendered verbatim in the card)
        val DEFAULT_RULES_CONFIG = listOf(
            RuleTemplate(
                categoryName = "Images",
                extensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic"),
                destination = "Pictures/Nova",
                displayExtensions = "(.jpg .jpeg .png .webp .gif .heic)"
            ),
            RuleTemplate(
                categoryName = "Video",
                extensions = listOf(".mp4", ".mkv", ".mov", ".webm"),
                destination = "Movies/Nova",
                displayExtensions = "(.mp4 .mkv .mov .webm)"
            ),
            RuleTemplate(
                categoryName = "Audio",
                extensions = listOf(".mp3", ".wav", ".m4a", ".ogg"),
                destination = "Music/Nova",
                displayExtensions = "(.mp3 .wav .m4a .ogg)"
            ),
            RuleTemplate(
                categoryName = "Docs",
                extensions = listOf(".pdf", ".doc", ".docx", ".txt", ".ppt", ".pptx", ".xls", ".xlsx"),
                destination = "Documents/Nova",
                displayExtensions = "(.pdf .doc .docx .txt .ppt .pptx .xls .xlsx)"
            )
        )
    }

    data class RuleTemplate(
        val categoryName: String,
        val extensions: List<String>,
        val destination: String,
        val displayExtensions: String
    )

    private val isCancellationRequested = AtomicBoolean(false)

    /**
     * Checks if the app has required storage permission:
     * API 30+: Environment.isExternalStorageManager()
     * API <= 29: ContextCompat.checkSelfPermission(WRITE_EXTERNAL_STORAGE)
     */
    fun hasRequiredPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Resolves the device's public Downloads directory.
     */
    fun getDownloadsDirectory(): File {
        val pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (pubDir != null && (pubDir.exists() || pubDir.mkdirs())) {
            return pubDir
        }
        // Fallback to standard path
        val fallback = File(Environment.getExternalStorageDirectory(), "Download")
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
    }

    /**
     * Lists non-dot top-level files in Downloads, capped at [MAX_FILES_PER_PLAN].
     */
    fun listTopLevelDownloadsFiles(): List<File> {
        val dir = getDownloadsDirectory()
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val files = dir.listFiles { file ->
            file.isFile && !file.name.startsWith(".")
        } ?: return emptyList()

        return files.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    /**
     * Validates and sanitizes a destination relative subfolder path.
     * Allowed roots:
     * - Pictures/...
     * - Documents/...
     * - Music/...
     * - Movies/...
     * - Downloads/<name>/...
     * Rejects "..", absolute paths, illegal characters, and anything escaping allowed roots.
     */
    fun sanitizeDestination(destination: String?): Pair<Boolean, String?> {
        if (destination.isNullOrBlank()) {
            return Pair(false, "invalid destination")
        }

        val trimmed = destination.trim()

        // Rejection checks: absolute paths, traversal, illegal characters
        if (trimmed.startsWith("/") || trimmed.startsWith("\\") || trimmed.contains(":")) {
            return Pair(false, "invalid destination")
        }
        if (trimmed.contains("..")) {
            return Pair(false, "invalid destination")
        }
        val illegalChars = setOf('*', '?', '"', '<', '>', '|', '\u0000')
        if (trimmed.any { it in illegalChars }) {
            return Pair(false, "invalid destination")
        }

        // Normalize slashes
        val normalized = trimmed.replace('\\', '/').trim('/')
        val parts = normalized.split('/').filter { it.isNotBlank() }

        if (parts.isEmpty()) {
            return Pair(false, "invalid destination")
        }

        val root = parts[0].lowercase(Locale.ROOT)
        when (root) {
            "pictures", "documents", "music", "movies" -> {
                // Allowed subfolders under these media roots
                return Pair(true, normalized)
            }
            "downloads", "download" -> {
                // Must be a non-empty subfolder under Downloads (cannot be top-level Downloads root)
                if (parts.size < 2 || parts[1].isBlank()) {
                    return Pair(false, "invalid destination")
                }
                return Pair(true, normalized)
            }
            else -> {
                return Pair(false, "invalid destination")
            }
        }
    }

    /**
     * Resolves absolute File destination on storage.
     */
    fun resolveDestinationFile(sanitizedDestination: String, fileName: String): File {
        val parts = sanitizedDestination.replace('\\', '/').trim('/').split('/')
        val rootName = parts[0].lowercase(Locale.ROOT)
        val subPath = if (parts.size > 1) parts.drop(1).joinToString("/") else ""

        val baseDir: File = when (rootName) {
            "pictures" -> {
                val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (pub != null && (pub.exists() || pub.mkdirs())) pub
                else File(Environment.getExternalStorageDirectory(), "Pictures")
            }
            "documents" -> {
                val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                if (pub != null && (pub.exists() || pub.mkdirs())) pub
                else File(Environment.getExternalStorageDirectory(), "Documents")
            }
            "music" -> {
                val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                if (pub != null && (pub.exists() || pub.mkdirs())) pub
                else File(Environment.getExternalStorageDirectory(), "Music")
            }
            "movies" -> {
                val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                if (pub != null && (pub.exists() || pub.mkdirs())) pub
                else File(Environment.getExternalStorageDirectory(), "Movies")
            }
            "downloads", "download" -> {
                getDownloadsDirectory()
            }
            else -> {
                File(Environment.getExternalStorageDirectory(), parts[0])
            }
        }

        val targetDir = if (subPath.isNotBlank()) File(baseDir, subPath) else baseDir
        return File(targetDir, fileName)
    }

    /**
     * Collision handling: if "cat.jpg" exists → "cat (1).jpg", incrementing.
     */
    fun resolveCollision(targetFile: File): File {
        if (!targetFile.exists()) return targetFile

        val parentDir = targetFile.parentFile ?: return targetFile
        val fullName = targetFile.name
        val dotIndex = fullName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fullName.substring(0, dotIndex) else fullName
        val ext = if (dotIndex > 0) fullName.substring(dotIndex) else ""

        var index = 1
        while (true) {
            val candidateName = "$baseName ($index)$ext"
            val candidateFile = File(parentDir, candidateName)
            if (!candidateFile.exists()) {
                return candidateFile
            }
            index++
        }
    }

    /**
     * Builds the default organization plan from existing files in Downloads.
     */
    fun buildDefaultPlan(): OrganizePlan {
        val allFiles = listTopLevelDownloadsFiles()
        val totalFound = allFiles.size

        // Cap at 300 files per plan
        val cappedFiles = if (totalFound > MAX_FILES_PER_PLAN) {
            allFiles.take(MAX_FILES_PER_PLAN)
        } else {
            allFiles
        }
        val cappedNote = if (totalFound > MAX_FILES_PER_PLAN) {
            "Capped at 300 files (${totalFound - MAX_FILES_PER_PLAN} files remaining in Downloads)"
        } else null

        val ruleFilesMap = mutableMapOf<String, MutableList<File>>()
        val matchedFilesSet = mutableSetOf<File>()

        val rules = DEFAULT_RULES_CONFIG.map { template ->
            val ruleId = UUID.randomUUID().toString()
            val matching = cappedFiles.filter { file ->
                val ext = getExtension(file.name)
                template.extensions.any { it.equals(ext, ignoreCase = true) }
            }
            ruleFilesMap[ruleId] = matching.toMutableList()
            matchedFilesSet.addAll(matching)

            val (isValid, _) = sanitizeDestination(template.destination)

            OrganizeRule(
                id = ruleId,
                label = "${template.categoryName} ${template.displayExtensions} → ${template.destination}",
                extensions = template.extensions,
                destination = template.destination,
                fileCount = matching.size,
                isEnabled = true,
                isValid = isValid,
                validationError = if (!isValid) "invalid destination" else null
            )
        }

        val stayingInPlace = totalFound - matchedFilesSet.size

        return OrganizePlan(
            mode = "default",
            title = "Organize Downloads",
            totalFilesInDownloads = totalFound,
            filesStayingInPlace = stayingInPlace,
            cappedNote = cappedNote,
            rules = rules,
            fileMapping = ruleFilesMap
        )
    }

    /**
     * Builds a custom plan with rules supplied by the planner LLM.
     */
    fun buildCustomPlan(customRules: List<OrganizeRule>): OrganizePlan {
        val allFiles = listTopLevelDownloadsFiles()
        val totalFound = allFiles.size

        val cappedFiles = if (totalFound > MAX_FILES_PER_PLAN) {
            allFiles.take(MAX_FILES_PER_PLAN)
        } else {
            allFiles
        }
        val cappedNote = if (totalFound > MAX_FILES_PER_PLAN) {
            "Capped at 300 files (${totalFound - MAX_FILES_PER_PLAN} files remaining in Downloads)"
        } else null

        val ruleFilesMap = mutableMapOf<String, MutableList<File>>()
        val matchedFilesSet = mutableSetOf<File>()

        val processedRules = customRules.take(MAX_RULES).map { rule ->
            val ruleId = rule.id
            val (isValid, _) = sanitizeDestination(rule.destination)

            val matching = if (isValid) {
                cappedFiles.filter { file ->
                    val ext = getExtension(file.name)
                    rule.extensions.any { it.equals(ext, ignoreCase = true) }
                }
            } else {
                emptyList()
            }

            ruleFilesMap[ruleId] = matching.toMutableList()
            if (isValid) {
                matchedFilesSet.addAll(matching)
            }

            val extensionsLabel = rule.extensions.joinToString(" ")
            val displayLabel = if (rule.label.isNotBlank()) rule.label else "$extensionsLabel → ${rule.destination}"

            OrganizeRule(
                id = ruleId,
                label = displayLabel,
                extensions = rule.extensions,
                destination = rule.destination,
                fileCount = matching.size,
                isEnabled = isValid,
                isValid = isValid,
                validationError = if (!isValid) "invalid destination" else null
            )
        }

        val stayingInPlace = totalFound - matchedFilesSet.size

        return OrganizePlan(
            mode = "custom",
            title = "Organize Downloads",
            totalFilesInDownloads = totalFound,
            filesStayingInPlace = stayingInPlace,
            cappedNote = cappedNote,
            rules = processedRules,
            fileMapping = ruleFilesMap
        )
    }

    /**
     * Request cancellation of an in-progress organization batch.
     */
    fun requestCancel() {
        isCancellationRequested.set(true)
    }

    /**
     * Executes the confirmed organization plan:
     * - Write-ahead log to Room before moving each file
     * - Collision handling with auto-increment
     * - Non-aborting batch: skip locked/unwritable files, record them, continue
     * - Cancellation stops after current file, leaves already-moved files moved
     */
    suspend fun executePlan(
        plan: OrganizePlan,
        onProgress: (current: Int, total: Int, currentFileName: String?) -> Unit
    ): OrganizeExecutionResult = withContext(Dispatchers.IO) {
        isCancellationRequested.set(false)

        // Gather all files to move from enabled and valid rules
        val activeRules = plan.rules.filter { it.isEnabled && it.isValid }
        val itemsToMove = mutableListOf<Pair<File, String>>() // file to sanitized destination

        for (rule in activeRules) {
            val files = plan.fileMapping[rule.id] ?: emptyList()
            for (file in files) {
                itemsToMove.add(Pair(file, rule.destination))
            }
        }

        val totalPlanned = itemsToMove.size

        // Real move operations strictly gated behind storage-manager check
        if (!hasRequiredPermission()) {
            Log.w(TAG, "Storage permission (All Files Access) not granted — aborting plan execution")
            return@withContext OrganizeExecutionResult(
                planId = plan.id,
                plannedCount = totalPlanned,
                movedCount = 0,
                skippedCount = totalPlanned,
                skippedDetails = listOf("All Files Access permission required"),
                wasCancelled = false,
                permissionDenied = true
            )
        }

        var movedCount = 0
        var skippedCount = 0
        val skippedDetails = mutableListOf<String>()
        var wasCancelled = false

        for (i in itemsToMove.indices) {
            if (isCancellationRequested.get()) {
                wasCancelled = true
                Log.i(TAG, "Plan execution cancelled by user after processing $i/$totalPlanned files.")
                break
            }

            val (sourceFile, destinationFolder) = itemsToMove[i]
            onProgress(i + 1, totalPlanned, sourceFile.name)

            if (!sourceFile.exists() || !sourceFile.canRead()) {
                val reason = "${sourceFile.name} (unreadable or not found)"
                Log.w(TAG, "Source file cannot be read or is locked: ${sourceFile.absolutePath}")
                skippedDetails.add(reason)
                skippedCount++
                continue
            }

            // 1. Resolve collision destination
            val baseDestFile = resolveDestinationFile(destinationFolder, sourceFile.name)
            val finalDestFile = resolveCollision(baseDestFile)

            // 2. Write-ahead persist in Room BEFORE moving
            val moveEntity = OrganizeMoveEntity(
                planId = plan.id,
                fromPath = sourceFile.absolutePath,
                toPath = finalDestFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
            val moveId = organizeMoveDao.insertMove(moveEntity)

            // 3. Perform move
            var moveFailureReason: String? = null
            val moveSuccess = try {
                finalDestFile.parentFile?.let { dir ->
                    if (!dir.exists()) dir.mkdirs()
                }

                // First attempt atomic rename
                val renamed = sourceFile.renameTo(finalDestFile)
                if (renamed) {
                    true
                } else {
                    // Fallback to copy & delete for cross-volume moves
                    sourceFile.inputStream().use { input ->
                        finalDestFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (finalDestFile.exists() && finalDestFile.length() == sourceFile.length()) {
                        sourceFile.delete()
                        true
                    } else {
                        finalDestFile.delete()
                        moveFailureReason = "${sourceFile.name} (copy length mismatch or write failed)"
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed moving ${sourceFile.name} to ${finalDestFile.absolutePath}", e)
                moveFailureReason = "${sourceFile.name} (${e.javaClass.simpleName}: ${e.message ?: "permission denied/locked"})"
                false
            }

            // 4. Update Room status
            if (moveSuccess) {
                organizeMoveDao.updateMoveStatus(moveId, "COMPLETED")
                movedCount++
            } else {
                val reason = moveFailureReason ?: "${sourceFile.name} (locked/permission denied)"
                organizeMoveDao.updateMoveStatus(moveId, "FAILED", reason)
                skippedDetails.add(reason)
                skippedCount++
            }
        }

        OrganizeExecutionResult(
            planId = plan.id,
            plannedCount = totalPlanned,
            movedCount = movedCount,
            skippedCount = skippedCount,
            skippedDetails = skippedDetails,
            wasCancelled = wasCancelled,
            permissionDenied = false
        )
    }

    /**
     * Reverses all completed moves for the given plan:
     * - Reverses toPath → fromPath
     * - Recreates directories as needed
     * - One full undo per plan
     * - Room-persisted, survives process death
     */
    suspend fun undoPlan(planId: String): UndoResult = withContext(Dispatchers.IO) {
        if (!hasRequiredPermission()) {
            return@withContext UndoResult(planId, 0, 0, listOf("All Files Access permission required to undo"))
        }

        val completedMoves = organizeMoveDao.getCompletedMovesForPlan(planId)
        if (completedMoves.isEmpty()) {
            return@withContext UndoResult(planId, 0, 0, listOf("No completed moves found to undo"))
        }

        var restoredCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()

        for (move in completedMoves) {
            val movedFile = File(move.toPath)
            val originalTarget = File(move.fromPath)

            if (!movedFile.exists()) {
                errors.add("File '${movedFile.name}' was not found at ${move.toPath}")
                failedCount++
                continue
            }

            // Collision check at original target (if user created a new file with same name)
            val finalOriginalFile = resolveCollision(originalTarget)

            val restored = try {
                finalOriginalFile.parentFile?.let { dir ->
                    if (!dir.exists()) dir.mkdirs()
                }

                val renamed = movedFile.renameTo(finalOriginalFile)
                if (renamed) {
                    true
                } else {
                    movedFile.inputStream().use { input ->
                        finalOriginalFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (finalOriginalFile.exists() && finalOriginalFile.length() == movedFile.length()) {
                        movedFile.delete()
                        true
                    } else {
                        finalOriginalFile.delete()
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring file ${movedFile.name} back to ${finalOriginalFile.absolutePath}", e)
                false
            }

            if (restored) {
                organizeMoveDao.updateMoveStatus(move.id, "UNDONE")
                restoredCount++
            } else {
                errors.add("Failed to restore ${movedFile.name}")
                failedCount++
            }
        }

        UndoResult(
            planId = planId,
            restoredCount = restoredCount,
            failedCount = failedCount,
            errors = errors
        )
    }

    private fun getExtension(filename: String): String {
        val dot = filename.lastIndexOf('.')
        return if (dot != -1) filename.substring(dot).lowercase(Locale.ROOT) else ""
    }
}
