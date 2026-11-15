package ldyc.qy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Retarget the same spring, including its velocity, rather than restarting an animation. */
internal class CMProgressMotion(initial: Float) {
    var value by mutableFloatStateOf(initial)
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    private var target = initial
    private var stiffness = 460f
    private var damping = 0.86f
    private var job: Job? = null

    fun follow(value: Float) {
        job?.cancel()
        job = null
        this.value = value
        target = value
        velocity = 0f
    }

    fun release(target: Float, velocity: Float, scope: CoroutineScope,
                stiffness: Float = 460f, damping: Float = 0.86f) {
        this.velocity = velocity
        animateTo(target, scope, stiffness, damping)
    }

    fun animateTo(target: Float, scope: CoroutineScope, stiffness: Float = 460f,
                  damping: Float = 0.86f) {
        this.target = target
        this.stiffness = stiffness
        this.damping = damping
        if (job?.isActive == true) return
        job = scope.launch {
            var previous = withFrameNanos { it }
            while (abs(value - this@CMProgressMotion.target) > 0.00001f ||
                abs(velocity) > 0.0001f) {
                val now = withFrameNanos { it }
                val next = progressSpringStep(
                    value - this@CMProgressMotion.target, velocity,
                    ((now - previous) / 1_000_000_000f).coerceAtLeast(0f),
                    this@CMProgressMotion.stiffness, this@CMProgressMotion.damping
                )
                previous = now
                value = this@CMProgressMotion.target + next.first
                velocity = next.second
            }
            value = this@CMProgressMotion.target
            velocity = 0f
        }
    }
}

/** Exact integration for underdamped and critically damped motion, even across late frames. */
internal fun progressSpringStep(
    position: Float, velocity: Float, seconds: Float, stiffness: Float, damping: Float
): Pair<Float, Float> {
    val omega = sqrt(stiffness)
    val decay = damping * omega
    val envelope = exp(-decay * seconds)
    if (damping >= 1f) {
        val c = velocity + omega * position
        return envelope * (position + c * seconds) to
            envelope * (velocity - omega * c * seconds)
    }
    val frequency = omega * sqrt(1f - damping * damping)
    val c = cos(frequency * seconds)
    val s = sin(frequency * seconds)
    return envelope * (position * c + (velocity + decay * position) / frequency * s) to
        envelope * (velocity * c - (decay * velocity + stiffness * position) / frequency * s)
}

/** Capsule docking has zero speed at either endpoint and joins center-following at equal speed. */
internal fun progressDotOffset(anchor: Float, length: Float, dot: Float): Float {
    val maximum = (length - dot).coerceAtLeast(0f)
    if (maximum == 0f) return 0f
    val x = anchor.coerceIn(0f, length)
    if (length < dot * 2f) {
        val t = x / length
        return maximum * t * t * (3f - 2f * t)
    }
    return when {
        x < dot -> x * x / (2f * dot)
        x > length - dot -> maximum - (length - x) * (length - x) / (2f * dot)
        else -> x - dot / 2f
    }
}
