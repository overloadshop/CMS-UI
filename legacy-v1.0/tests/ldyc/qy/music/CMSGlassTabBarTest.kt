package ldyc.qy.music

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CMSGlassTabBarTest {
    @Test fun endTabsHaveIdenticalFourSideInsets() {
        for (count in 2..5) {
            val g = CMTabGeometry(360f, 64f, count, 4f)
            assertEquals(4f, g.center(0) - g.indicatorWidth / 2, 0.0001f)
            assertEquals(4f, g.width - g.center(count - 1) - g.indicatorWidth / 2, 0.0001f)
            assertEquals(4f, (g.height - g.indicatorHeight) / 2, 0.0001f)
        }
    }

    @Test fun dropUsesActualCenterAndClampsOutsideTheBar() {
        val g = CMTabGeometry(360f, 64f, 4, 4f)
        for (index in 0..3) assertEquals(index, g.landing(g.center(index)))
        assertEquals(0, g.landing(-1000f))
        assertEquals(3, g.landing(1000f))
        assertEquals(1, g.landing(g.inset + g.slot * 2 - 0.01f))
        assertEquals(2, g.landing(g.inset + g.slot * 2 + 0.01f))
    }

    @Test fun horizontalDragHasHardLimitsIncludingEnlargedLensAndSpringOvershoot() {
        val g = CMTabGeometry(360f, 52f, 4, 4f)
        assertEquals(g.center(0), g.follow(g.center(0)), 0f)
        assertEquals(g.center(0), g.follow(-10000f), 0f)
        assertEquals(g.center(3), g.follow(10000f), 0f)
        for (growth in listOf(0f, 16f, 30f)) {
            val lensWidth = g.indicatorWidth + growth
            assertEquals(lensWidth / 2f, g.renderedCenter(-10000f, lensWidth), 0f)
            assertEquals(g.width - lensWidth / 2f, g.renderedCenter(10000f, lensWidth), 0f)
        }
        assertTrue(g.indicatorHeight + 16f > g.height)
    }

    @Test fun cancellationRestoresOriginalPageWithoutCommittingTheHoveredTab() {
        val g = CMTabGeometry(360f, 64f, 4, 4f)
        assertEquals(0 to false, g.release(0, g.center(3), cancelled = true))
        assertEquals(3 to true, g.release(0, g.center(3), cancelled = false))
    }

    @Test fun releasedLensShrinksPastRestingSizeAndThenSettles() {
        var lift = 1f
        var speed = 0f
        var minimum = lift
        repeat(180) {
            val next = progressSpringStep(lift, speed, 1f / 60, 180f, 0.52f)
            lift = next.first
            speed = next.second
            minimum = minOf(minimum, lift)
        }
        assertTrue(minimum < -0.12f)
        assertEquals(0f, lift, 0.001f)
        assertEquals(0f, speed, 0.001f)
    }

    @Test fun releaseVelocityConvergesToSelectedSlotInsteadOfSkippingTabs() {
        for (speed in listOf(-2400f, 2400f)) {
            var position = 38f
            var velocity = speed
            repeat(240) {
                val next = progressSpringStep(position, velocity, 1f / 60, 460f, 0.86f)
                position = next.first; velocity = next.second
            }
            assertEquals(0f, position, 0.001f)
            assertEquals(0f, velocity, 0.001f)
        }
    }
}
