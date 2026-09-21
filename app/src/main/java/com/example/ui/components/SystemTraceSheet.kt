package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diagnostics.SystemTraceManager
import com.example.diagnostics.SystemTraceState
import com.example.diagnostics.TraceHealthStatus
import com.example.diagnostics.TraceLogLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemTraceBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val traceState by SystemTraceManager.traceState.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("system_trace_bottom_sheet")
    ) {
        SystemTraceContent(
            traceState = traceState,
            onClose = onDismiss,
            onRefresh = {
                SystemTraceManager.inspectSystemResources(context, traceState.activeModelId)
                Toast.makeText(context, "Diagnostics refreshed", Toast.LENGTH_SHORT).show()
            },
            onCopyLogs = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = SystemTraceManager.getFormattedLogs()
                clipboard.setPrimaryClip(ClipData.newPlainText("Nova System Trace", text))
                Toast.makeText(context, "System trace copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            onClearLogs = {
                SystemTraceManager.clearLogs()
                Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun SystemTraceContent(
    traceState: SystemTraceState,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(traceState.logs.size) {
        if (traceState.logs.isNotEmpty()) {
            listState.animateScrollToItem(traceState.logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "System Trace & Telemetry",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Real-time RAM, storage & local model execution trace",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("system_trace_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Diagnostic Health Banner
        DiagnosticHealthCard(traceState = traceState)

        Spacer(modifier = Modifier.height(12.dp))

        // Resource Gauges: RAM & Storage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // RAM Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        RoundedCornerShape(14.dp)
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "DEVICE RAM",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.06.em,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (traceState.isLowMemory) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "LOW MEM",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "${traceState.availableRamMb} MB free",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Total: ${traceState.totalRamMb} MB (${traceState.ramUsagePercent}% used)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { (traceState.ramUsagePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (traceState.isLowMemory || traceState.availableRamMb < traceState.estimatedRamRequirementMb)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Model Need: ~${traceState.estimatedRamRequirementMb} MB",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (traceState.availableRamMb < traceState.estimatedRamRequirementMb)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Disk Storage Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        RoundedCornerShape(14.dp)
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "INTERNAL STORAGE",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.06.em,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "${"%.1f".format(traceState.freeDiskMb / 1024f)} GB free",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Total: ${"%.1f".format(traceState.totalDiskMb / 1024f)} GB",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { (traceState.diskUsagePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (traceState.freeDiskMb < 500) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val modelSizeMb = traceState.modelFileSizeBytes / (1024 * 1024)
                    Text(
                        text = if (traceState.modelFileExists) "On disk: ${modelSizeMb} MB" else "Model file: Missing",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (traceState.modelFileExists) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Trace Logs Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "EXECUTION TRACE LOG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.06.em,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (traceState.isExecuting) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp).testTag("system_trace_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onCopyLogs,
                    modifier = Modifier.size(32.dp).testTag("system_trace_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy logs",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier.size(32.dp).testTag("system_trace_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Clear logs",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Console Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14171A))
                .border(BorderStroke(1.dp, Color(0xFF2C3238)), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("system_trace_console")
        ) {
            if (traceState.logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No trace events recorded yet.\nRun a voice or text command to see live telemetry.",
                        fontSize = 12.sp,
                        color = Color(0xFF8899A6),
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(traceState.logs, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = entry.timestamp.takeLast(8),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF657786)
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            val tagColor = when (entry.level) {
                                TraceLogLevel.SUCCESS -> Color(0xFF00BA7C)
                                TraceLogLevel.WARN -> Color(0xFFFFD400)
                                TraceLogLevel.ERROR -> Color(0xFFF91880)
                                TraceLogLevel.INFO -> Color(0xFF1D9BF0)
                            }
                            Text(
                                text = "[${entry.tag}]",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = tagColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = entry.message,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE1E8ED),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticHealthCard(traceState: SystemTraceState) {
    val isIssue = traceState.healthStatus != TraceHealthStatus.READY
    val containerColor = if (isIssue) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    }
    val contentColor = if (isIssue) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val icon = when (traceState.healthStatus) {
        TraceHealthStatus.READY -> Icons.Outlined.CheckCircle
        TraceHealthStatus.CONSTRAINED_RAM, TraceHealthStatus.LOW_STORAGE -> Icons.Outlined.WarningAmber
        TraceHealthStatus.INSUFFICIENT_RAM, TraceHealthStatus.MODEL_NOT_FOUND, TraceHealthStatus.UNVERIFIED -> Icons.Outlined.ErrorOutline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    1.dp,
                    if (isIssue) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = traceState.diagnosisTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = traceState.diagnosisExplanation,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = contentColor.copy(alpha = 0.9f)
            )

            if (!traceState.recommendation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tip: ${traceState.recommendation}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }
    }
}

private val Double.em: androidx.compose.ui.unit.TextUnit get() = (this * 14).sp
