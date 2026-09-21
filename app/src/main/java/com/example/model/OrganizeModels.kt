package com.example.model

import java.io.File
import java.util.UUID

/**
 * A rule for categorizing and moving files based on their extension.
 */
data class OrganizeRule(
    val id: String = UUID.randomUUID().toString(),
    val label: String, // e.g. "Images (.jpg .png …)"
    val extensions: List<String>, // e.g. [".jpg", ".png"]
    val destination: String, // e.g. "Pictures/Nova"
    val fileCount: Int = 0,
    val isEnabled: Boolean = true,
    val isValid: Boolean = true,
    val validationError: String? = null
)

/**
 * Complete plan for organizing the Downloads folder.
 */
data class OrganizePlan(
    val id: String = UUID.randomUUID().toString(),
    val mode: String = "default", // "default" or "custom"
    val title: String = "Organize Downloads",
    val totalFilesInDownloads: Int = 0,
    val filesStayingInPlace: Int = 0,
    val cappedNote: String? = null,
    val rules: List<OrganizeRule> = emptyList(),
    val fileMapping: Map<String, List<File>> = emptyMap() // ruleId -> files matched
) {
    val totalFilesToMove: Int
        get() = rules.filter { it.isEnabled && it.isValid }.sumOf { it.fileCount }
}

/**
 * Active progress state while moving files.
 */
data class OrganizeProgressState(
    val planId: String,
    val current: Int,
    val total: Int,
    val currentFileName: String? = null
)

/**
 * Summary card state displayed upon batch completion or early cancellation.
 */
data class OrganizeSummaryState(
    val planId: String,
    val plannedCount: Int,
    val movedCount: Int,
    val skippedCount: Int,
    val skippedDetails: List<String> = emptyList(),
    val wasCancelled: Boolean = false,
    val isUndone: Boolean = false,
    val isUndoing: Boolean = false,
    val undoSummary: String? = null
)

/**
 * Result of plan execution.
 */
data class OrganizeExecutionResult(
    val planId: String,
    val plannedCount: Int,
    val movedCount: Int,
    val skippedCount: Int,
    val skippedDetails: List<String> = emptyList(),
    val wasCancelled: Boolean = false,
    val permissionDenied: Boolean = false
)

/**
 * Result of reversing a plan's moves.
 */
data class UndoResult(
    val planId: String,
    val restoredCount: Int,
    val failedCount: Int,
    val errors: List<String> = emptyList()
)
