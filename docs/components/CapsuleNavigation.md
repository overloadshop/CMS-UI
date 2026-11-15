# 胶囊导航 · Capsule Tag

这是 [Tabbar 家族](TabBar.md)的胶囊形态，入口为 `CMS.Navigation.CapsuleNavigation`，与玻璃 Tab 栏、下划线标签导航共用受控选择方式。默认自绘底板 / 描边，支持横向拖拽，也可开启纯文字液态玻璃。点击不使用默认涟漪，保留 Tab 语义与触觉反馈。

## CLEAR 默认规格

| 属性 | 默认值 |
| --- | --- |
| 高度 / 外框圆角 | 44dp / 22dp |
| 指示器圆角 / 内边距 | 18dp / 4dp |
| 选项 | 2～4 项，推荐 2～3 项 |
| 文字 | 14sp，常规到 Medium（500）随指示位置渐变 |
| 描边版 | 透明底，1.5dp 描边，前景色 15% |
| 底板版 | 前景色 6% 底板，15% 指示器 |
| 拖拽 / 液态玻璃 | 默认允许拖拽，液态玻璃默认关闭 |

文字默认采用主题的 PrimaryText / SecondaryText，兼容浅色；深色对应 CLEAR 主文字和辅助文字。主题强调色不会强行覆盖中性的胶囊底板。

文字颜色、深浅与字重混合比例由指示器当前连续位置驱动，点击动画和拖动使用同一规则：靠近的项逐渐增强，离开的项逐渐恢复。常规与 Medium 使用独立字形交叉渐变，避免静态字体文件造成字重跳变；主题色和禁用透明度变化也有过渡。语义选中态仍以宿主的 selectedIndex 为准。

## 完整最小示例

```kotlin
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.lindicra.cms.*

@Composable
fun CalculationModeNavigation() {
    var selected by remember { mutableIntStateOf(0) }
    CMS.Navigation.CapsuleNavigation(
        labels = listOf("计算", "工具"),
        selectedIndex = selected,
        onSelectedChange = { selected = it },
        modifier = Modifier.fillMaxWidth(),
        variant = CMSCapsuleVariant.Filled
    )
    // 宿主根据 selected 展示对应内容。
}
```

描边版设置 `variant = CMSCapsuleVariant.Outlined`。

## 自定义与接入

公开参数包含 variant、enabled、height、outerRadius、indicatorRadius、contentInset、borderWidth、fontSize、selectedColor、unselectedColor、containerColor、indicatorColor、pageProgress、pageTouchVersion、draggable、liquidGlass、liquidMotion、samplingSource 和 hazeState。

默认颜色由主题计算。containerColor / indicatorColor 传入 Color.Unspecified 保留自动值；显式传色时使用调用方提供的颜色。

