@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.lindicra.cms

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lindicra.cms.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun pickerWheelDistance(offset: Int, itemSize: Int, viewportStart: Int, viewportEnd: Int) =
    offset + itemSize / 2f - (viewportStart + viewportEnd) / 2f

internal fun pickerWheelAngle(distance: Float, radius: Float): Float =
    (distance / radius.coerceAtLeast(1f)).coerceIn(-1.25f, 1.25f)

internal fun picker12HourIndex(hour: Int) = (hour.coerceIn(0, 23) + 11) % 12
internal fun picker24Hour(index: Int, afternoon: Boolean) =
    (index.coerceIn(0, 11) + 1) % 12 + if (afternoon) 12 else 0

/**
 * 行的透视、触觉与吸附均使用 LazyColumn 的真实布局中心。
 * 滚动期间仅更新视觉和触觉；停稳后提交值，避免父组件反馈打断惯性。
 */
@Composable
fun CMSWheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 36.dp,
    visibleCount: Int = 5,
    loop: Boolean = false,
    frictionMultiplier: Float = 1.4f,
    dampingRatio: Float = Spring.DampingRatioNoBouncy,
    stiffness: Float = 500f,
    showSelectionBand: Boolean = true,
    wheelRadius: Dp = 110.dp,
    /** 保留调用兼容；透视直接跟随真实滚动位置，不再叠加延迟动画。 */
    scaleTransitionMillis: Int = 0,
    hapticEnabled: Boolean = true,
    edgeFadeStart: Float = 0.45f
) {
    if (items.isEmpty()) return
    val safeItemHeight = itemHeight.coerceAtLeast(1.dp)
    val rows = visibleCount.coerceIn(3, 9).let { if (it % 2 == 0) it + 1 else it }
    val selected by rememberUpdatedState(selectedIndex.coerceIn(items.indices))
    val callback by rememberUpdatedState(onSelected)
    val feedbackEnabled by rememberUpdatedState(hapticEnabled)
    val haptic = rememberCMSHaptic()
    val scope = rememberCoroutineScope()
    val virtualCount = if (loop && items.size > 1) 1_000_000 else items.size
    val initial = remember(loop, items.size) {
        if (virtualCount == items.size) selected
        else (virtualCount / 2).let { it - it % items.size + selected }
    }
    val state = remember(loop, items.size) { LazyListState(initial) }
    var externalUpdate by remember(state) { mutableStateOf(false) }
    val heightPx = with(LocalDensity.current) { safeItemHeight.toPx() }
    // 允许半径小于行高；只保留一个足够避免数值爆炸的下限。
    // 因此半径越小，distance / radius 越大，3D 弧度确实越强。
    val radiusPx = with(LocalDensity.current) { wheelRadius.toPx() }
        .coerceAtLeast(heightPx * 0.5f)
    val decay = remember(frictionMultiplier) {
        exponentialDecay<Float>(frictionMultiplier = frictionMultiplier.coerceAtLeast(0.1f))
    }
    val snap = remember(dampingRatio, stiffness) {
        spring<Float>(
            dampingRatio = dampingRatio.coerceIn(0.8f, 1f),
            stiffness = stiffness.coerceAtLeast(10f),
            visibilityThreshold = 0.5f
        )
    }
    val fling = remember(state, decay, snap) {
        snapFlingBehavior(SnapLayoutInfoProvider(state, SnapPosition.Center), decay, snap)
    }
    fun nearest(): Int {
        val layout = state.layoutInfo
        return layout.visibleItemsInfo.minByOrNull {
            abs(pickerWheelDistance(it.offset, it.size, layout.viewportStartOffset, layout.viewportEndOffset))
        }?.index ?: state.firstVisibleItemIndex
    }
    fun real(index: Int) = index % items.size
    fun targetIndex(target: Int): Int {
        if (virtualCount == items.size) return target
        val current = nearest()
        val base = current - real(current)
        return listOf(base + target - items.size, base + target, base + target + items.size)
            .filter { it in 0 until virtualCount }
            .minByOrNull { abs(it - current) }
            ?: current.coerceIn(0, virtualCount - 1)
    }
    LaunchedEffect(state, selectedIndex, safeItemHeight) {
        val target = selectedIndex.coerceIn(items.indices)
        if (real(nearest()) != target || !state.isScrollInProgress &&
            state.firstVisibleItemScrollOffset != 0) {
            externalUpdate = true
            try { state.scrollToItem(targetIndex(target)) }
            finally { externalUpdate = false }
        }
    }
    LaunchedEffect(state, items) {
        var previous = nearest()
        snapshotFlow { Triple(nearest(), state.isScrollInProgress, externalUpdate) }
            .collect { (index, scrolling, external) ->
                if (index != previous && scrolling && !external && feedbackEnabled) {
                    haptic(CMSHapticKind.Tick)
                }
                previous = index
                if (!scrolling && !external && state.layoutInfo.visibleItemsInfo.isNotEmpty() &&
                    real(index) != selected) callback(real(index))
            }
    }
    Box(modifier.fillMaxWidth().height(safeItemHeight * rows), contentAlignment = Alignment.Center) {
        if (showSelectionBand) {
            Box(Modifier.fillMaxWidth().height(safeItemHeight).clip(RoundedCornerShape(8.dp))
                .background(TertiaryFill))
        }
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = safeItemHeight * ((rows - 1) / 2)),
            modifier = Modifier.fillMaxSize()
                .semantics {
                    contentDescription = "滚筒选择器"
                    stateDescription = items[selected]
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        selected.toFloat(), 0f..items.lastIndex.coerceAtLeast(1).toFloat(),
                        items.lastIndex.coerceAtLeast(1))
                    setProgress { value ->
                        scope.launch {
                            state.animateScrollToItem(targetIndex(value.roundToInt().coerceIn(items.indices)))
                        }
                        true
                    }
                }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val fade = edgeFadeStart.coerceIn(0.05f, 0.49f)
                    val mask = Brush.verticalGradient(
                        0f to Color.Transparent, fade to Color.Black,
                        (1f - fade) to Color.Black, 1f to Color.Transparent)
                    onDrawWithContent {
                        drawContent()
                        drawRect(mask, blendMode = BlendMode.DstIn)
                    }
                }
        ) {
            items(virtualCount, key = { it }) { raw ->
                val active = raw == nearest()
                Box(
                    Modifier.fillMaxWidth().height(safeItemHeight)
                        .graphicsLayer {
                            val layout = state.layoutInfo
                            val info = layout.visibleItemsInfo.firstOrNull { it.index == raw }
                            val distance = info?.let {
                                pickerWheelDistance(it.offset, it.size,
                                    layout.viewportStartOffset, layout.viewportEndOffset)
                            } ?: 0f
                            val angle = pickerWheelAngle(distance, radiusPx)
                            rotationX = -angle * 180f / kotlin.math.PI.toFloat()
                            cameraDistance = radiusPx * 8f
                            // 只做一次圆柱投影：R 越小，sin(angle) 与原始直线
                            // 的差值越大，弧度越强；不再叠加额外缩放位移。
                            translationY = radiusPx * sin(angle) - distance
                            alpha = cos(angle).let { it * it }.coerceIn(0.12f, 1f)
                        }
                        .clickable {
                            if (raw == nearest()) {
                                if (feedbackEnabled) haptic(CMSHapticKind.Tick)
                            } else scope.launch { state.animateScrollToItem(raw) }
                        }
                        .semantics {
                            this.selected = active
                            onClick(label = "选择${items[real(raw)]}") {
                                scope.launch { state.animateScrollToItem(raw) }
                                true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(items[real(raw)], color = if (active) PrimaryText else SecondaryText,
                        fontSize = 17.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        fontFamily = HarmonySansSC, style = CMSNoFontPadding, maxLines = 1)
                }
            }
        }
    }
}

