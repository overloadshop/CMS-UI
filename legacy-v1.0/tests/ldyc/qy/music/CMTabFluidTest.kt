package ldyc.qy.music

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CMTabFluidTest {
    @Test fun deformationPreservesLocalAreaAcrossExtensionAndBending() {
        for (strain in listOf(-0.4f, 0f, 0.4f)) for (shear in listOf(-0.8f, 0f, 0.8f)) {
            val p = tabFluidPoint(17f, 9f, 90f, 44f, strain, shear)
            val x = tabFluidPoint(17.01f, 9f, 90f, 44f, strain, shear)
            val y = tabFluidPoint(17f, 9.01f, 90f, 44f, strain, shear)
            val determinant = ((x.first - p.first) * (y.second - p.second) -
                (y.first - p.first) * (x.second - p.second)) / 0.0001f
            assertEquals(1f, determinant, 0.002f)
        }
    }

    @Test fun unforcedSurfaceDissipatesEnergyAndSettles() {
        val fluid = CMTabFluid()
        repeat(30) { fluid.step(1f / 60, 4f, 20f, 0f) }
        val energy = fluid.energy
        repeat(240) { fluid.step(1f / 60, 0f, 0f, 0f) }
        assertTrue(fluid.energy < energy * 0.00001f)
        assertTrue(fluid.settled)
    }

    @Test fun directionReversalMirrorsBendingAndPreservesExtension() {
        val right = CMTabFluid()
        val left = CMTabFluid()
        repeat(30) {
            right.step(1f / 60, 3f, 15f, 0.2f)
            left.step(1f / 60, -3f, -15f, -0.2f)
        }
        assertEquals(right.strain, left.strain, 0.0001f)
        assertEquals(right.shear, -left.shear, 0.0001f)
        assertTrue(abs(right.shear) > 0.05f)
    }

    @Test fun simulationRemainsConsistentAcrossFrameRates() {
        val surfaces = listOf(30, 60, 120).map { fps ->
            CMTabFluid().apply { repeat(fps) { step(1f / fps, 3f, 10f, 0f) } }
        }
        surfaces.forEach {
            assertEquals(surfaces.first().strain, it.strain, 0.002f)
            assertEquals(surfaces.first().shear, it.shear, 0.002f)
        }
    }

    @Test fun largeInputsStayFiniteAndWallContactCompressesTheSurface() {
        val fluid = CMTabFluid()
        repeat(500) { fluid.step(0.08f, Float.MAX_VALUE, -Float.MAX_VALUE, 1f) }
        assertTrue(fluid.strain.isFinite() && fluid.shear.isFinite())
        assertTrue(abs(fluid.strain) <= 0.42f && abs(fluid.shear) <= 0.85f)
        val wall = CMTabFluid()
        repeat(60) { wall.step(1f / 60, 0f, 0f, 1f) }
        assertTrue(wall.strain < 0f)
    }
}
