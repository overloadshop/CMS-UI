# 玻璃融合 · Glass Fusion

`CMS.Interaction.GlassFusion` 为子树内的玻璃控件提供融合组。同组玻璃在靠近时形成联合光学表面，分开后恢复独立轮廓；各自的形状和点击区域仍由控件管理。

## 两块普通玻璃

推荐使用 CMS.Interaction.GlassFusion 包住 CMS.Controls.GlassButton。普通玻璃按距离融合时设置 proximityOnly = true，无需手动管理组号、供体方向或成长进度。原生层按两块玻璃的实际左右位置配对，并保留各自圆角；RTL 布局也按屏幕位置处理。

配对后的两块玻璃在靠近和分开时都由同一联合光学层绘制。融合距离只控制连接处的形变与吸引，不切换折射、高光或内容绘制层；超过距离后连接自然收回，各自轮廓保留。首次配对时连接从零形变进入弹簧动画，快速反向保留当前形变和速度。

```kotlin
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lindicra.cms.*
import com.lindicra.cms.ui.theme.*
import dev.chrisbanes.haze.HazeState

@Composable
fun GlassFusionExample() {
    var separated by remember { mutableStateOf(false) }
    val gap by animateDpAsState(if (separated) 36.dp else 2.dp, label = "glassGap")
    var source by remember { mutableStateOf<android.view.ViewGroup?>(null) }
    val haze = remember { HazeState() }
    CMS.Appearance.Theme(hazState = haze) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            CMS.Content.Source(
                Modifier.fillMaxSize(), hazeState = haze,
                onSourceReady = { source = it }
            ) {
                Column(Modifier.fillMaxSize().background(PageBackground),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(6) { index ->
                        Box(Modifier.fillMaxWidth().height(52.dp)
                            .background(LocalCMSAccentColor.current.copy(alpha = 0.08f + index % 4 * 0.04f)))
                    }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CMS.Interaction.GlassFusion(mergeGap = 16.dp, proximityOnly = true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        listOf("A", "B").forEach { title ->
                            CMS.Controls.GlassButton(
                                onClick = { separated = !separated },
                                width = 96.dp, height = 72.dp, cornerRadius = 24.dp,
                                sampleContent = true, samplingSource = source, hazeState = haze
                            ) { foreground -> CMS.Content.Text(title, color = foreground) }
                        }
                    }
                }
            }
        }
    }
}
```

| 示例操作 / 参数 | 结果 |
| --- | --- |
| 初始间距 2dp、融合距离 16dp | 两块玻璃形成可见的连接液面 |
| 点任意一块 | 间距动画变为 36dp，玻璃分开 |
| 再点一次 | 回到 2dp，重新融合 |
| 关闭启用融合 | 两块普通玻璃和点击动作保留 |
| 调整宽高与圆角 | 两块玻璃使用相同尺寸，方便观察连接处 |

基础案例使用 16dp 融合距离，让靠近、连接和分开的变化容易观察；接口默认仍为 3dp，适合更克制的融合范围。

背景色带只是采样内容；玻璃位于采样源的兄弟层。示例没有搜索框、键盘或取消状态。

## 高级示例：搜索与取消

搜索框是供体；展开时向取消键转移宽度，取消键随成长进度出现。反向操作给同一弹簧设新目标，保留当前进度和速度。取消键尚未充分分离时不可点击，回收后移除其点击语义与融合实体。

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lindicra.cms.*
import com.lindicra.cms.ui.theme.*
import dev.chrisbanes.haze.HazeState

