package com.lindicra.cms

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 播放器流体背景 — 对齐鸿蒙 FloatingPlayer 展开态背景（仅流体模式 playerBackgroundMode === 0）
 *
 * 图层结构（从底到顶，与鸿蒙 build() 完全一致）：
 *  1. 基色层   Rect(bgColorBase = blend 0.1:0.4:0.5).opacity(0.96)
 *  2. 封面层   Image(cover).opacity(0.06)（fluid 档 cover 极淡，靠 blend 底色托底）
 *  3. 流体     FluidBlob × 3（452/408/364 vp，每个 4 lobe 圆）：
 *              blob 整体物理位移（±188 vp 量级）+ scale 呼吸 + lobe 各自摆动/缩放，
 *              颜色 = palette 2/3/4 色轮转，容器透明度 0.96/0.88/0.8
 *  4. 背景整体 blur(380) + scale(1.08)（鸿蒙 getExpandedBackdropBlur/Scale）
 *  5. 压暗层   Rect('#081018').opacity(0.06 + 亮度自适应 boost，上限 0.5)
 *              —— 深蓝黑而非纯黑，且亮度越高压越重，保证上层文字可读
 * 其余背景模式（模糊封面 / 纯色）由设置决定，未实现前绝不预加载。
 *
 * 性能调度架构：
 *  - 封面 palette 取色：即时执行（决定背景/进度条颜色，不进后台节流队列）
 *  - 流体物理计算运行在独立动画引擎线程（CM-FluidEngine，Display 优先级），
 *    以 60Hz 时钟驱动，只把最终矩阵写入 FloatState；
 *    Compose 侧 graphicsLayer block 直接读 FloatState —— 仅触发图层矩阵局部更新，
 *    不触发 measure / layout / 整树重组，主线程零计算。
 *  - 播放器收起时引擎自动降频空转（不写状态，省电）。
 */
