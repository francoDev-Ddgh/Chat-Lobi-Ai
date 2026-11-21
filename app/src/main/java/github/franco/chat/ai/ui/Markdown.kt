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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
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
    data class Quote(val blocks: List<Block>) : Block
    data class ListBlock(val items: List<MdItem>) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
    data object Rule : Block
}

private data class MdItem(
    val depth: Int,
    val ordered: Boolean,
    val number: Int,
    val text: String,
    val checked: Boolean?
)

private val FENCE = Regex("^\\s{0,3}(`{3,}|~{3,})\\s*([^\\s`]*).*$")
private val HEADING = Regex("^\\s{0,3}(#{1,6})\\s+(.*)$")
private val RULE = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
private val TABLE_SEP = Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)+\\|?\\s*$")
private val QUOTE = Regex("^\\s{0,3}>\\s?(.*)$")
private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val ORDERED = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
private val TASK = Regex("^\\[( |x|X)\\]\\s+(.*)$")
private const val ESCAPABLE = "\\`*_{}[]()#+-.!~|>"

private fun splitRow(line: String): List<String> {
    var t = line.trim()
    if (t.startsWith("|")) t = t.drop(1)
    if (t.endsWith("|")) t = t.dropLast(1)
    return t.split("|").map { it.trim() }
}

private fun List<String>.padded(n: Int): List<String> = this + List(maxOf(0, n - size)) { "" }

private fun parseItem(depth: Int, ordered: Boolean, number: Int, body: String): MdItem {
    val task = TASK.find(body)
    return if (task != null) {
        MdItem(depth, ordered, number, task.groupValues[2], task.groupValues[1].lowercase() == "x")
    } else {
        MdItem(depth, ordered, number, body, null)
    }
}

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

        if (line.isBlank()) {
            flush()
            i++
            continue
        }

        val h = HEADING.find(line)
        if (h != null) {
            flush()
            out += Block.Heading(h.groupValues[1].length, h.groupValues[2].trim())
            i++
            continue
        }

        if (RULE.matches(line)) {
            flush()
            out += Block.Rule
            i++
            continue
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

        if (QUOTE.matches(line)) {
            flush()
            val inner = mutableListOf<String>()
            while (i < lines.size) {
                val q = QUOTE.find(lines[i]) ?: break
                inner += q.groupValues[1]
                i++
            }
            out += Block.Quote(parseBlocks(inner.joinToString("\n")))
            continue
        }

        if (BULLET.matches(line) || ORDERED.matches(line)) {
            flush()
            val items = mutableListOf<MdItem>()
            while (i < lines.size) {
                val l = lines[i]
                if (l.isBlank() || RULE.matches(l)) break
                val ord = ORDERED.find(l)
                val bul = BULLET.find(l)
                if (ord != null) {
                    items += parseItem(ord.groupValues[1].length / 2, true, ord.groupValues[2].toIntOrNull() ?: 1, ord.groupValues[3])
                } else if (bul != null) {
                    items += parseItem(bul.groupValues[1].length / 2, false, 0, bul.groupValues[2])
                } else if (l.first().isWhitespace() && items.isNotEmpty()) {
                    val last = items.removeAt(items.lastIndex)
                    items += last.copy(text = last.text + " " + l.trim())
                } else {
                    break
                }
                i++
            }
            out += Block.ListBlock(items)
            continue
        }

        buf.appendLine(line)
        i++
    }
    flush()
    return out
}

private fun parseLink(src: String, start: Int): Triple<String, String, Int>? {
    if (src[start] != '[') return null
    val rb = src.indexOf(']', start + 1)
    if (rb < 0 || rb + 1 >= src.length || src[rb + 1] != '(') return null
    val rp = src.indexOf(')', rb + 2)
    if (rp < 0) return null
    return Triple(src.substring(start + 1, rb), src.substring(rb + 2, rp).trim(), rp + 1)
}

private fun findClosing(src: String, marker: Char, from: Int): Int {
    var j = from
    while (j < src.length) {
        if (src[j] == marker) {
            val nextSame = j + 1 < src.length && src[j + 1] == marker
            val prevSame = src[j - 1] == marker
            if (!nextSame && !prevSame) return j
        }
        j++
    }
    return -1
}

