package com.lindicra.cms

import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.adamglin.composecontinuousroundedcornershape.ContinuousRoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qmdeve.liquidglass.widget.LiquidGlassView

/**
 * 可复用底栏组件。
 *
 * 三种预设模式：
 * 1. [Mode.TABS_ONLY] — 仅有 Tab 导航，适用于纯底栏场景
 * 2. [Mode.MINIPLAYER_ONLY] — 仅有迷你播放器，适用于播放器专用页
 * 3. [Mode.COMBINED] — Tab 导航 + 迷你播放器横向并排，适用于音乐 App 主页
 *
 * 使用方式：
 * ```
 * val bottomBar = CMSBottomBar(context, CMSBottomBar.Mode.COMBINED)
 * bottomBar.build(
 *     contentContainer = contentContainer,  // LiquidGlass 采样容器
 *     tabContent = { brightness -> TabContent(..., brightness = brightness) },
 *     miniPlayerContent = { brightness -> MiniPlayerContent(..., brightness = brightness) }
 * )
 * // 将 bottomBar 添加到父布局
 * root.addView(bottomBar)
 *
 * // 外部驱动弹簧动画
 * LaunchedEffect(isMiniExpanded) {
 *     springDriver.setTarget(if (isMiniExpanded) 1f else 0f)
 *     bottomBar.tabGlassOriginalWidthPx = ... // 记录原始宽度
 * }
 * springDriver.onUpdate = { progress ->
 *     bottomBar.applySpringProgress(progress, screenWidthPx)
 * }
 * ```
 */
class CMSBottomBar(context: android.content.Context) : LinearLayout(context) {

    /** 底栏预设模式 */
    enum class Mode {
        /** 仅有 Tab 导航 */
        TABS_ONLY,
        /** 仅有迷你播放器 */
        MINIPLAYER_ONLY,
        /** Tab 导航 + 迷你播放器横向并排 */
        COMBINED
    }

    companion object {
        /** 底栏高度 (dp) */
        const val BAR_HEIGHT_DP = 56f
        /** 迷你播放器收起宽度 (dp) */
        const val MINI_COLLAPSED_DP = 56f
        /** 迷你播放器与 Tab 间距 (dp) */
        const val MINI_GAP_DP = 10f
        /** 底栏底部外边距 (dp) */
        const val BAR_BOTTOM_MARGIN_DP = 12f
    }

    private val density = resources.displayMetrics.density
    private val isGlassSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // ── 公开引用，供外部弹簧动画驱动 ──
    /** Tab 栏玻璃视图引用 */
    var tabGlassRef: LiquidGlassView? by mutableStateOf(null)
    /** 迷你播放器玻璃视图引用 */
    var miniGlassRef: LiquidGlassView? by mutableStateOf(null)
    /** Tab 栏玻璃原始像素宽度（展开前），供弹簧动画计算 */
    var tabGlassOriginalWidthPx: Float by mutableFloatStateOf(0f)
    var tabContentBrightness: Float by mutableFloatStateOf(0.5f)
    var miniContentBrightness: Float by mutableFloatStateOf(0.5f)
    var tabSampleMode: String by mutableStateOf("none")
    var miniSampleMode: String by mutableStateOf("none")
    var tabLastSampleUptimeMs: Long by mutableLongStateOf(0L)
    var miniLastSampleUptimeMs: Long by mutableLongStateOf(0L)

    // ── 内部缓存 ──
    private var tabFallbackView: android.view.View? = null
    private var miniFallbackView: android.view.View? = null

    // ── 点击收起 mini player 的状态 ──
    private var tapDownX = 0f
    private var tapDownY = 0f
    private var onMiniExpandedChanged: (() -> Unit)? = null

