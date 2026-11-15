package com.lindicra.cms

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import com.adamglin.composecontinuousroundedcornershape.ContinuousRoundedCornerShape
import com.lindicra.cms.ui.theme.AccentBlue
import com.lindicra.cms.ui.theme.CardBackground
import com.lindicra.cms.ui.theme.DividerColor
import com.lindicra.cms.ui.theme.HarmonySansSC
import com.lindicra.cms.ui.theme.PrimaryText
import com.lindicra.cms.ui.theme.SecondaryCard
import com.lindicra.cms.ui.theme.SecondaryText
import com.lindicra.cms.ui.theme.TertiaryFill
import com.lindicra.cms.ui.theme.TertiaryText

/**
 * 设置列表 / List 的原子组件：
 *
 * - [CMSList]：分组容器（组间隙、留白）
 * - [CMSListSection]：卡片分组（圆角、容器色、内边距、行间距）
 * - [CMSListRow]：通用行（前导 / 内容 / 尾部三段，可完全自定义）
 * - [CMSDivider]：独立分割线（缩进、粗细、颜色），任何地方可用
 * - [CMSSectionTitle]：分组标题（字号、颜色、内边距）
 */

/** 关掉 Android 默认字体上下留白，否则双行列表行距会明显偏大。 */
internal val CMSNoFontPadding = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

private val CMSPressEase = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/** 列表默认度量，可在各组件里逐项覆盖。 */
object CMSListDefaults {
    val groupSpacing = 14.dp
    val sectionPadding = 18.dp
    val sectionCorner = 20.dp
    val rowMinHeight = 48.dp
    val rowPadding = 10.dp
    val titleSize = 15.sp
    val descSize = 11.sp
    val sectionTitleSize = 13.sp
    val sectionTitlePadding = PaddingValues(start = 2.dp, top = 2.dp, bottom = 0.dp)
}

/** 可选的行间距覆盖，由分组提供给内部行。 */
val LocalListRowPadding = compositionLocalOf { CMSListDefaults.rowPadding }

/** 行内容的水平内缩；背景仍铺满卡片，对应 系统的 listRowInsets + listRowBackground。 */
val LocalListRowHorizontalInset = compositionLocalOf { 16.dp }
private val LocalListRowEndInset = compositionLocalOf<Dp?> { null }
private data class CMSRowPlacement(
    val coordinates: LayoutCoordinates,
    val position: Offset,
    val highlight: State<Color>,
    val shape: State<Shape>
)

private val LocalSectionRows = compositionLocalOf<MutableMap<Any, CMSRowPlacement>?> { null }

/** 在卡片坐标中画底色，行的内容缩进和旧容器 padding 不再缩小按压范围。 */
private fun DrawScope.drawRowHighlights(
    rows: Map<Any, CMSRowPlacement>,
    origin: LayoutCoordinates?,
    topPadding: Float,
    bottomPadding: Float
) {
    if (origin == null || !origin.isAttached) return
    rows.values.forEach { row ->
        if (!row.coordinates.isAttached) return@forEach
        val color = row.highlight.value
        if (color.alpha == 0f) return@forEach
        val position = origin.localPositionOf(row.coordinates, Offset.Zero)
        val rowBottom = position.y + row.coordinates.size.height
        // 仅边缘行延伸到卡片边缘；前面/后面存在其他内容时不覆盖它们。
        val top = if (kotlin.math.abs(position.y - topPadding) <= 1f) 0f else position.y
        val bottom = if (kotlin.math.abs(rowBottom - (size.height - bottomPadding)) <= 1f)
            size.height else rowBottom
        if (bottom <= top) return@forEach
        withTransform({ translate(top = top) }) {
            drawOutline(
                row.shape.value.createOutline(Size(size.width, bottom - top), layoutDirection, this),
                color
            )
        }
    }
}

// ───────────────────────────── 组容器 ─────────────────────────────

