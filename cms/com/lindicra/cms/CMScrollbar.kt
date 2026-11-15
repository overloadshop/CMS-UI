package com.lindicra.cms

import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import com.lindicra.cms.ui.theme.LocalClearIsDark
import kotlin.math.roundToInt
import kotlin.math.abs

internal val LocalCMScrollbarSource = staticCompositionLocalOf<android.view.ViewGroup?> { null }

enum class CMScrollbarStyle { Classic, Alphabet, Sections }

enum class CMScrollbarIndexLayout { Distributed, TopPacked }

internal fun scrollbarIndexExtent(
    track: Float, count: Int, compactCell: Float, layout: CMScrollbarIndexLayout
): Float = if (layout == CMScrollbarIndexLayout.TopPacked)
    minOf(track, count.coerceAtLeast(0) * compactCell) else track

internal fun scrollbarPreviewAnchor(y: Float, section: Int, count: Int, extent: Float): Float {
    val cell = extent / count.coerceAtLeast(1)
    val center = (section.coerceIn(0, (count - 1).coerceAtLeast(0)) + 0.5f) * cell
    return center + (y - center).coerceIn(-cell * 0.5f, cell * 0.5f) * 0.1f
}

// Inverse fluid mapping: conserve area, bend the centre and narrow the trailing side.
// Apply it to the padded glass layer so refraction, rim, content and shadow stay together.
private const val INDEX_LIQUID_SHADER = """
uniform shader content;
uniform float2 layerSize;
uniform float2 glassSize;
uniform float strain;
uniform float bend;
half4 main(float2 xy) {
    float2 p = xy - layerSize * 0.5;
    float sy = max(0.72, 1.0 + strain);
    float nx = clamp(p.x / (glassSize.x * 0.5), -1.0, 1.0);
    float y = (p.y - bend * (1.0 - nx * nx)) / sy;
    float ny = clamp(y / (glassSize.y * 0.5), -1.0, 1.0);
    float taper = 1.0 + 0.576 * (bend / glassSize.y) * ny;
    float x = p.x * sy / max(0.75, taper);
    float2 source = float2(x, y) + layerSize * 0.5;
    return content.eval(clamp(source, float2(0.5), layerSize - float2(0.5)));
}
"""

/** Exact pixel offset for ScrollState, or item index for LazyGridState. */
data class CMScrollSection(val label: String, val scrollOffset: Int = 0, val itemIndex: Int = 0)

internal data class CMScrollMetrics(val maximum: Float, val position: Float)

internal fun scrollbarSeekFraction(top: Float, grabOffset: Float, track: Float, thumb: Float): Float =
    if (track <= thumb) 0f else ((top - grabOffset) / (track - thumb)).coerceIn(0f, 1f)

/** Keep real finger distance beyond the track; clamp only the content position. */
internal fun scrollbarDragOverflow(y: Float, grab: Float, track: Float, thumb: Float): Float {
    val top = y - grab
    val maximum = (track - thumb).coerceAtLeast(0f)
    return top - top.coerceIn(0f, maximum)
}

/** Continue dragging at a real boundary, including a final group shorter than the viewport. */
internal fun scrollbarIndexOverflow(
    raw: Float, delta: Float, sameSection: Boolean, wasAtTop: Boolean, wasAtBottom: Boolean,
    atTop: Boolean, atBottom: Boolean
): Float {
    if (!sameSection) return 0f
    if (raw > 0f && atBottom) return (raw + delta).coerceAtLeast(0f)
    if (raw < 0f && atTop) return (raw + delta).coerceAtMost(0f)
    return when {
        wasAtBottom && atBottom && delta > 0f -> delta
        wasAtTop && atTop && delta < 0f -> delta
        else -> 0f
    }
}

private data class ScrollbarSeek(val fraction: Float, val section: Int, val y: Float)

