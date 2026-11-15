package com.lindicra.cms.example

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.lindicra.cms.*
import com.lindicra.cms.ui.theme.*
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/** Pages enter the history only after being visited; selection truncates the real local stack. */
@Composable
internal fun BackHistoryCase(c: PreviewValues, haze: HazeState, onBack: () -> Unit,
    fullScreen: Boolean) {
    val context = LocalContext.current
    val hostHistory = LocalCMSBackHistory.current
    val maxPages = c.int("pageCount")
    var depth by remember(maxPages) { mutableIntStateOf(0) }
    val latestHost by rememberUpdatedState(hostHistory)
    val includeHost by rememberUpdatedState(c.bool("includeHostHistory"))
    val history = remember(maxPages) {
        object : CMSBackHistory {
            override fun previous(context: android.content.Context): List<CMSBackDestination> =
                (depth - 1 downTo 0).map { index ->
                    CMSBackDestination("case/$index", "示例第 ${index + 1} 页")
                } + if (includeHost) latestHost?.previous(context).orEmpty() else emptyList()

            override fun returnTo(context: android.content.Context, id: String) {
                val local = id.removePrefix("case/").toIntOrNull()
                if (id.startsWith("case/") && local != null && local in 0 until depth) {
                    depth = local
                } else if (includeHost) {
                    latestHost?.returnTo(context, id)
                }
            }
            // Local demo page names must not overwrite the host editor's title.
            override fun updateTitle(context: android.content.Context, title: String) {}
        }
    }
    fun back() { if (depth > 0) depth-- else onBack() }
    BackHandler(depth > 0) { back() }
    val topInset = if (fullScreen) with(androidx.compose.ui.platform.LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    } else 0.dp
    val showTopBar = !fullScreen || c.bool("previewTopBar")
    CMS.Interaction.BackHistory(history) {
        CMS.Content.Page(
            modifier = if (fullScreen) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().height(380.dp),
            topInset = topInset,
            topBarReserve = if (showTopBar) CM_SECONDARY_TOP_BAR_RESERVE else 0.dp,
            bottomReserve = if (fullScreen) WindowInsets.navigationBars
                .asPaddingValues().calculateBottomPadding() + 16.dp else 16.dp,
            hazeState = haze,
            topBar = {
                if (showTopBar) CMS.Navigation.SecondaryTopBar(
                    title = "示例第 ${depth + 1} 页", topInset = topInset,
                    hazeState = haze, onBack = ::back)
            }
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CMS.Content.Text("返回历史 · Back History")
                CMS.Content.Text("先进入后续页面，再长按左上角返回按钮；选择历史页可跨级返回。",
                    color = SecondaryText)
                CMS.Content.Section {
                    if (depth + 1 < maxPages) CMS.Content.ListRow(
                        "进入第 ${depth + 2} 页", "连续进入几页后体验跨级返回"
                    ) { depth++ }
                    else CMS.Content.Text("已经到最后一页，长按返回按钮选择更早的页面。")
                }
                CMS.Content.Text(
                    "当前可返回 ${history.previous(context).size} 页 · 短按返回上一页",
                    color = SecondaryText)
            }
        }
    }
}

