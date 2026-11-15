package ldyc.qy.music

import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import ldyc.qy.music.ui.theme.rememberCMSHaptic
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs

internal const val CM_MENU_HOLD_MILLIS = 180L
internal const val CM_MENU_DRAG_SLOP_DP = 3f

internal enum class CMMenuGesturePhase { Pressed, Scrolling, Selecting }

/** One contact can pan and choose without releasing the scroll owner's session. */
internal class CMMenuGesture(private val density: Float, private val scrollable: Boolean) {
    var phase = CMMenuGesturePhase.Pressed
        private set
    var selectionAllowed = true
        private set
    private var origin = Offset.Zero
    private var previous = Offset.Zero
    private var quietPoint = Offset.Zero
    private var sampleTime = 0L
    private var quietTime = 0L
    private val times = LongArray(32)
    private val deltas = Array(32) { Offset.Zero }
    private var head = 0
    private var count = 0
    private var recent = Offset.Zero
    private var selectionPanReady = true

    private fun resetTravel() {
        head = 0
        count = 0
        recent = Offset.Zero
    }

    private fun recordTravel(delta: Offset, time: Long) {
        // Forget old selection travel; a later acceleration or turn must
        // establish its own pan intent rather than reusing the whole path.
        while (count > 0 && (time - times[head] > 72L || count == times.size)) {
            recent -= deltas[head]
            head = (head + 1) % times.size
            count--
        }
        val index = (head + count) % times.size
        times[index] = sampleTime
        deltas[index] = delta
        count++
        recent += delta
    }

    fun begin(point: Offset, time: Long, interrupted: Boolean, selecting: Boolean = false,
              confirmedSelection: Boolean = false) {
        phase = if (selecting) CMMenuGesturePhase.Selecting else CMMenuGesturePhase.Pressed
        selectionAllowed = selecting || !interrupted
        selectionPanReady = !confirmedSelection
        origin = point
        previous = point
        quietPoint = point
        sampleTime = time
        quietTime = time
        resetTravel()
    }

    fun move(point: Offset, time: Long, selectionEdge: Boolean = false) {
        val delta = point - previous
        if (phase == CMMenuGesturePhase.Selecting && selectionEdge) selectionPanReady = false
        recordTravel(delta, time)
        val slop = CM_MENU_DRAG_SLOP_DP * density
        val holdSlop = if (phase == CMMenuGesturePhase.Scrolling) 1.5f * density else slop
        if ((point - quietPoint).getDistance() >= holdSlop) {
            quietPoint = point
            quietTime = time
        }
        val travel = point - origin
        val intent = if (phase == CMMenuGesturePhase.Selecting) recent else travel
        val vertical = abs(intent.y) > abs(intent.x) * 1.4f
        val recentSpeed = abs(recent.y) * 1000f / (time - times[head]).coerceAtLeast(1L)
        val pan = when (phase) {
            CMMenuGesturePhase.Pressed -> abs(travel.y) >= slop
            CMMenuGesturePhase.Selecting ->
                selectionPanReady && abs(recent.y) >= slop * 2f && recentSpeed >= 160f * density
            CMMenuGesturePhase.Scrolling -> false
        }
        if (scrollable && vertical && pan) {
            phase = CMMenuGesturePhase.Scrolling
            selectionAllowed = false
        }
        previous = point
        sampleTime = time
    }

    fun advance(time: Long, scrollHoldBlocked: Boolean = false, selectionEdge: Boolean = false) {
        if (phase == CMMenuGesturePhase.Selecting && selectionEdge) {
            // Edge scrolling is part of choosing. Reversing back into the
            // menu must not turn that movement into a new pan.
            selectionPanReady = false
            quietTime = time
            resetTravel()
            sampleTime = time
            return
        }
        if (phase == CMMenuGesturePhase.Scrolling && scrollHoldBlocked) {
            // An edge pull and its reverse belong to the same pan. Start
            // counting a new hold only after the elastic offset is restored.
            quietPoint = previous
            quietTime = time
            return
        }
        // A long-press has already confirmed choosing. Its first movement
        // reaches an option; only a fresh pan after settling may scroll.
        if (phase == CMMenuGesturePhase.Selecting && !selectionPanReady &&
            time - quietTime >= CM_MENU_HOLD_MILLIS) {
            selectionPanReady = true
            resetTravel()
            sampleTime = time
        }
        if (phase != CMMenuGesturePhase.Selecting && time - quietTime >= CM_MENU_HOLD_MILLIS) {
            phase = CMMenuGesturePhase.Selecting
            selectionAllowed = true
            selectionPanReady = true
            origin = previous
            resetTravel()
            sampleTime = time
        }
    }
}

