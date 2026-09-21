package com.example.executor

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import com.example.model.ActionCommand
import com.example.model.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CreateEventExecutor(private val context: Context) : ActionExecutor {

    override suspend fun execute(command: ActionCommand, isConfirmed: Boolean): ExecutionResult = withContext(Dispatchers.IO) {
        val title = command.target.trim()
        if (title.isBlank()) {
            return@withContext ExecutionResult.Error("No title specified for calendar event")
        }

        val startMillis = parseEventTime(command.datetime)
        val endMillis = startMillis + (60 * 60 * 1000) // Default 1 hour duration

        val displayTime = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(startMillis))

        // Before executing calendar inserts, show a confirmation card (action + target + Execute/Cancel buttons)
        if (!isConfirmed) {
            return@withContext ExecutionResult.RequiresConfirmation(
                command = command,
                targetResolved = title,
                details = "Scheduled for $displayTime"
            )
        }

        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            ExecutionResult.Success(
                message = "Opened Calendar for \"$title\"",
                details = displayTime
            )
        } catch (e: Exception) {
            Log.e("CreateEventExecutor", "Failed to launch calendar intent", e)
            ExecutionResult.Error("Failed to open calendar: ${e.localizedMessage}")
        }
    }

    private fun parseEventTime(datetimeStr: String?): Long {
        if (datetimeStr.isNullOrBlank()) {
            // Default to 1 hour from current time rounded to next hour
            val cal = Calendar.getInstance()
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "MM/dd/yyyy HH:mm",
            "MM/dd/yyyy"
        )

        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getDefault()
                val parsed = sdf.parse(datetimeStr)
                if (parsed != null) {
                    return parsed.time
                }
            } catch (ignored: Exception) {
            }
        }

        // Natural relative heuristics: "tomorrow", "tonight", "at 3pm", etc.
        val lower = datetimeStr.lowercase()
        val cal = Calendar.getInstance()

        if (lower.contains("tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        } else if (lower.contains("friday")) {
            advanceToDayOfWeek(cal, Calendar.FRIDAY)
        } else if (lower.contains("monday")) {
            advanceToDayOfWeek(cal, Calendar.MONDAY)
        }

        // Check for hour numbers e.g. "3pm", "10am", "15:00"
        val hourRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
        val match = hourRegex.find(lower)
        if (match != null) {
            var hour = match.groupValues[1].toIntOrNull() ?: 10
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val ampm = match.groupValues[3].lowercase()
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        // Fallback default
        cal.add(Calendar.HOUR_OF_DAY, 2)
        return cal.timeInMillis
    }

    private fun advanceToDayOfWeek(cal: Calendar, targetDay: Int) {
        var daysToAdd = (targetDay - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
        if (daysToAdd == 0) daysToAdd = 7
        cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
    }
}