@Composable
fun CMSPlayerBackground(
    coverUrl: String,
    isPlaying: Boolean,
    energy: Float,
    modifier: Modifier = Modifier
) {
    var paletteColors by remember { mutableStateOf(defaultPaletteColors) }
    var previousPaletteColors by remember { mutableStateOf(defaultPaletteColors) }
    var displayCoverUrl by remember { mutableStateOf(coverUrl) }
    var oldDisplayCoverUrl by remember { mutableStateOf<String?>(null) }
    val paletteTransition = remember { Animatable(1f) }
    val context = LocalContext.current

    // ── Lv1 视觉相关：palette 取色直接决定流体/进度条颜色，必须即时执行，
    //    不进后台节流队列（否则播放器展开时被挂起/延迟，颜色停留默认值）──
    LaunchedEffect(coverUrl) {
        try {
            val visiblePalette = interpolatePalette(
                previousPaletteColors,
                paletteColors,
                paletteTransition.value
            )
            val bitmap = if (coverUrl.isBlank()) {
                null
            } else withContext(Dispatchers.IO) {
                val req = ImageRequest.Builder(context)
                    .data(coverUrl).crossfade(false).allowHardware(false).build()
                val result = coil.ImageLoader(context).execute(req)
                val d = (result as? coil.request.SuccessResult)?.drawable
                if (d is android.graphics.drawable.BitmapDrawable) d.bitmap else null
            }

            val newPalette = bitmap?.let(::extractColors) ?: defaultPaletteColors
            if (displayCoverUrl == coverUrl && paletteColors == newPalette && paletteTransition.value >= 0.999f) {
                return@LaunchedEffect
            }

            previousPaletteColors = visiblePalette
            paletteColors = newPalette
            oldDisplayCoverUrl = displayCoverUrl.takeIf { it.isNotBlank() && it != coverUrl }
            displayCoverUrl = coverUrl

            // 调度接入：流体子系统重度节流（歌词页+GPU 忙）时直接切色，不播渐变
            if (PerformanceGovernor.getThrottle(RenderSubsystem.FLUID) < 0.15f) {
                paletteTransition.snapTo(1f)
                oldDisplayCoverUrl = null
                return@LaunchedEffect
            }
            paletteTransition.stop()
            paletteTransition.snapTo(0f)
            paletteTransition.animateTo(1f, animationSpec = tween(durationMillis = 480))
            oldDisplayCoverUrl = null
        } catch (_: Exception) {}
    }

    // ── Lv1：动画引擎输出状态（FloatState，引擎线程写 / graphicsLayer 读）──
    // bx/by：blob 整体位移（±188 vp 量级，鸿蒙 blobOffsetX/Y）
    // sx/sy：blob 整体缩放呼吸（鸿蒙 blobScaleX/Y）
    // phase：lobe 摆动/缩放相位（鸿蒙 motionPhase）
    val bx = remember { Array(3) { mutableFloatStateOf(floatArrayOf(-118f, 122f, 8f)[it]) } }
    val by = remember { Array(3) { mutableFloatStateOf(floatArrayOf(-102f, -26f, 132f)[it]) } }
    val sx = remember { Array(3) { mutableFloatStateOf(floatArrayOf(1.12f, 1.08f, 1.1f)[it]) } }
    val sy = remember { Array(3) { mutableFloatStateOf(floatArrayOf(0.96f, 1.06f, 0.98f)[it]) } }
    val phase = remember { mutableFloatStateOf(0f) }
    // 引擎线程读取的最新值（参数每次重组都会刷新，避免长驻 Runnable 捕获首帧旧值，
    // 导致流体不随播放/暂停/音频能量变化）
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentEnergy by rememberUpdatedState(energy)

    // ── 独立动画引擎线程（60Hz，只算矩阵，不触发重组）──
    val engineThread = remember {
        HandlerThread("CM-FluidEngine", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    }
    val engineHandler = remember { Handler(engineThread.looper) }
    DisposableEffect(Unit) {
        onDispose { engineThread.quitSafely() }
    }

    LaunchedEffect(Unit) {
        engineHandler.post(object : Runnable {
            // 引擎线程局部物理状态（非 Compose state，无同步开销）
            private val blobX = floatArrayOf(-118f, 122f, 8f)
            private val blobY = floatArrayOf(-102f, -26f, 132f)
            private val blobScaleX = floatArrayOf(1.12f, 1.08f, 1.1f)
            private val blobScaleY = floatArrayOf(0.96f, 1.06f, 0.98f)
            private val velocityX = floatArrayOf(0.72f, -0.64f, 0.5f)
            private val velocityY = floatArrayOf(0.56f, 0.38f, -0.48f)
            private var motionPhase = 0f
            private var currentIntensity = 0.2f

            override fun run() {
                val expanded = PlayerViewModel.isExpanded

                // 收起中：不写任何状态，直接降频空转（背景保留基色/封面/压暗，无流体实体）
                if (!expanded) {
                    engineHandler.postDelayed(this, 100)
                    return
                }

                // 流体节流由 PerformanceGovernor 统一调度（微内核式不公平调度）：
                // 歌词页可见时 governor 自动降 FLUID 到 LIGHT/MODERATE，
                // 过热时降到 HEAVY，非歌词页满血——不再硬编码 isLyricsPage 判断
                val fluidThrottle = PerformanceGovernor.getThrottle(RenderSubsystem.FLUID)

                // 目标强度：播放中跟随音频能量（节奏越响流体越快），暂停时极缓；governor 节流再乘系数
                val baseTarget = if (currentIsPlaying) (0.6f + currentEnergy * 1.6f) else 0.1f
                val targetIntensity = baseTarget * fluidThrottle
                currentIntensity += (targetIntensity - currentIntensity) * 0.05f

                motionPhase += 0.003f + currentIntensity * 0.01f

                for (i in 0 until 3) {
                    val offX = blobX[i]
                    val offY = blobY[i]
                    var vx = velocityX[i]
                    var vy = velocityY[i]

                    val flowX = sin(motionPhase * 0.82f + offY * 0.005f + i * 1.2f) * 0.48f +
                        cos(motionPhase * 0.43f + offX * 0.0036f + i * 0.55f) * 0.26f
                    val flowY = cos(motionPhase * 0.78f + offX * 0.0042f + i * 0.95f) * 0.46f +
                        sin(motionPhase * 0.38f + offY * 0.0038f + i * 0.72f) * 0.22f

                    vx = vx * 0.952f + flowX * currentIntensity
                    vy = vy * 0.952f + flowY * currentIntensity

                    // Blob 互斥
                    for (j in 0 until 3) {
                        if (i == j) continue
                        val dx = offX - blobX[j]
                        val dy = offY - blobY[j]
                        val distSq = dx * dx + dy * dy
                        if (distSq > 1f && distSq < 62000f) {
                            val repel = 260f / distSq
                            vx += dx * repel * currentIntensity
                            vy += dy * repel * currentIntensity
                        }
                    }

                    // 中心约束
                    val dist = sqrt(offX * offX + offY * offY)
                    if (dist > 168f) {
                        vx += (-offX / dist) * 0.72f * currentIntensity
                        vy += (-offY / dist) * 0.72f * currentIntensity
                    }

                    blobX[i] = offX + vx
                    blobY[i] = offY + vy
                    if (blobX[i] > 186f || blobX[i] < -186f) vx *= -0.82f
                    if (blobY[i] > 196f || blobY[i] < -196f) vy *= -0.82f
                    velocityX[i] = vx
                    velocityY[i] = vy
                    blobX[i] = blobX[i].coerceIn(-188f, 188f)
                    blobY[i] = blobY[i].coerceIn(-196f, 196f)

                    blobScaleX[i] = 1.1f + sin(motionPhase * (0.36f + i * 0.04f) + i * 0.8f) * 0.07f
                    blobScaleY[i] = 0.98f + cos(motionPhase * (0.32f + i * 0.03f) + i * 1.0f) * 0.06f

                    // 输出最终矩阵 → FloatState（仅触发 graphicsLayer 局部更新）
                    bx[i].floatValue = blobX[i]
                    by[i].floatValue = blobY[i]
                    sx[i].floatValue = blobScaleX[i]
                    sy[i].floatValue = blobScaleY[i]
                }
                phase.floatValue = motionPhase

                // 帧间隔随 throttle 反向缩放：满血 16ms，中度降 ~46ms，重度 ~80ms
                val frameMs = (16f / fluidThrottle.coerceAtLeast(0.15f)).toLong().coerceAtMost(80L)
                engineHandler.postDelayed(this, frameMs)
            }
        })
    }

    val transitionProgress = paletteTransition.value
    val visiblePalette = interpolatePalette(previousPaletteColors, paletteColors, transitionProgress)
    val c0 = visiblePalette[0]
    val c1 = visiblePalette[1]
    val c2 = visiblePalette[2]
    val c3 = visiblePalette[3]

    val baseColor = blendColors(c0, c1, c2)
    SideEffect {
        PlayerViewModel.bgBaseColor = baseColor
    }
    // 压暗自适应：鸿蒙 getExpandedBackdropOverlayOpacity（fluid 档）
    // base 0.06 + 流体亮度自适应 boost，上限 0.5；颜色为深蓝黑 #081018 而非纯黑
    val brightness = fluidBrightness(c1, c2, c3)
    val overlayOpacity = min(0.06f + min(brightness * 0.4f, 0.35f), 0.5f)

    // 最外层 clipToBounds：scale 1.08 会把模糊内容向四边放大溢出（对应鸿蒙 backdrop 外层 clip(true)），
    // 不裁的话，关闭下移动画时底部/顶部会残留溢出背景
    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        // 1) 基色层：不透明板（alpha 1.0），不参与模糊。
        //    鸿蒙是 opacity(0.96)，但那 4% 在安卓会透出底下主页；这里做实色兜底，
        //    blur 边缘的透明渐变带叠在它上面时，露出的是基色而非底下的主页 —— 彻底杜绝透出
        Box(
            modifier = Modifier.fillMaxSize().background(baseColor)
        )

        // 2) 模糊层（封面 + 流体）：scale 1.08 + blur（鸿蒙 fluid 档 380，
        //    但 380 在 RenderEffect 上雾化过死、把流体光斑糊没且开销大，收敛到 130 保留流动可见性）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f }
                .blur(130.dp)
        ) {
            // 封面层（鸿蒙 fluid 档 getExpandedBackdropCoverOpacity = 0.06，极淡）
            val oldCoverAlpha = 0.06f * (1f - transitionProgress)
            val newCoverAlpha = if (oldDisplayCoverUrl != null) 0.06f * transitionProgress else 0.06f
            val oldCoverScale = 1f - transitionProgress * 0.35f
            val newCoverScale = if (oldDisplayCoverUrl != null) 0.65f + transitionProgress * 0.35f else 1f
            if (oldDisplayCoverUrl != null && oldCoverAlpha > 0.001f) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(oldDisplayCoverUrl)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = oldCoverAlpha
                        scaleX = oldCoverScale
                        scaleY = oldCoverScale
                    },
                    contentScale = ContentScale.Crop
                )
            }
            if (displayCoverUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(displayCoverUrl).crossfade(false).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = newCoverAlpha
                        scaleX = newCoverScale
                        scaleY = newCoverScale
                    },
                    contentScale = ContentScale.Crop
                )
            }

            // 流体：3 × FluidBlob（鸿蒙 452/408/364 vp，透明度 0.96/0.88/0.8）
            // 平板按屏幕面积等比放大圆形尺寸（手机 scale=1 不变）
            val fluidScale = screenScaleFactor()
            val blobSizes = listOf(452.dp, 408.dp, 364.dp).map { it * fluidScale }
            val blobAlphas = listOf(0.96f, 0.88f, 0.8f)
            val blobTones = arrayOf(c1, c2, c3)
            for (i in 0 until 3) {
                FluidBlob(
                    index = i,
                    sizeDp = blobSizes[i],
                    blobAlpha = blobAlphas[i],
                    bxState = bx[i],
                    byState = by[i],
                    sxState = sx[i],
                    syState = sy[i],
                    phaseState = phase,
                    tones = blobTones
                )
            }
        }

        // 3) 压暗层（鸿蒙 Rect('#081018').opacity(overlayOpacity)）
        //    在 blur 层之外，只压背景；歌词/控件浮在上层不受影响
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF081018).copy(alpha = overlayOpacity))
        )
    }
}

