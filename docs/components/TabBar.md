# 悬浮底栏 · Tab Bar

`CMS.Navigation.TabView`（实现为 `CMSGlassTabBar`）是液态玻璃 Tab 栏，支持 2–5 项、底部/顶部两种位置、
贴边/悬浮/侧边三种形态，可作底栏、顶栏或 iPad 式侧栏。

Tab 栏、胶囊导航和标签导航都属于 Tabbar 家族，分别提供玻璃、胶囊与下划线形态。三个入口共用受控选择契约：宿主持有 `selectedIndex`，通过 `onSelectedChange` 切换内容；导航本身不创建页面或路由栈。

| 形态 | 调用入口 | 实现与适用场景 |
| --- | --- | --- |
| 玻璃 Tab 栏 | `CMS.Navigation.TabView(items, selectedIndex, onSelectedChange)` | 原生玻璃采样与 Compose 图文；可作顶栏、底栏、侧栏 |
| 胶囊 Tabbar | `CMS.Navigation.CapsuleNavigation(labels, selectedIndex, onSelectedChange)` | 底板 / 描边或纯文字液态玻璃；支持拖拽，2～4 项页内切换 |
| 下划线 Tabbar | `CMS.Navigation.TabNavigation(labels, selectedIndex, onSelectedChange)` | Compose 自绘文字与下划线；固定数量的页内分类 |

胶囊与下划线形态保留 Tab 语义、触觉和自定义选中反馈，不使用默认涟漪。三个横向形态都通过 `pageProgress` 绑定 Pager 连续位置，同时使用同一个 Pager 的 `selectedIndex` 与选择回调。`CMSTabPager` 是额外的「导航 + 分页」组合：2～4 项采用胶囊，其余采用下划线。

## 调用

```kotlin
var selected by remember { mutableIntStateOf(0) }
var source by remember { mutableStateOf<ViewGroup?>(null) }
Box(Modifier.fillMaxSize()) {
    CMS.Content.Source(Modifier.fillMaxSize(), onSourceReady = { source = it }) {
        PageContent(selected)          // 内容末尾为底栏预留高度
    }
    CMS.Navigation.TabView(
        items = CMSGlassTabBarDefaults.items.take(4),
        selectedIndex = selected,
        onSelectedChange = { selected = it },
        samplingSource = source,
        modifier = Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
    )
}
```

采样源只装页面内容，玻璃控件必须放在它外面、且与它互为兄弟节点。

## 参数

| 参数 | 默认 | 用途 |
| --- | --- | --- |
| `items` | — | 2–5 项，`CMSGlassTabItem(label, icon)`；省略 icon 可显示纯文字 |
| `selectedIndex` / `onSelectedChange` | — | 受控选中 |
| `pageProgress` | null | 内容滑页时直接跟随真实页位置；底栏点击和拖动期间由镜片动画控制 |
| `pageTouchVersion` | 0 | 内容每次按下时递增，立即打断镜片落地并恢复页面跟随 |
| `height` | 52dp | 外胶囊高度，≥ 44dp |
| `indicatorInset` | 4dp | 四边等距内间距 |
| `pressExpansion` | 20dp | 按下后镜片扩张量 |
| `iconSize` / `labelSize` | 24dp / 12sp | 图标与文字 |
| `showLabels` | true | 是否显示文字 |
| `selectedColor` / `unselectedColor` | 主题色 / 次级文字 | 前景 |
| `indicatorColor` | 深色 `0xB31C1C1E`、浅色 `0x388E8E93` | 静止指示标 |
| `liquidMotion` | true | 液面物理（形变） |
| `enabled` | true | 触摸与无障碍 |
| `samplingSource` / `hazeState` | 自动解析 | 页面内容与毛玻璃状态 |
| `fillWidth` | false | false = 按 `itemWidth` 自适应；true = 铺满 |
| `itemWidth` | 88dp | 每格基准宽（自适应时生效） |

`CMS.Navigation.TabView` 另有 `placement`（Bottom/Top）与 `form`（Auto/Edge/Floating/Rail），默认取全局
`CMSSettings`，见 [统一入口](../06-统一入口与全局配置.md)。

## 视觉与采样

镜片是**薄透镜**，不是一块带色玻璃：

- 镜体（内部）**完全透明**，不输出像素，人眼直接看到真实屏幕，因此不存在重影或叠色；
- 只有**边缘一圈**绘制折射与高光，透明度向内渐隐，与屏幕无缝衔接；
- 折射带宽度 = 短边 × 0.16，位移上限 = 带宽 × 0.8，上下方向权重高于左右；
- 边缘有 RGB 分离（色散），强度随弯曲量走，只在真正折射的带上出现；
- 白高光只出现在上下肩部，左右侧不亮；
- 模糊随抬起量渐入（默认 0.5dp），静止时为 0，边缘保持锐利。

采样链：页面 → 胶囊玻璃 → 图标文字。镜片把三者按屏幕坐标合成后只取边缘带，
所以它弯折的就是眼睛看到的那一层；胶囊与图标必须作为采样源传入，否则边缘会“看不到底下的东西”。

## 交互

