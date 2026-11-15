package com.lindicra.cms

import org.junit.Assert.*
import org.junit.Test

class CMSTouchPriorityTest {
    @Test fun movingTableViewportDoesNotCreateFingerIntentOrReleaseVelocity() {
        val initial = androidx.compose.ui.geometry.Offset(200f, 300f)
        val motion = CMSWindowMotion(initial)
        val session = CMSTouchPrioritySession(8f)
        val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
        tracker.addPosition(0L, initial)
        // The parent scrolls/rebounds while the finger remains at the same window point.
        for (frame in 1..12) {
            val origin = androidx.compose.ui.geometry.Offset(0f, frame * 12f)
            val local = initial - origin
            val delta = motion.add(local + origin)
            session.add(frame * 16L, delta.x, delta.y)
            tracker.addPosition(frame * 16L, motion.position)
            assertEquals(androidx.compose.ui.geometry.Offset.Zero, delta)
        }
        assertNull(session.axis)
        assertEquals(androidx.compose.ui.unit.Velocity.Zero, tracker.calculateVelocity())
        val moved = motion.add(initial + androidx.compose.ui.geometry.Offset(20f, -12f))
        assertEquals(20f, moved.x, 0f)
        assertEquals(-12f, moved.y, 0f)
    }

    @Test fun oldContactCannotDragEndOrCancelTheNextContact() {
        for (cancelOld in listOf(false, true)) {
            val bridge = CMVerticalDragBridge()
            val delivered = mutableListOf<Float>()
            var ends = 0
            var cancels = 0
            bridge.begin = { bridge.gestureGeneration++ }
            bridge.drag = { delivered.add(it); Unit }
            bridge.end = { ends++ }
            bridge.cancel = { cancels++ }
            val old = CMVerticalDragContinuity(bridge, 8f, CMSTouchPriority())
            old.drag(true, 20f, 20f)
            // A new down interrupts even before its own direction is known.
            bridge.gestureGeneration++
            val next = CMVerticalDragContinuity(bridge, 8f, CMSTouchPriority())
            next.drag(true, -20f, -20f)
            old.drag(true, 100f, 100f)
            if (cancelOld) old.cancel() else old.end(900f)
            assertEquals(0, ends)
            assertEquals(0, cancels)
            next.drag(true, -4f, -4f)
            next.end(-300f)
            assertEquals(listOf(12f, -12f, -4f), delivered)
            assertEquals(1, ends)
            assertEquals(0, cancels)
        }
    }

    @Test fun glassReservationCanYieldToScrollingWithoutReservingShadowOverflow() {
        val registry = CMSTouchRegionRegistry()
        val horizontal = CMSTouchRegion { true }.apply {
            bounds = androidx.compose.ui.geometry.Rect(0f, 0f, 300f, 200f)
        }
        var glassFirst = true
        val glass = CMSTouchRegion(nativeGlass = true) { glassFirst }.apply {
            bounds = androidx.compose.ui.geometry.Rect(20f, 20f, 100f, 80f)
        }
        registry.register(horizontal)
        registry.register(glass)
        val body = androidx.compose.ui.geometry.Offset(30f, 30f)
        assertSame(glass, registry.at(body))
        assertSame(horizontal, registry.at(androidx.compose.ui.geometry.Offset(10f, 30f)))
        glassFirst = false
        assertSame(horizontal, registry.at(body))
        registry.remove(glass)
        assertSame(horizontal, registry.at(body))
    }

    @Test fun rapidPerpendicularStrokeDoesNotWaitForTheOldMotionWindowToExpire() {
        val session = CMSTouchPrioritySession(8f)
        session.add(0, 20f, 0f)
        repeat(5) { session.add(10L + it * 10, 16f, 0f) }
        assertEquals(CMSTouchAxis.Horizontal, session.axis)
        session.add(60, 0f, -3f)
        session.add(70, 0f, -3f)
        assertEquals(CMSTouchAxis.Horizontal, session.axis)
        assertTrue(session.add(80, 0f, -4f))
        assertEquals(CMSTouchAxis.Vertical, session.axis)
        assertEquals(-10f, session.recentY, 0f)
        session.add(90, 0f, -24f)
        assertTrue(session.add(100, 10f, 0f))
        assertEquals(CMSTouchAxis.Horizontal, session.axis)
    }

    @Test fun perpendicularJitterAndRenewedForwardMotionDoNotBecomeATurn() {
        val session = CMSTouchPrioritySession(8f)
        session.add(0, 20f, 0f)
        repeat(12) { session.add(10L + it * 5, 0f, if (it % 2 == 0) 2f else -2f) }
        assertEquals(CMSTouchAxis.Horizontal, session.axis)
        session.add(75, 0f, 5f)
        session.add(80, 20f, 0f)
        session.add(85, 0f, 5f)
        assertEquals(CMSTouchAxis.Horizontal, session.axis)
        val reset = CMSTouchPrioritySession(8f)
        reset.add(0, 20f, 0f)
        reset.add(10, 0f, 7f)
        reset.add(20, 2f, 0f)
        reset.add(30, 0f, 3f)
        assertEquals(CMSTouchAxis.Horizontal, reset.axis)
    }

