package com.lindicra.cms

import android.os.Build
import android.view.ViewGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import com.lindicra.cms.ui.theme.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CMSGlassTabItem(val label: String, val icon: ImageVector? = null)

object CMSGlassTabBarDefaults {
    val items = listOf(
        CMSGlassTabItem("健康", Icons.Default.Favorite),
        CMSGlassTabItem("运动", Icons.Default.DirectionsRun),
        CMSGlassTabItem("设备", Icons.Default.Watch),
        CMSGlassTabItem("我的", Icons.Default.Person)
    )
}

/** Resting geometry shares one inset on all four sides, including the end tabs. */
internal data class CMTabGeometry(
    val width: Float, val height: Float, val count: Int, val inset: Float,
    val overlap: Float = 0f, val rtl: Boolean = false
) {
    val slot get() = (width - 2f * inset - overlap) / count
    val indicatorWidth get() = slot + overlap
    val indicatorHeight get() = height - 2f * inset
    fun center(index: Int) = center(index.toFloat())
    fun center(progress: Float) = inset + indicatorWidth / 2f +
        slot * (if (rtl) count - 1f - progress else progress)
    fun progress(center: Float): Float {
        val physical = (center - inset - indicatorWidth / 2f) / slot
        return (if (rtl) count - 1f - physical else physical).coerceIn(0f, count - 1f)
    }
    fun landing(center: Float): Int {
        val physical = floor((center - inset - overlap / 2f) / slot)
            .toInt().coerceIn(0, count - 1)
        return if (rtl) count - 1 - physical else physical
    }
    fun release(initialIndex: Int, lensCenter: Float, cancelled: Boolean): Pair<Int, Boolean> =
        if (cancelled) initialIndex to false else landing(lensCenter) to true
    fun follow(x: Float): Float = x.coerceIn(
        minOf(center(0), center(count - 1)), maxOf(center(0), center(count - 1)))
    fun renderedCenter(x: Float, renderedWidth: Float): Float {
        val half = renderedWidth.coerceIn(indicatorWidth, width) / 2f
        return x.coerceIn(half, width - half)
    }
}

/**
 * Floating bottom navigation. Host owns pages, safe-area placement and selection.
 * Pass a content-only sampling source; never bind the bar or its parent as the source.
 */
