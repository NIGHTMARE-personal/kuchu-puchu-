package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.OrganizePlan
import com.example.model.OrganizeRule

@Composable
fun DownloadsPlanCard(
    plan: OrganizePlan,
    onRuleToggle: (ruleId: String, isChecked: Boolean) -> Unit,
    onExecute: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("downloads_plan_card"),
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
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PLAN CONFIRMATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.08.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = plan.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.testTag("downloads_total_count_badge")
                ) {
                    Text(
                        text = "${plan.totalFilesInDownloads} files found",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (plan.cappedNote != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = plan.cappedNote,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Rule Rows
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                plan.rules.forEach { rule ->
                    RuleRow(
                        rule = rule,
                        onToggle = { isChecked -> onRuleToggle(rule.id, isChecked) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Files staying in place & safety guarantee
            Text(
                text = "${plan.filesStayingInPlace} files stay in place · Nothing is deleted — files are moved.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("downloads_stay_in_place_text")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Actions: Execute (primary) / Cancel (secondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("downloads_plan_cancel_button"),
                    shape = CircleShape
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val hasFilesToMove = plan.totalFilesToMove > 0
                Button(
                    onClick = onExecute,
                    enabled = hasFilesToMove,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("downloads_plan_execute_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (hasFilesToMove) "Organize (${plan.totalFilesToMove})" else "No files selected",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleRow(
    rule: OrganizeRule,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = rule.isValid) { onToggle(!rule.isEnabled) }
            .testTag("downloads_rule_row_${rule.id}"),
        shape = RoundedCornerShape(12.dp),
        color = if (rule.isValid && rule.isEnabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        },
        border = BorderStroke(
            0.5.dp,
            if (rule.isValid && rule.isEnabled) MaterialTheme.colorScheme.outlineVariant
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rule.isValid && rule.isEnabled,
                    onCheckedChange = { if (rule.isValid) onToggle(it) },
                    enabled = rule.isValid,
                    modifier = Modifier.testTag("downloads_rule_checkbox_${rule.id}")
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = rule.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (rule.isValid) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error
                    )
                    if (!rule.isValid) {
                        Text(
                            text = rule.validationError ?: "invalid destination",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = if (rule.fileCount > 0 && rule.isValid) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "${rule.fileCount}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (rule.fileCount > 0 && rule.isValid) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