    @Test fun liftingCreatesFreshIntentWithoutThePreviousAxisLock() {
        val policy = CMSTouchPriority(allowAxisChange = false)
        val first = CMSTouchPrioritySession(8f, policy)
        first.add(0, 20f, 0f)
        first.add(10, 0f, 40f)
        assertEquals(CMSTouchAxis.Horizontal, first.axis)
        val next = CMSTouchPrioritySession(8f, policy)
        next.add(12, 0f, 9f)
        assertEquals(CMSTouchAxis.Vertical, next.axis)
    }

    @Test fun nestedRegionsPreferTheSmallestViewportAndIgnoreContentThatFits() {
        val registry = CMSTouchRegionRegistry()
        val outer = CMSTouchRegion { true }.apply {
            bounds = androidx.compose.ui.geometry.Rect(0f, 0f, 300f, 200f)
        }
        var overflowing = true
        val inner = CMSTouchRegion { overflowing }.apply {
            bounds = androidx.compose.ui.geometry.Rect(20f, 20f, 200f, 100f)
        }
        registry.register(outer)
        registry.register(inner)
        val point = androidx.compose.ui.geometry.Offset(30f, 30f)
        assertSame(inner, registry.at(point))
        overflowing = false
        assertSame(outer, registry.at(point))
        registry.remove(outer)
        assertNull(registry.at(point))
        registry.remove(inner)
    }

    @Test fun verticalDeliveryContinuesAcrossHorizontalDominanceWithoutRestarting() {
        val policy = CMSTouchPriority()
        val session = CMSTouchPrioritySession(8f, policy)
        val bridge = CMVerticalDragBridge()
        val delivered = mutableListOf<Float>()
        var begins = 0
        var cancels = 0
        var ends = 0
        var releaseVelocity = 0f
        bridge.begin = { begins++ }
        bridge.drag = { delivered.add(it); Unit }
        bridge.cancel = { cancels++ }
        bridge.end = { ends++; releaseVelocity = it }
        val continuity = CMVerticalDragContinuity(bridge, 8f, policy)
        fun move(time: Long, dx: Float, dy: Float) {
            session.add(time, dx, dy)
            continuity.drag(session.axis == CMSTouchAxis.Vertical, dy, session.recentY)
        }

        move(0, 0f, 20f)
        move(120, 12f, 2f)
        assertEquals(CMSTouchAxis.Horizontal, session.axis)
        move(140, 0f, 3f)
        assertEquals(CMSTouchAxis.Horizontal, session.axis)
        move(240, 0f, 12f)
        assertEquals(CMSTouchAxis.Vertical, session.axis)
        assertEquals(listOf(12f, 2f, 3f, 12f), delivered)
        assertEquals(1, begins)
        assertEquals(0, cancels)
        continuity.end(450f)
        continuity.cancel()
        assertEquals(1, ends)
        assertEquals(450f, releaseVelocity, 0f)
        assertEquals(0, cancels)
    }

    @Test fun firstVerticalHandoffReplaysMovementFromDirectionObservation() {
        val policy = CMSTouchPriority()
        val session = CMSTouchPrioritySession(8f, policy)
        val bridge = CMVerticalDragBridge()
        val delivered = mutableListOf<Float>()
        bridge.drag = { delivered.add(it); Unit }
        val continuity = CMVerticalDragContinuity(bridge, 8f, policy)
        fun move(time: Long, dx: Float, dy: Float) {
            session.add(time, dx, dy)
            continuity.drag(session.axis == CMSTouchAxis.Vertical, dy, session.recentY)
        }

        move(0, 20f, 0f)
        move(20, 0f, -3f)
        move(40, 0f, -3f)
        assertFalse(continuity.started)
        move(60, 0f, -4f)
        assertEquals(CMSTouchAxis.Vertical, session.axis)
        assertEquals(listOf(-2f), delivered)
        move(80, 0f, -5f)
        assertEquals(listOf(-2f, -5f), delivered)
    }

    @Test fun horizontalAxisLockDoesNotStartParallelVerticalDelivery() {
        val policy = CMSTouchPriority(allowAxisChange = false)
        val session = CMSTouchPrioritySession(8f, policy)
        val bridge = CMVerticalDragBridge()
        var begins = 0
        bridge.begin = { begins++ }
        val continuity = CMVerticalDragContinuity(bridge, 8f, policy)
        session.add(0, 20f, 0f)
        session.add(120, 0f, 50f)
        continuity.drag(session.axis == CMSTouchAxis.Vertical, 50f, session.recentY)
        continuity.cancel()
        assertEquals(0, begins)
        assertFalse(continuity.started)
    }

