@file:Suppress("DEPRECATION")

package com.lindicra.cms

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import com.lindicra.cms.ui.theme.Accent
import com.lindicra.cms.ui.theme.LocalCMSAccentColor

// Aliases preserve existing enum values and state identity across old and new APIs.
typealias CMSSliderThumbStyle = CMSProgressDot
typealias CMSMenuItem = CMSelectMenuItem
typealias CMSMenuTouchState = CMMenuTouchState
typealias CMSScrollbarStyle = CMScrollbarStyle
typealias CMSScrollbarIndexLayout = CMScrollbarIndexLayout
typealias CMSScrollSection = CMScrollSection
typealias CMSVerticalDragBridge = CMVerticalDragBridge
typealias CMSCarouselDirection = CMSwipeDirection
typealias CMSCarouselOverscroll = CMSEdgeOverflow
typealias CMSCarouselIndicator = CMSNavBar

/** 返回按钮 · Back Button */
@Composable
fun CMSBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconColor: Color? = null,
    exclusiveTouchGesture: Boolean = false
) = CMSGlassBackButton(onClick, modifier, iconColor, exclusiveTouchGesture)

/** 滑块 · Slider. Values and callbacks use [range], including read-only display. */
@Composable
fun CMSSlider(
    value: Float,
    onValueChangeStarted: () -> Unit = {},
    onValueChange: (Float) -> Unit = {},
    onValueChangeFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    selectedColor: Color = Accent,
    thumbStyle: CMSSliderThumbStyle = CMSSliderThumbStyle.None,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    thumbRingColor: Color? = null,
    thumbFillDark: Color? = null,
    thumbFillLight: Color? = null,
    thumbStretch: Dp = 6.dp,
    vertical: Boolean = false,
    liveUpdate: Boolean = false,
    thicknessAnimation: Boolean = true,
    dampingEnabled: Boolean = true,
    overscrollEnabled: Boolean = true,
    trackThickness: Dp = 6.dp,
    pressedThickness: Dp = 13.dp,
    dampingStrength: Float = 1f,
    readOnlyDim: Boolean = true
) = CMSProgressBar(
    value, onValueChangeStarted, onValueChange, onValueChangeFinished, modifier,
    selectedColor, thumbStyle, range, steps, readOnly, enabled, thumbRingColor,
    thumbFillDark, thumbFillLight, thumbStretch, vertical, liveUpdate,
    thicknessAnimation, dampingEnabled, overscrollEnabled, trackThickness, pressedThickness,
    dampingStrength, readOnlyDim
)

/** 弹出菜单 · Popup Menu */
@Composable
fun CMSPopupMenu(
    hazeState: HazeState,
    items: List<CMSMenuItem>,
    currentIndex: Int,
    anchorRect: IntRect,
    visible: Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    type: CMSMenuType = CMSMenuType.Select,
    accentColor: Color = Color(0xFFFF5B68),
    onSelectPath: ((List<Int>) -> Unit)? = null,
    dimBackground: Boolean = false,
    dimAmount: Float = 0.24f,
    touchState: CMSMenuTouchState? = null,
    onClosed: (() -> Unit)? = null,
    samplingSource: android.view.ViewGroup? = null
) = CMSelectMenu(
    hazeState, items, currentIndex, anchorRect, visible, onSelect, onDismiss,
    type, accentColor, onSelectPath, dimBackground, dimAmount, touchState, onClosed, samplingSource
)

@Composable
fun rememberCMSMenuTouchState(): CMSMenuTouchState = rememberCMMenuTouchState()

fun Modifier.cmsMenuAnchor(onOpen: () -> Unit, onLongOpen: (() -> Unit)? = null): Modifier =
    cmMenuAnchor(onOpen, onLongOpen)

/** 滚动视图 · Scroll View */
@Composable
fun CMSScrollView(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    scrollEnabled: Boolean = true,
    showScrollbar: Boolean = false,
    springOverscroll: Boolean = false,
    lazyGridState: LazyGridState? = null,
    onOverscrollChanged: (Float) -> Unit = {},
    axisDragBridge: CMSVerticalDragBridge? = LocalCMSPageBridge.current,
    onUserScroll: (Float) -> Unit = {},
    onScrollRelease: (Float) -> Unit = {},
    onScrollPress: () -> Boolean = { false },
    sampleGlassContent: Boolean = !LocalCMSPagerSampling.current,
    scrollbarStyle: CMSScrollbarStyle = CMSScrollbarStyle.Classic,
    scrollbarInteractive: Boolean = true,
    scrollbarPressThickening: Boolean = true,
    scrollbarSections: List<CMSScrollSection> = emptyList(),
    scrollbarInsets: PaddingValues = PaddingValues(8.dp),
    scrollbarIndexLayout: CMSScrollbarIndexLayout = CMSScrollbarIndexLayout.TopPacked,
    content: @Composable ColumnScope.() -> Unit
) = CMScrollBox(
    modifier, scrollState, scrollEnabled, showScrollbar, springOverscroll, lazyGridState,
    onOverscrollChanged, axisDragBridge, onUserScroll, onScrollRelease, onScrollPress,
    sampleGlassContent, scrollbarStyle, scrollbarInteractive, scrollbarPressThickening,
    scrollbarSections, scrollbarInsets, scrollbarIndexLayout, content
)

/** 轮播视图 · Carousel */
@Composable
fun CMSCarousel(
    itemCount: Int,
    modifier: Modifier = Modifier,
    direction: CMSCarouselDirection = CMSCarouselDirection.Horizontal,
    autoPlay: Boolean = false,
    autoPlayInterval: Long = 3000L,
    showArrows: Boolean = false,
    edgeOverflow: CMSCarouselOverscroll = CMSCarouselOverscroll.Spring,
    snapBack: Boolean = true,
    indicatorStyle: CMSCarouselIndicator = CMSCarouselIndicator.DotIndicator,
    pageHeight: Dp = 140.dp,
    pageSpacing: Dp = 8.dp,
    clipRadius: Dp = 12.dp,
    freeMode: Boolean = false,
    pageWidthFraction: Float = 1f,
    indicatorVisibleCount: Int = 5,
    indicatorSize: Dp = 6.dp,
    indicatorActiveWidth: Dp = 14.dp,
    indicatorBottomInset: Dp = 8.dp,
    indicatorInteractive: Boolean = true,
    indicatorColor: Color = LocalCMSAccentColor.current,
    arrowSize: Dp = 28.dp,
    content: @Composable (page: Int) -> Unit
) = CMSwiper(
    itemCount, modifier, direction, autoPlay, autoPlayInterval, showArrows, edgeOverflow,
    snapBack, indicatorStyle, pageHeight, pageSpacing, clipRadius, freeMode, pageWidthFraction,
    indicatorVisibleCount, indicatorSize, indicatorActiveWidth, indicatorBottomInset,
    indicatorInteractive, indicatorColor, arrowSize, content
)

/** 底部面板 · Bottom Sheet */
@Composable
fun CMSBottomSheet(
    onDismiss: () -> Unit,
    heightFraction: Float = 0.86f,
    maxHeight: Dp = 640.dp,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) = CMHalfModal(onDismiss, heightFraction, maxHeight, content)

/** 分段控件 · Segmented Control */
@Composable
fun CMSSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) = CMSCapsuleTabs(labels, selectedIndex, onSelectedChange, modifier, outlined)
