package ldyc.qy.music

import kotlin.math.abs
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalCMVerticalDragBridge = staticCompositionLocalOf<CMVerticalDragBridge?> { null }

internal object CMPagerBounds {
    fun velocity(position: Float, lastPage: Int, scrollVelocity: Float): Float =
        if ((position <= 0.0001f && scrollVelocity < 0f) ||
            (position >= lastPage - 0.0001f && scrollVelocity > 0f)) 0f else scrollVelocity

    fun delta(start: Float, animatedOffset: Float, actual: Float, stride: Float, lastPage: Int): Float {
        if (stride <= 0f) return 0f
        val desired = (start + animatedOffset / stride).coerceIn(0f, lastPage.toFloat())
        return (desired - actual) * stride
    }
}

/** Recent movement, rather than distance from touch-down, determines a turn. */
internal class CMGestureAxis(private val slop: Float) {
    enum class Axis { Horizontal, Vertical }
    private val times = LongArray(32)
    private val xs = FloatArray(32)
    private val ys = FloatArray(32)
    private var head = 0
    private var count = 0
    private var sumX = 0f
    private var sumY = 0f
    var axis: Axis? = null
        private set

    fun add(time: Long, dx: Float, dy: Float): Boolean {
        while (count > 0 && (time - times[head] > 80L || count == times.size)) {
            sumX -= xs[head]
            sumY -= ys[head]
            head = (head + 1) % times.size
            count--
        }
        val index = (head + count) % times.size
        times[index] = time
        xs[index] = dx
        ys[index] = dy
        count++
        sumX += dx
        sumY += dy
        val x = abs(sumX)
        val y = abs(sumY)
        val candidate = when {
            x >= slop && x > y * 1.6f -> Axis.Horizontal
            y >= slop && y > x * 1.6f -> Axis.Vertical
            else -> return false
        }
        if (candidate == axis) return false
        val turned = axis != null
        axis = candidate
        head = 0
        count = 0
        sumX = 0f
        sumY = 0f
        return turned
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
}
