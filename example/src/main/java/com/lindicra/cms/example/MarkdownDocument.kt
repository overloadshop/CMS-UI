package com.lindicra.cms.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lindicra.cms.ui.theme.*
import com.lindicra.cms.CMSCodeBlock
import com.lindicra.cms.CMSTable
import com.lindicra.cms.CMSTableAlign

private val LocalDocumentAssetPath = staticCompositionLocalOf { "" }

@Composable
internal fun MarkdownDocument(document: String, assetPath: String) {
    val blocks = remember(document) { MarkdownParser.parse(document) }
    CompositionLocalProvider(LocalDocumentAssetPath provides assetPath) {
    SelectionContainer {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is DocumentBlock.Heading -> DocumentText(
                        block.text, Modifier.fillMaxWidth().padding(top = if (block.level == 1) 0.dp else 12.dp)
                            .semantics { heading() },
                        size = when (block.level) { 1 -> 28; 2 -> 22; 3 -> 18; else -> 16 },
                        weight = FontWeight.SemiBold)
                    is DocumentBlock.Paragraph -> DocumentText(block.text)
                    is DocumentBlock.Code -> DocumentCodeBlock(block.text, block.language)
                    is DocumentBlock.Item -> Row(
                        Modifier.fillMaxWidth().padding(start = (block.depth * 12).dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(block.marker, color = SecondaryText, fontSize = 14.sp,
                            modifier = Modifier.widthIn(min = 18.dp))
                        DocumentText(block.text, Modifier.weight(1f))
                    }
                    is DocumentBlock.Quote -> Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(TertiaryFill)
                    ) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(LocalCMSAccentColor.current))
                        DocumentText(block.text, Modifier.weight(1f).padding(12.dp))
                    }
                    is DocumentBlock.Table -> DocumentTable(block)
                    DocumentBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                }
            }
        }
    }
    }
}

internal fun copyCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("CMS-UI code", code))
    Toast.makeText(context, "已复制代码", Toast.LENGTH_SHORT).show()
}

/** 文档代码块与「完整调用示例」统一使用库组件 [CMSCodeBlock]。 */
@Composable
internal fun DocumentCodeBlock(code: String, language: String = "kotlin") {
    val context = LocalContext.current
    CMSCodeBlock(
        code = code,
        language = language,
        onCopied = { Toast.makeText(context, "已复制代码", Toast.LENGTH_SHORT).show() }
    )
}

/** 表格与代码块共用同一套外观，圆角、斑马纹与触边回弹都交给库组件。 */
@Composable
private fun DocumentTable(table: DocumentBlock.Table) {
    CMSTable(
        columns = table.header.map { plainCell(it) },
        rows = table.rows.map { row -> row.map { plainCell(it) } },
        minColumnWidth = 120.dp,
        alignments = table.alignment.map {
            when (it) {
                CellAlignment.Start -> CMSTableAlign.Start
                CellAlignment.Center -> CMSTableAlign.Center
                CellAlignment.End -> CMSTableAlign.End
            }
        }
    )
}

/** 表格单元格不解析行内链接，去掉 Markdown 标记避免显示出反引号与星号。 */
private fun plainCell(source: String): String = source
    .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
    .replace("`", "")
    .replace("**", "")

/** 行内强调、代码和链接；本地 .md 链接打开对应组件文档。 */
@Composable
private fun DocumentText(
    source: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    weight: FontWeight = FontWeight.Normal,
    alignment: TextAlign = TextAlign.Start
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val accent = LocalCMSAccentColor.current
    val assetPath = LocalDocumentAssetPath.current
    val text = remember(source, accent) {
        buildAnnotatedString {
            val pattern = Regex("(`[^`]+`|\\*\\*[^*]+\\*\\*|\\[[^\\]]+\\]\\([^)]+\\))")
            var previous = 0
            pattern.findAll(source).forEach { match ->
                append(source.substring(previous, match.range.first))
                val value = match.value
                when {
                    value.startsWith('`') -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace,
                        background = accent.copy(alpha = 0.08f))) { append(value.drop(1).dropLast(1)) }
                    value.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(value.drop(2).dropLast(2))
                    }
                    else -> {
                        val label = value.substringAfter('[').substringBefore(']')
                        val target = value.substringAfter("](").dropLast(1)
                        pushStringAnnotation("link", target)
                        withStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)) { append(label) }
                        pop()
                    }
                }
                previous = match.range.last + 1
            }
            append(source.substring(previous))
        }
    }
    ClickableText(text, modifier, style = TextStyle(
        color = PrimaryText, fontFamily = HarmonySansSC, fontSize = size.sp,
        fontWeight = weight, lineHeight = (size * 1.55f).sp, textAlign = alignment
    )) { offset ->
        text.getStringAnnotations("link", offset, offset).firstOrNull()?.let { annotation ->
            val target = annotation.item
            if (target.startsWith("https://") || target.startsWith("http://")) {
                runCatching { uriHandler.openUri(target) }
            } else {
                val path = resolveDocumentAssetPath(assetPath, target)
                val name = path?.substringAfterLast('/')?.removeSuffix(".md")
                val page = if (path?.startsWith("components/") == true)
                    DemoPage.entries.firstOrNull { it.name == name } else null
                when {
                    page != null -> context.startActivity(PreviewActivity.intent(context, page))
                    path == "components/InteractionGuide.md" ->
                        context.startActivity(PreviewActivity.interactionGuideIntent(context))
                    path != null -> context.startActivity(PreviewActivity.documentIntent(context, path))
                }
            }
        }
    }
}

/** Resolve relative Markdown links inside the bundled docs root. */
internal fun resolveDocumentAssetPath(current: String, target: String): String? {
    val relative = target.substringBefore('#')
    if (!relative.endsWith(".md") || relative.contains(':') || relative.startsWith('/')) return null
    val parts = mutableListOf<String>()
    val parent = current.substringBeforeLast('/', "")
    for (part in "$parent/$relative".split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
    }
    return parts.joinToString("/")
}
