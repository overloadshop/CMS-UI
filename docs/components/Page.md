# Page 页面容器

`CMS.Content.Page` 是完整的纵向内容页：负责滚动、玻璃背景采样、固定顶栏、上下避让，以及父级分页的纵向触控接管。页面内容通过 `ColumnScope` 插槽提供。

## 选择容器

| 需求 | 推荐组件 | 原因 |
| --- | --- | --- |
| 带固定标题、上下滚动的详情或工具页 | `CMS.Content.Page` | 内置滚动与顶栏避让 |
| 多个完整页面左右切换 | `CMS.Content.Pager` 内放 `Page` | 自动注册每页主滚动桥，统一方向仲裁与采样 |
| 已有页面结构，只需要一个滚动区域 | `CMS.Content.ScrollView` | 不额外插入顶栏和底部留位 |
| 页面内图片、卡片轮播 | `CMS.Content.PageView` | 提供轮播尺寸、指示器、自动播放和边界弹簧 |

`Page` 不负责业务路由，也不绘制底部导航。页面选择状态和返回行为由应用持有。

## 推荐：分页页面

以下内容放在 `CMS.Appearance.Theme` 内。`Pager` 使用 Compose 的 `PagerState`，页签可以选择胶囊导航、标签导航或应用自己的底栏。

```kotlin
val pager = rememberPagerState { 2 }
val scope = rememberCoroutineScope()
Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
    CMS.Navigation.CapsuleNavigation(
        labels = listOf("计算", "工具"),
        selectedIndex = pager.currentPage,
        onSelectedChange = { scope.launch { pager.animateScrollToPage(it) } },
        pageProgress = pager.currentPage + pager.currentPageOffsetFraction
    )
    CMS.Content.Pager(state = pager, modifier = Modifier.weight(1f)) { page ->
        CMS.Content.Page(
            topInset = 0.dp,
            topBarReserve = 12.dp,
            bottomReserve = 24.dp,
            springOverscroll = true,
            topBar = {}
        ) {
            CMS.Content.Section {
                repeat(24) { index ->
                    CMS.Content.ListRow(
                        title = "${if (page == 0) "计算记录" else "工具"} ${index + 1}",
                        showChevron = false
                    ) {}
                }
            }
        }
    }
}
```

此方式无需手写 `CMSVerticalDragBridge`。Pager 为每个已组成的页面提供独立桥；Page 或单个主 ScrollView 自动注册它。Pager 建立统一采样源，子页默认不再嵌套原生采样容器。

## Pager 参数

| 参数 | 类型 / 默认值 | 契约 |
| --- | --- | --- |
| `state` | `PagerState`，必填 | 使用 `rememberPagerState { pageCount }`；由它读取连续位置和停稳页 |
| `swipeEnabled` | `Boolean` / `true` | 关闭横向手势切页，仍保留动画打断和纵向接管；代码切页仍可用 |
| `touchPriority` | `CMSTouchPriority` / 继承作用域 | 同时传递给内部页；默认 `respectConsumedGestures = false` |
| `hazeState` | `HazeState?` / 继承主题 | 与外部固定玻璃控件共用 |
| `sampleGlassContent` | `Boolean` / `true` | 已有包含 Pager 的 Source 时设为 false，并关闭子页重复采样 |
| `beyondViewportPageCount` | `Int` / `1` | 额外组成的页数；内部限制到合法范围，越大越占内存 |
| `canStart` | `(Offset) -> Boolean` / 返回 true | 坐标是 Pager 视口局部 px；返回 false 让专用拖动区域自行处理 |
| `onTouchInterrupt` | `() -> Unit` / 空回调 | 启用按下打断时调用；可取消宿主独立维护的页签动画任务 |
| `content` | `@Composable (Int) -> Unit` | 每页的内容；一个页面只注册一个主纵向滚动宿主 |

内容从屏幕顶部开始滚动，首项用 Spacer 避让顶栏，因此滚动后能为顶栏提供真实的背景采样。顶栏始终位于滚动内容上层。

## Page 参数

| 参数 | 默认值 | 契约 |
| --- | --- | --- |
| topInset | 必填 Dp | 状态栏高度，宿主读取 WindowInsets.statusBars |
| glassEnabled | true | 页面 Haze 采样开关，顶栏应保持相同配置 |
| topBarReserve | CM_MAIN_TOP_BAR_RESERVE | 顶栏主体和内容间距，不含状态栏 |
| bottomReserve | 0dp | 将系统导航栏与底部浮层一起计入 |
| hazeState | 独立页创建；Pager 内继承 | 与实际采样源共用 |
| maxContentWidth | Dp.Unspecified | 未指定时使用平板布局限宽 |
| axisDragBridge | 自动继承 CMS Pager | 原生适配时显式传一页一个桥 |
| touchPriority | 继承作用域 | 父级 Pager 与 Page 保持同一策略 |
| springOverscroll / showScrollbar | false / false | 边界弹簧与滚动条 |
| sampleGlassContent | 独立页 true；采样中的 CMS Pager 内 false | 避免重复创建原生采样容器 |
| topBar | 必填 BoxScope.(HazeState) | 固定顶栏，参数为页面实际 HazeState；可传空内容 |
| content | 必填 ColumnScope | 滚动内容，不再嵌套同方向 ScrollView |

二级顶栏建议 `topBarReserve = CM_SECONDARY_TOP_BAR_RESERVE + 12.dp`。`hazeState` 省略时，独立 Page 创建自己的状态，CMS Pager 内则共享分页采样状态。

```kotlin
val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
CMS.Content.Page(
    topInset = topInset,
    topBarReserve = CM_SECONDARY_TOP_BAR_RESERVE + 12.dp,
    bottomReserve = bottomInset + 24.dp,
    topBar = { haze ->
        CMS.Navigation.SecondaryTopBar(
            title = "页面", topInset = topInset, hazeState = haze,
            onBack = onBack
        )
    }
) {
    CMS.Content.Section {
        repeat(24) { index ->
            CMS.Content.ListRow("内容 ${index + 1}", showChevron = false) {}
        }
    }
}
```

## 行为与布局边界

| 情况 | 组件行为 | 宿主职责 |
| --- | --- | --- |
| 固定顶栏覆盖内容 | 首项留出 `topInset + topBarReserve`，内容仍能滚到玻璃背后 | 不再给 Page 外层重复添加同一顶部留白 |
| 底部浮层或系统导航栏 | 内容末尾加入 `bottomReserve` | 将系统栏和浮层高度一起计入 |
| 同页放多个纵向滚动区 | 自动桥只能指向一个主滚动区 | 其他区域显式 `axisDragBridge = null`；需要时用底层适配器自行分流 |
| 滑杆、文本选择或地图 | 默认父级允许触控接管 | 使用 `canStart` 排除专用区域，不依赖全局方向权重 |
| 抬手后重新换向 | 新按下创建新方向会话，停止旧动量 | 保持 PagerState 稳定，不按方向重新创建页容器 |

Page 内不要再套同方向 ScrollView。长列表需要惰性布局时，使用 ScrollView 的 `lazyGridState` 接入或自行适配滚动桥；Page 的 Column 内容本身不是惰性列表。

## 无障碍与相关组件

顶栏应给出可理解的标题，点击行提供明确动作名称。内容字号随系统设置变化后应能自然撑高，不把整页或列表行锁死为固定高度。

相关：[滚动视图](Scroll.md)、[触控优先级](TouchPriority.md)、[胶囊导航](CapsuleNavigation.md)、[玻璃采样源](ContentSource.md)。
