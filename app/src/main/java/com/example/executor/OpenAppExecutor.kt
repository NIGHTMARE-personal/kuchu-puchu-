package com.example.executor

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.model.ActionCommand
import com.example.model.ActionType
import com.example.model.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenAppExecutor(private val context: Context) : ActionExecutor {

    companion object {
        private val GENERIC_APP_KEYWORDS = setOf(
            "something", "anything", "app", "an app", "the app", "whatever", "application", "apps"
        )

        private val COMMON_ALIASES = mapOf(
            "yt" to "YouTube",
            "youtube" to "YouTube",
            "you tube" to "YouTube",
            "chrome" to "Google Chrome",
            "browser" to "Chrome",
            "web browser" to "Chrome",
            "internet" to "Chrome",
            "calc" to "Calculator",
            "calculator" to "Calculator",
            "music" to "Spotify",
            "spotify" to "Spotify",
            "clock" to "Clock",
            "timer" to "Clock",
            "alarm" to "Clock",
            "alarms" to "Clock",
            "stopwatch" to "Clock",
            "camera" to "Camera",
            "cam" to "Camera",
            "gallery" to "Photos",
            "photos" to "Photos",
            "pictures" to "Photos",
            "mail" to "Gmail",
            "email" to "Gmail",
            "gmail" to "Gmail",
            "map" to "Google Maps",
            "maps" to "Google Maps",
            "google maps" to "Google Maps",
            "navigation" to "Google Maps",
            "gps" to "Google Maps",
            "settings" to "Settings",
            "setting" to "Settings",
            "calendar" to "Calendar",
            "cal" to "Calendar",
            "contacts" to "Contacts",
            "address book" to "Contacts",
            "people" to "Contacts",
            "phone" to "Phone",
            "dialer" to "Phone",
            "call" to "Phone",
            "files" to "Files",
            "file manager" to "Files",
            "downloads" to "Files",
            "play store" to "Google Play Store",
            "playstore" to "Google Play Store",
            "store" to "Google Play Store",
            "app store" to "Google Play Store",
            "messages" to "Messages",
            "sms" to "Messages",
            "texting" to "Messages",
            "whatsapp" to "WhatsApp",
            "instagram" to "Instagram",
            "telegram" to "Telegram"
        )

        private val YT_MEDIA_REGEX = Regex(
            "^(?:play|search|open|watch|listen\\s+to|show|find)?\\s*(.+?)\\s+(?:on|in)\\s+(?:youtube|yt)$",
            RegexOption.IGNORE_CASE
        )
        private val HINGLISH_YT_REGEX = Regex(
            "^(?:youtube|yt)\\s+(?:pe|par|me|mein)\\s+(.+?)(?:\\s+(?:chalao|play|open|dekho))?$",
            RegexOption.IGNORE_CASE
        )
        private val HINGLISH_YT_SUFFIX_REGEX = Regex(
            "^(.+?)\\s+(?:youtube|yt)\\s+(?:pe|par|me|mein)\\s*(?:chalao|play|open|dekho)?$",
            RegexOption.IGNORE_CASE
        )
        private val SPOTIFY_MEDIA_REGEX = Regex(
            "^(?:play|search|open|listen\\s+to)?\\s*(.+?)\\s+(?:on|in)\\s+spotify$",
            RegexOption.IGNORE_CASE
        )
    }

    override suspend fun execute(command: ActionCommand, isConfirmed: Boolean): ExecutionResult = withContext(Dispatchers.IO) {
        val rawTarget = command.target.trim()
        val cleanTarget = cleanTargetQuery(rawTarget)

        if (cleanTarget.isBlank() || cleanTarget.lowercase() in GENERIC_APP_KEYWORDS) {
            return@withContext ExecutionResult.Error(
                "Which app would you like to open? Try saying 'open YouTube', 'open Camera', 'open Settings', or 'open Calculator'."
            )
        }

        // Intercept media queries intended for YouTube, e.g. "open me tera ho gaya on youtube"
        val ytMatch = YT_MEDIA_REGEX.find(cleanTarget) ?: HINGLISH_YT_REGEX.find(cleanTarget) ?: HINGLISH_YT_SUFFIX_REGEX.find(cleanTarget)
        if (ytMatch != null) {
            val query = ytMatch.groupValues[1].trim()
            if (query.isNotBlank() && !query.equals("youtube", ignoreCase = true) && !query.equals("yt", ignoreCase = true)) {
                return@withContext YouTubeSearchExecutor(context).execute(
                    command.copy(action = ActionType.YOUTUBE_SEARCH, target = query),
                    isConfirmed
                )
            }
        }

        // Intercept Spotify search commands
        val spotifyMatch = SPOTIFY_MEDIA_REGEX.find(cleanTarget)
        if (spotifyMatch != null) {
            val track = spotifyMatch.groupValues[1].trim()
            if (track.isNotBlank() && !track.equals("spotify", ignoreCase = true)) {
                try {
                    val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(track)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setPackage("com.spotify.music")
                    }
                    if (context.packageManager.resolveActivity(spotifyIntent, 0) != null) {
                        context.startActivity(spotifyIntent)
                        return@withContext ExecutionResult.Success("Opened Spotify search for \"$track\"")
                    }
                } catch (e: Exception) {
                    Log.w("OpenAppExecutor", "Failed to launch Spotify search", e)
                }
            }
        }

        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        // Query launcher activities with flag 0 (avoid MATCH_DEFAULT_ONLY which drops launcher apps missing CATEGORY_DEFAULT)
        val resolveInfos: List<ResolveInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(launcherIntent, 0)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val appsFromResolve = resolveInfos.mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            val label = try {
                resolveInfo.loadLabel(packageManager).toString()
            } catch (e: Exception) {
                pkg
            }
            AppCandidate(label = label, packageName = pkg)
        }

        // Also query installed applications for any packages that expose a launch intent
        val appsFromInstalled = try {
            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0)
            }
            installed.mapNotNull { appInfo ->
                val launch = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                if (launch != null) {
                    val label = try {
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        appInfo.packageName
                    }
                    AppCandidate(label = label, packageName = appInfo.packageName)
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }

        val installedApps = (appsFromResolve + appsFromInstalled).distinctBy { it.packageName }

        val resolvedQuery = COMMON_ALIASES[cleanTarget.lowercase()] ?: cleanTarget
        val matchedApp = findBestMatch(resolvedQuery, installedApps)

        if (matchedApp != null) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(matchedApp.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return@withContext ExecutionResult.Success(
                        message = "Opened ${matchedApp.label}",
                        details = "Package: ${matchedApp.packageName}"
                    )
                }
            } catch (e: Exception) {
                Log.e("OpenAppExecutor", "Failed to launch ${matchedApp.label}", e)
            }
        }

        // Comprehensive system fallbacks for core capabilities and uninstalled services
        val fallbackResult = trySystemFallback(cleanTarget, resolvedQuery)
        if (fallbackResult != null) {
            return@withContext fallbackResult
        }

        // If not installed on device, show suggestion card instead of auto-launching
        if (!isConfirmed) {
            return@withContext ExecutionResult.RequiresConfirmation(
                command = command,
                targetResolved = "$cleanTarget isn't installed — open its store page?",
                details = "Open Google Play Store to install $cleanTarget"
            )
        }

        // When confirmed, search in Google Play Store (market:// first, web fallback strictly in ActivityNotFoundException)
        tryPlayStoreSearch(cleanTarget)
    }

    private fun cleanTargetQuery(raw: String): String {
        return raw.trim()
            .replace(Regex("^(?:the|my|an|a)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:app|application|please)$", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun trySystemFallback(cleanTarget: String, resolvedQuery: String): ExecutionResult? {
        val q = resolvedQuery.lowercase()

        // 1. YouTube Fallback (Launch web version if native app is not installed)
        if (q.contains("youtube") || cleanTarget.equals("yt", ignoreCase = true)) {
            try {
                val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(ytIntent)
                return ExecutionResult.Success("Opened YouTube")
            } catch (e: Exception) {
                Log.w("OpenAppExecutor", "YouTube web fallback failed", e)
            }
        }

        // 2. Settings Fallback
        if (q.contains("setting")) {
            try {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return ExecutionResult.Success("Opened Android Settings")
            } catch (e: Exception) {
                Log.w("OpenAppExecutor", "Settings fallback failed", e)
            }
        }

        // 3. Camera Fallback
        if (q.contains("camera") || q.contains("cam")) {
            try {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(cameraIntent)
                return ExecutionResult.Success("Opened Camera")
            } catch (e: Exception) {
                try {
                    val stillIntent = Intent("android.media.action.STILL_IMAGE_CAMERA").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(stillIntent)
                    return ExecutionResult.Success("Opened Camera")
                } catch (e2: Exception) {
                    Log.w("OpenAppExecutor", "Camera fallback failed", e2)
                }
            }
        }

        // 4. Clock / Timer / Alarm Fallback
        if (q.contains("clock") || q.contains("timer") || q.contains("alarm") || q.contains("stopwatch")) {
            try {
                val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(clockIntent)
                return ExecutionResult.Success("Opened Clock")
            } catch (e: Exception) {
                try {
                    val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, 60)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(timerIntent)
                    return ExecutionResult.Success("Opened Clock")
                } catch (e2: Exception) {
                    Log.w("OpenAppExecutor", "Clock fallback failed", e2)
                }
            }
        }

        // 5. Web Browser / Chrome Fallback
        if (q.contains("browser") || q.contains("chrome") || q.contains("internet") || q.contains("web")) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                return ExecutionResult.Success("Opened Web Browser")
            } catch (e: Exception) {
                Log.w("OpenAppExecutor", "Browser fallback failed", e)
            }
        }

        // 6. Calculator Fallback
        if (q.contains("calc")) {
            try {
                val calcIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(calcIntent)
                return ExecutionResult.Success("Opened Calculator")
            } catch (e: Exception) {
                try {
                    val webCalcIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=calculator")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webCalcIntent)
                    return ExecutionResult.Success("Opened Calculator")
                } catch (e2: Exception) {
                    Log.w("OpenAppExecutor", "Calculator fallback failed", e2)
                }
            }
        }

        // 7. Calendar Fallback
        if (q.contains("calendar") || q.contains("cal")) {
            try {
                val calIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(calIntent)
                return ExecutionResult.Success("Opened Calendar")
            } catch (e: Exception) {
                try {
                    val appCalIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_CALENDAR)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(appCalIntent)
                    return ExecutionResult.Success("Opened Calendar")
                } catch (e2: Exception) {
                    Log.w("OpenAppExecutor", "Calendar fallback failed", e2)
                }
            }
        }

        // 8. Contacts Fallback
        if (q.contains("contact") || q.contains("people") || q.contains("address book")) {
            try {
                val contactsIntent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(contactsIntent)
                return ExecutionResult.Success("Opened Contacts")
            } catch (e: Exception) {
                Log.w("OpenAppExecutor", "Contacts fallback failed", e)
            }
        }

        // 9. Phone / Dialer Fallback
        if (q.contains("phone") || q.contains("dialer") || q == "call") {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                return ExecutionResult.Success("Opened Phone")
            } catch (e: Exception) {
                Log.w("OpenAppExecutor", "Dialer fallback failed", e)
            }
        }

        // 10. Maps Fallback
        if (q.contains("map") || q.contains("navigation") || q.contains("gps")) {
            try {
                val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(mapsIntent)
                return ExecutionResult.Success("Opened Google Maps")
            } catch (e: Exception) {
                Log.w("OpenAppExecutor", "Maps fallback failed", e)
            }
        }

        // 11. Photos / Gallery Fallback
        if (q.contains("photo") || q.contains("gallery") || q.contains("picture")) {
            try {
                val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(galleryIntent)
                return ExecutionResult.Success("Opened Photos")
            } catch (e: Exception) {
                Log.w("OpenAppExecutor", "Photos fallback failed", e)
            }
        }

        // 12. Files / Downloads Fallback
        if (q.contains("file") || q.contains("download")) {
            try {
                val dlIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dlIntent)
                return ExecutionResult.Success("Opened Downloads")
            } catch (e: Exception) {
                Log.w("OpenAppExecutor", "Downloads fallback failed", e)
            }
        }

        // 13. Email / Gmail Fallback
        if (q.contains("mail") || q.contains("gmail")) {
            try {
                val emailIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_EMAIL)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(emailIntent)
                return ExecutionResult.Success("Opened Email")
            } catch (e: Exception) {
                try {
                    val mailtoIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(mailtoIntent)
                    return ExecutionResult.Success("Opened Email")
                } catch (e2: Exception) {
                    Log.w("OpenAppExecutor", "Mail fallback failed", e2)
                }
            }
        }

        // 14. Play Store Fallback
        if (q.contains("play store") || q.contains("playstore") || q.contains("store")) {
            try {
                val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(storeIntent)
                return ExecutionResult.Success("Opened Play Store")
            } catch (e: Exception) {
                try {
                    val webStore = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webStore)
                    return ExecutionResult.Success("Opened Play Store")
                } catch (e2: Exception) {
                    Log.w("OpenAppExecutor", "Play store fallback failed", e2)
                }
            }
        }

        return null
    }

    private fun tryPlayStoreSearch(appName: String): ExecutionResult {
        val encoded = Uri.encode(appName)
        val marketUri = Uri.parse("market://search?q=$encoded")
        val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(marketIntent)
            ExecutionResult.Success(
                message = "$appName is not installed — opened Play Store to install it",
                details = "Searched for $appName on Google Play"
            )
        } catch (e: ActivityNotFoundException) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                ExecutionResult.Success(
                    message = "$appName is not installed — opened Play Store in browser",
                    details = "Searched for $appName on Google Play"
                )
            } catch (webException: Exception) {
                ExecutionResult.Error("Could not open Play Store or browser for '$appName'.")
            }
        } catch (e: Exception) {
            ExecutionResult.Error("Could not launch Play Store: ${e.message}")
        }
    }

    private data class AppCandidate(val label: String, val packageName: String)

    private fun findBestMatch(query: String, candidates: List<AppCandidate>): AppCandidate? {
        val q = query.lowercase().trim()

        // 1. Exact match
        candidates.firstOrNull { it.label.equals(q, ignoreCase = true) }?.let { return it }

        // 2. Starts with query
        candidates.firstOrNull { it.label.lowercase().startsWith(q) }?.let { return it }

        // 3. Contains query as substring
        candidates.firstOrNull { it.label.lowercase().contains(q) }?.let { return it }

        // 4. Query contains candidate label (e.g. "open Google Chrome browser" matches "Chrome")
        candidates.firstOrNull { q.contains(it.label.lowercase()) }?.let { return it }

        // 5. Package name contains query
        candidates.firstOrNull { it.packageName.lowercase().contains(q) }?.let { return it }

        // 6. Fuzzy token distance (Levenshtein)
        var bestScore = Int.MAX_VALUE
        var bestCandidate: AppCandidate? = null
        for (app in candidates) {
            val dist = levenshtein(q, app.label.lowercase())
            val maxLen = maxOf(q.length, app.label.length)
            if (dist <= 2 || (maxLen > 4 && dist <= 3)) {
                if (dist < bestScore) {
                    bestScore = dist
                    bestCandidate = app
                }
            }
        }

        return bestCandidate
    }

    private fun levenshtein(s: String, t: String): Int {
        if (s == t) return 0
        if (s.isEmpty()) return t.length
        if (t.isEmpty()) return s.length

        var prev = IntArray(t.length + 1) { it }
        var curr = IntArray(t.length + 1)

        for (i in 1..s.length) {
            curr[0] = i
            for (j in 1..t.length) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,       // deletion
                    curr[j - 1] + 1,   // insertion
                    prev[j - 1] + cost // substitution
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[t.length]
    }
}

