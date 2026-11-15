package com.lindicra.cms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun swiperMinimum(count: Int, page: Float, spacing: Float, viewport: Float): Float =
    -(count.coerceAtLeast(0) * page + (count - 1).coerceAtLeast(0) * spacing - viewport)
        .coerceAtLeast(0f)

internal fun swiperTargetPage(position: Float, velocity: Float, advance: Float, count: Int): Int =
    ((-position - velocity * 0.16f) / advance.coerceAtLeast(1f)).roundToInt()
        .coerceIn(0, (count - 1).coerceAtLeast(0))

internal fun swiperDisplayOffset(raw: Float, minimum: Float, dimension: Float, spring: Boolean): Float {
    val anchor = raw.coerceIn(minimum, 0f)
    return anchor + if (spring) CMRubberBand.position(raw - anchor, dimension) else 0f
}

/** Shared position drives pages and indicators. An interruption preserves the visible position. */
internal class CMSwiperMotion {
    var value by mutableFloatStateOf(0f)
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    var moving by mutableStateOf(false)
        private set
    private var minimum = 0f
    private var dimension = 1f
    private var springEdge = true
    private var raw = 0f
    private var advance = 1f
    private var job: Job? = null

    fun configure(minimum: Float, dimension: Float, advance: Float, spring: Boolean) {
        stop()
        value = (value / this.advance * advance).coerceIn(minimum, 0f)
        this.minimum = minimum
        this.dimension = dimension.coerceAtLeast(1f)
        this.advance = advance.coerceAtLeast(1f)
        springEdge = spring
        velocity = 0f
        raw = value
    }

    fun stop() {
        job?.cancel()
        job = null
        moving = false
        val anchor = value.coerceIn(minimum, 0f)
        raw = anchor + if (springEdge) CMRubberBand.raw(value - anchor, dimension) else 0f
    }

    fun drag(delta: Float) {
        raw = if (springEdge) (raw + delta).coerceIn(minimum - dimension * 3f, dimension * 3f)
            else (raw + delta).coerceIn(minimum, 0f)
        value = swiperDisplayOffset(raw, minimum, dimension, springEdge)
        velocity = 0f
    }

    private fun displayedVelocity(fingerVelocity: Float): Float {
        val anchor = raw.coerceIn(minimum, 0f)
        return fingerVelocity * if (raw != anchor)
            CMRubberBand.derivative(raw - anchor, dimension) else 1f
    }

    fun settle(target: Float, scope: CoroutineScope, fingerVelocity: Float? = null) {
        val speed = fingerVelocity?.let { displayedVelocity(it) } ?: velocity
        stop()
        velocity = speed
        val destination = target.coerceIn(minimum, 0f)
        val rebound = value !in minimum..0f || destination == 0f && speed > 0f ||
            destination == minimum && speed < 0f
        moving = true
        job = scope.launch {
            var previous = withFrameNanos { it }
            var displacement = value - destination
            var resting = destination
            while (abs(displacement) > 0.1f || abs(velocity) > 4f) {
                val now = withFrameNanos { it }
                val step = progressSpringStep(displacement, velocity,
                    (now - previous) / 1_000_000_000f,
                    if (rebound) 190f else 270f, if (rebound) 0.84f else 0.82f)
                previous = now
                displacement = step.first
                velocity = step.second
                value = destination + displacement
                if (!springEdge && value !in minimum..0f) {
                    value = value.coerceIn(minimum, 0f)
                    resting = value
                    break
                }
            }
            value = resting
            velocity = 0f
            raw = value
            moving = false
        }
    }

    fun releaseFree(fingerVelocity: Float, scope: CoroutineScope) {
        if (value !in minimum..0f) {
            settle(value.coerceIn(minimum, 0f), scope, fingerVelocity)
            return
        }
        stop()
        if (abs(fingerVelocity) < 4f) { velocity = 0f; return }
        val trajectory = CMFlingTrajectory(fingerVelocity, dimension * 0.1f)
        val startPosition = value
        moving = true
        job = scope.launch {
            val start = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val elapsed = (now - start) / 1_000_000_000f
                val next = startPosition + trajectory.position(elapsed)
                velocity = trajectory.velocity(elapsed)
                value = next.coerceIn(minimum, 0f)
                if (next != value) {
                    val speed = velocity
                    if (springEdge) {
                        // Start the boundary spring exactly at contact, with the remaining momentum.
                        raw = value
                        settle(value, scope, speed)
                    } else { velocity = 0f; moving = false; raw = value }
                    return@launch
                }
                if (elapsed >= trajectory.duration) break
            }
            velocity = 0f
            raw = value
            moving = false
        }
    }
}
