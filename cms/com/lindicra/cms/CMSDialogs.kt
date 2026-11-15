package com.lindicra.cms

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.adamglin.composecontinuousroundedcornershape.ContinuousRoundedCornerShape
import com.lindicra.cms.ui.theme.AccentBlue
import com.lindicra.cms.ui.theme.CardBackground
import com.lindicra.cms.ui.theme.DividerColor
import com.lindicra.cms.ui.theme.HarmonySansSC
import com.lindicra.cms.ui.theme.PrimaryText
import com.lindicra.cms.ui.theme.SecondaryText
import com.lindicra.cms.ui.theme.TertiaryText
import kotlinx.coroutines.launch

// ─────────────────────────── 弹窗族 ───────────────────────────

/** 弹窗按钮定义。 */
data class CMSDialogAction(
    val title: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val emphasized: Boolean = false
)

/** 弹窗按钮排布：横向并排或纵向堆叠。 */
enum class CMSDialogButtonLayout { Horizontal, Vertical }

private const val CMSDangerColor = 0xFFFF6B6B

/** 进场 / 退场弹簧：放大入场、缩回退场，两端都用弹簧。 */
private val DialogEnterSpec = spring<Float>(
    dampingRatio = 0.62f,
    stiffness = 520f,
    visibilityThreshold = 0.001f
)

private val DialogExitSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 620f,
    visibilityThreshold = 0.001f
)

/**
 * 自定义内容弹窗：放大缩小的弹簧进场，关闭时先缩回再移除。
 *
 * @param scrimAlpha 遮罩层不透明度，0 为完全透明
 * @param dismissOnOutsideClick 点击弹窗外部是否关闭
 * @param dismissOnBackPress 返回键是否关闭
 * @param semanticsTitle 打开时供读屏播报的标题
 * content 会收到 `dismiss`，调用它才会走缩放退场动画。
 */
@Composable
fun CMSDialog(
    onDismiss: () -> Unit,
    maxWidth: Dp = 320.dp,
    dismissOnOutsideClick: Boolean = true,
    dismissOnBackPress: Boolean = true,
    scrimAlpha: Float = 0.32f,
    semanticsTitle: String? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) = CMSDialogHost(
    onDismiss = onDismiss,
    maxWidth = maxWidth,
    dismissOnOutsideClick = dismissOnOutsideClick,
    dismissOnBackPress = dismissOnBackPress,
    scrimAlpha = scrimAlpha,
    semanticsTitle = semanticsTitle,
    scaleContentOnly = false,
    content = content
)

/** 警告与动作列表固定位置，仅卡片缩放；通用弹窗保留原有呈现行为。 */
@Composable
private fun CMSDialogHost(
    onDismiss: () -> Unit,
    maxWidth: Dp = 320.dp,
    dismissOnOutsideClick: Boolean = true,
    dismissOnBackPress: Boolean = true,
    scrimAlpha: Float = 0.32f,
    semanticsTitle: String? = null,
    scaleContentOnly: Boolean,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(0.88f) }
    val alpha = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, DialogEnterSpec) }
        alpha.animateTo(1f, tween(90))
    }

    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                launch { scale.animateTo(0.9f, DialogExitSpec) }
                alpha.animateTo(0f, tween(90))
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnOutsideClick,
            usePlatformDefaultWidth = false
        )
    ) {
        // LocalView 必须取自 Dialog 子组合，才能拿到 DialogWindowProvider 对应的窗口。
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(scrimAlpha.coerceIn(0f, 1f))
                if (scaleContentOnly) window.setWindowAnimations(0)
            }
        }
        val contentMotion = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
            transformOrigin = TransformOrigin.Center
        }
        // 用安全区居中：避免弹窗按整屏（含状态栏）居中而显得偏上
        Box(
            Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .then(if (scaleContentOnly) Modifier else contentMotion),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .then(if (scaleContentOnly) contentMotion else Modifier)
                    .clip(ContinuousRoundedCornerShape(20.dp))
                    .background(CardBackground)
                    .padding(20.dp)
                    .semantics {
                        semanticsTitle?.let { paneTitle = it }
                        liveRegion = LiveRegionMode.Polite
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content(close)
            }
        }
    }
}

/**
 * 警告 / 确认弹窗。
 *
 * @param cancelText 传 null 表示只有一个按钮
 * @param buttonLayout 两个按钮横向并排或纵向堆叠
 * @param titleLineHeight 标题行距
 * @param messageLineHeight 说明文字行距
 */
