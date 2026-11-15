package com.lindicra.cms

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CMScrollBoundaryTest {
    @Test fun inwardFlingSharesOneFrameBetweenStretchAndContentAtEitherBoundary() {
        for (direction in listOf(1f, -1f)) {
            val edge = CMElasticMotion()
            edge.drag(100f * direction, 600f)
            val delivered = mutableListOf<Float>()
            val content = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float {
                    delivered.add(pixels)
                    return pixels
                }
            }
            val fling = CMEdgeFlingScope(content) {
                edge.consumeInward(it, 600f, direction)
            }
            assertEquals(40f * direction, fling.scrollBy(40f * direction), 0.0001f)
            assertTrue(edge.offset * direction > 0f)
            assertEquals(100f * direction, fling.scrollBy(100f * direction), 0.0001f)
            assertEquals(0f, edge.offset, 0f)
            assertEquals(40f * direction, delivered.sum(), 0.0001f)
            assertEquals(25f * direction, fling.scrollBy(25f * direction), 0.0001f)
            assertEquals(65f * direction, delivered.sum(), 0.0001f)
        }
    }

    @Test fun actualOppositeBoundaryReturnsRemainderRatherThanInventingConsumption() {
        val edge = CMElasticMotion()
        edge.drag(30f, 600f)
        var position = 0f
        val content = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                val previous = position
                position = (position + pixels).coerceIn(0f, 50f)
                return position - previous
            }
        }
        val fling = CMEdgeFlingScope(content) { edge.consumeInward(it, 600f, 1f) }
        assertEquals(80f, fling.scrollBy(120f), 0.0001f)
        assertEquals(50f, position, 0f)
        assertEquals(0f, edge.offset, 0f)
    }

    @Test fun grabbingAReboundAndFlickingInwardEscapesBothEdgesWithoutOppositeStretch() = runBlocking {
        val frames = Channel<Long>(Channel.UNLIMITED)
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
                onFrame(frames.receive())
        }
        val scope = CoroutineScope(coroutineContext + Job() + clock)
        try {
            for (direction in listOf(1f, -1f)) {
                // Early return, near zero, and the spring's cosmetic undershoot.
                for (frameCount in listOf(1, 8, 20, 40)) {
                    while (frames.tryReceive().isSuccess) { /* Discard idle frame ticks. */ }
                    val edge = CMElasticMotion()
                    edge.drag(180f * direction, 600f)
                    var time = System.nanoTime()
                    edge.release(0f, 600f, scope)
                    repeat(frameCount) {
                        time += 16_000_000L
                        frames.send(time)
                        yield()
                    }
                    edge.stop(600f, direction)
                    val stretch = abs(CMRubberBand.raw(edge.offset, 600f))
                    var travel = 0f
                    val content = object : ScrollScope {
                        override fun scrollBy(pixels: Float): Float {
                            travel += pixels * direction
                            return pixels
                        }
                    }
                    val fling = CMEdgeFlingScope(content) {
                        edge.consumeInward(it, 600f, direction)
                    }
                    val trajectory = CMFlingTrajectory(2000f * direction, 35f)
                    var previous = 0f
                    for (frame in 1..240) {
                        val position = trajectory.position(frame * 0.016f)
                        val delta = position - previous
                        assertEquals(delta, fling.scrollBy(delta), 0.001f)
                        assertTrue(edge.offset * direction >= 0f)
                        previous = position
                    }
                    assertEquals(abs(previous) - stretch, travel, 0.02f)
                    assertTrue(travel > 100f)
                    assertEquals(0f, edge.offset, 0f)
                    edge.reset()
                }
            }
        } finally {
            scope.cancel()
            frames.close()
        }
    }
}
