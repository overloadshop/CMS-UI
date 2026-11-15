package com.lindicra.cms

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
    private var releaseRecovery = 0f

    /** 松手只改变耗散，不重置当前宽度或形变速度，也不额外注入变宽脉冲。 */
    fun beginReleaseRecovery() {
        releaseRecovery = ((-strain).coerceAtLeast(0f) / 0.16f +
            abs(extensionSpeed) / 3f + abs(shear) / 0.5f).coerceIn(0f, 1f)
    }

    fun endReleaseRecovery() {
        releaseRecovery = 0f
    }

    val settled get() = abs(strain) + abs(shear) < 0.0001f &&
        abs(extensionSpeed) + abs(shearSpeed) < 0.001f
    val energy get() = 0.5f * (extensionSpeed * extensionSpeed + shearSpeed * shearSpeed) +
        95f * (cosh(2f * strain) - 1f) + 70f * (cosh(2f * shear) - 1f)

    fun step(
        seconds: Float, velocity: Float, acceleration: Float, contact: Float,
        fallVelocity: Float = 0f, landingContact: Float = 0f,
        landingDirection: Float = 0f
    ) {
        if (!seconds.isFinite() || seconds <= 0f) return
        val duration = seconds.coerceAtMost(0.1f)
        val steps = ceil(duration * 240f).toInt().coerceAtLeast(1)
        val dt = duration / steps
        val v = if (velocity.isFinite()) velocity.coerceIn(-20f, 20f) else 0f
        val a = if (acceleration.isFinite()) acceleration.coerceIn(-120f, 120f) else 0f
        val wall = if (contact.isFinite()) contact.coerceIn(-1f, 1f) else 0f
        val fall = if (fallVelocity.isFinite()) (-fallVelocity).coerceIn(0f, 6f) else 0f
        val landing = if (landingContact.isFinite()) landingContact.coerceIn(0f, 1f) else 0f
        val direction = if (landingDirection.isFinite()) landingDirection.coerceIn(-1f, 1f) else 0f
        repeat(steps) {
            // Inertia squashes a moving body along its path; it never stretches it. The
            // speed term is therefore negative (compression) and the area-preserving map
            // turns it into a taller, narrower lens, which is what a liquid slug does.
            // Squared speed with a low gain: casual drags barely register, and only a
            // hard throw reaches the cap. The previous gain made any movement look violent.
            // 快滑已经让镜片变高、变窄，并积累了形变速度。落地需延续这份
            // 状态，不能再完整叠加一个反向压扁脉冲，使它突然变宽。
            // 依据当前运动和压缩状态连续减弱额外落地力；即使平移已经慢下来，
            // 尚未恢复的压缩与形变速度仍参与计算，不重置任何模式。
            val compression = (-strain).coerceAtLeast(0f) / 0.08f
            val deformationSpeed = extensionSpeed / 0.8f
            val bending = shear / 0.15f
            val landingResponse = 1f / (1f + v * v * 0.6f +
                compression * compression + deformationSpeed * deformationSpeed +
                bending * bending)
            val landingForce = (fall * fall * 8f * landing).coerceAtMost(90f) *
                landingResponse
            val extensionForce = -(v * v * 2.6f).coerceAtMost(220f) -
                abs(wall) * 55f + landingForce
            // Bending is driven by a heavily filtered acceleration: raw frame derivatives
            // made the lens jitter on a tap switch, where velocity reverses every frame.
            // 轻微继承松手方向；只在落地受力时驱动，不引入新的帧间差分。
            val bendingForce = -a * 0.5f - wall * 80f + landingForce * direction * 0.25f
            // 快拖积累的压缩能先把当前窄形状弹开，再收回；暂时降低黏性，
            // 随恢复过程连续回到原阻尼。普通点击没有这份恢复量，效果不变。
            val extensionDamping = 16f - 10f * releaseRecovery
            val bendingDamping = 15f - 4f * releaseRecovery
            extensionSpeed = (extensionSpeed + dt *
                (extensionForce - 240f * sinh(2f * strain))) / (1f + extensionDamping * dt)
            shearSpeed = (shearSpeed + dt *
                (bendingForce - 200f * sinh(2f * shear))) / (1f + bendingDamping * dt)
            strain += extensionSpeed * dt
            shear += shearSpeed * dt
            releaseRecovery *= exp(-4f * dt)
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
