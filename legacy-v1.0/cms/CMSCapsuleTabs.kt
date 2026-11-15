package ldyc.qy.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.composecontinuousroundedcornershape.ContinuousRoundedCornerShape
import ldyc.qy.music.ui.theme.*

/** Compact in-page tabs: 44dp high, 4dp inset, 22/18dp concentric corners. */
@Composable
@Deprecated("Use CMSSegmentedControl", ReplaceWith("CMSSegmentedControl(labels, selectedIndex, onSelectedChange, modifier, outlined)"))
fun CMSCapsuleTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) {
    require(labels.size in 2..4) { "Capsule tabs support 2–4 options" }
    require(selectedIndex in labels.indices) { "selectedIndex must identify an option" }
    val dark = LocalClearIsDark.current
    val foreground = if (dark) Color.White else Color.Black
    val outerShape = ContinuousRoundedCornerShape(22.dp)
    val innerShape = ContinuousRoundedCornerShape(18.dp)
    val haptic = rememberCMSHaptic()
    val position by animateFloatAsState(
        selectedIndex.toFloat(), spring(dampingRatio = 0.9f, stiffness = 300f),
        label = "capsuleTabPosition"
    )
    BoxWithConstraints(
        modifier.height(44.dp).selectableGroup()
            .then(if (outlined) Modifier.border(1.5.dp, foreground.copy(alpha = 0.15f), outerShape)
                else Modifier.background(foreground.copy(alpha = if (dark) 0.06f else 0.04f), outerShape))
            .padding(4.dp)
    ) {
        val slotWidth = maxWidth / labels.size
        val slotPixels = with(LocalDensity.current) { slotWidth.toPx() }
        // Translation only: interrupted transitions retain spring velocity without relayout.
        Box(Modifier.width(slotWidth).fillMaxHeight()
            .graphicsLayer { translationX = position * slotPixels }
            .background(foreground.copy(alpha = if (outlined) 0.1f else if (dark) 0.15f else 0.08f),
                innerShape))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val pressColor by animateColorAsState(
                    if (pressed) Color(0xFF8E8E93).copy(alpha = 0.1f) else Color.Transparent,
                    label = "capsuleTabPress"
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(pressColor, innerShape)
                        .selectable(selected = selectedIndex == index, role = Role.Tab,
                            interactionSource = interaction, indication = null,
                            onClick = {
                                if (selectedIndex != index) {
                                    haptic(CMSHapticKind.Tick)
                                    onSelectedChange(index)
                                }
                            }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (selectedIndex == index) PrimaryText else TertiaryText,
                        fontSize = 14.sp,
                        fontWeight = if (selectedIndex == index) FontWeight.Medium else FontWeight.Normal,
                        fontFamily = HarmonySansSC, maxLines = 1,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
                }
            }
        }
    }
}
