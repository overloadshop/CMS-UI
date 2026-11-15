package com.lindicra.cms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lindicra.cms.ui.theme.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class CMSColorPickerMode { Spectrum, Wheel, Sliders, Palette }
enum class CMSColorPalette { Vivid, Pastel, Neutral }

val CMSColorPresets = listOf(
    Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899),
    Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFF14B8A6),
    Color(0xFF64748B), Color(0xFF0EA5E9), Color(0xFFA855F7), Color(0xFF84CC16),
    Color.White, Color(0xFFE5E7EB), Color(0xFF6B7280), Color.Black, Color(0xFF92400E), Color(0xFFFECACA)
)
private val HueColors = (0..6).map { Color.hsv((it * 60f).coerceAtMost(359.999f), 1f, 1f) }

internal fun pickerColorFromHex(text: String, allowAlpha: Boolean): Color? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 && (!allowAlpha || hex.length != 8)) return null
    if (hex.any { it.digitToIntOrNull(16) == null }) return null
    val value = hex.toLongOrNull(16) ?: return null
    return Color((if (hex.length == 6) value or 0xFF000000L else value).toInt())
}

private fun hsvOf(color: Color): FloatArray = FloatArray(3).also {
    android.graphics.Color.colorToHSV(color.toArgb(), it)
}

