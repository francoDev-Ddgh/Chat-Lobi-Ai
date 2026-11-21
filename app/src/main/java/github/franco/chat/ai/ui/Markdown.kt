package github.franco.chat.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.franco.chat.ai.text.Reasoning

private const val COLLAPSE_AFTER_LINES = 20

private sealed interface Block {
    data class Paragraph(val raw: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class Code(val lang: String, val code: String) : Block
    data object Rule : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
}

private val TABLE_SEP = Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)+\\|?\\s*$")
private val FENCE = Regex("^\\s{0,3}(`{3,}|~{3,})\\s*([^\\s`]*).*$")
private val HEADING = Regex("^\\s{0,3}(#{1,6})\\s+(.*)$")
private val RULE = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")

private fun splitRow(line: String): List<String> {
    var t = line.trim()
    if (t.startsWith("|")) t = t.drop(1)
    if (t.endsWith("|")) t = t.dropLast(1)
    return t.split("|").map { it.trim() }
}

private fun List<String>.padded(n: Int): List<String> = this + List(maxOf(0, n - size)) { "" }

private fun parseBlocks(src: String): List<Block> {
    val lines = src.replace("\r\n", "\n").lines()
    val out = mutableListOf<Block>()
    val buf = StringBuilder()

    fun flush() {
        val text = buf.toString().trim('\n')
        if (text.isNotBlank()) out += Block.Paragraph(text)
        buf.setLength(0)
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val fence = FENCE.find(line)
        if (fence != null) {
            flush()
            val marker = fence.groupValues[1]
            val lang = fence.groupValues[2]
            val code = StringBuilder()
            i++
            while (i < lines.size) {
                if (lines[i].trimStart().startsWith(marker.take(3))) {
                    i++
                    break
                }
                code.appendLine(lines[i])
                i++
            }
            out += Block.Code(lang, code.toString().trimEnd('\n'))
            continue
        }
        if (buf.isEmpty()) {
            val h = HEADING.find(line)
            if (h != null) {
                out += Block.Heading(h.groupValues[1].length, h.groupValues[2].trim())
                i++
                continue
            }
            if (RULE.matches(line)) {
                out += Block.Rule
                i++
                continue
            }
        }
        if (i + 1 < lines.size && line.contains('|') && TABLE_SEP.matches(lines[i + 1])) {
            flush()
            val header = splitRow(line)
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
                rows += splitRow(lines[i])
                i++
            }
            out += Block.Table(header, rows)
            continue
        }
        if (line.isBlank() && buf.isEmpty()) {
            i++
            continue
        }
        if (line.isBlank() && buf.isNotEmpty()) {
            flush()
            i++
            continue
        }
        buf.appendLine(line)
        i++
    }
    flush()
    return out
}

private fun formatParagraph(raw: String): String = raw.lines().joinToString("\n") { line ->
    val t = line.trimStart()
    when {
        t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") -> "•  " + t.drop(2)
        else -> line
    }
}

private fun inlineMarkdown(
    src: String,
    textColor: Color,
    codeBg: Color,
    linkColor: Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = src.length
    while (i < n) {
        val ch = src[i]
        if (ch == '`') {
            val end = src.indexOf('`', i + 1)
            if (end > i + 1) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBg,
                        color = textColor
                    )
                ) { append(src.substring(i + 1, end)) }
                i = end + 1
                continue
            }
        }
        if (ch == '*' && i + 1 < n && src[i + 1] == '*') {
            val end = src.indexOf("**", i + 2)
            if (end > i) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(inlineMarkdown(src.substring(i + 2, end), textColor, codeBg, linkColor))
                }
                i = end + 2
                continue
            }
        }
        if (ch == '*' || ch == '_') {
            val end = src.indexOf(ch, i + 1)
            if (end > i + 1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(inlineMarkdown(src.substring(i + 1, end), textColor, codeBg, linkColor))
                }
                i = end + 1
                continue
            }
        }
        if (ch == '[') {
            val rb = src.indexOf(']', i + 1)
            if (rb > i && rb + 1 < n && src[rb + 1] == '(') {
                val rp = src.indexOf(')', rb + 2)
                if (rp > rb) {
                    val label = src.substring(i + 1, rb)
                    val url = src.substring(rb + 2, rp)
                    withLink(LinkAnnotation.Url(url)) {
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            append(label)
                        }
                    }
                    i = rp + 1
                    continue
                }
            }
        }
        append(ch)
        i++
    }
}

