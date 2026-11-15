package com.lindicra.cms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lindicra.cms.ui.theme.AccentBlue
import com.lindicra.cms.ui.theme.HarmonySansSC
import com.lindicra.cms.ui.theme.PrimaryText
import com.lindicra.cms.ui.theme.SecondaryText
import com.lindicra.cms.ui.theme.TertiaryFill
import com.lindicra.cms.ui.theme.TertiaryText

// ─────────────────────────────── 文本 ───────────────────────────────

/** 文本字号阶梯：Auto 使用调用方传入的字号，其余预设覆盖字号。 */
enum class CMSTextPreset(val fontSize: TextUnit?) {
    Auto(null),
    Caption(12.sp),
    Body(15.sp),
    Title(20.sp)
}

/** 标签样式：描边或实心。 */
enum class CMSTagStyle { Outline, Filled }

/**
 * 基础文本：统一字体、字号阶梯、字重、颜色、行数与溢出策略。
 *
 * 字号使用 sp，默认跟随系统字体缩放；`respectFontScale = false` 时抵消系统缩放，
 * 用于需要像素级稳定的排版场景。
 */
@Composable
fun CMSText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PrimaryText,
    fontSize: TextUnit = 15.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign = TextAlign.Start,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    style: TextStyle? = null,
    preset: CMSTextPreset = CMSTextPreset.Auto,
    respectFontScale: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    minLines: Int = 1,
    softWrap: Boolean = true
) {
    val density = LocalDensity.current
    val requested = preset.fontSize ?: fontSize
    // sp 本身已经跟随系统 fontScale；关闭跟随时反向抵消，保证实际像素不随系统变化。
    val resolved = if (respectFontScale || !requested.isSp) requested
    else (requested.value / density.fontScale.coerceAtLeast(0.01f)).sp
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = resolved,
        fontWeight = fontWeight,
        maxLines = maxLines,
        minLines = minLines,
        softWrap = softWrap,
        overflow = overflow,
        textAlign = textAlign,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight,
        fontFamily = HarmonySansSC,
        style = style ?: CMSNoFontPadding
    )
}

/** 次要说明文本。 */
@Composable
fun CMSLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SecondaryText,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Normal
) = CMSText(text, modifier, color, fontSize, fontWeight, maxLines = 2)

/**
 * 标签文本：小圆角胶囊，适合状态、分类等短文案。
 *
 * `style` 控制描边 / 实心，圆角与内边距可调。
 */
@Composable
fun CMSTag(
    text: String,
    modifier: Modifier = Modifier,
    style: CMSTagStyle = CMSTagStyle.Outline,
    tint: Color = AccentBlue,
    fontSize: TextUnit = 11.sp,
    cornerRadius: Dp = 6.dp,
    horizontalPadding: Dp = 7.dp,
    verticalPadding: Dp = 3.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .clip(shape)
            .then(
                when (style) {
                    CMSTagStyle.Filled -> Modifier.background(tint.copy(alpha = 0.16f))
                    CMSTagStyle.Outline -> Modifier.border(1.dp, tint.copy(alpha = 0.55f), shape)
                }
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        CMSText(
            text,
            color = tint,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 链接文字：点击回调 + 可选下划线，支持行数截断。 */
@Composable
fun CMSLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AccentBlue,
    fontSize: TextUnit = 15.sp,
    underline: Boolean = false,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    Text(
        text = text,
        modifier = modifier.clickable(
            interactionSource = null,
            indication = null,
            onClick = onClick
        ),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = HarmonySansSC,
        maxLines = maxLines,
        overflow = overflow,
        textDecoration = if (underline) TextDecoration.Underline else null,
        style = CMSNoFontPadding
    )
}

// ─────────────────────────────── 布局 ───────────────────────────────

/** 排列方向。 */
enum class CMSAxis { Vertical, Horizontal, Stack }

private fun Modifier.cmsSizeBounds(
    minWidth: Dp,
    maxWidth: Dp,
    minHeight: Dp,
    maxHeight: Dp
): Modifier = this
    .then(Modifier.widthIn(min = minWidth, max = maxWidth))
    .then(Modifier.heightIn(min = minHeight, max = maxHeight))

/** 叠放容器：后声明的内容在上层。 */
@Composable
fun CMSZStack(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopStart,
    minWidth: Dp = Dp.Unspecified,
    maxWidth: Dp = Dp.Unspecified,
    minHeight: Dp = Dp.Unspecified,
    maxHeight: Dp = Dp.Unspecified,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) = Box(
    modifier.cmsSizeBounds(minWidth, maxWidth, minHeight, maxHeight),
    contentAlignment = alignment,
    content = content
)

/** 纵向线性布局：间距、横向对齐、排列方式与尺寸边界都可透传。 */
@Composable
fun CMSVStack(
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(spacing),
    minWidth: Dp = Dp.Unspecified,
    maxWidth: Dp = Dp.Unspecified,
    minHeight: Dp = Dp.Unspecified,
    maxHeight: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) = Column(
    modifier.cmsSizeBounds(minWidth, maxWidth, minHeight, maxHeight),
    verticalArrangement = verticalArrangement,
    horizontalAlignment = horizontalAlignment,
    content = content
)

/** 横向线性布局：间距、纵向对齐、排列方式与尺寸边界都可透传。 */
@Composable
fun CMSHStack(
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(spacing),
    minWidth: Dp = Dp.Unspecified,
    maxWidth: Dp = Dp.Unspecified,
    minHeight: Dp = Dp.Unspecified,
    maxHeight: Dp = Dp.Unspecified,
    content: @Composable RowScope.() -> Unit
) = Row(
    modifier.cmsSizeBounds(minWidth, maxWidth, minHeight, maxHeight),
    horizontalArrangement = horizontalArrangement,
    verticalAlignment = verticalAlignment,
    content = content
)

/**
 * 分组容器：只负责间距与对齐（不画卡片）。
 * 需要卡片外观时用 [CMSSection]。
 */
@Composable
fun CMSGroup(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    minWidth: Dp = Dp.Unspecified,
    maxWidth: Dp = Dp.Unspecified,
    minHeight: Dp = Dp.Unspecified,
    maxHeight: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) = CMSVStack(
    modifier.fillMaxWidth(),
    spacing = spacing,
    horizontalAlignment = horizontalAlignment,
    minWidth = minWidth,
    maxWidth = maxWidth,
    minHeight = minHeight,
    maxHeight = maxHeight,
    content = content
)

/** 固定宽度的水平间隔。 */
@Composable
fun CMSHSpacer(width: Dp) = Box(Modifier.width(width))

/** 固定高度的垂直间隔。 */
@Composable
fun CMSVSpacer(height: Dp) = Box(Modifier.padding(top = height))

/** 可伸缩间隔，用于把两侧内容推到两端。 */
@Composable
fun CMSFlexSpacer(modifier: Modifier = Modifier) = Box(modifier)