/** 双页全屏案例：Page 自带滚动，每页独立桥接。 */
@Composable
internal fun TouchPriorityCase(c: PreviewValues, haze: HazeState, fullScreen: Boolean) {
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topInset = if (fullScreen) with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    } else 0.dp
    val headerReserve = topInset +
        if (fullScreen && c.bool("previewTopBar")) CM_SECONDARY_TOP_BAR_RESERVE else 0.dp
    val bottomInset = if (fullScreen) with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    } else 0.dp
    val policy = CMSTouchPriority(
        horizontalPriority = c.float("horizontalPriority"),
        verticalPriority = c.float("verticalPriority"),
        directionRatio = c.float("directionRatio"),
        slopMultiplier = c.float("slopMultiplier"),
        historyMillis = c.long("historyMillis"),
        allowAxisChange = c.bool("allowAxisChange"),
        interruptOnDown = c.bool("interruptOnDown"),
        glassDragPriority = c.bool("glassDragPriority"),
        respectConsumedGestures = c.bool("respectConsumedGestures")
    )
    CMS.Interaction.TouchPriority(policy) {
        Box((if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(360.dp))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            CMS.Content.Pager(state = pager, hazeState = haze,
                modifier = Modifier.fillMaxSize()) { page ->
                CMS.Content.Page(
                    topInset = topInset,
                    topBarReserve = headerReserve - topInset + 64.dp,
                    bottomReserve = bottomInset + 24.dp,
                    hazeState = haze, springOverscroll = true, topBar = {}
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Label("左右切页，上下滚动；横滑途中也可转为纵滑。",
                            13.sp, color = SecondaryText)
                        Label("局部横滑区域：从表格或代码上横滑，再按住转为上下滑动。",
                            13.sp, color = SecondaryText)
                        CMS.Content.HorizontalScrollView(Modifier.fillMaxWidth()) {
                            repeat(6) { column ->
                                Box(Modifier.width(144.dp).background(TertiaryFill).padding(12.dp)) {
                                    CMS.Content.Text("列 ${column + 1}\n横滑查看后续列")
                                }
                            }
                        }
                        CMS.Content.HorizontalScrollView(Modifier.fillMaxWidth()) {
                            androidx.compose.material3.Text(
                                "CMS.Content.HorizontalScrollView { /* 表格、代码与宽内容；横纵换向共用触控优先级 */ }",
                                modifier = Modifier.background(TertiaryFill).padding(12.dp),
                                color = PrimaryText, fontSize = 12.sp, softWrap = false,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                        Label(if (policy.glassDragPriority) "拖拽玻璃可拉伸形变；关闭玻璃优先后可从这里滚动页面。"
                            else "玻璃优先已关闭：从玻璃上滑动可滚动页面。",
                            13.sp, color = SecondaryText)
                        CMS.Controls.GlassButton(onClick = {}, width = 164.dp, height = 80.dp,
                            exclusiveTouchGesture = true, hazeState = haze) { foreground ->
                            CMS.Content.Text(if (policy.glassDragPriority) "拖拽拉伸玻璃" else "从这里上下滑",
                                color = foreground)
                        }
                        CMS.Content.Section {
                            repeat(24) { index ->
                                CMS.Content.ListRow(
                                    if (page == 0) "计算记录 ${index + 1}" else "工具 ${index + 1}",
                                    if (page == 0) "上下滑动浏览记录" else "点按切换到计算页"
                                ) {
                                    if (page == 1) scope.launch { pager.animateScrollToPage(0) }
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = headerReserve)
                .background(PageBackground).padding(horizontal = 16.dp, vertical = 8.dp)) {
                CMS.Navigation.CapsuleNavigation(
                    listOf("计算", "工具"), pager.currentPage,
                    { target -> scope.launch { pager.animateScrollToPage(target) } },
                    pageProgress = pager.currentPage + pager.currentPageOffsetFraction)
            }
        }
    }
}

@Composable
internal fun GlassFusionCase(c: PreviewValues, haze: HazeState) {
    var separated by remember { mutableStateOf(false) }
    val gap by animateDpAsState(
        if (separated) c.dp("separatedGap") else c.dp("gap"),
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 220f), label = "glassGap")
    var source by remember { mutableStateOf<android.view.ViewGroup?>(null) }
    Box(Modifier.fillMaxWidth().height(c.dp("caseHeight"))) {
        CMS.Content.Source(Modifier.fillMaxSize(), hazeState = haze, onSourceReady = { source = it }) {
            Column(Modifier.fillMaxSize().background(PageBackground),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(8) { index ->
                    Box(Modifier.fillMaxWidth().height(52.dp)
                        .background(c.color("controlColor").copy(alpha = 0.08f + index % 4 * 0.04f)))
                }
            }
        }
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            CMS.Interaction.GlassFusion(
                enabled = c.bool("fusionEnabled"), mergeGap = c.dp("mergeGap"),
                proximityOnly = true, gravityStrength = c.float("gravityStrength")
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    listOf("A", "B").forEach { title ->
                        CMS.Controls.GlassButton(
                            onClick = { separated = !separated },
                            width = c.dp("glassWidth"), height = c.dp("glassHeight"),
                            cornerRadius = c.dp("cornerRadius"), sampleContent = true,
                            samplingSource = source, hazeState = haze
                        ) { foreground -> CMS.Content.Text(title, color = foreground) }
                    }
                }
            }
        }
    }
}
