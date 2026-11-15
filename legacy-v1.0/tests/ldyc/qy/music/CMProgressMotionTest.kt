package ldyc.qy.music

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CMProgressMotionTest {
    @Test fun capsuleKeepsMovingThroughDockingAndSlowsContinuouslyAtEdge() {
        val length = 300f
        val dot = 24f
        var previous = 0f
        for (x in 1..300) {
            val current = progressDotOffset(x.toFloat(), length, dot)
            assertTrue(current > previous)
            assertEquals(length - dot - current,
                progressDotOffset(length - x, length, dot), 0.0001f)
            previous = current
        }
        val h = 0.01f
        val nearEdgeSpeed = progressDotOffset(h, length, dot) / h
        val leftJoinSpeed = (progressDotOffset(dot, length, dot) -
            progressDotOffset(dot - h, length, dot)) / h
        val rightJoinSpeed = (progressDotOffset(dot + h, length, dot) -
            progressDotOffset(dot, length, dot)) / h
        assertTrue(nearEdgeSpeed < 0.001f)
        assertEquals(leftJoinSpeed, rightJoinSpeed, 0.001f)
    }

    @Test fun lateFrameMatchesSmoothFrames() {
        for (damping in listOf(0.86f, 0.95f, 1f)) {
            val late = progressSpringStep(0.6f, 4f, 0.16f, 460f, damping)
            var smooth = 0.6f to 4f
            repeat(10) {
                smooth = progressSpringStep(smooth.first, smooth.second, 0.016f, 460f, damping)
            }
            assertEquals(smooth.first, late.first, 0.00001f)
            assertEquals(smooth.second, late.second, 0.00001f)
        }
    }

    @Test fun criticalTrackingConvergesWithoutHittingBoundaryEarly() {
        var state = -1f to 0f
        var previous = -1f
        repeat(120) {
            state = progressSpringStep(state.first, state.second, 1f / 60f, 1100f, 1f)
            assertTrue(state.first <= 0f)
            assertTrue(state.first >= previous)
            previous = state.first
        }
        assertTrue(abs(state.first) < 0.00001f)
        assertTrue(abs(state.second) < 0.0001f)
    }

    @Test fun interruptedReturnKeepsPositionAndVelocity() = runBlocking {
        val frames = Channel<Long>(Channel.UNLIMITED)
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
                onFrame(frames.receive())
        }
        val scope = CoroutineScope(coroutineContext + Job() + clock)
        try {
            val motion = CMProgressMotion(16f)
            var time = 0L
            motion.animateTo(0f, scope)
            repeat(5) {
                time += 16_000_000L
                frames.send(time)
                yield()
            }
            val position = motion.value
            val speed = motion.velocity
            assertTrue(position in 0f..16f)
            assertTrue(speed < 0f)
            motion.animateTo(24f, scope, 1100f, 1f)
            assertEquals(position, motion.value, 0f)
            assertEquals(speed, motion.velocity, 0f)
            // An outgoing return brakes before changing direction.
            time += 1_000_000L
            frames.send(time)
            yield()
            assertTrue(motion.value < position)
            repeat(120) {
                time += 16_000_000L
                frames.send(time)
                yield()
            }
            assertEquals(24f, motion.value, 0.0001f)
            assertEquals(0f, motion.velocity, 0.001f)
        } finally {
            scope.cancel()
            frames.close()
        }
    }
}
