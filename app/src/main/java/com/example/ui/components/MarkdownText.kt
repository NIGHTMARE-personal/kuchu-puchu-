package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownBlock {
    data class Paragraph(val lines: List<String>) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 21.sp
) {
    val context = LocalContext.current

    val blocks = remember(markdown) {
        try {
            parseMarkdownBlocks(markdown)
        } catch (_: Throwable) {
            listOf(MarkdownBlock.Paragraph(listOf(markdown)))
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    RenderCodeBlock(
                        codeBlock = block,
                        onCopy = { codeToCopy ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("code", codeToCopy)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    RenderParagraph(
                        lines = block.lines,
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderParagraph(
    lines: List<String>,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in lines) {
            val trimmed = line.trimStart()
            when {
                // Bullet list item
                trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("+ ") || trimmed.startsWith("• ") -> {
                    val bulletContent = trimmed.substring(2)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = formatInlineMarkdown(bulletContent),
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Numbered list item
                trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                    val numPrefix = trimmed.substringBefore(". ") + "."
                    val itemContent = trimmed.substringAfter(". ")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = numPrefix,
                            fontSize = fontSize,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = formatInlineMarkdown(itemContent),
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Normal paragraph line
                else -> {
                    Text(
                        text = formatInlineMarkdown(line),
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderCodeBlock(
    codeBlock: MarkdownBlock.CodeBlock,
    onCopy: (String) -> Unit
) {
    val containerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .background(containerBg)
            .testTag("code_block")
    ) {
        // Code Block Header with Language and dedicated Copy button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = codeBlock.language.ifBlank { "code" },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onCopy(codeBlock.code) },
                modifier = Modifier
                    .size(28.dp)
                    .testTag("copy_code_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy code",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Monospace Code Body with horizontal scroll
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(12.dp)
        ) {
            Text(
                text = codeBlock.code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                lineHeight = 17.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Splits markdown text into paragraphs and fenced code blocks.
 */
private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var inCodeBlock = false
    var codeLang = ""
    val codeBuffer = StringBuilder()
    val paragraphLines = mutableListOf<String>()

    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                // Closing code block
                blocks.add(MarkdownBlock.CodeBlock(language = codeLang, code = codeBuffer.toString().trimEnd()))
                codeBuffer.clear()
                codeLang = ""
                inCodeBlock = false
            } else {
                // Opening code block
                if (paragraphLines.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Paragraph(paragraphLines.toList()))
                    paragraphLines.clear()
                }
                codeLang = line.trim().removePrefix("```").trim()
                inCodeBlock = true
            }
        } else {
            if (inCodeBlock) {
                codeBuffer.append(line).append("\n")
            } else {
                if (line.isBlank() && paragraphLines.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Paragraph(paragraphLines.toList()))
                    paragraphLines.clear()
                } else if (line.isNotBlank()) {
                    paragraphLines.add(line)
                }
            }
        }
    }

    if (inCodeBlock) {
        blocks.add(MarkdownBlock.CodeBlock(language = codeLang, code = codeBuffer.toString().trimEnd()))
    } else if (paragraphLines.isNotEmpty()) {
        blocks.add(MarkdownBlock.Paragraph(paragraphLines.toList()))
    }

    return blocks
}

/**
 * Formats inline Markdown elements like **bold**, __bold__, and `code`.
 */
private fun formatInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("""(\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`)""")
        val matches = regex.findAll(text)

        for (match in matches) {
            val range = match.range
            if (range.first > cursor) {
                append(text.substring(cursor, range.first))
            }

            when {
                // **bold** or __bold__
                match.value.startsWith("**") || match.value.startsWith("__") -> {
                    val content = match.groupValues[2].ifEmpty { match.groupValues[3] }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(content)
                    }
                }
                // `inline code`
                match.value.startsWith("`") -> {
                    val content = match.groupValues[4]
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(content)
                    }
                }
                else -> {
                    append(match.value)
                }
            }
            cursor = range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
