package ldyc.qy.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.round
import ldyc.qy.music.ui.theme.Accent
import ldyc.qy.music.ui.theme.LightPageBackground
import ldyc.qy.music.ui.theme.PageBackground

/**
 * 进度条圆点样式（参数选择，非开关）：按场景选择是否绘制当前进度点位。
 * - None：无圆点（全屏播放器默认，保持原样）
 * - Hollow：胶囊点位（外圈描边 + 内部填充页面背景色，横向拉长连续曲率）
 * - Solid：同 Hollow（统一"填充式"设计，便于按场景传参）
 */
enum class CMSProgressDot { None, Hollow, Solid }

/**
 * 帧形变阻尼：越界量起步 1:1 跟手，之后持续减速但始终有响应，渐近收敛到 max。
 * 用双曲线而非指数衰减，避免指数在中后段快速饱和、造成「拖到一半突然停」的手感。
 */
internal fun dampOverscroll(raw: Float, max: Float): Float {
    if (raw <= 0f) return 0f
    return max * raw / (raw + max)
}

/**
 * CMS 统一进度/调节条 — 鸿蒙版进度条精确翻译，一个组件覆盖所有场景：
 *
 * .trackColor('rgba(255,255,255,0.3)') / .selectedColor(Accent)
 * .trackThickness(isMoving ? 13 : 6) / .animation({ duration: 200 })
 *
 * 能力（按场景参数选择）：
 * - 只读进度：readOnly=true（播放列表迷你进度等，无手势）
 * - 无极调节：默认，任意连续值（音量 / 均衡器 / 声场 / 混响 / 压缩等滑块）
 * - 规定值：steps=N（定时时长等离散档位，点击/拖动吸附到 N+1 个档位）
 * - range：数值区间（默认 0..1；EQ 传 -12..12 等），progress/onSeek 均为区间内真实值
 * - dotStyle：当前值圆点形态（None / Hollow / Solid）
 * - vertical=true：竖向滑条（轨道纵向，进度自底向上，圆点垂直拉长；宽度固定 24dp，高度由外部控制）
 *
 * thicknessAnimation 控制按压增粗与越界渐细；dampingEnabled 控制轻微阻尼跟随；
 * overscrollEnabled 控制端点拉伸。默认全部开启，松手与重新拖动连续衔接位置、速度。
 */
