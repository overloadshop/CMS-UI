package com.lindicra.cms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lindicra.cms.ui.theme.LocalCMSAccentColor
import com.lindicra.cms.ui.theme.LocalClearIsDark
import com.lindicra.cms.ui.theme.PrimaryText
import com.lindicra.cms.ui.theme.SecondaryText
import com.lindicra.cms.ui.theme.TertiaryFill
import kotlinx.coroutines.delay

/** 代码着色类别；与示例工程的代码编辑器共用同一套语义。 */
enum class CMSCodeTokenKind { Keyword, Number, String, Comment, Type, Annotation }

data class CMSCodeToken(val start: Int, val end: Int, val kind: CMSCodeTokenKind)

/** 深浅色两套着色，保证与 CLEAR 色板共存。 */
fun cmsCodeTokenColor(kind: CMSCodeTokenKind, dark: Boolean): Color = when (kind) {
    CMSCodeTokenKind.Keyword -> if (dark) Color(0xFFCBA1EF) else Color(0xFF8045A4)
    CMSCodeTokenKind.Number -> if (dark) Color(0xFFF2B77C) else Color(0xFF9C571D)
    CMSCodeTokenKind.String -> if (dark) Color(0xFFA3CB91) else Color(0xFF477235)
    CMSCodeTokenKind.Comment -> if (dark) Color(0xFF8D99A3) else Color(0xFF747C83)
    CMSCodeTokenKind.Type -> if (dark) Color(0xFF81BCD9) else Color(0xFF276F95)
    CMSCodeTokenKind.Annotation -> if (dark) Color(0xFFE7B0A2) else Color(0xFF9A4A38)
}

private val cmsKotlinKeywords = setOf(
    "package", "import", "fun", "val", "var", "when", "else", "if", "for", "while", "do",
    "return", "break", "continue", "true", "false", "null", "by", "in", "is", "as", "object",
    "class", "interface", "data", "sealed", "enum", "annotation", "abstract", "open", "override",
    "private", "internal", "public", "protected", "suspend", "inline", "noinline", "crossinline",
    "reified", "const", "lateinit", "companion", "init", "constructor", "this", "super", "throw",
    "try", "catch", "finally", "typealias", "expect", "actual", "operator", "infix", "tailrec",
    "external", "vararg", "out", "where"
)

private val cmsKotlinPattern = Regex(
    """//[^\n]*|/\*[\s\S]*?(?:\*/|$)""" +
        """|"(?:(?:\\.)|[^"\\])*(?:"|$)""" +
        """|'(?:\\.|[^'\\])*'?""" +
        """|@[A-Za-z_][A-Za-z_0-9]*""" +
        """|\b(?:0[xX][0-9a-fA-F_]+|\d[\d_]*(?:\.\d+)?(?:[fFL])?)\b""" +
        """|\b[A-Za-z_][A-Za-z_0-9]*\b"""
)

/** Kotlin 代码分段着色；注释、字符串、注解优先，避免内部关键字被二次着色。 */
fun cmsKotlinTokens(source: String): List<CMSCodeToken> =
    cmsKotlinPattern.findAll(source).mapNotNull { match ->
        val text = match.value
        val kind = when {
            text.startsWith("//") || text.startsWith("/*") -> CMSCodeTokenKind.Comment
            text.startsWith('"') || text.startsWith('\'') -> CMSCodeTokenKind.String
            text.startsWith('@') -> CMSCodeTokenKind.Annotation
            text.first().isDigit() -> CMSCodeTokenKind.Number
            text in cmsKotlinKeywords -> CMSCodeTokenKind.Keyword
            text.first().isUpperCase() -> CMSCodeTokenKind.Type
            else -> return@mapNotNull null
        }
        CMSCodeToken(match.range.first, match.range.last + 1, kind)
    }.toList()

/**
 * 代码块 · Code Block
 *
 * 与 [CMSTable] 共用同一套外观：可调圆角、是否包边、最大高度 / 固定高度 / 无限高度、
 * 头行（语言 + 复制）与四边阻尼回弹。代码超出视口时横向滚动，到边有一次轻触反馈。
 *
 * @param cornerRadius 0.dp 为直角
 * @param height 固定高度；内容超出时纵向滚动
 * @param maxHeight 最大高度；内容不足时按内容收缩
 * @param wrap 是否自动换行（默认不换行，横向滚动）
 * @param showHeader 显示语言与复制按钮的头行
 * @param copyable 头行是否提供复制
 * @param highlight 是否按 Kotlin 语法着色
 * @param edgeFeedback 是否在触边时回弹并触发轻触
 */
@Composable
fun CMSCodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    language: String? = "kotlin",
    cornerRadius: Dp = 14.dp,
    height: Dp? = null,
    maxHeight: Dp? = null,
    wrap: Boolean = false,
    showHeader: Boolean = true,
    copyable: Boolean = true,
    highlight: Boolean = true,
    fontSize: TextUnit = 12.sp,
    lineHeight: TextUnit = 19.sp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    surfaceColor: Color = TertiaryFill,
    dividerColor: Color = SecondaryText.copy(alpha = 0.16f),
    languageColor: Color = SecondaryText,
    accentColor: Color = LocalCMSAccentColor.current,
    edgeFeedback: Boolean = true,
    onCopied: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val dark = LocalClearIsDark.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    val text = remember(code, dark, highlight) {
        if (!highlight) {
            buildAnnotatedString { append(code) }
        } else {
            buildAnnotatedString {
                append(code)
                cmsKotlinTokens(code).forEach {
                    addStyle(SpanStyle(color = cmsCodeTokenColor(it.kind, dark)), it.start, it.end)
                }
            }
        }
    }
    val shape = RoundedCornerShape(cornerRadius)
    val bodyHeight = when {
        height != null -> Modifier.height(height)
        maxHeight != null -> Modifier.heightIn(max = maxHeight)
        else -> Modifier
    }
    Column(modifier.clip(shape).background(surfaceColor)) {
        if (showHeader) {
            Row(
                Modifier.fillMaxWidth().height(34.dp)
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (language != null) {
                    Text(language.lowercase(), color = languageColor, fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 0.8.sp)
                }
                Spacer(Modifier.weight(1f))
                if (copyable) {
                    Box(
                        Modifier.clip(RoundedCornerShape(7.dp))
                            .clickable {
                                copyCmsCode(context, code)
                                copied = true
                                onCopied?.invoke()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(if (copied) "已复制" else "复制", color = accentColor, fontSize = 11.5.sp)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(0.6.dp).background(dividerColor))
        }
        CMEdgeScrollBox(
            modifier = Modifier.fillMaxWidth().then(bodyHeight),
            horizontal = !wrap,
            vertical = height != null || maxHeight != null,
            elastic = edgeFeedback
        ) {
            SelectionContainer {
                Text(
                    text, color = PrimaryText, fontFamily = FontFamily.Monospace,
                    fontSize = fontSize, lineHeight = lineHeight, softWrap = wrap,
                    modifier = Modifier.padding(contentPadding)
                )
            }
        }
    }
}

/** 复制到剪贴板；文案由宿主决定时改用 [CMSCodeBlock.onCopied] 自行提示。 */
fun copyCmsCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("CMS code", code))
}
