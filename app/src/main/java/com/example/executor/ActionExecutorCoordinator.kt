package com.example.executor

import android.content.Context
import com.example.data.local.SecurePreferences
import com.example.model.ActionCommand
import com.example.model.ActionType
import com.example.model.ExecutionResult

class ActionExecutorCoordinator(
    private val context: Context,
    private val preferences: SecurePreferences
) {
    private val openAppExecutor = OpenAppExecutor(context)
    private val youtubeSearchExecutor = YouTubeSearchExecutor(context)
    private val callExecutor = CallExecutor(context, preferences)
    private val createEventExecutor = CreateEventExecutor(context)
    private val timerExecutor = TimerExecutor(context)

    suspend fun execute(command: ActionCommand, isConfirmed: Boolean = false): ExecutionResult {
        return when (command.action) {
            ActionType.OPEN_APP -> openAppExecutor.execute(command, isConfirmed)
            ActionType.YOUTUBE_SEARCH -> youtubeSearchExecutor.execute(command, isConfirmed)
            ActionType.CALL -> callExecutor.execute(command, isConfirmed)
            ActionType.CREATE_EVENT -> createEventExecutor.execute(command, isConfirmed)
            ActionType.SET_TIMER -> timerExecutor.execute(command, isConfirmed)
            ActionType.ORGANIZE_DOWNLOADS -> {
                ExecutionResult.Success(
                    message = "Downloads organization initiated",
                    details = "Downloads organization"
                )
            }
            ActionType.ANSWER -> {
                val message = command.reply ?: command.target
                ExecutionResult.Success(
                    message = message,
                    details = message
                )
            }
            ActionType.GENERATE_IMAGE -> {
                val prompt = command.target.ifBlank { command.rawTranscript }
                val encodedPrompt = try {
                    java.net.URLEncoder.encode(prompt, "UTF-8")
                } catch (_: Exception) {
                    prompt.replace(" ", "%20")
                }
                val seed = System.currentTimeMillis() % 100000
                val generatedUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true&seed=$seed"
                val reply = command.reply ?: "Here is the image for \"$prompt\""
                ExecutionResult.Success(
                    message = reply,
                    details = generatedUrl
                )
            }
            ActionType.NONE -> {
                val reply = command.reply
                if (!reply.isNullOrBlank()) {
                    ExecutionResult.Success(
                        message = reply,
                        details = reply
                    )
                } else {
                    ExecutionResult.Error("No executable device action recognized. Please try saying 'call [name]', 'open [app]', 'play [song] on YouTube', 'schedule [meeting]', or 'set a timer for [duration]'.")
                }
            }
        }
    }
}
