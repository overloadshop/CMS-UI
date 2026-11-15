package com.lindicra.cms

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Window coordinates also work across the native sampling host's nested ComposeView. */
internal class CMSTouchRegion(val nativeGlass: Boolean = false, val accepts: () -> Boolean) {
    var bounds = Rect.Zero
}

internal class CMSTouchRegionRegistry {
    private val regions = mutableListOf<CMSTouchRegion>()
    fun register(region: CMSTouchRegion) { regions.add(region) }
    fun remove(region: CMSTouchRegion) { regions.remove(region) }
    fun at(point: Offset): CMSTouchRegion? = regions.asReversed()
        .filter { it.accepts() && it.bounds.contains(point) }
        .minByOrNull { it.bounds.width * it.bounds.height }
}

internal val LocalCMSTouchRegions = staticCompositionLocalOf<CMSTouchRegionRegistry?> { null }

/** Reserve the actual native viewport, excluding blur/shadow overflow. */
@Composable
internal fun Modifier.cmsGlassDragRegion(enabled: Boolean): Modifier {
    val registry = LocalCMSTouchRegions.current
    val accepting by rememberUpdatedState(enabled)
    val region = remember { CMSTouchRegion(nativeGlass = true) { accepting } }
    val bridge = LocalCMVerticalDragBridge.current
    val interruptOnDown = LocalCMSTouchPriority.current.interruptOnDown
    DisposableEffect(registry, region) {
        registry?.register(region)
        onDispose { registry?.remove(region) }
    }
    val positioned = onGloballyPositioned { region.bounds = it.boundsInWindow() }
    return if (!enabled) positioned else positioned.pointerInput(bridge, interruptOnDown) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (interruptOnDown) bridge?.interrupt?.invoke()
            // No consumption: AndroidView retains down, move, up and native deformation.
        }
    }
}

/**
 * Horizontal document/table scrolling with the same policy as Pager and PageView.
 * The nearest registered viewport owns this contact; its vertical component uses the
 * surrounding CMS ScrollView's bridge, including direction changes without lifting.
 */
@Composable
fun Modifier.cmsHorizontalScrollTouchPriority(
    state: ScrollState,
    enabled: Boolean = true,
    reverseScrolling: Boolean = false,
    policy: CMSTouchPriority = LocalCMSTouchPriority.current
): Modifier {
    val registry = LocalCMSTouchRegions.current
    val accepting by rememberUpdatedState(enabled && state.maxValue > 0)
    val region = remember(state) { CMSTouchRegion { accepting } }
    DisposableEffect(registry, region) {
        registry?.register(region)
        onDispose { registry?.remove(region) }
    }
    val bridge = LocalCMVerticalDragBridge.current
    return axisScrollTouchPriority(state, bridge, registry, region, enabled, reverseScrolling, policy)
        .cmsScrollViewport(state, horizontal = true, enabled = enabled,
            reverseScrolling = reverseScrolling)
}

/**
 * Foundation supplies layout and clipping, but no second touch drag recognizer.
 * Wheel and accessibility actions retain their own input paths without toggling
 * enabled during a contact (which would cancel and restart Foundation gestures).
 */
@Composable
internal fun Modifier.cmsScrollViewport(
    state: ScrollState,
    horizontal: Boolean,
    enabled: Boolean = true,
    reverseScrolling: Boolean = false
): Modifier {
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reversed = reverseScrolling xor (horizontal && rtl)
    val context = LocalContext.current
    val wheelScale = remember(context, horizontal) {
        val configuration = android.view.ViewConfiguration.get(context)
        if (horizontal) configuration.scaledHorizontalScrollFactor
        else configuration.scaledVerticalScrollFactor
    }
    val inputs = if (!enabled) this else semantics {
        val range = ScrollAxisRange(
            value = { state.value.toFloat() },
            maxValue = { state.maxValue.toFloat() },
            reverseScrolling = reversed
        )
        if (horizontal) horizontalScrollAxisRange = range else verticalScrollAxisRange = range
        scrollBy { x, y ->
            val amount = (if (horizontal) x else y) * if (reversed) -1f else 1f
            scope.launch {
                state.scroll(MutatePriority.Default) { scrollBy(amount) }
            }
            true
        }
    }.pointerInput(state, horizontal, reversed, wheelScale) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.type != PointerEventType.Scroll || event.changes.any { it.isConsumed }) continue
                val amount = event.changes.sumOf {
                    (if (horizontal) it.scrollDelta.x else it.scrollDelta.y).toDouble()
                }.toFloat() * wheelScale * if (reversed) -1f else 1f
                if (amount == 0f) continue
                event.changes.forEach { it.consume() }
                scope.launch {
                    state.scroll(MutatePriority.UserInput) { scrollBy(amount) }
                }
            }
        }
    }
    return if (horizontal) inputs.horizontalScroll(state, enabled = false,
        reverseScrolling = reverseScrolling)
    else inputs.verticalScroll(state, enabled = false, reverseScrolling = reverseScrolling)
}

