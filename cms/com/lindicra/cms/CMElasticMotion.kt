package com.lindicra.cms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.ScrollScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.ln

internal data class CMScrollbarGeometry(val top: Float, val length: Float) {
    companion object {
        fun calculate(viewport: Float, maximum: Float, position: Float,
                      overscroll: Float, minimum: Float): CMScrollbarGeometry {
            val height = viewport.coerceAtLeast(0f)
            val floor = minimum.coerceIn(0f, height)
            val base = if (height + maximum > 0f) height * height / (height + maximum) else height
            val length = (base.coerceIn(floor, height) - abs(overscroll)).coerceIn(floor, height)
            val progress = if (maximum > 0f) (position / maximum).coerceIn(0f, 1f) else 0f
            return CMScrollbarGeometry((height - length) * progress, length)
        }
    }
}

/** Linear air drag plus a small rolling resistance; velocity reaches zero continuously. */
internal class CMFlingTrajectory(
    initialVelocity: Float,
    resistance: Float,
    private val rate: Float = 1.8f
) {
    private val direction = sign(initialVelocity)
    private val speed = abs(initialVelocity)
    private val terminal = resistance.coerceAtLeast(1f) / rate
    val duration = ln(1f + speed / terminal) / rate
    fun velocity(seconds: Float): Float =
        direction * ((speed + terminal) * exp(-rate * seconds.coerceIn(0f, duration)) - terminal)
            .coerceAtLeast(0f)
    fun position(seconds: Float): Float {
        val t = seconds.coerceIn(0f, duration)
        return direction * ((speed + terminal) * (1f - exp(-rate * t)) / rate - terminal * t)
    }
}

internal object CMRubberBand {
    fun position(raw: Float, dimension: Float): Float {
        val d = dimension.coerceAtLeast(1f)
        return sign(raw) * d * 0.52f * abs(raw) / (d + 0.52f * abs(raw))
    }

    fun raw(position: Float, dimension: Float): Float {
        val d = dimension.coerceAtLeast(1f)
        val x = abs(position).coerceAtMost(d * 0.9f)
        return sign(position) * x * d / (0.52f * (d - x))
    }

    fun derivative(raw: Float, dimension: Float): Float {
        val r = 1f + 0.52f * abs(raw) / dimension.coerceAtLeast(1f)
        return 0.52f / (r * r)
    }
}

/** A returning spring may cross zero; its boundary remains the one that launched it. */
internal class CMBoundaryAnchor {
    var direction = 0f
        private set

    fun begin(delta: Float) {
        direction = sign(delta)
    }

    fun isAttached(canScrollBackward: Boolean, canScrollForward: Boolean): Boolean =
        when {
            direction > 0f -> !canScrollBackward
            direction < 0f -> !canScrollForward
            else -> false
        }

    fun clear() {
        direction = 0f
    }
}

/** Exact damped spring integration stays stable even when a frame is late. */
internal class CMSpringState(var position: Float, var velocity: Float) {
    fun step(seconds: Float, stiffness: Float = 190f, dampingRatio: Float = 0.84f) {
        val decay = dampingRatio * sqrt(stiffness)
        val frequency = sqrt(stiffness - decay * decay)
        val envelope = exp(-decay * seconds)
        val c = cos(frequency * seconds)
        val s = sin(frequency * seconds)
        val x = position
        val v = velocity
        position = envelope * (x * c + (v + decay * x) / frequency * s)
        velocity = envelope * (v * c - (decay * v + stiffness * x) / frequency * s)
    }
}

/** Retargeting cancels only the frame loop, never the physical state. */
internal class CMTargetSpringMotion(initial: Float = 0f) {
    var value by mutableFloatStateOf(initial)
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    private var job: Job? = null

    fun animateTo(target: Float, scope: CoroutineScope, stiffness: Float, dampingRatio: Float) {
        job?.cancel()
        val state = CMSpringState(value - target, velocity)
        var previous = System.nanoTime()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            while (kotlin.math.abs(state.position) > 0.0001f ||
                kotlin.math.abs(state.velocity) > 0.001f) {
                val now = withFrameNanos { it }
                state.step(((now - previous) / 1_000_000_000f).coerceIn(0f, 0.064f),
                    stiffness, dampingRatio)
                previous = now
                value = target + state.position
                velocity = state.velocity
            }
            value = target
            velocity = 0f
        }
    }
}

/** Offset is read by the drawing layer; idle motion schedules no frames. */
internal class CMElasticMotion {
    var offset by mutableFloatStateOf(0f)
        private set
    private var raw = 0f
    private var job: Job? = null
    val moving: Boolean get() = job?.isActive == true
    fun reset() {
        job?.cancel()
        job = null
        raw = 0f
        offset = 0f
    }

    fun stop(dimension: Float, boundaryDirection: Float = 0f) {
        job?.cancel()
        job = null
        // The returning spring's undershoot is cosmetic, not an opposite boundary.
        // A new finger drag must not inherit it as resistance on the wrong side.
        if (boundaryDirection != 0f && offset * boundaryDirection < 0f) offset = 0f
        raw = CMRubberBand.raw(offset, dimension)
    }

    fun drag(delta: Float, dimension: Float, releaseOnly: Boolean = false): Float {
        val before = raw
        raw = if (releaseOnly) {
            if (raw > 0f) (raw + delta).coerceAtLeast(0f)
            else if (raw < 0f) (raw + delta).coerceAtMost(0f)
            else 0f
        } else (raw + delta).coerceIn(-dimension * 3f, dimension * 3f)
        offset = CMRubberBand.position(raw, dimension)
        return raw - before
    }

    /** Inward motion unwinds only this boundary; its remainder belongs to content. */
    fun consumeInward(delta: Float, dimension: Float, boundaryDirection: Float): Float {
        if (offset == 0f || offset * delta >= 0f) return 0f
        stop(dimension, boundaryDirection)
        return drag(delta, dimension, releaseOnly = true)
    }

    fun release(
        fingerVelocity: Float,
        dimension: Float,
        scope: CoroutineScope,
        dampingRatio: Float = 0.84f,
        stiffness: Float = 190f
    ) {
        job?.cancel()
        val state = CMSpringState(
            offset,
            (fingerVelocity * CMRubberBand.derivative(raw, dimension))
                .coerceIn(-dimension * 3f, dimension * 3f)
        )
        if (abs(state.position) < 0.1f && abs(state.velocity) < 4f) {
            offset = 0f
            raw = 0f
            job = null
            return
        }
        var previous = System.nanoTime()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            while (true) {
                val now = withFrameNanos { it }
                state.step(((now - previous) / 1_000_000_000f).coerceAtMost(1f),
                    stiffness = stiffness, dampingRatio = dampingRatio)
                previous = now
                offset = state.position
                raw = CMRubberBand.raw(offset, dimension)
                if (abs(offset) < 0.1f && abs(state.velocity) < 4f) break
            }
            offset = 0f
            raw = 0f
        }
    }
}

/** FlingBehavior uses scroll signs; the edge uses finger signs, as nested scroll does. */
internal class CMEdgeFlingScope(
    private val content: ScrollScope,
    private val unwind: (Float) -> Float
) : ScrollScope {
    override fun scrollBy(pixels: Float): Float {
        val pre = -unwind(-pixels)
        return pre + content.scrollBy(pixels - pre)
    }
}
