# 触控优先级 · Touch Priority

`CMS.Interaction.TouchPriority` 为子树提供统一的方向仲裁策略，覆盖整页切换、表格与代码的局部横滑，以及玻璃拉伸与页面滚动的优先级选择。默认让独占拖拽的原生玻璃保留完整形变触控；普通内容支持按下停止动画、连续横纵滑动。

## 操作规则

1. down 不消费点击，只在启用 interruptOnDown 时停止当前页面与滚动惯性。
2. 起步累计按下后的位移，慢滑也能达到触发距离；接管后根据最近运动和连续的垂直于当前轴的位移换向，不必等待旧方向窗口过期。上下或左右交替的小抖动按带符号的净位移抵消，重新沿原轴移动会清除换向候选。
3. Pager 与局部横滑按占优方向启动；局部内容的每个轴都要经过方向权重和 touchSlop 判断，避免累计漂移绕过优先级。横向轮播的父级纵向通道保留独立启动规则。
4. 纵向通道启动后保持到抬手，每帧连续送达，不取消、重建通道，也不清空速度采样。局部横滑的横向通道启动后同样持续接收横向分量；Pager 与轮播自身的横向推进仍跟随占优方向。
5. 松手后分页横向由页容器落位，局部横向内容使用自身 ScrollState 惯性；纵向由原有 ScrollView 提供惯性和边界弹簧。
6. 多指、组件销毁、参数改变造成的取消会关闭拖动通道，不提交旧手势结果。

半切页时按实际触到的可见页选取滚动桥，而不是简单把所有纵向事件发给 currentPage。选定桥后，本次触控期间保持同一宿主。

横滑落位使用手指位移与抬手速度共同预测意图：`预测距离 = 手指位移 + 逻辑滚动速度 × 0.20 秒`。门槛为页宽的 6% 与 24dp 中较小的值，包含起步时用于判断方向的位移，无需在 touchSlop 之外再多拖一段。慢滑停顿后抬手按距离决定，短促轻扫由速度补足；明显反向时可撤回。未达到门槛时就近回弹，拖到整页后不会额外多跳一页；最后转为纵滑时只就近落位。RTL 同步转换位移与速度。

