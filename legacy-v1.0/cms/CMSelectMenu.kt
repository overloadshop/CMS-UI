package ldyc.qy.music

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.adamglin.composecontinuousroundedcornershape.ContinuousRoundedCornerShape
import dev.chrisbanes.haze.HazeState
import ldyc.qy.music.ui.theme.LightPrimaryText
import ldyc.qy.music.ui.theme.PrimaryText
import ldyc.qy.music.ui.theme.LocalClearIsDark
import ldyc.qy.music.ui.theme.LocalGlassMaterial
import ldyc.qy.music.ui.theme.SecondaryCard
import ldyc.qy.music.ui.theme.TertiaryText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.util.VelocityTracker

private data class CMSelectPlacement(
    val x: Int,
    val y: Int,
    val origin: TransformOrigin
)

/** 菜单类型（对齐 CLEAR 规范「菜单 Menu」章节） */
enum class CMSMenuType { Select, Actions, SortFilter }

/** 菜单项：支持图标、快捷键、危险红、禁用态、数字徽章、子菜单箭头与二级菜单 */
data class CMSelectMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val shortcut: String? = null,
    val danger: Boolean = false,
    val disabled: Boolean = false,
    val badge: String? = null,
    val subArrow: Boolean = false,
    val subItems: List<CMSelectMenuItem>? = null,
    val onClick: (() -> Unit)? = null
)

/** 菜单超过该行数时启用滚动布局（配合 CMScrollBox 侧边滚动条） */
private const val CM_MENU_MAX_VISIBLE_ROWS = 4

/**
 * 通用选择菜单浮层（C++ spring 动效 + 毛玻璃 + 进出场高斯模糊）
 *
 * 由外部提供触发锚点 [anchorRect] 与显示请求 [visible]。
 * 支持三级结构：主菜单 → 二级菜单（带 [CMSelectMenuItem.subItems] 的行，锚定该行位置弹出）。
 */
@Composable
@Deprecated("Use CMSPopupMenu", ReplaceWith("CMSPopupMenu(hazeState, items, currentIndex, anchorRect, visible, onSelect, onDismiss, type, accentColor, onSelectPath, dimBackground, dimAmount, touchState, onClosed, samplingSource)"))
fun CMSelectMenu(
    hazeState: HazeState,
    items: List<CMSelectMenuItem>,
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
    touchState: CMMenuTouchState? = null,
    onClosed: (() -> Unit)? = null,
    samplingSource: android.view.ViewGroup? = null
) {
    val touch = touchState ?: rememberCMMenuTouchState()
    // ── 二级菜单状态：锚定父菜单对应行 ──
    var subItems by remember { mutableStateOf<List<CMSelectMenuItem>>(emptyList()) }
    var subAnchor by remember { mutableStateOf<IntRect?>(null) }
    var subVisible by remember { mutableStateOf(false) }
    var subParent by remember { mutableStateOf(-1) }
    BackHandler(enabled = visible) { touch.finish(cancelled = true); onDismiss() }
    LaunchedEffect(touch.hoveredPath) {
        val hovered = touch.hoveredPath
        if (hovered?.size == 1 && items.getOrNull(hovered[0])?.subItems.isNullOrEmpty()) {
            subVisible = false
        }
    }
    // 父菜单被外部关闭时，一并收起二级菜单
    LaunchedEffect(visible) {
        if (!visible) {
            subVisible = false
            touch.finish(cancelled = true)
        }
    }
    val latestDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(touch) {
        val previousFinish = touch.onFinish
        val finish: () -> Unit = { latestDismiss() }
        touch.onFinish = finish
        onDispose {
            if (touch.onFinish === finish) {
                touch.finish(cancelled = true)
                touch.onFinish = previousFinish
            }
        }
    }

    // Group state without emitting an in-page layout node. Menus can be called
    // inside a settings card or scroll column without reserving viewport space.
    key(touch) {
        CMSMenuPopover(
            hazeState = hazeState,
            samplingSource = samplingSource,
            items = items,
            currentIndex = currentIndex,
            type = type,
            anchorRect = anchorRect,
            visible = visible,
            dimBackground = dimBackground,
            dimAmount = dimAmount,
            accentColor = accentColor,
            touch = touch,
            path = emptyList(),
            onClosed = onClosed,
            onSelect = { index ->
                items[index].onClick?.invoke()
                if (onSelectPath != null) onSelectPath(listOf(index)) else onSelect(index)
            },
            onDismiss = {
                onDismiss()
                subVisible = false
            },
            onSubOpen = { index, rowRect ->
                val subs = items.getOrNull(index)?.subItems
                if (subs != null && subs.isNotEmpty()) {
                    subItems = subs
                    subAnchor = rowRect
                    subVisible = true
                    subParent = index
                } else {
                    subVisible = false
                }
            }
        )

        // 二级菜单弹层：无 scrim，基于父菜单对应行的位置
        val anchor = subAnchor
        if (subItems.isNotEmpty() && anchor != null) {
            CMSMenuPopover(
                hazeState = hazeState,
                samplingSource = samplingSource,
                items = subItems,
                currentIndex = 0,
                type = CMSMenuType.Actions,
                anchorRect = anchor,
                visible = subVisible,
                submenu = true,
                accentColor = accentColor,
                touch = touch,
                path = listOf(subParent),
                onSelect = { index ->
                    subItems[index].onClick?.invoke()
                    onSelectPath?.invoke(listOf(subParent, index))
                    subVisible = false
                    onDismiss()
                },
                onDismiss = { subVisible = false }
            )
        }
    }
}

