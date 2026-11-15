package ldyc.qy.music

import kotlin.math.*

/**
 * Two incompressible surface modes: extension and fore/aft bending.
 * Capillary energy restores the surface; viscosity dissipates mode velocity.
 * Coordinates use a determinant-one extension followed by an area-preserving shear.
 */
internal class CMTabFluid {
    var strain = 0f
        private set
    var shear = 0f
        private set
    private var extensionSpeed = 0f
    private var shearSpeed = 0f
    val settled get() = abs(strain) + abs(shear) < 0.0001f &&
        abs(extensionSpeed) + abs(shearSpeed) < 0.001f
    val energy get() = 0.5f * (extensionSpeed * extensionSpeed + shearSpeed * shearSpeed) +
        95f * (cosh(2f * strain) - 1f) + 70f * (cosh(2f * shear) - 1f)

    fun step(seconds: Float, velocity: Float, acceleration: Float, contact: Float) {
        if (!seconds.isFinite() || seconds <= 0f) return
        val duration = seconds.coerceAtMost(0.1f)
        val steps = ceil(duration * 240f).toInt().coerceAtLeast(1)
        val dt = duration / steps
        val v = if (velocity.isFinite()) velocity.coerceIn(-20f, 20f) else 0f
        val a = if (acceleration.isFinite()) acceleration.coerceIn(-120f, 120f) else 0f
        val wall = if (contact.isFinite()) contact.coerceIn(-1f, 1f) else 0f
        repeat(steps) {
            val extensionForce = (v * v * 8f).coerceAtMost(140f) - abs(wall) * 65f
            val bendingForce = -a * 1.8f - wall * 95f
            extensionSpeed = (extensionSpeed + dt *
                (extensionForce - 190f * sinh(2f * strain))) / (1f + 12f * dt)
            shearSpeed = (shearSpeed + dt *
                (bendingForce - 140f * sinh(2f * shear))) / (1f + 10f * dt)
            strain += extensionSpeed * dt
            shear += shearSpeed * dt
            if (abs(strain) > 0.42f) {
                strain = strain.coerceIn(-0.42f, 0.42f)
                if (strain * extensionSpeed > 0f) extensionSpeed = 0f
            }
            if (abs(shear) > 0.85f) {
                shear = shear.coerceIn(-0.85f, 0.85f)
                if (shear * shearSpeed > 0f) shearSpeed = 0f
            }
        }
    }
}

/** Forward map shared by the resting silhouette and the native optical inverse map. */
internal fun tabFluidPoint(x: Float, y: Float, width: Float, height: Float,
                           strain: Float, shear: Float): Pair<Float, Float> {
    val stretch = exp(strain)
    val bend = shear * width * 0.12f * (1f - (2f * y / height).pow(2))
    return stretch * (x + bend) to y / stretch
}

internal fun tabFluidWidth(width: Float, strain: Float, shear: Float) =
    width * exp(strain) * (1f + 0.24f * abs(shear))
