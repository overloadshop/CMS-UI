package com.lindicra.cms.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * CMS 统一触感反馈（1.1.0）
 *
 * 组件内部只调用 [CMSHaptics.trigger]，不直接依赖宿主的震动实现。
 * 宿主可以：
 *  1. 直接使用默认实现（[CMSDefaultHapticsProvider]，与鸿蒙 HapticUtils 一致）；
 *  2. 通过 [CMSHaptics.provider] 全局替换（例如接入业务开关、统一音效）；
 *  3. 在 Compose 树内用 [LocalCMSHaptics] / [LocalCMSHapticsEnabled] 局部覆盖。
 */
enum class CMSHapticKind {
    /** 普通界面反馈（轻触），对应鸿蒙 EFFECT_TICK */
    Tick,

    /** 键盘按键反馈（略重），对应鸿蒙 EFFECT_CLICK */
    Click,

    /** 长按确认 */
    LongPress
}

/** 触感实现接口：宿主实现后交给 [CMSHaptics.provider]。 */
fun interface CMSHapticsProvider {
    fun perform(context: Context, kind: CMSHapticKind)
}

/** 默认实现：一次短震动，行为与主工程 Haptics 一致。 */
object CMSDefaultHapticsProvider : CMSHapticsProvider {

    override fun perform(context: Context, kind: CMSHapticKind) {
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(
                when (kind) {
                    CMSHapticKind.Tick, CMSHapticKind.LongPress -> VibrationEffect.EFFECT_TICK
                    CMSHapticKind.Click -> VibrationEffect.EFFECT_CLICK
                }
            )
        } else {
            when (kind) {
                CMSHapticKind.Tick, CMSHapticKind.LongPress ->
                    VibrationEffect.createOneShot(20, 150)
                CMSHapticKind.Click ->
                    VibrationEffect.createOneShot(25, 220)
            }
        }
        vibrate(context, effect)
    }

    private fun vibrate(context: Context, effect: VibrationEffect) {
        val app = context.applicationContext
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        runCatching { vibrator.vibrate(effect) }
    }
}

/** 全局触感入口：View 与 Composable 都通过这里触发。 */
object CMSHaptics {

    /** 宿主可替换为业务实现；默认使用系统预定义震动。 */
    @Volatile
    var provider: CMSHapticsProvider = CMSDefaultHapticsProvider

    /** 全局开关；宿主可绑定“设置 → 震动触感”。 */
    @Volatile
    var enabled: Boolean = true

    fun trigger(context: Context, kind: CMSHapticKind = CMSHapticKind.Tick) {
        if (!enabled) return
        runCatching { provider.perform(context, kind) }
    }
}

/** Compose 局部覆盖：null 表示使用 [CMSHaptics.provider]。 */
val LocalCMSHaptics = staticCompositionLocalOf<CMSHapticsProvider?> { null }

/** Compose 局部开关：与 [CMSHaptics.enabled] 同时为 true 才触发。 */
val LocalCMSHapticsEnabled = staticCompositionLocalOf { true }

/** 组件内部统一使用的触感调用器，自动套用局部覆盖与开关。 */
@Composable
fun rememberCMSHaptic(): (CMSHapticKind) -> Unit {
    val context = LocalContext.current
    val localProvider = LocalCMSHaptics.current
    val localEnabled = LocalCMSHapticsEnabled.current
    return remember(context, localProvider, localEnabled) {
        { kind ->
            if (localEnabled && CMSHaptics.enabled) {
                val provider = localProvider ?: CMSHaptics.provider
                runCatching { provider.perform(context, kind) }
            }
        }
    }
}