    init {
        clipChildren = false
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            (BAR_HEIGHT_DP * density).toInt()
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (BAR_BOTTOM_MARGIN_DP * density).toInt()
        }
    }

    /**
     * 构建底栏。
     *
     * @param contentContainer  LiquidGlass 采样容器（可为 null，降级时不使用）
     * @param mode              底栏模式
     * @param onMiniTapCollapse 迷你播放器触点击收起回调（通常设为 `{ PlayerViewModel.isMiniExpanded = false }`）
     * @param tabContent        Tab 导航内容 Composable
     * @param miniPlayerContent 迷你播放器内容 Composable（仅 COMBINED / MINIPLAYER_ONLY 模式需要）
     */
    fun build(
        contentContainer: FrameLayout?,
        mode: Mode = Mode.COMBINED,
        onMiniTapCollapse: (() -> Unit)? = null,
        tabContent: @Composable (Float) -> Unit,
        miniPlayerContent: (@Composable (Float) -> Unit)? = null
    ) {
        removeAllViews()
        onMiniExpandedChanged = onMiniTapCollapse

        when (mode) {
            Mode.TABS_ONLY -> {
                buildTabBar(contentContainer, tabContent)
            }
            Mode.MINIPLAYER_ONLY -> {
                miniPlayerContent?.let { buildMiniPlayer(contentContainer, it) }
            }
            Mode.COMBINED -> {
                buildTabBar(contentContainer, tabContent)
                miniPlayerContent?.let { buildMiniPlayer(contentContainer, it) }
            }
        }
    }

    /**
     * 应用弹簧进度 — 由外部的 LaunchedEffect 帧回调驱动。
     * 更新 Tab 栏宽度 和 迷你播放器宽度/leftMargin。
     *
     * @param progress     弹簧进度 0..1（0=收起，1=展开）
     * @param screenWidthPx 屏幕宽度像素，用于计算迷你播放器展开宽度
     */
    fun applySpringProgress(progress: Float, screenWidthPx: Int) {
        val d = density
        val glassTargetPx = (MINI_COLLAPSED_DP * d).toInt()  // Tab 栏最终宽度（仅圆形）

        // Tab 栏宽度
        tabGlassRef?.let { v ->
            val origW = tabGlassOriginalWidthPx
            if (origW > 0f) {
                val pw = Math.round(origW + (glassTargetPx - origW) * progress)
                if (v.layoutParams?.width != pw) {
                    v.layoutParams?.width = pw
                    v.requestLayout()
                }
            }
        }

        // 迷你播放器宽度 + leftMargin
        miniGlassRef?.let { v ->
            val lp = v.layoutParams
            if (lp is LinearLayout.LayoutParams) {
                val mw = Math.round(
                    (MINI_COLLAPSED_DP * d) + ((screenWidthPx * 0.9f - glassTargetPx) - MINI_COLLAPSED_DP * d) * progress
                )
                val ml = Math.round((MINI_GAP_DP * d) * (1f - progress) + (8f * d) * progress)
                if (lp.width != mw || lp.leftMargin != ml) {
                    lp.width = mw
                    lp.leftMargin = ml
                    v.layoutParams = lp
                }
            }
        }

        // 降级兜底视图也同步宽度
        tabFallbackView?.let { v ->
            val origW = tabGlassOriginalWidthPx
            if (origW > 0f) {
                val glassTargetPx = (MINI_COLLAPSED_DP * d).toInt()
                val pw = Math.round(origW + (glassTargetPx - origW) * progress)
                if (v.layoutParams?.width != pw) {
                    v.layoutParams?.width = pw
                    v.requestLayout()
                }
            }
        }
        miniFallbackView?.let { v ->
            val lp = v.layoutParams
            if (lp is LinearLayout.LayoutParams) {
                val glassTargetPx = (MINI_COLLAPSED_DP * d).toInt()
                val mw = Math.round(
                    (MINI_COLLAPSED_DP * d) + ((screenWidthPx * 0.9f - glassTargetPx) - MINI_COLLAPSED_DP * d) * progress
                )
                val ml = Math.round((MINI_GAP_DP * d) * (1f - progress) + (8f * d) * progress)
                if (lp.width != mw || lp.leftMargin != ml) {
                    lp.width = mw
                    lp.leftMargin = ml
                    v.layoutParams = lp
                }
            }
        }
    }

    // ── 自适应 tint ──

    /** 更新所有 LiquidGlassView 的自适应 tint */
    fun updateAdaptiveTint(brightness: Float) {
        tabGlassRef?.updateAdaptiveTint(brightness)
        miniGlassRef?.updateAdaptiveTint(brightness)
    }

    // ── 内部构建 ──

    /**
     * 构建 Tab 栏。
     * 优先使用 LiquidGlassView（API 33+），否则使用普通 ComposeView + 背景色。
     */
    private fun buildTabBar(contentContainer: FrameLayout?, content: @Composable (Float) -> Unit) {
        if (isGlassSupported && contentContainer != null) {
            val glassView = object : LiquidGlassView(context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            tapDownX = event.x
                            tapDownY = event.y
                        }
                        MotionEvent.ACTION_UP -> {
                            val dx = event.x - tapDownX
                            val dy = event.y - tapDownY
                            if (Math.abs(dx) < 20 && Math.abs(dy) < 20) {
                                onMiniExpandedChanged?.invoke()
                            }
                        }
                    }
                    return super.dispatchTouchEvent(event)
                }
            }.apply {
                setCornerRadius(28f * density)
                setBlurRadius(25f)
                setDispersion(0.3f)
                setRefractionHeight(12f * density)
                setRefractionOffset(40f * density)
                setTouchEffectEnabled(true)
                setElasticEnabled(true)
                setOnBrightnessChangedListener { brightness ->
                    tabContentBrightness = brightness
                    tabSampleMode = getLastSampleMode()
                    tabLastSampleUptimeMs = getLastSampleUptimeMs()
                }

                addView(ComposeView(context).apply {
                    setContent {
                        Box(modifier = Modifier.fillMaxSize()) {
                            content(tabContentBrightness)
                            if (DeveloperSettings.liquidGlassOverlayEnabled) {
                                LiquidGlassDebugOverlay(
                                    label = "TAB",
                                    brightness = tabContentBrightness,
                                    sampleMode = tabSampleMode,
                                    lastSampleUptimeMs = tabLastSampleUptimeMs
                                )
                            }
                        }
                    }
                }, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))

                post {
                    bind(contentContainer)
                    // 记录原始宽度
                    if (tabGlassOriginalWidthPx <= 0f && measuredWidth > 0) {
                        tabGlassOriginalWidthPx = measuredWidth.toFloat()
                    }
                }
            }
            tabGlassRef = glassView
            addView(glassView, LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            ))
        } else {
            // 降级
            val fallback = ComposeView(context).apply {
                setContent {
                    Box(modifier = Modifier.fillMaxSize()) {
                        content(tabContentBrightness)
                        if (DeveloperSettings.liquidGlassOverlayEnabled) {
                            LiquidGlassDebugOverlay(
                                label = "TAB",
                                brightness = tabContentBrightness,
                                sampleMode = "fallback",
                                lastSampleUptimeMs = tabLastSampleUptimeMs
                            )
                        }
                    }
                }
            }
            tabFallbackView = fallback
            addView(fallback, LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            ))
        }
    }

    /**
     * 构建迷你播放器。
     * 优先使用 LiquidGlassView（API 33+），否则使用普通 ComposeView。
     */
    private fun buildMiniPlayer(contentContainer: FrameLayout?, content: @Composable (Float) -> Unit) {
        if (isGlassSupported && contentContainer != null) {
            val glassView = LiquidGlassView(context).apply {
                setCornerRadius(28f * density)
                setBlurRadius(25f)
                setDispersion(0.3f)
                setRefractionHeight(12f * density)
                setRefractionOffset(40f * density)
                setTouchEffectEnabled(true)
                setElasticEnabled(true)
                setOnBrightnessChangedListener { brightness ->
                    miniContentBrightness = brightness
                    miniSampleMode = getLastSampleMode()
                    miniLastSampleUptimeMs = getLastSampleUptimeMs()
                }

                addView(ComposeView(context).apply {
                    setContent {
                        Box(modifier = Modifier.fillMaxSize()) {
                            content(miniContentBrightness)
                            if (DeveloperSettings.liquidGlassOverlayEnabled) {
                                LiquidGlassDebugOverlay(
                                    label = "MINI",
                                    brightness = miniContentBrightness,
                                    sampleMode = miniSampleMode,
                                    lastSampleUptimeMs = miniLastSampleUptimeMs
                                )
                            }
                        }
                    }
                }, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))

                post {
                    bind(contentContainer)
                }
            }
            miniGlassRef = glassView
            addView(glassView, LayoutParams(
                (MINI_COLLAPSED_DP * density).toInt(),
                LayoutParams.MATCH_PARENT
            ).apply {
                leftMargin = (MINI_GAP_DP * density).toInt()
            })
        } else {
            val fallback = ComposeView(context).apply {
                setContent {
                    Box(modifier = Modifier.fillMaxSize()) {
                        content(miniContentBrightness)
                        if (DeveloperSettings.liquidGlassOverlayEnabled) {
                            LiquidGlassDebugOverlay(
                                label = "MINI",
                                brightness = miniContentBrightness,
                                sampleMode = "fallback",
                                lastSampleUptimeMs = miniLastSampleUptimeMs
                            )
                        }
                    }
                }
            }
            miniFallbackView = fallback
            addView(fallback, LayoutParams(
                (MINI_COLLAPSED_DP * density).toInt(),
                LayoutParams.MATCH_PARENT
            ).apply {
                leftMargin = (MINI_GAP_DP * density).toInt()
            })
        }
    }

    @Composable
    private fun BoxScope.LiquidGlassDebugOverlay(
        label: String,
        brightness: Float,
        sampleMode: String,
        lastSampleUptimeMs: Long
    ) {
        val ageMs = (System.currentTimeMillis() - lastSampleUptimeMs).coerceAtLeast(0L)
        Text(
            text = "$label b=${"%.3f".format(brightness)}\n$sampleMode ${ageMs}ms",
            color = Color.White,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.52f), ContinuousRoundedCornerShape(6.dp))
                .padding(horizontal = 5.dp, vertical = 3.dp)
        )
    }
}
