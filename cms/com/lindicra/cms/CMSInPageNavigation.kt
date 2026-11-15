package com.lindicra.cms

import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.lindicra.cms.ui.theme.*
import com.adamglin.composecontinuousroundedcornershape.ContinuousRoundedCornerShape
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.roundToInt

enum class CMSCapsuleVariant { Filled, Outlined }

/** CLEAR 页面内导航；pageProgress 可直接绑定页面位置，接管时不会再追赶旧动画。 */
@Composable
fun CMSCapsuleNavigation(
    labels: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    variant: CMSCapsuleVariant = CMSCapsuleVariant.Filled,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    outerRadius: Dp = 22.dp,
    indicatorRadius: Dp = 18.dp,
    contentInset: Dp = 4.dp,
    borderWidth: Dp = 1.5.dp,
    fontSize: TextUnit = 14.sp,
    selectedColor: Color = PrimaryText,
    unselectedColor: Color = SecondaryText,
    containerColor: Color = Color.Unspecified,
    indicatorColor: Color = Color.Unspecified,
    pageProgress: Float? = null,
    draggable: Boolean = true,
    liquidGlass: Boolean = false,
    liquidMotion: Boolean = true,
    samplingSource: ViewGroup? = null,
    hazeState: HazeState? = null,
    pageTouchVersion: Int = 0
) {
    val options = labels.take(4)
    if (options.size < 2) return
    val selected = selectedIndex.coerceIn(options.indices)
    val dark = LocalClearIsDark.current
    val base = if (dark) Color.White else Color.Black
    val outer = ContinuousRoundedCornerShape(outerRadius.coerceAtLeast(0.dp))
    val inner = ContinuousRoundedCornerShape(indicatorRadius.coerceAtLeast(0.dp))
    val actualHeight = height.coerceAtLeast(36.dp)
    val inset = contentInset.coerceIn(0.dp, actualHeight / 3)
    if (liquidGlass) {
        CMSGlassTabBar(
            items = options.map { CMSGlassTabItem(it) },
            selectedIndex = selected, onSelectedChange = onSelectedChange,
            modifier = modifier.then(if (variant == CMSCapsuleVariant.Outlined)
                Modifier.border(borderWidth.coerceAtLeast(0.dp), base.copy(alpha = 0.15f), outer)
                else Modifier),
            height = actualHeight, indicatorInset = inset,
            pressExpansion = 12.dp, labelSize = fontSize,
            selectedColor = selectedColor, unselectedColor = unselectedColor,
            indicatorColor = if (indicatorColor != Color.Unspecified) indicatorColor
                else base.copy(alpha = 0.15f),
            liquidMotion = liquidMotion, enabled = enabled,
            samplingSource = samplingSource, hazeState = hazeState,
            fillWidth = true, itemOverlap = 0.dp, pageProgress = pageProgress,
            draggable = draggable, cornerRadius = outerRadius.coerceAtLeast(0.dp),
            indicatorRadius = indicatorRadius.coerceAtLeast(0.dp),
            containerColor = containerColor, labelWeight = FontWeight.Medium,
            pageTouchVersion = pageTouchVersion
        )
        return
    }
    val fill = if (containerColor != Color.Unspecified) containerColor
        else if (variant == CMSCapsuleVariant.Outlined) Color.Transparent
        else base.copy(alpha = 0.06f)
    val highlight = if (indicatorColor != Color.Unspecified) indicatorColor
        else base.copy(alpha = if (variant == CMSCapsuleVariant.Outlined) 0.1f else 0.15f)
    val externalPosition = pageProgress?.takeIf { it.isFinite() }
        ?.coerceIn(0f, options.lastIndex.toFloat())
    val motion = remember(options.size) { CMProgressMotion(externalPosition ?: selected.toFloat()) }
    val scope = rememberCoroutineScope()
    var dragging by remember(options.size) { mutableStateOf(false) }
    var settling by remember(options.size) { mutableStateOf(false) }
    val callback by rememberUpdatedState(onSelectedChange)
    val selection by rememberUpdatedState(selected)
    var handledPageTouch by remember { mutableIntStateOf(pageTouchVersion) }
    val pageInterrupted = pageTouchVersion != handledPageTouch && !dragging
    LaunchedEffect(selected, externalPosition, dragging, settling, motion) {
        if (!dragging) {
            if (settling) {
                motion.animateTo(externalPosition ?: selected.toFloat(), scope,
                    stiffness = 460f, damping = 0.95f)
                motion.awaitSettled()
                settling = false
            } else if (externalPosition == null) {
                motion.animateTo(selected.toFloat(), scope, stiffness = 300f, damping = 0.9f)
            }
        }
    }
    SideEffect {
        if (pageTouchVersion != handledPageTouch) {
            handledPageTouch = pageTouchVersion
            if (!dragging) {
                settling = false
                motion.follow(externalPosition ?: selected.toFloat())
            }
        }
        if (!dragging && !settling && externalPosition != null) motion.follow(externalPosition)
    }
    DisposableEffect(motion) { onDispose { motion.follow(motion.value) } }
    val progress = if (dragging || settling && !pageInterrupted) motion.value
        else externalPosition ?: motion.value
    val displayedProgress by rememberUpdatedState(progress)
    val pagerPosition by rememberUpdatedState(externalPosition)
    val selectedTint by animateColorAsState(selectedColor, tween(220), label = "capsuleSelectedTint")
    val unselectedTint by animateColorAsState(unselectedColor, tween(220), label = "capsuleUnselectedTint")
    val textOpacity by animateFloatAsState(if (enabled) 1f else 0.4f,
        tween(220), label = "capsuleTextOpacity")
    val haptic = rememberCMSHaptic()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier.fillMaxWidth().height(actualHeight).selectableGroup()
        .clip(outer).background(fill)
        .then(if (variant == CMSCapsuleVariant.Outlined)
            Modifier.border(borderWidth.coerceAtLeast(0.dp), base.copy(alpha = 0.15f), outer)
        else Modifier).padding(inset)) {
        val slot = maxWidth / options.size
        val slotPx = with(density) { slot.toPx() }
        Box(Modifier.align(Alignment.CenterStart).width(slot).fillMaxHeight()
            .graphicsLayer { translationX = progress * slotPx * if (rtl) -1f else 1f }
            .background(highlight, inner))
        Row(Modifier.fillMaxSize().pointerInput(slotPx, options.size, rtl, enabled, draggable,
            indicatorRadius, density) {
            if (!enabled || !draggable || slotPx <= 0f) return@pointerInput
            awaitEachGesture {
                // Leave the down event to the selectable children; only a real
                // horizontal drag originating on the current indicator is claimed.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val initial = displayedProgress
                val physical = if (rtl) options.lastIndex - initial else initial
                val centerX = (physical + 0.5f) * slotPx
                if (!cmsNavigationIndicatorHit(down.position, centerX, size.height / 2f,
                        slotPx, size.height.toFloat(), with(density) { indicatorRadius.toPx() }))
                    return@awaitEachGesture
                val grabOffset = down.position.x - centerX
                var requested = initial
                var hover = initial.roundToInt().coerceIn(options.indices)
                var started = false
                var commit = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        if (!change.pressed) {
                            if (started) {
                                val position = (change.position.x - grabOffset) / slotPx - 0.5f
                                requested = (if (rtl) options.lastIndex - position else position)
                                    .coerceIn(0f, options.lastIndex.toFloat())
                                commit = true
                                change.consume()
                            }
                            break
                        }
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (!started && abs(dy) > viewConfiguration.touchSlop && abs(dy) >= abs(dx))
                            break
                        if (!started && abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) {
                            started = true
                            dragging = true
                            settling = false
                        }
                        if (started) {
                            val position = (change.position.x - grabOffset) / slotPx - 0.5f
                            requested = (if (rtl) options.lastIndex - position else position)
                                .coerceIn(0f, options.lastIndex.toFloat())
                            motion.animateTo(requested, scope, stiffness = 460f, damping = 0.95f)
                            val next = requested.roundToInt()
                            if (next != hover) {
                                hover = next
                                haptic(CMSHapticKind.Tick)
                            }
                            change.consume()
                        }
                    }
                } finally {
                    if (started) {
                        settling = pagerPosition != null
                        dragging = false
                        val target = requested.roundToInt().coerceIn(options.indices)
                        if (commit && target != selection) callback(target)
                    }
                }
            }
        }) {
            options.forEachIndexed { index, label ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(inner)
                    .selectable(selected = selected == index, enabled = enabled, role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptic(CMSHapticKind.Tick)
                        if (selected != index) onSelectedChange(index)
                    }, contentAlignment = Alignment.Center) {
                    val emphasis = (1f - abs(progress - index)).coerceIn(0f, 1f)
                    val tint = lerp(unselectedTint, selectedTint, emphasis)
                    CMSNavigationLabel(label, emphasis,
                        tint.copy(alpha = tint.alpha * textOpacity), fontSize, FontWeight.Medium)
                }
            }
        }
    }
}

