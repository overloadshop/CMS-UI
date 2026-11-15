package com.lindicra.cms.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * 液态玻璃材质档位（1.1.0）
 *
 * @param advanced 是否启用高级材质（折射、高光、背景模糊）
 * @param clarity 通透度：0 毛玻璃 / 1 舒适 / 2 通透
 */
data class GlassMaterial(val advanced: Boolean = true, val clarity: Int = 1) {
    val frosted get() = advanced && clarity == 0
    val optical get() = advanced && clarity != 0
    val tintMultiplier get() = if (clarity == 2) 0.45f else 1f
    val blurRadius get() = if (clarity == 2) 8f else 25f
}

/** 当前玻璃材质；宿主在主题层提供，未提供时使用默认档位。 */
val LocalGlassMaterial = staticCompositionLocalOf { GlassMaterial() }

/** 页面内容采样源状态；毛玻璃控件通过它模糊页面内容。 */
val LocalGlassHazeState = staticCompositionLocalOf<HazeState?> { null }
