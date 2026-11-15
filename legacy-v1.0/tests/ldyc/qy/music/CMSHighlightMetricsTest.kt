package ldyc.qy.music

import com.qmdeve.liquidglass.widget.LiquidGlassHighlightMetrics as Metrics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CMSHighlightMetricsTest {
    private fun width(size: Float, density: Float = 1f, scale: Float = 1f,
                      cap: Float = Float.POSITIVE_INFINITY, stretch: Float = 1f) =
        Metrics.widthPx(size * density, size * density, density, scale, cap, stretch) / density

    @Test fun densityAndSizeNeverInflatePhysicalWidth() {
        for (size in listOf(1f, 28f, 44f, 280f, 10000f)) {
            val baseline = width(size)
            assertTrue(baseline in 0f..0.8f)
            for (density in listOf(1.5f, 2f, 3.5f)) {
                assertEquals(baseline, width(size, density), 0.00001f)
            }
        }
        assertTrue(width(1f) <= 0.125f)
    }

    @Test fun customCapsAndThinSearchHighlightsSurviveStretch() {
        assertEquals(width(44f) * 0.92f, width(44f, scale = 0.92f), 0.00001f)
        assertEquals(0.3f, width(44f, cap = 0.3f), 0.00001f)
        assertEquals(0.3f, width(44f, cap = 0.3f, stretch = 1.6f) * 1.6f, 0.00001f)
        assertEquals(0f, width(44f, cap = 0f), 0f)
    }

    @Test fun rotationDoesNotChangeWidthAndShearStaysWithinCap() {
        val angle = 0.7f
        assertEquals(1f, Metrics.maximumStretch(cos(angle), -sin(angle), sin(angle), cos(angle)), 0.00001f)
        val stretch = Metrics.maximumStretch(1f, 0.8f, 0f, 1f)
        assertTrue(stretch > 1f)
        assertTrue(width(44f, stretch = stretch) * stretch <= 0.8f)
    }

    @Test fun invalidParametersCannotPoisonRendering() {
        assertEquals(width(44f), width(44f, scale = Float.NaN, cap = Float.NaN), 0f)
        assertEquals(0f, Metrics.widthPx(Float.NaN, 44f, 1f, 1f, 1f, 1f), 0f)
        assertEquals(0f, Metrics.widthPx(44f, 44f, 0f, 1f, 1f, 1f), 0f)
    }

    @Test fun widthRemainsContinuousAcrossFormerAspectThreshold() {
        val before = Metrics.widthPx(63.79f, 44f, 1f, 1f, 1f, 1f)
        val after = Metrics.widthPx(63.81f, 44f, 1f, 1f, 1f, 1f)
        assertEquals(before, after, 0.000001f)
        assertTrue(width(44.01f) - width(44f) < 0.0001f)
    }
}
