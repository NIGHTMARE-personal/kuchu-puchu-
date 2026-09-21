package com.example.executor

import com.example.model.ActionCommand
import com.example.model.ExecutionResult

interface ActionExecutor {
    suspend fun execute(command: ActionCommand, isConfirmed: Boolean = false): ExecutionResult
}
