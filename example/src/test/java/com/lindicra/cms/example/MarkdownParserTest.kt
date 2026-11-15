package com.lindicra.cms.example

import org.junit.Assert.*
import org.junit.Test

class MarkdownParserTest {
    @Test fun tableKeepsHeaderAlignmentAndEscapedPipes() {
        val table = MarkdownParser.parse(
            "| 参数 | 说明 | 默认 |\n| :--- | :---: | ---: |\n| `a|b` | 左\\|右 | 1 |"
        ).single() as DocumentBlock.Table
        assertEquals(listOf("参数", "说明", "默认"), table.header)
        assertEquals(listOf(CellAlignment.Start, CellAlignment.Center, CellAlignment.End), table.alignment)
        assertEquals(listOf("`a|b`", "左|右", "1"), table.rows.single())
    }

    @Test fun fencedCodeIsNeverParsedAsTableOrHeading() {
        val block = MarkdownParser.parse("```kotlin\n# literal\n| a | b |\n```").single()
        assertEquals(DocumentBlock.Code("kotlin", "# literal\n| a | b |"), block)
    }

    @Test fun tableAfterParagraphAndShortRowsStaySeparate() {
        val blocks = MarkdownParser.parse("正文\n参数 | 默认\n--- | ---\na | 1\nb |\n\n## 下一节")
        assertTrue(blocks[0] is DocumentBlock.Paragraph)
        val table = blocks[1] as DocumentBlock.Table
        assertEquals(listOf("b", ""), table.rows.last())
        assertEquals(DocumentBlock.Heading(2, "下一节"), blocks[2])
    }

    @Test fun listsQuotesAndUnterminatedCodeRemainReadable() {
        val blocks = MarkdownParser.parse("1. 接入\n- 参数\n> 注意\n\n```kotlin\nval x = 1")
        assertEquals(DocumentBlock.Item("1.", "接入", 0), blocks[0])
        assertEquals(DocumentBlock.Item("•", "参数", 0), blocks[1])
        assertEquals(DocumentBlock.Quote("注意"), blocks[2])
        assertEquals(DocumentBlock.Code("kotlin", "val x = 1"), blocks[3])
    }

    @Test fun documentLinksResolveWithinBundledRoot() {
        assertEquals("components/Scroll.md",
            resolveDocumentAssetPath("components/Page.md", "Scroll.md#参数"))
        assertEquals("06-统一入口与全局配置.md",
            resolveDocumentAssetPath("components/TabBar.md", "../06-统一入口与全局配置.md"))
        assertEquals("components/Page.md",
            resolveDocumentAssetPath("03-集成指南.md", "components/Page.md"))
        assertNull(resolveDocumentAssetPath("components/Page.md", "../../outside.md"))
        assertNull(resolveDocumentAssetPath("components/Page.md", "https://example.com/Page.md"))
    }
}
