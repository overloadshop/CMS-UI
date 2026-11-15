# 滚动视图 · Scroll View

CMSScrollView 统一垂直滚动、越界弹簧、拖动定位与玻璃索引提示。

## 概述与推荐接入

用于工具列表、设置项和可浏览的纵向内容。推荐入口是 `CMS.Content.ScrollView`；需要固定顶栏和统一避让时使用 [Page](Page.md)。ScrollView 本身不创建业务标题，也不持久化滚动状态。

| 参数 | 类型 / 默认 | 使用约定 |
| --- | --- | --- |
| `scrollState` | `ScrollState` / `rememberScrollState()` | 外部持有可恢复或程序化定位；位置单位 px |
| `scrollEnabled` | `Boolean` / true | 关闭滚动与方向桥注册 |
| `axisDragBridge` | 可空桥 / 自动继承 CMS Pager | 每页只有一个主滚动宿主；额外独立区域显式传 null |
| `lazyGridState` | `LazyGridState?` / null | 非空时由该惰性网格承担滚动，content 应包含使用同一 state 的网格 |
| `springOverscroll` | `Boolean` / false | 到边缘后用弹簧消耗剩余位移和速度 |
| `showScrollbar` | `Boolean` / false | 是否绘制索引轨道 |
| `onUserScroll` | `(Float) -> Unit` | 用户拖动位移，不是内容绝对位置 |
| `onScrollRelease` | `(Float) -> Unit` | 释放时纵向速度，px/s |
| `onOverscrollChanged` | `(Float) -> Unit` | 边缘偏移，px；可用于联动外部装饰 |
| `sampleGlassContent` | `Boolean` / 根据宿主 | 独立视图采样，CMS Pager 内默认复用统一源 |

```kotlin
val pager = rememberPagerState { 2 }
CMS.Content.Pager(state = pager) { page ->
    CMS.Content.ScrollView(springOverscroll = true) {
        CMS.Content.Section {
            repeat(30) { CMS.Content.ListRow("第 ${page + 1} 页 · 内容 $it",
                showChevron = false) {} }
        }
    }
}
```

## 交互与状态

按下时停止滚动惯性和边缘回弹，但不因此取消普通点击。拖动到边界后使用剩余位移拉伸，反向拖动先收回越界再滚动内容；松手速度由滚动与边缘分别消耗。CMS Pager 接管横向后仍可转回本页纵向滚动，抬手再按下会建立新方向会话。

索引偏移以内容坐标记录，在 px 与 dp 之间只转换一次。索引列表改变后重新记录布局；不要用上一次布局的位置直接定位新列表。大数据量使用惰性网格或自行适配惰性列表，普通 Column 会组成全部内容。

宽表格、长代码使用 `CMS.Content.HorizontalScrollView`，或在已有布局上使用 `Modifier.cmsHorizontalScrollTouchPriority(state)`，无需另加 horizontalScroll。局部视口拥有本次触控，横向滚自身内容，纵向通过本 ScrollView 的桥连续送达；支持按住换向和抬手后重新判断。内容未溢出时不预留横向区域，横向到边缘时也不会突然交给外层 Pager。

普通 Column 的触控滚动只有 CMS 一套识别器；Foundation 仅负责布局和裁切，滚轮与无障碍保留独立路径。LazyGrid 保留自身原生滚动，局部横滑交接给 Grid 时由桥独占拖动。

新拖动等待旧惯性中断完成。滚动桥在自身惯性结束前保留触控代次，旧触控的取消与松手不能释放新触控的回弹。重新按下时，旧边界回弹越过零点的视觉余量会归零，避免出现相反边界的虚假阻力。

回弹中反向快速滑动并松手时，惯性先收回当前拉伸，同一帧剩余位移继续滚动内容；只有真正到达边界后的向外速度交给回弹。局部表格、代码块按窗口坐标采样方向和速度，页面滚动与回弹不会混入手指运动。

默认 `CMSTouchPriority(glassDragPriority = true)`：开启 `exclusiveTouchGesture` 的原生玻璃保留完整拖拽拉伸，ScrollView 从按下起让出该区域。关闭玻璃优先后，独立 ScrollView 在 Initial 阶段接管玻璃上的滑动；Pager 内由 Pager 统一协调。玻璃拖拽的抬手速度不触发页面惯性。交互滚动条保留实际轨道区域，静止长按后已消费的文字选择或菜单交给子控件。完整代码与专用控件边界见 [触控优先级](TouchPriority.md)。

## 调用参数

- scrollState：rememberScrollState()。
- springOverscroll：弹簧越界；关闭后拖动和惯性均不越界。
- showScrollbar：显示滚动条。
- scrollbarStyle：CMSScrollbarStyle.Classic / Alphabet / Sections。
- scrollbarIndexLayout：Distributed 分散；TopPacked 顶部聚集。
- scrollbarInteractive：允许拖动定位。
- scrollbarPressThickening：按下加粗。
- scrollbarSections：CMSScrollSection(label, scrollOffset) 列表，scrollOffset 为内容相对像素位置。
- scrollbarInsets：滚动条避让范围。
- sampleGlassContent：独立绑定内容采样，默认开启。

```kotlin
CMSScrollView(
    modifier = Modifier.height(360.dp),
    springOverscroll = true,
    showScrollbar = true,
    scrollbarStyle = CMSScrollbarStyle.Alphabet,
    scrollbarIndexLayout = CMSScrollbarIndexLayout.TopPacked,
    scrollbarInteractive = true,
    scrollbarPressThickening = true,
    scrollbarSections = sections
) { Content() }
```

## 分组定位

布局完成后通过 onGloballyPositioned 记录每个分组相对共同内容祖先的位置。不要记录窗口坐标，否则滚动时分组偏移会变化。末尾分组按列表底部对齐。

顶栏避让应在内容首项加入 Spacer，不给滚动容器添加顶部 Padding。内部滚动与页面滚动使用各自容器，轮播通过方向桥接分配手势。

若分组标题需停在顶栏下方，定位偏移扣除顶部留位像素；`scrollbarInsets` 同步预留顶栏和底部安全区域。首字母与分段索引提示使用启用硬件加速的独立窗口，在进入弹窗前绑定完整下层页面，包含滚动容器外的背景，排除提示玻璃自身。

## 索引参数与验收

| 参数 | 默认值 | 用途 |
| --- | --- | --- |
| scrollbarStyle | Classic | Classic、Alphabet、Sections |
| scrollbarInteractive / scrollbarPressThickening | true / true | 拖动定位与按压增粗 |
| scrollbarSections | emptyList | 分组名称与内容相对像素偏移 |
| scrollbarInsets | 四边 8dp | 轨道安全范围 |
| scrollbarIndexLayout | TopPacked | 可选 Distributed |
| onScrollPress | 返回 false | 按下回调，可用于宿主联动 |

| 场景 | 检查点 |
| --- | --- |
| 滚动中再按下 | 惯性与越界回弹停止，普通点击仍可判断 |
| 横滑后直接纵滑 | CMS Pager 交接给触到的主滚动页 |
| 拖动索引 | 使用本次布局记录的内容偏移 |
| 首末分组 | 可以定位，末尾不超出内容边界 |
| 大字体 / 内容变化 | 重新记录分组偏移，避免沿用旧高度 |

顶栏显示状态变化时，内容首项、索引轨道及分组定位应同步调整。滚动条不应成为唯一的浏览方式，内容仍要可以正常上下滚动。

相关：[Page](Page.md)、[触控优先级](TouchPriority.md)、[列表](SettingsRows.md)。