/**
 * 单个流体 Blob — 对齐鸿蒙 FluidBlob(index, size, color, opacity)
 * 容器 size*2.8，内含 4 个 lobe 圆（size/0.72/0.54/0.38），
 * 容器整体物理位移 + 缩放呼吸 + 透明度；lobe 各自摆动/缩放。
 * graphicsLayer block 直接读 FloatState，仅图层矩阵局部更新，不触发重组。
 */
@Composable
private fun FluidBlob(
    index: Int,
    sizeDp: Dp,
    blobAlpha: Float,
    bxState: MutableFloatState,
    byState: MutableFloatState,
    sxState: MutableFloatState,
    syState: MutableFloatState,
    phaseState: MutableFloatState,
    tones: Array<Color>
) {
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.toPx() }
    // 平板位移幅度随屏幕面积缩放同步放大（圆形本体已放大，位移幅度不放大会在视觉上"缩手缩脚"）
    val offsetScalePx = with(density) { (1.dp * screenScaleFactor()).toPx() }
    val lobeSizes = listOf(1f, 0.72f, 0.54f, 0.38f)

    Box(
        modifier = Modifier
            .size(sizeDp * 2.8f)
            .graphicsLayer {
                // blob 整体：物理位移 + 缩放呼吸 + 透明度（鸿蒙 FluidBlob 容器）
                translationX = bxState.floatValue * offsetScalePx
                translationY = byState.floatValue * offsetScalePx
                scaleX = sxState.floatValue
                scaleY = syState.floatValue
                alpha = blobAlpha
            }
    ) {
        for (l in 0 until 4) {
            val tone = tones[(index + l) % 3]
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(sizeDp * lobeSizes[l])
                    .graphicsLayer {
                        // lobe 摆动（鸿蒙 getLobeOffsetX/Y，振幅 size*(0.17+lobe*0.06) 量级）
                        val ampX = sizePx * (0.17f + l * 0.06f)
                        val ampY = sizePx * (0.16f + l * 0.055f)
                        val pxPhase = phaseState.floatValue * (0.9f - index * 0.05f + l * 0.08f) +
                            byState.floatValue * 0.008f + index * 0.9f + l * 1.3f
                        val pyPhase = phaseState.floatValue * (0.82f - index * 0.04f + l * 0.07f) +
                            bxState.floatValue * 0.007f + index * 1.1f + l * 1.7f
                        translationX = sin(pxPhase) * ampX
                        translationY = cos(pyPhase) * ampY
                        // lobe 缩放呼吸（鸿蒙 getLobeScaleX/Y：1±0.28 / 1±0.22）
                        val sxp = phaseState.floatValue * (0.7f + l * 0.05f) + index * 0.8f + l
                        val syp = phaseState.floatValue * (0.64f + l * 0.04f) + index * 0.7f + l * 1.4f
                        scaleX = 1f + sin(sxp) * 0.28f
                        scaleY = 1f + cos(syp) * 0.22f
                    }
                    .background(tone, CircleShape)
            )
        }
    }
}