@Composable
fun CMSAlert(
    title: String,
    message: String = "",
    confirmText: String = "好",
    cancelText: String? = null,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
    scrimAlpha: Float = 0.32f,
    dismissOnOutsideClick: Boolean = true,
    dismissOnBackPress: Boolean = true,
    buttonLayout: CMSDialogButtonLayout = CMSDialogButtonLayout.Horizontal,
    titleLineHeight: TextUnit = 24.sp,
    messageLineHeight: TextUnit = 20.sp
) {
    CMSDialogHost(
        onDismiss = onDismiss,
        dismissOnOutsideClick = dismissOnOutsideClick,
        dismissOnBackPress = dismissOnBackPress,
        scrimAlpha = scrimAlpha,
        semanticsTitle = title,
        scaleContentOnly = true
    ) { dismiss ->
        Text(
            title,
            color = PrimaryText,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = HarmonySansSC,
            style = CMSNoFontPadding,
            lineHeight = titleLineHeight,
            modifier = Modifier.semantics { heading() }
        )
        if (message.isNotEmpty()) {
            Text(
                message,
                color = SecondaryText,
                fontSize = 13.sp,
                fontFamily = HarmonySansSC,
                style = CMSNoFontPadding,
                lineHeight = messageLineHeight
            )
        }
        Spacer(Modifier.height(4.dp))
        when (buttonLayout) {
            CMSDialogButtonLayout.Horizontal -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (cancelText != null) {
                    DialogButton(cancelText, Modifier.weight(1f), accent = false) {
                        onCancel?.invoke()
                        dismiss()
                    }
                }
                DialogButton(
                    confirmText,
                    Modifier.weight(1f),
                    accent = true,
                    destructive = destructive
                ) {
                    onConfirm()
                    dismiss()
                }
            }
            CMSDialogButtonLayout.Vertical -> Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (cancelText != null) {
                    DialogButton(cancelText, Modifier.fillMaxWidth(), accent = false) {
                        onCancel?.invoke()
                        dismiss()
                    }
                }
                DialogButton(
                    confirmText,
                    Modifier.fillMaxWidth(),
                    accent = true,
                    destructive = destructive
                ) {
                    onConfirm()
                    dismiss()
                }
            }
        }
    }
}

/**
 * 动作列表弹窗：若干动作 + 取消。
 *
 * @param actionLayout 动作按钮纵向列表或横向并排
 */
@Composable
fun CMSActionSheet(
    actions: List<CMSDialogAction>,
    title: String = "",
    cancelText: String = "取消",
    onDismiss: () -> Unit,
    scrimAlpha: Float = 0.32f,
    dismissOnOutsideClick: Boolean = true,
    dismissOnBackPress: Boolean = true,
    actionLayout: CMSDialogButtonLayout = CMSDialogButtonLayout.Vertical,
    titleLineHeight: TextUnit = 18.sp
) {
    CMSDialogHost(
        onDismiss = onDismiss,
        dismissOnOutsideClick = dismissOnOutsideClick,
        dismissOnBackPress = dismissOnBackPress,
        scrimAlpha = scrimAlpha,
        semanticsTitle = title.ifEmpty { "操作列表" },
        scaleContentOnly = true
    ) { dismiss ->
        if (title.isNotEmpty()) {
            Text(
                title,
                color = TertiaryText,
                fontSize = 12.sp,
                fontFamily = HarmonySansSC,
                style = CMSNoFontPadding,
                lineHeight = titleLineHeight,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        when (actionLayout) {
            CMSDialogButtonLayout.Vertical -> actions.forEachIndexed { index, action ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                }
                ActionSheetRow(action, Modifier.fillMaxWidth()) {
                    action.onClick()
                    dismiss()
                }
            }
            CMSDialogButtonLayout.Horizontal -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actions.forEach { action ->
                    ActionSheetRow(action, Modifier.weight(1f)) {
                        action.onClick()
                        dismiss()
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { dismiss() }
                .semantics { role = Role.Button }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                cancelText,
                color = AccentBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = HarmonySansSC,
                style = CMSNoFontPadding
            )
        }
    }
}

@Composable
private fun ActionSheetRow(
    action: CMSDialogAction,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            action.title,
            color = if (action.destructive) Color(CMSDangerColor) else PrimaryText,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            fontFamily = HarmonySansSC,
            style = CMSNoFontPadding
        )
    }
}

@Composable
private fun DialogButton(
    text: String,
    modifier: Modifier,
    accent: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val color = when {
        destructive -> Color(CMSDangerColor)
        accent -> AccentBlue
        else -> SecondaryText
    }
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent) AccentBlue.copy(alpha = 0.14f) else DividerColor.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = HarmonySansSC,
            style = CMSNoFontPadding
        )
    }
}