/** 色谱、色轮、RGB 滑杆与多套色板；支持 HEX、RGBA 数值和最近使用。 */
@Composable
fun CMSColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showPresets: Boolean = true,
    showAlpha: Boolean = true,
    presets: List<Color> = CMSColorPresets,
    recentLimit: Int = 6,
    panelHeight: Dp = 150.dp,
    mode: CMSColorPickerMode = CMSColorPickerMode.Spectrum,
    showModeSwitch: Boolean = true,
    showInputs: Boolean = true,
    palette: CMSColorPalette = CMSColorPalette.Vivid
) {
    val initial = remember { hsvOf(color) }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var brightness by remember { mutableFloatStateOf(initial[2]) }
    var alpha by remember { mutableFloatStateOf(if (showAlpha) color.alpha else 1f) }
    var currentMode by remember(mode) { mutableStateOf(mode) }
    var currentPalette by remember(palette) { mutableStateOf(palette) }
    var interacting by remember { mutableStateOf(false) }
    var lastEmitted by remember { mutableIntStateOf(color.toArgb()) }
    val callback by rememberUpdatedState(onColorChange)
    val recent = remember { mutableStateListOf<Color>() }
    val haptic = rememberCMSHaptic()
    val current = Color.hsv(hue.coerceIn(0f, 359.999f), saturation, brightness,
        if (showAlpha) alpha else 1f)
    fun sync(next: Color) {
        val hsv = hsvOf(next)
        // 黑、白、灰没有可辨色相；保留当前色相，重新提高饱和度时不会跳回红色。
        if (hsv[1] > 0.0001f && hsv[2] > 0.0001f) hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        alpha = if (showAlpha) next.alpha else 1f
    }
    LaunchedEffect(color, interacting, showAlpha) {
        if (!interacting && color.toArgb() != lastEmitted) {
            sync(color)
            lastEmitted = color.toArgb()
        }
        if (!showAlpha) alpha = 1f
    }
    fun emit() {
        val next = Color.hsv(hue.coerceIn(0f, 359.999f), saturation.coerceIn(0f, 1f),
            brightness.coerceIn(0f, 1f), if (showAlpha) alpha.coerceIn(0f, 1f) else 1f)
        lastEmitted = next.toArgb()
        callback(next)
    }
    fun commit() {
        if (recentLimit <= 0) return
        val entry = Color.hsv(hue.coerceIn(0f, 359.999f), saturation, brightness,
            if (showAlpha) alpha else 1f)
        recent.removeAll { it.toArgb() == entry.toArgb() }
        recent.add(0, entry)
        while (recent.size > recentLimit) recent.removeAt(recent.lastIndex)
    }
    LaunchedEffect(recentLimit) {
        while (recent.size > recentLimit.coerceAtLeast(0)) recent.removeAt(recent.lastIndex)
    }
    fun pick(next: Color) {
        sync(next)
        emit()
        commit()
        haptic(CMSHapticKind.Tick)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showModeSwitch) {
            CMSSegmentedControl(listOf("色谱", "色轮", "滑杆", "色板"), currentMode.ordinal,
                { CMSColorPickerMode.entries.getOrNull(it)?.let { mode -> currentMode = mode } })
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                .border(1.dp, DividerColor, RoundedCornerShape(10.dp))) {
                Canvas(Modifier.fillMaxSize()) { drawCheckerboard(6.dp.toPx()) }
                Box(Modifier.fillMaxSize().background(current))
            }
            Column {
                Text(if (showAlpha) "#%08X".format(current.toArgb())
                    else "#%06X".format(current.toArgb() and 0xFFFFFF),
                    color = PrimaryText, fontSize = 15.sp, fontFamily = HarmonySansSC, style = CMSNoFontPadding)
                Text("H ${hue.roundToInt()}° · S ${(saturation * 100).roundToInt()}% · V ${(brightness * 100).roundToInt()}%",
                    color = SecondaryText, fontSize = 11.sp, fontFamily = HarmonySansSC, style = CMSNoFontPadding)
            }
        }
        when (currentMode) {
            CMSColorPickerMode.Spectrum -> {
                val drag = colorDragModifier({ interacting = it }, { position, size ->
                    saturation = (position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    brightness = (1f - position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    emit()
                }, { commit() })
                Canvas(Modifier.fillMaxWidth().height(panelHeight.coerceAtLeast(80.dp))
                    .clip(RoundedCornerShape(12.dp)).then(drag)
                    .semantics { contentDescription = "饱和度与亮度色谱，可通过下方数值输入精确选择" }) {
                    drawRect(Color.hsv(hue, 1f, 1f))
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    drawColorMarker(Offset(saturation * size.width, (1f - brightness) * size.height),
                        Color.hsv(hue, saturation, brightness))
                }
                ColorHueBar(hue, { interacting = it }, { hue = it; emit() }, { commit() })
            }
            CMSColorPickerMode.Wheel -> {
                val insetPx = with(LocalDensity.current) { 10.dp.toPx() }
                val drag = colorDragModifier({ interacting = it }, { position, size ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = (min(size.width, size.height) / 2f - insetPx).coerceAtLeast(1f)
                    val delta = position - center
                    hue = ((atan2(delta.y, delta.x) * 180f / kotlin.math.PI.toFloat()) + 360f) % 360f
                    saturation = (hypot(delta.x, delta.y) / radius.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    emit()
                }, { commit() }, { position, size ->
                    hypot(position.x - size.width / 2f, position.y - size.height / 2f) <=
                        min(size.width, size.height) / 2f - insetPx
                })
                Canvas(Modifier.fillMaxWidth().height(panelHeight.coerceAtLeast(180.dp)).then(drag)
                    .semantics { contentDescription = "色轮，角度控制色相，半径控制饱和度" }) {
                    val radius = (min(size.width, size.height) / 2f - 10.dp.toPx()).coerceAtLeast(1f)
                    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(
                        center.x - radius, center.y - radius, center.x + radius, center.y + radius)) }
                    clipPath(circle) {
                        drawCircle(Brush.sweepGradient(HueColors, center), radius, center)
                        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius),
                            radius, center)
                        drawCircle(Color.Black.copy(alpha = 1f - brightness), radius, center)
                    }
                    val angle = hue * kotlin.math.PI.toFloat() / 180f
                    drawColorMarker(center + Offset(cos(angle), sin(angle)) * (radius * saturation),
                        Color.hsv(hue, saturation, brightness))
                }
                ColorValueSlider("亮度", brightness, { brightness = it; emit() },
                    { interacting = it }, { commit() }, Color.hsv(hue, saturation, 1f))
            }
            CMSColorPickerMode.Sliders -> {
                listOf("红 R" to Color(0xFFEF4444), "绿 G" to Color(0xFF22C55E),
                    "蓝 B" to Color(0xFF3B82F6)).forEachIndexed { index, (label, tint) ->
                    val channels = listOf(current.red, current.green, current.blue)
                    ColorValueSlider(label, channels[index], { next ->
                        val updated = channels.toMutableList().also { it[index] = next }
                        sync(Color(updated[0], updated[1], updated[2], alpha))
                        emit()
                    }, { interacting = it }, { commit() }, tint, 255)
                }
            }
            CMSColorPickerMode.Palette -> {
                CMSSegmentedControl(listOf("鲜明", "柔和", "灰阶"), currentPalette.ordinal,
                    { CMSColorPalette.entries.getOrNull(it)?.let { palette -> currentPalette = palette } },
                    outlined = true)
                val colors = remember(currentPalette) {
                    when (currentPalette) {
                        CMSColorPalette.Vivid -> listOf(1f, 0.75f, 0.45f).flatMap { value ->
                            (0 until 12).map { Color.hsv(it * 30f, 0.85f, value) }
                        }
                        CMSColorPalette.Pastel -> listOf(0.2f, 0.4f, 0.6f).flatMap { saturation ->
                            (0 until 12).map { Color.hsv(it * 30f, saturation, 1f) }
                        }
                        CMSColorPalette.Neutral -> (0..17).map {
                            val value = it / 17f
                            Color(value, value, value)
                        }
                    }
                }
                ColorSwatches(colors, current, { pick(it.copy(alpha = if (showAlpha) alpha else 1f)) })
            }
        }
        if (showAlpha) {
            ColorValueSlider("透明度", alpha, { alpha = it; emit() },
                { interacting = it }, { commit() }, current.copy(alpha = 1f))
        }
        if (showInputs) {
            var hex by remember(current.toArgb(), showAlpha) {
                mutableStateOf(if (showAlpha) "%08X".format(current.toArgb())
                    else "%06X".format(current.toArgb() and 0xFFFFFF))
            }
            val parsed = pickerColorFromHex(hex, showAlpha)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom) {
                CMSPickerInput(if (showAlpha) "HEX · RRGGBB 或 AARRGGBB" else "HEX · RRGGBB",
                    hex, { hex = it }, Modifier.weight(1f), error = parsed == null)
                Box(Modifier.height(44.dp).clip(RoundedCornerShape(9.dp)).background(TertiaryFill)
                    .clickable(enabled = parsed != null) { parsed?.let { pick(it) } }
                    .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("应用", color = if (parsed != null) LocalCMSAccentColor.current else TertiaryText,
                        fontSize = 13.sp, fontFamily = HarmonySansSC)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val values = listOf(current.red, current.green, current.blue, alpha)
                val count = if (showAlpha) 4 else 3
                repeat(count) { index ->
                    var text by remember((values[index] * 255).roundToInt()) {
                        mutableStateOf((values[index] * 255).roundToInt().toString())
                    }
                    val number = text.toIntOrNull()
                    CMSPickerInput(listOf("R", "G", "B", "A")[index], text, { next ->
                        text = next
                        next.toIntOrNull()?.takeIf { it in 0..255 }?.let { value ->
                            val channels = values.toMutableList().also { it[index] = value / 255f }
                            sync(Color(channels[0], channels[1], channels[2], channels[3]))
                            emit()
                        }
                    }, Modifier.weight(1f), error = number == null || number !in 0..255,
                        keyboardType = KeyboardType.Number)
                }
            }
        }
        if (showPresets && presets.isNotEmpty()) {
            ColorLabel("预设色板")
            ColorSwatches(presets, current, { pick(it) })
        }
        if (recent.isNotEmpty()) {
            ColorLabel("最近使用")
            ColorSwatches(recent.toList(), current, { pick(it) })
        }
    }
}

