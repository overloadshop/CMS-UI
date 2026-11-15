package com.lindicra.cms

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.lindicra.cms.ui.theme.PageBackground
import com.lindicra.cms.ui.theme.CardBackground
import com.lindicra.cms.ui.theme.GlassMaterial
import com.lindicra.cms.ui.theme.LocalClearIsDark
import com.lindicra.cms.ui.theme.LocalGlassMaterial
import com.lindicra.cms.ui.theme.LocalGlassHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import com.qmdeve.liquidglass.widget.LiquidGlassView
import com.qmdeve.liquidglass.widget.LiquidGlassHighlightMetrics
import kotlin.math.abs

/** 玻璃采样源在窗口内的复用标记 */
private const val GLASS_SOURCE_TAG = "cms_glass_sample_source"
const val CMS_GLASS_CONTENT_SOURCE_TAG = "cms_glass_content_sample_source"

/** Body geometry inside a fixed optical viewport; sampled pixels never scale with the body. */
data class CMSGlassFluidShape(val width: Dp, val height: Dp, val strain: Float = 0f,
                             val shear: Float = 0f, val restingTint: Color = Color.Transparent,
                             val opticalProgress: Float = 1f)

/** Extra rendering room belongs to the blur layer, never to the control's layout or hit area. */
@Composable
internal fun CMBlurLayer(
    modifier: Modifier = Modifier,
    maxRadius: Dp = 18.dp,
    radius: () -> Dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val latestRadius = rememberUpdatedState(radius)
    val edge = maxRadius * 3
    Layout(
        modifier = modifier,
        content = {
            Box(
                Modifier
                    .graphicsLayer {
                        clip = false
                        val px = with(density) { latestRadius.value().toPx() }.coerceAtLeast(0f)
                        renderEffect = if (Build.VERSION.SDK_INT >= 31 && px > 0.1f) {
                            android.graphics.RenderEffect.createBlurEffect(
                                px, px, android.graphics.Shader.TileMode.DECAL
                            ).asComposeRenderEffect()
                        } else null
                    }
                    .padding(edge)
            ) { content() }
        }
    ) { measurables, constraints ->
        val margin = edge.roundToPx()
        val child = measurables.single().measure(constraints.offset(margin * 2, margin * 2))
        layout(child.width - margin * 2, child.height - margin * 2) {
            child.place(-margin, -margin)
        }
    }
}

internal fun resolveGlassSource(
    ctx: android.content.Context,
    sampleContent: Boolean,
    receiver: View
): ViewGroup? {
    var activity: android.app.Activity? = ctx as? android.app.Activity
    var probe: android.content.Context? = ctx
    while (activity == null && probe is android.content.ContextWrapper) {
        probe = probe.baseContext
        activity = probe as? android.app.Activity
    }
    val root = activity?.window?.decorView
        ?.findViewById<ViewGroup>(android.R.id.content) ?: return null

    if (sampleContent) {
        // 独立 Popup 不在宿主采样树中；窗口内控件仍须选择安全的内容兄弟。
        if (receiver.isAttachedToWindow && receiver.rootView !== root.rootView) {
            return root
        }
        fun containsReceiver(source: ViewGroup): Boolean {
            var ancestor: android.view.ViewParent? = receiver.parent
            if (source === receiver) return true
            while (ancestor != null) {
                if (ancestor === source) return true
                ancestor = ancestor.parent
            }
            return false
        }
        // 嵌套滚动区可能重复注册标记，选择可见的最大内容视口。
        val sources = mutableListOf<ViewGroup>()
        fun collect(view: View) {
            if (view is ViewGroup) {
                if (view.tag == CMS_GLASS_CONTENT_SOURCE_TAG &&
                    view.isShown && view.width > 0 && view.height > 0 && !containsReceiver(view)
                ) sources.add(view)
                view.children.forEach(::collect)
            }
        }
        collect(root)
        sources.maxByOrNull { it.width.toLong() * it.height }?.let { return it }
    }

    (root.findViewWithTag<View>(GLASS_SOURCE_TAG) as? android.widget.FrameLayout)
        ?.let { return it.also { source -> source.setBackgroundColor(PageBackground.toArgb()) } }

    val source = android.widget.FrameLayout(ctx).apply {
        tag = GLASS_SOURCE_TAG
        setBackgroundColor(PageBackground.toArgb())
    }
    root.addView(
        source,
        0,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )
    return source
}

