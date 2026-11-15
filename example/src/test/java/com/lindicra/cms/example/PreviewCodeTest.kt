package com.lindicra.cms.example

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PreviewCodeTest {
    @Test fun allComponentsExportAndParseTheirCurrentValues() {
        DemoPage.entries.forEach { page ->
            val definition = PreviewDefinition(page)
            val defaults = definition.defaults()
            assertEquals(page.name, defaults, definition.parse(definition.code(defaults)))
            val changed = defaults.toMutableMap().apply {
                put("themeColor", "Color(0xFFAA33BB)")
                put("controlColor", "Color(0x8033AA77)")
                put("appearance", "2")
                put("previewTopBar", (!defaults.getValue("previewTopBar").toBoolean()).toString())
            }
            assertEquals(changed, definition.parse(definition.code(changed)))
        }
    }

    @Test fun normalPreviewsHaveTopBarsAndBackgroundPreviewsCanOmitThem() {
        DemoPage.entries.forEach { page ->
            val definition = PreviewDefinition(page)
            assertEquals(page.name, page != DemoPage.GlassButton && page != DemoPage.ContentSource &&
                page != DemoPage.TabBar,
                definition.defaults().getValue("previewTopBar").toBoolean())
        }
    }

    @Test fun codeAllowsCustomNumbersBeyondQuickPresets() {
        val definition = PreviewDefinition(DemoPage.Swiper)
        val source = definition.code(definition.defaults())
            .replace("val itemCount = 3", "val itemCount = 11")
            .replace("val arrowSize = 28.0.dp", "val arrowSize = 39.dp")
            .replace("val direction = CMSCarouselDirection.Horizontal", "val direction = CMSCarouselDirection.Vertical")
        val parsed = definition.parse(source)
        assertEquals("11", parsed["itemCount"])
        assertEquals("39.0.dp", parsed["arrowSize"])
        assertEquals("CMSCarouselDirection.Vertical", parsed["direction"])
    }

    @Test fun rejectsInvalidValuesAndUnsupportedCodeWithoutChangingPreviousValues() {
        val definition = PreviewDefinition(DemoPage.Swiper)
        val previous = definition.defaults()
        val source = definition.code(previous)
        listOf(
            source.replace("val itemCount = 3", "val itemCount = 0"),
            source.replace("val autoPlay = false", "val autoPlay = unknown"),
            source.replace("val arrowSize = 28.0.dp", "val arrowSize = 10000.dp"),
            source.replace("Color(0xFF3F78D1)", "Color(0xGGFFFFFF)"),
            source.replace("CMSCarouselDirection.Horizontal", "CMSCarouselDirection.Diagonal"),
            source.replace("CMS.Content.PageView(", "OtherWidget("),
            source.replace("val itemCount = 3", "val itemCount = 3\n    val itemCount = 8"),
            source.replace("val indicatorActiveWidth = 14.0.dp", "val indicatorActiveWidth = 2.dp")
        ).forEach { invalid ->
            assertTrue(runCatching { definition.parse(invalid) }.isFailure)
            assertEquals(previous, definition.defaults())
        }
    }

    @Test fun independentInstancesStartWithDefaults() {
        val first = PreviewDefinition(DemoPage.Switch).defaults().toMutableMap()
        first["controlColor"] = "Color(0xFFCC44AA)"
        first["initialChecked"] = "false"
        val second = PreviewDefinition(DemoPage.Switch).defaults()
        assertEquals("true", second["initialChecked"])
        assertEquals("Color(0xFF5F9BFF)", second["controlColor"])
        assertNotEquals(first["controlColor"], second["controlColor"])
    }

    @Test fun errorsPointToTheEditedLineBeforePreviewCanOpen() {
        val definition = PreviewDefinition(DemoPage.Swiper)
        val source = definition.code(definition.defaults())
        listOf(
            "val itemCount = 3" to "val itemCount = 0",
            "CMS.Content.PageView(" to "OtherWidget(",
            "val indicatorActiveWidth = 14.0.dp" to "val indicatorActiveWidth = 2.dp"
        ).forEach { (before, after) ->
            val invalid = source.replace(before, after)
            val error = runCatching { definition.parse(invalid) }.exceptionOrNull()
            assertTrue(error is PreviewCodeError)
            val expectedLine = invalid.lines().indexOfFirst { it.contains(after) } + 1
            assertEquals(expectedLine, (error as PreviewCodeError).line)
            assertTrue(error.message!!.contains("第 $expectedLine 行"))
        }
    }

    @Test fun highlightingDoesNotInterpretKeywordsInsideCommentsAndStrings() {
        val source = "val color = Color(0xFF33AA77) // false 42\nval title = \"if true\""
        val tokens = kotlinTokens(source)
        assertEquals(listOf("val", "val"), tokens.filter { it.kind == KotlinTokenKind.Keyword }
            .map { source.substring(it.start, it.end) })
        assertEquals("\"if true\"", tokens.single { it.kind == KotlinTokenKind.String }
            .let { source.substring(it.start, it.end) })
        assertEquals("// false 42", tokens.single { it.kind == KotlinTokenKind.Comment }
            .let { source.substring(it.start, it.end) })
        tokens.forEach { assertTrue(it.start >= 0 && it.end <= source.length && it.end > it.start) }
    }

    @Test fun exportCompileFixtures() {
        val directory = System.getProperty("cms.fixtureDirectory") ?: return
        val destination = File(directory).apply { mkdirs() }
        DemoPage.entries.forEach { page ->
            val definition = PreviewDefinition(page)
            File(destination, "${page.name}Example.kt")
                .writeText("package com.lindicra.cms.example.exportcheck\n\n" +
                    definition.code(definition.defaults()))
        }
        // Compile old and new entry points together, sharing enum/data/state types.
        File(destination, "CompatibilityExample.kt").writeText("""
            @file:Suppress("DEPRECATION")
            package com.lindicra.cms.example.exportcheck
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.IntRect
            import dev.chrisbanes.haze.HazeState
            import com.lindicra.cms.*

            @Composable
            fun CompatibilityExample(haze: HazeState) {
                val oldItems = listOf(CMSelectMenuItem("A"))
                val items: List<CMSMenuItem> = oldItems
                val touch: CMSMenuTouchState = rememberCMMenuTouchState()
                val oldTouch: CMMenuTouchState = rememberCMSMenuTouchState()
                CMSBackButton({})
                CMSGlassBackButton({})
                CMSGlassContentSource(hazeState = haze) {}
                CMGlassContentSource(hazeState = haze) {}
                CMSSlider(value = 0.5f, thumbStyle = CMSProgressDot.Hollow)
                CMSProgressBar(progress = 0.5f, dotStyle = CMSSliderThumbStyle.Solid)
                CMSPopupMenu(haze, items, 0, IntRect.Zero, false, {}, {}, touchState = oldTouch)
                CMSelectMenu(haze, oldItems, 0, IntRect.Zero, false, {}, {}, touchState = touch)
                CMSScrollView(scrollbarStyle = CMScrollbarStyle.Alphabet,
                    scrollbarSections = listOf(CMScrollSection("A"))) {}
                CMScrollBox(scrollbarStyle = CMSScrollbarStyle.Sections,
                    scrollbarSections = listOf(CMSScrollSection("A"))) {}
                CMSCarousel(1, direction = CMSwipeDirection.Vertical,
                    indicatorStyle = CMSNavBar.DigitIndicator) {}
                CMSwiper(1, direction = CMSCarouselDirection.Horizontal,
                    navBar = CMSCarouselIndicator.DotIndicator) {}
                CMSSegmentedControl(listOf("A", "B"), 0, {})
                CMSCapsuleTabs(listOf("A", "B"), 0, {})
                Modifier.cmsMenuAnchor({})
                Modifier.cmMenuAnchor({})
                CMSBottomSheet({}) {}
                CMHalfModal({}) {}
            }
        """.trimIndent())
    }
}
