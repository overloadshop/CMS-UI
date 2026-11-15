package com.mxalbert.sharedelements

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp

abstract class KeyframeBasedMotion : PathMotion {

    private var start = Offset.Unspecified
    private var end = Offset.Unspecified
    private var keyframes: Pair<FloatArray, Pair<FloatArray, FloatArray>>? = null

    protected abstract fun getKeyframes(start: Offset, end: Offset): Pair<FloatArray, Pair<FloatArray, FloatArray>>

    override fun invoke(start: Offset, end: Offset, fraction: Float): Offset {
        var frac = fraction
        if (start != this.start || end != this.end) {
            if (start == this.end && end == this.start) {
                frac = 1 - frac
            } else {
                keyframes = null
                this.start = start
                this.end = end
            }
        }
        val (fractions, coords) = keyframes ?: getKeyframes(start, end).also { keyframes = it }
        val xs = coords.first
        val ys = coords.second
        val count = fractions.size

        return when {
            frac < 0f -> interpolateInRange(fractions, xs, ys, frac, 0, 1)
            frac > 1f -> interpolateInRange(fractions, xs, ys, frac, count - 2, count - 1)
            frac == 0f -> Offset(xs[0], ys[0])
            frac == 1f -> Offset(xs[count - 1], ys[count - 1])
            else -> {
                var low = 0
                var high = count - 1
                while (low <= high) {
                    val mid = (low + high) / 2
                    val midFraction = fractions[mid]
                    when {
                        frac < midFraction -> high = mid - 1
                        frac > midFraction -> low = mid + 1
                        else -> return Offset(xs[mid], ys[mid])
                    }
                }
                interpolateInRange(fractions, xs, ys, frac, high, low)
            }
        }
    }

    private fun interpolateInRange(
        fractions: FloatArray, xs: FloatArray, ys: FloatArray,
        fraction: Float, startIndex: Int, endIndex: Int
    ): Offset {
        val startFraction = fractions[startIndex]
        val endFraction = fractions[endIndex]
        val intervalFraction = (fraction - startFraction) / (endFraction - startFraction)
        return lerp(
            Offset(xs[startIndex], ys[startIndex]),
            Offset(xs[endIndex], ys[endIndex]),
            intervalFraction
        )
    }

}