@Composable
@Deprecated("Use CMSSlider", ReplaceWith("CMSSlider(progress, onSeekBegin, onSeek, onSeekEnd, modifier, selectedColor, dotStyle, range, steps, readOnly, enabled, dotRingColor, dotFillDark, dotFillLight, dotStretch, vertical, liveUpdate, thicknessAnimation, dampingEnabled, overscrollEnabled)"))
fun CMSProgressBar(
    progress: Float,
    onSeekBegin: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onSeekEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
    selectedColor: Color = Accent,
    dotStyle: CMSProgressDot = CMSProgressDot.None,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    dotRingColor: Color? = null,
    dotFillDark: Color? = null,
    dotFillLight: Color? = null,
    dotStretch: Dp = 6.dp,
    vertical: Boolean = false,
    liveUpdate: Boolean = false,
    thicknessAnimation: Boolean = true,
    dampingEnabled: Boolean = true,
    overscrollEnabled: Boolean = true
) {
    val overscrollLow = remember { CMProgressMotion(0f) }
    val overscrollHigh = remember { CMProgressMotion(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isDark = ldyc.qy.music.ui.theme.LocalClearIsDark.current
    var dragging by remember { mutableStateOf(false) }
    val latestBegin = rememberUpdatedState(onSeekBegin)
    val latestSeek = rememberUpdatedState(onSeek)
    val latestEnd = rememberUpdatedState(onSeekEnd)
    val latestDamping = rememberUpdatedState(dampingEnabled)
    val latestOverscroll = rememberUpdatedState(overscrollEnabled)
    val latestSteps = rememberUpdatedState(steps)
    val restTrackPx = with(density) { 6.dp.toPx() }
    val dragTrackPx = with(density) { 13.dp.toPx() }
    val thickness = remember { CMProgressMotion(0f) }
    val dotScale = remember { CMProgressMotion(1f) }
    val maxOverscrollPx = with(density) { 16.dp.toPx() }

    // 禁用态：轨道/圆点变灰半透明，手势全部屏蔽（锁定不可交互）
    val disabledColor = Color(0xFF9E9EA3).copy(alpha = 0.45f)
    val activeColor = if (enabled) selectedColor else disabledColor
    val inactiveColor = if (enabled) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.12f)
    val rangeStart = range.start
    val rangeSpan = (range.endInclusive - rangeStart).takeIf { it > 0f } ?: 1f
    val display = remember { CMProgressMotion(((progress - rangeStart) / rangeSpan).coerceIn(0f, 1f)) }
    LaunchedEffect(progress, rangeStart, rangeSpan, dragging, dampingEnabled, liveUpdate) {
        if (!dragging) {
            val target = ((progress - rangeStart) / rangeSpan).coerceIn(0f, 1f)
            if (dampingEnabled) display.animateTo(target, scope, 650f, if (liveUpdate) 1f else 0.95f)
            else display.follow(target)
        }
    }
    LaunchedEffect(dragging, thicknessAnimation) {
        thickness.animateTo(if (dragging && thicknessAnimation) 1f else 0f, scope, 420f, 0.95f)
        dotScale.animateTo(if (dragging) 1.2f else 1f, scope, 500f, 0.86f)
    }
    LaunchedEffect(overscrollEnabled) {
        if (!overscrollEnabled) {
            overscrollLow.animateTo(0f, scope)
            overscrollHigh.animateTo(0f, scope)
        }
    }
    fun seek(position: Float, length: Float) {
        val raw = position / length
        val count = latestSteps.value
        val pct = if (count > 0) round(raw.coerceIn(0f, 1f) * count) / count else raw.coerceIn(0f, 1f)
        if (latestDamping.value) display.animateTo(pct, scope, 1100f, 1f)
        else display.follow(pct)
        latestSeek.value(rangeStart + pct * rangeSpan)
        val low = if (latestOverscroll.value) dampOverscroll(-position, maxOverscrollPx) else 0f
        val high = if (latestOverscroll.value) dampOverscroll(position - length, maxOverscrollPx) else 0f
        if (latestDamping.value) {
            overscrollLow.animateTo(low, scope, 1100f, 1f)
            overscrollHigh.animateTo(high, scope, 1100f, 1f)
        } else {
            overscrollLow.follow(low)
            overscrollHigh.follow(high)
        }
    }
    // A single recognizer waits for axis intent. Cross-axis movement remains available to scrolling.
    val gestureModifier = if (readOnly || !enabled) Modifier else
        Modifier.pointerInput(vertical, rangeStart, rangeSpan, maxOverscrollPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val len = if (vertical) size.height.toFloat() else size.width.toFloat()
                if (len <= 0f) return@awaitEachGesture
                var owned = false
                var completed = false
                var offset = 0f
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        val delta = change.position - down.position
                        val along = if (vertical) delta.y else delta.x
                        val across = if (vertical) delta.x else delta.y
                        if (!owned && abs(across) > viewConfiguration.touchSlop && abs(across) > abs(along)) break
                        if (!owned && abs(along) > viewConfiguration.touchSlop && abs(along) >= abs(across)) {
                            owned = true
                            dragging = true
                            latestBegin.value()
                            // Grabbing the capsule resumes at its visible position, including an interrupted return.
                            val axisDown = if (vertical) len - down.position.y else down.position.x
                            val anchor = len * display.value + overscrollHigh.value - overscrollLow.value
                            offset = if (abs(axisDown - anchor) <= 24.dp.toPx()) anchor - axisDown else 0f
                        }
                        val axis = if (vertical) len - change.position.y else change.position.x
                        if (owned) {
                            seek(axis + offset, len)
                            change.consume()
                        }
                        if (!change.pressed) {
                            if (!owned) {
                                latestBegin.value()
                                seek(axis, len)
                                change.consume()
                            }
                            completed = true
                            latestEnd.value()
                            break
                        }
                        // Parents may claim an unconsumed cross-axis drag in Final.
                        if (!owned) {
                            val final = awaitPointerEvent(PointerEventPass.Final)
                            if (final.changes.any { it.id == down.id && it.isConsumed && it.positionChanged() }) break
                        }
                    }
                } finally {
                    if (owned && !completed) latestEnd.value()
                    dragging = false
                    overscrollLow.animateTo(0f, scope)
                    overscrollHigh.animateTo(0f, scope)
                }
            }
        }

    Canvas(
        modifier = if (vertical) {
            modifier
                // 竖向：宽/高由外部 modifier 控制（如 Row weight 均分 + fillMaxHeight）
                .then(gestureModifier)
        } else {
            modifier
                .fillMaxWidth()
                // 固定高度，轨道垂直居中：变粗/变细只发生在容器内部，不影响下方布局
                .height(32.dp)
                .then(gestureModifier)
        }
    ) {
        val norm = display.value
        val trackPx = restTrackPx + (dragTrackPx - restTrackPx) * thickness.value
        val overLow = overscrollLow.value
        val overHigh = overscrollHigh.value
        val overMag = maxOf(abs(overLow), abs(overHigh))

        // 帧形变变细：从「拖动变粗后的 trackPx」平滑回到「非交互 6dp」，越界越满越接近 6dp
        val overRatio = if (thicknessAnimation) (overMag / maxOverscrollPx).coerceIn(0f, 1f) else 0f
        val restPx = 6.dp.toPx()
        val effTrackPx = trackPx + (restPx - trackPx) * overRatio
        val r = effTrackPx / 2f

        if (vertical) {
            val w = size.width
            val h = size.height
            val offsetX = (w - effTrackPx) / 2f

            // 拉伸方向：顶端向上、底端向下延伸；回弹过冲为负时向内缩进（保持在对应边）
            val startY = -overHigh
            val endY = h + overLow
            val trackH = endY - startY

            // 未选中轨道（整条，含越界拉伸）
            if (trackH > 0f) {
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(offsetX, startY),
                    size = Size(effTrackPx, trackH),
                    cornerRadius = CornerRadius(r, r)
                )
            }

            // 已选中轨道（底端越界时同步向下延伸）
            val activeH = trackH * norm.coerceIn(0f, 1f)
            if (activeH > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(offsetX, endY - activeH),
                    size = Size(effTrackPx, activeH),
                    cornerRadius = CornerRadius(r, r)
                )
            }

            // 当前值圆点：竖向胶囊（垂直拉长），不参与形变（不随越界变细），仅跟随进度末端
            if (dotStyle != CMSProgressDot.None) {
                val fillColor = (if (isDark) dotFillDark else dotFillLight)
                    ?: (if (isDark) dotFillLight else dotFillDark)
                    ?: (if (isDark) PageBackground else LightPageBackground)
                val ringColor = dotRingColor ?: activeColor

                val baseDotPx = with(density) { 13.dp.toPx() }
                val dotW = baseDotPx * dotScale.value
                val dotH = (baseDotPx + with(density) { dotStretch.toPx() }) * dotScale.value
                val ringW = with(density) { 3.dp * dotScale.value }.toPx()

                // 进度末端（含越界延伸），圆点跟随它；在 [startY, endY] 内平滑贴边
                val anchorY = (endY - activeH).coerceIn(startY, endY)
                val anchorLocal = anchorY - startY
                val dotTopLocal = progressDotOffset(anchorLocal, trackH, dotH)
                val dotTop = startY + dotTopLocal
                val left = (w - dotW) / 2f

                // 外圈描边（连续曲率：两端全圆角胶囊）
                drawRoundRect(
                    color = ringColor,
                    topLeft = Offset(left, dotTop),
                    size = Size(dotW, dotH),
                    cornerRadius = CornerRadius(dotW / 2f, dotW / 2f),
                    style = Stroke(width = ringW)
                )
                // 内部填充：紧贴描边内壁（inset = 半个描边宽），填满胶囊内部
                val inset = ringW * 0.5f
                val innerW = (dotW - inset * 2).coerceAtLeast(1f)
                val innerH = (dotH - inset * 2).coerceAtLeast(1f)
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(left + inset, dotTop + inset),
                    size = Size(innerW, innerH),
                    cornerRadius = CornerRadius(innerW / 2f, innerW / 2f),
                    style = Fill
                )
            }
        } else {
            val w = size.width
            val h = size.height
            val offsetY = (h - effTrackPx) / 2f

            // 拉伸方向：左端向左、右端向右延伸；回弹过冲为负时向内缩进（保持在对应边，不翻转到另一边）
            val startX = -overLow
            val endX = w + overHigh
            val trackW = endX - startX

            // 未选中轨道（整条，含越界拉伸）
            if (trackW > 0f) {
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(startX, offsetY),
                    size = Size(trackW, effTrackPx),
                    cornerRadius = CornerRadius(r, r)
                )
            }

            // 已选中轨道（右端越界时同步向右延伸，覆盖延伸段）
            val activeW = trackW * norm.coerceIn(0f, 1f)
            if (activeW > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(startX, offsetY),
                    size = Size(activeW, effTrackPx),
                    cornerRadius = CornerRadius(r, r)
                )
            }

            // 当前值圆点：胶囊点位（外圈描边 + 内部填充页面背景色，横向拉长连续曲率）。
            // 圆点不参与形变（不随越界变细），仅跟随进度末端（含越界延伸）。
            if (dotStyle != CMSProgressDot.None) {
                val fillColor = (if (isDark) dotFillDark else dotFillLight)
                    ?: (if (isDark) dotFillLight else dotFillDark)
                    ?: (if (isDark) PageBackground else LightPageBackground)
                val ringColor = dotRingColor ?: activeColor

                val baseDotH = with(density) { 13.dp.toPx() }
                val baseDotW = with(density) { (13.dp + dotStretch).toPx() }
                val dotH = baseDotH * dotScale.value
                val dotW = baseDotW * dotScale.value
                val ringW = with(density) { 3.dp * dotScale.value }.toPx()

                // 进度末端（含越界延伸），圆点跟随它；在 [startX, endX] 内平滑贴边
                val anchorX = startX + activeW
                val anchorLocal = anchorX - startX
                val dotLeftLocal = progressDotOffset(anchorLocal, trackW, dotW)
                val dotLeft = startX + dotLeftLocal
                val top = h / 2f - dotH / 2f

                // 外圈描边（连续曲率：两端全圆角胶囊）
                drawRoundRect(
                    color = ringColor,
                    topLeft = Offset(dotLeft, top),
                    size = Size(dotW, dotH),
                    cornerRadius = CornerRadius(dotH / 2f, dotH / 2f),
                    style = Stroke(width = ringW)
                )
                // 内部填充：紧贴描边内壁（inset = 半个描边宽），填满胶囊内部
                val inset = ringW * 0.5f
                val innerW = (dotW - inset * 2).coerceAtLeast(1f)
                val innerH = (dotH - inset * 2).coerceAtLeast(1f)
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(dotLeft + inset, top + inset),
                    size = Size(innerW, innerH),
                    cornerRadius = CornerRadius(innerH / 2f, innerH / 2f),
                    style = Fill
                )
            }
        }
    }
}