/** 分组容器：只负责组与组之间的间隙，本身不画背景。 */
@Composable
fun CMSList(
    modifier: Modifier = Modifier,
    spacing: Dp = CMSListDefaults.groupSpacing,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

/**
 * 卡片分组。
 *
 * @param contentPadding 水平值下发给行内容，垂直值是卡片上下留白；
 * @param spacing 行与行之间的额外间距，默认 0（由分割线提供节奏）；
 * @param containerColor 卡片底色；
 * @param cornerRadius 连续圆角半径；
 * @param shape 需要自定义形状时优先于 [cornerRadius]。
 */
@Composable
fun CMSListSection(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = CMSListDefaults.sectionPadding),
    spacing: Dp = 0.dp,
    containerColor: Color = CardBackground,
    cornerRadius: Dp = CMSListDefaults.sectionCorner,
    shape: Shape = ContinuousRoundedCornerShape(cornerRadius),
    rowPadding: Dp = CMSListDefaults.rowPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    CMSSection(
        modifier = modifier,
        contentPadding = contentPadding,
        containerColor = containerColor,
        cornerRadius = cornerRadius,
        shape = shape,
        showSeparators = false,
        rowSpacing = spacing
    ) {
        CompositionLocalProvider(LocalListRowPadding provides rowPadding) {
            content()
        }
    }
}

/** 分组标题。 */
@Composable
fun CMSSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = TertiaryText,
    fontSize: TextUnit = CMSListDefaults.sectionTitleSize,
    fontWeight: FontWeight = FontWeight.Medium,
    padding: PaddingValues = CMSListDefaults.sectionTitlePadding
) {
    Text(
        text = title,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = HarmonySansSC,
        style = CMSNoFontPadding,
        modifier = modifier.padding(padding)
    )
}

// ───────────────────────────── 分割线 ─────────────────────────────

/**
 * 独立分割线，可放在列表里，也可放在任意布局中。
 *
 * @param startIndent 左缩进，对齐行内容用；
 * @param endIndent 右缩进。
 */
@Composable
fun CMSDivider(
    modifier: Modifier = Modifier,
    color: Color = DividerColor,
    thickness: Dp = 1.dp,
    startIndent: Dp = 0.dp,
    endIndent: Dp = 0.dp
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent, end = endIndent)
            .height(thickness)
            .background(color)
    )
}

/** 带上下留白的分割线，用于直接分隔两条行。 */
@Composable
fun CMSRowDivider(
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 0.dp,
    color: Color = DividerColor,
    thickness: Dp = 1.dp
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding)
            .height(thickness)
            .background(color)
    )
}

// ───────────────────────────── 行 ─────────────────────────────

/**
 * 通用行：把一行拆成前导 / 内容 / 尾部三段，内容完全自定义。
 *
 * ```kotlin
 * CMSListRow(onClick = {}, trailing = { Text("任意内容") }) {
 *     CMSListRowText("自定义标题", "说明")
 * }
 * ```
 */
@Composable
fun CMSListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    minHeight: Dp = CMSListDefaults.rowMinHeight,
    contentPadding: PaddingValues = PaddingValues(vertical = LocalListRowPadding.current),
    horizontalInset: Dp? = null,
    highlightShape: Shape = RoundedCornerShape(0.dp),
    pressedHighlight: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    pressedColor: Color = Color(0x1F8E8E93),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val ownInteraction = remember { MutableInteractionSource() }
    val interaction = interactionSource ?: ownInteraction
    val pressed by interaction.collectIsPressedAsState()
    val highlight = animateColorAsState(
        targetValue = if (enabled && pressed && pressedHighlight) pressedColor else Color.Transparent,
        // 按下立即压暗；松开才用缓动淡出
        animationSpec = if (pressed) tween(0) else tween(160, easing = CMSPressEase),
        label = "cmsListRowBg"
    )
    val rows = LocalSectionRows.current
    val currentShape = rememberUpdatedState(highlightShape)
    val rowKey = remember { Any() }
    DisposableEffect(rows, rowKey) { onDispose { rows?.remove(rowKey) } }
    val startInset = horizontalInset ?: LocalListRowHorizontalInset.current
    val endInset = horizontalInset ?: LocalListRowEndInset.current ?: startInset
        Row(
            modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    rows?.set(rowKey, CMSRowPlacement(it, it.positionInParent(), highlight, currentShape))
                }
                .clip(highlightShape)
                .background(if (rows == null) highlight.value else Color.Transparent)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            enabled = enabled,
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick
                        )
                    } else Modifier
                )
                .defaultMinSize(minHeight = minHeight)
                .padding(start = startInset, end = endInset)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { leading() }
                Spacer(Modifier.width(12.dp))
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = content)
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
            }
        }
}

