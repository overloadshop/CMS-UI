package com.lindicra.cms.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════
// CLEAR Design 色板（1.1.0）
// 与鸿蒙版共用同一套 token；切换主题时宿主调用 applyDarkPalette /
// applyLightPalette，Compose 读取处会自动重组。
// ═══════════════════════════════════════════════════════════════════

var PageBackground by mutableStateOf(Color(0xFF111318))   // pageBackground，页面根背景
var CardBackground by mutableStateOf(Color(0xFF1B1F27))   // cardBackground，卡片/容器背景
var SecondaryCard by mutableStateOf(Color(0xFF232833))    // secondaryCardBackground，次级卡片背景
var TertiaryFill by mutableStateOf(Color(0xFF2A303B))     // tertiaryFill，输入框/三级填充
var ImagePlaceholder by mutableStateOf(Color(0xFF2B313C)) // imagePlaceholder，图片/头像占位
var DividerColor by mutableStateOf(Color(0x14FFFFFF))     // divider，rgba(255,255,255,0.08)

var AccentBlue by mutableStateOf(Color(0xFF3F78D1))       // 软件主题色（浅色/深色共用）
var LinkBlue by mutableStateOf(Color(0xFF1E6FBF))         // 链接色

var PrimaryText by mutableStateOf(Color(0xFFF3F5F7))      // primaryText，标题/正文
var SecondaryText by mutableStateOf(Color(0xFFB7BFCC))    // secondaryText，描述/辅助信息
var TertiaryText by mutableStateOf(Color(0xFF8C95A3))     // tertiaryText，Section 标题/占位/时间戳

/** 深色主题色板 */
fun applyDarkPalette() {
    PageBackground = Color(0xFF111318)
    CardBackground = Color(0xFF1B1F27)
    SecondaryCard = Color(0xFF232833)
    TertiaryFill = Color(0xFF2A303B)
    ImagePlaceholder = Color(0xFF2B313C)
    DividerColor = Color(0x14FFFFFF)
    AccentBlue = Color(0xFF5F9BFF)
    LinkBlue = Color(0xFF78AFFF)
    PrimaryText = Color(0xFFF3F5F7)
    SecondaryText = Color(0xFFB7BFCC)
    TertiaryText = Color(0xFF8C95A3)
}

/** 浅色主题色板 */
fun applyLightPalette() {
    PageBackground = Color(0xFFF5F6F8)
    CardBackground = Color(0xFFFFFFFF)
    SecondaryCard = Color(0xFFE9ECF1)
    TertiaryFill = Color(0xFFE1E5EB)
    ImagePlaceholder = Color(0xFFD9DEE6)
    DividerColor = Color(0x14000000)
    AccentBlue = Color(0xFF3F78D1)
    LinkBlue = Color(0xFF1E6FBF)
    PrimaryText = Color(0xFF171A20)
    SecondaryText = Color(0xFF596170)
    TertiaryText = Color(0xFF737B89)
}

// ═══════════════════════════════════════════════════════════════════
// 1.0 兼容别名（旧组件继续可用，新代码请使用上面的 token）
// ═══════════════════════════════════════════════════════════════════

/** @deprecated 旧版强调色（红），新代码请用 [AccentBlue] */
@Deprecated("使用 AccentBlue")
val Accent = Color(0xFFFF5B68)

/** @deprecated 使用 [SecondaryCard] */
@Deprecated("使用 SecondaryCard")
val SecondaryCardBackground get() = SecondaryCard

/** @deprecated 使用 [DividerColor] */
@Deprecated("使用 DividerColor")
val Divider get() = DividerColor

val NavBackground = Color(0xCC151922)
val NavDivider = Color(0x0AFFFFFF)

// 浅色常量：旧组件（GlassTopBar 等）在浅色模式下直接引用
val LightPageBackground = Color(0xFFF0F0F5)
val LightCardBackground = Color(0xFFF7F7FB)
val LightSecondaryCardBackground = Color(0xFFF2F2F7)
val LightTertiaryFill = Color(0xFFECECF2)
val LightImagePlaceholder = Color(0xFFDDDDDD)
val LightNavBackground = Color(0xFFF0F0F5)
val LightPrimaryText = Color(0xFF1A1A1E)
val LightSecondaryText = Color(0x993C3C43)
val LightTertiaryText = Color(0x4D3C3C43)
val LightDivider = Color(0x263C3C43)
val LightNavDivider = Color(0x143C3C43)