    @Test fun cancellationClosesTheContinuousVerticalGestureOnlyOnce() {
        val bridge = CMVerticalDragBridge()
        var cancels = 0
        var ends = 0
        bridge.cancel = { cancels++ }
        bridge.end = { ends++ }
        val continuity = CMVerticalDragContinuity(bridge, 8f, CMSTouchPriority())
        continuity.drag(true, 20f, 20f)
        continuity.cancel()
        continuity.cancel()
        continuity.end(300f)
        assertEquals(1, cancels)
        assertEquals(0, ends)
    }

    @Test fun slowInitialSwipeCanAccumulateBeyondTheRecentWindow() {
        val session = CMSTouchPrioritySession(8f)
        repeat(8) { session.add(it * 40L, 1f, 0f) }
        assertEquals(CMSTouchAxis.Horizontal, session.axis)
        assertEquals(8f, session.recentX, 0f)
        assertTrue(session.add(400, 0f, 10f))
        assertEquals(CMSTouchAxis.Vertical, session.axis)
    }

    @Test fun aNaturalDiagonalSwipeDoesNotRequireNearlyPureHorizontalMotion() {
        val session = CMSTouchPrioritySession(8f)
        assertTrue(session.add(20, 16f, 12f))
        assertEquals(CMSTouchAxis.Horizontal, session.axis)
    }

    @Test fun slowJitterDoesNotAccumulateAsSwipeIntent() {
        val session = CMSTouchPrioritySession(8f)
        repeat(32) { session.add(it * 40L, if (it % 2 == 0) 2f else -2f, 0f) }
        assertNull(session.axis)
    }

    @Test fun shortFlickIncludesTheMovementUsedToRecognizeTheGesture() {
        assertEquals(1, CMPagerBounds.target(0f, 0f, 8f, 180f, 400f, 1f, 3, true))
        assertEquals(1, CMPagerBounds.target(0.04f, 0f, 24f, 0f, 400f, 1f, 3, true))
        assertEquals(0, CMPagerBounds.target(0.03f, 0f, 20f, 0f, 400f, 1f, 3, true))
    }

    @Test fun reversalAndVerticalHandoffDoNotCommitTheOldHorizontalSwipe() {
        assertEquals(0, CMPagerBounds.target(0.13f, 0f, 60f, -500f, 400f, 1f, 3, true))
        assertEquals(0, CMPagerBounds.target(0.20f, 0f, 88f, 900f, 400f, 1f, 3, false))
        assertEquals(1, CMPagerBounds.target(0.70f, 0f, 288f, 0f, 400f, 1f, 3, false))
    }

    @Test fun landingOnAnExactPageDoesNotSkipAnotherPageOrEscapeTheBounds() {
        assertEquals(1, CMPagerBounds.target(1f, 0f, 408f, 1200f, 400f, 1f, 3, true))
        assertEquals(2, CMPagerBounds.target(2f, 2f, 24f, 900f, 400f, 1f, 3, true))
        assertEquals(0, CMPagerBounds.target(0f, 0f, -24f, -900f, 400f, 1f, 3, true))
    }

    @Test fun recentVerticalMovementCanTakeOverAfterALongHorizontalDrag() {
        val session = CMSTouchPrioritySession(8f)
        session.add(0, 40f, 0f)
        session.add(30, 30f, 0f)
        assertEquals(CMSTouchAxis.Horizontal, session.axis)
        assertTrue(session.add(120, 0f, 12f))
        assertEquals(CMSTouchAxis.Vertical, session.axis)
    }

    @Test fun configurableWeightResolvesADiagonalWithoutLoweringTheSlop() {
        val standard = CMSTouchPrioritySession(8f)
        standard.add(0, 10f, 10f)
        assertNull(standard.axis)
        val verticalFirst = CMSTouchPrioritySession(8f,
            CMSTouchPriority(verticalPriority = 2f))
        verticalFirst.add(0, 10f, 10f)
        assertEquals(CMSTouchAxis.Vertical, verticalFirst.axis)
        val short = CMSTouchPrioritySession(8f, CMSTouchPriority(verticalPriority = 8f))
        short.add(0, 0f, 2f)
        assertNull(short.axis)
    }

    @Test fun axisLockAndJitterDoNotSwitchTheOwner() {
        val locked = CMSTouchPrioritySession(8f, CMSTouchPriority(allowAxisChange = false))
        locked.add(0, 20f, 0f)
        locked.add(120, 0f, 50f)
        assertEquals(CMSTouchAxis.Horizontal, locked.axis)
        val session = CMSTouchPrioritySession(8f)
        session.add(0, 20f, 0f)
        repeat(20) { session.add(100L + it * 5, 0f, if (it % 2 == 0) 1f else -1f) }
        assertEquals(CMSTouchAxis.Horizontal, session.axis)
    }

    @Test fun invalidSamplesCannotPoisonTheNextValidGesture() {
        val session = CMSTouchPrioritySession(Float.NaN,
            CMSTouchPriority(directionRatio = Float.NaN, historyMillis = -1L))
        assertFalse(session.add(0, Float.NaN, Float.POSITIVE_INFINITY))
        assertTrue(session.add(20, 20f, 0f))
        assertEquals(CMSTouchAxis.Horizontal, session.axis)
    }
}