- **文字与前景**：颜色、深浅和常规 / 选中字重的字形混合比例跟随镜片当前连续位置，避免跟着离散选中索引突然切换；图标颜色使用同样的位置渐变。
- **按下**：镜片浮起并滑到**手指位置**（不是格子中心）；文字/图标保持在真实层，由 zIndex 决定在镜片之上或之下。
- **整体反馈**：底栏玻璃、镜片与文字一起放大至约 1.015 倍，松手通过轻阻尼弹簧回到原尺寸。
  缩放沿用普通玻璃的刚度 180、阻尼 0.35，并保留速度与高精度的小幅回弹。
  胶囊复用原生玻璃的白色触摸辉光，强度 0.12、半径 96dp，跟随当前二维触点，松手渐退；辉光位于文字下层。
  外部触点仅驱动光效，避免原生玻璃再次缩放或位移。
- **拖动**：镜片用阻尼弹簧跟随手指（`stiffness 180`、`damping 0.9`），会略微拖后；
  底栏胶囊与图标随手指位移做低通平滑的轻微平移（上限约 ±5dp），不会瞬移。
- **形变**：速度驱动**压缩**（沿运动方向压窄、垂直方向变高），加速度驱动弯曲；两者都经过低通滤波，
  轻拖几乎不变形，重甩才明显，不会抽搐。
- **松手**：按落点提交页面；有抛出速度时先让镜片滑完再落下，下落初速与速度相关，落地有回弹。
- **落地力度**：抬起轴保留当前速度，并承接部分松手动能；接近落点时，下落速度驱动镜片横向展开、纵向压扁。
  流体速度在玻璃渐退后继续耗散，灰色指示标也承接压扁和恢复，避免落下时轮廓突然变硬。
- **点击**：按下时镜片滑到手指，抬起后先飞到目标格再落下，不会“原地变色”。

相邻项默认重叠 `itemOverlap = 10.dp`，图标间距、指示标宽度和触摸落点共用同一几何。
重叠范围由相邻中心的中点划分归属，单次松手仍只提交一个页面；设为 `0.dp` 可恢复无重叠布局。

纯文字液态玻璃胶囊与图文底栏共用上述起拖规则：按在任意选项位置，镜片都会平滑跟随触点。点击或拖动底栏时，镜片独立完成向目标选项的移动和落地，期间不跟随页面翻页进度。落地后平滑交还页面位置；若内容区重新按下，`pageTouchVersion` 的变化立即打断落地等待，指示器从该帧起同步连续页位置。

## 形态

| form | 说明 |
| --- | --- |
| `Auto` | 默认：宽度 ≥ 600dp 用侧栏，否则贴边底栏 |
| `Edge` | 贴边胶囊，宽度按图标数量自适应 |
| `Floating` | 左右 20dp 留白 |
| `Rail` | 竖向侧栏，图标在上文字在下，滑动指示器（暂未接液面物理） |

## 无障碍

每一项都是可选择的 `Role.Tab`，提供 `contentDescription` 与选中状态；
`enabled = false` 时整体禁用点击但保留朗读。

## 已知边界

- 采样源不能包含 Tab 栏自身（自采样会递归崩溃）；
- 镜片按 1:1 屏幕像素采样，视口固定，不随形变缩放；
- 顶部形态只负责摆放，状态栏避让由宿主处理，可使用 `safeDrawing` 内边距。

## 页面状态接入

| 状态 | 接入方式 |
| --- | --- |
| 内容不支持手势切页 | 宿主持有 selectedIndex，选择回调切换业务内容 |
| 完整分页页面 | 使用 CMS.Content.Pager，selectedIndex 和 pageProgress 读取同一 PagerState |
| 程序跳页 / 动态删项 | 宿主同步目的页、items 和有效索引 |
| 自定义底栏位置 | 用 Modifier.align / padding；placement 不会在外部 Box 中自动改变对齐 |
| 宽屏 Auto | 切换到 Rail，宿主仍需为内容安排可用空间 |

TabView 不拥有路由栈，也不替页面保存业务数据。固定底栏需要把底栏高度、留白和系统导航栏计入 Page.bottomReserve。通过 Pager.onTouchInterrupt 取消宿主翻页任务，同时递增传给底栏的 `pageTouchVersion`。页面进度本身不能区分程序翻页与内容触摸，需要分别传入这两个信号。

```kotlin
var pageTouchVersion by remember { mutableIntStateOf(0) }
var navigationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
Box(Modifier.fillMaxSize()) {
    CMS.Content.Pager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        onTouchInterrupt = {
            navigationJob?.cancel()
            ++pageTouchVersion
        }
    ) { page -> PageContent(page) }
    CMS.Navigation.TabView(
        items = tabs,
        selectedIndex = pager.currentPage,
        onSelectedChange = { target ->
            navigationJob?.cancel()
            navigationJob = scope.launch { pager.animateScrollToPage(target) }
        },
        pageProgress = pager.currentPage + pager.currentPageOffsetFraction,
        pageTouchVersion = pageTouchVersion,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        samplingSource = source,
        hazeState = haze
    )
}
```

`pager`、`scope`、`tabs` 和内容采样源由宿主持有，`PageContent` 表示页面内容。从内容区域左右滑动页面时，指示器直接跟随连续页位置，形变由采样速度驱动。点击或拖动底栏时，镜片由底栏自身的弹簧控制，回调只通知宿主翻页，页面进度不抢占镜片的移动和落地动画；落地后恢复页面同步。内容重新按下可立即接管，旧落地任务不会再恢复控制权。未绑定 `pageProgress` 时使用底栏自身的弹簧。侧栏仍按离散选择切换。

相关：[Page / Pager](Page.md)、[触控优先级](TouchPriority.md)、[胶囊导航](CapsuleNavigation.md)。
