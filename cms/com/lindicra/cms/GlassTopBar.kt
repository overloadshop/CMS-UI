package com.lindicra.cms

import android.os.Build
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import com.adamglin.composecontinuousroundedcornershape.ContinuousRoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import com.lindicra.cms.ui.theme.HarmonySansSC
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.platform.ComposeView
import com.qmdeve.liquidglass.widget.LiquidGlassView
import com.lindicra.cms.ui.theme.DividerColor
import com.lindicra.cms.ui.theme.LightNavBackground
import com.lindicra.cms.ui.theme.LightNavDivider
import com.lindicra.cms.ui.theme.LightPageBackground
import com.lindicra.cms.ui.theme.LightPrimaryText
import com.lindicra.cms.ui.theme.LocalGlassMaterial
import com.lindicra.cms.ui.theme.NavBackground
import com.lindicra.cms.ui.theme.NavDivider
import com.lindicra.cms.ui.theme.PageBackground
import com.lindicra.cms.ui.theme.PrimaryText

// 鸿蒙主Tab顶栏参数: left=24, right=24, top=safeAreaTop+12, bottom=12
private val CM_MAIN_TOP_BAR_PADDING_TOP = 12.dp
private val CM_MAIN_TOP_BAR_PADDING_BOTTOM = 12.dp
private val CM_MAIN_TOP_BAR_PADDING_HORIZONTAL = 24.dp

// 顶栏与内容之间的标准间距
private val CM_TOP_BAR_STANDARD_GAP = 8.dp

// 主Tab顶栏内容区总预留: topPad + 28sp文字 + bottomPad + 标准间距
val CM_MAIN_TOP_BAR_RESERVE = CM_MAIN_TOP_BAR_PADDING_TOP + 28.dp + CM_MAIN_TOP_BAR_PADDING_BOTTOM + CM_TOP_BAR_STANDARD_GAP
// 二级页顶栏：51dp 主体 + 1dp 分隔线。内容自身再留 12dp 间距。
val CM_SECONDARY_TOP_BAR_RESERVE = 52.dp

/**
 * 顶栏玻璃样式 — 支持深浅色分别配置。
 * @param background      背景色（纯色基底，位于 haze 下层）
 * @param overlayTint     haze 叠加染色（影响玻璃质感色相与透亮度）
 * @param blurRadius      模糊半径
 * @param borderColor     底部分割线颜色（仅二级页使用）
 */
data class CMSGlassTopBarStyle(
    val background: Color,
    val materialOverlay: Color,
    val overlayTint: Color,
    val blurRadius: Dp = 22.dp,
    val highlightColor: Color = Color.Transparent,
    val borderColor: Color = Color.Transparent
) {
    companion object {
        /**
         * 暗色默认 — 基底 0xF0 (94% 不透明)。
         * haze 仅作为表层装饰效果，不依赖背后内容实时模糊。
         */
        val dark = CMSGlassTopBarStyle(
            background = Color(0xCC141923),
            materialOverlay = Color(0xD41A202A),
            overlayTint = Color.White.copy(alpha = 0.10f),
            blurRadius = 18.dp,
            highlightColor = Color.White.copy(alpha = 0.08f),
            borderColor = Color.White.copy(alpha = 0.05f)
        )
        /** 亮色默认 */
        val light = CMSGlassTopBarStyle(
            background = Color(0xD9F2F4F7),
            materialOverlay = Color.White.copy(alpha = 0.78f),
            overlayTint = Color.White.copy(alpha = 0.12f),
            blurRadius = 18.dp,
            highlightColor = Color.White.copy(alpha = 0.24f),
            borderColor = Color(0x143C3C43)
        )
        /** 完全透明毛玻璃（仅模糊无叠色） */
        val clear = CMSGlassTopBarStyle(
            background = Color.Transparent,
            materialOverlay = Color.Transparent,
            overlayTint = Color.Transparent,
            blurRadius = 22.dp
        )
    }
}

fun CMSGlassTopBarPadding(topInset: Dp): Dp = topInset

/**
 * 毛玻璃页面容器 — 统一处理 hazeSource + 滚动内容 + 顶栏叠层 + 底部避让。
 * 内容从屏幕顶部(0)开始，完整延伸到顶栏背后为 Haze 提供真实模糊素材；
 * 列表内通过前后 Spacer 自适应避让顶栏和底部播放器。
 *
 * @param topInset 状态栏高度
 * @param modifier 外层 modifier
 * @param topBarReserve 列表内顶部预留高度（顶栏内容区 + 标准间距），默认主Tab值
 * @param bottomReserve 列表内底部预留高度（迷你播放器 / 导航栏），默认 0
 * @param axisDragBridge 父级 Pager 的纵向接管桥，一页一桥，直接注册到内部滚动
 * @param touchPriority 内部交互策略；与父级 Pager 共用同一触控优先级作用域
 * @param topBar 顶栏 Composable
 * @param content 可滚动内容
 */
