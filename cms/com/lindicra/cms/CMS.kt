package com.lindicra.cms

import android.view.ViewGroup
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lindicra.cms.ui.theme.CMSTheme
import com.lindicra.cms.ui.theme.GlassMaterial
import com.lindicra.cms.ui.theme.HarmonySansSC
import com.lindicra.cms.ui.theme.LocalCMSAccentColor
import com.lindicra.cms.ui.theme.LocalClearIsDark
import com.lindicra.cms.ui.theme.PageBackground
import com.lindicra.cms.ui.theme.PrimaryText
import com.lindicra.cms.ui.theme.SecondaryText
import dev.chrisbanes.haze.HazeState

/**
 * AndroidCMS 入口，按 系统（声明式）的组件体系组织：
 *
 * - [Appearance]：主题与全局外观
 * - [Navigation]：TabView、返回按钮
 * - [Controls]：Toggle、Slider、SegmentedPicker、Menu、GlassButton
 * - [Content]：Source、Page、ScrollView、PageView、设置列表
 * - [Presentation]：Sheet
 *
 * 形式切换沿用 系统 思路：默认 `Auto`，按窗口宽度决定形态，宿主仍可手动覆盖。
 */
object CMS {

    // ─────────────────────────────── Appearance ───────────────────────────────
    object Appearance {

        /** 全局默认值：深浅色、强调色、材质、触感、Tab 形态。 */
        @Composable
        fun Provide(settings: CMSSettings, content: @Composable () -> Unit) {
            CompositionLocalProvider(LocalCMSSettings provides settings, content = content)
        }

        @Composable
        fun Theme(
            darkTheme: Boolean = CMSDefaults.darkTheme(),
            glassMaterial: GlassMaterial = CMSDefaults.glassMaterial(),
            hapticsEnabled: Boolean = CMSDefaults.hapticsEnabled(),
            accentColor: Color? = CMSDefaults.accentColor(),
            hazState: HazeState? = null,
            haptics: com.lindicra.cms.ui.theme.CMSHapticsProvider? = null,
            backHistory: com.lindicra.cms.ui.theme.CMSBackHistory? =
                com.lindicra.cms.ui.theme.LocalCMSBackHistory.current,
            content: @Composable () -> Unit
        ) {
            CMSTheme(
                darkTheme = darkTheme,
                glassMaterial = glassMaterial,
                hapticsEnabled = hapticsEnabled,
                haptics = haptics,
                backHistory = backHistory,
                accentColor = accentColor,
                hazState = hazState ?: remember { HazeState() },
                content = content
            )
        }
    }

    // ─────────────────────────────── Navigation ───────────────────────────────
    object Navigation {
        // Tabbar 家族：TabView（玻璃）、CapsuleNavigation（胶囊）、TabNavigation（下划线）。
        // 三者都是受控导航；页面和 PagerState 由宿主持有。
        /** Tabbar 的胶囊形态，提供描边 / 底板变体。 */
        @Composable
        fun CapsuleNavigation(
            labels: List<String>, selectedIndex: Int, onSelectedChange: (Int) -> Unit,
            modifier: Modifier = Modifier,
            variant: CMSCapsuleVariant = CMSCapsuleVariant.Filled,
            enabled: Boolean = true,
            height: Dp = 44.dp, outerRadius: Dp = 22.dp, indicatorRadius: Dp = 18.dp,
            contentInset: Dp = 4.dp, borderWidth: Dp = 1.5.dp, fontSize: TextUnit = 14.sp,
            selectedColor: Color = PrimaryText, unselectedColor: Color = SecondaryText,
            containerColor: Color = Color.Unspecified, indicatorColor: Color = Color.Unspecified,
            pageProgress: Float? = null,
            draggable: Boolean = true, liquidGlass: Boolean = false, liquidMotion: Boolean = true,
            samplingSource: ViewGroup? = null, hazeState: HazeState? = null,
            pageTouchVersion: Int = 0
        ) = CMSCapsuleNavigation(labels, selectedIndex, onSelectedChange, modifier, variant,
            enabled, height, outerRadius, indicatorRadius, contentInset, borderWidth, fontSize,
            selectedColor, unselectedColor, containerColor, indicatorColor, pageProgress,
            draggable, liquidGlass, liquidMotion, samplingSource, hazeState, pageTouchVersion)

        @Composable
        fun TabNavigation(
            labels: List<String>, selectedIndex: Int, onSelectedChange: (Int) -> Unit,
            modifier: Modifier = Modifier, enabled: Boolean = true,
            height: Dp = 48.dp, fontSize: TextUnit = 14.sp,
            selectedColor: Color = LocalCMSAccentColor.current, unselectedColor: Color = SecondaryText,
            indicatorColor: Color = LocalCMSAccentColor.current,
            indicatorHeight: Dp = 5.dp, indicatorRadius: Dp = 2.dp,
            indicatorWidthFraction: Float = 0.6f, pageProgress: Float? = null,
            indicatorGap: Dp = 3.dp
        ) = CMSTabNavigation(labels, selectedIndex, onSelectedChange, modifier, enabled, height,
            fontSize, selectedColor, unselectedColor, indicatorColor, indicatorHeight,
            indicatorRadius, indicatorWidthFraction, pageProgress, indicatorGap)