/** 标题 + 说明的文本段，供自定义行内容使用。 */
@Composable
fun CMSListRowText(
    title: String,
    desc: String = "",
    titleColor: Color = PrimaryText,
    descColor: Color = SecondaryText,
    titleSize: TextUnit = CMSListDefaults.titleSize,
    descSize: TextUnit = CMSListDefaults.descSize,
    spacing: Dp = 4.dp
) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        Text(
            title,
            color = titleColor,
            fontSize = titleSize,
            fontWeight = FontWeight.Medium,
            fontFamily = HarmonySansSC,
            style = CMSNoFontPadding
        )
        if (desc.isNotEmpty()) {
            Text(
                desc,
                color = descColor,
                fontSize = descSize,
                fontFamily = HarmonySansSC,
                style = CMSNoFontPadding
            )
        }
    }
}

/**
 * 标题行：标题 + 说明 + 可选前导图标 + 可选箭头。
 * 内部使用 [CMSListRow]，所以按压反馈与自定义行一致。
 */
@Composable
fun CMSListRow(
    title: String,
    desc: String = "",
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    showChevron: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    CMSListRow(
        modifier = modifier,
        onClick = onClick,
        leading = leading,
        trailing = if (!showChevron && trailing == null) null else ({
            trailing?.invoke()
            if (showChevron) {
                if (trailing != null) Spacer(Modifier.width(6.dp))
                if (chevron != null) {
                    chevron()
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = TertiaryText,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        })
    ) {
        CMSListRowText(
            title = title,
            desc = desc,
            titleColor = if (danger) Color(0xFFFF6B6B) else PrimaryText
        )
    }
}

/** 开关行。 */
@Composable
fun CMSToggleRow(
    title: String,
    desc: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    CMSListRow(
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        leading = leading,
        trailing = {
            CMSSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                trackOnColor = AccentBlue,
                trackOffColor = TertiaryFill,
                thumbOnColor = Color.White,
                thumbOffColor = TertiaryText
            )
        }
    ) {
        CMSListRowText(title, desc)
    }
}

/** 菜单行：右侧是当前值胶囊，点按/长按把胶囊矩形交给弹出菜单做锚点。 */
@Composable
fun CMSMenuRow(
    title: String,
    value: String,
    desc: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    capsuleColor: Color = TertiaryFill,
    capsulePressedColor: Color = SecondaryCard,
    valueColor: Color = AccentBlue,
    minHeight: Dp = CMSListDefaults.rowMinHeight,
    contentPadding: PaddingValues = PaddingValues(vertical = LocalListRowPadding.current),
    horizontalInset: Dp? = null,
    highlightShape: Shape = RoundedCornerShape(0.dp),
    pressedColor: Color = Color(0x1F8E8E93),
    titleColor: Color = PrimaryText,
    descColor: Color = SecondaryText,
    titleSize: TextUnit = CMSListDefaults.titleSize,
    descSize: TextUnit = CMSListDefaults.descSize,
    onOpen: (IntRect) -> Unit
) {
    var capsuleRect by remember { mutableStateOf(IntRect.Zero) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val capsuleBg by animateColorAsState(
        targetValue = if (pressed) capsulePressedColor else capsuleColor,
        animationSpec = tween(140, easing = CMSPressEase),
        label = "cmsMenuCapsuleBg"
    )
    CMSListRow(
        modifier = modifier.then(if (enabled) Modifier.cmMenuAnchor(
            onOpen = { onOpen(capsuleRect) }, interactionSource = interaction
        ) else Modifier),
        enabled = enabled,
        minHeight = minHeight,
        contentPadding = contentPadding,
        horizontalInset = horizontalInset,
        highlightShape = highlightShape,
        pressedColor = pressedColor,
        interactionSource = interaction,
        trailing = {
        Row(
            modifier = Modifier
                .widthIn(min = 80.dp)
                .height(27.dp)
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    capsuleRect = IntRect(bounds.left.toInt(), bounds.top.toInt(),
                        bounds.right.toInt(), bounds.bottom.toInt())
                }
                .clip(ContinuousRoundedCornerShape(12.dp)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .height(25.dp)
                    .clip(ContinuousRoundedCornerShape(10.dp))
                    .background(capsuleBg)
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    value,
                    color = valueColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = HarmonySansSC,
                    style = CMSNoFontPadding,
                    maxLines = 1
                )
            }
            Text(
                "▼",
                color = TertiaryText,
                fontSize = 8.sp,
                fontFamily = HarmonySansSC,
                style = CMSNoFontPadding,
                modifier = Modifier.padding(start = 5.dp)
            )
        }
        }
    ) {
        CMSListRowText(title, desc, titleColor, descColor, titleSize, descSize)
    }
}

// ───────────────────────── 兼容旧调用名 ─────────────────────────