/** Static font resources need a glyph crossfade to make weight changes continuous. */
@Composable
internal fun CMSNavigationLabel(
    label: String, emphasis: Float, color: Color, fontSize: TextUnit, selectedWeight: FontWeight
) {
    val weight = emphasis.coerceIn(0f, 1f)
    Box(contentAlignment = Alignment.BottomCenter) {
        Text(label, color = color, fontSize = fontSize, fontFamily = HarmonySansSC,
            fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = CMSNoFontPadding, modifier = Modifier.graphicsLayer { alpha = 1f - weight })
        Text(label, color = color, fontSize = fontSize, fontFamily = HarmonySansSC,
            fontWeight = selectedWeight, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = CMSNoFontPadding, modifier = Modifier.clearAndSetSemantics {}
                .graphicsLayer { alpha = weight })
    }
}

/** Hit the rendered rounded indicator, rather than the selected item's entire tap area. */
internal fun cmsNavigationIndicatorHit(
    point: Offset, centerX: Float, centerY: Float, width: Float, height: Float, radius: Float
): Boolean {
    if (width <= 0f || height <= 0f) return false
    val x = abs(point.x - centerX)
    val y = abs(point.y - centerY)
    if (x > width / 2f || y > height / 2f) return false
    val r = radius.coerceIn(0f, minOf(width, height) / 2f)
    val cornerX = (x - width / 2f + r).coerceAtLeast(0f)
    val cornerY = (y - height / 2f + r).coerceAtLeast(0f)
    return cornerX * cornerX + cornerY * cornerY <= r * r
}