internal fun cmMenuEdgeDirection(point: Offset, width: Float, height: Float, edge: Float, outside: Float): Float {
    if (point.x !in -edge..(width + edge) || point.y !in -outside..(height + outside)) return 0f
    return when {
        point.y < edge -> -(1f - point.y / edge).coerceAtMost(2f)
        point.y > height - edge -> (1f - (height - point.y) / edge).coerceAtMost(2f)
        else -> 0f
    }
}

internal fun cmMenuAutoScrollDelta(requested: Float, position: Int, maximum: Int): Float =
    requested.coerceIn(-position.toFloat(), (maximum - position).coerceAtLeast(0).toFloat())

/** Window coordinates keep one gesture valid across separate Compose/native hosts. */
class CMMenuTouchState {
    private data class Panel(
        val depth: Int, val bounds: Rect, val begin: (Offset) -> Unit,
        val move: (Offset) -> Unit, val end: (Boolean) -> Unit
    )
    private data class Target(
        val path: List<Int>, val bounds: Rect, val disabled: Boolean,
        val enter: (() -> Unit)?, val select: () -> Unit
    )
    private val targets = mutableMapOf<Any, Target>()
    private val panels = mutableMapOf<Any, Panel>()
    private var contactPanel: Any? = null
    private var inherited = false
    private var initialPoint = Offset.Zero
    private var awaitingInitialHover = false
    private var initialHoverPath: List<Int>? = null
    var active by mutableStateOf(false)
        private set
    var position by mutableStateOf(Offset.Unspecified)
        private set
    var hoveredPath by mutableStateOf<List<Int>?>(null)
        private set
    var selecting by mutableStateOf(true)
        private set
    var gestureId by mutableLongStateOf(0L)
        private set
    internal var onHover: () -> Unit = {}
    internal var onFinish: () -> Unit = {}

    fun begin(point: Offset, allowSelection: Boolean = true, inheritedContact: Boolean = false) {
        if (active) finish(cancelled = true, dismiss = false)
        active = true
        inherited = inheritedContact
        initialPoint = point
        awaitingInitialHover = inheritedContact
        initialHoverPath = null
        contactPanel = null
        gestureId++
        selecting = allowSelection
        position = point
        hoveredPath = null
        routeContact()
        resolve()
    }

    fun move(point: Offset) {
        if (!active) return
        if ((point - initialPoint).getDistance() >= CM_MENU_DRAG_SLOP_DP) {
            awaitingInitialHover = false
        }
        position = point
        routeContact()
        resolve()
    }

    internal fun registerPanel(key: Any, depth: Int, bounds: Rect,
                               begin: (Offset) -> Unit, move: (Offset) -> Unit,
                               end: (Boolean) -> Unit, captureInherited: Boolean = false) {
        panels[key] = Panel(depth, bounds, begin, move, end)
        if (active && inherited && contactPanel == null) {
            if (captureInherited) {
                contactPanel = key
                begin(position)
            } else routeContact()
        }
    }

    internal fun primeInitialHover(path: List<Int>) {
        if (!active || !inherited || !awaitingInitialHover) return
        initialHoverPath = path
        resolve(feedback = false)
    }

    internal fun feedbackPoint(): Offset =
        if (awaitingInitialHover) {
            targets.values.firstOrNull { !it.disabled && it.path == initialHoverPath }?.bounds?.center
                ?: position
        } else position

    internal fun removePanel(key: Any) {
        if (contactPanel == key) {
            panels[key]?.end?.invoke(true)
            contactPanel = null
        }
        panels.remove(key)
    }

    private fun routeContact() {
        if (!inherited) return
        val candidate = panels.entries.filter { it.value.bounds.contains(position) }
            .maxByOrNull { it.value.depth }?.key ?: contactPanel
        if (candidate != contactPanel) {
            contactPanel?.let { panels[it]?.end?.invoke(true) }
            contactPanel = candidate
            candidate?.let { panels[it]?.begin?.invoke(position) }
        } else candidate?.let { panels[it]?.move?.invoke(position) }
    }