/** 单个菜单弹层（主菜单 / 二级菜单共用）：定位、弹簧、模糊、毛玻璃、滚动 */
@Composable
private fun CMSMenuPopover(
    hazeState: HazeState,
    samplingSource: android.view.ViewGroup? = null,
    items: List<CMSelectMenuItem>,
    currentIndex: Int,
    type: CMSMenuType,
    anchorRect: IntRect,
    visible: Boolean,
    submenu: Boolean = false,
    dimBackground: Boolean = false,
    dimAmount: Float = 0.24f,
    accentColor: Color = Color(0xFFFF5B68),
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSubOpen: ((Int, IntRect) -> Unit)? = null,
    touch: CMMenuTouchState,
    path: List<Int>,
    onClosed: (() -> Unit)? = null
) {
    val latestClosed by rememberUpdatedState(onClosed)
    val windowPosition = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ) = IntOffset.Zero
        }
    }
    val isDark = LocalClearIsDark.current
    val dimAlpha by animateFloatAsState(
        targetValue = if (visible && dimBackground && !submenu) dimAmount.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 220f),
        label = "menuBackgroundDim"
    )
    val shadowStrength by animateFloatAsState(
        targetValue = if (dimBackground && !submenu) 1f else 3f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 220f),
        label = "menuShadowStrength"
    )
    val density = LocalDensity.current
    val textColor = if (isDark) PrimaryText else LightPrimaryText
    val configuration = LocalConfiguration.current
    val windowView = LocalView.current.rootView
    val screenWidthPx = windowView.width.takeIf { it > 0 }?.toFloat()
        ?: with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = windowView.height.takeIf { it > 0 }?.toFloat()
        ?: with(density) { configuration.screenHeightDp.dp.toPx() }
    val safeTopPx = WindowInsets.safeDrawing.getTop(density)
    val safeBottomPx = screenHeightPx.toInt() - WindowInsets.safeDrawing.getBottom(density)

    // 关闭动画期间保留最后一次的内容与选中态：
    // 外部一旦把可见置 false，通常会同时清空 items/currentIndex，
    // 若直接用新值渲染，菜单内容会先消失、只剩空玻璃收缩，看起来像"被卡住"
    var lastItems by remember { mutableStateOf(items) }
    var lastIndex by remember { mutableStateOf(currentIndex) }
    LaunchedEffect(visible, items, currentIndex) {
        if (visible && items.isNotEmpty()) {
            lastItems = items
            lastIndex = currentIndex
        }
    }
    val renderItems = if (items.isNotEmpty()) items else lastItems
    val renderIndex = if (items.isNotEmpty()) currentIndex else lastIndex

    // 保底宽度 132dp：内容更宽时跟随内容，否则保持保底宽度（不会完全贴着内容收缩）。
    // 用 TextMeasurer 预估宽度，保证「定位预估宽度 = 渲染宽度」，避免右对齐/左对齐偏移。
    val textMeasurer = rememberTextMeasurer()
    val menuWidthPx = remember(renderItems, type, textMeasurer) {
        val floorPx = with(density) { 132.dp.roundToPx() }
        val iconPx = with(density) { 16.dp.roundToPx() }
        val gapPx = with(density) { 8.dp.roundToPx() }
        val smallGapPx = with(density) { 6.dp.roundToPx() }
        val badgeMinW = with(density) { 20.dp.roundToPx() }
        val rowPadPx = with(density) { (16 + 16).dp.roundToPx() }
        val containerPadPx = with(density) { (4 + 4).dp.roundToPx() }
        val widest = renderItems.maxOfOrNull { item ->
            val labelW = textMeasurer.measure(
                text = AnnotatedString(item.label),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
            ).size.width
            val shortcutW = item.shortcut?.let {
                textMeasurer.measure(
                    text = AnnotatedString(it),
                    style = TextStyle(fontSize = 12.sp)
                ).size.width
            } ?: 0
            labelW +
                (if (item.icon != null) iconPx + gapPx else 0) +
                (if (item.shortcut != null) gapPx + shortcutW else 0) +
                (if (item.badge != null) smallGapPx + badgeMinW else 0) +
                (if (item.subArrow || !item.subItems.isNullOrEmpty()) gapPx + iconPx else 0)
        } ?: 0
        maxOf(floorPx, widest + rowPadPx + containerPadPx)
    }
    val menuWidthDp = with(density) { menuWidthPx.toDp() }
    val menuHeightPx = with(density) { (renderItems.size * 48 + (renderItems.size - 1) + 8).dp.toPx().toInt() }
    // 超出行数时滚动：菜单实际渲染高度封顶，定位预估也按封顶高度算
    val maxMenuHeightPx = with(density) { (CM_MENU_MAX_VISIBLE_ROWS * 48 + (CM_MENU_MAX_VISIBLE_ROWS - 1) + 8).dp.toPx().toInt() }
    val maxMenuHeightDp = with(density) { maxMenuHeightPx.toDp() }
    val effectiveMenuHeightPx = minOf(menuHeightPx, maxMenuHeightPx)
    val menuGapPx = with(density) { 6.dp.toPx() }.toInt()
    val menuScroll = rememberScrollState()

    var menuOpen by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var menuProgress by remember { mutableFloatStateOf(0f) }
    var menuPlacement by remember { mutableStateOf<CMSelectPlacement?>(null) }

    val scope = rememberCoroutineScope()
    var openCount by remember { mutableIntStateOf(0) }
    val springPtr = remember { CLEARMotion.nativeSpringCreate(0.0, 380.0, 14.0, 1.0, 0.0, 0.05, 0.01) }
    val scaleSpring = remember { CLEARMotion.nativeSpringCreate(0.0, 380.0, 24.0, 1.0, 0.0, 0.05, 0.01) }
    var scaleGate by remember { mutableFloatStateOf(0f) }
    var springX by remember { mutableStateOf(0L) }
    var springY by remember { mutableStateOf(0L) }
    var menuOffsetX by remember { mutableFloatStateOf(0f) }
    var menuOffsetY by remember { mutableFloatStateOf(0f) }
    var startOffsetX by remember { mutableFloatStateOf(0f) }
    var startOffsetY by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        onDispose {
            CLEARMotion.nativeSpringDestroy(springPtr)
            CLEARMotion.nativeSpringDestroy(scaleSpring)
            if (springX != 0L) CLEARMotion.nativeSpringDestroy(springX)
            if (springY != 0L) CLEARMotion.nativeSpringDestroy(springY)
        }
    }

    fun openMenu() {
        if (menuVisible) return
        if (menuOpen) {
            menuVisible = true
            CLEARMotion.nativeSpringSetTarget(springPtr, 1.0)
            CLEARMotion.nativeSpringSetTarget(scaleSpring, 1.0)
            if (springX != 0L) CLEARMotion.nativeSpringSetTarget(springX, 0.0)
            if (springY != 0L) CLEARMotion.nativeSpringSetTarget(springY, 0.0)
            return
        }
        val insetPx = with(density) { 6.dp.toPx() }.toInt()
        // 平板宽屏：内容限宽居中后触发点常位于屏幕右侧区域，
        // 菜单仍按"向右自适应"会向左拉得很远且远离触发物 —— 此时应向左展开（右对齐触发物右缘）
        val isTablet = configuration.screenWidthDp >= TABLET_MIN_DP
        val anchorCenterX = anchorRect.left + (anchorRect.right - anchorRect.left) / 2
        val anchorCenterY = anchorRect.top + (anchorRect.bottom - anchorRect.top) / 2
        // Keep lower-screen choices above the contact even when there is room
        // below it. Existing controls may sit underneath this window overlay.
        val prefersUp = anchorCenterY >= screenHeightPx * (2f / 3f)
        val maxTop = (safeBottomPx - effectiveMenuHeightPx).coerceAtLeast(safeTopPx)
        val prefersLeft = isTablet && anchorCenterX > screenWidthPx / 2
        val x: Int
        val y: Int
        val origin: TransformOrigin
        val startY: Double
        if (submenu) {
            // 二级菜单：紧贴父菜单对应行，水平展开（右优先，平板右半区则左），垂直与该行对齐
            val openRight = !prefersLeft && (anchorRect.right + menuWidthPx + menuGapPx) <= screenWidthPx
            val fitsDown = !prefersUp &&
                (anchorRect.top + effectiveMenuHeightPx + menuGapPx) <= safeBottomPx
            x = if (openRight) anchorRect.right + menuGapPx else (anchorRect.left - menuWidthPx - menuGapPx).coerceAtLeast(0)
            y = (if (fitsDown) anchorRect.top else anchorRect.bottom - effectiveMenuHeightPx)
                .coerceIn(safeTopPx, maxTop)
            origin = TransformOrigin(if (openRight) 0f else 1f, if (fitsDown) 0f else 1f)
            // 垂直与该行对齐，水平从该行位置飞出
            startY = (anchorRect.top - y).toDouble()
        } else {
            // 自适应定位：向左/上不足则反向展开，定位角与方向一致；
            // 两侧都略微向中间内缩 inset，不贴死触发物边缘
            val openRight = !prefersLeft && (anchorRect.left + menuWidthPx) <= screenWidthPx
            val openDown = !prefersUp &&
                (anchorRect.bottom + effectiveMenuHeightPx + menuGapPx) <= safeBottomPx
            x = if (openRight) (anchorRect.left + insetPx) else (anchorRect.right - menuWidthPx - insetPx).coerceAtLeast(0)
            y = (if (openDown) anchorRect.bottom + menuGapPx else anchorRect.top - effectiveMenuHeightPx - menuGapPx)
                .coerceIn(safeTopPx, maxTop)
            origin = TransformOrigin(if (openRight) 0f else 1f, if (openDown) 0f else 1f)
            // 起点 = 触发元素位置：
            // 向下展开 → 菜单顶边从触发物上缘出发，向下生长到位（原点在顶部）
            // 向上展开 → 菜单底边贴触发物上缘出发，向上生长到位（原点在底部，y 轴尺寸变化朝上）
            startY = if (openDown) (anchorRect.top - y).toDouble()
                else (anchorRect.top - y - effectiveMenuHeightPx).toDouble()
        }
        val startX = (anchorRect.left - x).toDouble()
        menuPlacement = CMSelectPlacement(x, y, origin)

        if (springX != 0L) CLEARMotion.nativeSpringDestroy(springX)
        if (springY != 0L) CLEARMotion.nativeSpringDestroy(springY)
        val xSpring = CLEARMotion.nativeSpringCreate(startX, 340.0, 16.0, 1.0, 0.0, 0.05, 0.01)
        val ySpring = CLEARMotion.nativeSpringCreate(startY, 640.0, 24.0, 1.0, 0.0, 0.05, 0.01)
        springX = xSpring
        springY = ySpring
        CLEARMotion.nativeSpringSetTarget(ySpring, 0.0)
        scope.launch {
            delay(70)
            if (menuVisible && springX == xSpring) CLEARMotion.nativeSpringSetTarget(xSpring, 0.0)
        }
        CLEARMotion.nativeSpringSetTarget(scaleSpring, 0.0)
        val token = ++openCount
        scope.launch {
            delay(60)
            if (menuVisible && openCount == token) CLEARMotion.nativeSpringSetTarget(scaleSpring, 1.0)
        }
        startOffsetX = startX.toFloat()
        startOffsetY = startY.toFloat()
        menuOffsetX = startX.toFloat()
        menuOffsetY = startY.toFloat()
        menuVisible = true
        CLEARMotion.nativeSpringSetTarget(springPtr, 1.0)
        menuOpen = true
    }

    fun closeMenu() {
        if (!menuVisible) return
        openCount++
        menuVisible = false
        CLEARMotion.nativeSpringSetTarget(springPtr, 0.0)
        CLEARMotion.nativeSpringSetTarget(scaleSpring, 0.0)
    }

    LaunchedEffect(visible) {
        if (visible) openMenu() else closeMenu()
    }

    LaunchedEffect(menuOpen, menuVisible) {
        if (!menuOpen) return@LaunchedEffect
        var lastFrame = 0L
        while (true) {
            withFrameNanos { ns ->
                val dt = if (lastFrame == 0L) 1f / 60f
                else ((ns - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastFrame = ns
                CLEARMotion.nativeSpringUpdate(springPtr, dt.toDouble())
                menuProgress = CLEARMotion.nativeSpringGetValue(springPtr).toFloat()
                CLEARMotion.nativeSpringUpdate(scaleSpring, dt.toDouble())
                scaleGate = CLEARMotion.nativeSpringGetValue(scaleSpring).toFloat()
                if (springX != 0L) {
                    CLEARMotion.nativeSpringUpdate(springX, dt.toDouble())
                    menuOffsetX = CLEARMotion.nativeSpringGetValue(springX).toFloat()
                }
                if (springY != 0L) {
                    CLEARMotion.nativeSpringUpdate(springY, dt.toDouble())
                    menuOffsetY = CLEARMotion.nativeSpringGetValue(springY).toFloat()
                }
            }
            // 压暗层也收敛后再销毁浮层，避免背景在退场末尾突然恢复。
            if (!menuVisible && menuProgress < 0.02f && dimAlpha < 0.001f) break
            if (menuVisible &&
                !CLEARMotion.nativeSpringIsRunning(springPtr) &&
                !CLEARMotion.nativeSpringIsRunning(scaleSpring) &&
                (springX == 0L || !CLEARMotion.nativeSpringIsRunning(springX)) &&
                (springY == 0L || !CLEARMotion.nativeSpringIsRunning(springY))) {
                return@LaunchedEffect
            }
        }
        menuOpen = false
        latestClosed?.invoke()
    }

    val placement = menuPlacement
    if (menuOpen && placement != null) {
        var panelCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
        val menuPointer = remember { booleanArrayOf(false) }
        val inheritedPointer = remember { booleanArrayOf(false) }
        val panelKey = remember { Any() }
        val scrollSession = remember { booleanArrayOf(false) }
        val lastPoint = remember { arrayOf(Offset.Zero) }
        val dragBridge = remember { CMVerticalDragBridge() }
        val pointerVelocity = remember { VelocityTracker() }
        val gesture = remember(density.density, renderItems.size) {
            CMMenuGesture(density.density, renderItems.size > CM_MENU_MAX_VISIBLE_ROWS)
        }
        fun beginScrollSession() {
            if (!scrollSession[0]) {
                scrollSession[0] = true
                dragBridge.begin()
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                touch.removePanel(panelKey)
                dragBridge.cancel()
            }
        }
        val localTouch = if (touch.active && touch.position.isSpecified) {
            panelCoordinates?.takeIf { it.isAttached }?.windowToLocal(touch.position)
        } else null
        val followsTouch = localTouch != null &&
            localTouch.x in 0f..menuWidthPx.toFloat() &&
            localTouch.y in 0f..effectiveMenuHeightPx.toFloat()
        var feedbackGesture by remember { mutableLongStateOf(-1L) }
        fun selectionAtEdge(point: Offset): Boolean {
            val local = panelCoordinates?.takeIf { it.isAttached }?.windowToLocal(point) ?: return true
            return cmMenuEdgeDirection(local, menuWidthPx.toFloat(), effectiveMenuHeightPx.toFloat(),
                with(density) { 28.dp.toPx() }, with(density) { 96.dp.toPx() }) != 0f ||
                local.x !in 0f..menuWidthPx.toFloat() || local.y !in 0f..effectiveMenuHeightPx.toFloat()
        }
        SideEffect {
            if (followsTouch) feedbackGesture = touch.gestureId
        }
        val retainFeedback = touch.active && (followsTouch || feedbackGesture == touch.gestureId)
        SideEffect {
            val bounds = panelCoordinates?.takeIf { it.isAttached }?.boundsInWindow()
            if (visible && bounds != null) {
                touch.registerPanel(panelKey, path.size, bounds,
                    begin = { point ->
                        dragBridge.interrupt()
                        inheritedPointer[0] = true
                        feedbackGesture = touch.gestureId
                        scrollSession[0] = false
                        lastPoint[0] = point
                        val time = android.os.SystemClock.uptimeMillis()
                        gesture.begin(point, time, false, selecting = true,
                            confirmedSelection = true)
                        pointerVelocity.resetTracking()
                        pointerVelocity.addPosition(time, point)
                        touch.updateSelection(true)
                    },
                    move = { point ->
                        val time = android.os.SystemClock.uptimeMillis()
                        val delta = point - lastPoint[0]
                        pointerVelocity.addPosition(time, point)
                        gesture.move(point, time,
                            selectionEdge = selectionAtEdge(point) || selectionAtEdge(lastPoint[0]))
                        touch.updateSelection(gesture.selectionAllowed)
                        if (gesture.phase == CMMenuGesturePhase.Scrolling) {
                            beginScrollSession()
                            dragBridge.drag(delta.y)
                        }
                        lastPoint[0] = point
                    },
                    end = { cancelled ->
                        if (scrollSession[0]) dragBridge.end(
                            if (!cancelled && gesture.phase == CMMenuGesturePhase.Scrolling)
                                pointerVelocity.calculateVelocity().y else 0f
                        )
                        inheritedPointer[0] = false
                        scrollSession[0] = false
                    }, captureInherited = path.isEmpty())
                if (path.isEmpty()) touch.primeInitialHover(path + renderIndex)
            } else touch.removePanel(panelKey)
        }
        LaunchedEffect(touch.active, visible) {
            if (!touch.active || !visible) {
                return@LaunchedEffect
            }
            var lastFrame = 0L
            while (touch.active) {
                val now = withFrameNanos { it }
                val dt = if (lastFrame == 0L) 0f else
                    ((now - lastFrame) / 1_000_000_000f).coerceAtMost(0.032f)
                lastFrame = now
                if (menuPointer[0] || inheritedPointer[0]) {
                    gesture.advance(android.os.SystemClock.uptimeMillis(),
                        scrollHoldBlocked = dragBridge.selectionHoldBlocked(),
                        selectionEdge = selectionAtEdge(touch.position))
                    touch.updateSelection(gesture.selectionAllowed)
                }
                val point = panelCoordinates?.takeIf { it.isAttached }
                    ?.windowToLocal(touch.position) ?: continue
                // A short tap near the edge must not scroll the row out from
                // under the finger before its release.
                if (renderItems.size <= CM_MENU_MAX_VISIBLE_ROWS || !touch.selecting ||
                    (!menuPointer[0] && !inheritedPointer[0]) ||
                    gesture.phase != CMMenuGesturePhase.Selecting) continue
                val edge = with(density) { 28.dp.toPx() }
                val direction = cmMenuEdgeDirection(point, menuWidthPx.toFloat(),
                    effectiveMenuHeightPx.toFloat(), edge, with(density) { 96.dp.toPx() })
                if (direction != 0f) {
                    val scroll = cmMenuAutoScrollDelta(
                        direction * with(density) { 160.dp.toPx() } * dt,
                        menuScroll.value, menuScroll.maxValue
                    )
                    if (scroll == 0f) continue
                    beginScrollSession()
                    dragBridge.drag(-scroll)
                }
            }
        }
        // Animation values are read in drawing/layers, keeping rows and Haze
        // out of the per-frame composition path.
        // The scrim and animated panel live outside both page layout and its
        // sampling tree. Retain the window until the exit spring has settled.
        Popup(
            popupPositionProvider = windowPosition,
            onDismissRequest = {
                onDismiss()
                closeMenu()
            },
            properties = PopupProperties(focusable = false, clippingEnabled = false)
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (submenu) {
                // 二级菜单：透明拦截，点击任意处仅收起二级菜单
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(menuVisible) {
                            if (menuVisible) {
                                detectTapGestures { onDismiss() }
                            }
                        }
                )
            } else {
                // 主菜单 scrim：仅打开时拦截
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(Color.Black.copy(alpha = dimAlpha.coerceIn(0f, 1f)))
                        }
                        .pointerInput(menuVisible) {
                            if (menuVisible) {
                                detectTapGestures {
                                    onDismiss()
                                    closeMenu()
                                }
                            }
                        }
                )
            }

            CMBlurLayer(
                modifier = Modifier
                    .offset { IntOffset(placement.x, placement.y) }
                    .width(menuWidthDp)
                    .graphicsLayer {
                        translationX = menuOffsetX
                        translationY = menuOffsetY
                        val gate = scaleGate.coerceAtLeast(0f)
                        scaleX = 0.9f + 0.1f * gate
                        scaleY = 0.5f + 0.5f * gate
                        alpha = (menuProgress / 0.5f).coerceIn(0f, 1f)
                        transformOrigin = placement.origin
                        clip = false
                    },
                maxRadius = 14.dp,
                radius = { 14.dp * (1f - (menuProgress * 2f).coerceIn(0f, 1f)) }
            ) {
            CMSGlassButton(
                onClick = {},
                width = menuWidthDp,
                height = with(density) { effectiveMenuHeightPx.toDp() },
                cornerRadius = 20.dp,
                highlightMaxWidth = 0.8.dp,
                sampleContent = true,
                samplingSource = samplingSource,
                hazeState = hazeState,
                menuSurface = true,
                shadowStrength = shadowStrength,
                interactiveContent = true,
                touchEffectIntensity = 0.12f,
                touchEffectRadius = 180.dp,
                externalTouchFeedback = true,
                feedbackPosition = if (retainFeedback) touch.feedbackPoint() else null,
                onTouch = { point, action ->
                    when (action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            val interrupted = dragBridge.interrupt()
                            menuPointer[0] = true
                            scrollSession[0] = false
                            lastPoint[0] = point
                            val time = android.os.SystemClock.uptimeMillis()
                            gesture.begin(point, time, interrupted)
                            pointerVelocity.resetTracking()
                            pointerVelocity.addPosition(time, point)
                            touch.begin(point, allowSelection = !interrupted)
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (menuPointer[0]) {
                                val delta = point - lastPoint[0]
                                val time = android.os.SystemClock.uptimeMillis()
                                pointerVelocity.addPosition(time, point)
                                gesture.move(point, time,
                                    selectionEdge = selectionAtEdge(point) || selectionAtEdge(lastPoint[0]))
                                touch.updateSelection(gesture.selectionAllowed)
                                touch.move(point)
                                if (gesture.phase == CMMenuGesturePhase.Scrolling) {
                                    beginScrollSession()
                                    dragBridge.drag(delta.y)
                                }
                                lastPoint[0] = point
                            }
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            if (menuPointer[0] && touch.active) {
                                pointerVelocity.addPosition(android.os.SystemClock.uptimeMillis(), point)
                                if (scrollSession[0]) dragBridge.end(
                                    if (gesture.phase == CMMenuGesturePhase.Scrolling)
                                        pointerVelocity.calculateVelocity().y else 0f
                                )
                                touch.move(point)
                                touch.finish()
                            }
                            menuPointer[0] = false
                            scrollSession[0] = false
                        }
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            if (scrollSession[0]) dragBridge.cancel()
                            if (menuPointer[0]) touch.finish(cancelled = true, dismiss = false)
                            menuPointer[0] = false
                            scrollSession[0] = false
                        }
                    }
                    true
                },
                glassBackground = if (isDark) {
                    Color(0xFF1E232A).copy(alpha = if (LocalGlassMaterial.current.clarity == 1) 0.62f else 0.5f)
                } else {
                    Color.White.copy(alpha = if (LocalGlassMaterial.current.clarity == 1) 0.58f else 0.45f)
                },
                modifier = Modifier
                    .onGloballyPositioned { panelCoordinates = it }
            ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(4.dp)
            ) {
                val onPick: (Int) -> Unit = { index ->
                    onSelect(index)
                    closeMenu()
                }
                // 超过 4 行：封顶高度 + CMScrollBox 滚动（自带侧边滚动条）
                if (renderItems.size > CM_MENU_MAX_VISIBLE_ROWS) {
                    CMScrollBox(
                        sampleGlassContent = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(maxMenuHeightDp),
                        showScrollbar = true,
                        scrollState = menuScroll,
                        axisDragBridge = dragBridge,
                    ) {
                        CMSMenuItems(
                            renderItems, renderIndex, type, isDark, textColor,
                            accentColor, onPick, onSubOpen, touch, path, visible
                        )
                    }
                } else {
                    CMSMenuItems(
                        renderItems, renderIndex, type, isDark, textColor,
                        accentColor, onPick, onSubOpen, touch, path, visible
                    )
                }
            }
            }
            }
        }
        }
    }
}