/** Tabbar 的下划线形态：完整点击区域，文字与线作为一个紧凑组居中。 */
@Composable
fun CMSTabNavigation(
    labels: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    fontSize: TextUnit = 14.sp,
    selectedColor: Color = LocalCMSAccentColor.current,
    unselectedColor: Color = SecondaryText,
    indicatorColor: Color = LocalCMSAccentColor.current,
    indicatorHeight: Dp = 5.dp,
    indicatorRadius: Dp = 2.dp,
    indicatorWidthFraction: Float = 0.6f,
    pageProgress: Float? = null,
    indicatorGap: Dp = 3.dp
) {
    if (labels.isEmpty()) return
    val selected = selectedIndex.coerceIn(labels.indices)
    val externalPosition = pageProgress?.takeIf { it.isFinite() }
        ?.coerceIn(0f, labels.lastIndex.toFloat())
    val motion = remember(labels.size) {
        CMSUnderlineMotion(externalPosition ?: selected.toFloat())
    }
    LaunchedEffect(motion, selected, externalPosition) {
        var firstFrame = true
        do {
            val now = withFrameNanos { it }
            val dt = if (motion.frameTime == 0L ||
                (firstFrame && now - motion.frameTime > 100_000_000L)) 1f / 60f else
                ((now - motion.frameTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
            motion.frameTime = now
            if (externalPosition != null && firstFrame) motion.follow(externalPosition, now)
            motion.step(dt, selected.toFloat(), externalPosition != null, firstFrame)
            firstFrame = false
        } while (!motion.settled(selected.toFloat(), externalPosition != null))
        motion.finish(selected.toFloat(), externalPosition != null)
    }
    val progress = externalPosition ?: motion.position
    val targetFraction = if (indicatorWidthFraction.isFinite())
        indicatorWidthFraction.coerceIn(0.1f, 1f) else 0.6f
    val fraction by animateFloatAsState(targetFraction,
        tween(260, easing = FastOutSlowInEasing), label = "tabIndicatorWidth")
    val glowAvailability by animateFloatAsState(if (enabled) 1f else 0f,
        tween(260, easing = FastOutSlowInEasing), label = "tabGlowEnabled")
    val glowOpacity = motion.glow * (1f - exp(-abs(motion.velocity) * 0.38f)) *
        glowAvailability
    val haptic = rememberCMSHaptic()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lineHeight by animateDpAsState(indicatorHeight.coerceIn(0.dp, 12.dp),
        tween(260, easing = FastOutSlowInEasing), label = "tabIndicatorThickness")
    val lineRadius by animateDpAsState(indicatorRadius.coerceAtLeast(0.dp),
        tween(260, easing = FastOutSlowInEasing), label = "tabIndicatorRadius")
    val gap by animateDpAsState(indicatorGap.coerceIn(0.dp, 24.dp),
        tween(260, easing = FastOutSlowInEasing), label = "tabIndicatorGap")
    val indicatorTint by animateColorAsState(
        if (enabled) indicatorColor else indicatorColor.copy(alpha = 0.4f),
        tween(300, easing = FastOutSlowInEasing), label = "tabIndicatorTint")
    val textOpacity by animateFloatAsState(if (enabled) 1f else 0.4f,
        tween(300, easing = FastOutSlowInEasing), label = "tabEnabledOpacity")
    val selectedTint by animateColorAsState(selectedColor,
        tween(300, easing = FastOutSlowInEasing), label = "tabSelectedTint")
    val unselectedTint by animateColorAsState(unselectedColor,
        tween(300, easing = FastOutSlowInEasing), label = "tabUnselectedTint")
    var labelHeightPx by remember(fontSize, labels) { mutableIntStateOf(0) }
    val labelHeight = with(density) {
        if (labelHeightPx > 0) labelHeightPx.toDp()
        else (if (fontSize.isSp) fontSize else 14.sp).toDp()
    }
    // Scaling grows upward from the text's bottom; reserve its maximum height.
    val actualHeight = maxOf(height, 36.dp, labelHeight * 1.06f + gap + lineHeight)
    BoxWithConstraints(modifier.fillMaxWidth().height(actualHeight)
        .selectableGroup()) {
        val slot = maxWidth / labels.size
        val slotPx = with(density) { slot.toPx() }
        Box(Modifier.align(Alignment.CenterStart).offset(y = (labelHeight + gap) / 2)
            .width(slot * fraction).height(lineHeight)
            .graphicsLayer {
                translationX = (progress + (1f - fraction) / 2f) * slotPx * if (rtl) -1f else 1f
                scaleX = exp(motion.strain)
                scaleY = exp(-motion.strain)
            }.cmsTabIndicator(glowOpacity, indicatorTint, lineRadius))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                // Selection intent drives the labels; passing underneath is not selection.
                val emphasis by animateFloatAsState(if (selected == index) 1f else 0f,
                    spring(dampingRatio = 1f, stiffness = 260f),
                    label = "tabLabelEmphasis$index")
                val textColor = lerp(unselectedTint, selectedTint, emphasis)
                val textScale = 1f + 0.06f * emphasis
                Box(Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected == index, enabled = enabled, role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptic(CMSHapticKind.Tick)
                        if (selected != index) onSelectedChange(index)
                    }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.onSizeChanged {
                                labelHeightPx = maxOf(labelHeightPx, it.height)
                            }.graphicsLayer {
                                scaleX = textScale
                                scaleY = textScale
                                alpha = textOpacity
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }, contentAlignment = Alignment.BottomCenter) {
                            // HarmonySansSC contains separate font files, not a variable
                            // weight axis. Blend both glyphs instead of snapping weights.
                            Text(label, color = textColor, fontSize = fontSize,
                                fontFamily = HarmonySansSC, fontWeight = FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = CMSNoFontPadding,
                                modifier = Modifier.graphicsLayer { alpha = 1f - emphasis })
                            Text(label, color = textColor, fontSize = fontSize,
                                fontFamily = HarmonySansSC, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = CMSNoFontPadding,
                                modifier = Modifier.clearAndSetSemantics {}
                                    .graphicsLayer { alpha = emphasis })
                        }
                        Spacer(Modifier.height(gap + lineHeight))
                    }
                }
            }
        }
    }
}

