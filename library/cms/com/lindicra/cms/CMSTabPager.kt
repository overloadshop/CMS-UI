package com.lindicra.cms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

/**
 * 分页 Tab：顶部切换栏 + 可横滑的页面容器。
 *
 * @param swipeEnabled 是否允许手势拖拽切换；关闭后只能点标签切换
 */
@Composable
fun CMSTabPager(
    titles: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
    barHorizontalPadding: Dp = 16.dp,
    barBackground: Color = Color.Unspecified,
    touchPriority: CMSTouchPriority = LocalCMSTouchPriority.current,
    verticalBridge: (page: Int) -> CMSVerticalDragBridge? = { null },
    content: @Composable (index: Int) -> Unit
) {
    if (titles.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = selectedIndex.coerceIn(titles.indices)) { titles.size }

    val selection by rememberUpdatedState(selectedIndex.coerceIn(titles.indices))
    val callback by rememberUpdatedState(onSelectedChange)
    LaunchedEffect(selectedIndex, titles.size) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex.coerceIn(titles.indices))
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .collect { (moving, settled) ->
                if (!moving && settled != selection) callback(settled)
            }
    }

    Column(modifier) {
        if (titles.size in 2..4) CMSCapsuleNavigation(
            titles, selectedIndex, onSelectedChange,
            Modifier.fillMaxWidth().padding(horizontal = barHorizontalPadding, vertical = 8.dp),
            containerColor = barBackground,
            pageProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction
        ) else CMSTabNavigation(
            titles, selectedIndex, onSelectedChange,
            Modifier.fillMaxWidth().padding(horizontal = barHorizontalPadding, vertical = 8.dp),
            pageProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction
        )
        CMSPagePager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            swipeEnabled = swipeEnabled,
            touchPriority = touchPriority,
            verticalBridge = verticalBridge
        ) { page ->
            content(page)
        }
    }
}