@Composable
fun CMSGlassPage(
    topInset: Dp,
    modifier: Modifier = Modifier,
    glassEnabled: Boolean = true,
    topBarReserve: Dp = CM_MAIN_TOP_BAR_RESERVE,
    bottomReserve: Dp = 0.dp,
    hazeState: HazeState? = if (LocalCMSPageBridge.current != null)
        com.lindicra.cms.ui.theme.LocalGlassHazeState.current else null,
    maxContentWidth: Dp = Dp.Unspecified,
    axisDragBridge: CMSVerticalDragBridge? = LocalCMSPageBridge.current,
    touchPriority: CMSTouchPriority = LocalCMSTouchPriority.current,
    springOverscroll: Boolean = false,
    showScrollbar: Boolean = false,
    sampleGlassContent: Boolean = !LocalCMSPagerSampling.current,
    topBar: @Composable BoxScope.(hazeState: HazeState) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = com.lindicra.cms.ui.theme.LocalClearIsDark.current
    val bg = if (isDark) PageBackground else LightPageBackground
    val resolvedHaze = hazeState ?: remember { HazeState() }
    // 平板内容限宽：平板宽屏下内容区收窄居中，避免被拉满全宽（二级页面左右边距过大即源于此）
    val effectiveMaxWidth = if (maxContentWidth != Dp.Unspecified) {
        maxContentWidth
    } else {
        tabletContentMaxWidth()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
        ) {
            // 滚动容器自身提供唯一采样源，使用页面实际的 HazeState。
            CompositionLocalProvider(
                LocalCMSTouchPriority provides touchPriority,
                com.lindicra.cms.ui.theme.LocalGlassHazeState provides
                    if (glassEnabled) resolvedHaze else null
            ) {
                // 滚动内容从屏幕顶部(0)开始，完整覆盖到顶栏背后
                CMSScrollView(
                    modifier = Modifier.fillMaxSize(),
                    axisDragBridge = axisDragBridge,
                    springOverscroll = springOverscroll,
                    showScrollbar = showScrollbar,
                    sampleGlassContent = sampleGlassContent
                ) {
                    // 顶部避让：状态栏 + 顶栏高度 + 标准间距
                    Spacer(modifier = Modifier.height(topInset + topBarReserve))
                    if (effectiveMaxWidth != Dp.Unspecified) {
                        // 平板：内容限宽并水平居中
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = effectiveMaxWidth)
                                    .align(Alignment.TopCenter)
                            ) {
                                content()
                            }
                        }
                    } else {
                        content()
                    }
                    // 底部避让：迷你播放器 / 导航栏
                    if (bottomReserve > 0.dp) {
                        Spacer(modifier = Modifier.height(bottomReserve))
                    }
                }
            }
        }
        topBar(resolvedHaze)
    }
}

@Composable
fun CMSMainGlassTopBar(
    title: String,
    topInset: Dp,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    glassEnabled: Boolean = true,
    style: CMSGlassTopBarStyle? = null,
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    val isDark = com.lindicra.cms.ui.theme.LocalClearIsDark.current
    val textColor = PrimaryText
    val resolvedStyle = style ?: if (isDark) CMSGlassTopBarStyle.dark else CMSGlassTopBarStyle.light

    CMSGlassTopBarShell(
        topInset = topInset,
        hazeState = hazeState,
        glassEnabled = glassEnabled,
        style = resolvedStyle,
        horizontalPadding = CM_MAIN_TOP_BAR_PADDING_HORIZONTAL,
        topPaddingOffset = CM_MAIN_TOP_BAR_PADDING_TOP,
        bottomPadding = CM_MAIN_TOP_BAR_PADDING_BOTTOM,
        showBottomBorder = false,
        modifier = modifier
    ) {
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = HarmonySansSC,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        trailingContent()
    }
}

