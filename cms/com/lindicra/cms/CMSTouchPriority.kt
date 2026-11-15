package com.lindicra.cms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.abs

enum class CMSTouchAxis { Horizontal, Vertical }

/**
 * 同一套方向仲裁供 Pager、Carousel、局部横滑与 ScrollView 使用。
 * priority 为相对权重；阈值使用平台 touchSlop 的倍数；时间窗口使用毫秒。
 */
data class CMSTouchPriority(
    val horizontalPriority: Float = 1f,
    val verticalPriority: Float = 1f,
    val directionRatio: Float = 1.2f,
    val slopMultiplier: Float = 1f,
    val historyMillis: Long = 80L,
    val allowAxisChange: Boolean = true,
    val interruptOnDown: Boolean = true,
    /** 默认由父级统一仲裁；需要独立拖动的子控件由 Pager.canStart 排除。 */
    val respectConsumedGestures: Boolean = false,
    /** 显式开启 exclusiveTouchGesture 的原生玻璃优先保留完整拉伸手势。 */
    val glassDragPriority: Boolean = true
)

val LocalCMSTouchPriority = staticCompositionLocalOf { CMSTouchPriority() }

@Composable
fun CMSTouchPriorityScope(
    policy: CMSTouchPriority = CMSTouchPriority(),
    content: @Composable () -> Unit
) {
    val regions = LocalCMSTouchRegions.current ?: androidx.compose.runtime.remember {
        CMSTouchRegionRegistry()
    }
    CompositionLocalProvider(
        LocalCMSTouchPriority provides policy,
        LocalCMSTouchRegions provides regions,
        content = content
    )
}

/** 起步累计按下后的位移；接管后只用最近运动换向，不让旧方向锁住新意图。 */
class CMSTouchPrioritySession(touchSlop: Float, val policy: CMSTouchPriority = CMSTouchPriority()) {
    private val times = LongArray(64)
    private val xs = FloatArray(64)
    private val ys = FloatArray(64)
    private var head = 0
    private var count = 0
    private val threshold = (touchSlop.takeIf { it.isFinite() } ?: 8f).coerceAtLeast(0.1f) *
        (policy.slopMultiplier.takeIf { it.isFinite() } ?: 1f).coerceIn(0.1f, 8f)
    private val ratio = (policy.directionRatio.takeIf { it.isFinite() } ?: 1.2f).coerceIn(1f, 8f)
    private val horizontalWeight = safeWeight(policy.horizontalPriority)
    private val verticalWeight = safeWeight(policy.verticalPriority)
    private var sumX = 0f
    private var sumY = 0f
    private var initialX = 0f
    private var initialY = 0f
    private var turnX = 0f
    private var turnY = 0f
    private var turnStart: Long? = null
    var axis: CMSTouchAxis? = null
        private set
    var recentX = 0f
        private set
    var recentY = 0f
        private set

    fun add(timeMillis: Long, dx: Float, dy: Float): Boolean {
        if (!dx.isFinite() || !dy.isFinite()) return false
        if (count > 0 && timeMillis < times[(head + count - 1) % times.size]) {
            clear()
            clearTurn()
            initialX = 0f
            initialY = 0f
        }
        if (axis == null) {
            initialX += dx
            initialY += dy
        }
        while (count > 0 && (timeMillis - times[head] >
                policy.historyMillis.coerceIn(16L, 300L) || count == times.size)) {
            sumX -= xs[head]
            sumY -= ys[head]
            head = (head + 1) % times.size
            count--
        }
        val index = (head + count) % times.size
        times[index] = timeMillis
        xs[index] = dx
        ys[index] = dy
        count++
        sumX += dx
        sumY += dy
        recentX = if (axis == null) initialX else sumX
        recentY = if (axis == null) initialY else sumY
        if (axis != null && !policy.allowAxisChange) return false
        // A coherent perpendicular stroke is fresh intent, even while the old axis
        // still fills the recent window. Alternating jitter cancels itself below slop.
        val current = axis
        if (current != null) {
            val stepX = abs(dx) * horizontalWeight
            val stepY = abs(dy) * verticalWeight
            val opposite = if (current == CMSTouchAxis.Horizontal)
                stepY > stepX * ratio else stepX > stepY * ratio
            val same = if (current == CMSTouchAxis.Horizontal)
                stepX > stepY * ratio else stepY > stepX * ratio
            if (same) clearTurn()
            else {
                if (turnStart?.let { timeMillis - it > policy.historyMillis.coerceIn(16L, 300L) } == true)
                    clearTurn()
                if (opposite && turnStart == null) turnStart = timeMillis
                if (turnStart != null) {
                    turnX += dx
                    turnY += dy
                    val confirmed = if (current == CMSTouchAxis.Horizontal)
                        abs(turnY) >= threshold &&
                            abs(turnY) * verticalWeight > abs(turnX) * horizontalWeight * ratio
                    else abs(turnX) >= threshold &&
                        abs(turnX) * horizontalWeight > abs(turnY) * verticalWeight * ratio
                    if (confirmed) {
                        recentX = turnX
                        recentY = turnY
                        axis = if (current == CMSTouchAxis.Horizontal)
                            CMSTouchAxis.Vertical else CMSTouchAxis.Horizontal
                        clear()
                        clearTurn()
                        return true
                    }
                }
            }
            // Only fresh coherent intent may change an established axis. Reusing
            // the old window here would revive a candidate cleared by forward motion.
            return false
        }
        val x = abs(recentX)
        val y = abs(recentY)
        val next = when {
            x >= threshold && x * horizontalWeight > y * verticalWeight * ratio ->
                CMSTouchAxis.Horizontal
            y >= threshold && y * verticalWeight > x * horizontalWeight * ratio ->
                CMSTouchAxis.Vertical
            else -> return false
        }
        if (next == axis) return false
        axis = next
        clear()
        clearTurn()
        return true
    }

    private fun clear() { head = 0; count = 0; sumX = 0f; sumY = 0f }
    private fun clearTurn() { turnStart = null; turnX = 0f; turnY = 0f }
    private fun safeWeight(value: Float) =
        (value.takeIf { it.isFinite() } ?: 1f).coerceIn(0.1f, 8f)
}
