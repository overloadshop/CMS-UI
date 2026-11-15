package com.lindicra.cms

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import com.lindicra.cms.ui.theme.LocalGlassHazeState
import com.lindicra.cms.ui.theme.LocalGlassMaterial
import com.lindicra.cms.ui.theme.PageBackground
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeState

/**
 * A native sampling host containing page content only; glass remains its sibling.
 *
 * [backgroundColor] must be the colour the page actually paints behind its content.
 * A transparent host records transparent pixels in the gaps between components, and a
 * glass layer sampling them gets no colour to refract there.
 */
@Composable
fun CMSGlassContentSource(
    modifier: Modifier = Modifier,
    sourceTag: String = CMS_GLASS_CONTENT_SOURCE_TAG,
    onSourceReady: (android.view.ViewGroup) -> Unit = {},
    hazeState: HazeState? = null,
    backgroundColor: Color = PageBackground,
    content: @Composable () -> Unit
) {
    val parent = rememberCompositionContext()
    val latest = rememberUpdatedState(content)
    val haze = hazeState ?: LocalGlassHazeState.current
    val frosted = LocalGlassMaterial.current.frosted
    AndroidView(
        modifier = if ((hazeState != null || frosted) && haze != null) modifier.hazeSource(haze) else modifier,
        factory = { context ->
            FrameLayout(context).apply {
                tag = sourceTag
                clipChildren = false
                clipToPadding = false
                if (backgroundColor != Color.Unspecified) {
                    setBackgroundColor(backgroundColor.toArgb())
                }
                addView(ComposeView(context).apply {
                    setParentCompositionContext(parent)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent { latest.value() }
                }, FrameLayout.LayoutParams(-1, -1))
            }.also(onSourceReady)
        }
    )
}

/** Legacy entry point kept for existing call sites. */
@Composable
@Deprecated("Use CMSGlassContentSource", ReplaceWith("CMSGlassContentSource(modifier, sourceTag, onSourceReady, hazeState, content)"))
fun CMGlassContentSource(
    modifier: Modifier = Modifier,
    sourceTag: String = CMS_GLASS_CONTENT_SOURCE_TAG,
    onSourceReady: (android.view.ViewGroup) -> Unit = {},
    hazeState: HazeState? = null,
    content: @Composable () -> Unit
) {
    CMSGlassContentSource(modifier, sourceTag, onSourceReady, hazeState, Color.Unspecified,
        content = content)
}