@Composable
fun CMSGlassTabBar(
    items: List<CMSGlassTabItem>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    indicatorInset: Dp = 4.dp,
    pressExpansion: Dp = 20.dp,
    iconSize: Dp = 24.dp,
    labelSize: TextUnit = 12.sp,
    showLabels: Boolean = true,
    selectedColor: Color = LocalCMSAccentColor.current,
    unselectedColor: Color = SecondaryText,
    indicatorColor: Color = if (LocalClearIsDark.current) Color(0xB31C1C1E)
        else Color(0x388E8E93),
    liquidMotion: Boolean = true,
    enabled: Boolean = true,
    samplingSource: ViewGroup? = null,
    hazeState: HazeState? = null,
    fillWidth: Boolean = true,
    itemWidth: Dp = 88.dp,
    itemOverlap: Dp = 16.dp,
    pageProgress: Float? = null,
    draggable: Boolean = true,
    cornerRadius: Dp = height / 2,
    indicatorRadius: Dp = (height - indicatorInset * 2) / 2,
    containerColor: Color = Color.Unspecified,
    labelWeight: FontWeight = FontWeight.SemiBold,
    pageTouchVersion: Int = 0
) {
    require(items.size in 2..5) { "底栏支持 2 至 5 项" }
    require(selectedIndex in items.indices) { "selectedIndex 超出底栏范围" }
    val textOnly = items.all { it.icon == null }
    require(height >= (if (textOnly) 36.dp else 44.dp) &&
        indicatorInset >= 0.dp && indicatorInset * 2 < height) {
        "图文底栏高度至少 44dp，纯文字至少 36dp，内间距必须小于高度的一半"
    }
    require(pressExpansion >= 0.dp) { "按下扩张不能为负数" }
    require(itemOverlap >= 0.dp && itemOverlap < minOf(itemWidth, height) / 2) {
        "页签重叠量必须非负，且小于页签宽度和底栏高度的一半"
    }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val externalPosition = pageProgress?.takeIf { it.isFinite() }
        ?.coerceIn(0f, items.lastIndex.toFloat())
    val pagerPosition by rememberUpdatedState(externalPosition)
    val windowView = LocalView.current
    var barScreenPosition by remember { mutableStateOf<Offset?>(null) }
    var barCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var contactWindowPosition by remember { mutableStateOf<Offset?>(null) }
    val scope = rememberCoroutineScope()
    val material = LocalGlassMaterial.current
    val optical = material.optical && Build.VERSION.SDK_INT >= 33
    val dark = LocalClearIsDark.current
    val haptic = rememberCMSHaptic()
    val callback by rememberUpdatedState(onSelectedChange)
    val selection by rememberUpdatedState(selectedIndex)
    val selectedTint by androidx.compose.animation.animateColorAsState(
        selectedColor, androidx.compose.animation.core.tween(220), label = "glassTabSelectedTint")
    val unselectedTint by androidx.compose.animation.animateColorAsState(
        unselectedColor, androidx.compose.animation.core.tween(220), label = "glassTabUnselectedTint")
    val textOpacity by androidx.compose.animation.core.animateFloatAsState(
        if (enabled) 1f else 0.4f, androidx.compose.animation.core.tween(220), label = "glassTabOpacity")
    val pressScale = remember { CMProgressMotion(1f) }
    var pressed by remember { mutableStateOf(false) }
    var releasing by remember { mutableStateOf(false) }
    var pagerHandoff by remember { mutableStateOf(false) }
    // 整体共用一个缩放层，玻璃、镜片与文字一起轻微抬起。
    LaunchedEffect(pressed, enabled) {
        pressScale.animateTo(
            if (pressed && enabled) 1.015f else 1f,
            scope, stiffness = 180f, damping = 0.35f
        )
    }
    DisposableEffect(pressScale) {
        onDispose { pressScale.follow(1f) }
    }
    var gestureToken by remember { mutableIntStateOf(0) }
    var hovering by remember { mutableIntStateOf(selectedIndex) }
    var drivenSelection by remember { mutableIntStateOf(selectedIndex) }
    val lift = remember { CMProgressMotion(0f) }
    var dropping by remember { mutableStateOf(false) }
    var landingDirection by remember { mutableFloatStateOf(0f) }
    val fluid = remember { CMTabFluid() }
    var strain by remember { mutableFloatStateOf(0f) }
    var shear by remember { mutableFloatStateOf(0f) }
    var barDrift by remember { mutableFloatStateOf(0f) }
    var pressAnchor by remember { mutableFloatStateOf(0f) }
    var fluidVelocity by remember { mutableFloatStateOf(0f) }
    var fluidAcceleration by remember { mutableFloatStateOf(0f) }
    var requestedCenter by remember { mutableFloatStateOf(0f) }
    @Composable
    fun TabContents(progress: Float) {
        Row(Modifier.fillMaxSize().selectableGroup().padding(horizontal = indicatorInset),
            horizontalArrangement = Arrangement.spacedBy(-itemOverlap)) {
            items.forEachIndexed { index, item ->
                val emphasis = (1f - abs(progress - index)).coerceIn(0f, 1f)
                val tint = lerp(unselectedTint, selectedTint, emphasis)
                val color = tint.copy(alpha = tint.alpha * textOpacity)
                Column(Modifier.weight(1f).fillMaxHeight().semantics {
                    role = Role.Tab
                    selected = index == selectedIndex
                    contentDescription = item.label
                    if (enabled) onClick {
                        haptic(CMSHapticKind.Click)
                        if (index != selection) callback(index)
                        true
                    } else disabled()
                }, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    item.icon?.let {
                        Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
                    }
                    if (showLabels) {
                        if (item.icon != null) Spacer(Modifier.height(3.dp))
                        CMSNavigationLabel(item.label, emphasis, color, labelSize, labelWeight)
                    }
                }
            }
        }
    }
    // Fill the host by default, or size to the items so a two- or three-tab bar stays a
    // compact capsule instead of a full-width slab with empty ends.
    val adaptiveWidth = if (fillWidth) Modifier.fillMaxWidth()
        else Modifier.width(indicatorInset * 2 + itemWidth * items.size - itemOverlap * (items.size - 1))
    BoxWithConstraints(
        modifier.height(height).then(adaptiveWidth).graphicsLayer {
            clip = false
            scaleX = pressScale.value
            scaleY = pressScale.value
        }
        .onGloballyPositioned { coordinates ->
            val screen = IntArray(2)
            val window = IntArray(2)
            windowView.getLocationOnScreen(screen)
            windowView.getLocationInWindow(window)
            barCoordinates = coordinates
            barScreenPosition = coordinates.positionInWindow() +
                Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
        }) {
        val width = maxWidth
        require(width.value.isFinite() && width > indicatorInset * 2 + itemOverlap) { "底栏需要有限宽度" }
        val geometry = remember(width, height, indicatorInset, itemOverlap, items.size, density, rtl) {
            with(density) {
                CMTabGeometry(width.toPx(), height.toPx(), items.size, indicatorInset.toPx(),
                    itemOverlap.toPx(), rtl)
            }
        }
        val center = remember { CMProgressMotion(geometry.center(selectedIndex)) }
        var handledPageTouch by remember { mutableIntStateOf(pageTouchVersion) }
        val pageInterrupted = pageTouchVersion != handledPageTouch && !pressed
        val followingPage = externalPosition != null && !pressed &&
            (pageInterrupted || !releasing && !pagerHandoff)
        var wasExternallyDriven by remember { mutableStateOf(externalPosition != null) }
        LaunchedEffect(geometry) {
            center.follow(geometry.center(externalPosition ?: selection.toFloat()))
        }
        // Content swipes follow the pager directly. A bar gesture owns its complete
        // flight and landing; the page animation must not retarget that spring.
        SideEffect {
            if (pageTouchVersion != handledPageTouch) {
                handledPageTouch = pageTouchVersion
                if (!pressed) {
                    ++gestureToken
                    releasing = false
                    pagerHandoff = false
                    dropping = false
                    landingDirection = 0f
                    fluid.endReleaseRecovery()
                    lift.animateTo(0f, scope, stiffness = 180f, damping = 0.9f)
                    center.follow(geometry.center(externalPosition ?: selection.toFloat()))
                }
            }
            if (followingPage && externalPosition != null) {
                center.follow(geometry.center(externalPosition))
            }
        }
        LaunchedEffect(externalPosition, selectedIndex, geometry, pressed, releasing, pagerHandoff) {
            if (pagerHandoff && !pressed && !releasing) {
                val token = gestureToken
                center.animateTo(geometry.center(externalPosition ?: selection.toFloat()), scope,
                    stiffness = 460f, damping = 0.95f)
                center.awaitSettled()
                if (token == gestureToken && !pressed) pagerHandoff = false
            }
        }
        LaunchedEffect(selectedIndex, geometry, externalPosition == null, releasing) {
            if (!pressed && !releasing && externalPosition == null) {
                if (selectedIndex != drivenSelection || wasExternallyDriven) {
                    drivenSelection = selectedIndex
                    hovering = selectedIndex
                    center.animateTo(geometry.center(selectedIndex), scope)
                }
                wasExternallyDriven = false
            } else if (externalPosition != null) {
                wasExternallyDriven = true
            }
        }
        var sampledCenter by remember(geometry) { mutableFloatStateOf(center.value) }
        LaunchedEffect(geometry, liquidMotion, optical) {
            while (true) {
                snapshotFlow { pressed || abs(center.value - sampledCenter) > 0.01f ||
                    abs(center.velocity) > 0.01f || abs(lift.velocity) > 0.001f }
                    .first { it }
                var previous = withFrameNanos { it }
                var previousCenter = sampledCenter
                var previousVelocity = 0f
                do {
                    val now = withFrameNanos { it }
                    val dt = ((now - previous) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                    val growth = with(density) { pressExpansion.toPx() } *
                        lift.value.coerceIn(-0.3f, 1.3f)
                    val bodyWidth = (geometry.indicatorWidth + growth).coerceAtLeast(1f)
                    val boundWidth = tabFluidWidth(bodyWidth, strain, shear).coerceAtMost(geometry.width)
                    val rendered = geometry.renderedCenter(center.value, boundWidth)
                    val rawVelocity = (rendered - previousCenter) / dt / geometry.indicatorWidth
                    // Frame-derived velocity is noisy and its derivative is worse: unfiltered,
                    // acceleration flips sign every frame and the lens twitches. Both inputs are
                    // low-passed and the acceleration is bounded before it reaches the fluid.
                    fluidVelocity += (rawVelocity - fluidVelocity) * 0.35f
                    val rawAcceleration = (fluidVelocity - previousVelocity) / dt
                    fluidAcceleration += (rawAcceleration - fluidAcceleration) * 0.2f
                    val contact = ((if (pressed)
                        requestedCenter - geometry.follow(requestedCenter) else 0f) +
                        (center.value - rendered)) / geometry.indicatorWidth
                    fluid.step(dt, if (liquidMotion && optical) fluidVelocity else 0f,
                        if (liquidMotion && optical) fluidAcceleration.coerceIn(-45f, 45f) else 0f,
                        if (liquidMotion && optical) contact else 0f,
                        // 落下的解析弹簧速度驱动竖向惯性，不对高度做帧间差分。
                        fallVelocity = if (dropping && liquidMotion && optical)
                            lift.velocity * with(density) { pressExpansion.toPx() } /
                                geometry.indicatorHeight else 0f,
                        landingContact = if (dropping) 1f - lift.value.coerceIn(0f, 1f) else 0f,
                        landingDirection = if (dropping && liquidMotion && optical)
                            landingDirection else 0f)
                    strain = fluid.strain
                    shear = fluid.shear
                    // The whole bar drifts with the hand, not with the lens' distance from the
                    // selected tab: that made a press on a far tab shove the bar across.
                    val driftTarget = if (pressed) {
                        ((requestedCenter - pressAnchor) * 0.05f)
                            .coerceIn(-geometry.indicatorHeight * 0.12f, geometry.indicatorHeight * 0.12f)
                    } else 0f
                    barDrift += (driftTarget - barDrift) * 0.22f
                    previousCenter = rendered
                    sampledCenter = rendered
                    previousVelocity = fluidVelocity
                    previous = now
                } while (pressed || abs(center.velocity) > 0.01f ||
                    abs(lift.velocity) > 0.001f || abs(fluidVelocity) > 0.001f || !fluid.settled)
                strain = 0f
                shear = 0f
                fluidVelocity = 0f
                fluidAcceleration = 0f
                barDrift = 0f
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                center.follow(center.value); lift.follow(lift.value)
            }
        }
        var foregroundSource by remember { mutableStateOf<ViewGroup?>(null) }
        var underlaySource by remember { mutableStateOf<ViewGroup?>(null) }
        val underlayHaze = remember { HazeState() }
        val amount = lift.value.coerceIn(0f, 1f)
        // At rest the tint and the labels live above the glass, so the bar reads as
        // a capsule with text on it. Once the lens lifts, the labels have to pass
        // under its surface instead: the lens owns the layer above them from the
        // moment the cross-fade is half-way, which keeps the swap seamless.
        val lensOnTop = lift.value > 0.7f
        val growth = with(density) { pressExpansion.toPx() } * lift.value.coerceIn(-0.3f, 1.3f)
        // 抬起量控制基础大小；下落速度通过面积守恒的流体模式产生压扁和回弹。
        val bodyWidth = (geometry.indicatorWidth + growth).coerceAtLeast(1f)
            .coerceAtMost(geometry.width / (exp(strain) * (1f + 0.24f * abs(shear))))
        val bodyHeight = (geometry.indicatorHeight + growth).coerceAtLeast(1f)
        val displayCenter = if (followingPage && externalPosition != null)
            geometry.center(externalPosition) else center.value
        val rendered = geometry.renderedCenter(displayCenter, tabFluidWidth(bodyWidth, strain, shear))
        // The bar follows the lens through the smoothed [barDrift] state above, so it can
        // never snap or wander on its own.
        val contentDrift = barDrift
        val driftModifier = Modifier.offset { IntOffset(contentDrift.roundToInt(), 0) }
        // The capsule is the glass the lens floats on, so it is the lens' sampling source:
        // the edge has to bend the bar itself, not an empty page. This container is pure
        // sampling scaffolding, so it must stay transparent: painting a background here
        // showed as a black rectangle behind the capsule's rounded corners.
        Box(Modifier.fillMaxSize().zIndex(0f).then(driftModifier)) {
            CMSGlassContentSource(Modifier.align(Alignment.Center).width(width).height(height),
                sourceTag = "cms_tab_bar_underlay", backgroundColor = Color.Unspecified,
                onSourceReady = { underlaySource = it }, hazeState = underlayHaze) {
                CMSGlassButton({}, width = width, height = height, cornerRadius = cornerRadius,
                    enabled = false, sampleContent = true, samplingSource = samplingSource, hazeState = hazeState,
                    shadowStrength = 0f,
                    // 复用现有玻璃辉光，降低亮度并限定物理范围；文字仍绘制在其上。
                    touchEffectIntensity = 0.12f, touchEffectRadius = 96.dp,
                    externalTouchFeedback = true, externalTouchDeformation = false,
                    feedbackPosition = if (pressed && enabled) contactWindowPosition else null,
                    glassTintAlpha = if (dark) 0.32f else 0.05f,
                    glassBackground = if (containerColor != Color.Unspecified) containerColor
                        else if (dark) Color(0xFF38383A).copy(alpha = 0.60f)
                        else Color.White.copy(alpha = 0.34f)) {}
            }
        }

        // Fixed recording room accommodates every allowed mode, without bitmap scaling.
        val lensWidth = with(density) { (geometry.indicatorWidth * 2f + pressExpansion.toPx() * 3f).toDp() }
        val lensHeight = with(density) { (geometry.indicatorHeight * 2f + pressExpansion.toPx() * 3f).toDp() }
        if (optical) {
            Box(Modifier.zIndex(if (lensOnTop) 2f else 1f)
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(lensWidth, lensHeight).graphicsLayer {
                clip = false
                translationX = rendered - with(density) { lensWidth.toPx() } / 2f
                translationY = geometry.height / 2f - with(density) { lensHeight.toPx() } / 2f
            }) {
                CMSGlassButton({}, modifier = Modifier.graphicsLayer { clip = false },
                    width = lensWidth, height = lensHeight, cornerRadius = lensHeight / 2,
                    enabled = false, useNativeGlass = material.optical,
                    sampleContent = true, samplingSource = samplingSource, hazeState = hazeState,
                    shadowStrength = 0f, touchEffectIntensity = 0f, glassTintAlpha = 0f,
                    glassBackground = Color.Transparent, clearLens = true,
                    clearLensBlur = 0.5.dp,
                    samplingUnderlay = underlaySource,
                    samplingForeground = foregroundSource,
                    samplingScreenPosition = barScreenPosition?.plus(Offset(
                        (rendered - with(density) { lensWidth.toPx() } / 2f) * pressScale.value,
                        (geometry.height / 2f - with(density) { lensHeight.toPx() } / 2f) * pressScale.value)),
                    fluidShape = CMSGlassFluidShape(with(density) { bodyWidth.toDp() },
                        with(density) { bodyHeight.toDp() }, strain, shear, indicatorColor, amount),
                    motionBlur = (abs(lift.velocity) * 0.15f).coerceAtMost(2f).dp) {}
            }
        }
        // Indicator tint and icons travel with the capsule, above the lens at rest and under
        // it once the lens lifts (zIndex 1.5 against the lens' 1 / 2).
        Box(Modifier.fillMaxSize().zIndex(1.5f).then(driftModifier)) {
            Box(Modifier.graphicsLayer {
                translationX = rendered - bodyWidth / 2f
                translationY = (geometry.height - bodyHeight) / 2f
                // 与玻璃共用体积形变，渐退或再次按下都不会重置灰色轮廓。
                scaleX = if (liquidMotion && optical) exp(strain) else 1f
                scaleY = 1f / scaleX
                clip = false
            }.size(with(density) { bodyWidth.toDp() }, with(density) { bodyHeight.toDp() })
                .drawBehind {
                    val tintAlpha = indicatorColor.alpha * (1f - amount)
                    if (tintAlpha > 0.001f) {
                        drawRoundRect(indicatorColor.copy(alpha = tintAlpha),
                            cornerRadius = CornerRadius(with(density) { indicatorRadius.toPx() }
                                .coerceIn(0f, minOf(size.width, size.height) / 2f)))
                    }
                }) {}
            // The foreground layer stays fully opaque: the sampled icons and labels are the
            // only copy left once the lens owns the layer above them. It carries no background
            // of its own; painting one would cover the capsule with a flat rect.
            CMSGlassContentSource(Modifier.fillMaxSize().graphicsLayer {
                alpha = 1f
                clip = false
            }, sourceTag = "cms_tab_bar_foreground", backgroundColor = Color.Unspecified,
                onSourceReady = { foregroundSource = it }) {
                TabContents(geometry.progress(rendered))
            }
        }
        Box(Modifier.fillMaxSize().zIndex(3f).pointerInput(geometry, enabled, liquidMotion, draggable) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down.consume()
                haptic(CMSHapticKind.Click)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                val initial = selection
                var landed = geometry.landing(down.position.x)
                hovering = landed
                // All glass navigation previews the actual touch point, including
                // presses outside the current lens. Text and icon bars share this path.
                requestedCenter = down.position.x
                pressAnchor = down.position.x
                barDrift = 0f
                contactWindowPosition = barCoordinates?.takeIf { it.isAttached }
                    ?.localToWindow(down.position)
                dropping = false
                landingDirection = 0f
                fluid.endReleaseRecovery()
                ++gestureToken
                pressed = true
                releasing = false
                pagerHandoff = false
                lift.animateTo(1f, scope, stiffness = 170f, damping = 0.78f)
                center.animateTo(geometry.follow(requestedCenter), scope,
                    stiffness = 300f, damping = 0.92f)
                var cancelled = true
                var moved = false
                var releaseCenter: Float? = null
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        // 光源读取实际触点，不等拖拽阈值，也不使用滞后的镜片中心。
                        contactWindowPosition = if (change.pressed)
                            barCoordinates?.takeIf { it.isAttached }?.localToWindow(change.position)
                        else null
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) {
                            if (moved) {
                                val x = geometry.follow(change.position.x)
                                releaseCenter = x
                                landed = geometry.landing(x)
                            }
                            cancelled = false
                            change.consume()
                            break
                        }
                        val dx = change.position.x - down.position.x
                        if (draggable && abs(dx) > viewConfiguration.touchSlop)
                            moved = true
                        if (moved) {
                            requestedCenter = change.position.x
                            // Damped follow: the lens trails the finger instead of being glued
                            // to it, so a flick keeps its momentum for the release.
                            center.animateTo(geometry.follow(requestedCenter), scope,
                                stiffness = 180f, damping = 0.9f)
                            val hover = geometry.landing(geometry.follow(requestedCenter))
                            if (hover != hovering) { hovering = hover; haptic(CMSHapticKind.Tick) }
                        }
                        change.consume()
                    }
                } finally {
                    contactWindowPosition = null
                    releasing = true
                    pagerHandoff = false
                    pressed = false
                    val (target, commit) = geometry.release(initial,
                        releaseCenter ?: geometry.center(landed), cancelled)
                    hovering = target
                    drivenSelection = target
                    val velocity = if (moved && !cancelled)
                        tracker.calculateVelocity().x.coerceIn(-2400f, 2400f) else center.velocity
                    landingDirection = if (!cancelled)
                        (velocity / geometry.indicatorWidth / 2.5f).coerceIn(-1f, 1f) else 0f
                    if (moved && !cancelled) fluid.beginReleaseRecovery()
                    val token = ++gestureToken
                    // A drag keeps its momentum through a softer spring; a tap has no momentum
                    // to keep and should simply arrive at the chosen tab.
                    center.release(geometry.center(target), velocity, scope,
                        stiffness = if (moved) 220f else 300f,
                        damping = if (moved) 0.88f else 0.92f)
                    // The lens rides to the chosen tab before it settles. A throw should keep
                    // flying while it still has momentum, so a fast drag waits for the slide to
                    // nearly finish before it shrinks; a tap only needs to be visibly on target.
                    scope.launch {
                        if (token != gestureToken || pressed) return@launch
                        lift.animateTo(1f, scope, stiffness = 240f, damping = 0.9f)
                        val restX = geometry.center(target)
                        val thrown = (abs(velocity) / geometry.indicatorWidth).coerceIn(0f, 2.5f)
                        val landedSlop = geometry.indicatorWidth *
                            (if (moved) 0.10f + 0.08f * thrown else 0.05f)
                        while (token == gestureToken && !pressed &&
                            abs(center.value - restX) > landedSlop) {
                            withFrameNanos { }
                        }
                        if (token == gestureToken && !pressed) {
                            // 横向动能已经进入变高、变窄的流体模式，不再重复注入
                            // 下落轴；保留现有抬起轴速度，让两轴与形变一起连续恢复。
                            val verticalSpeed = lift.velocity
                            dropping = true
                            lift.release(0f, velocity = verticalSpeed, scope,
                                stiffness = 180f, damping = 0.6f)
                            center.awaitSettled()
                            lift.awaitSettled()
                            if (token == gestureToken && !pressed) {
                                // Only after the bar's own landing may page position
                                // regain control. Rejected selections reconcile smoothly.
                                pagerHandoff = pagerPosition != null
                                releasing = false
                            }
                        }
                    }
                    if (commit && target != selection) {
                        callback(target)
                    }
                }
            }
        })
    }
}
