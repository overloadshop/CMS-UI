package com.lindicra.cms.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import com.lindicra.cms.*
import com.lindicra.cms.ui.theme.*

/** 代码块演示内容：覆盖关键字、字符串、数字、类型、注解与注释。 */
private val SampleCodeBlock = """
@Composable
fun Greeting(name: String, count: Int) {
    // 语法着色：关键字 / 字符串 / 数字 / 类型
    val retries = 3
    Text("Hello, ${'$'}name × ${'$'}count", fontSize = 14.sp)
}
""".trimIndent()

private val SampleTableRows = listOf(
    listOf("圆角", "0 到 28dp", "0.dp 为直角"),
    listOf("最大高度", "Dp?", "null 为无限高度"),
    listOf("触边", "四边", "回弹 + 轻触"),
    listOf("横向", "自动", "列宽不足时滚动")
)

class PreviewActivity : ExampleActivity() {
    companion object {
        internal fun intent(context: Context, page: DemoPage) =
            Intent(context, PreviewActivity::class.java).putExtra("page", page.name)
        internal fun interactionGuideIntent(context: Context) =
            Intent(context, PreviewActivity::class.java).putExtra("interactionGuide", true)
        internal fun documentIntent(context: Context, assetPath: String) =
            Intent(context, PreviewActivity::class.java).putExtra("document", assetPath)
        internal fun documentCenterIntent(context: Context) =
            Intent(context, PreviewActivity::class.java).putExtra("documentCenter", true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        setExampleContent {
            val documentPath = intent.getStringExtra("document")
            if (intent.getBooleanExtra("documentCenter", false)) DocumentCenterScreen { finish() }
            else if (documentPath != null) AssetDocumentScreen(documentPath) { finish() }
            else if (intent.getBooleanExtra("interactionGuide", false)) InteractionGuideDocument { finish() }
            else ComponentDocumentScreen(DemoPage.from(intent.getStringExtra("page"))) { finish() }
        }
    }
}

private data class DocumentEntry(
    val title: String,
    val summary: String,
    val assetPath: String,
    val page: DemoPage? = null
) {
    fun matches(query: String) = query.isEmpty() ||
        "$title $summary $assetPath".contains(query, ignoreCase = true)
}

@Composable
private fun DocumentCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val guides = remember {
        listOf(
            DocumentEntry("开始接入", "组件库依赖、主题与 Pager + Page", "03-集成指南.md"),
            DocumentEntry("组件手册", "API 总览与基础用法", "01-组件手册.md"),
            DocumentEntry("统一入口与全局配置", "CMS 门面、默认值与作用域", "06-统一入口与全局配置.md"),
            DocumentEntry("动效指南", "CLEAR Motion · 弹簧与手势", "02-CLEAR-Motion.md"),
            DocumentEntry("触感与宿主接口", "震动、返回与业务连接", "04-触感与宿主接口.md"),
            DocumentEntry("组件命名", "统一名称与公开入口", "05-组件命名.md"),
            DocumentEntry("交互接入", "触控优先级、玻璃融合、返回历史与规范覆盖", "components/InteractionGuide.md")
        )
    }
    val groups = remember {
        DemoCategory.entries.map { category ->
            category to DemoPage.entries.filter { it.category == category }.map { page ->
                DocumentEntry(page.title, page.englishName, "components/${page.name}.md", page)
            }
        }
    }
    val search = query.trim()
    val matchingGuides = guides.filter { it.matches(search) }
    val matchingGroups = groups.map { (category, entries) ->
        category to entries.filter { it.matches(search) }
    }.filter { it.second.isNotEmpty() }
    val haze = remember { HazeState() }
    CMS.Appearance.Theme(darkTheme = resolveDark(), hazState = haze,
        glassMaterial = GlassMaterial(DemoStore.advanced, DemoStore.clarity)) {
        StudioFrame("文档中心", haze, onBack) {
            CMS.Controls.TextField(query, { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = "搜索文档名称或组件", variant = CMSTextFieldVariant.Search,
                showClearButton = true)
            if (matchingGuides.isNotEmpty()) {
                CMS.Content.SectionTitle("指南与教程")
                CMS.Content.Section {
                    matchingGuides.forEach { entry ->
                        CMS.Content.ListRow(entry.title, entry.summary) {
                            context.startActivity(PreviewActivity.documentIntent(context, entry.assetPath))
                        }
                    }
                }
            }
            matchingGroups.forEach { (category, entries) ->
                CMS.Content.SectionTitle(category.title)
                CMS.Content.Section {
                    entries.forEach { entry ->
                        CMS.Content.ListRow(entry.title, entry.summary) {
                            entry.page?.let { context.startActivity(PreviewActivity.intent(context, it)) }
                        }
                    }
                }
            }
            if (matchingGuides.isEmpty() && matchingGroups.isEmpty()) {
                CMS.Content.Text("没有找到相关文档", color = SecondaryText)
            }
        }
    }
}

@Composable
private fun InteractionGuideDocument(onBack: () -> Unit) {
    val context = LocalContext.current
    val document = remember {
        context.assets.open("components/InteractionGuide.md").bufferedReader().use { it.readText() }
    }
    val haze = remember { HazeState() }
    CMS.Appearance.Theme(darkTheme = resolveDark(), hazState = haze,
        glassMaterial = GlassMaterial(DemoStore.advanced, DemoStore.clarity)) {
        StudioFrame("交互接入 · 教程与覆盖清单", haze, onBack) {
            CMS.Content.Section {
                DemoPage.entries.filter { it.category == DemoCategory.Interaction }.forEach { page ->
                    CMS.Content.ListRow(page.title, "阅读教程与完整示例") {
                        context.startActivity(PreviewActivity.intent(context, page))
                    }
                }
            }
            MarkdownDocument(document, "components/InteractionGuide.md")
        }
    }
}

@Composable
private fun AssetDocumentScreen(assetPath: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val document = remember(assetPath) {
        runCatching { context.assets.open(assetPath).bufferedReader().use { it.readText() } }
            .getOrElse { "# 文档未能打开\n\n请返回后重新打开文档。" }
    }
    val title = remember(document) {
        document.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")
            ?: assetPath.substringAfterLast('/').removeSuffix(".md")
    }
    val haze = remember { HazeState() }
    CMS.Appearance.Theme(darkTheme = resolveDark(), hazState = haze,
        glassMaterial = GlassMaterial(DemoStore.advanced, DemoStore.clarity)) {
        StudioFrame(title, haze, onBack) {
            MarkdownDocument(document, assetPath)
        }
    }
}

@Composable
internal fun ComponentDocumentScreen(page: DemoPage, onBack: () -> Unit) {
    val context = LocalContext.current
    val definition = remember(page) { PreviewDefinition(page) }
    val document = remember(page) {
        runCatching {
            context.assets.open("components/${page.name}.md").bufferedReader().use { it.readText() }
        }.getOrElse {
            "# ${page.title}\n\n文档未能打开，请返回后重新打开。下方保留完整调用示例。"
        }
    }
    val haze = remember { HazeState() }
    CMS.Appearance.Theme(darkTheme = resolveDark(), hazState = haze,
        glassMaterial = GlassMaterial(DemoStore.advanced, DemoStore.clarity)) {
        StudioFrame("${page.title} · 文档", haze, onBack) {
            MarkdownDocument(document, "components/${page.name}.md")
            CMS.Content.SectionTitle("完整调用示例")
            DocumentCodeBlock(definition.code(definition.defaults()))
        }
    }
}

/** Activity-local state is deliberately not saved to preferences or DemoStore. */
@Composable
internal fun ComponentEditorScreen(page: DemoPage, onBack: () -> Unit) {
    val definition = remember(page) { PreviewDefinition(page) }
    val values = remember(page) {
        mutableStateMapOf<String, String>().apply {
            putAll(definition.defaults())
        }
    }
    var codeMode by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(definition.code(values)) }
    var draftDirty by remember { mutableStateOf(false) }
    var codeError by remember { mutableStateOf<String?>(null) }
    var errorLine by remember { mutableStateOf<Int?>(null) }
    val fieldErrors = remember { mutableStateMapOf<String, String>() }
    val context = LocalContext.current
    val preview = remember(page, context) { PreviewConnection(context, page) }
    DisposableEffect(preview) { onDispose { preview.close() } }
    val snapshot = values.toMap()
    LaunchedEffect(snapshot) { preview.sync(snapshot) }
    DisposableEffect(preview, codeMode) {
        preview.onValues = { received ->
            values.putAll(received)
            // Never overwrite an unfinished/invalid code draft with preview gestures.
            if (!draftDirty) draft = definition.code(values)
        }
        onDispose { preview.onValues = null }
    }
    val haze = remember { HazeState() }
    val config = PreviewValues(values)
    val dark = when (config.int("appearance")) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    LaunchedEffect(draft, codeMode) {
        if (codeMode) {
            delay(250)
            runCatching { definition.parse(draft) }
                .onSuccess {
                    parsed -> values.putAll(parsed); codeError = null; errorLine = null
                    draftDirty = false
                    fieldErrors.clear()
                }
                .onFailure { codeError = it.message; errorLine = (it as? PreviewCodeError)?.line }
        }
    }
    fun update(name: String, value: String): String? {
        val candidate = values.toMutableMap().apply { put(name, value) }
        return runCatching {
            definition.validateConstraints(candidate)
            values[name] = value
            draft = definition.code(values)
            draftDirty = false
            codeError = null
            errorLine = null
        }.exceptionOrNull()?.message
    }
    fun openPreview(reset: Boolean = false) {
        val result = runCatching {
            require(fieldErrors.isEmpty()) { fieldErrors.values.first() }
            definition.parse(draft)
        }
        result.onSuccess {
            values.putAll(it)
            codeError = null
            errorLine = null
            preview.open(it, reset)
        }.onFailure {
            codeError = it.message
            errorLine = (it as? PreviewCodeError)?.line
            if (fieldErrors.isEmpty()) codeMode = true
            Toast.makeText(context, "请先修正错误再打开预览", Toast.LENGTH_SHORT).show()
        }
    }
    CMS.Appearance.Theme(darkTheme = dark, hazState = haze,
        glassMaterial = GlassMaterial(config.bool("advanced"), config.int("clarity")),
        hapticsEnabled = config.bool("hapticsEnabled"),
        accentColor = config.color("themeColor")) {
        ApplySystemBars(dark, (context as Activity).window)
        StudioFrame("${page.title} · 编辑", haze, onBack) {
            CMSSection {
                CMS.Content.ListRow("文档") {
                    context.startActivity(PreviewActivity.intent(context, page))
                }
            }
            CMSSettingsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        CMS.Navigation.CapsuleNavigation(
                            labels = listOf("快速", "代码"),
                            selectedIndex = if (codeMode) 1 else 0,
                            onSelectedChange = { codeMode = it == 1 },
                            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth()
                        )
                    }
                    StudioAction("复制") {
                        copyCode(context, if (codeMode) draft else definition.code(values))
                    }
                }
            }
            CMS.Content.SectionTitle("独立预览")
            if (!codeMode) codeError?.let { Label(it, 12.sp, color = Color(0xFFD65B5B)) }
            CMSSection {
                CMSListRow { Label(preview.status, 14.sp, color = SecondaryText) }
                CMS.Content.ListRow(if (preview.running) "进入预览" else "打开预览",
                    showChevron = false) { openPreview() }
                if (preview.running) {
                    CMS.Content.ListRow("重新打开", showChevron = false) { openPreview(reset = true) }
                    CMS.Content.ListRow("关闭预览", showChevron = false) { preview.close() }
                }
            }
            if (codeMode) {
                CMS.Content.SectionTitle("代码")
                Label("修改顶部 val 参数。检查通过后即可打开预览。", 12.sp,
                    color = SecondaryText)
                codeError?.let { Label(it, 12.sp, color = Color(0xFFD65B5B)) }
                fieldErrors.values.forEach { Label(it, 12.sp, color = Color(0xFFD65B5B)) }
                CMSSettingsCard {
                    HighlightedCodeEditor(draft, errorLine, dark) { draft = it; draftDirty = true }
                }
            } else {
                CMS.Content.SectionTitle("独立配色与参数")
                CMSSection {
                    definition.fields.forEach { field ->
                        QuickField(field, values.getValue(field.name), onError = { error ->
                            if (error == null) fieldErrors.remove(field.name)
                            else fieldErrors[field.name] = error
                        }) { update(field.name, it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioAction(title: String, onClick: () -> Unit) {
    CMS.Controls.GlassButton(onClick, width = 60.dp, height = 36.dp, sampleContent = false,
        glassTint = Color.White) { color ->
        Label(title, 14.sp, color = color)
    }
}

@Composable
private fun HighlightedCodeEditor(source: String, errorLine: Int?, dark: Boolean,
    onChange: (String) -> Unit) {
    val base = PrimaryText
    val tokens = remember(source) { kotlinTokens(source) }
    val transformation = remember(tokens, errorLine, dark) {
        VisualTransformation { original ->
            val highlighted = buildAnnotatedString {
                append(original)
                tokens.forEach {
                    addStyle(SpanStyle(color = kotlinTokenColor(it.kind, dark)), it.start, it.end)
                }
                errorLine?.let { line ->
                    val lines = original.text.split('\n')
                    if (line in 1..lines.size) {
                        val start = lines.take(line - 1).sumOf { it.length + 1 }
                        val end = start + lines[line - 1].length
                        if (end > start) addStyle(SpanStyle(background = Color(0x22D65B5B)), start, end)
                    }
                }
            }
            TransformedText(highlighted, OffsetMapping.Identity)
        }
    }
    Row(Modifier.fillMaxWidth().cmsHorizontalScrollTouchPriority(rememberScrollState())) {
        Text((1..source.count { it == '\n' } + 1).joinToString("\n"),
            color = SecondaryText, fontFamily = FontFamily.Monospace,
            fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(end = 14.dp))
        BasicTextField(source, onChange, modifier = Modifier.widthIn(min = 640.dp),
            textStyle = TextStyle(color = base, fontFamily = FontFamily.Monospace,
                fontSize = 12.sp, lineHeight = 18.sp),
            cursorBrush = SolidColor(LocalCMSAccentColor.current),
            visualTransformation = transformation)
    }
}

@Composable
private fun QuickField(field: PreviewField, value: String, onError: (String?) -> Unit,
    onValue: (String) -> String?) {
    var input by remember(field.name) { mutableStateOf(field.editorText(value)) }
    var error by remember(field.name) { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    val haze = LocalGlassHazeState.current ?: remember { HazeState() }
    LaunchedEffect(value) {
        if (runCatching { field.fromEditor(input) }.getOrNull() != value) {
            input = field.editorText(value)
            error = null
            onError(null)
        }
    }
    when {
        field.kind == ValueKind.Boolean -> CMS.Content.ToggleRow(field.title, checked = value.toBoolean(),
            onCheckedChange = { onValue(it.toString()) })
        field.choices.isNotEmpty() -> {
            CMS.Content.MenuRow(field.title,
                value = field.choices.firstOrNull { it.second == value }?.first ?: value,
                onOpen = { anchor = it; visible = true })
            CMS.Controls.Menu(items = field.choices.map { MenuItem(it.first) },
                currentIndex = field.choices.indexOfFirst { it.second == value },
                visible = visible, anchorRect = anchor, accentColor = LocalCMSAccentColor.current,
                hazeState = haze,
                onSelect = { onValue(field.choices[it].second); visible = false },
                onDismiss = { visible = false })
        }
        else -> CMSListRow {
          Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(field.title + if (field.kind == ValueKind.Dp) " · dp" else "",
                    15.sp, modifier = Modifier.weight(1f))
                if (field.kind == ValueKind.Color) {
                    Box(Modifier.size(18.dp).background(PreviewValues(mapOf(field.name to value))
                        .color(field.name)))
                    Spacer(Modifier.width(8.dp))
                }
            }
            BasicTextField(value = input, onValueChange = {
                input = it
                runCatching { field.fromEditor(it) }
                    .onSuccess { normalized -> error = onValue(normalized); onError(error) }
                    .onFailure { error = it.message; onError(error) }
            }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = when (field.kind) {
                    ValueKind.Int, ValueKind.Long -> KeyboardType.Number
                    ValueKind.Float, ValueKind.Dp -> KeyboardType.Decimal
                    ValueKind.Color -> KeyboardType.Ascii
                    else -> KeyboardType.Text
                }),
                modifier = Modifier.fillMaxWidth().background(TertiaryFill, RoundedCornerShape(8.dp)).padding(10.dp),
                textStyle = TextStyle(color = PrimaryText, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(LocalCMSAccentColor.current))
            error?.let { Label(it, 11.sp, color = Color(0xFFD65B5B)) }
          }
        }
    }
}

@Composable
internal fun StudioFrame(title: String, haze: HazeState, onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    val history = LocalCMSBackHistory.current
    val context = LocalContext.current
    SideEffect { history?.updateTitle(context, title) }
    val inset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    Box(Modifier.fillMaxSize().background(PageBackground)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        CMS.Content.ScrollView(Modifier.fillMaxSize().hazeSource(haze), springOverscroll = true,
            showScrollbar = false) {
            Spacer(Modifier.height(inset + CM_SECONDARY_TOP_BAR_RESERVE))
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        CMS.Navigation.SecondaryTopBar(title = title, topInset = inset, hazeState = haze, onBack = onBack)
    }
}

/** The preview owns a viewport; only editor/document pages use StudioFrame's scroll column. */
@Composable
internal fun FullScreenPreviewFrame(page: DemoPage, haze: HazeState, onBack: () -> Unit,
    showTopBar: Boolean = true,
    content: @Composable () -> Unit) {
    val inset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val fillsViewport = page == DemoPage.Scroll || page == DemoPage.GlassButton ||
        page == DemoPage.ContentSource || page == DemoPage.TabBar || page == DemoPage.Page ||
        page == DemoPage.TouchPriority || page == DemoPage.BackHistory || page == DemoPage.CapsuleNavigation
    Box(Modifier.fillMaxSize().background(PageBackground)) {
        Box(if (page == DemoPage.TabBar || page == DemoPage.Page || page == DemoPage.GlassFusion ||
            page == DemoPage.TouchPriority || page == DemoPage.BackHistory ||
            page == DemoPage.CapsuleNavigation) Modifier.fillMaxSize()
            else Modifier.fillMaxSize().hazeSource(haze)) {
            if (fillsViewport) {
                content()
            } else {
                Box(Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(top = inset + if (showTopBar) CM_SECONDARY_TOP_BAR_RESERVE else 0.dp)
                    .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center) {
                    content()
                }
            }
        }
        if (showTopBar && page != DemoPage.Page && page != DemoPage.BackHistory) CMS.Navigation.SecondaryTopBar(title = "${page.title} · 预览", topInset = inset,
            hazeState = haze, onBack = onBack)
    }
}

internal class PreviewValues(private val values: Map<String, String>) {
    fun bool(key: String) = values.getValue(key).toBooleanStrict()
    fun int(key: String) = values.getValue(key).toInt()
    fun long(key: String) = values.getValue(key).removeSuffix("L").toLong()
    fun float(key: String) = values.getValue(key).removeSuffix("f").toFloat()
    fun dp(key: String) = values.getValue(key).removeSuffix(".dp").toFloat().dp
    fun color(key: String) = Color(values.getValue(key).removePrefix("Color(0x").removeSuffix(")").toLong(16))
    fun text(key: String) = literalText(values.getValue(key))
    fun choice(key: String) = values.getValue(key)
    inline fun <reified T : Enum<T>> enum(key: String): T =
        enumValueOf(values.getValue(key).substringAfterLast('.'))
}

private fun PreviewValues.fontWeight(key: String): FontWeight = when (choice(key)) {
    "FontWeight.Medium" -> FontWeight.Medium
    "FontWeight.SemiBold" -> FontWeight.SemiBold
    "FontWeight.Bold" -> FontWeight.Bold
    else -> FontWeight.Normal
}

private fun PreviewValues.textOverflow(key: String): androidx.compose.ui.text.style.TextOverflow =
    when (choice(key)) {
        "TextOverflow.Ellipsis" -> androidx.compose.ui.text.style.TextOverflow.Ellipsis
        else -> androidx.compose.ui.text.style.TextOverflow.Clip
    }

private fun PreviewValues.horizontalAlignmentOf(key: String): Alignment.Horizontal =
    when (choice(key)) {
        "Alignment.CenterHorizontally" -> Alignment.CenterHorizontally
        "Alignment.End" -> Alignment.End
        else -> Alignment.Start
    }

private fun PreviewValues.verticalAlignmentOf(key: String): Alignment.Vertical =
    when (choice(key)) {
        "Alignment.Top" -> Alignment.Top
        "Alignment.Bottom" -> Alignment.Bottom
        else -> Alignment.CenterVertically
    }

private fun PreviewValues.stackAlignmentOf(key: String): Alignment =
    when (choice(key)) {
        "Alignment.Center" -> Alignment.Center
        "Alignment.BottomEnd" -> Alignment.BottomEnd
        else -> Alignment.TopStart
    }

@Composable
internal fun ComponentPreview(page: DemoPage, c: PreviewValues, haze: HazeState,
    onBack: () -> Unit, fullScreen: Boolean = false, update: (String, String) -> Unit) {
    val color = c.color("controlColor")
    val haptic = rememberCMSHaptic()
    var menu by remember { mutableStateOf(false) }
    var modal by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    val touch = rememberCMSMenuTouchState()
    BackHandler(menu || modal) { menu = false; modal = false }
    when (page) {
        DemoPage.TouchPriority -> TouchPriorityCase(c, haze, fullScreen)
        DemoPage.GlassFusion -> GlassFusionCase(c, haze)
        DemoPage.BackHistory -> BackHistoryCase(c, haze, onBack, fullScreen)
        DemoPage.CapsuleNavigation -> CapsuleNavigationPreview(c, haze, update)
        DemoPage.TabNavigation -> {
            val options = List(c.int("itemCount")) { index ->
                c.text("labels").split('|').getOrNull(index)?.takeIf { it.isNotBlank() } ?: "分类 ${index + 1}"
            }
            CMS.Navigation.TabNavigation(options, c.int("selectedIndex").coerceIn(options.indices),
                { update("selectedIndex", it.toString()) }, enabled = c.bool("enabled"),
                height = c.dp("height"), fontSize = c.int("fontSize").sp,
                selectedColor = if (c.bool("customColors")) c.color("selectedColor") else LocalCMSAccentColor.current,
                unselectedColor = if (c.bool("customColors")) c.color("unselectedColor") else SecondaryText,
                indicatorColor = color, indicatorHeight = c.dp("indicatorHeight"),
                indicatorRadius = c.dp("indicatorRadius"), indicatorWidthFraction = c.float("indicatorWidthFraction"),
                indicatorGap = c.dp("indicatorGap"))
        }
        DemoPage.TabBar -> TabBarPreview(c, haze, update)
        DemoPage.GlassButton, DemoPage.ContentSource -> {
            var source by remember { mutableStateOf<ViewGroup?>(null) }
            val surfaceModifier = if (fullScreen) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().height(240.dp)
            Box(surfaceModifier, contentAlignment = Alignment.Center) {
                CMS.Content.Source(Modifier.fillMaxSize(), hazeState = haze,
                    onSourceReady = { source = it }) { PreviewImage(Modifier.fillMaxSize()) }
                val inset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
                Box(if (fullScreen) Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(top = inset + if (c.bool("previewTopBar")) CM_SECONDARY_TOP_BAR_RESERVE else 0.dp)
                    else Modifier, contentAlignment = Alignment.Center) {
                source?.let {
                    CMS.Controls.GlassButton(onClick = { haptic(CMSHapticKind.Tick) }, width = c.dp("width"),
                        height = c.dp("height"), cornerRadius = c.dp("cornerRadius"),
                        glassTint = color, glassTintAlpha = c.float("tintAlpha"),
                        shadowStrength = if (page == DemoPage.GlassButton) c.float("shadowStrength") else 1f,
                        sampleContent = true, samplingSource = it, hazeState = haze,
                        exclusiveTouchGesture = true) { foreground ->
                        Label("玻璃", 14.sp, color = foreground)
                    }
                }
                }
            }
        }
        // 预览只展示控件本身，不再套设置卡片
        DemoPage.BackButton -> Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            CMS.Navigation.BackButton(onBack, iconColor = color,
                exclusiveTouchGesture = c.bool("exclusiveTouchGesture"))
        }
        DemoPage.SettingsRows -> CMS.Content.List(spacing = c.dp("groupSpacing")) {
            val rowPad = PaddingValues(vertical = c.dp("rowPaddingV"))
            val highlight = RoundedCornerShape(c.dp("highlightRadius"))
            val titleColor = if (c.bool("customColors")) c.color("titleColor") else PrimaryText
            val descColor = if (c.bool("customColors")) c.color("descColor") else SecondaryText
            val containerColor = if (c.bool("customColors")) c.color("containerColor") else CardBackground
            val sectionPad = PaddingValues(horizontal = c.dp("rowPaddingH"), vertical = c.dp("sectionPadding"))
            val chevron: @Composable () -> Unit = {
                if (c.bool("showChevron")) {
                    if (c.bool("customChevron")) Text(c.text("chevronText"), color = color, fontSize = 14.sp)
                    else androidx.compose.material3.Icon(
                        androidx.compose.ui.res.painterResource(com.lindicra.cms.R.drawable.ic_chevron_right),
                        contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                }
            }
            CMS.Content.Section(
                contentPadding = sectionPad,
                containerColor = containerColor,
                cornerRadius = c.dp("cornerRadius"),
                separatorIndent = c.dp("dividerPadding"),
                separatorEndIndent = c.dp("dividerPadding"),
                separatorColor = c.color("separatorColor"),
                showSeparators = c.bool("showDividers")
            ) {
                CMS.Content.ListRow(
                    minHeight = c.dp("rowHeight"),
                    contentPadding = rowPad,
                    highlightShape = highlight,
                    pressedColor = c.color("pressedColor"),
                    trailing = if (c.bool("showChevron")) chevron else null,
                    onClick = { haptic(CMSHapticKind.Tick) }
                ) {
                    CMS.Content.ListRowText(
                        c.text("navTitle"), c.text("navDesc"),
                        titleColor = titleColor, descColor = descColor,
                        titleSize = c.int("titleSize").sp,
                        descSize = c.int("descSize").sp
                    )
                }
                CMS.Content.ListRow(
                    minHeight = c.dp("rowHeight"),
                    contentPadding = rowPad,
                    highlightShape = highlight,
                    pressedColor = c.color("pressedColor"),
                    onClick = { update("initialChecked", (!c.bool("initialChecked")).toString()) },
                    trailing = {
                        CMS.Controls.Toggle(
                            c.bool("initialChecked"),
                            { update("initialChecked", it.toString()) },
                            trackOnColor = color
                        )
                    }
                ) {
                    CMS.Content.ListRowText(
                        c.text("toggleTitle"), c.text("toggleDesc"),
                        titleColor = titleColor, descColor = descColor,
                        titleSize = c.int("titleSize").sp,
                        descSize = c.int("descSize").sp
                    )
                }
                CMS.Content.MenuRow(
                    c.text("menuTitle"), desc = c.text("menuDesc"),
                    value = listOf("选项 A", "选项 B", "选项 C")[c.int("selectedIndex")],
                    minHeight = c.dp("rowHeight"), contentPadding = rowPad,
                    highlightShape = highlight, pressedColor = c.color("pressedColor"),
                    titleColor = titleColor, descColor = descColor, valueColor = color,
                    titleSize = c.int("titleSize").sp, descSize = c.int("descSize").sp,
                    onOpen = { anchor = it; menu = true }
                )
            }
            CMS.Content.Section(
                contentPadding = sectionPad,
                containerColor = containerColor,
                cornerRadius = c.dp("cornerRadius"),
                separatorIndent = c.dp("dividerPadding"),
                separatorEndIndent = c.dp("dividerPadding"),
                separatorColor = c.color("separatorColor"),
                showSeparators = c.bool("showDividers")
            ) {
                CMS.Content.ListRow(
                    minHeight = c.dp("rowHeight"),
                    contentPadding = rowPad,
                    highlightShape = highlight,
                    pressedColor = c.color("pressedColor"),
                    trailing = if (c.bool("showChevron")) chevron else null,
                    onClick = { haptic(CMSHapticKind.Tick) }
                ) {
                    CMS.Content.ListRowText(
                        c.text("secondTitle"), c.text("secondDesc"),
                        titleColor = titleColor, descColor = descColor,
                        titleSize = c.int("titleSize").sp,
                        descSize = c.int("descSize").sp
                    )
                }
            }
        }
        DemoPage.Switch -> Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            CMS.Controls.Toggle(c.bool("initialChecked"), {
                update("initialChecked", it.toString()); haptic(CMSHapticKind.Tick)
            }, trackOnColor = color, trackOffColor = c.color("trackOffColor"),
                thumbOnColor = c.color("thumbColor"), thumbOffColor = c.color("thumbColor"))
        }
        DemoPage.Progress -> Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            CMS.Controls.Slider(value = c.float("initialValue"), onValueChange = {
                if (it.isFinite()) update("initialValue", "${it.coerceIn(0f, 1f)}f")
            }, onValueChangeFinished = { haptic(CMSHapticKind.Tick) }, modifier = Modifier.fillMaxWidth(),
                selectedColor = color, thumbStyle = c.enum("thumbStyle"), steps = c.int("steps"),
                readOnly = c.bool("readOnly"), thicknessAnimation = c.bool("thicknessAnimation"),
                dampingEnabled = c.bool("dampingEnabled"), overscrollEnabled = c.bool("overscrollEnabled"),
                trackThickness = c.dp("trackThickness"), pressedThickness = c.dp("pressedThickness"),
                dampingStrength = c.float("dampingStrength"), readOnlyDim = c.bool("readOnlyDim"))
        }
        DemoPage.SelectMenu -> CMSSection {
            CMS.Content.MenuRow("操作菜单",
                value = "选项 ${c.int("selectedIndex") + 1}",
                desc = "轻触或长按滑动选择",
                onOpen = { anchor = it; menu = true })
        }
        DemoPage.Scroll -> ScrollPreview(c, color, fullScreen)
        DemoPage.Page -> {
            val inset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            CMS.Content.Page(
                topInset = if (fullScreen) inset else 0.dp,
                modifier = if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(360.dp),
                glassEnabled = c.bool("glassEnabled"),
                topBarReserve = if (c.bool("previewTopBar")) c.dp("topBarReserve") else 12.dp,
                bottomReserve = c.dp("bottomReserve") + bottomInset,
                maxContentWidth = c.dp("maxContentWidth"),
                hazeState = haze,
                topBar = { pageHaze ->
                    if (c.bool("previewTopBar")) CMS.Navigation.SecondaryTopBar(
                        title = c.text("title"), topInset = if (fullScreen) inset else 0.dp,
                        hazeState = pageHaze, onBack = onBack
                    )
                }
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CMS.Content.SectionTitle("页面内容")
                    CMS.Content.Section {
                        repeat(c.int("rowCount")) { index ->
                            CMS.Content.ListRow(
                                "内容 ${index + 1}", "向上滚动可体验顶栏玻璃",
                                showChevron = false
                            ) { haptic(CMSHapticKind.Tick) }
                        }
                    }
                }
            }
        }
        DemoPage.Swiper -> CMS.Content.PageView(itemCount = c.int("itemCount"), direction = c.enum("direction"),
            autoPlay = c.bool("autoPlay"), autoPlayInterval = c.long("autoPlayInterval"),
            showArrows = c.bool("showArrows"), arrowSize = c.dp("arrowSize"),
            edgeOverflow = c.enum("edgeOverflow"), snapBack = c.bool("snapBack"),
            freeMode = c.bool("freeMode"), pageWidthFraction = c.float("pageWidthFraction"),
            pageHeight = c.dp("pageHeight"), pageSpacing = c.dp("pageSpacing"),
            clipRadius = c.dp("clipRadius"), indicatorStyle = c.enum("indicatorStyle"),
            indicatorVisibleCount = c.int("indicatorVisibleCount"),
            indicatorSize = c.dp("indicatorSize"), indicatorActiveWidth = c.dp("indicatorActiveWidth"),
            indicatorBottomInset = c.dp("indicatorBottomInset"),
            indicatorInteractive = c.bool("indicatorInteractive"), indicatorColor = color) { index ->
            Box(Modifier.fillMaxSize()) {
                PreviewImage(Modifier.fillMaxSize())
                Label("第 ${index + 1} 页", 28.sp, FontWeight.Bold, Color.White,
                    Modifier.align(Alignment.Center))
            }
        }
        DemoPage.HalfModal -> CMSSection {
            CMS.Content.ListRow("打开底部面板", "拖动关闭与惯性回弹") { modal = true }
        }
        DemoPage.Haptics -> CMSSettingsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label("触感体验")
                CMS.Controls.GlassButton({ haptic(c.enum("kind")) }, width = 72.dp, height = 40.dp,
                    glassTint = color, exclusiveTouchGesture = true) { foreground ->
                    Label("体验", 14.sp, color = foreground)
                }
            }
        }
        DemoPage.CodeBlock -> CMSSection {
            CMS.Content.CodeBlock(
                SampleCodeBlock,
                cornerRadius = c.dp("cornerRadius"),
                maxHeight = c.dp("maxHeight").takeIf { it > 0.dp },
                wrap = c.bool("wrap"),
                highlight = c.bool("highlight"),
                showHeader = c.bool("showHeader"),
                edgeFeedback = c.bool("edgeFeedback")
            )
        }
        DemoPage.Table -> CMSSection {
            CMS.Content.Table(
                columns = listOf("参数", "类型", "说明"),
                rows = SampleTableRows,
                cornerRadius = c.dp("cornerRadius"),
                maxHeight = c.dp("maxHeight").takeIf { it > 0.dp },
                minColumnWidth = c.dp("minColumnWidth"),
                striped = c.bool("striped"),
                showDividers = c.bool("showDividers"),
                edgeFeedback = c.bool("edgeFeedback")
            )
        }
        DemoPage.Text -> CMS.Content.Text(
            c.text("content"),
            modifier = Modifier.fillMaxWidth(),
            color = color,
            fontSize = c.int("fontSize").sp,
            fontWeight = c.fontWeight("weight"),
            maxLines = c.int("maxLines"),
            preset = c.enum("preset"),
            respectFontScale = c.bool("respectFontScale"),
            overflow = c.textOverflow("overflow"),
            lineHeight = c.int("lineHeight").sp
        )
        DemoPage.Tag -> Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            CMS.Content.Tag(
                c.text("content"),
                style = c.enum("style"),
                tint = color,
                fontSize = c.int("fontSize").sp,
                cornerRadius = c.dp("cornerRadius"),
                horizontalPadding = c.dp("horizontalPadding"),
                verticalPadding = c.dp("verticalPadding")
            )
        }
        DemoPage.Link -> Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            CMS.Content.Link(
                c.text("content"),
                onClick = { haptic(CMSHapticKind.Tick) },
                color = color,
                fontSize = c.int("fontSize").sp,
                underline = c.bool("underline"),
                fontWeight = c.fontWeight("weight"),
                maxLines = c.int("maxLines")
            )
        }
        DemoPage.Alert -> {
            var alert by remember { mutableStateOf(false) }
            CMSSection {
                CMS.Content.ListRow("打开警告弹窗", showChevron = false) { alert = true }
            }
            if (alert) {
                CMS.Presentation.Alert(
                    title = c.text("title"),
                    message = c.text("message"),
                    confirmText = c.text("confirmText"),
                    cancelText = if (c.bool("showCancel")) "取消" else null,
                    destructive = c.bool("destructive"),
                    scrimAlpha = c.float("scrimAlpha"),
                    dismissOnOutsideClick = c.bool("dismissOnOutsideClick"),
                    dismissOnBackPress = c.bool("dismissOnBackPress"),
                    buttonLayout = c.enum("buttonLayout"),
                    titleLineHeight = c.int("titleLineHeight").sp,
                    messageLineHeight = c.int("messageLineHeight").sp,
                    onConfirm = {},
                    onCancel = {},
                    onDismiss = { alert = false }
                )
            }
        }
        DemoPage.ActionSheet -> {
            var sheet by remember { mutableStateOf(false) }
            CMSSection {
                CMS.Content.ListRow("打开动作列表", showChevron = false) { sheet = true }
            }
            if (sheet) {
                CMS.Presentation.ActionSheet(
                    actions = listOf(
                        CMSDialogAction(c.text("actionOne"), {}),
                        CMSDialogAction(c.text("actionTwo"), {}),
                        CMSDialogAction(c.text("actionThree"), {},
                            destructive = c.bool("destructiveAction"))
                    ),
                    title = c.text("sheetTitle"),
                    cancelText = c.text("cancelText"),
                    scrimAlpha = c.float("scrimAlpha"),
                    dismissOnOutsideClick = c.bool("dismissOnOutsideClick"),
                    dismissOnBackPress = c.bool("dismissOnBackPress"),
                    actionLayout = c.enum("actionLayout"),
                    onDismiss = { sheet = false }
                )
            }
        }
        DemoPage.WheelPicker -> CMS.Controls.WheelPicker(
            items = listOf("周一", "周二", "周三", "周四", "周五"),
            selectedIndex = c.int("selectedIndex").coerceIn(0, 4),
            onSelected = { update("selectedIndex", it.toString()) },
            modifier = Modifier.fillMaxWidth(),
            itemHeight = c.dp("itemHeight"),
            visibleCount = c.int("visibleCount"),
            loop = c.bool("loop"),
            frictionMultiplier = c.float("frictionMultiplier"),
            dampingRatio = c.float("dampingRatio"),
            stiffness = c.float("stiffness"),
            wheelRadius = c.dp("wheelRadius"),
            hapticEnabled = c.bool("hapticEnabled")
        )
        DemoPage.DatePicker -> {
            val today = java.time.LocalDate.now()
            val range = c.int("rangeMonths").toLong()
            val selected = runCatching { java.time.LocalDate.parse(c.text("selectedDate")) }
                .getOrElse { today }
            CMS.Controls.DatePicker(
                selected = selected,
                onSelected = { update("selectedDate", it.toString()) },
                weekStart = c.enum("weekStart"),
                minDate = today.minusMonths(range),
                maxDate = today.plusMonths(range),
                showTodayButton = c.bool("showToday"),
                mode = c.enum("pickerMode"),
                showModeSwitch = c.bool("showModeSwitch")
            )
        }
        DemoPage.TimePicker -> CMS.Controls.TimePicker(
            hour = c.int("hour"),
            minute = c.int("minute"),
            onTimeChange = { hour, minute ->
                update("hour", hour.toString())
                update("minute", minute.toString())
            },
            minuteStep = c.int("minuteStep"),
            is24Hour = c.bool("is24Hour"),
            itemHeight = c.dp("itemHeight"),
            visibleCount = c.int("visibleCount"),
            loop = c.bool("loop")
        )
        DemoPage.ColorPicker -> CMS.Controls.ColorPicker(
            color = color,
            onColorChange = {
                update("controlColor", "Color(0x%08X)".format(it.toArgb()))
            },
            showPresets = c.bool("showPresets"),
            showAlpha = c.bool("showAlpha"),
            recentLimit = c.int("recentLimit"),
            panelHeight = c.dp("panelHeight"),
            mode = c.enum("pickerMode"),
            palette = c.enum("palette"),
            showModeSwitch = c.bool("showModeSwitch"),
            showInputs = c.bool("showInputs")
        )
        DemoPage.TextField -> {
            var input by remember(c.text("inputValue")) { mutableStateOf(c.text("inputValue")) }
            CMS.Controls.TextField(
                value = input,
                onValueChange = {
                    input = it
                    update("inputValue", it)
                },
                label = c.text("label"),
                placeholder = c.text("placeholder"),
                supportingText = c.text("supportingText"),
                variant = c.enum("variant"),
                prefixText = c.text("prefixText").ifEmpty { null },
                suffixText = c.text("suffixText").ifEmpty { null },
                showClearButton = c.bool("showClearButton"),
                isError = c.bool("isError"),
                isSuccess = c.bool("isSuccess"),
                enabled = c.bool("enabled"),
                size = c.enum("fieldSize")
            )
        }
        DemoPage.VStack -> CMS.Layout.VStack(
            spacing = c.dp("spacing"),
            horizontalAlignment = c.horizontalAlignmentOf("horizontalAlignment"),
            minWidth = c.dp("minWidth"),
            maxWidth = c.dp("maxWidth"),
            minHeight = c.dp("minHeight"),
            maxHeight = c.dp("maxHeight")
        ) {
            Box(Modifier.size(48.dp).background(color.copy(alpha = 0.22f)))
            Box(Modifier.size(76.dp).background(color.copy(alpha = 0.42f)))
        }
        DemoPage.HStack -> CMS.Layout.HStack(
            spacing = c.dp("spacing"),
            verticalAlignment = c.verticalAlignmentOf("verticalAlignment"),
            minWidth = c.dp("minWidth"),
            maxWidth = c.dp("maxWidth"),
            minHeight = c.dp("minHeight"),
            maxHeight = c.dp("maxHeight")
        ) {
            Box(Modifier.size(52.dp).background(color.copy(alpha = 0.22f)))
            Box(Modifier.size(32.dp).background(color.copy(alpha = 0.42f)))
            CMS.Content.Text("内容")
        }
        DemoPage.ZStack -> CMS.Layout.ZStack(
            alignment = c.stackAlignmentOf("alignment"),
            minWidth = c.dp("minWidth"),
            maxWidth = c.dp("maxWidth"),
            minHeight = c.dp("minHeight"),
            maxHeight = c.dp("maxHeight")
        ) {
            Box(Modifier.size(120.dp).background(color.copy(alpha = 0.18f)))
            CMS.Content.Text("叠放内容")
        }
        DemoPage.Group -> CMS.Layout.Group(
            spacing = c.dp("spacing"),
            horizontalAlignment = c.horizontalAlignmentOf("horizontalAlignment"),
            minWidth = c.dp("minWidth"),
            maxWidth = c.dp("maxWidth"),
            minHeight = c.dp("minHeight"),
            maxHeight = c.dp("maxHeight")
        ) {
            CMS.Content.Text("第一组")
            CMS.Content.Text("第二组")
        }
        DemoPage.Appearance -> CMSSettingsCard {
            Label("外观预览", 21.sp, color = color)
            Spacer(Modifier.height(14.dp))
            Label("主题强调色", color = LocalCMSAccentColor.current)
            Spacer(Modifier.height(14.dp))
            CMS.Controls.Slider(value = 0.6f, selectedColor = color, readOnly = true)
        }
    }
    if (page == DemoPage.SettingsRows || page == DemoPage.SelectMenu) {
        val actions = page == DemoPage.SelectMenu
        CMS.Controls.Menu(
            items = if (actions) listOf(MenuItem("选项 A"), MenuItem("选项 B"),
                MenuItem("更多", subItems = listOf(MenuItem("选项 C"),
                    MenuItem("打开底部面板"))),
                MenuItem("不可用项目", disabled = true))
            else listOf("选项 A", "选项 B", "选项 C").map { MenuItem(it) },
            currentIndex = c.int("selectedIndex"), visible = menu, anchorRect = anchor,
            type = if (actions) CMSMenuType.Actions else CMSMenuType.Select,
            hazeState = haze, accentColor = color, touchState = touch,
            dimBackground = actions && c.bool("dimBackground"),
            dimAmount = if (actions) c.float("dimAmount") else 0.24f,
            onSelect = {},
            onSelectPath = { path ->
                if (path == listOf(2, 1)) modal = true
                else update("selectedIndex", path.first().toString())
                haptic(CMSHapticKind.Tick); menu = false
            }, onDismiss = { menu = false })
    }
    if (modal) {
        CMS.Presentation.Sheet(onDismiss = { modal = false },
            heightFraction = if (page == DemoPage.HalfModal) c.float("heightFraction") else 0.86f) { dismiss ->
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Label("底部面板", 28.sp, color = color)
                CMS.Controls.GlassButton(dismiss, width = 64.dp, height = 40.dp, glassTint = color) { foreground ->
                    Label("完成", 14.sp, color = foreground)
                }
                CMS.Content.ScrollView(Modifier.weight(1f), springOverscroll = true, showScrollbar = true) {
                    repeat(10) { index ->
                        CMSSettingsCard { Label("内容 ${index + 1}") }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleNavigationPreview(c: PreviewValues, haze: HazeState, update: (String, String) -> Unit) {
    val options = List(c.int("itemCount")) { index ->
        c.text("labels").split('|').getOrNull(index)?.takeIf { it.isNotBlank() } ?: "选项 ${index + 1}"
    }
    var source by remember { mutableStateOf<ViewGroup?>(null) }
    val inset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    Box(Modifier.fillMaxSize()) {
        if (c.bool("liquidGlass")) {
            CMS.Content.Source(Modifier.fillMaxSize(), hazeState = haze, onSourceReady = { source = it }) {
                CMS.Content.ScrollView(Modifier.fillMaxSize(), showScrollbar = false) {
                    Spacer(Modifier.height(inset +
                        if (c.bool("previewTopBar")) CM_SECONDARY_TOP_BAR_RESERVE else 0.dp))
                    Column(Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(12) { index ->
                            CMSSettingsCard {
                                Label("${options[index % options.size]} · ${index + 1}",
                                    color = c.color("controlColor"))
                            }
                        }
                    }
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
        Box(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(top = inset + if (c.bool("previewTopBar")) CM_SECONDARY_TOP_BAR_RESERVE else 0.dp)
            .padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            CMS.Navigation.CapsuleNavigation(options, c.int("selectedIndex").coerceIn(options.indices),
                { update("selectedIndex", it.toString()) }, variant = c.enum("variant"),
                enabled = c.bool("enabled"), height = c.dp("height"),
                outerRadius = c.dp("outerRadius"), indicatorRadius = c.dp("indicatorRadius"),
                contentInset = c.dp("contentInset"), borderWidth = c.dp("borderWidth"),
                fontSize = c.int("fontSize").sp,
                selectedColor = if (c.bool("customColors")) c.color("selectedColor") else PrimaryText,
                unselectedColor = if (c.bool("customColors")) c.color("unselectedColor") else SecondaryText,
                containerColor = if (c.bool("customColors")) c.color("containerColor") else Color.Unspecified,
                indicatorColor = if (c.bool("customColors")) c.color("indicatorColor") else Color.Unspecified,
                draggable = c.bool("draggable"), liquidGlass = c.bool("liquidGlass"),
                liquidMotion = c.bool("liquidMotion"), samplingSource = source, hazeState = haze)
        }
    }
}

@Composable
private fun TabBarPreview(c: PreviewValues, haze: HazeState, update: (String, String) -> Unit) {
    var source by remember { mutableStateOf<ViewGroup?>(null) }
    val itemCount = c.int("itemCount").coerceIn(2, 5)
    val tabs = CMSGlassTabBarDefaults.items.take(itemCount)
    val selected = c.int("selectedIndex").coerceIn(0, itemCount - 1)
    val inset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    Box(Modifier.fillMaxSize()) {
        CMS.Content.Source(Modifier.fillMaxSize(), hazeState = haze, onSourceReady = { source = it }) {
            CMS.Content.ScrollView(Modifier.fillMaxSize(), showScrollbar = false) {
                Spacer(Modifier.height(inset + if (c.bool("previewTopBar")) CM_SECONDARY_TOP_BAR_RESERVE else 0.dp))
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Label(tabs[selected].label, 40.sp, FontWeight.Bold, c.color("controlColor"))
                    repeat(10) { index ->
                        CMSSettingsCard { Label("${tabs[selected].label} · ${index + 1}") }
                    }
                }
                Spacer(Modifier.height(c.dp("barHeight") + c.dp("bottomInset") + 20.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        val tabPlacement = c.enum<CMSTabPlacement>("tabPlacement")
        val tabForm = c.enum<CMSTabForm>("tabForm")
        val rail = tabForm == CMSTabForm.Rail
        val top = tabPlacement == CMSTabPlacement.Top && !rail
        val alignment = when {
            rail -> Alignment.CenterEnd
            top -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }
        val sides = WindowInsetsSides.Horizontal +
            if (top) WindowInsetsSides.Top else WindowInsetsSides.Bottom
        Box(Modifier.align(alignment)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(sides))
            .padding(horizontal = c.dp("horizontalInset"), vertical = c.dp("bottomInset"))) {
            CMS.Navigation.TabView(items = tabs, selectedIndex = selected,
                onSelectedChange = { update("selectedIndex", it.toString()) },
                modifier = Modifier, height = c.dp("barHeight"),
                placement = tabPlacement, form = tabForm,
                itemWidth = c.dp("itemWidth"), itemOverlap = c.dp("itemOverlap"),
                fillWidth = c.bool("fillWidth"),
                indicatorInset = c.dp("indicatorInset"), pressExpansion = c.dp("pressExpansion"),
                iconSize = c.dp("iconSize"), showLabels = c.bool("showLabels"),
                liquidMotion = c.bool("liquidMotion"), selectedColor = c.color("controlColor"),
                indicatorColor = c.color(if (LocalClearIsDark.current) "darkIndicatorColor" else "indicatorColor"),
                samplingSource = source, hazeState = haze)
        }
    }
}

@Composable
private fun ScrollPreview(c: PreviewValues, color: Color, fullScreen: Boolean = false) {
    val offsets = remember { mutableStateMapOf<String, Int>() }
    var origin by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    val inset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val topReserve = if (fullScreen) inset +
        if (c.bool("previewTopBar")) CM_SECONDARY_TOP_BAR_RESERVE else 0.dp else 0.dp
    val topReservePx = with(density) { topReserve.toPx() }
    val bottomReserve = if (fullScreen) with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    } else 0.dp
    val viewportModifier = if (fullScreen) Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        else Modifier.fillMaxWidth().height(360.dp)
    CMS.Content.ScrollView(viewportModifier, scrollState = rememberScrollState(),
        springOverscroll = c.bool("springOverscroll"), showScrollbar = c.bool("showScrollbar"),
        scrollbarStyle = c.enum("scrollbarStyle"), scrollbarIndexLayout = c.enum("scrollbarIndexLayout"),
        scrollbarInteractive = c.bool("scrollbarInteractive"),
        scrollbarPressThickening = c.bool("scrollbarPressThickening"),
        scrollbarInsets = PaddingValues(start = 8.dp, top = topReserve + 8.dp,
            end = 8.dp, bottom = bottomReserve + 8.dp),
        scrollbarSections = ('A'..'H').map { CMSScrollSection(it.toString(), offsets[it.toString()] ?: 0) }) {
        Column(Modifier.fillMaxWidth().onGloballyPositioned { origin = it }) {
            if (fullScreen) Spacer(Modifier.height(topReserve))
            ('A'..'H').forEach { letter ->
                Column(Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                    origin?.takeIf { it.isAttached && coordinates.isAttached }?.let {
                        val offset = (it.localPositionOf(coordinates, Offset.Zero).y -
                            topReservePx).toInt().coerceAtLeast(0)
                        if (offsets[letter.toString()] != offset) offsets[letter.toString()] = offset
                    }
                }) {
                    Column(if (fullScreen) Modifier.padding(horizontal = 16.dp) else Modifier) {
                        CMS.Content.SectionTitle(letter.toString())
                        Spacer(Modifier.height(8.dp))
                        repeat(3) { index ->
                            CMSSettingsCard { Label("$letter · ${index + 1}", color = color) }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
            if (fullScreen) Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
