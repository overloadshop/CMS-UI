package com.lindicra.cms

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import com.lindicra.cms.ui.theme.*
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first

/** 同一搜索实体向取消键转移宽度，反向操作从当前形状与速度继续。 */
@Composable
fun CMSGlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索工具",
    cancelText: String = "取消",
    enabled: Boolean = true,
    fusionEnabled: Boolean = true,
    height: Dp = 44.dp,
    cancelWidth: Dp = 54.dp,
    gap: Dp = 8.dp,
    mergeGap: Dp = 3.dp,
    gravityStrength: Float = 1f,
    samplingSource: ViewGroup? = null,
    hazeState: HazeState? = null,
    onSearch: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val material = LocalGlassMaterial.current
    val optical = material.optical && android.os.Build.VERSION.SDK_INT >= 33
    val motion = remember { CMProgressMotion(if (expanded) 1f else 0f) }
    val focus = remember { FocusRequester() }
    var inputReady by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val search by rememberUpdatedState(onSearch)
    val currentQuery by rememberUpdatedState(query)
    LaunchedEffect(expanded, optical, enabled) {
        motion.animateTo(if (expanded) 1f else 0f, scope,
            stiffness = 230f, damping = if (optical) 0.76f else 1f)
        if (expanded && enabled) {
            snapshotFlow { motion.value >= 0.98f && inputReady }.first { it }
            focus.requestFocus()
            keyboard?.show()
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    DisposableEffect(motion) { onDispose { motion.follow(motion.value) } }
    val progress = motion.value.coerceIn(0f, 1f)
    val growth = progress * progress * (3f - 2f * progress)
    val haptic = rememberCMSHaptic()
    val accent = LocalCMSAccentColor.current
    CMSGlassFusionGroup(fusionEnabled && optical, mergeGap,
        gravityStrength = gravityStrength) {
        BoxWithConstraints(modifier.fillMaxWidth().height(height.coerceAtLeast(36.dp))) {
            val buttonWidth = cancelWidth.coerceIn(36.dp, maxOf(36.dp, maxWidth / 3))
            val spacing = gap.coerceIn(0.dp, 24.dp)
            val donorWidth = (maxWidth - (buttonWidth + spacing) * progress)
                .coerceAtLeast(36.dp)
            CMSGlassButton(
                onClick = {
                    if (enabled) {
                        haptic(CMSHapticKind.Click)
                        if (expanded && inputReady) { focus.requestFocus(); keyboard?.show() }
                        else onExpandedChange(true)
                    }
                },
                modifier = Modifier.align(Alignment.CenterStart),
                width = donorWidth, height = height.coerceAtLeast(36.dp),
                enabled = enabled, interactiveContent = expanded,
                sampleContent = true, samplingSource = samplingSource, hazeState = hazeState,
                metaballAnchorRight = true, touchEffectIntensity = if (expanded) 0.3f else 1f
            ) { foreground ->
                DisposableEffect(Unit) { onDispose { inputReady = false } }
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null,
                        tint = foreground, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(placeholder, color = SecondaryText,
                            fontSize = 15.sp, fontFamily = HarmonySansSC, maxLines = 1,
                            style = CMSNoFontPadding)
                        BasicTextField(query, onQueryChange,
                            enabled = enabled && expanded, singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                                .onGloballyPositioned { inputReady = true },
                            textStyle = TextStyle(color = foreground, fontSize = 15.sp,
                                fontFamily = HarmonySansSC).merge(CMSNoFontPadding),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                search(currentQuery)
                                focusManager.clearFocus()
                                keyboard?.hide()
                            }))
                    }
                }
            }
            // 收起后移除取消实体和语义；展开及回收期间持续保留融合注册。
            if (expanded || progress > 0.001f) CMSGlassButton(
                onClick = {
                    haptic(CMSHapticKind.Click)
                    focusManager.clearFocus()
                    keyboard?.hide()
                    onExpandedChange(false)
                },
                modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer {
                    alpha = growth
                    val strain = if (optical) (motion.velocity * 0.018f).coerceIn(-0.12f, 0.12f) * growth else 0f
                    val scale = if (optical) 0.4f + 0.6f * growth else 1f
                    scaleX = scale * (1f + strain)
                    scaleY = scale / (1f + strain)
                    translationX = if (optical) -(1f - progress) * 38.dp.toPx() else 0f
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
                width = buttonWidth, height = height.coerceAtLeast(36.dp),
                enabled = enabled && expanded && progress >= 0.98f,
                sampleContent = true, samplingSource = samplingSource, hazeState = hazeState,
                metaballEmergence = growth, glassTint = accent, glassTintAlpha = 0.22f,
                glassBackground = accent.copy(alpha = 0.1f), metaballColor = accent
            ) { foreground ->
                Text(cancelText, color = foreground, fontSize = 14.sp,
                    fontFamily = HarmonySansSC, maxLines = 1, style = CMSNoFontPadding)
            }
        }
    }
}
