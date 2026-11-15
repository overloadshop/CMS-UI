package com.lindicra.cms

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lindicra.cms.ui.theme.GlassMaterial
import com.lindicra.cms.ui.theme.LocalGlassMaterial

/** 菜单项（声明式 命名）。 */
typealias MenuItem = CMSMenuItem

/** Tab 栏的位置：底部或顶部。 */
enum class CMSTabPlacement { Bottom, Top }

/**
 * Tab 栏形态。`Auto` 按窗口宽度自适应（≥600dp 用侧栏，否则底栏），
 * 其余值用于宿主强制指定，对应 系统的 `tabViewStyle`。
 */
enum class CMSTabForm { Auto, Edge, Floating, Rail }

/**
 * 全局默认配置。组件参数为 `null` 时回落到这里，所以宿主可以一次性统一浅色/深色、
 * 强调色、玻璃材质与触感，单个组件仍然可以显式覆盖。
 */
@Immutable
data class CMSSettings(
    /** `null` 表示跟随系统。 */
    val darkTheme: Boolean? = null,
    val accentColor: Color? = null,
    val glassMaterial: GlassMaterial? = null,
    val hapticsEnabled: Boolean? = null,
    val tabPlacement: CMSTabPlacement = CMSTabPlacement.Bottom,
    val tabForm: CMSTabForm = CMSTabForm.Auto
)

val LocalCMSSettings = staticCompositionLocalOf { CMSSettings() }

/** 读取全局默认值的便捷入口；全部在组合期解析，组件里直接用即可。 */
object CMSDefaults {
    @Composable fun darkTheme(): Boolean =
        LocalCMSSettings.current.darkTheme ?: isSystemInDarkTheme()

    @Composable fun accentColor(): Color? = LocalCMSSettings.current.accentColor

    @Composable fun glassMaterial(): GlassMaterial =
        LocalCMSSettings.current.glassMaterial ?: LocalGlassMaterial.current

    @Composable fun hapticsEnabled(): Boolean =
        LocalCMSSettings.current.hapticsEnabled ?: true

    @Composable fun tabPlacement(): CMSTabPlacement = LocalCMSSettings.current.tabPlacement

    @Composable fun tabForm(): CMSTabForm = LocalCMSSettings.current.tabForm
}
