package ldyc.qy.music

import kotlin.math.abs
import kotlin.math.floor

/** The active capsule contributes its extra length to every following dot's position. */
internal class CMSwiperIndicatorLayout(
    val count: Int, val progress: Float, val base: Float, val active: Float, val gap: Float
) {
    private val lower = floor(progress).toInt()
    private val fraction = progress - lower
    private val extra = active - base
    fun weight(index: Int) = (1f - abs(progress - index)).coerceIn(0f, 1f)
    fun length(index: Int) = base + extra * weight(index)
    fun leading(index: Int): Float = index * (base + gap) +
        (if (index > lower) extra * (1f - fraction) else 0f) +
        (if (index > lower + 1) extra * fraction else 0f)
    fun windowOffset(start: Float): Float {
        val first = floor(start).toInt()
        return leading(first) + (leading(first + 1) - leading(first)) * (start - first)
    }
    fun center(index: Int) = leading(index) + length(index) / 2f
}
