package com.lindicra.cms.example

internal sealed interface DocumentBlock {
    data class Heading(val level: Int, val text: String) : DocumentBlock
    data class Paragraph(val text: String) : DocumentBlock
    data class Code(val language: String, val text: String) : DocumentBlock
    data class Item(val marker: String, val text: String, val depth: Int) : DocumentBlock
    data class Quote(val text: String) : DocumentBlock
    data class Table(
        val header: List<String>, val alignment: List<CellAlignment>,
        val rows: List<List<String>>
    ) : DocumentBlock
    data object Rule : DocumentBlock
}

internal enum class CellAlignment { Start, Center, End }

/** 项目文档使用的 Markdown 子集；解析和显示分离，表格不经过段落格式化。 */
internal object MarkdownParser {
    private val heading = Regex("^(#{1,6})\\s+(.+)$")
    private val item = Regex("^(\\s*)([-+*]|\\d+[.)])\\s+(.+)$")
    private val delimiter = Regex("^:?-{3,}:?$")

    fun cells(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        var code = false
        for (character in line.trim()) {
            when {
                escaped -> { current.append(character); escaped = false }
                character == '\\' -> escaped = true
                character == '`' -> { code = !code; current.append(character) }
                character == '|' && !code -> { result += current.toString().trim(); current.clear() }
                else -> current.append(character)
            }
        }
        if (escaped) current.append('\\')
        result += current.toString().trim()
        if (line.trim().startsWith('|') && result.firstOrNull() == "") result.removeAt(0)
        if (line.trim().endsWith('|') && result.lastOrNull() == "") result.removeAt(result.lastIndex)
        return result
    }

    private fun tableAt(lines: List<String>, index: Int): Boolean {
        if (index + 1 >= lines.size || !lines[index].contains('|')) return false
        val headers = cells(lines[index])
        val separators = cells(lines[index + 1])
        return headers.isNotEmpty() && separators.size == headers.size &&
            separators.all { delimiter.matches(it) }
    }

    private fun fence(line: String) = line.trimStart().let {
        it.startsWith("```") || it.startsWith("~~~")
    }

    fun parse(markdown: String): List<DocumentBlock> {
        val lines = markdown.replace("\r\n", "\n").lines()
        val blocks = mutableListOf<DocumentBlock>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> index++
                fence(line) -> {
                    val token = trimmed.takeWhile { it == trimmed.first() }
                    val language = trimmed.removePrefix(token).trim()
                    val code = mutableListOf<String>()
                    index++
                    while (index < lines.size && !lines[index].trim().startsWith(token)) {
                        code += lines[index++]
                    }
                    if (index < lines.size) index++
                    blocks += DocumentBlock.Code(language, code.joinToString("\n"))
                }
                tableAt(lines, index) -> {
                    val headers = cells(line)
                    val alignment = cells(lines[index + 1]).map {
                        when {
                            it.startsWith(':') && it.endsWith(':') -> CellAlignment.Center
                            it.endsWith(':') -> CellAlignment.End
                            else -> CellAlignment.Start
                        }
                    }
                    val rows = mutableListOf<List<String>>()
                    index += 2
                    while (index < lines.size && lines[index].isNotBlank() &&
                        lines[index].contains('|')) {
                        val row = cells(lines[index++])
                        rows += List(headers.size) { row.getOrElse(it) { "" } }
                    }
                    blocks += DocumentBlock.Table(headers, alignment, rows)
                }
                heading.matches(trimmed) -> {
                    val match = heading.matchEntire(trimmed)!!
                    blocks += DocumentBlock.Heading(match.groupValues[1].length, match.groupValues[2])
                    index++
                }
                trimmed in listOf("---", "***", "___") -> {
                    blocks += DocumentBlock.Rule
                    index++
                }
                item.matches(line) -> {
                    val match = item.matchEntire(line)!!
                    val marker = match.groupValues[2].let { if (it.first().isDigit()) it else "•" }
                    blocks += DocumentBlock.Item(marker, match.groupValues[3], match.groupValues[1].length / 2)
                    index++
                }
                trimmed.startsWith(">") -> {
                    val quote = mutableListOf<String>()
                    while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                        quote += lines[index++].trimStart().removePrefix(">").trimStart()
                    }
                    blocks += DocumentBlock.Quote(quote.joinToString("\n"))
                }
                else -> {
                    val paragraph = mutableListOf(line.trim())
                    index++
                    while (index < lines.size && lines[index].isNotBlank() &&
                        !fence(lines[index]) && !heading.matches(lines[index].trim()) &&
                        !item.matches(lines[index]) && !lines[index].trimStart().startsWith(">") &&
                        lines[index].trim() !in listOf("---", "***", "___") &&
                        !tableAt(lines, index)) paragraph += lines[index++].trim()
                    blocks += DocumentBlock.Paragraph(paragraph.joinToString("\n"))
                }
            }
        }
        return blocks
    }
}