        /**
         * Tab 栏。`form = Auto` 时按窗口宽度自适应：宽屏用侧栏，窄屏用底栏。
         * 底栏与侧栏共用同一套玻璃与选中逻辑。
         */
        @Composable
        fun TabView(
            items: List<CMSGlassTabItem>,
            selectedIndex: Int,
            onSelectedChange: (Int) -> Unit,
            modifier: Modifier = Modifier,
            form: CMSTabForm = CMSDefaults.tabForm(),
            placement: CMSTabPlacement = CMSDefaults.tabPlacement(),
            height: Dp = 52.dp,
            indicatorInset: Dp = 4.dp,
            pressExpansion: Dp = 20.dp,
            iconSize: Dp = 24.dp,
            labelSize: TextUnit = 12.sp,
            showLabels: Boolean = true,
            selectedColor: Color = LocalCMSAccentColor.current,
            unselectedColor: Color = SecondaryText,
            indicatorColor: Color = if (LocalClearIsDark.current) Color(0xB31C1C1E) else Color(0x388E8E93),
            liquidMotion: Boolean = true,
            enabled: Boolean = true,
            samplingSource: ViewGroup? = null,
            hazeState: HazeState? = null,
            fillWidth: Boolean = false,
            itemWidth: Dp = 88.dp,
            itemOverlap: Dp = 10.dp,
            pageProgress: Float? = null,
            pageTouchVersion: Int = 0
        ) {
            val wide = LocalConfiguration.current.screenWidthDp >= 600
            val resolved = when (form) {
                CMSTabForm.Auto -> if (wide) CMSTabForm.Rail else CMSTabForm.Edge
                else -> form
            }
            if (resolved == CMSTabForm.Rail) {
                TabViewRail(
                    items = items,
                    selectedIndex = selectedIndex,
                    onSelectedChange = onSelectedChange,
                    modifier = modifier,
                    iconSize = iconSize,
                    labelSize = labelSize,
                    selectedColor = selectedColor,
                    unselectedColor = unselectedColor,
                    indicatorColor = indicatorColor,
                    enabled = enabled
                )
                return
            }
            val floatingPad = if (resolved == CMSTabForm.Floating) Modifier.padding(horizontal = 20.dp) else Modifier
            CMSGlassTabBar(
                items = items,
                selectedIndex = selectedIndex,
                onSelectedChange = onSelectedChange,
                modifier = modifier.then(floatingPad),
                height = height,
                indicatorInset = indicatorInset,
                pressExpansion = pressExpansion,
                iconSize = iconSize,
                labelSize = labelSize,
                showLabels = showLabels,
                selectedColor = selectedColor,
                unselectedColor = unselectedColor,
                indicatorColor = indicatorColor,
                liquidMotion = liquidMotion,
                enabled = enabled,
                samplingSource = samplingSource,
                hazeState = hazeState,
                fillWidth = fillWidth,
                itemWidth = itemWidth,
                itemOverlap = itemOverlap,
                pageProgress = pageProgress,
                pageTouchVersion = pageTouchVersion
            )
        }