/**
 * 通用液态玻璃按钮：支持文字胶囊和图标按钮，独立于返回按钮。
 * API 33+ 使用 LiquidGlassView，低版本使用无图像处理的玻璃降级。
 */
@Composable
fun CMSGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 36.dp,
    height: Dp = 36.dp,
    enabled: Boolean = true,
    useNativeGlass: Boolean = true,
    sampleContent: Boolean = false,
    samplingSource: ViewGroup? = null,
    hazeState: HazeState? = null,
    menuSurface: Boolean = false,
    shadowStrength: Float = if (menuSurface) 3f else 1f,
    exclusiveTouchGesture: Boolean = false,
    interactiveContent: Boolean = false,
    touchEffectIntensity: Float = 1f,
    glassTint: Color = Color.White,
    glassTintAlpha: Float? = null,
    glassBackground: Color = Color.White.copy(alpha = 0.08f),
    motionBlur: Dp = 0.dp,
    metaballGroupId: String? = LocalCMSGlassFusion.current?.groupId,
    metaballProximityOnly: Boolean = LocalCMSGlassFusion.current?.proximityOnly ?: false,
    metaballAnchorRight: Boolean = false,
    metaballMergeGap: Dp = LocalCMSGlassFusion.current?.mergeGap ?: 18.dp,
    metaballProgress: Float = LocalCMSGlassFusion.current?.progress ?: 1f,
    metaballColor: Color = Color.White,
    gravityStrength: Float = LocalCMSGlassFusion.current?.gravityStrength ?: 1f,
    metaballEmergence: Float = 1f,
    cornerRadius: Dp? = null,
    touchEffectRadius: Dp = 100.dp,
    highlightMaxWidth: Dp = Dp.Infinity,
    highlightWidthScale: Float = 1f,
    externalTouchFeedback: Boolean = false,
    feedbackPosition: androidx.compose.ui.geometry.Offset? = null,
    onLongClick: (() -> Unit)? = null,
    onTouch: ((androidx.compose.ui.geometry.Offset, Int) -> Boolean)? = null,
    adaptiveAppearance: Boolean = false,
    edgeOnly: Boolean = false,
    clearLens: Boolean = false,
    // Blur applied to a clear lens' sample. It stays zero for the resting lens and lets a
    // floating one frost what it refracts without tinting it.
    clearLensBlur: Dp = 0.dp,
    samplingUnderlay: ViewGroup? = null,
    fluidShape: CMSGlassFluidShape? = null,
    samplingScreenPosition: androidx.compose.ui.geometry.Offset? = null,
    samplingForeground: ViewGroup? = null,
    /** 外部触点可只驱动辉光，形变由宿主统一控制。 */
    externalTouchDeformation: Boolean = true,
    content: @Composable (Color) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ownsGlassDrag = exclusiveTouchGesture && LocalCMSTouchPriority.current.glassDragPriority
    val parentComposition = rememberCompositionContext()
    val baseMaterial = LocalGlassMaterial.current
    // Menus keep the former comfortable optical baseline in the real-glass
    // mode; the page's other glass controls retain their own material setting.
    val material = if (menuSurface && baseMaterial.clarity == 2) {
        GlassMaterial(baseMaterial.advanced, 1)
    } else {
        baseMaterial
    }
    val contentHaze = LocalGlassHazeState.current
    val emptyHaze = remember { HazeState() }
    val surfaceHaze = hazeState ?: if (sampleContent) contentHaze ?: emptyHaze else emptyHaze
    val isDark = LocalClearIsDark.current
    val contentColor = MaterialTheme.colorScheme.onSurface
    var sampledLight by remember { mutableStateOf(!isDark) }
    val adaptiveForeground by androidx.compose.animation.animateColorAsState(
        if (sampledLight) Color(0xFF17191C) else Color.White,
        animationSpec = androidx.compose.animation.core.tween(160),
        label = "glassSampledForeground"
    )
    val safeWidth = width.coerceAtLeast(0.dp)
    val safeHeight = height.coerceAtLeast(0.dp)
    val resolvedTintAlpha = (glassTintAlpha ?: if (isDark) 0.06f else 0.05f) * material.tintMultiplier
    val materialBackground = glassBackground.copy(alpha = glassBackground.alpha * material.tintMultiplier)
    // Grouped glass owns its complete tint. A clipped content background would
    // reintroduce the two original capsule silhouettes over the optical union.
    val groupedGlass = metaballGroupId != null && material.optical
    val surfaceTintAlpha = if (groupedGlass) {
        1f - (1f - resolvedTintAlpha) * (1f - materialBackground.alpha)
    } else resolvedTintAlpha
    val surfaceTint = if (groupedGlass && surfaceTintAlpha > 0f) {
        val baseWeight = resolvedTintAlpha * (1f - materialBackground.alpha)
        Color(
            red = ((glassTint.red * baseWeight + materialBackground.red * materialBackground.alpha) / surfaceTintAlpha).coerceIn(0f, 1f),
            green = ((glassTint.green * baseWeight + materialBackground.green * materialBackground.alpha) / surfaceTintAlpha).coerceIn(0f, 1f),
            blue = ((glassTint.blue * baseWeight + materialBackground.blue * materialBackground.alpha) / surfaceTintAlpha).coerceIn(0f, 1f)
        )
    } else glassTint
    val metaballMergeGapPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        metaballMergeGap.coerceAtLeast(0.dp).toPx()
    }
    val metaballTintArgb = metaballColor.toArgb()
    val radius = cornerRadius ?: safeHeight / 2
    val shape = RoundedCornerShape(radius)
    val cornerRadiusPx = with(LocalDensity.current) { radius.toPx() }
    val motionBlurPx = with(androidx.compose.ui.platform.LocalDensity.current) { motionBlur.toPx() }
    // A clear lens grows its edge curvature with the fluid lift: at rest it needs
    // only a hairline bevel, but once the lens floats it has to bend light over a
    // band that scales with the body, otherwise the rim reads as a cut edge.
    val lensClarity = (fluidShape?.opticalProgress ?: 0f).coerceIn(0f, 1f)
    val clearBevelDp = 3f + 9f * lensClarity
    val clearOffsetDp = 5f + 21f * lensClarity
    // Frost fades in with the lift instead of switching on: the resting lens must stay
    // perfectly sharp, and the blur has to arrive together with the refraction.
    val clearLensBlurPx = clearLensBlur.value * lensClarity
    val refractionTuning = remember { floatArrayOf(-1f, -1f) }
    val latestContent = rememberUpdatedState(content)
    val latestContentColor = rememberUpdatedState(if (adaptiveAppearance) adaptiveForeground else contentColor)
    val latestGlassBg = rememberUpdatedState(if (groupedGlass) Color.Transparent else materialBackground)
    val latestMaterial = rememberUpdatedState(material)
    val latestHaze = rememberUpdatedState(surfaceHaze)
    val latestTint = rememberUpdatedState(glassTint)
    val latestSamplingSource = rememberUpdatedState(samplingSource)
    val latestShape = rememberUpdatedState(shape)
    val motionBlurModifier = if (motionBlur > 0.dp) {
        Modifier.blur(motionBlur, BlurredEdgeTreatment.Unbounded)
    } else {
        Modifier
    }
    val shadowSpread = 100.dp
    val drawShadow = !isDark || menuSurface
    var glassScaleX by remember { mutableFloatStateOf(1f) }
    var glassScaleY by remember { mutableFloatStateOf(1f) }
    var glassTranslationX by remember { mutableFloatStateOf(0f) }
    var glassTranslationY by remember { mutableFloatStateOf(0f) }

    if (useNativeGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        key(contentColor) {
            CMBlurLayer(
                modifier = modifier
                    .width(safeWidth)
                    .height(safeHeight)
                    .graphicsLayer { clip = false },
                radius = { if (groupedGlass) 0.dp else motionBlur }
            ) {
                Layout(
                    {
                    if (drawShadow) {
                    // 独立阴影层：源图形是圆角矩形的描边环（天然空心，不擦除底层），
                    // 再对这一层做高斯模糊，得到跟随圆角曲率、中心不压暗的柔和阴影。
                    Box(
                        modifier = Modifier
                            // 阴影拥有独立的超出画布，但父按钮尺寸不变、不参与排版。
                            .size(safeWidth + shadowSpread * 2, safeHeight + shadowSpread * 2)
                            .graphicsLayer {
                                clip = false
                                // 跟随 LiquidGlassView 内部的弹簧形变，而不是只跟随页面动画。
                                scaleX = glassScaleX
                                scaleY = glassScaleY
                                // 阴影与玻璃共享完整位移，拉伸时轮廓保持重合。
                                translationX = glassTranslationX
                                translationY = glassTranslationY
                            }
                            .drawBehind {
                                // 用同心圆角描边模拟高斯衰减，避免 RenderEffect 在超出边界
                                // 与弹簧变换叠加时产生对角采样伪影。
                                val edge = shadowSpread.toPx()
                                val corner = radius.toPx()
                                val layers = arrayOf(
                                    16.dp.toPx() to 0.00085f,
                                    12.dp.toPx() to 0.0017f,
                                    8.dp.toPx() to 0.003f,
                                    5.dp.toPx() to 0.005f,
                                    2.dp.toPx() to 0.008f
                                )
                                layers.forEach { (strokeWidth, alpha) ->
                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = (alpha * shadowStrength).coerceIn(0f, 1f)),
                                        topLeft = androidx.compose.ui.geometry.Offset(edge, edge),
                                        size = androidx.compose.ui.geometry.Size(
                                            safeWidth.toPx(),
                                            safeHeight.toPx()
                                        ),
                                        cornerRadius = CornerRadius(corner, corner),
                                        style = Stroke(width = strokeWidth)
                                    )
                                }
                            }
                    )
                }
                AndroidView(
                    modifier = Modifier
                        .width(safeWidth)
                        .height(safeHeight)
                        .cmsGlassDragRegion(enabled && ownsGlassDrag)
                        .graphicsLayer { clip = false },
                    factory = { ctx ->
                    LiquidGlassView(ctx).apply {
                        setOpticalEnabled(material.optical)
                        setSurfaceHighlightsEnabled(material.advanced)
                        setInputEnabled(enabled)
                        setExclusiveTouchGesture(ownsGlassDrag)
                        setInteractiveContentEnabled(interactiveContent)
                        setCornerRadius(cornerRadiusPx)
                        setBlurRadius(if (clearLens) clearLensBlurPx * ctx.resources.displayMetrics.density
                            else if (edgeOnly) 0f else material.blurRadius)
                        setEdgeOnly(edgeOnly)
                        setClearLens(clearLens)
                        setSamplingUnderlay(samplingUnderlay)
                        setSamplingForeground(samplingForeground)
                        setSamplingScreenPosition(samplingScreenPosition?.x ?: 0f,
                            samplingScreenPosition?.y ?: 0f, samplingScreenPosition != null)
                        setFluidShape(
                            fluidShape?.width?.value?.times(ctx.resources.displayMetrics.density) ?: 0f,
                            fluidShape?.height?.value?.times(ctx.resources.displayMetrics.density) ?: 0f,
                            fluidShape?.strain ?: 0f, fluidShape?.shear ?: 0f)
                        setFluidSurface(fluidShape?.restingTint?.toArgb() ?: 0,
                            fluidShape?.opticalProgress ?: 1f)
                        setDispersion(0.3f)
                        val density = ctx.resources.displayMetrics.density
                        setRefractionHeight((if (edgeOnly) 3f else if (clearLens) clearBevelDp else 12f) * density)
                        setRefractionOffset((if (edgeOnly) 5f else if (clearLens) clearOffsetDp else 40f) * density)
                        setTouchEffectEnabled(true)
                        setTouchEffectIntensity(touchEffectIntensity)
                        setTouchEffectRadius(touchEffectRadius.value)
                        setHighlightMaxWidth(highlightMaxWidth.value)
                        setHighlightWidthScale(highlightWidthScale)
                        setExternalTouchFeedbackEnabled(externalTouchFeedback)
                        setExternalTouchDeformationEnabled(externalTouchDeformation)
                        setMenuLongPressThreshold(CM_MENU_HOLD_MILLIS, CM_MENU_DRAG_SLOP_DP)
                        setTouchObserver(onTouch?.let { observer ->
                            LiquidGlassView.TouchObserver { x, y, action ->
                                observer(androidx.compose.ui.geometry.Offset(x, y), action)
                            }
                        })
                        setOnLongClickListener(onLongClick?.let { callback ->
                            View.OnLongClickListener { callback(); transferTouchFeedback(); true }
                        })
                        setGravityStrength(gravityStrength)
                        setMetaballEmergence(metaballEmergence)
                        setMetaballMotionBlur(motionBlurPx)
                        setMetaballProximityOnly(metaballProximityOnly)
                        setElasticEnabled(true)
                        setAdaptiveTintEnabled(adaptiveAppearance)
                        setOnBrightnessChangedListener { brightness ->
                            if (brightness > 0.55f) sampledLight = true
                            else if (brightness < 0.45f) sampledLight = false
                        }
                        setTintAlpha(surfaceTintAlpha)
                        setTintColorRed(surfaceTint.red)
                        setTintColorGreen(surfaceTint.green)
                        setTintColorBlue(surfaceTint.blue)
                        setMetaballGroup(
                            metaballGroupId,
                            metaballAnchorRight,
                            metaballMergeGapPx,
                            metaballProgress,
                            metaballTintArgb
                        )
                            addView(
                            ComposeView(ctx).apply {
                                setParentCompositionContext(parentComposition)
                                // 普通按钮只绘制内容；可编辑玻璃面板显式开启 Compose 输入。
                                isEnabled = interactiveContent
                                isClickable = interactiveContent
                                isFocusable = interactiveContent
                                isFocusableInTouchMode = interactiveContent
                                setContent {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(latestShape.value),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!edgeOnly && !clearLens) CMGlassSurface(
                                            latestMaterial.value,
                                            latestHaze.value,
                                            latestTint.value,
                                            latestGlassBg.value
                                        )
                                        latestContent.value(latestContentColor.value)
                                    }
                                }
                            },
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        // 原生绘制状态保持不变，避免显隐时内部高光/阴影硬切。
                        isEnabled = true
                        // 由外层 alpha/scale 做流体退场，禁止原生 View 硬切造成高光闪烁。
                        visibility = android.view.View.VISIBLE
                        setTouchEffectEnabled(true)
                        setOnTransformChangedListener { scaleX, scaleY, translationX, translationY ->
                            glassScaleX = scaleX
                            glassScaleY = scaleY
                            glassTranslationX = translationX
                            glassTranslationY = translationY
                        }
                        setOnClickListener { if (enabled) onClick() }
                        // 绑定采样源：必须是「不包含玻璃自身」的 ViewGroup，
                        // 否则采样树会形成 RenderNode 循环引用，直接在 hwui 阶段栈溢出闪退。
                        post {
                            (latestSamplingSource.value ?: resolveGlassSource(ctx, sampleContent, this))?.let { source ->
                                bind(source)
                            }
                        }
                    }
                },
                    update = { view ->
                    view.setOpticalEnabled(material.optical)
                    view.setSurfaceHighlightsEnabled(material.advanced)
                    view.setBlurRadius(if (clearLens) clearLensBlurPx * context.resources.displayMetrics.density
                        else if (edgeOnly) 0f else material.blurRadius)
                    if (view.isEdgeOnly != edgeOnly || view.isClearLens != clearLens) {
                        view.setEdgeOnly(edgeOnly)
                        view.setClearLens(clearLens)
                        view.setRefractionHeight((if (edgeOnly) 3f else if (clearLens) clearBevelDp else 12f) * context.resources.displayMetrics.density)
                        view.setRefractionOffset((if (edgeOnly) 5f else if (clearLens) clearOffsetDp else 40f) * context.resources.displayMetrics.density)
                    }
                    if (clearLens) {
                        val tuningDensity = context.resources.displayMetrics.density
                        val heightPx = clearBevelDp * tuningDensity
                        val offsetPx = clearOffsetDp * tuningDensity
                        if (abs(refractionTuning[0] - heightPx) > 0.5f || abs(refractionTuning[1] - offsetPx) > 0.5f) {
                            view.setRefractionHeight(heightPx)
                            view.setRefractionOffset(offsetPx)
                            refractionTuning[0] = heightPx
                            refractionTuning[1] = offsetPx
                        }
                    }
                    view.setInputEnabled(enabled)
                    view.setSamplingUnderlay(samplingUnderlay)
                    view.setSamplingForeground(samplingForeground)
                    view.setSamplingScreenPosition(samplingScreenPosition?.x ?: 0f,
                        samplingScreenPosition?.y ?: 0f, samplingScreenPosition != null)
                    view.setFluidShape(
                        fluidShape?.width?.value?.times(context.resources.displayMetrics.density) ?: 0f,
                        fluidShape?.height?.value?.times(context.resources.displayMetrics.density) ?: 0f,
                        fluidShape?.strain ?: 0f, fluidShape?.shear ?: 0f)
                    view.setFluidSurface(fluidShape?.restingTint?.toArgb() ?: 0,
                        fluidShape?.opticalProgress ?: 1f)
                    view.setExclusiveTouchGesture(ownsGlassDrag)
                    if (view.isAdaptiveTintEnabled != adaptiveAppearance) {
                        view.setAdaptiveTintEnabled(adaptiveAppearance)
                    }
                    if (view.isAttachedToWindow) {
                        val source = samplingSource ?: resolveGlassSource(view.context, sampleContent, view)
                        if (source != null && view.metaballSamplingSource !== source) {
                            view.bind(source)
                        }
                    }
                    view.setInteractiveContentEnabled(interactiveContent)
                    view.setCornerRadius(cornerRadiusPx)
                    view.isEnabled = true
                    view.visibility = android.view.View.VISIBLE
                    view.setTouchEffectEnabled(true)
                    view.setTouchEffectIntensity(touchEffectIntensity)
                    view.setTouchEffectRadius(touchEffectRadius.value)
                    view.setHighlightMaxWidth(highlightMaxWidth.value)
                    view.setHighlightWidthScale(highlightWidthScale)
                    view.setExternalTouchFeedbackEnabled(externalTouchFeedback)
                    view.setExternalTouchDeformationEnabled(externalTouchDeformation)
                    view.setMenuLongPressThreshold(CM_MENU_HOLD_MILLIS, CM_MENU_DRAG_SLOP_DP)
                    if (externalTouchFeedback) {
                        view.updateExternalTouchFeedback(
                            feedbackPosition?.x ?: 0f, feedbackPosition?.y ?: 0f,
                            feedbackPosition != null
                        )
                    }
                    view.setTouchObserver(onTouch?.let { observer ->
                        LiquidGlassView.TouchObserver { x, y, action ->
                            observer(androidx.compose.ui.geometry.Offset(x, y), action)
                        }
                    })
                    view.setOnLongClickListener(onLongClick?.let { callback ->
                        View.OnLongClickListener { callback(); view.transferTouchFeedback(); true }
                    })
                    view.setGravityStrength(gravityStrength)
                    view.setMetaballEmergence(metaballEmergence)
                    view.setMetaballMotionBlur(motionBlurPx)
                    view.setMetaballProximityOnly(metaballProximityOnly)
                    // The optical renderer inserts itself at index 0 after attachment.
                    view.children.filterIsInstance<ComposeView>().firstOrNull()?.apply {
                        isEnabled = interactiveContent
                        isClickable = interactiveContent
                        isFocusable = interactiveContent
                        isFocusableInTouchMode = interactiveContent
                    }
                    if (!adaptiveAppearance) {
                        view.setTintAlpha(surfaceTintAlpha)
                        view.setTintColorRed(surfaceTint.red)
                        view.setTintColorGreen(surfaceTint.green)
                        view.setTintColorBlue(surfaceTint.blue)
                    }
                    view.setMetaballGroup(
                        metaballGroupId,
                        metaballAnchorRight,
                        metaballMergeGapPx,
                        metaballProgress,
                        metaballTintArgb
                    )
                    view.setOnTransformChangedListener { scaleX, scaleY, translationX, translationY ->
                        glassScaleX = scaleX
                        glassScaleY = scaleY
                        glassTranslationX = translationX
                        glassTranslationY = translationY
                    }
                    view.setOnClickListener { if (enabled) onClick() }
                }
            )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { clip = false }
                ) { measurables, _ ->
                    val buttonWidth = safeWidth.roundToPx()
                    val buttonHeight = safeHeight.roundToPx()
                    val shadowIndex = if (drawShadow) 0 else -1
                    val glassIndex = if (drawShadow) 1 else 0
                    val shadow = if (shadowIndex >= 0) measurables[shadowIndex] else null
                    val glass = measurables[glassIndex]
                    val spread = shadowSpread.roundToPx()
                    val shadowPlaceable = shadow?.measure(
                        Constraints.fixed(buttonWidth + spread * 2, buttonHeight + spread * 2)
                    )
                    val glassPlaceable = glass.measure(
                        Constraints.fixed(buttonWidth, buttonHeight)
                    )
                    layout(buttonWidth, buttonHeight) {
                        shadowPlaceable?.place(-spread, -spread)
                        glassPlaceable.place(0, 0)
                    }
                }
        }
        }
    } else {
        Box(
            modifier = modifier
                .width(safeWidth)
                .height(safeHeight)
                .then(motionBlurModifier)
                .then(if (menuSurface) Modifier.shadow(
                    elevation = 16.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = (0.04f * shadowStrength).coerceIn(0f, 1f)),
                    spotColor = Color.Black.copy(alpha = (0.04f * shadowStrength).coerceIn(0f, 1f))
                ) else Modifier)
                .clip(shape)
                .then(if (!edgeOnly && !clearLens && material.advanced && !material.frosted) Modifier.background(
                    Brush.verticalGradient(
                        if (glassTint == Color.White) {
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.18f else 0.34f),
                                Color.White.copy(alpha = if (isDark) 0.05f else 0.12f)
                            )
                        } else {
                            listOf(
                                glassTint.copy(alpha = if (isDark) 0.34f else 0.26f),
                                glassTint.copy(alpha = if (isDark) 0.15f else 0.11f)
                            )
                        }
                    )
                ) else Modifier)
                .then(if (material.advanced || edgeOnly) Modifier.drawWithCache {
                    val edgeWidth = LiquidGlassHighlightMetrics.widthPx(
                        size.width, size.height, density,
                        highlightWidthScale, highlightMaxWidth.value, 1f
                    )
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val edgePath = Path().apply {
                        when (outline) {
                            is Outline.Rectangle -> addRect(outline.rect)
                            is Outline.Rounded -> addRoundRect(outline.roundRect)
                            is Outline.Generic -> addPath(outline.path)
                        }
                    }
                    val edgeBrush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.38f else 0.7f),
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = if (isDark) 0.38f else 0.7f)
                        )
                    )
                    onDrawWithContent {
                        drawContent()
                        // The outer half is clipped by the same surface outline.
                        // Avoid Border's integer-pixel rounding and zero-width hairline.
                        if (edgeWidth > 0f) drawPath(
                            edgePath, edgeBrush, style = Stroke(edgeWidth * 2f)
                        )
                    }
                } else Modifier)
                .then(when {
                    !enabled -> Modifier
                    onLongClick != null -> cmMenuAnchorModifier(onClick, onLongClick)
                    else -> Modifier.clickable(onClick = onClick)
                }),
            contentAlignment = Alignment.Center
        ) {
            if (!edgeOnly && !clearLens && !material.optical) CMGlassSurface(material, surfaceHaze, glassTint, materialBackground)
            content(contentColor)
        }
    }
}

/** Surface and foreground are siblings: text is never blurred with the backdrop. */
@Composable
private fun CMGlassSurface(
    material: GlassMaterial,
    haze: HazeState,
    tint: Color,
    background: Color
) {
    val base = if (tint == Color.White) CardBackground else
        androidx.compose.ui.graphics.lerp(CardBackground, tint, 0.18f)
    val surface = when {
        !material.advanced -> Modifier.background(base)
        material.frosted -> Modifier.hazeEffect(haze) {
            backgroundColor = PageBackground
            blurRadius = 24.dp
            noiseFactor = 0.02f
            tints = listOf(HazeTint(base.copy(alpha = 0.56f)))
            fallbackTint = HazeTint(base.copy(alpha = 0.94f))
        }
        else -> Modifier.background(background)
    }
    Box(Modifier.fillMaxSize().then(surface))
}
