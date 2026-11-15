package ldyc.qy.music

import android.os.Build
import android.view.ViewGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.HazeState
import ldyc.qy.music.ui.theme.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.exp
import kotlinx.coroutines.flow.first

data class CMSGlassTabItem(val label: String, val icon: ImageVector)

object CMSGlassTabBarDefaults {
    val items = listOf(
        CMSGlassTabItem("健康", Icons.Default.Favorite),
        CMSGlassTabItem("运动", Icons.Default.DirectionsRun),
        CMSGlassTabItem("设备", Icons.Default.Watch),
        CMSGlassTabItem("我的", Icons.Default.Person)
    )
}

/** Resting geometry shares one inset on all four sides, including the end tabs. */
internal data class CMTabGeometry(val width: Float, val height: Float, val count: Int, val inset: Float) {
    val slot get() = (width - 2f * inset) / count
    val indicatorWidth get() = slot
    val indicatorHeight get() = height - 2f * inset
    fun center(index: Int) = inset + slot * (index + 0.5f)
    fun landing(center: Float) = floor((center - inset) / slot).toInt().coerceIn(0, count - 1)
    fun release(initialIndex: Int, lensCenter: Float, cancelled: Boolean): Pair<Int, Boolean> =
        if (cancelled) initialIndex to false else landing(lensCenter) to true
    fun follow(x: Float): Float = x.coerceIn(center(0), center(count - 1))
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
    hazeState: HazeState? = null
) {
    require(items.size in 2..5) { "底栏支持 2 至 5 项" }
    require(selectedIndex in items.indices) { "selectedIndex 超出底栏范围" }
    require(height >= 44.dp && indicatorInset >= 0.dp && indicatorInset * 2 < height) {
        "底栏高度至少 44dp，内间距必须小于高度的一半"
    }
    require(pressExpansion >= 0.dp) { "按下扩张不能为负数" }
    val density = LocalDensity.current
    val windowView = LocalView.current
    var barScreenPosition by remember { mutableStateOf<Offset?>(null) }
    val scope = rememberCoroutineScope()
    val material = LocalGlassMaterial.current
    val optical = material.optical && Build.VERSION.SDK_INT >= 33
    val dark = LocalClearIsDark.current
    val haptic = rememberCMSHaptic()
    val callback by rememberUpdatedState(onSelectedChange)
    val selection by rememberUpdatedState(selectedIndex)
    var pressed by remember { mutableStateOf(false) }
    var hovering by remember { mutableIntStateOf(selectedIndex) }
    var drivenSelection by remember { mutableIntStateOf(selectedIndex) }
    val lift = remember { CMProgressMotion(0f) }
    val fluid = remember { CMTabFluid() }
    var strain by remember { mutableFloatStateOf(0f) }
    var shear by remember { mutableFloatStateOf(0f) }
    var requestedCenter by remember { mutableFloatStateOf(0f) }
    @Composable
    fun TabContents() {
        Row(Modifier.fillMaxSize().selectableGroup().padding(horizontal = indicatorInset)) {
            items.forEachIndexed { index, item ->
                val active = index == if (pressed) hovering else selectedIndex
                val color by androidx.compose.animation.animateColorAsState(
                    if (active) selectedColor else unselectedColor, label = "tabForeground")
                Column(Modifier.weight(1f).fillMaxHeight().semantics {
                    role = Role.Tab
                    selected = index == selectedIndex
                    contentDescription = item.label
                    if (enabled) onClick {
                        if (index != selection) { haptic(CMSHapticKind.Click); callback(index) }
                        true
                    } else disabled()
                }, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
                    if (showLabels) {
                        Spacer(Modifier.height(3.dp))
                        Text(item.label, color = color, fontSize = labelSize, maxLines = 1,
                            fontFamily = HarmonySansSC, fontWeight = FontWeight.SemiBold,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
                    }
                }
            }
        }
    }
    BoxWithConstraints(modifier.height(height).graphicsLayer { clip = false }
        .onGloballyPositioned { coordinates ->
            val screen = IntArray(2)
            val window = IntArray(2)
            windowView.getLocationOnScreen(screen)
            windowView.getLocationInWindow(window)
            barScreenPosition = coordinates.positionInWindow() +
                Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
        }) {
        val width = maxWidth
        require(width.value.isFinite() && width > indicatorInset * 2) { "底栏需要有限宽度" }
        val geometry = remember(width, height, indicatorInset, items.size, density) {
            with(density) { CMTabGeometry(width.toPx(), height.toPx(), items.size, indicatorInset.toPx()) }
        }
        val center = remember { CMProgressMotion(geometry.center(selectedIndex)) }
        LaunchedEffect(geometry) { center.follow(geometry.center(selection)) }
        LaunchedEffect(selectedIndex, geometry) {
            if (!pressed && selectedIndex != drivenSelection) {
                drivenSelection = selectedIndex
                hovering = selectedIndex
                center.animateTo(geometry.center(selectedIndex), scope)
            }
        }
        LaunchedEffect(geometry, liquidMotion, optical) {
            while (true) {
                snapshotFlow { pressed || abs(center.velocity) > 0.01f || abs(lift.velocity) > 0.001f }
                    .first { it }
                var previous = withFrameNanos { it }
                var previousCenter = center.value
                var previousVelocity = 0f
                do {
                    val now = withFrameNanos { it }
                    val dt = ((now - previous) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                    val growth = with(density) { pressExpansion.toPx() } *
                        lift.value.coerceIn(-0.3f, 1.3f)
                    val bodyWidth = (geometry.indicatorWidth + growth).coerceAtLeast(1f)
                    val boundWidth = tabFluidWidth(bodyWidth, strain, shear).coerceAtMost(geometry.width)
                    val rendered = geometry.renderedCenter(center.value, boundWidth)
                    val velocity = (rendered - previousCenter) / dt / geometry.indicatorWidth
                    val acceleration = (velocity - previousVelocity) / dt
                    val contact = ((if (pressed)
                        requestedCenter - geometry.follow(requestedCenter) else 0f) +
                        (center.value - rendered)) / geometry.indicatorWidth
                    fluid.step(dt, if (liquidMotion && optical) velocity else 0f,
                        if (liquidMotion && optical) acceleration else 0f,
                        if (liquidMotion && optical) contact else 0f)
                    strain = fluid.strain
                    shear = fluid.shear
                    previousCenter = rendered
                    previousVelocity = velocity
                    previous = now
                } while (pressed || abs(center.velocity) > 0.01f ||
                    abs(lift.velocity) > 0.001f || !fluid.settled)
                strain = 0f
                shear = 0f
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                center.follow(center.value); lift.follow(lift.value)
            }
        }
        var underlaySource by remember { mutableStateOf<ViewGroup?>(null) }
        var foregroundSource by remember { mutableStateOf<ViewGroup?>(null) }
        val underlayHaze = remember { HazeState() }
        val amount = lift.value.coerceIn(0f, 1f)
        val growth = with(density) { pressExpansion.toPx() } * lift.value.coerceIn(-0.3f, 1.3f)
        val bodyWidth = (geometry.indicatorWidth + growth).coerceAtLeast(1f)
            .coerceAtMost(geometry.width / (exp(strain) * (1f + 0.24f * abs(shear))))
        val bodyHeight = (geometry.indicatorHeight + growth).coerceAtLeast(1f)
        val rendered = geometry.renderedCenter(center.value, tabFluidWidth(bodyWidth, strain, shear))
        // The indicator is one persistent surface between the bar and its foreground.
        // The upper lens is a sibling, so the capture can never include itself.
        CMSGlassContentSource(Modifier.fillMaxSize(), sourceTag = "cms_tab_bar_underlay",
            onSourceReady = { underlaySource = it }, hazeState = underlayHaze) {
            CMSGlassButton({}, width = width, height = height, cornerRadius = height / 2,
                enabled = false, sampleContent = true, samplingSource = samplingSource, hazeState = hazeState,
                shadowStrength = 2f, touchEffectIntensity = 0f,
                glassTintAlpha = if (dark) 0.32f else 0.05f,
                glassBackground = if (dark) Color(0xFF38383A).copy(alpha = 0.60f)
                    else Color.White.copy(alpha = 0.34f)) {}
        }

        // Fixed recording room accommodates every allowed mode, without bitmap scaling.
        val lensWidth = with(density) { (geometry.indicatorWidth * 2f + pressExpansion.toPx() * 3f).toDp() }
        val lensHeight = with(density) { (geometry.indicatorHeight * 2f + pressExpansion.toPx() * 3f).toDp() }
        if (optical) Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
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
                samplingUnderlay = underlaySource,
                samplingForeground = foregroundSource,
                samplingScreenPosition = barScreenPosition?.plus(Offset(
                    rendered - with(density) { lensWidth.toPx() } / 2f,
                    geometry.height / 2f - with(density) { lensHeight.toPx() } / 2f)),
                fluidShape = CMSGlassFluidShape(with(density) { bodyWidth.toDp() },
                    with(density) { bodyHeight.toDp() }, strain, shear, indicatorColor, amount),
                motionBlur = (abs(lift.velocity) * 0.15f).coerceAtMost(2f).dp) {}
        } else {
            CMSGlassButton({}, modifier = Modifier.graphicsLayer {
                translationX = rendered - bodyWidth / 2f
                translationY = (geometry.height - bodyHeight) / 2f
                clip = false
            }.drawBehind {
                drawRoundRect(indicatorColor.copy(alpha = indicatorColor.alpha * (1f - amount)),
                    cornerRadius = CornerRadius(size.height / 2f))
            }, width = with(density) { bodyWidth.toDp() }, height = with(density) { bodyHeight.toDp() },
                cornerRadius = with(density) { (bodyHeight / 2f).toDp() },
                enabled = false, useNativeGlass = false, clearLens = true,
                glassBackground = Color.Transparent, shadowStrength = 0f,
                motionBlur = (abs(lift.velocity) * 0.15f).coerceAtMost(2f).dp) {}
        }
        // Direct native capture omits this Compose layer's alpha. The shader receives the
        // foreground progressively while the visible original stays above the resting tint.
        val opticalAmount = amount * amount * (3f - 2f * amount)
        CMSGlassContentSource(Modifier.fillMaxSize().graphicsLayer {
            alpha = if (optical) 1f - opticalAmount else 1f
            clip = false
        }, sourceTag = "cms_tab_bar_foreground",
            onSourceReady = { foregroundSource = it }) {
            TabContents()
        }
        Box(Modifier.fillMaxSize().pointerInput(geometry, enabled, liquidMotion) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down.consume()
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                val initial = selection
                var landed = geometry.landing(down.position.x)
                hovering = landed
                requestedCenter = geometry.center(landed)
                pressed = true
                lift.animateTo(1f, scope, stiffness = 260f, damping = 0.62f)
                // A tap anywhere lifts the lens at that tab; dragging keeps the original grab offset.
                val grabOffset = if (landed == geometry.landing(center.value))
                    down.position.x - center.value else 0f
                center.animateTo(geometry.center(landed), scope)
                var cancelled = true
                var moved = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) {
                            if (moved) center.follow(geometry.follow(change.position.x - grabOffset))
                            landed = if (moved) geometry.landing(center.value) else landed
                            cancelled = false
                            change.consume()
                            break
                        }
                        if (abs(change.position.x - down.position.x) > viewConfiguration.touchSlop)
                            moved = true
                        if (moved) {
                            requestedCenter = change.position.x - grabOffset
                            center.follow(geometry.follow(requestedCenter))
                            val hover = geometry.landing(center.value)
                            if (hover != hovering) { hovering = hover; haptic(CMSHapticKind.Tick) }
                        }
                        change.consume()
                    }
                } finally {
                    pressed = false
                    val (target, commit) = geometry.release(initial,
                        if (moved) center.value else geometry.center(landed), cancelled)
                    hovering = target
                    drivenSelection = target
                    val velocity = if (moved && !cancelled)
                        tracker.calculateVelocity().x.coerceIn(-2400f, 2400f) else center.velocity
                    center.release(geometry.center(target), velocity, scope, stiffness = 260f, damping = 0.65f)
                    lift.animateTo(0f, scope, stiffness = 180f, damping = 0.52f)
                    if (commit && target != selection) {
                        haptic(CMSHapticKind.Click)
                        callback(target)
                    }
                }
            }
        })
    }
}
