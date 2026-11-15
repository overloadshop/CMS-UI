package com.lindicra.cms.example

import androidx.compose.ui.graphics.Color

internal enum class KotlinTokenKind { Keyword, Number, String, Comment, Type, Annotation }

internal data class KotlinToken(val start: Int, val end: Int, val kind: KotlinTokenKind)

/** 文档代码块与代码编辑器共用的一套配色；深浅色分别取值。 */
internal fun kotlinTokenColor(kind: KotlinTokenKind, dark: Boolean): Color = when (kind) {
    KotlinTokenKind.Keyword -> if (dark) Color(0xFFCBA1EF) else Color(0xFF8045A4)
    KotlinTokenKind.Number -> if (dark) Color(0xFFF2B77C) else Color(0xFF9C571D)
    KotlinTokenKind.String -> if (dark) Color(0xFFA3CB91) else Color(0xFF477235)
    KotlinTokenKind.Comment -> if (dark) Color(0xFF8D99A3) else Color(0xFF747C83)
    KotlinTokenKind.Type -> if (dark) Color(0xFF81BCD9) else Color(0xFF276F95)
    KotlinTokenKind.Annotation -> if (dark) Color(0xFFE7B0A2) else Color(0xFF9A4A38)
}

private val kotlinKeywords = setOf(
    "package", "import", "fun", "val", "var", "when", "else", "if", "for", "while", "do",
    "return", "break", "continue", "true", "false", "null", "by", "in", "is", "as", "object",
    "class", "interface", "data", "sealed", "enum", "annotation", "abstract", "open", "override",
    "private", "internal", "public", "protected", "suspend", "inline", "noinline", "crossinline",
    "reified", "const", "lateinit", "companion", "init", "constructor", "this", "super", "throw",
    "try", "catch", "finally", "typealias", "expect", "actual", "operator", "infix", "tailrec",
    "external", "vararg", "out", "where"
)

/** 注释、字符串、字符、注解优先匹配，避免其中的关键字被二次着色；之后才是数字与标识符。 */
private val kotlinPattern = Regex(
    """//[^\n]*|/\*[\s\S]*?(?:\*/|$)""" +
        """|"(?:(?:\\.)|[^"\\])*(?:"|$)""" +
        """|'(?:\\.|[^'\\])*'?""" +
        """|@[A-Za-z_][A-Za-z_0-9]*""" +
        """|\b(?:0[xX][0-9a-fA-F_]+|\d[\d_]*(?:\.\d+)?(?:[fFL])?)\b""" +
        """|\b[A-Za-z_][A-Za-z_0-9]*\b"""
)

internal fun kotlinTokens(source: String): List<KotlinToken> =
    kotlinPattern.findAll(source).mapNotNull { match ->
        val text = match.value
        val kind = when {
            text.startsWith("//") || text.startsWith("/*") -> KotlinTokenKind.Comment
            text.startsWith('"') || text.startsWith('\'') -> KotlinTokenKind.String
            text.startsWith('@') -> KotlinTokenKind.Annotation
            text.first().isDigit() -> KotlinTokenKind.Number
            text in kotlinKeywords -> KotlinTokenKind.Keyword
            text.first().isUpperCase() -> KotlinTokenKind.Type
            else -> return@mapNotNull null
        }
        KotlinToken(match.range.first, match.range.last + 1, kind)
    }.toList()