        /** 侧栏形态：竖向排列，图标在上、文字在下。 */
        @Composable
        fun TabViewRail(
            items: List<CMSGlassTabItem>,
            selectedIndex: Int,
            onSelectedChange: (Int) -> Unit,
            modifier: Modifier = Modifier,
            width: Dp = 76.dp,
            itemHeight: Dp = 64.dp,
            iconSize: Dp = 24.dp,
            labelSize: TextUnit = 11.sp,
            showLabels: Boolean = true,
            selectedColor: Color = LocalCMSAccentColor.current,
            unselectedColor: Color = SecondaryText,
            indicatorColor: Color = if (LocalClearIsDark.current) Color(0xB31C1C1E) else Color(0x388E8E93),
            enabled: Boolean = true
        ) {
            val verticalPadding = 12.dp
            val railHeight = itemHeight * items.size + verticalPadding * 2
            val indicatorY by animateDpAsState(
                verticalPadding + itemHeight * selectedIndex,
                label = "cmsRailIndicator"
            )
            Box(modifier.size(width, railHeight)) {
                CMSGlassButton(
                    onClick = {},
                    width = width,
                    height = railHeight,
                    cornerRadius = width / 2,
                    enabled = false,
                    sampleContent = false
                ) {}
                Box(
                    Modifier.offset(y = indicatorY)
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clip(RoundedCornerShape(itemHeight / 2))
                        .background(indicatorColor)
                )
                Column(Modifier.fillMaxSize().padding(vertical = verticalPadding)) {
                    items.forEachIndexed { index, item ->
                        val active = index == selectedIndex
                        Column(
                            Modifier.height(itemHeight)
                                .fillMaxWidth()
                                .selectable(selected = active, enabled = enabled) {
                                    if (enabled && index != selectedIndex) onSelectedChange(index)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            item.icon?.let {
                                Icon(
                                    it,
                                    contentDescription = item.label,
                                    tint = if (active) selectedColor else unselectedColor,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            if (showLabels) {
                                if (item.icon != null) Spacer(Modifier.height(3.dp))
                                Text(
                                    item.label,
                                    color = if (active) selectedColor else unselectedColor,
                                    fontSize = labelSize,
                                    fontFamily = HarmonySansSC,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        /** 主页面顶栏（大标题 + 玻璃背景）。 */
        @Composable
        fun MainTopBar(
            title: String,
            topInset: Dp,
            hazeState: HazeState,
            modifier: Modifier = Modifier,
            glassEnabled: Boolean = true,
            style: CMSGlassTopBarStyle? = null,
            trailingContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
        ) = CMSMainGlassTopBar(title, topInset, hazeState, modifier, glassEnabled, style, trailingContent)

        /** 二级页面顶栏（返回 + 居中标题）。 */
        @Composable
        fun SecondaryTopBar(
            title: String,
            topInset: Dp = 0.dp,
            hazeState: HazeState? = null,
            modifier: Modifier = Modifier,
            onBack: (() -> Unit)? = null,
            onTitleClick: (() -> Unit)? = null
        ) = CMSSecondaryTopBar(title, topInset, hazeState, modifier, onBack, onTitleClick)

        @Composable
        fun BackButton(
            onClick: () -> Unit,
            modifier: Modifier = Modifier,
            iconColor: Color? = null,
            exclusiveTouchGesture: Boolean = false
        ) = CMSBackButton(onClick, modifier, iconColor, exclusiveTouchGesture)
    }

    /** 可复用的交互接入；不持有软件设置。 */
    object Interaction {
        /** 返回历史：为作用域内的返回按钮提供真实页面栈。 */
        @Composable
        fun BackHistory(
            history: com.lindicra.cms.ui.theme.CMSBackHistory?,
            content: @Composable () -> Unit
        ) {
            CompositionLocalProvider(
                com.lindicra.cms.ui.theme.LocalCMSBackHistory provides history,
                content = content
            )
        }

        @Composable
        fun TouchPriority(
            policy: CMSTouchPriority = CMSTouchPriority(),
            content: @Composable () -> Unit
        ) = CMSTouchPriorityScope(policy, content)

        @Composable
        fun GlassFusion(
            enabled: Boolean = true, mergeGap: Dp = 3.dp,
            proximityOnly: Boolean = false, progress: Float = 1f,
            gravityStrength: Float = 1f, content: @Composable () -> Unit
        ) = CMSGlassFusionGroup(enabled, mergeGap, proximityOnly, progress, gravityStrength, content)

        @Composable
        fun GlassSearch(
            query: String, onQueryChange: (String) -> Unit,
            expanded: Boolean, onExpandedChange: (Boolean) -> Unit,
            modifier: Modifier = Modifier,
            placeholder: String = "搜索工具", cancelText: String = "取消",
            enabled: Boolean = true, fusionEnabled: Boolean = true,
            height: Dp = 44.dp, cancelWidth: Dp = 54.dp, gap: Dp = 8.dp,
            mergeGap: Dp = 3.dp, gravityStrength: Float = 1f,
            samplingSource: ViewGroup? = null, hazeState: HazeState? = null,
            onSearch: (String) -> Unit = {}
        ) = CMSGlassSearchBar(query, onQueryChange, expanded, onExpandedChange, modifier,
            placeholder, cancelText, enabled, fusionEnabled, height, cancelWidth, gap,
            mergeGap, gravityStrength, samplingSource, hazeState, onSearch)
    }

    // ─────────────────────────────── Controls ────────────────────────────────
    object Controls {

        @Composable
        fun Toggle(
            checked: Boolean,
            onCheckedChange: (Boolean) -> Unit,
            modifier: Modifier = Modifier,
            trackOnColor: Color = LocalCMSAccentColor.current,
            trackOffColor: Color = com.lindicra.cms.ui.theme.TertiaryFill,
            thumbOnColor: Color = Color.White,
            thumbOffColor: Color = com.lindicra.cms.ui.theme.TertiaryText,
            width: Dp = 44.dp,
            height: Dp = 24.dp
        ) = CMSSwitch(checked, onCheckedChange, modifier, trackOnColor, trackOffColor,
            thumbOnColor, thumbOffColor, width, height)

        @Composable
        fun Slider(
            value: Float,
            onValueChangeStarted: () -> Unit = {},
            onValueChange: (Float) -> Unit = {},
            onValueChangeFinished: () -> Unit = {},
            modifier: Modifier = Modifier,
            selectedColor: Color = LocalCMSAccentColor.current,
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
        ) = CMSSlider(
            value, onValueChangeStarted, onValueChange, onValueChangeFinished, modifier,
            selectedColor, thumbStyle, range, steps, readOnly, enabled, thumbRingColor,
            thumbFillDark, thumbFillLight, thumbStretch, vertical, liveUpdate,
            thicknessAnimation, dampingEnabled, overscrollEnabled, trackThickness,
            pressedThickness, dampingStrength, readOnlyDim
        )

        @Composable
        fun SegmentedPicker(
            labels: List<String>,
            selectedIndex: Int,
            onSelectedChange: (Int) -> Unit,
            modifier: Modifier = Modifier,
            outlined: Boolean = false
        ) = CMSSegmentedControl(labels, selectedIndex, onSelectedChange, modifier, outlined)

        /** 滚筒选择器。 */
        @Composable
        fun WheelPicker(
            items: List<String>,
            selectedIndex: Int,
            onSelected: (Int) -> Unit,
            modifier: Modifier = Modifier,
            itemHeight: Dp = 36.dp,
            visibleCount: Int = 5,
            loop: Boolean = false,
            frictionMultiplier: Float = 1.4f,
            dampingRatio: Float = 1f,
            stiffness: Float = 500f,
            showSelectionBand: Boolean = true,
            wheelRadius: Dp = 110.dp,
            hapticEnabled: Boolean = true,
            edgeFadeStart: Float = 0.45f
        ) = CMSWheelPicker(
            items, selectedIndex, onSelected, modifier, itemHeight, visibleCount,
            loop, frictionMultiplier, dampingRatio, stiffness, showSelectionBand,
            wheelRadius = wheelRadius, hapticEnabled = hapticEnabled, edgeFadeStart = edgeFadeStart
        )

        /** 日期选择器。 */
        @Composable
        fun DatePicker(
            selected: java.time.LocalDate,
            onSelected: (java.time.LocalDate) -> Unit,
            modifier: Modifier = Modifier,
            weekStart: CMSWeekStart = CMSWeekStart.Monday,
            minDate: java.time.LocalDate? = null,
            maxDate: java.time.LocalDate? = null,
            showTodayButton: Boolean = true,
            mode: CMSDatePickerMode = CMSDatePickerMode.Calendar,
            showModeSwitch: Boolean = false
        ) = CMSDatePicker(
            selected, onSelected, modifier,
            weekStart = weekStart, minDate = minDate, maxDate = maxDate,
            showTodayButton = showTodayButton, mode = mode, showModeSwitch = showModeSwitch
        )

        /** 时间选择器。 */
        @Composable
        fun TimePicker(
            hour: Int,
            minute: Int,
            onTimeChange: (Int, Int) -> Unit,
            modifier: Modifier = Modifier,
            minuteStep: Int = 1,
            is24Hour: Boolean = true,
            itemHeight: Dp = 36.dp,
            visibleCount: Int = 5,
            loop: Boolean = false
        ) = CMSTimePicker(
            hour, minute, onTimeChange, modifier, minuteStep, is24Hour,
            itemHeight, visibleCount, loop
        )

        /** 颜色选择器。 */
        @Composable
        fun ColorPicker(
            color: Color,
            onColorChange: (Color) -> Unit,
            modifier: Modifier = Modifier,
            showPresets: Boolean = true,
            showAlpha: Boolean = true,
            presets: List<Color> = CMSColorPresets,
            recentLimit: Int = 6,
            panelHeight: Dp = 150.dp,
            mode: CMSColorPickerMode = CMSColorPickerMode.Spectrum,
            showModeSwitch: Boolean = true,
            showInputs: Boolean = true,
            palette: CMSColorPalette = CMSColorPalette.Vivid
        ) = CMSColorPicker(
            color, onColorChange, modifier, showPresets, showAlpha,
            presets, recentLimit, panelHeight, mode, showModeSwitch, showInputs, palette
        )

        /** CLEAR 标准输入框。 */
        @Composable
        fun TextField(
            value: String,
            onValueChange: (String) -> Unit,
            modifier: Modifier = Modifier,
            placeholder: String? = null,
            label: String? = null,
            supportingText: String? = null,
            isError: Boolean = false,
            isSuccess: Boolean = false,
            enabled: Boolean = true,
            readOnly: Boolean = false,
            size: CMSTextFieldSize = CMSTextFieldSize.Medium,
            variant: CMSTextFieldVariant = CMSTextFieldVariant.Plain,
            prefixText: String? = null,
            suffixText: String? = null,
            showClearButton: Boolean = false,
            singleLine: Boolean = true,
            maxLines: Int = if (singleLine) 1 else 4,
            keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions.Default,
            keyboardActions: androidx.compose.foundation.text.KeyboardActions =
                androidx.compose.foundation.text.KeyboardActions.Default,
            visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
                androidx.compose.ui.text.input.VisualTransformation.None,
            leadingContent: (@Composable (() -> Unit))? = null,
            trailingContent: (@Composable (() -> Unit))? = null
        ) = CMSTextField(
            value, onValueChange, modifier, placeholder, label, supportingText,
            isError, isSuccess, enabled, readOnly, size, variant, prefixText, suffixText,
            showClearButton, singleLine, maxLines, keyboardOptions, keyboardActions,
            visualTransformation, leadingContent, trailingContent
        )

        @Composable
        fun Menu(
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
            samplingSource: ViewGroup? = null
        ) = CMSPopupMenu(
            hazeState, items, currentIndex, anchorRect, visible, onSelect, onDismiss,
            type, accentColor, onSelectPath, dimBackground, dimAmount, touchState, onClosed,
            samplingSource
        )

        @Composable
        fun GlassButton(
            onClick: () -> Unit,
            modifier: Modifier = Modifier,
            width: Dp = 36.dp,
            height: Dp = 36.dp,
            enabled: Boolean = true,
            useNativeGlass: Boolean = true,
            sampleContent: Boolean = false,
            samplingSource: ViewGroup? = null,
            hazeState: HazeState? = null,
            shadowStrength: Float = 1f,
            touchEffectIntensity: Float = 1f,
            glassTint: Color = Color.White,
            glassTintAlpha: Float? = null,
            glassBackground: Color = Color.White.copy(alpha = 0.08f),
            motionBlur: Dp = 0.dp,
            cornerRadius: Dp? = null,
            exclusiveTouchGesture: Boolean = false,
            onLongClick: (() -> Unit)? = null,
            content: @Composable (Color) -> Unit
        ) = CMSGlassButton(
            onClick = onClick, modifier = modifier, width = width, height = height,
            enabled = enabled, useNativeGlass = useNativeGlass, sampleContent = sampleContent,
            samplingSource = samplingSource, hazeState = hazeState, shadowStrength = shadowStrength,
            touchEffectIntensity = touchEffectIntensity, glassTint = glassTint,
            glassTintAlpha = glassTintAlpha, glassBackground = glassBackground,
            motionBlur = motionBlur, cornerRadius = cornerRadius,
            exclusiveTouchGesture = exclusiveTouchGesture, onLongClick = onLongClick,
            content = content
        )
    }

    // ─────────────────────────────── Content ─────────────────────────────────
    object Content {

        /** 页面分页：自动连接内部 Page / ScrollView 的主滚动与触控仲裁。 */
        @Composable
        fun Pager(
            state: PagerState,
            modifier: Modifier = Modifier,
            swipeEnabled: Boolean = true,
            touchPriority: CMSTouchPriority = LocalCMSTouchPriority.current,
            hazeState: HazeState? = com.lindicra.cms.ui.theme.LocalGlassHazeState.current,
            sampleGlassContent: Boolean = true,
            beyondViewportPageCount: Int = 1,
            canStart: (androidx.compose.ui.geometry.Offset) -> Boolean = { true },
            onTouchInterrupt: () -> Unit = {},
            content: @Composable (page: Int) -> Unit
        ) = CMSPagePager(state, modifier, swipeEnabled, touchPriority, hazeState,
            sampleGlassContent, beyondViewportPageCount, canStart, onTouchInterrupt,
            content = content)

        /** 页面容器：滚动内容、玻璃采样、顶栏叠层、上下避让与触控桥接。 */
        @Composable
        fun Page(
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
            topBar: @Composable BoxScope.(HazeState) -> Unit,
            content: @Composable ColumnScope.() -> Unit
        ) = CMSGlassPage(
            topInset = topInset, modifier = modifier, glassEnabled = glassEnabled,
            topBarReserve = topBarReserve, bottomReserve = bottomReserve,
            hazeState = hazeState, maxContentWidth = maxContentWidth,
            axisDragBridge = axisDragBridge, touchPriority = touchPriority,
            springOverscroll = springOverscroll, showScrollbar = showScrollbar,
            sampleGlassContent = sampleGlassContent, topBar = topBar, content = content
        )

        @Composable
        fun Source(
            modifier: Modifier = Modifier,
            sourceTag: String = CMS_GLASS_CONTENT_SOURCE_TAG,
            onSourceReady: (ViewGroup) -> Unit = {},
            hazeState: HazeState? = null,
            backgroundColor: Color = PageBackground,
            content: @Composable () -> Unit
        ) = CMSGlassContentSource(modifier, sourceTag, onSourceReady, hazeState, backgroundColor, content)

        @Composable
        fun ScrollView(
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
        ) = CMSScrollView(
            modifier, scrollState, scrollEnabled, showScrollbar, springOverscroll, lazyGridState,
            onOverscrollChanged, axisDragBridge, onUserScroll, onScrollRelease, onScrollPress,
            sampleGlassContent, scrollbarStyle, scrollbarInteractive, scrollbarPressThickening,
            scrollbarSections, scrollbarInsets, scrollbarIndexLayout, content
        )

        /** 代码块：圆角（0 为直角）、最大 / 固定 / 无限高度、语法着色与四边触边回弹。 */
        @Composable
        fun CodeBlock(
            code: String,
            modifier: Modifier = Modifier,
            language: String? = "kotlin",
            cornerRadius: Dp = 14.dp,
            height: Dp? = null,
            maxHeight: Dp? = null,
            wrap: Boolean = false,
            showHeader: Boolean = true,
            copyable: Boolean = true,
            highlight: Boolean = true,
            edgeFeedback: Boolean = true
        ) = CMSCodeBlock(
            code = code, modifier = modifier, language = language, cornerRadius = cornerRadius,
            height = height, maxHeight = maxHeight, wrap = wrap, showHeader = showHeader,
            copyable = copyable, highlight = highlight, edgeFeedback = edgeFeedback
        )

        /** 表格：与代码块同风格，可调圆角、高度与列宽，超出后横向滚动并触边回弹。 */
        @Composable
        fun Table(
            columns: List<String>,
            rows: List<List<String>>,
            modifier: Modifier = Modifier,
            cornerRadius: Dp = 14.dp,
            height: Dp? = null,
            maxHeight: Dp? = null,
            minColumnWidth: Dp = 120.dp,
            striped: Boolean = true,
            showDividers: Boolean = true,
            alignments: List<CMSTableAlign> = emptyList(),
            edgeFeedback: Boolean = true
        ) = CMSTable(
            columns, rows, modifier, cornerRadius, height, maxHeight, minColumnWidth,
            striped = striped, showDividers = showDividers,
            alignments = alignments, edgeFeedback = edgeFeedback
        )

        /** 局部横向内容；与父级 Page / ScrollView 共用触控优先级及换向交接。 */
        @Composable
        fun HorizontalScrollView(
            modifier: Modifier = Modifier,
            scrollState: ScrollState = rememberScrollState(),
            scrollEnabled: Boolean = true,
            reverseScrolling: Boolean = false,
            touchPriority: CMSTouchPriority = LocalCMSTouchPriority.current,
            content: @Composable RowScope.() -> Unit
        ) {
            androidx.compose.foundation.layout.Row(
                modifier.cmsHorizontalScrollTouchPriority(
                    scrollState, scrollEnabled, reverseScrolling, touchPriority),
                content = content
            )
        }

        @Composable
        fun PageView(
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
        ) = CMSCarousel(
            itemCount, modifier, direction, autoPlay, autoPlayInterval, showArrows, edgeOverflow,
            snapBack, indicatorStyle, pageHeight, pageSpacing, clipRadius, freeMode,
            pageWidthFraction, indicatorVisibleCount, indicatorSize, indicatorActiveWidth,
            indicatorBottomInset, indicatorInteractive, indicatorColor, arrowSize, content
        )

        /** 组容器：只负责组与组之间的间隙。 */
        @Composable
        fun List(
            modifier: Modifier = Modifier,
            spacing: Dp = CMSListDefaults.groupSpacing,
            content: @Composable ColumnScope.() -> Unit
        ) = CMSList(modifier = modifier, spacing = spacing, content = content)

        /** 托管分组：卡片 + 自动分割线（首行不画），对应 系统的 Section。 */
        @Composable
        fun Section(
            modifier: Modifier = Modifier,
            contentPadding: PaddingValues = PaddingValues(horizontal = CMSListDefaults.sectionPadding),
            containerColor: Color = com.lindicra.cms.ui.theme.CardBackground,
            cornerRadius: Dp = CMSListDefaults.sectionCorner,
            separatorIndent: Dp = 16.dp,
            showSeparators: Boolean = true,
            header: (@Composable () -> Unit)? = null,
            footer: (@Composable () -> Unit)? = null,
            separatorEndIndent: Dp = 0.dp,
            separatorColor: Color = com.lindicra.cms.ui.theme.DividerColor,
            separatorThickness: Dp = 1.dp,
            rowSpacing: Dp = 0.dp,
            content: @Composable ColumnScope.() -> Unit
        ) = CMSSection(
            modifier = modifier, contentPadding = contentPadding,
            containerColor = containerColor, cornerRadius = cornerRadius,
            separatorIndent = separatorIndent, showSeparators = showSeparators,
            header = header, footer = footer, separatorEndIndent = separatorEndIndent,
            separatorColor = separatorColor, separatorThickness = separatorThickness,
            rowSpacing = rowSpacing, content = content
        )

        /** 卡片分组：圆角、容器色、内边距、行间距都可调。 */
        @Composable
        fun ListSection(
            modifier: Modifier = Modifier,
            contentPadding: PaddingValues = PaddingValues(horizontal = CMSListDefaults.sectionPadding),
            spacing: Dp = 0.dp,
            containerColor: Color = com.lindicra.cms.ui.theme.CardBackground,
            cornerRadius: Dp = CMSListDefaults.sectionCorner,
            rowPadding: Dp = CMSListDefaults.rowPadding,
            content: @Composable ColumnScope.() -> Unit
        ) = CMSListSection(
            modifier = modifier,
            contentPadding = contentPadding,
            spacing = spacing,
            containerColor = containerColor,
            cornerRadius = cornerRadius,
            rowPadding = rowPadding,
            content = content
        )

        @Composable
        fun SectionTitle(
            title: String,
            modifier: Modifier = Modifier,
            color: Color = com.lindicra.cms.ui.theme.TertiaryText,
            fontSize: TextUnit = CMSListDefaults.sectionTitleSize,
            padding: PaddingValues = CMSListDefaults.sectionTitlePadding
        ) = CMSSectionTitle(
            title = title, modifier = modifier, color = color,
            fontSize = fontSize, padding = padding
        )

        /** 基础文本。 */
        @Composable
        fun Text(
            text: String,
            modifier: Modifier = Modifier,
            color: Color = com.lindicra.cms.ui.theme.PrimaryText,
            fontSize: TextUnit = 15.sp,
            fontWeight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
            maxLines: Int = Int.MAX_VALUE,
            textAlign: androidx.compose.ui.text.style.TextAlign =
                androidx.compose.ui.text.style.TextAlign.Start,
            preset: CMSTextPreset = CMSTextPreset.Auto,
            respectFontScale: Boolean = true,
            overflow: TextOverflow = TextOverflow.Clip,
            lineHeight: TextUnit = TextUnit.Unspecified,
            letterSpacing: TextUnit = TextUnit.Unspecified
        ) = CMSText(
            text, modifier, color, fontSize, fontWeight, maxLines, textAlign,
            letterSpacing, lineHeight, preset = preset,
            respectFontScale = respectFontScale, overflow = overflow
        )

        /** 次要说明文本。 */
        @Composable
        fun Label(
            text: String,
            modifier: Modifier = Modifier,
            color: Color = com.lindicra.cms.ui.theme.SecondaryText,
            fontSize: TextUnit = 12.sp
        ) = CMSLabel(text, modifier, color, fontSize)

        /** 标签文本。 */
        @Composable
        fun Tag(
            text: String,
            modifier: Modifier = Modifier,
            style: CMSTagStyle = CMSTagStyle.Outline,
            tint: Color = com.lindicra.cms.ui.theme.AccentBlue,
            fontSize: TextUnit = 11.sp,
            cornerRadius: Dp = 6.dp,
            horizontalPadding: Dp = 7.dp,
            verticalPadding: Dp = 3.dp
        ) = CMSTag(text, modifier, style, tint, fontSize, cornerRadius, horizontalPadding, verticalPadding)

        /** 链接文字。 */
        @Composable
        fun Link(
            text: String,
            onClick: () -> Unit,
            modifier: Modifier = Modifier,
            color: Color = com.lindicra.cms.ui.theme.AccentBlue,
            fontSize: TextUnit = 15.sp,
            underline: Boolean = false,
            fontWeight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
            maxLines: Int = 1,
            overflow: TextOverflow = TextOverflow.Ellipsis
        ) = CMSLink(text, onClick, modifier, color, fontSize, underline, fontWeight, maxLines, overflow)

        /** 行内文本段（标题 + 说明），供自定义行内容使用。 */
        @Composable
        fun ListRowText(
            title: String,
            desc: String = "",
            titleColor: Color = com.lindicra.cms.ui.theme.PrimaryText,
            descColor: Color = com.lindicra.cms.ui.theme.SecondaryText,
            titleSize: TextUnit = CMSListDefaults.titleSize,
            descSize: TextUnit = CMSListDefaults.descSize,
            spacing: Dp = 4.dp
        ) = CMSListRowText(title, desc, titleColor, descColor, titleSize, descSize, spacing)

        /** 独立分割线，任何位置可用。 */
        @Composable
        fun Divider(
            modifier: Modifier = Modifier,
            color: Color = com.lindicra.cms.ui.theme.DividerColor,
            thickness: Dp = 1.dp,
            startIndent: Dp = 0.dp,
            endIndent: Dp = 0.dp
        ) = CMSDivider(modifier, color, thickness, startIndent, endIndent)

        /** 带上下留白的分割线，用于行与行之间。 */
        @Composable
        fun RowDivider(
            modifier: Modifier = Modifier,
            verticalPadding: Dp = 0.dp,
            color: Color = com.lindicra.cms.ui.theme.DividerColor
        ) = CMSRowDivider(modifier, verticalPadding, color)

        /** 通用行：前导 / 内容 / 尾部三段，内容完全自定义。 */
        @Composable
        fun ListRow(
            modifier: Modifier = Modifier,
            onClick: (() -> Unit)? = null,
            enabled: Boolean = true,
            minHeight: Dp = CMSListDefaults.rowMinHeight,
            contentPadding: PaddingValues = PaddingValues(vertical = LocalListRowPadding.current),
            leading: (@Composable () -> Unit)? = null,
            trailing: (@Composable () -> Unit)? = null,
            horizontalInset: Dp? = null,
            highlightShape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            pressedHighlight: Boolean = true,
            pressedColor: Color = Color(0x1F8E8E93),
            content: @Composable RowScope.() -> Unit
        ) = CMSListRow(
            modifier = modifier, onClick = onClick, enabled = enabled,
            minHeight = minHeight, contentPadding = contentPadding,
            leading = leading, trailing = trailing, horizontalInset = horizontalInset,
            highlightShape = highlightShape, pressedHighlight = pressedHighlight,
            pressedColor = pressedColor, content = content
        )

        /** 标题行：标题 + 说明 + 可选图标 / 箭头。 */
        @Composable
        fun ListRow(
            title: String,
            desc: String = "",
            modifier: Modifier = Modifier,
            danger: Boolean = false,
            showChevron: Boolean = true,
            leading: (@Composable () -> Unit)? = null,
            trailing: (@Composable () -> Unit)? = null,
            chevron: (@Composable () -> Unit)? = null,
            onClick: () -> Unit
        ) = CMSListRow(
            title = title, desc = desc, modifier = modifier, danger = danger,
            showChevron = showChevron, leading = leading, trailing = trailing,
            chevron = chevron, onClick = onClick
        )

        @Composable
        fun ToggleRow(
            title: String,
            desc: String = "",
            checked: Boolean,
            onCheckedChange: (Boolean) -> Unit,
            modifier: Modifier = Modifier,
            leading: (@Composable () -> Unit)? = null
        ) = CMSToggleRow(title, desc, checked, onCheckedChange, modifier, leading)

        @Composable
        fun MenuRow(
            title: String,
            value: String,
            desc: String = "",
            modifier: Modifier = Modifier,
            enabled: Boolean = true,
            minHeight: Dp = CMSListDefaults.rowMinHeight,
            contentPadding: PaddingValues = PaddingValues(vertical = LocalListRowPadding.current),
            highlightShape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            pressedColor: Color = Color(0x1F8E8E93),
            valueColor: Color = com.lindicra.cms.ui.theme.AccentBlue,
            titleColor: Color = com.lindicra.cms.ui.theme.PrimaryText,
            descColor: Color = com.lindicra.cms.ui.theme.SecondaryText,
            titleSize: TextUnit = CMSListDefaults.titleSize,
            descSize: TextUnit = CMSListDefaults.descSize,
            onOpen: (IntRect) -> Unit
        ) = CMSMenuRow(
            title = title, value = value, desc = desc,
            modifier = modifier, enabled = enabled, minHeight = minHeight,
            contentPadding = contentPadding, highlightShape = highlightShape,
            pressedColor = pressedColor, valueColor = valueColor,
            titleColor = titleColor, descColor = descColor,
            titleSize = titleSize, descSize = descSize, onOpen = onOpen
        )
    }

    // ───────────────────────────────── Layout ───────────────────────────────
    object Layout {
        @Composable
        fun ZStack(
            modifier: Modifier = Modifier,
            alignment: Alignment = Alignment.TopStart,
            minWidth: Dp = Dp.Unspecified,
            maxWidth: Dp = Dp.Unspecified,
            minHeight: Dp = Dp.Unspecified,
            maxHeight: Dp = Dp.Unspecified,
            content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
        ) = CMSZStack(modifier, alignment, minWidth, maxWidth, minHeight, maxHeight, content)

        @Composable
        fun VStack(
            modifier: Modifier = Modifier,
            spacing: Dp = 0.dp,
            horizontalAlignment: Alignment.Horizontal = Alignment.Start,
            verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(spacing),
            minWidth: Dp = Dp.Unspecified,
            maxWidth: Dp = Dp.Unspecified,
            minHeight: Dp = Dp.Unspecified,
            maxHeight: Dp = Dp.Unspecified,
            content: @Composable ColumnScope.() -> Unit
        ) = CMSVStack(
            modifier, spacing, horizontalAlignment, verticalArrangement,
            minWidth, maxWidth, minHeight, maxHeight, content
        )

        @Composable
        fun HStack(
            modifier: Modifier = Modifier,
            spacing: Dp = 0.dp,
            verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
            horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(spacing),
            minWidth: Dp = Dp.Unspecified,
            maxWidth: Dp = Dp.Unspecified,
            minHeight: Dp = Dp.Unspecified,
            maxHeight: Dp = Dp.Unspecified,
            content: @Composable RowScope.() -> Unit
        ) = CMSHStack(
            modifier, spacing, verticalAlignment, horizontalArrangement,
            minWidth, maxWidth, minHeight, maxHeight, content
        )

        /** 纯分组容器：只管间距与对齐，不画卡片。 */
        @Composable
        fun Group(
            modifier: Modifier = Modifier,
            spacing: Dp = 8.dp,
            horizontalAlignment: Alignment.Horizontal = Alignment.Start,
            minWidth: Dp = Dp.Unspecified,
            maxWidth: Dp = Dp.Unspecified,
            minHeight: Dp = Dp.Unspecified,
            maxHeight: Dp = Dp.Unspecified,
            content: @Composable ColumnScope.() -> Unit
        ) = CMSGroup(
            modifier, spacing, horizontalAlignment,
            minWidth, maxWidth, minHeight, maxHeight, content
        )

        @Composable
        fun HSpacer(width: Dp) = CMSHSpacer(width)

        @Composable
        fun VSpacer(height: Dp) = CMSVSpacer(height)
    }

    // ─────────────────────────────── Presentation ────────────────────────────
    object Presentation {
        /** 警告 / 确认弹窗。 */
        @Composable
        fun Alert(
            title: String,
            message: String = "",
            confirmText: String = "好",
            cancelText: String? = null,
            destructive: Boolean = false,
            onConfirm: () -> Unit,
            onCancel: (() -> Unit)? = null,
            onDismiss: () -> Unit = {},
            scrimAlpha: Float = 0.32f,
            dismissOnOutsideClick: Boolean = true,
            dismissOnBackPress: Boolean = true,
            buttonLayout: CMSDialogButtonLayout = CMSDialogButtonLayout.Horizontal,
            titleLineHeight: TextUnit = 24.sp,
            messageLineHeight: TextUnit = 20.sp
        ) = CMSAlert(
            title, message, confirmText, cancelText, destructive,
            onConfirm, onCancel, onDismiss, scrimAlpha, dismissOnOutsideClick,
            dismissOnBackPress, buttonLayout, titleLineHeight, messageLineHeight
        )

        /** 动作列表弹窗。 */
        @Composable
        fun ActionSheet(
            actions: List<CMSDialogAction>,
            title: String = "",
            cancelText: String = "取消",
            onDismiss: () -> Unit,
            scrimAlpha: Float = 0.32f,
            dismissOnOutsideClick: Boolean = true,
            dismissOnBackPress: Boolean = true,
            actionLayout: CMSDialogButtonLayout = CMSDialogButtonLayout.Vertical,
            titleLineHeight: TextUnit = 18.sp
        ) = CMSActionSheet(
            actions, title, cancelText, onDismiss, scrimAlpha,
            dismissOnOutsideClick, dismissOnBackPress, actionLayout, titleLineHeight
        )

        /** 自定义内容弹窗。 */
        @Composable
        fun Dialog(
            onDismiss: () -> Unit,
            maxWidth: Dp = 320.dp,
            dismissOnOutsideClick: Boolean = true,
            dismissOnBackPress: Boolean = true,
            scrimAlpha: Float = 0.32f,
            semanticsTitle: String? = null,
            content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
        ) = CMSDialog(
            onDismiss, maxWidth, dismissOnOutsideClick,
            dismissOnBackPress, scrimAlpha, semanticsTitle, content = content
        )

        @Composable
        fun Sheet(
            onDismiss: () -> Unit,
            heightFraction: Float = 0.86f,
            maxHeight: Dp = 640.dp,
            content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
        ) = CMSBottomSheet(onDismiss, heightFraction, maxHeight, content)
    }
}