/** 手势始终调用最新颜色状态；取消或切换模式也会结束交互状态。 */
@Composable
private fun colorDragModifier(
    onInteracting: (Boolean) -> Unit,
    onPosition: (Offset, androidx.compose.ui.unit.IntSize) -> Unit,
    onFinished: () -> Unit,
    canStart: (Offset, androidx.compose.ui.unit.IntSize) -> Boolean = { _, _ -> true }
): Modifier {
    val active by rememberUpdatedState(onInteracting)
    val position by rememberUpdatedState(onPosition)
    val finished by rememberUpdatedState(onFinished)
    val accepted by rememberUpdatedState(canStart)
    return Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!accepted(down.position, size)) return@awaitEachGesture
            down.consume()
            active(true)
            try {
                position(down.position, size)
                val completed = drag(down.id) { change ->
                    position(change.position, size)
                    change.consume()
                }
                if (completed) finished()
            } finally { active(false) }
        }
    }
}

@Composable
private fun ColorHueBar(
    hue: Float, onActive: (Boolean) -> Unit, onChange: (Float) -> Unit, onFinished: () -> Unit
) {
    val drag = colorDragModifier(onActive, { point, size ->
        onChange((point.x / size.width.coerceAtLeast(1) * 359.999f).coerceIn(0f, 359.999f))
    }, onFinished)
    Column {
        ColorLabel("色相")
        Canvas(Modifier.fillMaxWidth().height(44.dp).then(drag).semantics {
            contentDescription = "色相"
            progressBarRangeInfo = ProgressBarRangeInfo(hue, 0f..359.999f)
            setProgress { onChange(it.coerceIn(0f, 359.999f)); onFinished(); true }
        }) {
            val trackHeight = 18.dp.toPx()
            drawRoundRect(Brush.horizontalGradient(HueColors),
                topLeft = Offset(0f, (size.height - trackHeight) / 2f),
                size = Size(size.width, trackHeight), cornerRadius = CornerRadius(trackHeight / 2f))
            drawColorMarker(Offset(hue / 360f * size.width, size.height / 2f), Color.hsv(hue, 1f, 1f))
        }
    }
}