@Composable
fun CMSecondaryGlassTopBar(
    title: String,
    topInset: Dp,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    glassEnabled: Boolean = true,
    style: CMSGlassTopBarStyle? = null,
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    trailingContent: @Composable BoxScope.() -> Unit = {}
) {
    val textColor = PrimaryText
    val resolvedStyle = style ?: CMSGlassTopBarStyle(
        background = Color.Transparent, materialOverlay = Color.Transparent,
        overlayTint = PageBackground.copy(alpha = 0.68f),
        blurRadius = 20.dp, borderColor = com.lindicra.cms.ui.theme.DividerColor)
    val context = LocalContext.current
    val backAction: () -> Unit = onBack ?: {
        (context as? ComponentActivity)?.finish()
        Unit
    }

    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(topInset).background(PageBackground))
        Box(Modifier.fillMaxWidth().height(51.dp).offset(y = (-2).dp).then(
            if (glassEnabled) Modifier.background(resolvedStyle.background).hazeEffect(hazeState) {
                backgroundColor = PageBackground
                blurRadius = resolvedStyle.blurRadius
                tints = listOf(HazeTint(resolvedStyle.overlayTint))
                fallbackTint = HazeTint(PageBackground.copy(alpha = 0.94f))
            } else Modifier.background(PageBackground.copy(alpha = 0.96f))
        )) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
        CMSGlassBackButton(
            onClick = backAction,
            iconColor = textColor
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = HarmonySansSC,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                maxLines = 1,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .then(
                        if (onTitleClick != null) {
                            Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onTitleClick() }
                        } else {
                            Modifier
                        }
                    )
            )
        }
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            trailingContent()
        }
        }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(resolvedStyle.borderColor))
    }
}

/**
 * 二级页顶栏：整条 51dp 覆盖（含实色底），左右 12dp、返回按钮 44dp、
 * 标题 18sp 居中、底部全宽 1dp 分隔线。
 *
 * 玻璃连同状态栏一起覆盖：[topInset] 为状态栏高度，顶栏本体从屏幕顶部
 * 一直铺到 51dp 主体结束，状态栏区域同样获得实色底 + 模糊 + 染色。
 * 调用方让滚动内容延伸到屏幕顶部（内容首项预留 `topInset + CM_SECONDARY_TOP_BAR_RESERVE`），
 * 内容即可滚入顶栏玻璃后方参与采样。
 *
 * @param topInset 状态栏高度
 * @param hazeState 由同一页面滚动内容 `hazeSource` 提供的状态；为空时只使用实色底。
 */
@Composable
fun CMSSecondaryTopBar(
    title: String,
    topInset: Dp = 0.dp,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null
) {
    val glass = LocalGlassMaterial.current
    val context = LocalContext.current
    val backAction: () -> Unit = onBack ?: {
        (context as? ComponentActivity)?.finish()
        Unit
    }
    Column(modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + 51.dp)
                // 实色底保证整条顶栏（含状态栏）始终覆盖完整，模糊层只作为叠加质感。
                .background(PageBackground)
                .then(
                    if (glass.advanced && hazeState != null) {
                        Modifier.hazeEffect(hazeState) {
                            backgroundColor = PageBackground
                            blurRadius = 20.dp
                            tints = listOf(HazeTint(PageBackground.copy(alpha = 0.82f)))
                            fallbackTint = HazeTint(PageBackground.copy(alpha = 0.96f))
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInset, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CMSGlassBackButton(onClick = backAction)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = title,
                        color = PrimaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = HarmonySansSC,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        maxLines = 1,
                        modifier = Modifier
                            .then(
                                if (onTitleClick != null) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onTitleClick
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.size(40.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
    }
}

@Composable
private fun CMSGlassTopBarShell(
    topInset: Dp,
    hazeState: HazeState,
    glassEnabled: Boolean,
    style: CMSGlassTopBarStyle,
    horizontalPadding: Dp,
    topPaddingOffset: Dp,
    bottomPadding: Dp,
    showBottomBorder: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(style.background)
            .then(
                if (glassEnabled) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurRadius = style.blurRadius
                        tints = listOf(HazeTint(style.overlayTint))
                    }
                } else {
                    Modifier
                }
            )
            .drawWithContent {
                if (style.materialOverlay != Color.Transparent) {
                    drawRect(color = style.materialOverlay)
                }
                if (style.highlightColor != Color.Transparent) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                style.highlightColor,
                                style.highlightColor.copy(alpha = style.highlightColor.alpha * 0.35f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.62f
                        )
                    )
                }
                drawContent()
                if (showBottomBorder && style.borderColor != Color.Transparent) {
                    drawLine(
                        color = style.borderColor,
                        start = Offset(horizontalPadding.toPx(), size.height),
                        end = Offset(size.width - horizontalPadding.toPx(), size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = topInset + topPaddingOffset,
                bottom = bottomPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

// 1.1.0：返回按钮已迁移到 CMSGlassBackButton.kt（长按返回历史 + 独立 Popup 菜单）。
