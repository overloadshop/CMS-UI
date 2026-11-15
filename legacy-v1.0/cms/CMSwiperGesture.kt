package ldyc.qy.music

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
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
    val parentScroll = LocalCMVerticalDragBridge.current
    val dragging = rememberUpdatedState(onDragging)
    val release = rememberUpdatedState(onRelease)
    return if (count <= 1) this else pointerInput(count, horizontal, motion, parentScroll) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val interrupted = motion.moving
            motion.stop()
            parentScroll?.interrupt?.invoke()
            val axes = CMGestureAxis(viewConfiguration.touchSlop)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var owner: CMGestureAxis.Axis? = null
            var pendingX = 0f
            var pendingY = 0f
            var claimed = false
            var finished = false
            fun ownsSwiper(axis: CMGestureAxis.Axis?) =
                axis == if (horizontal) CMGestureAxis.Axis.Horizontal else CMGestureAxis.Axis.Vertical
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val delta = change.position - change.previousPosition
                    pendingX += delta.x
                    pendingY += delta.y
                    axes.add(change.uptimeMillis, delta.x, delta.y)
                    val candidate = axes.axis
                    if (candidate != null && candidate != owner &&
                        (ownsSwiper(candidate) || horizontal && parentScroll != null)) {
                        if (ownsSwiper(owner)) release.value(0f)
                        else if (owner != null) parentScroll?.end?.invoke(0f)
                        val firstClaim = owner == null
                        owner = candidate
                        claimed = true
                        nativeView.parent?.requestDisallowInterceptTouchEvent(true)
                        tracker.resetTracking()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (ownsSwiper(owner)) {
                            motion.stop()
                            dragging.value(true)
                        } else {
                            dragging.value(false)
                            parentScroll?.begin?.invoke()
                        }
                        // Apply only movement beyond the intent threshold when first taking ownership.
                        if (firstClaim) {
                            val along = if (owner == CMGestureAxis.Axis.Horizontal) pendingX else pendingY
                            val excess = sign(along) * (abs(along) - viewConfiguration.touchSlop).coerceAtLeast(0f)
                            if (ownsSwiper(owner)) motion.drag(excess) else parentScroll?.drag?.invoke(excess)
                        } else {
                            if (ownsSwiper(owner)) motion.drag(if (horizontal) delta.x else delta.y)
                            else parentScroll?.drag?.invoke(delta.y)
                        }
                    } else if (owner != null) {
                        if (ownsSwiper(owner)) motion.drag(if (horizontal) delta.x else delta.y)
                        else parentScroll?.drag?.invoke(delta.y)
                    }
                    if (claimed) change.consume()
                    if (!change.pressed) {
                        val measured = tracker.calculateVelocity()
                        val limit = 8000f * nativeView.resources.displayMetrics.density
                        if (ownsSwiper(owner)) {
                            release.value((if (horizontal) measured.x else measured.y).coerceIn(-limit, limit))
                        } else if (owner != null) {
                            parentScroll?.end?.invoke(measured.y.coerceIn(-limit, limit))
                        } else if (interrupted) release.value(0f)
                        finished = true
                        break
                    }
                    if (!claimed) {
                        val final = awaitPointerEvent(PointerEventPass.Final)
                        if (final.changes.any { it.id == down.id && it.isConsumed }) break
                    }
                }
            } finally {
                if (!finished) {
                    if (ownsSwiper(owner) || interrupted) release.value(0f)
                    else if (owner != null) parentScroll?.cancel?.invoke()
                }
                dragging.value(false)
                if (claimed) nativeView.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }
}
