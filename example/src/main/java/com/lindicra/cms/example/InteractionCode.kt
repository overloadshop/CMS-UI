package com.lindicra.cms.example

internal fun backHistorySnippet() = """val hostHistory = LocalCMSBackHistory.current
val latestHost by rememberUpdatedState(hostHistory)
val includeHost by rememberUpdatedState(includeHostHistory)
var depth by remember(pageCount) { mutableIntStateOf(0) }
val history = remember(pageCount) {
    object : CMSBackHistory {
        override fun previous(context: android.content.Context): List<CMSBackDestination> =
            (depth - 1 downTo 0).map { index ->
                CMSBackDestination("case/${'$'}index", "示例第 ${'$'}{index + 1} 页")
            } + if (includeHost) latestHost?.previous(context).orEmpty() else emptyList()

        override fun returnTo(context: android.content.Context, id: String) {
            val local = id.removePrefix("case/").toIntOrNull()
            if (id.startsWith("case/") && local != null && local in 0 until depth) {
                depth = local
            } else if (includeHost) {
                latestHost?.returnTo(context, id)
            }
        }
        override fun updateTitle(context: android.content.Context, title: String) {}
    }
}
val context = androidx.compose.ui.platform.LocalContext.current
fun back() {
    if (depth > 0) depth--
    else (context as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
}
androidx.activity.compose.BackHandler(enabled = depth > 0) { back() }
val statusInset = with(androidx.compose.ui.platform.LocalDensity.current) {
    WindowInsets.statusBars.getTop(this).toDp()
}
CMS.Interaction.BackHistory(history) {
    CMS.Content.Page(
        modifier = Modifier.fillMaxSize(), topInset = statusInset,
        topBarReserve = if (previewTopBar) CM_SECONDARY_TOP_BAR_RESERVE else 0.dp,
        bottomReserve = WindowInsets.navigationBars.asPaddingValues()
            .calculateBottomPadding() + 16.dp,
        hazeState = haze,
        topBar = {
            if (previewTopBar) CMS.Navigation.SecondaryTopBar(
                title = "示例第 ${'$'}{depth + 1} 页", topInset = statusInset,
                hazeState = haze, onBack = ::back)
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CMS.Content.Text("先进入后续页面，再长按左上角返回按钮，选择历史页跨级返回。",
                color = SecondaryText)
            CMS.Content.Section {
                if (depth + 1 < pageCount) CMS.Content.ListRow(
                    "进入第 ${'$'}{depth + 2} 页", "连续进入几页后体验跨级返回"
                ) { depth++ }
                else CMS.Content.Text("已经到最后一页，长按返回按钮选择更早的页面。")
            }
        }
    }
}"""

/** 导出的是独立可接入的代码，不能依赖示例应用里的私有演示函数。 */
internal fun touchPrioritySnippet() = """val pager = rememberPagerState { 2 }
val scope = rememberCoroutineScope()
val policy = CMSTouchPriority(
    horizontalPriority = horizontalPriority, verticalPriority = verticalPriority,
    directionRatio = directionRatio, slopMultiplier = slopMultiplier,
    historyMillis = historyMillis, allowAxisChange = allowAxisChange,
    interruptOnDown = interruptOnDown, respectConsumedGestures = respectConsumedGestures,
    glassDragPriority = glassDragPriority
)
CMS.Interaction.TouchPriority(policy) {
    Box(Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        CMS.Content.Pager(
            state = pager, hazeState = haze, modifier = Modifier.fillMaxSize()
        ) { page ->
            CMS.Content.Page(
                topInset = statusInset,
                topBarReserve = previewTopInset - statusInset + 64.dp,
                bottomReserve = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 24.dp,
                hazeState = haze, springOverscroll = true, topBar = {}
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("左右切页，上下滚动；横滑途中也可转为纵滑。",
                        color = SecondaryText, fontSize = 13.sp)
                    Text("从表格或代码上横滑，再按住转为上下滑动。",
                        color = SecondaryText, fontSize = 13.sp)
                    CMS.Content.HorizontalScrollView(Modifier.fillMaxWidth()) {
                        repeat(6) { column ->
                            Box(Modifier.width(144.dp).background(TertiaryFill).padding(12.dp)) {
                                CMS.Content.Text("列 ${'$'}{column + 1}\n横滑查看后续列")
                            }
                        }
                    }
                    CMS.Content.HorizontalScrollView(Modifier.fillMaxWidth()) {
                        Text(
                            "CMS.Content.HorizontalScrollView { /* 表格、代码与宽内容；横纵换向共用触控优先级 */ }",
                            modifier = Modifier.background(TertiaryFill).padding(12.dp),
                            color = PrimaryText, fontSize = 12.sp, softWrap = false,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    Text(if (policy.glassDragPriority) "拖拽玻璃可拉伸形变；关闭玻璃优先后可从这里滚动页面。"
                        else "玻璃优先已关闭：从玻璃上滑动可滚动页面。",
                        color = SecondaryText, fontSize = 13.sp)
                    CMS.Controls.GlassButton(onClick = {}, width = 164.dp, height = 80.dp,
                        exclusiveTouchGesture = true, hazeState = haze) { foreground ->
                        CMS.Content.Text(if (policy.glassDragPriority) "拖拽拉伸玻璃" else "从这里上下滑",
                            color = foreground)
                    }
                    CMS.Content.Section {
                        repeat(24) { index ->
                            CMS.Content.ListRow(
                                if (page == 0) "计算记录 ${'$'}{index + 1}" else "工具 ${'$'}{index + 1}",
                                if (page == 0) "上下滑动浏览记录" else "点按切换到计算页"
                            ) {
                                if (page == 1) scope.launch { pager.animateScrollToPage(0) }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = previewTopInset)
            .background(PageBackground).padding(horizontal = 16.dp, vertical = 8.dp)) {
            CMS.Navigation.CapsuleNavigation(
                listOf("计算", "工具"), pager.currentPage,
                { target -> scope.launch { pager.animateScrollToPage(target) } },
                pageProgress = pager.currentPage + pager.currentPageOffsetFraction
            )
        }
    }
}"""

internal fun glassFusionSnippet() = """var separated by remember { mutableStateOf(false) }
val animatedGap by androidx.compose.animation.core.animateDpAsState(
    if (separated) separatedGap else gap,
    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 220f),
    label = "glassGap"
)
var source by remember { mutableStateOf<android.view.ViewGroup?>(null) }
Box(Modifier.fillMaxWidth().height(caseHeight)) {
    CMS.Content.Source(Modifier.fillMaxSize(), hazeState = haze, onSourceReady = { source = it }) {
        Column(Modifier.fillMaxSize().background(PageBackground),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(8) { index ->
                Box(Modifier.fillMaxWidth().height(52.dp)
                    .background(controlColor.copy(alpha = 0.08f + index % 4 * 0.04f)))
            }
        }
    }
    Box(Modifier.fillMaxSize().padding(12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        CMS.Interaction.GlassFusion(
            enabled = fusionEnabled, mergeGap = mergeGap,
            proximityOnly = true, gravityStrength = gravityStrength
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(animatedGap)) {
                listOf("A", "B").forEach { title ->
                    CMS.Controls.GlassButton(
                        onClick = { separated = !separated },
                        width = glassWidth, height = glassHeight, cornerRadius = cornerRadius,
                        sampleContent = true, samplingSource = source, hazeState = haze
                    ) { foreground -> CMS.Content.Text(title, color = foreground) }
                }
            }
        }
    }
}"""
