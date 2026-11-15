package com.lindicra.cms.example

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import com.lindicra.cms.*
import com.lindicra.cms.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : ExampleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        applyWindowTheme(window, DemoStore.appearance)
        setExampleContent {
            val dark = resolveDark()
            ApplySystemBars(dark, window)
            CMSHome()
        }
    }
}

/** 主页提供组件、示例、交互与文档入口。 */
@Composable
private fun CMSHome() {
    val haze = remember { HazeState() }
    val groups = remember {
        DemoCategory.entries.filter { it != DemoCategory.Interaction }.mapNotNull { category ->
            DemoPage.entries.filter { it.category == category }
                .takeIf { it.isNotEmpty() }?.let { category to it }
        }
    }
    CMS.Appearance.Theme(
        darkTheme = resolveDark(),
        glassMaterial = GlassMaterial(DemoStore.advanced, DemoStore.clarity),
        hazState = haze,
        hapticsEnabled = DemoStore.haptics
    ) {
        val haptic = rememberCMSHaptic()
        val context = LocalContext.current
        val topInset = with(LocalDensity.current) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
        var homeTab by remember { mutableIntStateOf(0) }
        val history = LocalCMSBackHistory.current
        SideEffect {
            history?.updateTitle(context, listOf("组件", "样例", "交互")[homeTab])
        }
        val pagerState = rememberPagerState(initialPage = 0) { 3 }
        val scope = rememberCoroutineScope()
        var navigationJob by remember { mutableStateOf<Job?>(null) }
        val motion = remember { floatArrayOf(0f) } // 页 / 秒，用于同向改目标时衔接速度
        var navigationGeneration by remember { mutableIntStateOf(0) }
        var pageTouchVersion by remember { mutableIntStateOf(0) }
        fun selectTab(target: Int) {
            if (homeTab == target && navigationJob?.isActive == true) return
            homeTab = target
            val generation = ++navigationGeneration
            val initialVelocity = motion[0]
            navigationJob?.cancel()
            navigationJob = scope.launch {
                try {
                    // 直接滚动真实页面，不经过 animateScrollToPage 的预跳页。
                    pagerState.scroll {
                        val start = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        val delta = target - start
                        if (abs(delta) < 0.0001f) return@scroll
                        // 跨多页只走一条曲线；距离增加时适当延长，不在中间页停顿。
                        val duration = (340f * sqrt(abs(delta))).roundToInt().coerceIn(140, 520)
                        val startSlope = (initialVelocity * duration / 1000f / delta)
                            .coerceIn(0f, 3f)
                        animate(
                            initialValue = start,
                            targetValue = target.toFloat(),
                            animationSpec = tween(
                                durationMillis = duration,
                                // 控制点保持单调：非线性加减速，到目标即止，不越过再回弹。
                                easing = CubicBezierEasing(0.25f, startSlope * 0.25f, 0.25f, 1f)
                            )
                        ) { position, velocity ->
                            motion[0] = velocity
                            val actual = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            scrollBy((position - actual) * pagerState.layoutInfo.pageSize)
                        }
                    }
                } finally {
                    if (generation == navigationGeneration) {
                        motion[0] = 0f
                        navigationJob = null
                        homeTab = pagerState.currentPage
                    }
                }
            }
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
                .collect { (scrolling, settled) ->
                    // 手势停稳才同步标签；中途经过的页面不能覆盖点击目标。
                    if (!scrolling && navigationJob?.isActive != true) homeTab = settled
                }
        }
        LaunchedEffect(pagerState) {
            var previousPage = pagerState.currentPage
            snapshotFlow { pagerState.isScrollInProgress to pagerState.currentPage }
                .collect { (scrolling, page) ->
                    if (page != previousPage && scrolling && navigationJob?.isActive != true) {
                        haptic(CMSHapticKind.Tick)
                    }
                    previousPage = page
                }
        }

        Box(Modifier.fillMaxSize().background(PageBackground)) {
            CMS.Content.Source(
                modifier = Modifier.fillMaxSize(),
                hazeState = haze
            ) {
                Box(Modifier.fillMaxSize().background(PageBackground)) {
                    CMS.Content.Pager(
                        state = pagerState,
                        beyondViewportPageCount = 2,
                        sampleGlassContent = false,
                        hazeState = haze,
                        swipeEnabled = DemoStore.swipeTabs,
                        modifier = Modifier.fillMaxSize(),
                        onTouchInterrupt = {
                            ++pageTouchVersion
                            ++navigationGeneration
                            navigationJob?.cancel()
                            navigationJob = null
                            motion[0] = 0f
                            homeTab = pagerState.currentPage
                        }
                    ) { page ->
                        when (page) {
                            0 -> ComponentsPage(groups, haptic, context, topInset)
                            1 -> SamplesPage(topInset)
                            else -> InteractionsPage(topInset)
                        }
                    }
                }
            }

            // 底部玻璃 Tab：组件 / 样例 / 交互
            CMS.Navigation.TabView(
                items = listOf(
                    CMSGlassTabItem("组件", Icons.Default.Favorite),
                    CMSGlassTabItem("样例", Icons.Default.Star),
                    CMSGlassTabItem("交互", Icons.Default.Settings)
                ),
                selectedIndex = if (navigationJob?.isActive == true) homeTab else pagerState.currentPage,
                onSelectedChange = { selectTab(it) },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(bottom = 12.dp),
                hazeState = haze,
                pageProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                pageTouchVersion = pageTouchVersion,
                form = CMSTabForm.Edge
            )

            // 内容、渐进模糊、标题为三个兄弟节点。
            Box(
                Modifier.fillMaxWidth().height(topInset + 80.dp).then(
                    if (DemoStore.advanced) {
                        Modifier.hazeEffect(haze) {
                            backgroundColor = PageBackground
                            blurRadius = 20.dp
                            tints = listOf(HazeTint(PageBackground.copy(alpha = 0.78f)))
                            progressive = HazeProgressive.verticalGradient(
                                startIntensity = 1f,
                                endIntensity = 0f,
                                preferPerformance = true
                            )
                        }
                    } else {
                        Modifier.background(PageBackground)
                    }
                )
            )
            CMS.Navigation.MainTopBar(
                title = "AndroidCMS",
                topInset = topInset,
                hazeState = haze,
                glassEnabled = false,
                style = CMSGlassTopBarStyle.clear
            )
        }
    }
}

