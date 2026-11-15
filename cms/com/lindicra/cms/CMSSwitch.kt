package com.lindicra.cms

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.lindicra.cms.ui.theme.LocalCMSAccentColor
import com.lindicra.cms.ui.theme.TertiaryFill
import com.lindicra.cms.ui.theme.TertiaryText

/**
 * CMS 滑块开关（对齐 CLEAR Design「开关 Switch」）：
 *  - 轨道 44×24dp、滑块 20dp、内缩 2dp
 *  - 关闭态轨道 #3A4150 / 滑块 #5C6575，开启态轨道跟随主题色 / 滑块 #FFFFFF
 *  - 点击或拖动滑块切换；拖动越界时轨道拉伸变细，松手用弹簧回弹
 */
@Composable
fun CMSSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trackOnColor: Color = LocalCMSAccentColor.current,
    trackOffColor: Color = TertiaryFill,
    thumbOnColor: Color = Color.White,
    thumbOffColor: Color = TertiaryText,
    width: Dp = 44.dp,
    height: Dp = 24.dp
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 圆点位置：0=off（左），1=on（右）
    val thumb = remember { Animatable(if (checked) 1f else 0f) }
    val settleSpec = spring<Float>(dampingRatio = 0.6f, stiffness = 480f)

    // 闭包内始终读取最新 checked，避免 pointerInput(Unit) 捕获首次旧值
    val currentChecked by rememberUpdatedState(checked)

    // 帧形变越界量
    val leftState = remember { mutableFloatStateOf(0f) }
    val rightState = remember { mutableFloatStateOf(0f) }
    var bounceJobLeft by remember { mutableStateOf<Job?>(null) }
    var bounceJobRight by remember { mutableStateOf<Job?>(null) }
    val maxOverscrollPx = with(density) { 16.dp.toPx() }

    // checked 变化（外部同步 / 点击）→ 吸附动画
    LaunchedEffect(checked) {
        thumb.animateTo(if (checked) 1f else 0f, settleSpec)
    }

    Canvas(
        modifier = modifier
            .width(width)
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures { onCheckedChange(!currentChecked) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        bounceJobLeft?.cancel()
                        bounceJobRight?.cancel()
                        bounceJobLeft = null
                        bounceJobRight = null
                        leftState.floatValue = 0f
                        rightState.floatValue = 0f
                    },
                    onDragEnd = {
                        val target = thumb.value > 0.5f
                        if (target != currentChecked) onCheckedChange(target)
                        else scope.launch { thumb.animateTo(if (target) 1f else 0f, settleSpec) }
                        bounceJobLeft = cmsSwitchBounceBack(scope, leftState)
                        bounceJobRight = cmsSwitchBounceBack(scope, rightState)
                    },
                    onDragCancel = {
                        val target = thumb.value > 0.5f
                        if (target != currentChecked) onCheckedChange(target)
                        else scope.launch { thumb.animateTo(if (target) 1f else 0f, settleSpec) }
                        bounceJobLeft = cmsSwitchBounceBack(scope, leftState)
                        bounceJobRight = cmsSwitchBounceBack(scope, rightState)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val w = size.width.toFloat()
                        if (w > 0f) {
                            val x = change.position.x
                            // 圆点值 clamp 到 [0,1]，越界只体现在轨道形变上
                            scope.launch { thumb.snapTo((x / w).coerceIn(0f, 1f)) }
                            leftState.floatValue = if (x < 0f) cmsSwitchDampOverscroll(-x, maxOverscrollPx) else 0f
                            rightState.floatValue = if (x > w) cmsSwitchDampOverscroll(x - w, maxOverscrollPx) else 0f
                        }
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val overLeft = leftState.floatValue
        val overRight = rightState.floatValue
        val overMag = maxOf(abs(overLeft), abs(overRight))
        val overRatio = (overMag / maxOverscrollPx).coerceIn(0f, 1f)

        // 越界时轨道变细，滑块尺寸不变
        val effH = h * (1f - overRatio * 0.3f)
        val trackR = effH / 2f
        val trackTop = (h - effH) / 2f
        val startX = -overLeft
        val endX = w + overRight
        val trackW = endX - startX

        val trackColor = lerp(trackOffColor, trackOnColor, thumb.value)
        val thumbColor = lerp(thumbOffColor, thumbOnColor, thumb.value)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(startX, trackTop),
            size = Size(trackW, effH),
            cornerRadius = CornerRadius(trackR, trackR)
        )

        val thumbPadding = 2.dp.toPx()
        val thumbD = h - thumbPadding * 2
        val travel = (trackW - thumbD - thumbPadding * 2).coerceAtLeast(0f)
        val centerX = startX + thumbPadding + thumbD / 2f + thumb.value * travel
        drawCircle(thumbColor, radius = thumbD / 2f, center = Offset(centerX, h / 2f))
    }
}

/** 越界阻尼：越拖越沉，最终渐近到 [limit] */
private fun cmsSwitchDampOverscroll(raw: Float, limit: Float): Float =
    limit * (raw / (raw + limit))

/** 松手后把越界量弹回 0（带过冲） */
private fun cmsSwitchBounceBack(scope: CoroutineScope, state: MutableFloatState): Job =
    scope.launch {
        animate(
            initialValue = state.floatValue,
            targetValue = 0f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f)
        ) { value, _ -> state.floatValue = value }
    }