/** Position and axial compression retain their velocities when a click interrupts motion. */
private class CMSUnderlineMotion(initial: Float) {
    var position by mutableFloatStateOf(initial)
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    var strain by mutableFloatStateOf(0f)
        private set
    var glow by mutableFloatStateOf(0f)
        private set
    var frameTime = 0L
    private var strainVelocity = 0f
    private var sampleTime = 0L
    private var sampledAcceleration = 0f

    fun follow(value: Float, now: Long) {
        val dt = if (sampleTime == 0L) 1f / 60f else
            ((now - sampleTime) / 1_000_000_000f).coerceAtLeast(1f / 240f)
        val measured = ((value - position) / dt).coerceIn(-16f, 16f)
        val oldVelocity = velocity
        velocity += (measured - velocity) * 0.55f
        sampledAcceleration = (velocity - oldVelocity) / dt
        position = value
        sampleTime = now
    }

    fun step(dt: Float, target: Float, external: Boolean, firstFrame: Boolean) {
        val oldVelocity = velocity
        if (!external) {
            val next = progressSpringStep(position - target, velocity, dt, 240f, 0.76f)
            position = target + next.first
            velocity = next.second
        } else if (!firstFrame) {
            velocity *= exp(-18f * dt)
        }
        val acceleration = if (external && firstFrame) sampledAcceleration else
            if (dt > 0f) (velocity - oldVelocity) / dt else 0f
        // Smooth ignition, with an additional speed gate so fading cannot outlive motion.
        val lightTarget = 1f - exp(-abs(velocity) * 0.38f)
        glow += (lightTarget - glow) * (1f - exp(-20f * dt))
        // Keep the underline straight. Motion compresses its width slightly, with
        // reciprocal thickness; braking adds a small load before the spring recovers.
        val braking = (-acceleration * sign(velocity)).coerceAtLeast(0f)
        val extension = (-0.1f * (1f - exp(-velocity * velocity / 10f)) -
            braking * 0.0008f).coerceIn(-0.14f, 0f)
        val nextStrain = progressSpringStep(strain - extension, strainVelocity,
            dt, 380f, 0.78f)
        strain = extension + nextStrain.first
        strainVelocity = nextStrain.second
    }

