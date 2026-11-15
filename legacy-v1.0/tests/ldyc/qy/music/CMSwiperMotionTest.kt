package ldyc.qy.music

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CMSwiperMotionTest {
    @Test fun indicatorsKeepEqualEdgeGapsThroughoutPageTransitions() {
        for (progress in listOf(0f, 0.1f, 0.5f, 0.9f, 1f, 3.4f, 7f)) {
            val layout = CMSwiperIndicatorLayout(8, progress, 6f, 18f, 5f)
            for (index in 0 until 7) {
                assertEquals(5f, layout.leading(index + 1) -
                    (layout.leading(index) + layout.length(index)), 0.00001f)
            }
        }
    }

    @Test fun directionCanTurnWithoutLiftingAndVerticalMovementDoesNotClaimHorizontalPaging() {
        val axes = CMGestureAxis(8f)
        axes.add(10L, 2f, 12f)
        assertEquals(CMGestureAxis.Axis.Vertical, axes.axis)
        axes.add(30L, 14f, 1f)
        assertEquals(CMGestureAxis.Axis.Horizontal, axes.axis)
        axes.add(50L, 1f, -14f)
        assertEquals(CMGestureAxis.Axis.Vertical, axes.axis)
    }

    private class FrameClock(val step: Long, val observe: () -> Unit = {}) : MonotonicFrameClock {
        var now = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            now += step
            observe()
            return onFrame(now)
        }
    }

    @Test fun partialPagesEndAtTheContentBoundary() {
        assertEquals(-1232f, swiperMinimum(5, 300f, 8f, 300f), 0f)
        assertEquals(-482f, swiperMinimum(5, 150f, 8f, 300f), 0f)
        assertEquals(0f, swiperMinimum(1, 150f, 8f, 300f), 0f)
    }

    @Test fun projectedPageUsesTheActualPositionAndSignedVelocity() {
        assertEquals(2, swiperTargetPage(-520f, 0f, 300f, 5))
        assertEquals(3, swiperTargetPage(-520f, -1500f, 300f, 5))
        assertEquals(1, swiperTargetPage(-520f, 1500f, 300f, 5))
        assertEquals(0, swiperTargetPage(50f, 1500f, 300f, 5))
    }

    @Test fun edgeResistanceDoesNotDependOnEventFrequencyAndRegrabDoesNotJump() {
        val single = CMSwiperMotion().apply { configure(-600f, 300f, 300f, true); drag(90f) }
        val split = CMSwiperMotion().apply {
            configure(-600f, 300f, 300f, true)
            repeat(9) { drag(10f) }
        }
        assertEquals(single.value, split.value, 0.0001f)
        val before = split.value
        split.stop()
        split.drag(0f)
        assertEquals(before, split.value, 0.0001f)
        split.drag(-90f)
        assertEquals(0f, split.value, 0.0001f)
    }

    @Test fun releasingOutwardPreservesMomentumThenReturnsAtBothFrameRates() {
        for (frame in listOf(16_666_667L, 8_333_333L)) {
            val motion = CMSwiperMotion()
            val values = mutableListOf<Float>()
            val clock = FrameClock(frame) { values.add(motion.value) }
            runBlocking(clock) {
                motion.configure(-600f, 300f, 300f, true)
                motion.drag(80f)
                val released = motion.value
                motion.settle(0f, this, 1500f)
                yield()
                assertTrue(values.maxOrNull()!! > released + 1f)
                assertEquals(0f, motion.value, 0f)
                assertEquals(0f, motion.velocity, 0f)
                assertTrue(!motion.moving)
                assertTrue(clock.now < 3_000_000_000L)
            }
        }
    }

    @Test fun disablingSpringClampsDragAndFlingAtBothEdges() {
        val motion = CMSwiperMotion()
        val values = mutableListOf<Float>()
        runBlocking(FrameClock(16_666_667L) { values.add(motion.value) }) {
            motion.configure(-600f, 300f, 300f, false)
            motion.drag(100f)
            assertEquals(0f, motion.value, 0f)
            motion.drag(-1000f)
            assertEquals(-600f, motion.value, 0f)
            motion.drag(300f)
            motion.releaseFree(-3000f, this)
            yield()
            assertEquals(-600f, motion.value, 0f)
            assertTrue(values.all { it in -600f..0f })
        }
    }

    @Test fun resizingAndReducingCountPreserveThenClampPageProgress() {
        val motion = CMSwiperMotion()
        motion.configure(-900f, 300f, 300f, true)
        motion.drag(-450f)
        motion.configure(-1800f, 600f, 600f, true)
        assertEquals(-900f, motion.value, 0f)
        motion.configure(-600f, 600f, 600f, true)
        assertEquals(-600f, motion.value, 0f)
    }
}