    fun updateSelection(enabled: Boolean) {
        if (selecting == enabled) return
        selecting = enabled
        resolve(feedback = false)
    }

    internal fun register(
        key: Any, path: List<Int>, bounds: Rect, disabled: Boolean,
        enter: (() -> Unit)?, select: () -> Unit
    ) {
        targets[key] = Target(path, bounds, disabled, enter, select)
        if (active) resolve()
    }

    internal fun remove(key: Any) {
        targets.remove(key)
        if (active) resolve()
    }

    private fun hit(): Target? = targets.values
        .filter { !it.disabled && it.bounds.contains(position) }
        .maxByOrNull { it.path.size }

    private fun resolve(feedback: Boolean = true) {
        val target = if (selecting) {
            if (awaitingInitialHover && initialHoverPath != null) {
                targets.values.firstOrNull { !it.disabled && it.path == initialHoverPath }
            } else hit()
        } else null
        if (hoveredPath == target?.path) return
        hoveredPath = target?.path
        if (target != null) {
            if (feedback && !awaitingInitialHover) onHover()
            if (!awaitingInitialHover) target.enter?.invoke()
        }
    }

    fun finish(cancelled: Boolean = false, dismiss: Boolean = true) {
        if (!active) return
        contactPanel?.let { panels[it]?.end?.invoke(cancelled) }
        contactPanel = null
        inherited = false
        awaitingInitialHover = false
        initialHoverPath = null
        val selection = if (!cancelled && selecting) hit()?.takeIf { it.enter == null }?.select else null
        active = false
        hoveredPath = null
        position = Offset.Unspecified
        selection?.invoke()
        if (dismiss && selection != null) onFinish()
    }
}

private val windowTouches = WeakHashMap<View, WeakReference<CMMenuTouchState>>()

@Composable
fun rememberCMMenuTouchState(): CMMenuTouchState {
    val view = LocalView.current
    val haptic = rememberCMSHaptic()
    val root = view.rootView
    val state = remember(root) {
        synchronized(windowTouches) {
            windowTouches[root]?.get() ?: CMMenuTouchState().also {
                windowTouches[root] = WeakReference(it)
            }
        }
    }
    SideEffect {
        state.onHover = { haptic(ldyc.qy.music.ui.theme.CMSHapticKind.Tick) }
    }
    return state
}

/** The original anchor owns the pointer until release, even after a popover appears. */
fun Modifier.cmMenuAnchor(onOpen: () -> Unit, onLongOpen: (() -> Unit)? = null): Modifier = composed {
    val touch = rememberCMMenuTouchState()
    val density = LocalDensity.current
    val latestOpen by rememberUpdatedState(onOpen)
    val latestLongOpen by rememberUpdatedState(onLongOpen ?: onOpen)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    onGloballyPositioned { coordinates = it }
        .semantics { onClick { latestOpen(); true } }
        .pointerInput(touch) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val slop = CM_MENU_DRAG_SLOP_DP * density.density
                val result = withTimeoutOrNull(CM_MENU_HOLD_MILLIS) {
                    var released: androidx.compose.ui.input.pointer.PointerInputChange? = null
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed ||
                            abs(change.position.x - down.position.x) > slop ||
                            abs(change.position.y - down.position.y) > slop) break
                        if (!change.pressed) { released = change; break }
                    }
                    released to true
                }
                val up = result?.first
                if (up != null) {
                    up.consume()
                    latestOpen()
                } else if (result == null && currentEvent.changes.any { it.id == down.id && it.pressed && !it.isConsumed }) {
                    val point = currentEvent.changes.first { it.id == down.id }
                    touch.begin(coordinates?.localToWindow(point.position) ?: return@awaitEachGesture,
                        inheritedContact = true)
                    latestLongOpen()
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                ?: break
                            coordinates?.takeIf { it.isAttached }?.let {
                                touch.move(it.localToWindow(change.position))
                            }
                            change.consume()
                            if (!change.pressed) {
                                touch.finish()
                                break
                            }
                        }
                    } finally {
                        touch.finish(cancelled = true)
                    }
                }
            }
        }
}

fun cmMenuAnchorModifier(onOpen: () -> Unit): Modifier = Modifier.cmMenuAnchor(onOpen)

internal fun cmMenuAnchorModifier(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    Modifier.cmMenuAnchor(onClick, onLongClick)