    fun settled(target: Float, external: Boolean) =
        (external || abs(position - target) < 0.0005f) &&
            abs(velocity) < 0.005f && abs(strain) < 0.001f &&
            abs(strainVelocity) < 0.01f

    fun finish(target: Float, external: Boolean) {
        if (!external) position = target
        velocity = 0f
        strain = 0f
        strainVelocity = 0f
        glow = 0f
    }
}

/** The line and every light contour stay straight and share the same axial scaling. */
private fun Modifier.cmsTabIndicator(
    strength: Float,
    color: Color,
    radius: Dp
): Modifier = drawWithCache {
    val spread = 10.dp.toPx()
    val downward = 2.dp.toPx()
    val baseRadius = radius.coerceAtLeast(0.dp).toPx()
        .coerceAtMost(minOf(size.width, size.height) / 2f)
    fun contour(expansion: Float, offsetY: Float): Path {
        val left = -expansion
        val right = size.width + expansion
        val top = offsetY - expansion * 0.65f
        val bottom = offsetY + size.height + expansion * 0.65f
        val r = (baseRadius + expansion * 0.65f)
            .coerceAtMost(minOf(right - left, bottom - top) / 2f)
        return Path().apply {
            moveTo(left + r, top)
            lineTo(right - r, top)
            quadraticBezierTo(right, top, right, top + r)
            lineTo(right, bottom - r)
            quadraticBezierTo(right, bottom, right - r, bottom)
            lineTo(left + r, bottom)
            quadraticBezierTo(left, bottom, left, bottom - r)
            lineTo(left, top + r)
            quadraticBezierTo(left, top, left + r, top)
            close()
        }
    }
    val line = contour(0f, 0f)
    val glowContours = (24 downTo 1).map { layer ->
        val falloff = layer / 24f
        contour(spread * falloff, downward) to
            (0.045f * (1f - falloff) * (1f - falloff))
    }
    onDrawBehind {
        if (size.width > 0f && size.height > 0f) {
            if (strength > 0f) for ((path, opacity) in glowContours) {
                drawPath(path, color.copy(alpha = color.alpha * strength * opacity))
            }
            drawPath(line, color)
        }
    }
}
