package ldyc.qy.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import com.adamglin.composecontinuousroundedcornershape.ContinuousRoundedCornerShape
import ldyc.qy.music.ui.theme.*

/** 设置页公共布局；间距由 Divider 明确分隔。 */
@Composable
fun CMSSettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(ContinuousRoundedCornerShape(20.dp))
        .background(CardBackground).padding(18.dp), content = content)
}

@Composable
fun CMSSettingsDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(DividerColor))
}

@Composable
fun CMSSettingsSectionTitle(title: String) {
    SettingsText(title, 13, TertiaryText, FontWeight.Medium,
        Modifier.padding(start = 2.dp, top = 2.dp))
}

@Composable
private fun SettingsText(text: String, size: Int, color: Color,
    weight: FontWeight? = null, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = color, fontSize = size.sp,
        fontWeight = weight, fontFamily = HarmonySansSC,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
}

@Composable
private fun RowScope.SettingsCaption(title: String, desc: String, danger: Boolean = false) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsText(title, 15, if (danger) Color(0xFFFF6B6B) else PrimaryText, FontWeight.Medium)
        if (desc.isNotBlank()) SettingsText(desc, 11, SecondaryText)
    }
}

@Composable
fun CMSSettingsSwitchRow(title: String, desc: String = "", checked: Boolean,
    onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsCaption(title, desc)
        Spacer(Modifier.width(12.dp))
        CMSSwitch(checked, onCheckedChange)
    }
}

/** 实色 25dp 胶囊承载菜单锚点；长按与滑动沿用 CMS 的连续触摸会话。 */
@Composable
fun CMSSettingsMenuRow(title: String, desc: String = "", value: String,
    enabled: Boolean = true, onOpen: (IntRect) -> Unit) {
    var bounds by remember { mutableStateOf(IntRect.Zero) }
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically) {
        SettingsCaption(title, desc)
        Row(Modifier.widthIn(min = 80.dp).height(27.dp)
            .onGloballyPositioned { bounds = it.boundsInWindow().roundToIntRect() }
            .clip(ContinuousRoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.cmMenuAnchor(
                onOpen = { onOpen(bounds) }, onLongOpen = { onOpen(bounds) }) else Modifier),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(25.dp).clip(ContinuousRoundedCornerShape(10.dp))
                .background(TertiaryFill).padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SettingsText(value, 12, LocalCMSAccentColor.current, FontWeight.Medium)
            }
            SettingsText("▼", 8, TertiaryText, modifier = Modifier.padding(start = 5.dp))
        }
    }
}

@Composable
fun CMSSettingsNavRow(title: String, desc: String = "", danger: Boolean = false,
    showChevron: Boolean = true, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (pressed && onClick != null) DividerColor else Color.Transparent,
        tween(140, easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)), label = "settingsNavBg")
    Row(Modifier.fillMaxWidth().drawBehind {
        if (bg.alpha > 0f) drawRoundRect(bg, cornerRadius = CornerRadius(10.dp.toPx()))
    }.then(if (onClick != null) Modifier.clickable(
        interactionSource = interaction, indication = null, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        SettingsCaption(title, desc, danger)
        if (showChevron) Icon(painterResource(R.drawable.ic_chevron_right), null,
            tint = TertiaryText, modifier = Modifier.size(14.dp))
    }
}
