package com.lindicra.cms

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

@Composable
internal fun rememberCMScrollFlingBehavior(): FlingBehavior {
    val resistance = with(LocalDensity.current) { 35.dp.toPx() }
    return remember(resistance) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (abs(initialVelocity) < 4f) return 0f
                val trajectory = CMFlingTrajectory(initialVelocity, resistance)
                val start = System.nanoTime()
                var previous = 0f
                while (true) {
                    val now = withFrameNanos { it }
                    val elapsed = (now - start) / 1_000_000_000f
                    val position = trajectory.position(elapsed)
                    val delta = position - previous
                    previous = position
                    val consumed = scrollBy(delta)
                    if (abs(delta - consumed) > 0.5f) {
                        // The edge receives actual remaining velocity exactly once.
                        return trajectory.velocity(elapsed)
                    }
                    if (elapsed >= trajectory.duration) return 0f
                }
            }
        }
    }
}

@Composable
@Deprecated("Use CMSScrollView", ReplaceWith("CMSScrollView(modifier, scrollState, scrollEnabled, showScrollbar, springOverscroll, lazyGridState, onOverscrollChanged, axisDragBridge, onUserScroll, onScrollRelease, onScrollPress, sampleGlassContent, scrollbarStyle, scrollbarInteractive, scrollbarPressThickening, scrollbarSections, scrollbarInsets, scrollbarIndexLayout, content)"))
fun CMScrollBox(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    scrollEnabled: Boolean = true,
    showScrollbar: Boolean = false,
    springOverscroll: Boolean = false,
    lazyGridState: LazyGridState? = null,
    onOverscrollChanged: (Float) -> Unit = {},
    axisDragBridge: CMVerticalDragBridge? = LocalCMSPageBridge.current,
    onUserScroll: (Float) -> Unit = {},
    onScrollRelease: (Float) -> Unit = {},
    onScrollPress: () -> Boolean = { false },
    sampleGlassContent: Boolean = !LocalCMSPagerSampling.current,
    scrollbarStyle: CMScrollbarStyle = CMScrollbarStyle.Classic,
    scrollbarInteractive: Boolean = true,
    scrollbarPressThickening: Boolean = true,
    scrollbarSections: List<CMScrollSection> = emptyList(),
    scrollbarInsets: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(8.dp),
    scrollbarIndexLayout: CMScrollbarIndexLayout = CMScrollbarIndexLayout.TopPacked,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollBridge = axisDragBridge ?: remember { CMVerticalDragBridge() }
    val regions = LocalCMSTouchRegions.current ?: remember { CMSTouchRegionRegistry() }
    val policy = LocalCMSTouchPriority.current
    val pageCoordinated = LocalCMSPageBridge.current != null
    // Secondary-page glass and menus capture this viewport, never the enclosing
    // Column containing the top bar. Keep the existing scroll/gesture nodes inside.
    if (sampleGlassContent) {
        var scrollbarSource by remember {
            androidx.compose.runtime.mutableStateOf<android.view.ViewGroup?>(null)
        }
        CMGlassContentSource(
            modifier = modifier,
            hazeState = com.lindicra.cms.ui.theme.LocalGlassHazeState.current,
            onSourceReady = { scrollbarSource = it }
        ) {
            CompositionLocalProvider(LocalCMScrollbarSource provides scrollbarSource) {
            CMScrollBox(
                modifier = Modifier.fillMaxSize(),
                scrollState = scrollState,
                scrollEnabled = scrollEnabled,
                showScrollbar = showScrollbar,
                springOverscroll = springOverscroll,
                lazyGridState = lazyGridState,
                onOverscrollChanged = onOverscrollChanged,
                axisDragBridge = scrollBridge,
                onUserScroll = onUserScroll,
                onScrollRelease = onScrollRelease,
                onScrollPress = onScrollPress,
                sampleGlassContent = false,
                scrollbarStyle = scrollbarStyle,
                scrollbarInteractive = scrollbarInteractive,
                scrollbarPressThickening = scrollbarPressThickening,
                scrollbarSections = scrollbarSections,
                scrollbarInsets = scrollbarInsets,
                scrollbarIndexLayout = scrollbarIndexLayout,
                content = content
            )
            }
        }
        return
    }
    val fling = rememberCMScrollFlingBehavior()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edge = remember { CMElasticMotion() }
    val edgeAnchor = remember { CMBoundaryAnchor() }
    val reportEdge by rememberUpdatedState(onOverscrollChanged)
    val userScroll by rememberUpdatedState(onUserScroll)
    val scrollRelease by rememberUpdatedState(onScrollRelease)
    val scrollPress by rememberUpdatedState(onScrollPress)
    var viewport by remember { mutableIntStateOf(0) }
    var contentHeight by remember { mutableIntStateOf(0) }
    val owner: ScrollableState = lazyGridState ?: scrollState
    val dragging = remember { booleanArrayOf(false) }
    val touchPressed = remember { booleanArrayOf(false) }
    val releaseReported = remember { booleanArrayOf(false) }
    val bridgeJob = remember { arrayOfNulls<Job>(1) }
    val interruptJob = remember { arrayOfNulls<Job>(1) }
    val bridgeDeltas = remember { arrayOfNulls<Channel<Float>>(1) }
    val bridgeRelease = remember { floatArrayOf(0f) }
    val scrollbarHeld = remember { booleanArrayOf(false) }
    val scrollbarRawEdge = remember { floatArrayOf(0f) }
    val nativeFlingGeneration = remember { longArrayOf(-1L) }
    val touchCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun dimension() = viewport.toFloat().coerceAtLeast(1f)
    fun atEdge(delta: Float) = if (delta > 0f) {
        lazyGridState?.let { !it.canScrollBackward } ?: !scrollState.canScrollBackward
    } else {
        lazyGridState?.let { !it.canScrollForward } ?: !scrollState.canScrollForward
    }
    fun stopEdge() {
        edge.stop(dimension(), edgeAnchor.direction)
        dragging[0] = true
    }
    fun reconcileEdge() {
        val attached = edgeAnchor.isAttached(
            lazyGridState?.canScrollBackward ?: scrollState.canScrollBackward,
            lazyGridState?.canScrollForward ?: scrollState.canScrollForward
        )
        if (!attached) {
            edge.reset()
            edgeAnchor.clear()
            dragging[0] = false
        }
    }
    fun releaseEdge(velocity: Float) {
        dragging[0] = false
        if (!springOverscroll) {
            edge.reset()
            edgeAnchor.clear()
            return
        }
        edge.release(velocity, dimension(), scope,
            dampingRatio = 0.84f)
    }
    fun prepareFling(velocity: Float): Float {
        reconcileEdge()
        if (!releaseReported[0]) {
            scrollRelease(velocity)
            releaseReported[0] = true
        }
        if (edge.offset == 0f) {
            dragging[0] = false
            return 0f
        }
        // An inward flick must continue into content after unwinding the stretch.
        // Sending it to the spring alone leaves content pinned at a ghost boundary.
        if (edge.offset * velocity < 0f) {
            stopEdge()
            return 0f
        }
        releaseEdge(velocity)
        return velocity
    }
    fun finishFling(remaining: Float): Float {
        reconcileEdge()
        if (springOverscroll && remaining != 0f && atEdge(remaining)) {
            edgeAnchor.begin(remaining)
            releaseEdge(remaining)
            return remaining
        }
        if (dragging[0] && !edge.moving) releaseEdge(0f)
        return 0f
    }
    val connection = remember(owner, scrollEnabled, springOverscroll, scrollBridge) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (scrollEnabled) reconcileEdge()
                if (scrollEnabled && source == NestedScrollSource.UserInput) userScroll(available.y)
                if (!springOverscroll || !scrollEnabled ||
                    edge.offset == 0f || edge.offset * available.y >= 0f) return Offset.Zero
                // Native grid flings dispatch SideEffect; bridge flings use the same
                // displacement split below. Neither may lose the inward remainder.
                val used = edge.consumeInward(available.y, dimension(), edgeAnchor.direction)
                dragging[0] = true
                return Offset(0f, used)
            }

            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset {
                if (scrollEnabled && source == NestedScrollSource.UserInput) reconcileEdge()
                if (!springOverscroll || !scrollEnabled || available.y == 0f ||
                    source != NestedScrollSource.UserInput || !atEdge(available.y)) return Offset.Zero
                stopEdge()
                edgeAnchor.begin(available.y)
                edge.drag(available.y, dimension())
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!scrollEnabled || scrollBridge.active || touchPressed[0]) return Velocity.Zero
                nativeFlingGeneration[0] = scrollBridge.gestureGeneration
                return Velocity(0f, prepareFling(available.y))
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!scrollEnabled || scrollBridge.active || touchPressed[0] ||
                    nativeFlingGeneration[0] != scrollBridge.gestureGeneration) return Velocity.Zero
                return Velocity(0f, finishFling(available.y))
            }
        }
    }
    DisposableEffect(scrollBridge, owner, scrollEnabled, springOverscroll) {
        val bridge = scrollBridge
        if (bridge != null) {
            bridge.selectionHoldBlocked = { abs(edge.offset) > 0.5f || edge.moving }
            bridge.interrupt = {
                val searchMoving = scrollPress()
                val moving = owner.isScrollInProgress || edge.moving ||
                    bridgeJob[0]?.isActive == true || searchMoving
                val previousJob = bridgeJob[0]
                val previousDeltas = bridgeDeltas[0]
                bridge.gestureGeneration++
                bridgeJob[0] = null
                bridgeDeltas[0] = null
                touchPressed[0] = true
                releaseReported[0] = false
                previousJob?.cancel()
                previousDeltas?.cancel()
                bridge.active = false
                stopEdge()
                interruptJob[0]?.cancel()
                interruptJob[0] = if (owner.isScrollInProgress) {
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        owner.stopScroll(MutatePriority.UserInput)
                    }
                } else null
                moving
            }
            bridge.begin = {
                bridgeJob[0]?.cancel()
                bridgeDeltas[0]?.cancel()
                bridge.gestureGeneration++
                val generation = bridge.gestureGeneration
                val interrupted = interruptJob[0]
                bridgeRelease[0] = 0f
                val deltas = Channel<Float>(Channel.UNLIMITED)
                bridgeDeltas[0] = deltas
                bridge.active = true
                fun ownsContact() = bridge.gestureGeneration == generation && bridgeDeltas[0] === deltas
                bridgeJob[0] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        interrupted?.join()
                        currentCoroutineContext().ensureActive()
                        if (!ownsContact()) return@launch
                        owner.scroll(if (lazyGridState != null) MutatePriority.PreventUserInput
                            else MutatePriority.UserInput) {
                            stopEdge()
                            for (delta in deltas) {
                                if (!ownsContact()) break
                                val pre = connection.onPreScroll(Offset(0f, delta), NestedScrollSource.UserInput).y
                                val used = -scrollBy(-(delta - pre))
                                connection.onPostScroll(
                                    Offset(0f, used), Offset(0f, delta - pre - used), NestedScrollSource.UserInput
                                )
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        if (!ownsContact()) return@launch
                        // Retain bridge ownership through its fling. Pointer observers
                        // and cancelled native flings must not also release this edge.
                        touchPressed[0] = false
                        val velocity = bridgeRelease[0]
                        val pre = prepareFling(velocity)
                        owner.scroll(MutatePriority.Default) {
                            val edgeScope = CMEdgeFlingScope(this) { delta ->
                                reconcileEdge()
                                edge.consumeInward(delta, dimension(), edgeAnchor.direction)
                            }
                            val remaining = with(fling) {
                                edgeScope.performFling(-(velocity - pre))
                            }
                            currentCoroutineContext().ensureActive()
                            if (ownsContact()) finishFling(-remaining)
                        }
                    } finally {
                        if (ownsContact()) {
                            bridge.active = false
                            bridgeDeltas[0] = null
                            bridgeJob[0] = null
                            if (!touchPressed[0] && dragging[0] && !edge.moving) releaseEdge(0f)
                        }
                        deltas.cancel()
                    }
                }
            }
            bridge.drag = { bridgeDeltas[0]?.trySend(it); Unit }
            bridge.end = { bridgeRelease[0] = it; bridgeDeltas[0]?.close(); Unit }
            bridge.cancel = {
                bridge.gestureGeneration++
                bridgeJob[0]?.cancel()
                bridgeDeltas[0]?.cancel()
                bridgeJob[0] = null
                bridgeDeltas[0] = null
                bridge.active = false
                if (!touchPressed[0] && dragging[0] && !edge.moving) releaseEdge(0f)
            }
        }
        onDispose {
            bridgeJob[0]?.cancel()
            interruptJob[0]?.cancel()
            bridgeDeltas[0]?.cancel()
            edge.stop(dimension())
            reportEdge(0f)
            if (bridge != null) {
                bridge.gestureGeneration++
                bridge.begin = {}
                bridge.drag = {}
                bridge.end = {}
                bridge.cancel = {}
                bridge.interrupt = { false }
                bridge.selectionHoldBlocked = { false }
                bridge.active = false
            }
        }
    }
    LaunchedEffect(edge) {
        snapshotFlow { edge.offset }.collect { reportEdge(it) }
    }
    LaunchedEffect(springOverscroll, scrollEnabled) {
        if (!springOverscroll || !scrollEnabled) {
            edge.reset()
            edgeAnchor.clear()
            dragging[0] = false
        }
    }
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { viewport = it.height }
            .onGloballyPositioned { touchCoordinates[0] = it }
            .pointerInput(owner, scrollEnabled) {
                val velocity = VelocityTracker()
                var glassContact = false
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            velocity.resetTracking()
                            releaseReported[0] = false
                            nativeFlingGeneration[0] = -1L
                            val down = event.changes.firstOrNull()
                            val target = down?.let { change ->
                                touchCoordinates[0]?.takeIf { it.isAttached }?.let {
                                    regions.at(it.localToWindow(change.position))
                                }
                            }
                            glassContact = target?.nativeGlass == true
                        }
                        event.changes.firstOrNull()?.let {
                            velocity.addPosition(it.uptimeMillis, it.position)
                        }
                        touchPressed[0] = event.changes.any { it.pressed }
                        if (scrollEnabled && !scrollBridge.active &&
                            event.type == PointerEventType.Press) {
                            val searchMoving = scrollPress()
                            if (owner.isScrollInProgress || edge.moving || searchMoving) {
                                // Stop momentum in Initial, but let this same down reach
                                // the item. Only actual dragging should cancel its click.
                                // The axis coordinator performs the interrupt for
                                // Column content; only a native grid needs this path.
                                if (lazyGridState != null) {
                                    scrollBridge.interrupt()
                                }
                            }
                            stopEdge()
                        }
                        if (scrollEnabled && !touchPressed[0] && !scrollbarHeld[0] &&
                            !scrollBridge.active &&
                            !releaseReported[0]) {
                            val speed = velocity.calculateVelocity()
                            scrollRelease(if (!glassContact && abs(speed.y) > abs(speed.x) * 1.2f)
                                speed.y else 0f)
                            releaseReported[0] = true
                        }
                        if (!event.changes.any { it.pressed } && dragging[0] && !scrollbarHeld[0] &&
                            !scrollBridge.active && !owner.isScrollInProgress) {
                            releaseEdge(0f)
                        }
                    }
                }
            }
            .cmsVerticalScrollTouchPriority(scrollBridge, regions,
                enabled = scrollEnabled && !pageCoordinated && lazyGridState == null, policy = policy)
            .then(if (scrollEnabled) Modifier.nestedScroll(connection) else Modifier)
    ) {
        CompositionLocalProvider(
            LocalOverscrollFactory provides null,
            LocalCMSTouchRegions provides regions,
            LocalCMVerticalDragBridge provides if (scrollEnabled) scrollBridge else null
        ) {
            Box(Modifier.fillMaxSize().padding(end =
                if (showScrollbar && scrollbarStyle != CMScrollbarStyle.Classic &&
                    scrollbarSections.isNotEmpty()) 12.dp else 0.dp
            ).graphicsLayer {
                translationY = if (springOverscroll) edge.offset else 0f
                clip = false
            }) {
                if (lazyGridState != null) {
                    Column(Modifier.fillMaxSize(), content = content)
                } else {
                    Column(
                        Modifier.fillMaxWidth().then(
                            if (scrollEnabled) Modifier.cmsScrollViewport(scrollState, horizontal = false)
                            else Modifier
                        )
                    ) {
                        Column(Modifier.fillMaxWidth().onSizeChanged { contentHeight = it.height }, content = content)
                        if (viewport > contentHeight) {
                            Spacer(Modifier.fillMaxWidth().height(
                                with(density) { (viewport - contentHeight).toDp() }
                            ))
                        }
                    }
                }
            }
        }
        if (showScrollbar && scrollEnabled) {
            CMInteractiveScrollbar(
                scrollState, lazyGridState, if (springOverscroll) edge.offset else 0f,
                scrollbarStyle, scrollbarInteractive, scrollbarPressThickening,
                scrollbarSections, scrollbarInsets,
                scrollbarIndexLayout,
                onPress = {
                    edge.reset()
                    edgeAnchor.clear()
                    dragging[0] = false
                    scrollbarHeld[0] = true
                    scrollbarRawEdge[0] = 0f
                },
                onOverscrollDrag = { raw ->
                    if (springOverscroll) {
                        // Thumb movement above the track stretches content down, and vice versa.
                        val contentRaw = -raw
                        if (contentRaw == 0f) {
                            edge.reset()
                            edgeAnchor.clear()
                        } else {
                            edgeAnchor.begin(contentRaw)
                            edge.stop(dimension())
                            edge.drag(contentRaw - scrollbarRawEdge[0], dimension())
                        }
                        scrollbarRawEdge[0] = contentRaw
                    }
                },
                onRelease = { velocity ->
                    scrollbarHeld[0] = false
                    releaseReported[0] = true
                    releaseEdge(if (scrollbarRawEdge[0] != 0f) velocity else 0f)
                },
                touchRegions = regions
            )
        }
    }
}
