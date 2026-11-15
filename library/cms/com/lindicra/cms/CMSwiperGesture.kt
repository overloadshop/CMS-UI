package com.lindicra.cms

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs
import kotlin.math.sign

/** Runs inside the page sampling host, before its native boundary can swallow direction intent. */
@Composable
internal fun Modifier.swiperGesture(
    count: Int,
    horizontal: Boolean,
    motion: CMSwiperMotion,
    onDragging: (Boolean) -> Unit,
    onRelease: (Float) -> Unit
): Modifier {
    val nativeView = LocalView.current
    val policy = LocalCMSTouchPriority.current
    val parentScroll = LocalCMVerticalDragBridge.current
    val regions = LocalCMSTouchRegions.current
    val region = remember(count, horizontal) { CMSTouchRegion { count > 1 } }
    DisposableEffect(regions, region) {
        regions?.register(region)
        onDispose { regions?.remove(region) }
    }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val dragging = rememberUpdatedState(onDragging)
    val release = rememberUpdatedState(onRelease)
    return if (count <= 1) this else onGloballyPositioned {
        coordinates[0] = it
        region.bounds = it.boundsInWindow()
    }
        .pointerInput(count, horizontal, motion, parentScroll, policy, regions) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val windowPoint = coordinates[0]?.takeIf { it.isAttached }?.localToWindow(down.position)
            val target = windowPoint?.let { regions?.at(it) }
            if (target != null && target !== region) return@awaitEachGesture
            if (policy.respectConsumedGestures && down.isConsumed) return@awaitEachGesture
            val interrupted = policy.interruptOnDown && motion.moving
            if (policy.interruptOnDown) {
                motion.stop()
                parentScroll?.interrupt?.invoke()
            }
            val axes = CMGestureAxis(viewConfiguration.touchSlop, policy)
            val verticalDrag = if (horizontal) parentScroll?.let {
                CMVerticalDragContinuity(it, viewConfiguration.touchSlop, policy)
            } else null
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var owner: CMGestureAxis.Axis? = null
            var claimed = false
            var finished = false
            var needsSwiperSettle = interrupted
            var pendingVerticalY = 0f
            val verticalThreshold = viewConfiguration.touchSlop *
                (policy.slopMultiplier.takeIf { it.isFinite() } ?: 1f).coerceIn(0.1f, 8f)
            fun ownsSwiper(axis: CMGestureAxis.Axis?) =
                axis == if (horizontal) CMGestureAxis.Axis.Horizontal else CMGestureAxis.Axis.Vertical
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.count { it.pressed } > 1) break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val delta = change.position - change.previousPosition
                    axes.add(change.uptimeMillis, delta.x, delta.y)
                    val candidate = axes.axis
                    if (candidate != null && candidate != owner &&
                        (ownsSwiper(candidate) || horizontal && parentScroll != null)) {
                        if (ownsSwiper(owner)) motion.stop()
                        val firstClaim = owner == null
                        owner = candidate
                        claimed = true
                        nativeView.parent?.requestDisallowInterceptTouchEvent(true)
                        // 保留连续速度采样，横向占优不打断已经启动的纵向通道。
                        if (ownsSwiper(owner)) {
                            motion.stop()
                            needsSwiperSettle = true
                            dragging.value(true)
                        } else {
                            // 按住转为纵向时也暂停轮播自动播放，页面保持当前物理位置。
                            dragging.value(true)
                        }
                        if (ownsSwiper(owner)) {
                            // Apply only movement beyond the intent threshold on the first claim.
                            val amount = if (firstClaim) {
                                val along = if (horizontal) axes.recentX else axes.recentY
                                val threshold = viewConfiguration.touchSlop *
                                    (policy.slopMultiplier.takeIf { it.isFinite() } ?: 1f).coerceIn(0.1f, 8f)
                                sign(along) * (abs(along) - threshold).coerceAtLeast(0f)
                            } else if (horizontal) delta.x else delta.y
                            motion.drag(amount)
                        }
                    } else if (owner != null) {
                        if (ownsSwiper(owner)) motion.drag(if (horizontal) delta.x else delta.y)
                    }
                    val verticalWasStarted = verticalDrag?.started == true
                    if (!verticalWasStarted) pendingVerticalY += delta.y
                    verticalDrag?.drag(
                        owner == CMGestureAxis.Axis.Vertical ||
                            policy.allowAxisChange && abs(pendingVerticalY) >= verticalThreshold,
                        delta.y, pendingVerticalY
                    )
                    if (!verticalWasStarted && verticalDrag?.started == true) {
                        needsSwiperSettle = needsSwiperSettle || motion.moving
                        motion.stop()
                        dragging.value(true)
                        nativeView.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    claimed = claimed || verticalDrag?.started == true
                    if (claimed) change.consume()
                    if (!change.pressed) {
                        val measured = tracker.calculateVelocity()
                        val limit = 8000f * nativeView.resources.displayMetrics.density
                        verticalDrag?.end(measured.y.coerceIn(-limit, limit))
                        if (ownsSwiper(owner)) {
                            release.value((if (horizontal) measured.x else measured.y).coerceIn(-limit, limit))
                        } else if (owner != null) {
                            if (needsSwiperSettle) release.value(0f)
                        } else if (needsSwiperSettle) release.value(0f)
                        finished = true
                        break
                    }
                    if (!claimed) {
                        val final = awaitPointerEvent(PointerEventPass.Final)
                        if (policy.respectConsumedGestures &&
                            final.changes.any { it.id == down.id && it.isConsumed }) break
                    }
                }
            } finally {
                if (!finished) {
                    verticalDrag?.cancel()
                    if (ownsSwiper(owner) || needsSwiperSettle) release.value(0f)
                }
                dragging.value(false)
                if (claimed) nativeView.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }
}
