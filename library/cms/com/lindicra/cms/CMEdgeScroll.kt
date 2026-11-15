package com.lindicra.cms

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.lindicra.cms.ui.theme.CMSHapticKind
import com.lindicra.cms.ui.theme.rememberCMSHaptic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Local wide content owns the contact; vertical remainder belongs to the enclosing page. */
@Composable
internal fun CMEdgeScrollBox(
    modifier: Modifier = Modifier,
    horizontal: Boolean = true,
    vertical: Boolean = false,
    elastic: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val hState = rememberScrollState()
    val vState = rememberScrollState()
    val haptic by rememberUpdatedState(rememberCMSHaptic())
    val scope = rememberCoroutineScope()
    val fling = rememberCMScrollFlingBehavior()
    val size = remember { mutableStateOf(IntSize.Zero) }
    val parent = LocalCMVerticalDragBridge.current
    val registry = LocalCMSTouchRegions.current ?: remember { CMSTouchRegionRegistry() }
    val accepts by rememberUpdatedState(
        horizontal && hState.maxValue > 0 || vertical && vState.maxValue > 0
    )
    val region = remember { CMSTouchRegion { accepts } }
    DisposableEffect(registry, region) {
        registry?.register(region)
        onDispose { registry?.remove(region) }
    }
    val x = remember(hState, elastic, fling) {
        CMContentScrollAxis(hState, { size.value.width.toFloat() }, elastic, scope, fling) {
            haptic(CMSHapticKind.Tick)
        }
    }
    val y = remember(vState, elastic, parent, fling) {
        CMContentScrollAxis(vState, { size.value.height.toFloat() },
            elastic && parent == null, scope, fling) { haptic(CMSHapticKind.Tick) }
    }
    val localVertical = remember { CMVerticalDragBridge() }
    DisposableEffect(localVertical, vState, y, parent) {
        var job: Job? = null
        var interrupt: Job? = null
        var deltas: Channel<Float>? = null
        var releaseVelocity = 0f
        var contact: CMContentParentContact? = null
        fun cancel() {
            localVertical.gestureGeneration++
            job?.cancel()
            deltas?.cancel()
            job = null
            deltas = null
            contact?.cancel()
            contact = null
            localVertical.active = false
        }
        localVertical.interrupt = {
            val moving = vState.isScrollInProgress || y.moving || job?.isActive == true
            cancel()
            val parentMoving = parent?.interrupt?.invoke() == true
            y.interrupt()
            interrupt?.cancel()
            interrupt = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                vState.stopScroll(MutatePriority.UserInput)
            }
            moving || parentMoving
        }
        localVertical.begin = {
            cancel()
            val generation = localVertical.gestureGeneration
            val channel = Channel<Float>(Channel.UNLIMITED)
            val interrupted = interrupt
            val parentContact = parent?.let { CMContentParentContact(it) }
            contact = parentContact
            deltas = channel
            releaseVelocity = 0f
            localVertical.active = true
            fun owns() = localVertical.gestureGeneration == generation && deltas === channel
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    interrupted?.join()
                    currentCoroutineContext().ensureActive()
                    if (!owns()) return@launch
                    vState.scroll(MutatePriority.UserInput) {
                        for (delta in channel) {
                            if (!owns()) break
                            if (parentContact?.active == true) {
                                parentContact.drag(delta)
                            } else {
                                val remaining = y.move(this, delta, -1f)
                                if (abs(remaining) > 0.5f) parentContact?.drag(remaining)
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    if (!owns()) return@launch
                    if (parentContact?.active == true) {
                        parentContact.end(releaseVelocity)
                    } else {
                        vState.scroll(MutatePriority.Default) {
                            val remaining = y.finish(this, releaseVelocity, -1f)
                            currentCoroutineContext().ensureActive()
                            if (owns()) parentContact?.end(remaining)
                        }
                    }
                } finally {
                    parentContact?.cancel()
                    if (owns()) {
                        localVertical.active = false
                        deltas = null
                        job = null
                        contact = null
                        y.cancel()
                    }
                    channel.cancel()
                }
            }
        }
        localVertical.drag = { deltas?.trySend(it); Unit }
        localVertical.end = { releaseVelocity = it; deltas?.close(); Unit }
        localVertical.cancel = { cancel(); y.cancel() }
        localVertical.selectionHoldBlocked = { y.moving }
        onDispose {
            cancel()
            interrupt?.cancel()
            y.reset()
            localVertical.begin = {}
            localVertical.drag = {}
            localVertical.end = {}
            localVertical.cancel = {}
            localVertical.interrupt = { false }
            localVertical.selectionHoldBlocked = { false }
        }
    }
    DisposableEffect(x) {
        onDispose { x.reset() }
    }
    LaunchedEffect(horizontal, vertical, elastic, hState.maxValue, vState.maxValue) {
        if (!horizontal || !elastic || hState.maxValue == 0) x.reset()
        if (!vertical || !elastic || vState.maxValue == 0) y.reset()
    }
    Box(
        modifier.clipToBounds().onSizeChanged { size.value = it }
            .cmsEdgeScrollTouchPriority(
                if (horizontal) hState else null,
                if (vertical) localVertical else parent,
                region, registry, if (horizontal) x else null
            )
    ) {
        CompositionLocalProvider(
            LocalCMSTouchRegions provides registry,
            LocalCMVerticalDragBridge provides if (vertical) localVertical else parent
        ) {
            Box(
                Modifier
                    .then(if (horizontal) Modifier.cmsScrollViewport(hState, horizontal = true) else Modifier)
                    .then(if (vertical) Modifier.cmsScrollViewport(vState, horizontal = false) else Modifier)
            ) {
                Box(Modifier.graphicsLayer {
                    translationX = x.offset
                    translationY = y.offset
                }, content = content)
            }
        }
    }
}

