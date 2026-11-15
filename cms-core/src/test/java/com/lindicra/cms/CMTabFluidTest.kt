package com.lindicra.cms

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CMTabFluidTest {
    @Test fun fastReleaseReboundsFromCurrentCompressionWithoutResettingShape() {
        val released = CMTabFluid()
        val ordinary = CMTabFluid()
        repeat(30) {
            released.step(1f / 60, 5f, 0f, 0f)
            ordinary.step(1f / 60, 5f, 0f, 0f)
        }
        val initialWidth = tabFluidWidth(90f, released.strain, released.shear)
        released.beginReleaseRecovery()
        assertEquals(initialWidth, tabFluidWidth(90f, released.strain, released.shear), 0f)
        assertTrue(initialWidth < 90f)
        var releasePeak = 0f
        var ordinaryPeak = 0f
        repeat(60) {
            released.step(1f / 60, 0f, 0f, 0f)
            ordinary.step(1f / 60, 0f, 0f, 0f)
            releasePeak = maxOf(releasePeak, released.strain)
            ordinaryPeak = maxOf(ordinaryPeak, ordinary.strain)
        }
        assertTrue(releasePeak > ordinaryPeak * 1.2f)
        repeat(240) { released.step(1f / 60, 0f, 0f, 0f) }
        assertTrue(released.settled)
    }

    @Test fun landingAddsLessWideningWhileFastDragIsAlreadyCompressed() {
        val compressed = CMTabFluid()
        val compressedWithoutLanding = CMTabFluid()
        repeat(30) {
            compressed.step(1f / 60, 5f, 0f, 0f)
            compressedWithoutLanding.step(1f / 60, 5f, 0f, 0f)
        }
        assertTrue(compressed.strain < 0f)
        // 平移刚停止也不能忽略尚在恢复的横向压缩。
        compressed.step(1f / 60, 0f, 0f, 0f, -3f, 1f)
        compressedWithoutLanding.step(1f / 60, 0f, 0f, 0f)
        val resting = CMTabFluid()
        resting.step(1f / 60, 0f, 0f, 0f, -3f, 1f)
        val addedWidening = compressed.strain - compressedWithoutLanding.strain
        assertTrue(addedWidening > 0f)
        assertTrue(addedWidening < resting.strain * 0.5f)
        assertTrue(compressed.strain < 0f)
        repeat(240) { compressed.step(1f / 60, 0f, 0f, 0f) }
        assertTrue(compressed.settled)
    }

    @Test fun landingFollowsReleaseDirectionGentlyAndSettles() {
        val right = CMTabFluid()
        val left = CMTabFluid()
        repeat(12) {
            right.step(1f / 60, 0f, 0f, 0f, -3f, 1f, 1f)
            left.step(1f / 60, 0f, 0f, 0f, -3f, 1f, -1f)
        }
        assertEquals(right.strain, left.strain, 0.0001f)
        assertEquals(right.shear, -left.shear, 0.0001f)
        assertTrue(right.shear > 0f && right.shear < 0.08f)
        repeat(240) {
            right.step(1f / 60, 0f, 0f, 0f)
            left.step(1f / 60, 0f, 0f, 0f)
        }
        assertTrue(right.settled && left.settled)
    }

    @Test fun landingSpeedControlsFlatteningWithoutSidewaysWobbleAndThenSettles() {
        val gentle = CMTabFluid()
        val hard = CMTabFluid()
        repeat(12) {
            gentle.step(1f / 60, 0f, 0f, 0f, fallVelocity = -1f, landingContact = 1f)
            hard.step(1f / 60, 0f, 0f, 0f, fallVelocity = -3f, landingContact = 1f)
        }
        assertTrue(gentle.strain > 0f)
        assertTrue(hard.strain > gentle.strain)
        assertEquals(0f, hard.shear, 0.00001f)
        val energy = hard.energy
        repeat(240) { hard.step(1f / 60, 0f, 0f, 0f) }
        assertTrue(hard.energy < energy * 0.00001f)
        assertTrue(hard.settled)
    }

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