private fun metrics(scroll: ScrollState, grid: LazyGridState?): CMScrollMetrics {
    if (grid == null) return CMScrollMetrics(scroll.maxValue.toFloat(), scroll.value.toFloat())
    val layout = grid.layoutInfo
    val visible = layout.visibleItemsInfo
    if (visible.isEmpty()) return CMScrollMetrics(0f, 0f)
    val columns = visible.groupBy { it.row }.values.maxOf { it.size }.coerceAtLeast(1)
    val lineHeight = visible.groupBy { it.row }.values
        .map { line -> line.maxOf { it.size.height } }.average().toFloat() +
        layout.mainAxisItemSpacing
    val rows = (layout.totalItemsCount + columns - 1) / columns
    val viewport = (layout.viewportEndOffset - layout.viewportStartOffset).toFloat()
    val maximum = (rows * lineHeight - layout.mainAxisItemSpacing +
        layout.beforeContentPadding + layout.afterContentPadding - viewport).coerceAtLeast(0f)
    val position = (grid.firstVisibleItemIndex / columns * lineHeight +
        grid.firstVisibleItemScrollOffset).coerceIn(0f, maximum)
    return CMScrollMetrics(maximum, position)
}

@Composable
internal fun BoxScope.CMInteractiveScrollbar(
    scroll: ScrollState,
    grid: LazyGridState?,
    overscroll: Float,
    style: CMScrollbarStyle,
    interactive: Boolean,
    pressThickening: Boolean,
    sections: List<CMScrollSection>,
    insets: PaddingValues,
    indexLayout: CMScrollbarIndexLayout,
    onPress: () -> Unit,
    onOverscrollDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    touchRegions: CMSTouchRegionRegistry? = LocalCMSTouchRegions.current
) {
    val scope = rememberCoroutineScope()
    val owner = grid ?: scroll
    val currentMetrics by rememberUpdatedState(metrics(scroll, grid))
    val currentSections by rememberUpdatedState(sections)
    val currentPress by rememberUpdatedState(onPress)
    val currentOverscroll by rememberUpdatedState(onOverscrollDrag)
    val currentRelease by rememberUpdatedState(onRelease)
    val dark = LocalClearIsDark.current
    val density = LocalDensity.current
    // Resolve before entering Popup's composition. Its own root is a different
    // window. Capture the complete underlying page, including the background
    // outside a transparent or narrow scroll viewport, without this capsule.
    val pageSource = LocalView.current.rootView
        .findViewById<android.view.View>(android.R.id.content) as? android.view.ViewGroup
    val previewSource = pageSource ?: LocalCMScrollbarSource.current
    val text = rememberTextMeasurer()
    var pressed by remember { mutableStateOf(false) }
    var activeSection by remember { mutableIntStateOf(-1) }
    var lastLabel by remember { mutableStateOf("") }
    var trackHeight by remember { mutableIntStateOf(0) }
    var trackOrigin by remember { mutableStateOf(IntOffset.Zero) }
    val previewY = remember { CMProgressMotion(0f) }
    val previewGate = remember { CMProgressMotion(0f) }
    val fluidStrain = remember { CMProgressMotion(0f) }
    val fluidBend = remember { CMProgressMotion(0f) }
    val optical = com.lindicra.cms.ui.theme.LocalGlassMaterial.current.optical
    val liquidShader = remember {
        if (android.os.Build.VERSION.SDK_INT >= 33)
            android.graphics.RuntimeShader(INDEX_LIQUID_SHADER) else null
    }
    val selectedMotion = remember { CMProgressMotion(0f) }
    val seekJob = remember { arrayOfNulls<Job>(1) }
    val width by animateFloatAsState(
        if (pressed && pressThickening) 8f else 4f,
        spring(dampingRatio = 0.88f, stiffness = 420f), label = "scrollbarWidth"
    )
    val color = if (dark) Color.White else Color.Black
    val indexed = style != CMScrollbarStyle.Classic && sections.isNotEmpty()
    val selected = if (pressed && activeSection in sections.indices) activeSection
        else if (currentMetrics.maximum > 0f &&
            !(grid?.canScrollForward ?: scroll.canScrollForward)) sections.lastIndex.coerceAtLeast(0)
        else sections.indexOfLast {
            if (grid == null) it.scrollOffset <= currentMetrics.position
            else it.itemIndex <= grid.firstVisibleItemIndex
        }.coerceAtLeast(0)
    val pressGate by animateFloatAsState(if (pressed) 1f else 0f,
        spring(0.88f, 420f), label = "indexPress")
    LaunchedEffect(selected) { selectedMotion.animateTo(selected.toFloat(), scope, 540f, 0.86f) }
    LaunchedEffect(pressed, indexed) {
        previewGate.animateTo(if (pressed && indexed) 1f else 0f, scope, 420f, 0.82f)
    }
    LaunchedEffect(previewY, density) {
        snapshotFlow { previewY.velocity }.collect { speed ->
            val velocityDp = speed / density.density
            val drive = velocityDp / (900f + abs(velocityDp))
            fluidStrain.animateTo(0.34f * abs(drive), scope, 240f, 0.55f)
            fluidBend.animateTo(10f * drive, scope, 180f, 0.6f)
        }
    }
    suspend fun seek(fraction: Float, section: Int = -1) {
        val entry = currentSections.getOrNull(section)
        if (grid == null) {
            scroll.scrollTo(entry?.scrollOffset?.coerceIn(0, scroll.maxValue)
                ?: (fraction * scroll.maxValue).roundToInt())
        } else {
            val count = grid.layoutInfo.totalItemsCount
            if (count > 0) {
                if (entry != null) grid.scrollToItem(entry.itemIndex.coerceIn(0, count - 1))
                else if (fraction >= 1f) {
                    grid.scrollToItem(count - 1)
                    grid.scroll { scrollBy(currentMetrics.maximum) }
                } else {
                    // Variable-height grids use an estimate; explicit sections always seek exactly.
                    val columns = grid.layoutInfo.visibleItemsInfo.groupBy { it.row }
                        .values.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                    val rowHeight = grid.layoutInfo.visibleItemsInfo
                        .groupBy { it.row }.values
                        .map { line -> line.maxOf { it.size.height } }.average().toFloat()
                        .takeIf { it.isFinite() && it > 0f } ?: 1f
                    val stride = rowHeight + grid.layoutInfo.mainAxisItemSpacing
                    val target = fraction * currentMetrics.maximum
                    val row = (target / stride).toInt()
                    grid.scrollToItem((row * columns).coerceIn(0, count - 1),
                        (target - row * stride).roundToInt())
                }
            }
        }
    }
    val thumbMinimum = with(density) { 16.dp.toPx() }
    // A narrow rail keeps the full content margin; pointer capture continues outside it.
    val railWidth = if (indexed) 16.dp else 10.dp
    val hitWidth = with(density) { railWidth.toPx() }
    val compactCell = with(density) { 22.dp.toPx() }
    val layout = if (style == CMScrollbarStyle.Alphabet) indexLayout
        else CMScrollbarIndexLayout.Distributed
    val accepting by rememberUpdatedState(interactive && currentMetrics.maximum > 0f)
    val touchRegion = remember { CMSTouchRegion { accepting } }
    val railBounds = remember { arrayOf(androidx.compose.ui.geometry.Rect.Zero) }
    fun updateTouchBounds() {
        val bounds = railBounds[0]
        val extent = if (indexed) scrollbarIndexExtent(bounds.height,
            sections.size, compactCell, layout) else bounds.height
        touchRegion.bounds = bounds.copy(bottom = bounds.top + extent)
    }
    SideEffect { updateTouchBounds() }
    DisposableEffect(touchRegions, touchRegion) {
        touchRegions?.register(touchRegion)
        onDispose { touchRegions?.remove(touchRegion) }
    }
    Box(Modifier.fillMaxSize().padding(insets)) {
    Canvas(
        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(railWidth)
            .onSizeChanged { trackHeight = it.height }
            .onGloballyPositioned {
                val bounds = it.boundsInWindow()
                trackOrigin = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
                railBounds[0] = bounds
                updateTouchBounds()
            }
            .semantics {
                contentDescription = "滚动条"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    if (currentMetrics.maximum > 0f) currentMetrics.position / currentMetrics.maximum else 0f,
                    0f..1f
                )
                if (interactive) setProgress { value ->
                    scope.launch {
                        currentPress()
                        try { seek(value.coerceIn(0f, 1f)) }
                        finally { currentOverscroll(0f); currentRelease(0f) }
                    }
                    true
                }
            }
            .pointerInput(owner, interactive, indexed, style, layout) {
                if (!interactive) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.isConsumed || currentMetrics.maximum <= 0f ||
                        down.position.x < size.width - hitWidth ||
                        indexed && down.position.y > scrollbarIndexExtent(size.height.toFloat(),
                            currentSections.size, compactCell, layout)) return@awaitEachGesture
                    down.consume()
                    pressed = true
                    seekJob[0]?.cancel()
                    currentPress()
                    val channel = Channel<ScrollbarSeek>(Channel.CONFLATED)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var releaseVelocity = 0f
                    val initial = CMScrollbarGeometry.calculate(size.height.toFloat(),
                        currentMetrics.maximum, currentMetrics.position, 0f, thumbMinimum)
                    val grab = if (down.position.y in initial.top..(initial.top + initial.length))
                        down.position.y - initial.top else initial.length / 2f
                    val previewHalf = with(density) { 18.dp.toPx() }
                    fun previewTarget(y: Float, raw: Float, section: Int = activeSection): Float =
                        scrollbarPreviewAnchor(y, section, currentSections.size,
                            scrollbarIndexExtent(size.height.toFloat(), currentSections.size,
                                compactCell, layout)).coerceIn(previewHalf,
                            (size.height - previewHalf).coerceAtLeast(previewHalf)) +
                            CMRubberBand.position(raw, size.height.toFloat())
                    val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        var previousY = down.position.y
                        var previousSection = -1
                        var raw = 0f
                        try {
                            owner.stopScroll(MutatePriority.UserInput)
                            for (request in channel) {
                                val wasAtTop = !(grid?.canScrollBackward ?: scroll.canScrollBackward)
                                val wasAtBottom = !(grid?.canScrollForward ?: scroll.canScrollForward)
                                seek(request.fraction, request.section)
                                val atTop = !(grid?.canScrollBackward ?: scroll.canScrollBackward)
                                val atBottom = !(grid?.canScrollForward ?: scroll.canScrollForward)
                                raw = if (indexed) scrollbarIndexOverflow(raw,
                                    request.y - previousY, request.section == previousSection,
                                    wasAtTop, wasAtBottom, atTop, atBottom)
                                else scrollbarDragOverflow(request.y, grab,
                                    size.height.toFloat(), initial.length).let {
                                    if (it < 0f && atTop || it > 0f && atBottom) it else 0f
                                }
                                raw = raw.coerceIn(-size.height * 3f, size.height * 3f)
                                currentOverscroll(raw)
                                if (indexed) previewY.animateTo(previewTarget(request.y, raw, request.section),
                                    scope, 420f, 0.72f)
                                previousY = request.y
                                previousSection = request.section
                            }
                        } finally {
                            if (seekJob[0] === coroutineContext[Job]) {
                                previewY.animateTo(previewTarget(previewY.value, 0f),
                                    scope, 420f, 0.84f)
                                currentRelease(releaseVelocity)
                            }
                        }
                    }
                    seekJob[0] = job
                    val downExtent = scrollbarIndexExtent(size.height.toFloat(),
                        currentSections.size, compactCell, layout).coerceAtLeast(1f)
                    val downSection = (down.position.y / downExtent * currentSections.size)
                        .toInt().coerceIn(0, currentSections.lastIndex.coerceAtLeast(0))
                    if (previewGate.value < 0.001f)
                        previewY.follow(previewTarget(down.position.y, 0f, downSection))
                    fun send(y: Float) {
                        if (indexed) {
                            val extent = scrollbarIndexExtent(size.height.toFloat(),
                                currentSections.size, compactCell, layout).coerceAtLeast(1f)
                            val index = ((y / extent) * currentSections.size)
                                .toInt().coerceIn(0, currentSections.lastIndex)
                            activeSection = index
                            lastLabel = currentSections[index].label
                            channel.trySend(ScrollbarSeek(0f, index, y))
                        } else {
                            channel.trySend(ScrollbarSeek(scrollbarSeekFraction(y, grab,
                                size.height.toFloat(), initial.length), -1, y))
                        }
                    }
                    var released = false
                    try {
                        // Grabbing the thumb preserves the exact scroll position on down.
                        if (indexed || down.position.y !in initial.top..(initial.top + initial.length))
                            send(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (change.position != change.previousPosition) send(change.position.y)
                            change.consume()
                            if (!change.pressed) {
                                releaseVelocity = -tracker.calculateVelocity().y
                                released = true
                                break
                            }
                        }
                    } finally {
                        channel.close()
                        pressed = false
                        // Drain the latest requested position on normal release.
                        if (!released) job.cancel()
                    }
                }
            }
    ) {
        val m = currentMetrics
        if (m.maximum <= 0f) return@Canvas
        val geometry = CMScrollbarGeometry.calculate(size.height, m.maximum, m.position,
            overscroll, thumbMinimum)
        val w = width.dp.toPx()
        if (!indexed) {
            drawRoundRect(color.copy(alpha = (if (dark) 0.3f else 0.2f) + 0.25f * pressGate.coerceIn(0f, 1f)),
                Offset(size.width - w, geometry.top),
                Size(w, geometry.length), CornerRadius(w / 2))
        } else {
            val extent = scrollbarIndexExtent(size.height, sections.size, compactCell, layout)
            val cell = extent / sections.size
            val gate = pressGate.coerceIn(0f, 1f)
            val highlight = selectedMotion.value.coerceIn(0f, sections.lastIndex.toFloat())
            drawRoundRect(color.copy(alpha = 0.045f * gate),
                Offset(0f, 0f), Size(hitWidth, extent),
                CornerRadius(10.dp.toPx()))
            if (style == CMScrollbarStyle.Alphabet) {
                drawRoundRect(color.copy(alpha = 0.07f + 0.04f * gate),
                    Offset(0f, highlight * cell + (cell - minOf(cell, 18.dp.toPx())) / 2),
                    Size(size.width, minOf(cell, 18.dp.toPx())), CornerRadius(size.width / 2))
            }
            sections.forEachIndexed { index, entry ->
                val emphasis = (1f - abs(index - highlight)).coerceIn(0f, 1f)
                val label = text.measure(entry.label, TextStyle(
                    color = color.copy(alpha = 0.45f + 0.45f * emphasis),
                    fontSize = (10f + if (pressThickening) 2f * gate else 0f).sp,
                    fontWeight = FontWeight.Medium
                ))
                if (style == CMScrollbarStyle.Sections) {
                    drawRoundRect(color.copy(alpha = 0.12f),
                        Offset(size.width - w, index * cell + 3.dp.toPx()),
                        Size(w, (cell - 6.dp.toPx()).coerceAtLeast(1f)), CornerRadius(w / 2))
                }
                drawText(label, topLeft = Offset(
                    if (style == CMScrollbarStyle.Sections)
                        size.width - w - 3.dp.toPx() - label.size.width
                    else (size.width - label.size.width) / 2,
                    index * cell + (cell - label.size.height) / 2))
            }
            if (style == CMScrollbarStyle.Sections) {
                drawRoundRect(color.copy(alpha = 0.3f),
                    Offset(size.width - w, highlight * cell + 3.dp.toPx()),
                    Size(w, (cell - 6.dp.toPx()).coerceAtLeast(1f)), CornerRadius(w / 2))
            }
        }
    }
    }
    if (lastLabel.isNotEmpty() && (pressed && indexed ||
            abs(previewGate.value) > 0.0001f || abs(previewGate.velocity) > 0.001f)) {
        val gap = with(density) { 4.dp.roundToPx() }
        val renderMargin = with(density) { 48.dp.roundToPx() }
        val centerY = previewY.value
        val position = remember(trackOrigin, trackHeight, gap, centerY, renderMargin) {
            object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                    layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                    val half = popupContentSize.height / 2f
                    return IntOffset(
                        trackOrigin.x - popupContentSize.width - gap + renderMargin,
                        (trackOrigin.y + centerY - half).roundToInt()
                    )
                }
            }
        }
        // A separate window keeps this glass outside its native content sampling source.
        Popup(popupPositionProvider = position,
            properties = PopupProperties(
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    // Native RenderNode glass and the liquid deformation shader
                    // both require a hardware canvas in this separate window.
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                dismissOnBackPress = false, dismissOnClickOutside = false)) {
            val gate = previewGate.value
            val targetWidth = with(density) {
                text.measure(lastLabel, TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold))
                    .size.width.toDp().value + 24f
            }.coerceIn(56f, 144f)
            val capsuleWidth by animateFloatAsState(targetWidth, spring(0.88f, 420f),
                label = "indexCapsuleWidth")
            CMBlurLayer(
                modifier = Modifier.graphicsLayer {
                    clip = false
                    if (android.os.Build.VERSION.SDK_INT >= 33 && optical && liquidShader != null) {
                        liquidShader.setFloatUniform("layerSize", size.width, size.height)
                        liquidShader.setFloatUniform("glassSize",
                            with(density) { capsuleWidth.dp.toPx() },
                            with(density) { 36.dp.toPx() })
                        liquidShader.setFloatUniform("strain", fluidStrain.value)
                        // Bend is kept in dp in the spring; the shader uses physical pixels.
                        liquidShader.setFloatUniform("bend", fluidBend.value * density.density)
                        renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(
                            liquidShader, "content").asComposeRenderEffect()
                    } else {
                        renderEffect = null
                        scaleY = if (optical) 1f + fluidStrain.value else 1f
                        scaleX = 1f / scaleY
                    }
                }.padding(48.dp).graphicsLayer {
                    alpha = (gate * 1.4f).coerceIn(0f, 1f)
                    scaleX = 0.68f + 0.32f * gate
                    scaleY = 0.82f + 0.18f * gate
                    translationX = with(density) { 12.dp.toPx() } * (1f - gate)
                    transformOrigin = TransformOrigin(1f, 0.5f)
                    clip = false
                },
                maxRadius = 10.dp,
                radius = { 10.dp * (1f - previewGate.value.coerceIn(0f, 1f)) }
            ) {
                CMSGlassButton(
                    onClick = {},
                    enabled = false,
                    width = capsuleWidth.dp,
                    height = 36.dp,
                    cornerRadius = 18.dp,
                    sampleContent = true,
                    samplingSource = previewSource,
                    menuSurface = true,
                    shadowStrength = 1.2f,
                    highlightMaxWidth = 0.8.dp,
                    touchEffectIntensity = 0f,
                    glassBackground = if (dark) Color(0xFF232329).copy(alpha = 0.4f)
                        else Color.White.copy(alpha = 0.4f)
                ) { foreground ->
                    AnimatedContent(
                        targetState = lastLabel,
                        transitionSpec = {
                            (fadeIn(spring(0.9f, 540f)) +
                                scaleIn(spring(0.9f, 540f), 0.86f) +
                                slideInVertically(spring(0.9f, 540f)) { it / 4 })
                                .togetherWith(fadeOut(spring(0.9f, 540f)) +
                                    scaleOut(spring(0.9f, 540f), 1.08f) +
                                    slideOutVertically(spring(0.9f, 540f)) { -it / 4 })
                                .using(SizeTransform(clip = false))
                        },
                        label = "indexLabel"
                    ) { label ->
                        val labelBlur by transition.animateFloat(
                            transitionSpec = { spring(0.9f, 540f) }, label = "indexLabelBlur"
                        ) { state -> if (state == EnterExitState.Visible) 0f else 8f }
                        CMBlurLayer(maxRadius = 8.dp, radius = { labelBlur.coerceIn(0f, 8f).dp }) {
                        Text(label, fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                            color = foreground, maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
                        }
                    }
                }
            }
        }
    }
}
