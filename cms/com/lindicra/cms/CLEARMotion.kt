package com.lindicra.cms

import android.view.Choreographer
import kotlin.math.abs

/**
 * CLEAR Motion Pro — Android JNI 封装
 *
 * 所有物理动效通过 JNI 桥接直接调用原生 libCLEARMotionPro.so，
 * 不使用 Kotlin/Java 重实现。
 *
 * 集成方式：
 *   1. libCLEARMotionPro.so 放入 app/src/main/jniLibs/<abi>/
 *   2. clearmotion_jni （JNI 桥接库）由 CMake 自动编译
 */
object CLEARMotion {
    init {
        System.loadLibrary("clearmotion_jni")
    }

    // ===== PhysicsEngine =====

    external fun nativeEngineCreate(): Long
    external fun nativeEngineDestroy(engine: Long)
    external fun nativeEngineUpdate(engine: Long, dt: Double)
    external fun nativeEngineStart(engine: Long)
    external fun nativeEngineStop(engine: Long)

    // ===== ScrollSimulator（滚动模拟器） =====

    /** 创建滚动模拟器实例，返回 native 指针 (long) */
    external fun nativeScrollCreate(): Long

    /** 销毁滚动模拟器 */
    external fun nativeScrollDestroy(ptr: Long)

    /** 设置滚动边界 (minPos, maxPos) */
    external fun nativeScrollSetBounds(ptr: Long, minPos: Double, maxPos: Double)

    /** 触摸开始：传入当前滚动位置 */
    external fun nativeScrollStart(ptr: Long, position: Double)

    /** 触摸移动：传入位移增量 */
    external fun nativeScrollMove(ptr: Long, delta: Double)

    /** 触摸结束：传入松手速度 */
    external fun nativeScrollEnd(ptr: Long, velocity: Double)

    /** 驱动物理步进：传入 dt (秒) */
    external fun nativeScrollUpdate(ptr: Long, dt: Double)

    /** 获取当前滚动位置 */
    external fun nativeScrollGetPosition(ptr: Long): Double

    /** 获取当前速度 */
    external fun nativeScrollGetVelocity(ptr: Long): Double

    /** 是否正在拖拽中 */
    external fun nativeScrollIsDragging(ptr: Long): Boolean

    // ===== SpringAnimator（弹簧动画器） =====

    /** 创建弹簧动画器实例，返回 native 指针 (long) */
    external fun nativeSpringCreate(
        initialValue: Double,
        stiffness: Double,
        damping: Double,
        mass: Double,
        gravity: Double,
        roughness: Double,
        airResistance: Double
    ): Long

    /** 销毁弹簧动画器 */
    external fun nativeSpringDestroy(ptr: Long)

    /** 设置弹簧目标值 */
    external fun nativeSpringSetTarget(ptr: Long, target: Double)

    /** 驱动弹簧步进：传入 dt (秒) */
    external fun nativeSpringUpdate(ptr: Long, dt: Double)

    /** 获取当前弹簧值 */
    external fun nativeSpringGetValue(ptr: Long): Double

    /** 获取当前弹簧速度 */
    external fun nativeSpringGetVelocity(ptr: Long): Double

    /** 弹簧是否仍在运行 */
    external fun nativeSpringIsRunning(ptr: Long): Boolean
}

// ═══════════════════════════════════════════
// SpringDriver — 纯 Kotlin 弹簧动画驱动器
// ⚠ 临时保留，后续需迁移到 JNI 原生调用
// ═══════════════════════════════════════════
class SpringDriver(
    private val stiffness: Float = 200f,
    private val damping: Float = 26f,
    private val mass: Float = 1f,
    private val roughness: Float = 0.05f,
    private val airResistance: Float = 0.01f
) {
    private var value = 0f
    private var velocity = 0f
    private var target = 0f
    private var running = false

    var onUpdate: ((Float) -> Unit)? = null
    var onSettled: (() -> Unit)? = null

    private var lastFrameTimeNanos = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return

            val rawDt = if (lastFrameTimeNanos > 0L)
                (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
            else
                1f / 60f
            val dt = rawDt.coerceIn(0f, 0.05f)
            lastFrameTimeNanos = frameTimeNanos

            val springForce = -stiffness * (value - target)
            val dampingForce = -damping * velocity
            val airDrag = -airResistance * velocity * abs(velocity)

            val acceleration = (springForce + dampingForce + airDrag) / mass
            velocity += acceleration * dt
            velocity *= (1f - roughness * dt)
            value += velocity * dt

            if (abs(value - target) < 0.005f && abs(velocity) < 0.1f) {
                value = target
                velocity = 0f
                running = false
            }

            onUpdate?.invoke(value)

            if (running) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                onSettled?.invoke()
            }
        }
    }

    fun setTarget(t: Float) {
        target = t
        if (!running) {
            running = true
            lastFrameTimeNanos = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun getValue(): Float = value
    fun isRunning(): Boolean = running

    fun reset(value: Float = 0f) {
        this.value = value
        this.target = value
        this.velocity = 0f
        this.running = false
    }

    fun destroy() {
        running = false
        onUpdate = null
        onSettled = null
    }
}
