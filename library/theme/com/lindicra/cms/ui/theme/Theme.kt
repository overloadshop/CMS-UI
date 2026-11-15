package com.lindicra.cms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import dev.chrisbanes.haze.HazeState

/** 当前是否深色；由 [CMSTheme] 提供，玻璃组件据此调整高光与色调。 */
val LocalClearIsDark = staticCompositionLocalOf { true }
/** A theme's accent stays local to its composition, including independent previews. */
val LocalCMSAccentColor = staticCompositionLocalOf { Color(0xFF3F78D1) }
private val LocalCMSBaseDensity = staticCompositionLocalOf<androidx.compose.ui.unit.Density?> { null }

private fun cmsDarkColorScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = PrimaryText,
    secondary = SecondaryText,
    tertiary = TertiaryText,
    background = PageBackground,
    surface = CardBackground,
    onBackground = PrimaryText,
    onSurface = PrimaryText,
    outline = DividerColor
)

private fun cmsLightColorScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = SecondaryText,
    tertiary = TertiaryText,
    background = PageBackground,
    surface = CardBackground,
    onBackground = PrimaryText,
    onSurface = PrimaryText,
    outline = DividerColor
)

/**
 * CMS 主题入口（1.1.0）
 *
 * 一次性提供色板、玻璃材质、内容采样 [HazeState]、震动与返回历史。
 * 宿主已有自己的主题时，也可以只在自己的
 * `CompositionLocalProvider` 里提供这些 Local，而不调用本函数。
 */
@Composable
fun CMSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    glassMaterial: GlassMaterial = GlassMaterial(),
    hazState: HazeState = remember { HazeState() },
    hapticsEnabled: Boolean = true,
    haptics: CMSHapticsProvider? = LocalCMSHaptics.current,
    backHistory: CMSBackHistory? = LocalCMSBackHistory.current,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    if (darkTheme) applyDarkPalette() else applyLightPalette()
    val accent = accentColor ?: if (darkTheme) Color(0xFF5F9BFF) else Color(0xFF3F78D1)
    val colorScheme = if (darkTheme) cmsDarkColorScheme(accent) else cmsLightColorScheme(accent)
    val baseDensity = LocalCMSBaseDensity.current ?: LocalDensity.current
    val visualDensity = remember(baseDensity, darkTheme) {
        if (darkTheme) baseDensity else Density(baseDensity.density, baseDensity.fontScale * 1.02f)
    }
    val typography = if (darkTheme) HarmonySansSCTypography else HarmonySansSCTypography.copy(
        bodyLarge = HarmonySansSCTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        bodyMedium = HarmonySansSCTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        bodySmall = HarmonySansSCTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
        labelLarge = HarmonySansSCTypography.labelLarge.copy(fontWeight = FontWeight.Medium),
        labelMedium = HarmonySansSCTypography.labelMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = HarmonySansSCTypography.labelSmall.copy(fontWeight = FontWeight.Medium))
    CompositionLocalProvider(
        LocalCMSBaseDensity provides baseDensity,
        LocalDensity provides visualDensity,
        LocalClearIsDark provides darkTheme,
        LocalCMSAccentColor provides accent,
        LocalGlassMaterial provides glassMaterial,
        LocalGlassHazeState provides hazState,
        LocalCMSHapticsEnabled provides hapticsEnabled,
        LocalCMSHaptics provides haptics,
        LocalCMSBackHistory provides backHistory
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

/** 1.0 兼容入口；新代码建议使用 [CMSTheme]。 */
@Composable
fun CLEARmusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CMSTheme(darkTheme = darkTheme, content = content)
}