@Composable
fun MessageContent(
    raw: String,
    streaming: Boolean,
    waitSeconds: Int,
    isUser: Boolean
) {
    val parsed = remember(raw) { Reasoning.split(raw) }
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val muted = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f)
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val codeBg = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val linkColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Column {
        // Si el modelo no manda texto fuera del razonamiento, mostramos el razonamiento
        // como respuesta y evitamos que la burbuja quede vacia al plegarse.
        val onlyReasoning = parsed.body.isBlank() && parsed.reasoning.isNotBlank()

        if (parsed.reasoning.isNotBlank()) {
            ReasoningSection(
                text = parsed.reasoning,
                streaming = streaming,
                tint = muted,
                autoExpand = onlyReasoning
            )
        }

        if (parsed.body.isNotBlank()) {
            val blocks = remember(parsed.body) { parseBlocks(parsed.body) }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    blocks.forEach { block ->
                        when (block) {
                            is Block.Code -> CodeBlock(block.lang, block.code, isUser)
                            is Block.Heading -> Text(
                                text = inlineMarkdown(block.text, textColor, codeBg, linkColor),
                                style = if (block.level <= 2) MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.titleSmall,
                                color = textColor
                            )
                            is Block.Table -> TableBlock(block.header, block.rows, textColor, codeBg, linkColor)
                            is Block.Rule -> HorizontalDivider(
                                color = textColor.copy(alpha = 0.15f)
                            )
                            is Block.Paragraph -> Text(
                                text = inlineMarkdown(formatParagraph(block.raw), textColor, codeBg, linkColor),
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                        }
                    }
                }
            }
        } else if (streaming) {
            Text(
                text = if (waitSeconds < 3) "Conectando…" else "Esperando al proveedor… $waitSeconds s",
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
        } else if (onlyReasoning) {
            Text(
                text = "El proveedor no devolvio texto fuera del razonamiento.",
                style = MaterialTheme.typography.labelSmall,
                color = muted
            )
        }
    }
}

@Composable
private fun ReasoningSection(
    text: String,
    streaming: Boolean,
    tint: Color,
    autoExpand: Boolean
) {
    var expanded by remember { mutableStateOf(autoExpand || streaming) }

    LaunchedEffect(streaming, autoExpand) {
        if (autoExpand) expanded = true else if (!streaming) expanded = false
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = tint
            )
            Text(
                text = "Razonamiento",
                style = MaterialTheme.typography.labelMedium,
                color = tint
            )
            if (!expanded) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${text.lines().count { it.isNotBlank() }} líneas",
                    style = MaterialTheme.typography.labelSmall,
                    color = tint.copy(alpha = 0.7f)
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tint,
                    modifier = Modifier.padding(start = 24.dp, bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String, isUser: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lines = remember(code) { code.lines() }
    val total = lines.size
    val collapsible = total > COLLAPSE_AFTER_LINES
    val visible = if (collapsible && !expanded) lines.take(COLLAPSE_AFTER_LINES) else lines

    val container = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
    else MaterialTheme.colorScheme.surface
    val border = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)
    else MaterialTheme.colorScheme.outlineVariant
    val foreground = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val muted = foreground.copy(alpha = 0.65f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lang.ifBlank { "código" }.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { copyToClipboard(context, code) },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("Copiar", style = MaterialTheme.typography.labelSmall)
            }
        }
        HorizontalDivider(color = border)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = visible.joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = foreground,
                modifier = Modifier.padding(12.dp)
            )
        }
        if (collapsible) {
            HorizontalDivider(color = border)
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (expanded) "Ocultar" else "Mostrar todo ($total líneas)",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("código", text))
}

@Composable
private fun TableBlock(
    header: List<String>,
    rows: List<List<String>>,
    textColor: Color,
    codeBg: Color,
    linkColor: Color
) {
    val cols = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val fill = cols <= 4
    val border = textColor.copy(alpha = 0.2f)
    val tableModifier = (if (fill) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()))
        .border(1.dp, border, RoundedCornerShape(6.dp))
    Column(modifier = tableModifier) {
        TableRowView(header.padded(cols), true, fill, textColor, codeBg, linkColor)
        rows.forEach { row ->
            HorizontalDivider(color = border)
            TableRowView(row.padded(cols), false, fill, textColor, codeBg, linkColor)
        }
    }
}

@Composable
private fun TableRowView(
    cells: List<String>,
    header: Boolean,
    fill: Boolean,
    textColor: Color,
    codeBg: Color,
    linkColor: Color
) {
    Row(modifier = if (fill) Modifier.fillMaxWidth() else Modifier) {
        cells.forEach { cell ->
            Text(
                text = inlineMarkdown(cell, textColor, codeBg, linkColor),
                modifier = (if (fill) Modifier.weight(1f) else Modifier.width(140.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.Bold else null,
                color = textColor
            )
        }
    }
}