@Composable
private fun ColorValueSlider(
    label: String, value: Float, onChange: (Float) -> Unit, onActive: (Boolean) -> Unit,
    onFinished: () -> Unit, tint: Color, maximum: Int = 100
) {
    DisposableEffect(Unit) { onDispose { onActive(false) } }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ColorLabel(label)
            ColorLabel("${(value * maximum).roundToInt()}${if (maximum == 100) "%" else ""}")
        }
        CMSSlider(value = value,
            onValueChangeStarted = { onActive(true) }, onValueChange = onChange,
            onValueChangeFinished = { onFinished(); onActive(false) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), selectedColor = tint,
            thumbStyle = CMSSliderThumbStyle.Solid,
            dampingEnabled = false, thicknessAnimation = false, overscrollEnabled = false,
            liveUpdate = true)
    }
}

@Composable
private fun ColorLabel(text: String) {
    Text(text, color = SecondaryText, fontSize = 11.sp, fontFamily = HarmonySansSC, style = CMSNoFontPadding)
}

@Composable
private fun ColorSwatches(colors: List<Color>, selected: Color, onSelected: (Color) -> Unit) {
    colors.chunked(6).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(6) { index ->
                val entry = row.getOrNull(index)
                if (entry == null) Box(Modifier.weight(1f).height(44.dp))
                else {
                    val active = entry.toArgb() == selected.toArgb()
                    Box(Modifier.weight(1f).height(44.dp).padding(2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(if (active) 2.dp else 1.dp,
                            if (active) PrimaryText else DividerColor, RoundedCornerShape(8.dp))
                        .clickable { onSelected(entry) }
                        .semantics {
                            this.selected = active
                            contentDescription = "颜色 #%08X".format(entry.toArgb())
                        }) {
                        Canvas(Modifier.fillMaxSize()) { drawCheckerboard(6.dp.toPx()) }
                        Box(Modifier.fillMaxSize().background(entry))
                        if (active) Text("✓", Modifier.align(Alignment.Center),
                            color = if (entry.red * 0.299f + entry.green * 0.587f + entry.blue * 0.114f > 0.55f)
                                Color.Black else Color.White,
                            fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawColorMarker(point: Offset, color: Color) {
    val radius = 7.dp.toPx()
    val safe = Offset(point.x.coerceIn(radius, (size.width - radius).coerceAtLeast(radius)),
        point.y.coerceIn(radius, (size.height - radius).coerceAtLeast(radius)))
    drawCircle(Color.Black.copy(alpha = 0.45f), radius + 1.dp.toPx(), safe)
    drawCircle(Color.White, radius, safe)
    drawCircle(color, radius - 2.dp.toPx(), safe)
}

private fun DrawScope.drawCheckerboard(cell: Float) {
    for (row in 0..(size.height / cell).toInt()) {
        for (column in 0..(size.width / cell).toInt()) {
            drawRect(if ((row + column) % 2 == 0) Color(0xFFE6E6E6) else Color(0xFFBDBDBD),
                Offset(column * cell, row * cell), Size(cell, cell))
        }
    }
}
