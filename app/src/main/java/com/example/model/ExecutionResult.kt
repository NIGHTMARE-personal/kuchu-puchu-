package com.example.model

sealed interface ExecutionResult {
    data class Success(
        val message: String,
        val details: String? = null
    ) : ExecutionResult

    data class RequiresConfirmation(
        val command: ActionCommand,
        val targetResolved: String,
        val details: String? = null
    ) : ExecutionResult

    data class MissingPermission(
        val permission: String,
        val explanation: String
    ) : ExecutionResult

    data class Error(
        val error: String
    ) : ExecutionResult
}
