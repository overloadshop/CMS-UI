package com.lindicra.cms

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * 原生 HorizontalPager 适配器。Pager 的 userScrollEnabled 应为 false，避免双重手势所有权。
 * verticalBridge 按触到的可见页解析；一次手势期间固定宿主，不会向另一页发送位移。
 * canStart 用于排除滑杆、文本选择等拥有专用拖动的区域（坐标为本 Modifier 的局部 px）。
 */
@Composable
fun Modifier.cmsPagerTouchPriority(
    pagerState: PagerState,
    verticalBridge: (page: Int) -> CMSVerticalDragBridge? = { null },
    policy: CMSTouchPriority = LocalCMSTouchPriority.current,
    enabled: Boolean = true,
    canStart: (Offset) -> Boolean = { true },
    onTouchInterrupt: () -> Unit = {},
    onAxisChange: (CMSTouchAxis?) -> Unit = {},
    horizontalEnabled: Boolean = true
): Modifier {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val regions = LocalCMSTouchRegions.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val bridgeForPage by rememberUpdatedState(verticalBridge)
    val allowed by rememberUpdatedState(canStart)
    val interrupt by rememberUpdatedState(onTouchInterrupt)
    val changed by rememberUpdatedState(onAxisChange)
    val jobs = remember(pagerState) { arrayOfNulls<Job>(1) }
    val interrupts = remember(pagerState) { arrayOfNulls<Job>(1) }
    val previousBridge = remember(pagerState) { arrayOfNulls<CMSVerticalDragBridge>(1) }
    DisposableEffect(pagerState) {
        onDispose {
            jobs[0]?.cancel()
            interrupts[0]?.cancel()
            previousBridge[0]?.cancel?.invoke()
        }
    }
    return if (!enabled) this else onGloballyPositioned { coordinates[0] = it }
        .pointerInput(pagerState, pagerState.pageCount, policy, density, rtl, horizontalEnabled, regions) {
        fun stopPager() {
            jobs[0]?.cancel()
            interrupts[0]?.cancel()
            interrupts[0] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                pagerState.stopScroll(MutatePriority.UserInput)
            }
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val windowPoint = coordinates[0]?.takeIf { it.isAttached }?.localToWindow(down.position)
            if (windowPoint != null && regions?.at(windowPoint) != null) {
                // The local viewport owns dragging, but a new contact still stops
                // page motion. Defer settling until release so its geometry stays fixed.
                val moving = pagerState.isScrollInProgress || jobs[0]?.isActive == true
                if (policy.interruptOnDown) {
                    stopPager()
                    previousBridge[0]?.interrupt?.invoke()
                    interrupt()
                }
                if (moving && policy.interruptOnDown) {
                    val interrupted = interrupts[0]
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        jobs[0] = scope.launch {
                            interrupted?.join()
                            if (pagerState.pageCount > 0) pagerState.animateScrollToPage(
                                pagerState.currentPage.coerceIn(0, pagerState.pageCount - 1))
                        }
                    }
                }
                return@awaitEachGesture
            }
            if (!allowed(down.position) ||
                (policy.respectConsumedGestures && down.isConsumed)) return@awaitEachGesture
            val logicalX = if (rtl) size.width - down.position.x else down.position.x
            val touchedPage = pagerState.layoutInfo.visiblePagesInfo.minByOrNull {
                abs(logicalX - (it.offset + pagerState.layoutInfo.pageSize / 2f))
            }?.index ?: pagerState.currentPage
            val bridge = bridgeForPage(touchedPage)
            val wasMoving = pagerState.isScrollInProgress || jobs[0]?.isActive == true
            if (policy.interruptOnDown) {
                stopPager()
                if (previousBridge[0] !== bridge) previousBridge[0]?.interrupt?.invoke()
                bridge?.interrupt?.invoke()
                interrupt()
            }
            previousBridge[0] = bridge
            val session = CMSTouchPrioritySession(viewConfiguration.touchSlop, policy)
            val verticalDrag = bridge?.let {
                CMVerticalDragContinuity(it, viewConfiguration.touchSlop, policy)
            }
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var owner: CMSTouchAxis? = null
            var deltas: Channel<Float>? = null
            var dragJob: Job? = null
            var releaseX = 0f
            var horizontalDistance = 0f
            var horizontalStart = pagerState.currentPage + pagerState.currentPageOffsetFraction
            var releaseHorizontal = false
            var lifted = false
            val interrupted = interrupts[0]
            fun startPagerDrag() {
                if (deltas != null) return
                val channel = Channel<Float>(Channel.UNLIMITED)
                deltas = channel
                dragJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        interrupted?.join()
                        pagerState.scroll(MutatePriority.UserInput) {
                            for (dx in channel) scrollBy(if (rtl) dx else -dx)
                        }
                        val count = pagerState.pageCount
                        val stride = (pagerState.layoutInfo.pageSize +
                            pagerState.layoutInfo.pageSpacing).toFloat()
                        if (count <= 0 || stride <= 0f) return@launch
                        val start = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        // 短滑按距离表达切页意图；停顿抬手不再要求跨过半页。
                        // 纵向结束只就近落位，避免换向后仍执行旧横滑意图。
                        // 距离保留触发前的手指位移；速度连续投影，不再要求另跨固定速度门槛。
                        val target = CMPagerBounds.target(start, horizontalStart, horizontalDistance,
                            -releaseX, stride, density, count, releaseHorizontal)
                        val speed = CMPagerBounds.velocity(start, count - 1, -releaseX)
                        pagerState.scroll(MutatePriority.Default) {
                            animate(0f, (target - start) * stride, initialVelocity = speed,
                                animationSpec = spring(dampingRatio = 0.86f, stiffness = 220f)) { value, _ ->
                                val actual = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                scrollBy(CMPagerBounds.delta(start, value, actual, stride,
                                    (pagerState.pageCount - 1).coerceAtLeast(0)))
                            }
                        }
                    } finally { channel.cancel() }
                }
                jobs[0] = dragJob
            }
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.count { it.pressed } > 1) break
                    val delta = change.position - change.previousPosition
                    val turned = session.add(change.uptimeMillis, delta.x, delta.y)
                    val candidate = session.axis
                    if (candidate != null && candidate != owner &&
                        (candidate == CMSTouchAxis.Horizontal && horizontalEnabled ||
                            candidate == CMSTouchAxis.Vertical && bridge != null)) {
                        val firstClaim = owner == null
                        owner = candidate
                        if (owner == CMSTouchAxis.Horizontal) {
                            horizontalDistance = 0f
                            horizontalStart = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        }
                        // 两轴共用连续位置采样；换向不清空仍在送达的纵向速度。
                        startPagerDrag() // 保持页位置冻结，换向时不启动横向落位动画。
                        changed(owner)
                        if (owner == CMSTouchAxis.Horizontal &&
                            (change.pressed || delta != Offset.Zero)) {
                            val amount = if (firstClaim) {
                                val slop = viewConfiguration.touchSlop *
                                    (policy.slopMultiplier.takeIf { it.isFinite() } ?: 1f).coerceIn(0.1f, 8f)
                                sign(session.recentX) * (abs(session.recentX) - slop).coerceAtLeast(0f)
                            } else delta.x
                            val intent = if (firstClaim) session.recentX else delta.x
                            horizontalDistance += if (rtl) intent else -intent
                            deltas?.trySend(amount)
                        }
                    } else if (owner != null && (change.pressed || delta != Offset.Zero)) {
                        if (owner == CMSTouchAxis.Horizontal) {
                            horizontalDistance += if (rtl) delta.x else -delta.x
                            deltas?.trySend(delta.x)
                        }
                    }
                    if (change.pressed || delta != Offset.Zero) {
                        verticalDrag?.drag(owner == CMSTouchAxis.Vertical, delta.y, session.recentY)
                    }
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (owner != null) event.changes.forEach { it.consume() }
                    if (!change.pressed) {
                        lifted = true
                        val velocity = tracker.calculateVelocity()
                        val limit = 8000f * density
                        releaseHorizontal = owner == CMSTouchAxis.Horizontal
                        releaseX = if (owner == CMSTouchAxis.Horizontal)
                            (if (rtl) -velocity.x else velocity.x).coerceIn(-limit, limit) else 0f
                        verticalDrag?.end(velocity.y.coerceIn(-limit, limit))
                        if (deltas == null && wasMoving && policy.interruptOnDown) startPagerDrag()
                        deltas?.close()
                        break
                    }
                    if (owner == null && !turned && policy.respectConsumedGestures) {
                        val final = awaitPointerEvent(PointerEventPass.Final)
                        if (final.changes.any { it.id == down.id && it.isConsumed }) break
                    }
                }
            } finally {
                if (!lifted) {
                    deltas?.cancel()
                    dragJob?.cancel()
                    verticalDrag?.cancel()
                    if (owner != null || wasMoving && policy.interruptOnDown) {
                        jobs[0] = scope.launch {
                            interrupted?.join()
                            if (pagerState.pageCount > 0)
                                pagerState.animateScrollToPage(pagerState.currentPage
                                    .coerceIn(0, pagerState.pageCount - 1))
                        }
                    }
                }
                changed(null)
            }
        }
    }
}