pageProgress 是连续的页面位置，例如 `pager.currentPage + pager.currentPageOffsetFraction`。传入时内容滑页直接驱动指示器；胶囊拖动结束时从当前指示位置平滑交接给真实页面位置。内容每次按下时，在 `Pager.onTouchInterrupt` 中取消宿主翻页任务并递增 `pageTouchVersion`，即可立即打断交接或玻璃落地，恢复页面跟随。接入方式见 [Tab 栏的页面状态示例](TabBar.md#页面状态接入)。省略 pageProgress 时点选采用可打断的弹簧。不要在一次翻页中反复切换是否绑定页面进度。

普通底板 / 描边模式必须从当前可见指示器的圆角轮廓内按住，横向移动超过触摸阈值后才开始拖动；保留按下时的接触偏移，指示器以弹簧平滑跟随，不会突然跳到手指中心。其他位置仍可点击切换，但不能从那里起拖。液态玻璃模式的起拖规则见下节。跨过选项时提供轻触觉反馈，松手只提交一个落点选项，取消时平滑恢复受控选择。拖动期间不会额外触发点击；`draggable = false` 可保留点按、关闭拖动。支持 RTL，禁用后不能提交选择。动态删减选项时，选中索引会限于有效范围；少于两项隐藏，超过四项只展示前四项。大量分类应使用独立筛选界面。

## 液态玻璃

设置 `liquidGlass = true` 后复用玻璃底栏的背景、镜片、抬起、拖动与落地物理，仅显示文字，没有图标间隔。组件仍在调用方指定的位置布局，不会自动移动到底部。`liquidMotion = false` 可关闭镜片流体形变，保留玻璃材质与选择交互。光学效果跟随主题 `GlassMaterial` 和平台能力，不支持时使用现有毛玻璃降级。

玻璃胶囊与玻璃底栏共用触摸逻辑：在任意选项位置按下，镜片都会从当前位置以弹簧滑向触点，即使触点不在当前镜片内。横向移动超过阈值后平滑跟随手指；松手提交落点，镜片独立完成移动和落地，期间页面翻页进度不改变镜片目标。落地后平滑交还页面位置；内容重新按下时通过 `pageTouchVersion` 立即接管，从内容区域滑动页面时直接同步页进度。点击仍可切换，文字样式随镜片连续位置渐变。

```kotlin
var selected by remember { mutableIntStateOf(0) }
var source by remember { mutableStateOf<android.view.ViewGroup?>(null) }
val haze = remember { dev.chrisbanes.haze.HazeState() }
Box(Modifier.fillMaxSize()) {
    CMS.Content.Source(Modifier.fillMaxSize(), hazeState = haze,
        onSourceReady = { source = it }) {
        PageContent(selected)
    }
    CMS.Navigation.CapsuleNavigation(
        labels = listOf("推荐", "收藏", "历史"),
        selectedIndex = selected,
        onSelectedChange = { selected = it },
        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
        draggable = true,
        liquidGlass = true,
        samplingSource = source,
        hazeState = haze
    )
}
```

示例布局另需导入 `androidx.compose.foundation.layout.*` 和 `androidx.compose.ui.Alignment`，`PageContent` 是宿主内容。采样源只包含页面内容，必须与玻璃胶囊互为兄弟节点。玻璃模式的描边仍由 `variant` 控制；镜片抬起后采用底栏同款圆润轮廓，`indicatorRadius` 控制静止指示器。

页面进度绑定见[触控优先级](TouchPriority.md)的完整示例。

## 兼容

CMS.Controls.SegmentedPicker、CMSSegmentedControl 和旧 CMSCapsuleTabs 共用同一底层实现。页面导航优先使用 CMS.Navigation.CapsuleNavigation；互斥的控件值仍可使用 SegmentedPicker。胶囊标签在这里是单选导航，不表示多个独立标签的多选筛选。

## 状态与参数契约

| 参数组 | 默认值 / 契约 |
| --- | --- |
| labels / selectedIndex / onSelectedChange | 必填；宿主持有最终选择 |
| variant / enabled | Filled / true |
| height / outerRadius / indicatorRadius | 44dp / 22dp / 18dp |
| contentInset / borderWidth | 4dp / 1.5dp |
| fontSize / selectedColor / unselectedColor | 14sp / PrimaryText / SecondaryText |
| containerColor / indicatorColor | Color.Unspecified，按主题解析 |
| pageProgress | null；指定时跟随真实连续页位置 |
| pageTouchVersion | 0；内容每次按下时递增，打断交接或镜片落地 |
| draggable / liquidGlass / liquidMotion | true / false / true；拖动、玻璃材质与玻璃形变 |
| samplingSource / hazeState | null；复用现有玻璃采样与毛玻璃状态 |

| 操作 | 宿主责任 |
| --- | --- |
| 点击 | 更新选中值，或调用 PagerState.animateScrollToPage |
| 胶囊拖动 | 松手回调落点选项；绑定 Pager 时宿主负责发起翻页 |
| 手势翻页 | selectedIndex 读取 PagerState，pageProgress 同时读取连续位置 |
| 程序跳页 | 保持同一 PagerState，不另建选中状态副本 |
| 动态删减项 | 同步页面数和业务选择，组件限界不能代替数据修正 |
| 禁用 | enabled = false；选中内容仍由宿主显示 |

每项提供 Tab 语义和选中状态。检查大字体、RTL、连续翻页中再点选，以及一轮动画中重新按下。相关：[Page / Pager](Page.md)、[触控优先级](TouchPriority.md)、[标签导航](TabNavigation.md)。
