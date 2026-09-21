package com.example.executor

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import com.example.model.ActionCommand
import com.example.model.ExecutionResult
import com.example.util.TimerParserHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TimerExecutor(private val context: Context) : ActionExecutor {

    override suspend fun execute(command: ActionCommand, isConfirmed: Boolean): ExecutionResult = withContext(Dispatchers.IO) {
        val seconds = command.durationSeconds ?: TimerParserHelper.parseDurationToSeconds(command.target)
        if (seconds <= 0) {
            return@withContext ExecutionResult.Error("No valid timer duration specified")
        }

        val label = command.label?.takeIf { it.isNotBlank() } ?: extractLabelFromTarget(command.target)
        val formattedDuration = TimerParserHelper.formatDurationPrefix(seconds)
        val resultMessage = "$formattedDuration timer ready — press start in Clock"

        try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                if (!label.isNullOrBlank()) {
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                }
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Check if any app handles ACTION_SET_TIMER
            val resolveInfo = try {
                context.packageManager.resolveActivity(intent, 0)
            } catch (e: Exception) {
                null
            }

            if (resolveInfo != null) {
                context.startActivity(intent)
            } else {
                // Fallback attempt to open Clock app
                val clockIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
                    ?: context.packageManager.getLaunchIntentForPackage("com.sec.android.app.clockpackage")
                    ?: Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setPackage("com.google.android.deskclock")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                try {
                    context.startActivity(clockIntent)
                } catch (e: Exception) {
                    Log.w("TimerExecutor", "Could not start fallback clock app", e)
                }
            }

            ExecutionResult.Success(
                message = resultMessage,
                details = if (!label.isNullOrBlank()) "Label: $label • ${TimerParserHelper.formatDurationFull(seconds)}" else TimerParserHelper.formatDurationFull(seconds)
            )
        } catch (e: Exception) {
            Log.e("TimerExecutor", "Failed to launch timer intent", e)
            ExecutionResult.Error("Failed to set timer: ${e.localizedMessage}")
        }
    }

    companion object {
        fun formatDurationPrefix(seconds: Int): String = TimerParserHelper.formatDurationPrefix(seconds)
        fun formatDurationFull(seconds: Int): String = TimerParserHelper.formatDurationFull(seconds)
        fun parseSecondsFromTarget(target: String): Int = TimerParserHelper.parseDurationToSeconds(target)
        fun extractLabelFromTarget(target: String): String? = TimerParserHelper.extractDurationAndLabel(target).second
    }
}