/** 菜单行列表：选择 / 操作 / 排序筛选 三种类型通用渲染；带二级菜单的行锚定自身位置 */
@Composable
private fun ColumnScope.CMSMenuItems(
    items: List<CMSelectMenuItem>,
    currentIndex: Int,
    type: CMSMenuType,
    isDark: Boolean,
    textColor: Color,
    accentColor: Color,
    onSelect: (Int) -> Unit,
    onSubOpen: ((Int, IntRect) -> Unit)? = null,
    touch: CMMenuTouchState,
    path: List<Int>,
    visible: Boolean
) {
    items.forEachIndexed { index, item ->
        if (index > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(0.5.dp)
                    .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f))
            )
        }
        val selected = type == CMSMenuType.Select && index == currentIndex
        val hasSub = !item.subItems.isNullOrEmpty()
        var rowRect by remember(index, item.label) { mutableStateOf<IntRect?>(null) }
        val targetKey = remember { Any() }
        val itemPath = path + index
        val latestPick by rememberUpdatedState(onSelect)
        val latestSub by rememberUpdatedState(onSubOpen)
        DisposableEffect(touch, targetKey, visible, itemPath) {
            onDispose { touch.remove(targetKey) }
        }
        SideEffect {
            val rect = rowRect
            if (visible && rect != null) {
                touch.register(targetKey, itemPath, androidx.compose.ui.geometry.Rect(
                    rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()
                ), item.disabled, if (hasSub) ({
                    latestSub?.invoke(index, rect)
                }) else null, { latestPick(index) })
            } else touch.remove(targetKey)
        }
        val hovered = touch.active && touch.hoveredPath == itemPath && !item.disabled
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onGloballyPositioned { coords ->
                    rowRect = coords.boundsInWindow().let { r ->
                        if (visible) touch.register(
                            targetKey, itemPath, r, item.disabled,
                            if (hasSub) ({
                                latestSub?.invoke(index, IntRect(r.left.toInt(), r.top.toInt(),
                                    r.right.toInt(), r.bottom.toInt()))
                            }) else null,
                            { latestPick(index) }
                        )
                        IntRect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                    }
                }
                // 同心圆角：菜单容器 20dp，内边距 4dp → 行圆角 20-4=16dp
                .clip(ContinuousRoundedCornerShape(16.dp))
                .background(when {
                    hovered -> accentColor.copy(alpha = 0.2f)
                    selected -> accentColor.copy(alpha = 0.15f)
                    else -> Color.Transparent
                })
                .clickable(
                    enabled = !item.disabled,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    val rect = rowRect
                    if (hasSub && rect != null && onSubOpen != null) {
                        onSubOpen(index, rect)
                    } else {
                        onSelect(index)
                    }
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 操作菜单：图标
            if (item.icon != null) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when {
                        item.disabled -> TertiaryText
                        item.danger -> Color(0xFFEF4444)
                        else -> TertiaryText
                    }
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = item.label,
                fontSize = 14.sp,
                maxLines = 1,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = when {
                    item.disabled -> TertiaryText
                    item.danger -> Color(0xFFEF4444)
                    selected -> accentColor
                    else -> textColor
                },
                modifier = Modifier.weight(1f)
            )
            // 排序筛选：数字徽章
            if (item.badge != null) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .defaultMinSize(minWidth = 20.dp)
                        .clip(ContinuousRoundedCornerShape(12.dp))
                        .background(accentColor)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.badge, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            // 操作菜单：快捷键
            if (item.shortcut != null) {
                Spacer(Modifier.width(8.dp))
                Text(item.shortcut, fontSize = 12.sp, color = TertiaryText, maxLines = 1)
            }
            // 子菜单箭头
            if (item.subArrow || hasSub) {
                Spacer(Modifier.width(8.dp))
                Text("›", fontSize = 14.sp, color = TertiaryText, maxLines = 1)
            }
        }
    }
}
