package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CommandHistoryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowThisWasHandledSheet(
    item: CommandHistoryEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val engine = remember(item) {
        item.engineName ?: when (item.source.uppercase()) {
            "ROUTER" -> "Built-in Fast Router"
            "LOCAL" -> "Gemma 3 1B (On-Device)"
            "GEMINI" -> "Gemini 3.6 Flash (ONLINE)"
            "OPENAI" -> "GPT-4o Mini (ONLINE)"
            "ANTHROPIC" -> "Claude 3.5 Sonnet (ONLINE)"
            else -> item.source
        }
    }

    val route = remember(item) {
        item.routeType ?: if (item.source.equals("ROUTER", ignoreCase = true)) {
            "fast-path"
        } else {
            "LLM"
        }
    }

    val parsedIntentJson = remember(item) {
        if (!item.rawIntentJson.isNullOrBlank()) {
            item.rawIntentJson
        } else {
            buildString {
                append("{\n")
                append("  \"action\": \"${item.action}\",\n")
                val safeTarget = item.target.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                append("  \"target\": \"$safeTarget\"")
                if (!item.datetime.isNullOrBlank()) {
                    append(",\n  \"datetime\": \"${item.datetime}\"")
                }
                append("\n}")
            }
        }
    }

    val executorResult = remember(item) {
        item.details ?: item.outcome
    }

    val latency = remember(item) {
        if (item.latencyMs != null) "${item.latencyMs} ms" else "N/A"
    }

    val confidence = remember(item) {
        if (item.confidence != null) {
            String.format(java.util.Locale.US, "%.2f", item.confidence)
        } else {
            // Check if transcript had confidence score
            val regex = Regex("""\(0\.\d{1,2}\)""")
            val match = regex.find(item.transcript)
            match?.value?.removePrefix("(")?.removeSuffix(")")
        }
    }

    val formattedOutput = remember(engine, route, parsedIntentJson, executorResult, latency, confidence) {
        buildString {
            append("engine: $engine\n")
            append("route: $route\n")
            append("parsed intent JSON:\n$parsedIntentJson\n")
            append("executor result: $executorResult\n")
            append("latency: $latency\n")
            if (confidence != null) {
                append("confidence: $confidence")
            }
        }.trimEnd()
    }

    fun copyDebugInfo() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("How this was handled", formattedOutput))
        Toast.makeText(context, "Telemetry copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("how_this_was_handled_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "How this was handled",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Monospace read-only execution trace
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = formattedOutput,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("telemetry_monospace_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { copyDebugInfo() },
                    modifier = Modifier.testTag("copy_telemetry_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Trace")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
