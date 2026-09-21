package com.example.executor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.model.ActionCommand
import com.example.model.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class YouTubeSearchExecutor(private val context: Context) : ActionExecutor {

    override suspend fun execute(command: ActionCommand, isConfirmed: Boolean): ExecutionResult = withContext(Dispatchers.IO) {
        val query = command.target.trim()
        if (query.isBlank()) {
            return@withContext ExecutionResult.Error("No search term specified for YouTube")
        }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encodedQuery"
            val uri = Uri.parse(url)

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Prefer YouTube native app package
                setPackage("com.google.android.youtube")
            }

            // Check if YouTube native app is installed and can handle intent
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            if (resolveInfo == null) {
                // If native YouTube app is not available, remove package hint to allow browser or alternate player
                intent.setPackage(null)
            }

            context.startActivity(intent)
            ExecutionResult.Success(
                message = "Opened YouTube search for \"$query\"",
                details = "Target: $url"
            )
        } catch (e: Exception) {
            Log.e("YouTubeSearchExecutor", "Failed to launch YouTube intent", e)
            ExecutionResult.Error("Failed to open YouTube: ${e.localizedMessage}")
        }
    }
}