/** 兼容通用内容卡片；设置行分组使用 [CMSSection]。 */
@Composable
fun CMSSettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val rows = remember { mutableStateMapOf<Any, CMSRowPlacement>() }
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Column(modifier.fillMaxWidth().clip(ContinuousRoundedCornerShape(CMSListDefaults.sectionCorner))
        .background(CardBackground)
        .onGloballyPositioned { cardCoordinates = it }
        .drawWithContent {
            drawRowHighlights(rows, cardCoordinates,
                CMSListDefaults.sectionPadding.toPx(), CMSListDefaults.sectionPadding.toPx())
            drawContent()
        }
        .padding(CMSListDefaults.sectionPadding)) {
        CompositionLocalProvider(
            LocalListRowPadding provides CMSListDefaults.rowPadding,
            LocalListRowHorizontalInset provides 0.dp,
            LocalListRowEndInset provides 0.dp,
            LocalSectionRows provides rows
        ) { content() }
    }
}

/** @deprecated 使用 [CMSRowDivider]。 */
@Composable
fun CMSSettingsDivider() = CMSRowDivider()

/** @deprecated 使用 [CMSSectionTitle]。 */
@Composable
fun CMSSettingsSectionTitle(title: String) = CMSSectionTitle(title)

/** @deprecated 使用 [CMSListRow]。 */
@Composable
fun CMSSettingsNavRow(
    title: String,
    desc: String = "",
    danger: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit
) = CMSListRow(title = title, desc = desc, danger = danger, showChevron = showChevron, onClick = onClick)

/** @deprecated 使用 [CMSToggleRow]。 */
@Composable
fun CMSSettingsSwitchRow(
    title: String,
    desc: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) = CMSToggleRow(title = title, desc = desc, checked = checked, onCheckedChange = onCheckedChange)

/** @deprecated 使用 [CMSMenuRow]。 */
@Composable
fun CMSSettingsMenuRow(
    title: String,
    desc: String = "",
    value: String,
    enabled: Boolean = true,
    onOpen: (IntRect) -> Unit
) = CMSMenuRow(title = title, value = value, desc = desc, enabled = enabled, onOpen = onOpen)

/**
 * 托管式分组：卡片外观、行内边距与**行间分割线**都由分组统一处理，
 * 行只声明内容与行为，对应 系统的 `Section` + `.insetGrouped`。
 */
@Composable
fun CMSSection(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = CMSListDefaults.sectionPadding),
    containerColor: Color = CardBackground,
    cornerRadius: Dp = CMSListDefaults.sectionCorner,
    separatorIndent: Dp = 16.dp,
    showSeparators: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    separatorEndIndent: Dp = 0.dp,
    separatorColor: Color = DividerColor,
    separatorThickness: Dp = 1.dp,
    rowSpacing: Dp = 0.dp,
    shape: Shape = ContinuousRoundedCornerShape(cornerRadius),
    content: @Composable ColumnScope.() -> Unit
) {
    val rows = remember { mutableStateMapOf<Any, CMSRowPlacement>() }
    var sectionCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val direction = LocalLayoutDirection.current
    Column(modifier.fillMaxWidth()) {
        header?.invoke()
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(containerColor)
                .onGloballyPositioned { sectionCoordinates = it }
                .drawWithContent {
                    drawRowHighlights(rows, sectionCoordinates,
                        contentPadding.calculateTopPadding().toPx(),
                        contentPadding.calculateBottomPadding().toPx())
                    drawContent()
                    val origin = sectionCoordinates
                    if (showSeparators && separatorThickness > 0.dp && origin != null && origin.isAttached) {
                        val positions = rows.values.filter { it.coordinates.isAttached }
                            .map { origin.localPositionOf(it.coordinates, Offset.Zero).y }
                            .sorted()
                        val left = if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr)
                            separatorIndent.toPx() else separatorEndIndent.toPx()
                        val right = size.width - if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr)
                            separatorEndIndent.toPx() else separatorIndent.toPx()
                        if (right > left) positions.drop(1).forEach { y ->
                            drawLine(separatorColor, Offset(left, y), Offset(right, y),
                                separatorThickness.toPx())
                        }
                    }
                }
                .padding(top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            CompositionLocalProvider(
                LocalSectionRows provides rows,
                LocalListRowHorizontalInset provides
                    contentPadding.calculateStartPadding(direction),
                LocalListRowEndInset provides contentPadding.calculateEndPadding(direction)
            ) {
                content()
            }
        }
        footer?.invoke()
    }
}
