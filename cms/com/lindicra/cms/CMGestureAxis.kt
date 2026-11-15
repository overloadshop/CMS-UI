package com.lindicra.cms

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

internal val LocalCMVerticalDragBridge = staticCompositionLocalOf<CMVerticalDragBridge?> { null }

/** Sample in a fixed space: scroll/elastic translations must not become finger velocity. */
internal class CMSWindowMotion(initial: Offset) {
    var position = initial
        private set

    fun add(windowPosition: Offset): Offset {
        val delta = windowPosition - position
        position = windowPosition
        return delta
    }
}

internal object CMPagerBounds {
    /** 位移包含起步 slop；松手速度预测短距离延伸，所有输入均为逻辑滚动方向。 */
    fun target(position: Float, dragStart: Float, distance: Float, velocity: Float,
               stride: Float, density: Float, count: Int, horizontal: Boolean): Int {
        if (count <= 1) return 0
        val threshold = minOf(stride * 0.06f, 24f * density)
        val projected = distance + velocity * 0.20f
        val direction = if (horizontal && abs(projected) >= threshold) sign(projected) else 0f
        // 正好停在起始页时，已确认的 flick 也能前进；拖到整页后不再额外多跳一页。
        val atOrigin = abs(position - dragStart) < 0.001f
        return (when {
            direction > 0f -> ceil(position + if (atOrigin) 0.001f else -0.001f).toInt()
            direction < 0f -> floor(position + if (atOrigin) -0.001f else 0.001f).toInt()
            else -> position.roundToInt()
        }).coerceIn(0, count - 1)
    }

    fun velocity(position: Float, lastPage: Int, scrollVelocity: Float): Float =
        if ((position <= 0.0001f && scrollVelocity < 0f) ||
            (position >= lastPage - 0.0001f && scrollVelocity > 0f)) 0f else scrollVelocity

    fun delta(start: Float, animatedOffset: Float, actual: Float, stride: Float, lastPage: Int): Float {
        if (stride <= 0f) return 0f
        val desired = (start + animatedOffset / stride).coerceIn(0f, lastPage.toFloat())
        return (desired - actual) * stride
    }
}

/** 起步累计位移，接管后用最近运动判断换向。 */
internal class CMGestureAxis(slop: Float, policy: CMSTouchPriority = CMSTouchPriority()) {
    enum class Axis { Horizontal, Vertical }
    private val session = CMSTouchPrioritySession(slop, policy)
    val recentX get() = session.recentX
    val recentY get() = session.recentY
    val axis: Axis? get() = when (session.axis) {
        CMSTouchAxis.Horizontal -> Axis.Horizontal
        CMSTouchAxis.Vertical -> Axis.Vertical
        null -> null
    }

    fun add(time: Long, dx: Float, dy: Float): Boolean {
        val previous = axis
        return session.add(time, dx, dy) && previous != null
    }
}

/** The scroll owner supplies its existing overscroll and fling physics. */
class CMVerticalDragBridge {
    var begin: () -> Unit = {}
    var drag: (Float) -> Unit = {}
    var end: (Float) -> Unit = {}
    var cancel: () -> Unit = {}
    var interrupt: () -> Boolean = { false }
    internal var selectionHoldBlocked: () -> Boolean = { false }
    var active = false
    internal var gestureGeneration = 0L
}

/** 方向判断与纵向送达并行；启动过的纵向通道在本次触控内不因横向占优而取消。 */
internal class CMVerticalDragContinuity(
    private val bridge: CMVerticalDragBridge,
    touchSlop: Float,
    policy: CMSTouchPriority
) {
    private val threshold = (touchSlop.takeIf { it.isFinite() } ?: 8f).coerceAtLeast(0.1f) *
        (policy.slopMultiplier.takeIf { it.isFinite() } ?: 1f).coerceIn(0.1f, 8f)
    var started = false
        private set
    private var generation = 0L

    fun drag(vertical: Boolean, delta: Float, recent: Float) {
        if (!delta.isFinite() || !recent.isFinite()) return
        if (!started) {
            if (!vertical) return
            started = true
            bridge.begin()
            generation = bridge.gestureGeneration
            // 首次纵向接管补发判断期间的位移，避免只收到确认方向的最后一帧。
            bridge.drag(sign(recent) * (abs(recent) - threshold).coerceAtLeast(0f))
        } else {
            if (generation == bridge.gestureGeneration) bridge.drag(delta)
        }
    }

    fun end(velocity: Float) {
        if (!started) return
        started = false
        if (generation == bridge.gestureGeneration) bridge.end(velocity)
    }

    fun cancel() {
        if (!started) return
        started = false
        if (generation == bridge.gestureGeneration) bridge.cancel()
    }
}
