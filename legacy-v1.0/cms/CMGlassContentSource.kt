package ldyc.qy.music

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import ldyc.qy.music.ui.theme.LocalGlassHazeState
import ldyc.qy.music.ui.theme.LocalGlassMaterial
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeState

/** A native sampling host containing page content only; glass remains its sibling. */
@Composable
@Deprecated("Use CMSGlassContentSource", ReplaceWith("CMSGlassContentSource(modifier, sourceTag, onSourceReady, hazeState, content)"))
fun CMGlassContentSource(
    modifier: Modifier = Modifier,
    sourceTag: String = CMS_GLASS_CONTENT_SOURCE_TAG,
    onSourceReady: (android.view.ViewGroup) -> Unit = {},
    hazeState: HazeState? = null,
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
                addView(ComposeView(context).apply {
                    setParentCompositionContext(parent)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent { latest.value() }
                }, FrameLayout.LayoutParams(-1, -1))
            }.also(onSourceReady)
        }
    )
}