private val defaultPaletteColors = listOf(
    Color(0xFF20232A), Color(0xFF445064), Color(0xFF374255), Color(0xFF66748C)
)

private fun interpolatePalette(from: List<Color>, to: List<Color>, progress: Float): List<Color> {
    val t = progress.coerceIn(0f, 1f)
    return List(4) { index ->
        interpolateColor(from.getOrElse(index) { defaultPaletteColors[index] }, to.getOrElse(index) { defaultPaletteColors[index] }, t)
    }
}

private fun interpolateColor(from: Color, to: Color, progress: Float): Color {
    val t = progress.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = from.alpha + (to.alpha - from.alpha) * t
    )
}

/** 按权重混合颜色（鸿蒙 blendHexColors：primary 0.1 / secondary 0.4 / tertiary 0.5） */
private fun blendColors(c0: Color, c1: Color, c2: Color): Color {
    val r = (c0.red * 0.1f + c1.red * 0.4f + c2.red * 0.5f)
    val g = (c0.green * 0.1f + c1.green * 0.4f + c2.green * 0.5f)
    val b = (c0.blue * 0.1f + c1.blue * 0.4f + c2.blue * 0.5f)
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
}

/** 流体三色平均亮度 0..1（鸿蒙 getFluidBrightness） */
private fun fluidBrightness(c1: Color, c2: Color, c3: Color): Float {
    val lum1 = 0.299f * c1.red + 0.587f * c1.green + 0.114f * c1.blue
    val lum2 = 0.299f * c2.red + 0.587f * c2.green + 0.114f * c2.blue
    val lum3 = 0.299f * c3.red + 0.587f * c3.green + 0.114f * c3.blue
    return (lum1 + lum2 + lum3) / 3f
}