/** A spring stays attached to its original boundary, regardless of cosmetic undershoot. */
private class CMContentScrollAxis(
    private val state: ScrollState,
    private val dimension: () -> Float,
    private val elastic: Boolean,
    private val scope: CoroutineScope,
    private val fling: FlingBehavior,
    private val tick: () -> Unit
) : CMSHorizontalScrollPhysics {
    private val edge = CMElasticMotion()
    private val anchor = CMBoundaryAnchor()
    private var direction = -1f
    val offset get() = edge.offset
    val moving get() = edge.moving
    private fun extent() = dimension().coerceAtLeast(1f)
    private fun atEdge(delta: Float) = if (delta * direction < 0f)
        !state.canScrollBackward else !state.canScrollForward
    private fun reconcile() {
        if (state.maxValue == 0 || anchor.direction != 0f && !atEdge(anchor.direction)) reset()
        else if (edge.offset == 0f && !edge.moving) anchor.clear()
    }
    fun reset() {
        edge.reset()
        anchor.clear()
    }
    override fun interrupt() {
        reconcile()
        edge.stop(extent(), anchor.direction)
    }
    override fun cancel() {
        reconcile()
        if (!edge.moving) edge.release(0f, extent(), scope)
    }
    private fun hit(delta: Float) {
        if (anchor.direction != sign(delta)) tick()
        anchor.begin(delta)
    }
    fun move(content: ScrollScope, delta: Float, scrollDirection: Float): Float {
        direction = scrollDirection
        reconcile()
        val pre = edge.consumeInward(delta, extent(), anchor.direction)
        val used = content.scrollBy((delta - pre) * direction) / direction
        val remaining = delta - pre - used
        reconcile()
        if (!elastic || state.maxValue == 0 || abs(remaining) <= 0.5f || !atEdge(remaining))
            return remaining
        edge.stop(extent(), anchor.direction)
        hit(remaining)
        edge.drag(remaining, extent())
        return 0f
    }
    override fun drag(scope: ScrollScope, delta: Float, scrollDirection: Float) {
        move(scope, delta, scrollDirection)
    }
    suspend fun finish(content: ScrollScope, velocity: Float, scrollDirection: Float): Float {
        direction = scrollDirection
        reconcile()
        if (edge.offset != 0f && edge.offset * velocity >= 0f) {
            edge.release(velocity, extent(), scope)
            return 0f
        }
        val edgeScope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                reconcile()
                val fingerDelta = pixels / direction
                val pre = edge.consumeInward(fingerDelta, extent(), anchor.direction)
                return pre * direction + content.scrollBy(pixels - pre * direction)
            }
        }
        val remaining = with(fling) {
            edgeScope.performFling(velocity * direction)
        } / direction
        reconcile()
        if (elastic && state.maxValue > 0 && abs(remaining) > 0.5f && atEdge(remaining)) {
            hit(remaining)
            edge.release(remaining, extent(), scope)
            return 0f
        }
        cancel()
        return remaining
    }
    override suspend fun fling(scope: ScrollScope, velocity: Float, scrollDirection: Float) {
        finish(scope, velocity, scrollDirection)
    }
}

/** Delayed local fling callbacks cannot end a parent's newer contact. */
private class CMContentParentContact(private val bridge: CMVerticalDragBridge) {
    private var generation = bridge.gestureGeneration
    private var started = false
    private var ended = false
    val active get() = started && !ended && generation == bridge.gestureGeneration
    private fun begin(): Boolean {
        if (ended || generation != bridge.gestureGeneration) return false
        if (!started) {
            bridge.begin()
            generation = bridge.gestureGeneration
            started = true
        }
        return true
    }
    fun drag(delta: Float) {
        if (begin()) bridge.drag(delta)
    }
    fun end(velocity: Float) {
        if (velocity != 0f && !started) begin()
        if (started && generation == bridge.gestureGeneration) bridge.end(velocity)
        ended = true
    }
    fun cancel() {
        if (!ended && started && generation == bridge.gestureGeneration) bridge.cancel()
        ended = true
    }
}