@Composable
fun SearchFusionExample() {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf<android.view.ViewGroup?>(null) }
    val haze = remember { HazeState() }
    CMS.Appearance.Theme(hazState = haze) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            CMS.Content.Source(
                Modifier.fillMaxSize(),
                hazeState = haze,
                onSourceReady = { source = it }
            ) {
                CMS.Content.ScrollView(sampleGlassContent = false) {
                    Spacer(Modifier.height(64.dp))
                    CMS.Content.Section {
                        repeat(20) { index ->
                            CMS.Content.ListRow("工具 ${index + 1}", showChevron = false) {}
                        }
                    }
                }
            }
            CMS.Interaction.GlassSearch(
                query = query,
                onQueryChange = { query = it },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.padding(12.dp),
                placeholder = "搜索工具",
                cancelText = "取消",
                samplingSource = source,
                hazeState = haze,
                onSearch = { /* 宿主提交搜索请求 */ }
            )
        }
    }
}
```

query、expanded 由宿主持有。输入时更新 query，键盘搜索动作通过 onSearch 提交业务请求；取消收起搜索并释放焦点，不自动清空查询。焦点只在输入节点完成布局且展开充分后请求，避免初次展开时向尚未初始化的节点请求焦点。

GlassSearch 可调 placeholder、cancelText、enabled、fusionEnabled、height、cancelWidth、gap、mergeGap、gravityStrength、samplingSource、hazeState。默认 44dp 高、54dp 取消宽、8dp 分离间距、3dp 融合距离。关闭真实光学材质时保留搜索功能，省略融合和流体形变。

## 自定义工具栏接入

```kotlin
CMS.Interaction.GlassFusion(
    enabled = true,
    mergeGap = 3.dp,
    proximityOnly = true,
    gravityStrength = 1f
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("历史", "搜索", "设置").forEach { title ->
            CMS.Controls.GlassButton(
                onClick = { /* 当前按钮的业务动作 */ },
                width = 64.dp,
                height = 44.dp,
                sampleContent = true,
                samplingSource = source,
                hazeState = haze
            ) { foreground ->
                CMS.Content.Text(title, color = foreground)
            }
        }
    }
}
```

此片段使用前一例的 source / haze，在采样源的兄弟层放置。工具栏靠近融合设置 proximityOnly = true；自定义分离 / 回收动画可使用 false，并给各玻璃的 metaballEmergence 提供成长进度。

融合组只注册其中的 CMS 玻璃，保留每个按钮自己的点击范围。每个作用域自动创建独立组号；两个页面或两个搜索栏不会因为硬编码相同组号而互相融合。嵌套组覆盖父组，离开作用域后原生 View 更新注册。

## 分组参数与底层扩展

| 参数 | 默认值 | 含义 |
| --- | --- | --- |
| enabled | true | 是否给玻璃注册融合组 |
| mergeGap | 3dp | 接近时的融合距离 |
| proximityOnly | false | 按实际左右位置配对、按距离产生连接，适合普通玻璃；分开时保留联合光学层 |
| progress | 1f | 整组显现进度 |
| gravityStrength | 1f | 相互吸引强度 |

CMS.Controls.GlassButton 自动继承作用域；无需自己拼组号。proximityOnly = true 时直接为普通玻璃按位置配对。顶层 CMSGlassButton 仍可显式覆盖 metaballGroupId、metaballAnchorRight、metaballEmergence、metaballColor 等底层参数；有明确供体方向的分离动画使用 proximityOnly = false。

## 采样与材质

内容采样源必须与玻璃是兄弟层。不要把玻璃或包含玻璃的祖先作为 samplingSource，否则会递归采样。所有参与同一融合的玻璃使用一致的内容源和材质。

API 33+ 且光学材质开启时显示原生融合。毛玻璃或关闭高级材质时仍保留搜索、点击、焦点与取消操作。关闭 fusionEnabled 仅关闭融合，不取消功能。

## 搜索状态与默认参数

| GlassSearch 参数 | 默认值 / 契约 |
| --- | --- |
| query / onQueryChange | 必填，宿主持有查询字符串 |
| expanded / onExpandedChange | 必填，宿主持有展开意图 |
| placeholder / cancelText | “搜索工具” / “取消” |
| enabled / fusionEnabled | true / true |
| height / cancelWidth / gap | 44dp / 54dp / 8dp |
| mergeGap / gravityStrength | 3dp / 1f |
| samplingSource / hazeState | null / null，可显式共用背景状态 |
| onSearch | 空回调，宿主提交查询 |

| 操作 | 结果 |
| --- | --- |
| 展开 | 搜索框向取消键转移宽度，布局就绪后请求焦点 |
| 输入 | 更新 query，不自动请求业务搜索 |
| 键盘搜索 | 调用 onSearch |
| 取消 | 收起、释放焦点；query 保留 |
| 动画中反向 | 给现有弹簧新目标，保留进度与速度 |
| 关闭融合 | 功能保留，省略融合外观 |

仅支持光学材质的系统才显示原生融合效果。设备验收应覆盖快速展开 / 收起、软键盘变化、取消键分离前后的点击区域，以及两组玻璃同时存在时是否独立。搜索结果、空状态、加载和错误提示由应用负责。

相关：[采样源](ContentSource.md)、[玻璃按钮](GlassButton.md)、[交互索引](InteractionGuide.md)。
