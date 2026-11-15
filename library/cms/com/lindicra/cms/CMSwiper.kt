package com.lindicra.cms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.composecontinuousroundedcornershape.ContinuousRoundedCornerShape
import kotlinx.coroutines.delay
import com.lindicra.cms.ui.theme.LocalCMSAccentColor
import com.lindicra.cms.ui.theme.LocalClearIsDark
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

enum class CMSwipeDirection { Horizontal, Vertical }
enum class CMSEdgeOverflow { None, Spring }
enum class CMSNavBar { None, DotIndicator, DigitIndicator }

/** Pages, boundary springs and indicators share one continuous physical position. */
@Composable
@Deprecated("Use CMSCarousel", ReplaceWith("CMSCarousel(itemCount, modifier, direction, autoPlay, autoPlayInterval, showArrows, edgeOverflow, snapBack, navBar, pageHeight, pageSpacing, clipRadius, freeMode, pageWidthFraction, indicatorVisibleCount, indicatorSize, indicatorActiveWidth, indicatorBottomInset, indicatorInteractive, indicatorColor, arrowSize, content)"))
fun CMSwiper(
    itemCount: Int,
    modifier: Modifier = Modifier,
    direction: CMSwipeDirection = CMSwipeDirection.Horizontal,
    autoPlay: Boolean = false,
    autoPlayInterval: Long = 3000L,
    showArrows: Boolean = false,
    edgeOverflow: CMSEdgeOverflow = CMSEdgeOverflow.Spring,
    snapBack: Boolean = true,
    navBar: CMSNavBar = CMSNavBar.DotIndicator,
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
) {
    val count = itemCount.coerceAtLeast(0)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val motion = remember { CMSwiperMotion() }
    var pageSource by remember { mutableStateOf<android.view.ViewGroup?>(null) }
    val pageHaze = remember { dev.chrisbanes.haze.HazeState() }
    var viewportSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val horizontal = direction == CMSwipeDirection.Horizontal
    val viewport = (if (horizontal) viewportSize.width else viewportSize.height).toFloat()
    val fraction = if (freeMode) pageWidthFraction.coerceIn(0.1f, 1f) else 1f
    val pageSize = (viewport * fraction).coerceAtLeast(1f)
    val spacing = with(density) { pageSpacing.toPx().coerceAtLeast(0f) }
    val advance = pageSize + spacing
    val minimum = swiperMinimum(count, pageSize, spacing, viewport)
    val progress = (-motion.value / advance).coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())
    val paging = snapBack && !freeMode
    fun goTo(page: Int) {
        if (count > 0) motion.settle(-page.coerceIn(0, count - 1) * advance, scope)
    }
    fun release(speed: Float) {
        if (paging) {
            val page = swiperTargetPage(motion.value, speed, advance, count)
            motion.settle(-page * advance, scope, speed)
        } else motion.releaseFree(speed, scope)
    }

    LaunchedEffect(minimum, viewport, advance, edgeOverflow, direction, paging) {
        motion.configure(minimum, viewport, advance, edgeOverflow == CMSEdgeOverflow.Spring)
        if (paging && count > 0) goTo((-motion.value / advance).roundToInt())
    }
    DisposableEffect(motion) { onDispose { motion.stop() } }
    LaunchedEffect(autoPlay, autoPlayInterval, count, paging, advance) {
        if (!autoPlay || !paging || count <= 1) return@LaunchedEffect
        while (true) {
            delay(autoPlayInterval.coerceAtLeast(500L))
            if (!dragging && !motion.moving) {
                val page = (-motion.value / advance).roundToInt().coerceIn(0, count - 1)
                goTo((page + 1) % count)
            }
        }
    }

    Box(
        modifier.fillMaxWidth().height(pageHeight)
            .clip(ContinuousRoundedCornerShape(clipRadius))
            .onSizeChanged { viewportSize = it }
    ) {
        // Sample only the pages; arrows and indicators remain outside the source.
        CMGlassContentSource(
            Modifier.fillMaxSize(),
            onSourceReady = { pageSource = it },
            hazeState = pageHaze
        ) {
            Box(Modifier.fillMaxSize().swiperGesture(
                count, horizontal, motion, { dragging = it }, { release(it) }
            )) {
                // Only compose nearby pages; translate during drawing, without per-frame layout.
                val first = (floor(progress).toInt() - 1).coerceAtLeast(0)
                val visible = (1f / fraction).toInt() + 3
                for (i in first until minOf(count, first + visible)) {
                    key(i) {
                        Box(
                            Modifier
                                .then(if (horizontal) Modifier.width(with(density) { pageSize.toDp() }).fillMaxHeight()
                                    else Modifier.fillMaxWidth().height(with(density) { pageSize.toDp() }))
                                .graphicsLayer {
                                    if (horizontal) translationX = motion.value + i * advance
                                    else translationY = motion.value + i * advance
                                    clip = false
                                }
                        ) { content(i) }
                    }
                }
            }
        }
        if (showArrows && count > 1) {
            SwiperArrow(if (horizontal) Alignment.CenterStart else Alignment.TopCenter,
                if (horizontal) 0f else 90f, motion.value < -0.5f, arrowSize, pageSource, pageHaze) {
                goTo((progress - 1f).roundToInt())
            }
            SwiperArrow(if (horizontal) Alignment.CenterEnd else Alignment.BottomCenter,
                if (horizontal) 0f else 90f, motion.value > minimum + 0.5f, arrowSize, pageSource, pageHaze,
                mirrored = true) {
                goTo((progress + 1f).roundToInt())
            }
        }
        if (count > 1) {
            val indicatorModifier = if (horizontal)
                Modifier.align(Alignment.BottomCenter).padding(bottom = indicatorBottomInset)
            else Modifier.align(Alignment.CenterEnd).padding(end = indicatorBottomInset)
            when (navBar) {
                CMSNavBar.DotIndicator -> SwiperDotIndicator(count, progress, indicatorVisibleCount,
                    indicatorSize, indicatorActiveWidth, indicatorColor, indicatorInteractive, horizontal,
                    indicatorModifier) { goTo(it) }
                CMSNavBar.DigitIndicator -> SwiperDigitIndicator(count, progress, indicatorModifier)
                CMSNavBar.None -> Unit
            }
        }
    }
}