private val pageInset = 160.dp

@Composable
private fun ComponentsPage(
    groups: List<Pair<DemoCategory, List<DemoPage>>>,
    haptic: (CMSHapticKind) -> Unit,
    context: android.content.Context,
    topInset: androidx.compose.ui.unit.Dp
) {
    CMS.Content.Page(
        topInset = topInset, topBarReserve = 96.dp, bottomReserve = pageInset,
        modifier = Modifier.fillMaxSize(),
        glassEnabled = false,
        springOverscroll = true,
        showScrollbar = false,
        sampleGlassContent = false,
        topBar = {}
    ) {
        Column(
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CMSSection {
                CMS.Content.ListRow("文档中心", "接入指南、组件参考与交互教程") {
                    haptic(CMSHapticKind.Tick)
                    context.startActivity(PreviewActivity.documentCenterIntent(context))
                }
            }
            groups.forEach { (category, pages) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CMS.Content.SectionTitle(category.title)
                    CMSSection {
                        pages.forEach { page ->
                            CMS.Content.ListRow(page.title, page.englishName) {
                                haptic(CMSHapticKind.Tick)
                                context.startActivity(DemoActivity.intent(context, page))
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CMS.Content.SectionTitle("全部组件")
                CMSSection {
                    CMS.Content.ListRow("组件清单", "All Components · 按组件分类") {
                        haptic(CMSHapticKind.Tick)
                        context.startActivity(ComponentCatalogActivity.intent(context))
                    }
                }
            }
            CMS.Content.SectionTitle("AndroidCMS · $CMS_VERSION")
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun SamplesPage(topInset: androidx.compose.ui.unit.Dp) {
    CMS.Content.Page(
        topInset = topInset, topBarReserve = 96.dp, bottomReserve = pageInset,
        modifier = Modifier.fillMaxSize(),
        glassEnabled = false,
        springOverscroll = true,
        sampleGlassContent = false,
        topBar = {}
    ) {
        Column(
            Modifier.widthIn(max = 680.dp).fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CMS.Content.SectionTitle("样例项目")
            CMSSection {
                listOf(
                    "计算器" to "Calculator",
                    "音乐播放器" to "Music Player",
                    "健康记录" to "Health"
                ).forEach { CMS.Content.ListRow(it.first, it.second) {} }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun InteractionsPage(topInset: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    CMS.Content.Page(
        topInset = topInset, topBarReserve = 96.dp, bottomReserve = pageInset,
        modifier = Modifier.fillMaxSize(),
        glassEnabled = false,
        springOverscroll = true,
        sampleGlassContent = false,
        topBar = {}
    ) {
        Column(
            Modifier.widthIn(max = 680.dp).fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CMSSection {
                CMS.Content.ListRow("触控优先级", "双页滚动与切换") {
                    context.startActivity(DemoActivity.intent(context, DemoPage.TouchPriority))
                }
                CMS.Content.ListRow("玻璃融合", "两块普通玻璃靠近与分开") {
                    context.startActivity(DemoActivity.intent(context, DemoPage.GlassFusion))
                }
                CMS.Content.ListRow("返回历史", "Back History · 长按跨级返回") {
                    context.startActivity(DemoActivity.intent(context, DemoPage.BackHistory))
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