private fun extractColors(bitmap: Bitmap): List<Color> {
    try {
        val sampleSize = 8
        val w = bitmap.width / sampleSize
        val h = bitmap.height / sampleSize
        if (w <= 0 || h <= 0) return defaultPaletteColors
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        scaled.recycle()
        val centers = simpleKMeans(pixels.toList(), 4)
        return centers.map { Color(it) }.ifEmpty { defaultPaletteColors }
    } catch (_: Exception) { return defaultPaletteColors }
}

private fun simpleKMeans(pixels: List<Int>, k: Int, maxIter: Int = 10): List<Int> {
    if (pixels.isEmpty()) return emptyList()
    val step = (pixels.size / k).coerceAtLeast(1)
    val centers = (0 until k).map { i -> pixels[(i * step).coerceAtMost(pixels.size - 1)] }.toMutableList()
    val assignments = IntArray(pixels.size)
    for (iter in 0 until maxIter) {
        var changed = false
        for (i in pixels.indices) {
            var best = 0; var bestDist = Float.MAX_VALUE
            for (j in centers.indices) { val d = colorDistance(pixels[i], centers[j]); if (d < bestDist) { bestDist = d; best = j } }
            if (assignments[i] != best) { assignments[i] = best; changed = true }
        }
        if (!changed) break
        for (j in centers.indices) {
            val group = pixels.indices.filter { assignments[it] == j }; if (group.isEmpty()) continue
            var r = 0L; var g = 0L; var b = 0L
            for (idx in group) { r += (pixels[idx] shr 16) and 0xFF; g += (pixels[idx] shr 8) and 0xFF; b += pixels[idx] and 0xFF }
            val n = group.size; centers[j] = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }
    }
    return centers
}

private fun colorDistance(c1: Int, c2: Int): Float {
    val dr = ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
    val dg = ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
    val db = (c1 and 0xFF) - (c2 and 0xFF)
    return (dr * dr + dg * dg + db * db).toFloat()
}
