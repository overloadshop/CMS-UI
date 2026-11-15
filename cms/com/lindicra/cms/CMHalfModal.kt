package com.lindicra.cms

import android.os.Build
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** CMS 半模态：同一条位移弹簧负责入场、拖拽回弹和退场，正文滚到顶后可继续下拉。 */
@Composable
@Deprecated("Use CMSBottomSheet", ReplaceWith("CMSBottomSheet(onDismiss, heightFraction, maxHeight, content)"))
fun CMHalfModal(
    onDismiss: () -> Unit,
    heightFraction: Float = 0.86f,
    maxHeight: Dp = 640.dp,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    val latestDismiss by rememberUpdatedState(onDismiss)
    Dialog(
        onDismissRequest = {}, // 返回键和遮罩均交给下方的弹簧退场。
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        val dialogView = LocalView.current
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        DisposableEffect(dialogView, landscape, light) {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                if (Build.VERSION.SDK_INT >= 29) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (landscape) hide(WindowInsetsCompat.Type.statusBars())
                    else show(WindowInsetsCompat.Type.statusBars())
                }
            }
            onDispose {}
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val topInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
            val sheetHeight = minOf(
                maxHeight,
                (this.maxHeight - topInset - 8.dp) * heightFraction.coerceIn(0.4f, 0.92f)
            ).coerceAtLeast(1.dp)
            val heightPx = with(density) { sheetHeight.toPx() }
            val scope = rememberCoroutineScope()
            val motion = remember { CMHalfModalMotion(scope, heightPx) { latestDismiss() } }
            LaunchedEffect(heightPx) {
                motion.height = heightPx
                motion.open()
            }
            androidx.activity.compose.BackHandler { motion.close() }
            val nestedScroll = remember(motion) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (source == NestedScrollSource.UserInput &&
                            available.y < 0f && motion.offset > 0f
                        ) return Offset(0f, motion.drag(available.y))
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset, available: Offset, source: NestedScrollSource
                    ): Offset {
                        if (source == NestedScrollSource.UserInput && available.y > 0f) {
                            return Offset(0f, motion.drag(available.y))
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (motion.offset > 0f) {
                            motion.settle(available.y)
                            return available
                        }
                        return Velocity.Zero
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        // PreFling 已接管的释放不能再用零速度重启弹簧。
                        if (motion.offset > 0f && available.y != 0f) {
                            motion.settle(available.y)
                            return available
                        }
                        return Velocity.Zero
                    }
                }
            }
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        alpha = (1f - motion.offset / heightPx).coerceIn(0f, 1f)
                    }
                    .background(Color.Black.copy(alpha = 0.32f))
                    .pointerInput(motion) { detectTapGestures { motion.close() } }
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(minOf(maxWidth, 560.dp))
                    .height(sheetHeight)
                    .graphicsLayer {
                        translationY = motion.offset
                        clip = false
                        val blur = with(density) { 6.dp.toPx() } *
                            (motion.offset / heightPx).coerceIn(0f, 1f)
                        renderEffect = if (Build.VERSION.SDK_INT >= 31 && blur > 0.1f) {
                            android.graphics.RenderEffect.createBlurEffect(
                                blur, blur, android.graphics.Shader.TileMode.DECAL
                            ).asComposeRenderEffect()
                        } else null
                    }
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
                    .nestedScroll(nestedScroll)
                    .pointerInput(Unit) { detectTapGestures {} }
                    .navigationBarsPadding()
            ) {
                Box(
                    Modifier.fillMaxWidth().height(32.dp)
                        .draggable(
                            state = rememberDraggableState { motion.drag(it) },
                            orientation = Orientation.Vertical,
                            onDragStarted = { motion.interrupt() },
                            onDragStopped = { motion.settle(it) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.width(48.dp).height(5.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    content { motion.close() }
                }
            }
        }
    }
}

@Stable
private class CMHalfModalMotion(
    private val scope: CoroutineScope,
    var height: Float,
    private val onDismiss: () -> Unit
) {
    var offset by mutableFloatStateOf(height + 32f)
        private set
    private var velocity = 0f
    private var job: Job? = null
    private var closing = false

    fun interrupt() {
        job?.cancel()
        closing = false
    }

    fun drag(delta: Float): Float {
        interrupt()
        val before = offset
        offset = (offset + delta).coerceIn(0f, height + 32f)
        velocity = 0f
        return offset - before
    }

    fun open() = move(0f, velocity, dismiss = false)

    fun close() {
        if (!closing) move(height + 32f, velocity, dismiss = true)
    }

    fun settle(releaseVelocity: Float) {
        val dismiss = cmHalfModalShouldDismiss(offset, height, releaseVelocity)
        move(if (dismiss) height + 32f else 0f, releaseVelocity, dismiss)
    }

    private fun move(target: Float, initialVelocity: Float, dismiss: Boolean) {
        job?.cancel()
        closing = dismiss
        job = scope.launch {
            animate(
                initialValue = offset,
                targetValue = target,
                initialVelocity = initialVelocity,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 230f, visibilityThreshold = 0.2f)
            ) { value, speed ->
                offset = value
                velocity = speed
            }
            velocity = 0f
            if (dismiss) onDismiss()
        }
    }
}