/** 时、分与可选的上午/下午滚筒。回调小时始终为 0–23。 */
@Composable
fun CMSTimePicker(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    minuteStep: Int = 1,
    is24Hour: Boolean = true,
    itemHeight: Dp = 36.dp,
    visibleCount: Int = 5,
    loop: Boolean = false
) {
    val step = minuteStep.coerceIn(1, 60)
    val safeHour = hour.coerceIn(0, 23)
    val minuteValues = remember(step) { (0 until 60 step step).toList() }
    val minuteIndex = minuteValues.indices.minBy { abs(minuteValues[it] - minute.coerceIn(0, 59)) }
    val safeMinute = minuteValues[minuteIndex]
    val hours = remember(is24Hour) {
        (if (is24Hour) 0..23 else 1..12).map { it.toString().padStart(2, '0') }
    }
    val hourIndex = if (is24Hour) safeHour else picker12HourIndex(safeHour)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CMSWheelPicker(hours, hourIndex, { index ->
            val nextHour = if (is24Hour) index else picker24Hour(index, safeHour >= 12)
            onTimeChange(nextHour, safeMinute)
        }, Modifier.weight(1f), itemHeight, visibleCount, loop)
        Text(":", color = SecondaryText, fontSize = 20.sp, fontFamily = HarmonySansSC)
        CMSWheelPicker(minuteValues.map { it.toString().padStart(2, '0') }, minuteIndex,
            { index -> minuteValues.getOrNull(index)?.let { onTimeChange(safeHour, it) } },
            Modifier.weight(1f), itemHeight, visibleCount, loop)
        if (!is24Hour) {
            CMSWheelPicker(listOf("上午", "下午"), safeHour / 12,
                { index ->
                    if (index in 0..1) onTimeChange(safeHour % 12 + index * 12, safeMinute)
                },
                Modifier.weight(0.9f), itemHeight, visibleCount)
        }
    }
}

@Composable
internal fun CMSPickerInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = SecondaryText, fontSize = 11.sp, fontFamily = HarmonySansSC,
            style = CMSNoFontPadding)
        CMSTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            isError = error,
            size = CMSTextFieldSize.Medium,
            variant = if (keyboardType == KeyboardType.Decimal) {
                CMSTextFieldVariant.Numeric
            } else {
                CMSTextFieldVariant.Plain
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}
