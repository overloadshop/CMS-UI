package ldyc.qy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CMScrollbarTest {
    @Test fun previewDocksToTheIndexWithOnlySmallFingerInfluence() {
        assertEquals(55f, scrollbarPreviewAnchor(55f, 2, 8, 176f), 0f)
        assertEquals(56.1f, scrollbarPreviewAnchor(100f, 2, 8, 176f), 0.0001f)
        assertEquals(53.9f, scrollbarPreviewAnchor(0f, 2, 8, 176f), 0.0001f)
        assertEquals(77f, scrollbarPreviewAnchor(77f, 3, 8, 176f), 0f)
    }

    @Test fun packedLettersUseCompactHitRangeWithoutChangingDistributedTrack() {
        assertEquals(176f, scrollbarIndexExtent(600f, 8, 22f,
            CMScrollbarIndexLayout.TopPacked), 0f)
        assertEquals(600f, scrollbarIndexExtent(600f, 8, 22f,
            CMScrollbarIndexLayout.Distributed), 0f)
        assertEquals(120f, scrollbarIndexExtent(120f, 26, 22f,
            CMScrollbarIndexLayout.TopPacked), 0f)
    }

    @Test fun shortFinalGroupStretchesOnlyAfterReachingRealBottom() {
        assertEquals(0f, scrollbarIndexOverflow(0f, 80f, false,
            false, false, false, true), 0f)
        assertEquals(25f, scrollbarIndexOverflow(0f, 25f, true,
            false, true, false, true), 0f)
        assertEquals(0f, scrollbarIndexOverflow(0f, 25f, true,
            false, false, false, false), 0f)
    }

    @Test fun reversingAtBoundaryReleasesStretchBeforeAnotherGroup() {
        assertEquals(18f, scrollbarIndexOverflow(40f, -22f, true,
            false, true, false, true), 0f)
        assertEquals(0f, scrollbarIndexOverflow(18f, -30f, true,
            false, true, false, true), 0f)
        assertEquals(0f, scrollbarIndexOverflow(40f, 20f, false,
            false, true, false, true), 0f)
        assertEquals(-25f, scrollbarIndexOverflow(0f, -25f, true,
            true, false, true, false), 0f)
        assertEquals(-10f, scrollbarIndexOverflow(-25f, 15f, true,
            true, false, true, false), 0f)
    }

    @Test fun thumbGrabDoesNotJumpAtAnyPosition() {
        for (position in listOf(0f, 325f, 1400f)) {
            val geometry = CMScrollbarGeometry.calculate(600f, 1400f, position, 0f, 16f)
            for (grab in listOf(0f, geometry.length / 2, geometry.length)) {
                assertEquals(position / 1400f,
                    scrollbarSeekFraction(geometry.top + grab, grab, 600f, geometry.length),
                    0.000001f)
            }
        }
    }

    @Test fun draggingClampsAtBothEndsAndHandlesShortTrack() {
        assertEquals(0f, scrollbarSeekFraction(-30f, 20f, 600f, 180f), 0f)
        assertEquals(1f, scrollbarSeekFraction(800f, 20f, 600f, 180f), 0f)
        assertEquals(0f, scrollbarSeekFraction(30f, 20f, 10f, 10f), 0f)
    }

    @Test fun draggingBeyondTrackPreservesDistanceWhileContentPositionClamps() {
        val track = 600f
        val thumb = 180f
        val grab = 20f
        assertEquals(0f, scrollbarDragOverflow(grab, grab, track, thumb), 0f)
        assertEquals(-30f, scrollbarDragOverflow(grab - 30f, grab, track, thumb), 0f)
        assertEquals(0f, scrollbarSeekFraction(grab - 30f, grab, track, thumb), 0f)
        val bottom = track - thumb + grab
        assertEquals(0f, scrollbarDragOverflow(bottom, grab, track, thumb), 0f)
        assertEquals(75f, scrollbarDragOverflow(bottom + 75f, grab, track, thumb), 0f)
        assertEquals(1f, scrollbarSeekFraction(bottom + 75f, grab, track, thumb), 0f)
        val near = CMRubberBand.position(30f, track)
        val far = CMRubberBand.position(75f, track)
        assertTrue(far > near && far < 75f)
        assertEquals(-far, CMRubberBand.position(-75f, track), 0.0001f)
    }

    @Test fun overscrollShrinksThumbByActualDistanceAndKeepsEdgeAttached() {
        val base = CMScrollbarGeometry.calculate(600f, 1400f, 0f, 0f, 16f)
        val top = CMScrollbarGeometry.calculate(600f, 1400f, 0f, 45f, 16f)
        val bottom = CMScrollbarGeometry.calculate(600f, 1400f, 1400f, -45f, 16f)
        assertEquals(base.length - 45f, top.length, 0.0001f)
        assertEquals(0f, top.top, 0f)
        assertEquals(600f, bottom.top + bottom.length, 0.0001f)
        assertTrue(CMScrollbarGeometry.calculate(600f, 1400f, 0f, 900f, 16f).length >= 16f)
    }
}
