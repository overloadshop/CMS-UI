package com.lindicra.cms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.lindicra.cms.ui.theme.LocalGlassHazeState
import dev.chrisbanes.haze.HazeState

/** 仅当前分页子树继承；一个页面中的主滚动宿主注册此桥。 */
internal val LocalCMSPageBridge = staticCompositionLocalOf<CMSVerticalDragBridge?> { null }
internal val LocalCMSPagerSampling = staticCompositionLocalOf { false }

/**
 * 推荐页面分页容器：统一采样、仲裁及每页滚动桥。
 * Page / ScrollView 自动接入，不要求业务维护桥列表；导航栏由宿主自由放置。
 */
@Composable
fun CMSPagePager(
    state: PagerState,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
    touchPriority: CMSTouchPriority = LocalCMSTouchPriority.current,
    hazeState: HazeState? = LocalGlassHazeState.current,
    sampleGlassContent: Boolean = true,
    beyondViewportPageCount: Int = 1,
    canStart: (Offset) -> Boolean = { true },
    onTouchInterrupt: () -> Unit = {},
    verticalBridge: (Int) -> CMSVerticalDragBridge? = { null },
    content: @Composable (page: Int) -> Unit
) {
    val count = state.pageCount
    val bridges = remember(state, count) { List(count) { CMSVerticalDragBridge() } }
    val inheritedSampling = LocalCMSPagerSampling.current
    val regions = LocalCMSTouchRegions.current ?: remember { CMSTouchRegionRegistry() }
    val resolvedHaze = hazeState ?: remember { HazeState() }
    CompositionLocalProvider(
        LocalCMSTouchPriority provides touchPriority,
        LocalCMSTouchRegions provides regions,
        LocalGlassHazeState provides resolvedHaze,
        LocalCMSPagerSampling provides (sampleGlassContent || inheritedSampling)
    ) {
        val pages: @Composable () -> Unit = {
            HorizontalPager(
                state = state,
                userScrollEnabled = false,
                beyondViewportPageCount = beyondViewportPageCount.coerceAtLeast(0)
                    .coerceAtMost((count - 1).coerceAtLeast(0)),
                modifier = Modifier.fillMaxSize().cmsPagerTouchPriority(
                    state, verticalBridge = { verticalBridge(it) ?: bridges.getOrNull(it) },
                    policy = touchPriority, horizontalEnabled = swipeEnabled,
                    canStart = canStart, onTouchInterrupt = onTouchInterrupt
                )
            ) { page ->
                CompositionLocalProvider(LocalCMSPageBridge provides
                    (verticalBridge(page) ?: bridges.getOrNull(page))) {
                    content(page)
                }
            }
        }
        if (sampleGlassContent && !inheritedSampling) {
            // Pager 与其内容位于同一 Compose 树；页面不重复创建 AndroidView 采样边界。
            CMSGlassContentSource(modifier.fillMaxSize(), hazeState = resolvedHaze, content = pages)
        } else {
            Box(modifier.fillMaxSize(), content = { pages() })
        }
    }
}
