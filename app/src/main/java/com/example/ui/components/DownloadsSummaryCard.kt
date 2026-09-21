package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.OrganizeSummaryState

@Composable
fun DownloadsSummaryCard(
    summaryState: OrganizeSummaryState,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("downloads_summary_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (summaryState.wasCancelled) "BATCH STOPPED" else "ORGANIZATION COMPLETE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.08.sp,
                        color = if (summaryState.wasCancelled) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (summaryState.isUndone) {
                            "Moves Undone"
                        } else {
                            val skippedLabel = if (summaryState.skippedDetails.isNotEmpty()) {
                                " · ${summaryState.skippedCount} skipped: ${summaryState.skippedDetails.joinToString(", ")}"
                            } else if (summaryState.skippedCount > 0) {
                                " · ${summaryState.skippedCount} skipped"
                            } else ""
                            "Moved ${summaryState.movedCount} files$skippedLabel"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("downloads_summary_title")
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("downloads_summary_dismiss_button")
                ) {
                    Text(
                        text = "Dismiss",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val statusDescription = when {
                summaryState.isUndone -> {
                    summaryState.undoSummary ?: "Files have been restored back to Downloads."
                }
                summaryState.wasCancelled -> {
                    "Operation cancelled. ${summaryState.movedCount} files were moved before stopping. You can undo anytime."
                }
                summaryState.skippedCount > 0 -> {
                    val detailSnippet = if (summaryState.skippedDetails.isNotEmpty()) {
                        "${summaryState.skippedCount} skipped: ${summaryState.skippedDetails.joinToString(", ")}."
                    } else {
                        "${summaryState.skippedCount} locked or inaccessible files were kept in place."
                    }
                    "${summaryState.movedCount} files moved successfully. $detailSnippet"
                }
                else -> {
                    "All ${summaryState.movedCount} planned files were safely organized into their respective folders."
                }
            }

            Text(
                text = statusDescription,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("downloads_summary_description")
            )

            if (!summaryState.isUndone && summaryState.movedCount > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onUndo,
                        enabled = !summaryState.isUndoing,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("downloads_undo_button")
                    ) {
                        if (summaryState.isUndoing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Restoring...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "UNDO",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