private fun inlineMarkdown(
    src: String,
    textColor: Color,
    codeBg: Color,
    linkColor: Color
): AnnotatedString = buildAnnotatedString {
    val linkStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    fun styled(style: SpanStyle, inner: String) {
        withStyle(style) { append(inlineMarkdown(inner, textColor, codeBg, linkColor)) }
    }
    val n = src.length
    var i = 0
    while (i < n) {
        val ch = src[i]

        if (ch == '\\' && i + 1 < n && src[i + 1] in ESCAPABLE) {
            append(src[i + 1])
            i += 2
            continue
        }

        if (ch == '`') {
            var k = 0
            while (i + k < n && src[i + k] == '`') k++
            val end = src.indexOf("`".repeat(k), i + k)
            if (end > 0) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = textColor)) {
                    append(src.substring(i + k, end).trim())
                }
                i = end + k
                continue
            }
        }

        if (ch == '!' && i + 1 < n && src[i + 1] == '[') {
            val link = parseLink(src, i + 1)
            if (link != null) {
                withLink(LinkAnnotation.Url(link.second)) {
                    withStyle(linkStyle) { append("Imagen: " + link.first) }
                }
                i = link.third
                continue
            }
        }

        if (ch == '[') {
            val link = parseLink(src, i)
            if (link != null) {
                withLink(LinkAnnotation.Url(link.second)) {
                    withStyle(linkStyle) { append(link.first) }
                }
                i = link.third
                continue
            }
        }

        if (ch == '<') {
            val end = src.indexOf('>', i + 1)
            if (end > i + 1 && (src.startsWith("http://", i + 1) || src.startsWith("https://", i + 1))) {
                val url = src.substring(i + 1, end)
                withLink(LinkAnnotation.Url(url)) { withStyle(linkStyle) { append(url) } }
                i = end + 1
                continue
            }
        }

        if (ch == 'h' && (src.startsWith("https://", i) || src.startsWith("http://", i)) &&
            (i == 0 || !src[i - 1].isLetterOrDigit())
        ) {
            var end = i
            while (end < n && !src[end].isWhitespace() && src[end] != '<' && src[end] != '>') end++
            val url = src.substring(i, end).trimEnd('.', ',', ';', ':', '!', '?', ')')
            withLink(LinkAnnotation.Url(url)) { withStyle(linkStyle) { append(url) } }
            i += url.length
            continue
        }

        if (src.startsWith("~~", i)) {
            val end = src.indexOf("~~", i + 2)
            if (end > i + 2) {
                styled(SpanStyle(textDecoration = TextDecoration.LineThrough), src.substring(i + 2, end))
                i = end + 2
                continue
            }
        }

        if (src.startsWith("**", i) || src.startsWith("__", i)) {
            val marker = src.substring(i, i + 2)
            val end = src.indexOf(marker, i + 2)
            if (end > i + 2) {
                styled(SpanStyle(fontWeight = FontWeight.Bold), src.substring(i + 2, end))
                i = end + 2
                continue
            }
        }

        if (ch == '*' || (ch == '_' && (i == 0 || !src[i - 1].isLetterOrDigit()))) {
            val end = findClosing(src, ch, i + 1)
            if (end > i + 1 && (ch == '*' || end + 1 >= n || !src[end + 1].isLetterOrDigit())) {
                styled(SpanStyle(fontStyle = FontStyle.Italic), src.substring(i + 1, end))
                i = end + 1
                continue
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
                RenderBlocks(blocks, textColor, codeBg, linkColor, muted, isUser)
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
private fun RenderBlocks(
    blocks: List<Block>,
    textColor: Color,
    codeBg: Color,
    linkColor: Color,
    muted: Color,
    isUser: Boolean
) {
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
                is Block.Rule -> HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                is Block.Paragraph -> Text(
                    text = inlineMarkdown(block.raw, textColor, codeBg, linkColor),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                is Block.Table -> TableBlock(block.header, block.rows, textColor, codeBg, linkColor)
                is Block.ListBlock -> ListBlock(block.items, textColor, codeBg, linkColor)
                is Block.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(muted, RoundedCornerShape(2.dp))) { }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        RenderBlocks(block.blocks, textColor, codeBg, linkColor, muted, isUser)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListBlock(
    items: List<MdItem>,
    textColor: Color,
    codeBg: Color,
    linkColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(start = (item.depth * 16).dp)) {
                val checked = item.checked
                if (checked != null) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(18.dp)
                            .border(1.dp, textColor, RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (checked) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    val marker = if (item.ordered) "${item.number}." else if (item.depth % 2 == 0) "•" else "–"
                    Text(
                        text = marker,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.width(24.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = inlineMarkdown(item.text, textColor, codeBg, linkColor),
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
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