/** Initial-pass observation can reclaim vertical movement from native glass children. */
@Composable
internal fun Modifier.cmsVerticalScrollTouchPriority(
    bridge: CMSVerticalDragBridge,
    registry: CMSTouchRegionRegistry,
    enabled: Boolean,
    policy: CMSTouchPriority
): Modifier = axisScrollTouchPriority(null, bridge, registry, null, enabled, false, policy)

/** Custom edge physics uses the same recognizer and reservation as ordinary wide content. */
internal interface CMSHorizontalScrollPhysics {
    fun interrupt()
    fun drag(scope: ScrollScope, delta: Float, scrollDirection: Float)
    suspend fun fling(scope: ScrollScope, velocity: Float, scrollDirection: Float)
    fun cancel()
}

@Composable
internal fun Modifier.cmsEdgeScrollTouchPriority(
    horizontal: ScrollState?,
    vertical: CMSVerticalDragBridge?,
    region: CMSTouchRegion,
    registry: CMSTouchRegionRegistry?,
    physics: CMSHorizontalScrollPhysics?
): Modifier = axisScrollTouchPriority(horizontal, vertical, registry, region,
    true, false, LocalCMSTouchPriority.current, physics)

@Composable
private fun Modifier.axisScrollTouchPriority(
    horizontal: ScrollState?,
    bridge: CMSVerticalDragBridge?,
    registry: CMSTouchRegionRegistry?,
    region: CMSTouchRegion?,
    enabled: Boolean,
    reverseScrolling: Boolean,
    policy: CMSTouchPriority,
    physics: CMSHorizontalScrollPhysics? = null
): Modifier {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fling = rememberCMScrollFlingBehavior()
    val scrollDirection = if (rtl xor reverseScrolling) 1f else -1f
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val jobs = remember(horizontal) { arrayOfNulls<Job>(1) }
    val interrupts = remember(horizontal) { arrayOfNulls<Job>(1) }
    val activeVertical = remember(bridge) { arrayOfNulls<CMVerticalDragContinuity>(1) }
    DisposableEffect(horizontal, bridge, enabled, policy, rtl, reverseScrolling, density, physics) {
        onDispose {
            jobs[0]?.cancel()
            interrupts[0]?.cancel()
            activeVertical[0]?.cancel()
            physics?.cancel()
        }
    }
    val positioned = onGloballyPositioned {
        coordinates[0] = it
        region?.bounds = it.boundsInWindow()
    }
    return if (!enabled) positioned else positioned.pointerInput(
        horizontal, bridge, registry, region, policy, density, rtl, reverseScrolling, physics
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // An explicit ancestor coordinator is already delivering this contact.
            if (down.isConsumed) return@awaitEachGesture
            val windowPoint = coordinates[0]?.takeIf { it.isAttached }?.localToWindow(down.position)
            val motion = CMSWindowMotion(windowPoint ?: down.position)
            val target = windowPoint?.let { registry?.at(it) }
            if (region == null && target != null ||
                region != null && (!region.accepts() || target != null && target !== region)) {
                return@awaitEachGesture
            }
            if (policy.interruptOnDown) {
                jobs[0]?.cancel()
                interrupts[0]?.cancel()
                if (horizontal != null) {
                    interrupts[0] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        horizontal.stopScroll(MutatePriority.UserInput)
                    }
                }
                bridge?.interrupt?.invoke()
                physics?.interrupt()
            }
            val session = CMSTouchPrioritySession(viewConfiguration.touchSlop, policy)
            val vertical = bridge?.let {
                CMVerticalDragContinuity(it, viewConfiguration.touchSlop, policy)
            }
            activeVertical[0] = vertical
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, motion.position)
            var deltas: Channel<Float>? = null
            var dragJob: Job? = null
            var releaseX = 0f
            var claimed = false
            var lifted = false
            val interrupted = interrupts[0]
            val intentThreshold = viewConfiguration.touchSlop *
                (policy.slopMultiplier.takeIf { it.isFinite() } ?: 1f).coerceIn(0.1f, 8f)
            fun startHorizontal() {
                if (deltas != null || horizontal == null) return
                val channel = Channel<Float>(Channel.UNLIMITED)
                deltas = channel
                dragJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        interrupted?.join()
                        horizontal.scroll(MutatePriority.UserInput) {
                            for (delta in channel) {
                                if (physics != null) physics.drag(this, delta, scrollDirection)
                                else scrollBy(delta * scrollDirection)
                            }
                        }
                        horizontal.scroll(MutatePriority.Default) {
                            if (physics != null) physics.fling(this, releaseX, scrollDirection)
                            else with(fling) { performFling(releaseX * scrollDirection) }
                        }
                    } finally { channel.cancel() }
                }
                jobs[0] = dragJob
            }
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.count { it.pressed } > 1 ||
                        !claimed && change.isConsumed &&
                            (horizontal == null || policy.respectConsumedGestures)) break
                    val point = coordinates[0]?.takeIf { it.isAttached }
                        ?.localToWindow(change.position) ?: break
                    val delta = motion.add(point)
                    var finalObserved = false
                    if (!claimed && session.axis == null &&
                        change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) {
                        val final = awaitPointerEvent(PointerEventPass.Final)
                        finalObserved = true
                        if (final.changes.any { it.id == down.id && it.isConsumed }) break
                    }
                    session.add(change.uptimeMillis, delta.x, delta.y)
                    tracker.addPosition(change.uptimeMillis, motion.position)
                    if (change.pressed || delta != Offset.Zero) {
                        if (horizontal != null && (deltas != null ||
                            session.axis == CMSTouchAxis.Horizontal)) {
                            val first = deltas == null
                            startHorizontal()
                            val amount = if (first) sign(session.recentX) *
                                (abs(session.recentX) - intentThreshold).coerceAtLeast(0f) else delta.x
                            deltas?.trySend(amount)
                            claimed = true
                        }
                        // Start each axis only after the shared policy confirms it.
                        // Once started, its delivery stays continuous across later turns.
                        vertical?.drag(
                            session.axis == CMSTouchAxis.Vertical,
                            delta.y, session.recentY
                        )
                        claimed = claimed || vertical?.started == true
                    }
                    if (claimed) change.consume()
                    if (!change.pressed) {
                        lifted = true
                        val speed = tracker.calculateVelocity()
                        val limit = 8000f * density
                        releaseX = if (deltas != null && session.axis == CMSTouchAxis.Horizontal)
                            speed.x.coerceIn(-limit, limit) else 0f
                        vertical?.end(if (session.axis == CMSTouchAxis.Vertical)
                            speed.y.coerceIn(-limit, limit) else 0f)
                        deltas?.close()
                        break
                    }
                    if (!claimed && !finalObserved) {
                        val final = awaitPointerEvent(PointerEventPass.Final)
                        val consumed = final.changes.any { it.id == down.id && it.isConsumed }
                        // A stationary long press belongs to text selection / a native menu.
                        val held = session.axis == null && change.uptimeMillis - down.uptimeMillis >=
                            viewConfiguration.longPressTimeoutMillis
                        if (consumed && (policy.respectConsumedGestures || held)) break
                    }
                }
            } finally {
                if (!lifted) {
                    deltas?.cancel()
                    dragJob?.cancel()
                    vertical?.cancel()
                }
                if (!lifted || deltas == null) physics?.cancel()
                if (activeVertical[0] === vertical) activeVertical[0] = null
            }
        }
    }
}
