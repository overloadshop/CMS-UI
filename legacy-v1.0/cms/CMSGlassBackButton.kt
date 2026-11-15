package ldyc.qy.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import dev.chrisbanes.haze.HazeState
import ldyc.qy.music.ui.theme.LocalCMSAccentColor
import ldyc.qy.music.ui.theme.CMSBackDestination
import ldyc.qy.music.ui.theme.CMSHapticKind
import ldyc.qy.music.ui.theme.LocalCMSBackHistory
import ldyc.qy.music.ui.theme.LocalGlassHazeState
import ldyc.qy.music.ui.theme.rememberCMSHaptic

/**
 * 玻璃返回按钮（1.1.0）
 *
 * - 短按：返回上一页；
 * - 长按：弹出返回历史菜单，直接回到之前任意页面；
 * - 长按菜单放在独立 Popup 窗口中，天然位于玻璃/Haze 采样树之外，
 *   不会形成 RenderNode 自采样；
 * - 历史数据由宿主通过 [LocalCMSBackHistory] 提供；
 *   未提供时长按不弹菜单。
 */
@Composable
@Deprecated("Use CMSBackButton", ReplaceWith("CMSBackButton(onClick, modifier, iconColor, exclusiveTouchGesture)"))
fun CMSGlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconColor: Color? = null,
    exclusiveTouchGesture: Boolean = false
) {
    val context = LocalContext.current
    val pageView = androidx.compose.ui.platform.LocalView.current
    var menuSource by remember { mutableStateOf<android.view.ViewGroup?>(null) }
    val touch = rememberCMMenuTouchState()
    val haptic = rememberCMSHaptic()
    val history = LocalCMSBackHistory.current
    val haze = LocalGlassHazeState.current ?: remember { HazeState() }
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    var lastTouch by remember { mutableStateOf(Offset.Zero) }
    var visible by remember { mutableStateOf(false) }
    var retained by remember { mutableStateOf(false) }
    var destinations by remember { mutableStateOf(emptyList<CMSBackDestination>()) }
    val openHistory = {
        val provider = history
        if (provider != null) {
            destinations = provider.previous(context)
            if (destinations.isNotEmpty()) {
                // 菜单在独立窗口中，可采样完整宿主，避免透明视口导致玻璃消失。
                menuSource = pageView.rootView.findViewById<android.view.ViewGroup>(android.R.id.content)
                    ?: resolveGlassSource(context, true, pageView)
                haptic(CMSHapticKind.LongPress)
                visible = true
                retained = true
            }
        }
    }
    Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
        CMSGlassButton(
            onClick = onClick,
            width = 40.dp,
            height = 40.dp,
            // 返回按钮始终落在不透明顶栏/卡片上，采样「页面实色基底」而不是它
            // 背后的滚动内容；否则滚动内容一旦延伸到顶栏后方，按钮就会变成一块
            // 透明窗口，直接透出顶栏下滚动的图片与文字。
            sampleContent = false,
            exclusiveTouchGesture = exclusiveTouchGesture,
            modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow().roundToIntRect() },
            onLongClick = {
                openHistory()
                if (visible && !touch.active) touch.begin(lastTouch, inheritedContact = true)
            },
            onTouch = { point, action ->
                lastTouch = point
                val inherited = visible && touch.active
                if (inherited) {
                    touch.move(point)
                    when (action) {
                        android.view.MotionEvent.ACTION_UP -> touch.finish()
                        android.view.MotionEvent.ACTION_CANCEL -> touch.finish(cancelled = true)
                    }
                }
                inherited
            }
        ) { color ->
            Icon(
                painter = painterResource(R.drawable.chevron_left),
                contentDescription = "返回",
                tint = iconColor ?: color,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    // CMSelectMenu owns the window overlay; the anchor retains this touch
    // session and the menu remains composed through its exit animation.
    if (visible || retained) {
            CMSelectMenu(
                hazeState = haze,
                samplingSource = menuSource,
                items = destinations.map { CMSelectMenuItem(it.label) },
                currentIndex = -1,
                anchorRect = anchor,
                visible = visible,
                touchState = touch,
                onClosed = { retained = false },
                type = CMSMenuType.Actions,
                accentColor = LocalCMSAccentColor.current,
                onSelect = { index ->
                    visible = false
                    val target = destinations.getOrNull(index)
                    if (target != null) history?.returnTo(context, target.id)
                },
                onDismiss = { visible = false }
            )
    }
}