这遵循 [Apple HIG 手势](https://developer.apple.com/design/human-interface-guidelines/gestures) 的及时反馈原则，以及 [WWDC Designing Fluid Interfaces](https://developer.apple.com/videos/play/wwdc2018/803/) 关于短而轻的触控、保留动量和随时打断的建议。上述数值是 CMS 的实现选择，HIG 没有规定统一的分页距离或速度门槛。

## 可调算法

| CMSTouchPriority 参数 | 默认值 | 意义 |
| --- | --- | --- |
| horizontalPriority | 1f | 横向判断的相对权重 |
| verticalPriority | 1f | 纵向判断的相对权重 |
| directionRatio | 1.2f | 占优方向必须超过另一方向的比例；允许自然的轻微斜滑 |
| slopMultiplier | 1f | 平台 touchSlop 的倍数 |
| historyMillis | 80L | 最近运动及连续换向候选的有效窗口；起步累计不受该窗口截断 |
| allowAxisChange | true | 同一次触控能否换向 |
| interruptOnDown | true | 按下是否立即停止动画 |
| respectConsumedGestures | false | 默认允许父级仲裁；显式开启后，仲裁前遇到子控件消费会放弃本次手势 |
| glassDragPriority | true | 开启 exclusiveTouchGesture 的原生玻璃优先拖拽拉伸；关闭后由页面仲裁滑动 |

判断使用 `abs(dx) * horizontalPriority` 与 `abs(dy) * verticalPriority` 比较。权重只调整方向竞争，不把低于触发距离的轻微抖动变成拖动。适度提高 verticalPriority 可偏向工具列表；提高 directionRatio 可降低斜滑误判。换向候选达到 touchSlop 且占优时可立即确认；窗口控制旧样本和候选的有效期，不是换向必须等待的延迟。

首次纵向接管会补发判断窗口内超过 touchSlop 的位移。纵向已经启动后不再重复扣除 touchSlop；即使横向暂时占优，实际的纵向位移也会继续滚动，因此确认过两轴后斜滑可以同时移动页面和列表。速度使用连续位置样本，局部横滑抬手时仅最终确认的方向提交惯性，另一轴以零速度结束拖动。`allowAxisChange = false` 仍锁定首次方向，首次横向接管后不会开启纵向通道。

算法由 CMSTouchPrioritySession 实现。CMS.Content.PageView、原生 Pager 适配器、局部横向内容和独立 ScrollView 共用该算法；Page 内部滚动通过 axisDragBridge 接入，touchPriority 默认继承作用域。父级 Pager 与 Page 应保持同一策略。

## 推荐组件库接入

完整页面使用 `CMS.Content.Pager`，内部直接放 `CMS.Content.Page` 或单个 `CMS.Content.ScrollView`。页签与业务路由由应用决定，桥注册、同页滚动接管和统一采样由库处理。

```kotlin
val pager = rememberPagerState { 2 }
val scope = rememberCoroutineScope()
CMS.Interaction.TouchPriority(CMSTouchPriority(respectConsumedGestures = false)) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        CMS.Navigation.CapsuleNavigation(
            listOf("计算", "工具"), pager.currentPage,
            { target -> scope.launch { pager.animateScrollToPage(target) } },
            pageProgress = pager.currentPage + pager.currentPageOffsetFraction
        )
        CMS.Content.Pager(pager, Modifier.weight(1f)) { page ->
            CMS.Content.Page(
                topInset = 0.dp, topBarReserve = 12.dp, bottomReserve = 24.dp,
                springOverscroll = true, topBar = {}
            ) {
                CMS.Content.Section {
                    repeat(24) { index ->
                        CMS.Content.ListRow("第 ${page + 1} 页 · ${index + 1}",
                            showChevron = false) {}
                    }
                }
            }
        }
    }
}
```

将上例放在 `CMS.Appearance.Theme` 内，导入 Compose layout、pager、runtime、Modifier、dp、CMS 与 `kotlinx.coroutines.launch`。上下安全区也可改由 Page 预留，让内容滚到固定顶栏背后。

| 接入方式 | 适用情况 | 维护成本 |
| --- | --- | --- |
| `CMS.Content.Pager` + `Page` | 新建完整分页页面，推荐 | 每页桥与采样由库管理 |
| `CMS.Content.Pager` + `ScrollView` | 已有固定标题或自定义页面外壳 | 一个主 ScrollView 自动继承桥 |
| `CMSTabPager` | 顶部页签和分页放在一起 | 同样使用 CMS Pager；受控 selectedIndex |
| 原生 `HorizontalPager` + Modifier | 保留现有复杂 Pager 结构 | 宿主管理一页一桥、采样边界和特殊触控区域 |
| `CMS.Content.PageView` | 卡片轮播 | 同一方向算法，自动交接父 ScrollView |
| `CMS.Content.HorizontalScrollView` | 表格、长代码、宽内容 | 自动登记局部视口并连接父级纵向桥 |

## 文档表格、代码与局部横滑

在 `Page` 或 `ScrollView` 内用 `CMS.Content.HorizontalScrollView` 包住宽内容。已有 Row、Column 或 Box 可将 `.horizontalScroll(state)` 替换为 `.cmsHorizontalScrollTouchPriority(state)`；新 Modifier 已包含原生 horizontalScroll，不要再叠加一次。

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lindicra.cms.*

@Composable
fun WideDocument() {
    CMS.Appearance.Theme {
        CMS.Interaction.TouchPriority {
            CMS.Content.ScrollView(
                modifier = Modifier.fillMaxSize(),
                springOverscroll = true
            ) {
                CMS.Content.Text("表格横滑，按住转为纵滑可继续浏览页面")
                CMS.Content.HorizontalScrollView(Modifier.fillMaxWidth()) {
                    repeat(6) { column ->
                        Box(Modifier.width(144.dp).padding(12.dp)) {
                            CMS.Content.Text("第 ${column + 1} 列")
                        }
                    }
                }
                val codeScroll = rememberScrollState()
                Box(Modifier.fillMaxWidth().cmsHorizontalScrollTouchPriority(codeScroll)) {
                    androidx.compose.material3.Text(
                        "val message = \"一行超过视口宽度的代码，用来检查局部横滑与页面纵滑的交接\"",
                        softWrap = false
                    )
                }
                repeat(30) { CMS.Content.Text("文档正文 ${it + 1}") }
            }
        }
    }
}
```

| HorizontalScrollView 参数 | 默认值 | 作用 |
| --- | --- | --- |
| modifier | Modifier | 挂在受限宽度的视口，例如 fillMaxWidth |
| scrollState | rememberScrollState() | 局部横向位置；可由宿主持有 |
| scrollEnabled | true | 关闭后不登记、不接管横滑 |
| reverseScrolling | false | 横向位移和惯性统一反向，支持 RTL |
| touchPriority | 继承作用域 | 与父级共用方向参数 |
| content | RowScope | 放置宽表格或代码；其他布局使用 Modifier 接入 |

区域通过窗口坐标登记，跨玻璃采样的嵌套 ComposeView 仍能命中。按下时选择最小的有效视口，并保持到本次抬手；父级 Pager、ScrollView 或轮播让出此次仲裁，由局部内容处理横向、父 ScrollView 的桥处理纵向。滚到横向边缘也不会把同一次触控突然交给 Pager；抬手后在区域外开始新手势可以切页。内容宽度未超过视口时不预留横滑区域，正常交给页面。交互滚动条也登记实际轨道范围，避免页面抢走索引拖动；顶部聚集索引只预留有索引的高度。

默认允许换向时，局部横滑区域在纵向方向确认后开启页面滚动；仅累计纵向漂移达到 touchSlop 不会绕过方向竞争。两轴分别确认后可以并行推进，纵向首次送达只扣一次阈值，随后连续送达；上下抖动仍按带符号的净位移抵消。横向轮播的纵向通道保留独立判断。显式关闭 allowAxisChange 时保留首次方向锁定。

局部横滑和普通 Column ScrollView 的 Foundation 滚动 Modifier 只提供布局与裁切，触控拖动始终由 CMS 仲裁器负责，避免两套识别器争抢 ScrollState。鼠标滚轮与无障碍滚动保留独立输入路径。停止旧惯性的任务完成后才进入新拖动，避免延迟中断取消新通道。LazyGrid 保留原生触控；通过局部区域桥拖动 Grid 时使用 PreventUserInput 排除原生拖动竞争。

横滑、上下左右小抖动、按住连续换向和抬手再换向均使用同一组触控优先级参数。

`CMS.Content.CodeBlock` 与 `CMS.Content.Table` 的双轴容器也使用这一仲裁器和触控区域登记。每个通道只在方向确认后启动，不以单轴累计漂移绕过方向权重；已启动的通道保持连续送达，松手惯性只提交给最终确认的方向。限制高度的组件先滚自身纵向内容，到边后的剩余运动交给父级页面，拖动交接后保持页面接管到抬手；独立使用时才启用组件上下回弹。

## 按下、抬手和再次换向

| 阶段 | 仲裁行为 | 位置与速度 |
| --- | --- | --- |
| 按下内容 | 默认停止上一轮页动画和滚动惯性；不消费点击 | 保留当前位置，创建新的方向会话 |
| 轻微移动 | 未达到 touchSlop 时不接管 | 防止点按抖动切页 |
| 横向占优 | 命中的局部内容或 Pager 获得横向位移 | 从当前位置继续 |
| 按住转纵向 | 暂停横向推进，将纵向位移交给触到的页 | 首次纵向接管补发观察期间的位移，保留连续速度采样 |
| 纵滑中横向占优 | 横向开始推进，已启动的纵向通道继续接收纵向分量 | 不取消纵向拖动，不重复扣除 touchSlop |
| 抬手 | 当前方向决定 Pager 落位，已启动的纵向通道提交纵向速度 | Pager 落位，ScrollView 惯性与越界回弹 |
| 抬手后再按下，改为另一方向 | 打断上一轮动量，重新判断方向 | 不继承上次方向锁定 |

`allowAxisChange` 控制按住期间能否换向，不限制抬手后的新手势。`interruptOnDown` 控制新按下是否立即停止旧动画。

`respectConsumedGestures` 默认关闭：打开后，原生采样容器或子滚动先消费事件时，父级会放弃仲裁。只有明确需要子控件优先时才打开。局部表格、代码和轮播通过登记区域让出父级仲裁；其他专用控件仍需明确边界。

## 原生玻璃与长按边界

`glassDragPriority = true`（默认）与 `exclusiveTouchGesture = true` 配合时，原生玻璃登记实际触控视口，阴影和模糊溢出不占用区域。父级 Pager、ScrollView、局部横滑和轮播从 down 起让出整次触控，玻璃保留拖拽、拉伸、回弹、点击和长按，滑出玻璃范围也不会突然滚动页面。普通非独占按钮保持页面仲裁；不支持原生形变的降级分支不预留玻璃区域。

`glassDragPriority = false` 时，取消该玻璃的原生独占拦截和区域预留。CMS 在 Initial 阶段观察方向，确认滑动后消费拖动、取消玻璃点按，交给页面。未达到阈值的移动仍保留点按反馈。该参数独立于 `respectConsumedGestures`。

```kotlin
CMS.Interaction.TouchPriority(CMSTouchPriority(glassDragPriority = true)) {
    CMS.Content.ScrollView {
        CMS.Controls.GlassButton(
            onClick = {}, width = 164.dp, height = 80.dp,
            exclusiveTouchGesture = true
        ) { color -> CMS.Content.Text("拖拽拉伸玻璃", color = color) }
    }
}
```

滚动桥按触控代次保留所有权，直到其惯性结束；旧通道的取消、结束和惯性回调不能释放新通道。回弹穿过零点后重新按下，会清除属于旧边界的反向视觉余量，避免把它变成相反边界的阻力。玻璃拖拽的抬手速度也不提交为页面滚动速度。

回弹中反向 flick 的速度不会整段交给弹簧：先用惯性位移收回拉伸，再将剩余位移送入内容滚动，触顶与触底对称处理。表格与代码的局部横滑用窗口坐标记录方向和松手速度，避免所在页面的滚动、拉伸与回弹产生虚假运动；横纵通道仍可连续交接。

局部横滑适配器在静止长按后检查子控件消费，已接管的文字选择或原生菜单会保留本次触控。已启动的拖动不会仅因持续时间超过长按阈值而中断。编辑器选区手柄、滑杆、地图等复杂独立拖动区域，仍应在 Pager 的 canStart 中排除，或使用 respectConsumedGestures 并验收原生边界；本能力不自动识别任意控件类型。

## 保留原生 Pager 的接入示例

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lindicra.cms.*
import kotlinx.coroutines.launch

@Composable
fun ToolCalculationInteraction() {
    val pager = rememberPagerState { 2 }
    val bridges = remember { List(2) { CMSVerticalDragBridge() } }
    val scope = rememberCoroutineScope()
    val policy = CMSTouchPriority(verticalPriority = 1.2f)
    CMS.Interaction.TouchPriority(policy) {
        Column(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)) {
            CMS.Navigation.CapsuleNavigation(
                listOf("计算", "工具"),
                pager.currentPage,
                { target -> scope.launch { pager.animateScrollToPage(target) } },
                pageProgress = pager.currentPage + pager.currentPageOffsetFraction
            )
            HorizontalPager(
                pager,
                userScrollEnabled = false,
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f).cmsPagerTouchPriority(
                    pager,
                    verticalBridge = { page -> bridges.getOrNull(page) }
                )
            ) { page ->
                CMS.Content.Page(
                    topInset = 0.dp,
                    topBarReserve = 12.dp,
                    bottomReserve = 24.dp,
                    axisDragBridge = bridges[page],
                    springOverscroll = true,
                    topBar = {}
                ) {
                    CMS.Content.Section {
                        repeat(24) { index ->
                            CMS.Content.ListRow(
                                if (page == 0) "计算记录 ${index + 1}" else "工具 ${index + 1}",
                                "可纵滑浏览；点工具切换计算页"
                            ) {
                                if (page == 1) scope.launch { pager.animateScrollToPage(0) }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

由宿主提供 CMS.Appearance.Theme。上例使用两页和页签，支持左右切页、上下滚动。通过 Page 的上下预留避让固定页签和系统栏；替换列表内容时保留一页一桥的关系。

## 接入边界

原生 HorizontalPager 必须关闭自己的 userScrollEnabled，由 cmsPagerTouchPriority 唯一处理横向拖动；否则两套识别器会争抢。Modifier 应挂在 Pager 视口，玻璃采样跨 AndroidView 边界时挂在包含 Pager 的内容 Compose 树内部。

桥按页面分别 remember。Page 会直接把桥交给内部 ScrollView，不要再嵌套纵向滚动容器。不得让多个 Page 或 ScrollView 同时注册同一个桥，否则后注册者会覆盖前一个。没有纵向滚动的页面返回 null；没有桥时无法在横向接管后交还该滚动宿主。

已经接入的表格、代码和轮播不需要在 Pager 的 canStart 中手动排除。其他文本选择、滑杆、地图等区域有自己的拖动，使用 canStart 排除其局部 px 范围；复杂嵌套不要仅靠方向权重争抢。原生 Pager 适配器与子内容应位于同一个 TouchPriority 作用域，以共享区域登记。独立横滑内容不在 CMS ScrollView 内时仍可横滑，但没有纵向桥可接管父页面。

onTouchInterrupt 可通知宿主停止独立指示器动画。更直接的办法是把指示器 pageProgress 始终绑定页面位置。onAxisChange 可做调试观测，不应在里面重建 Pager 或修改页面数量。

## 验证案例

- 点工具，在动画中按下并纵滑：旧动画停止，内容立即接受纵向拖动。
- 按住横滑后转纵滑、再转横滑：纵向通道只启动一次，之后每帧纵向位移持续送达，横向按占优方向推进。
- 纵滑中夹杂短横滑、斜滑或停顿后换向：列表不因横向判断而断触，不重启边界弹簧；抬手仍有连续的纵向惯性。
- 横滑抬手后，落位过程中重新按下纵滑；纵滑惯性中重新按下横滑：新方向能接管，旧方向速度不参与新一轮提交。
- 两页同时可见时触碰左右不同页面：各自的纵向滚动列表接收事件。
- 单纯点工具行：正常进入计算页，不因 down 被消费而失去点击。
- 在宽表格或代码上横滑：内容移动，整页不切换；按住转为纵滑再转回横滑，保持同一内容区域与连续纵向通道。
- 上下左右交替小抖动不触发换向；连续明确换向在旧方向窗口过期前即可确认。
- 局部内容到达左右边缘、内容缩短到不再溢出、在嵌套视口开始触控：分别保持当前宿主、释放横滑预留、由最小视口接管。
- 默认在独占原生玻璃上拖拽：玻璃拉伸，页面不滚动；从玻璃内滑出仍保持玻璃所有权。关闭 glassDragPriority 后从玻璃上纵滑：页面滚动，拖动取消玻璃点按。
- 顶部和底部回弹尚未结束时重新按下滑动，包括回弹穿过零点的时刻：旧回弹不制造反向阻力或重复撞边。
- 多指加入、销毁页面、实时改参数：旧通道取消，没有残留拖动任务。

以上场景可用于检查宿主接入后的触控行为。