@Composable
private fun SwiperDotIndicator(
    count: Int, progress: Float, visibleCount: Int, dotSize: Dp, activeWidth: Dp,
    activeColor: Color, interactive: Boolean, horizontal: Boolean,
    modifier: Modifier, onSelect: (Int) -> Unit
) {
    val dark = LocalClearIsDark.current
    val background = if (dark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.18f)
    val slots = visibleCount.coerceIn(1, count)
    val base = dotSize.coerceAtLeast(2.dp)
    val pill = activeWidth.coerceAtLeast(base)
    val pitch = base + 5.dp
    val width = pill + pitch * (slots - 1)
    val start = (progress - (slots - 1) / 2f).coerceIn(0f, (count - slots).toFloat())
    val density = LocalDensity.current
    val geometry = with(density) {
        CMSwiperIndicatorLayout(count, progress, base.toPx(), pill.toPx(), 5.dp.toPx())
    }
    val offset = geometry.windowOffset(start)
    val currentGeometry by rememberUpdatedState(geometry)
    val currentOffset by rememberUpdatedState(offset)
    val currentSelect by rememberUpdatedState(onSelect)
    Canvas(modifier.then(if (horizontal) Modifier.width(width).height(24.dp)
            else Modifier.width(24.dp).height(width)).clipToBounds()
        .then(if (interactive) Modifier.pointerInput(count, slots, horizontal) {
            detectTapGestures { tap ->
                val coordinate = (if (horizontal) tap.x else tap.y) + currentOffset
                val page = (0 until count).minByOrNull { abs(currentGeometry.center(it) - coordinate) } ?: 0
                currentSelect(page)
            }
        } else Modifier)
    ) {
        val basePx = base.toPx()
        val extent = if (horizontal) size.width else size.height
        for (index in (floor(start).toInt() - 1).coerceAtLeast(0)..minOf(count - 1, start.toInt() + slots + 1)) {
            val weight = geometry.weight(index)
            val center = geometry.center(index) - offset
            val visibility = (minOf(center, extent - center) / (basePx / 2)).coerceIn(0f, 1f)
            val length = geometry.length(index)
            val color = lerp(background, activeColor, weight)
            drawRoundRect(color.copy(alpha = color.alpha * visibility),
                if (horizontal) Offset(center - length / 2, (size.height - basePx) / 2)
                    else Offset((size.width - basePx) / 2, center - length / 2),
                if (horizontal) Size(length, basePx) else Size(basePx, length),
                CornerRadius(basePx / 2))
        }
    }
}

@Composable
private fun SwiperDigitIndicator(count: Int, progress: Float, modifier: Modifier) {
    val lower = floor(progress).toInt().coerceIn(0, count - 1)
    val fraction = progress - lower
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width((count.toString().length * 9).dp).height(24.dp), contentAlignment = Alignment.Center) {
            Text("${lower + 1}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer { alpha = 1f - fraction; translationY = -fraction * 8.dp.toPx() })
            if (lower + 1 < count) Text("${lower + 2}", color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer { alpha = fraction; translationY = (1f - fraction) * 8.dp.toPx() })
        }
        Text(" / $count", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BoxScope.SwiperArrow(
    alignment: Alignment, rotation: Float, enabled: Boolean, buttonSize: Dp,
    source: android.view.ViewGroup?, haze: dev.chrisbanes.haze.HazeState,
    mirrored: Boolean = false,
    onClick: () -> Unit
) {
    Box(Modifier.align(alignment).padding(6.dp)) {
        val diameter = buttonSize.coerceAtLeast(20.dp)
        CMSGlassButton(onClick, width = diameter, height = diameter, enabled = enabled,
            sampleContent = true, samplingSource = source, hazeState = haze,
            adaptiveAppearance = true) { foreground ->
            Icon(painterResource(R.drawable.chevron_left),
                contentDescription = if (mirrored) "下一页" else "上一页",
                tint = foreground.copy(alpha = if (enabled) 1f else 0.3f),
                modifier = Modifier.size(diameter * 0.5f).graphicsLayer {
                    scaleX = if (mirrored) -1f else 1f
                    rotationZ = rotation
                })
        }
    }
}
