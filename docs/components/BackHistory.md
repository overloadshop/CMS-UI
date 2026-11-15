# 返回历史 · Back History

返回历史属于交互体验，正式入口为 `CMS.Interaction.BackHistory`。长按返回按钮可查看已访问的页面，滑动选择后直接回到目标页；短按仍返回上一页。返回按钮自身保留在导航分类。

## 作用域与宿主契约

| 接口 | 含义 |
| --- | --- |
| `CMS.Interaction.BackHistory(history) { ... }` | 为子树提供历史接口；传 null 可关闭该作用域的历史 |
| `previous(context)` | 仅返回当前栈内可返回的页面，最近的在前，不含当前页 |
| `returnTo(context, id)` | 回到指定页面，关闭其上方页面；跳转后更新真实栈 |
| `updateTitle(context, title)` | 同步当前页标题，历史菜单使用最新名称 |
| `CMSBackDestination(id, label)` | 页面实例的稳定标识与显示标题 |

作用域不会创建导航栈。宿主负责保存实际页面、更新标题和执行返回。嵌套 `CMS.Appearance.Theme` 默认继承外层历史，也可通过 `backHistory` 显式覆盖。未提供接口或没有历史时，长按不显示菜单。

## 接入实际导航

1. 为每次进入的页面保存独立稳定 id；重复打开同一种页面也应能区分。
2. `previous` 从真实栈生成列表，排除当前页、已关闭的页面和未访问的页面。
3. `returnTo` 按 id 截断栈；系统返回和按钮短按执行同一个弹栈操作。
4. 在页面外壳更新标题，并将历史作用域包在主题外层。

```kotlin
val context = LocalContext.current
CMS.Interaction.BackHistory(appHistory) {
    CMS.Appearance.Theme {
        SideEffect { appHistory.updateTitle(context, title) }
        CMS.Navigation.SecondaryTopBar(
            title = title,
            hazeState = haze,
            onBack = onBack
        )
    }
}
```

`appHistory` 实现上表的宿主契约，可桥接 Activity 栈或应用自己的导航控制器。按钮不根据页面类名猜测目的地。

## 可独立运行的多级案例

下面的四页案例只在进入下一页后添加历史。到第四页后长按返回，可直接选择第一页；返回后再进入下一页会沿当前栈重新前进。

```kotlin
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lindicra.cms.*
import com.lindicra.cms.ui.theme.*
import dev.chrisbanes.haze.HazeState

@Composable
fun BackHistoryExample(onExit: () -> Unit) {
    val haze = remember { HazeState() }
    var depth by remember { mutableIntStateOf(0) }
    val history = remember {
        object : CMSBackHistory {
            override fun previous(context: Context) =
                (depth - 1 downTo 0).map { index ->
                    CMSBackDestination("page/$index", "第 ${index + 1} 页")
                }
            override fun returnTo(context: Context, id: String) {
                val index = id.removePrefix("page/").toIntOrNull()
                if (id.startsWith("page/") && index != null && index in 0 until depth) {
                    depth = index
                }
            }
            override fun updateTitle(context: Context, title: String) {}
        }
    }
    fun back() { if (depth > 0) depth-- else onExit() }
    BackHandler(depth > 0) { back() }
    val inset = with(androidx.compose.ui.platform.LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    CMS.Interaction.BackHistory(history) {
        CMS.Appearance.Theme(hazState = haze) {
            CMS.Content.Page(
                modifier = Modifier.fillMaxSize(), topInset = inset,
                topBarReserve = CM_SECONDARY_TOP_BAR_RESERVE,
                bottomReserve = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 16.dp,
                hazeState = haze,
                topBar = {
                    CMS.Navigation.SecondaryTopBar(
                        title = "第 ${depth + 1} 页", topInset = inset,
                        hazeState = haze, onBack = ::back)
                }
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    CMS.Content.Text("长按左上角返回按钮，选择已访问的页面。")
                    if (depth < 3) CMS.Content.ListRow("进入下一页") { depth++ }
                    else CMS.Content.Text("已到第四页，可跨级返回。")
                }
            }
        }
    }
}
```

## 历史恢复与跨进程接入

页面重建时应恢复稳定 id 与标题；需要进程重启后恢复历史时，由宿主从持久化导航状态重建实际栈。仅保存进程内列表无法恢复已被系统回收的页面。

跨进程页面需要由宿主传递可返回的稳定 id 与标题，并将 `returnTo` 转交给拥有导航栈的进程。目标进程执行返回后同步历史，清理目标页上方的页面及其关联资源。

## 操作检查

| 操作 | 预期 |
| --- | --- |
| 短按 / 系统返回 | 弹出一层；根页行为由宿主决定 |
| 长按后选择较早页面 | 回到该页，移除其上方页面 |
| 长按后取消 | 保留当前页和完整栈 |
| 进入同一种页面两次 | 按不同页面 id 区分 |
| 修改标题 / 旋转页面 | 标题同步，当前 id 保持稳定 |
| 跨进程页面跨级返回 | 导航栈所属进程回到目标页，并清理中间页面资源 |

相关文档：[返回按钮](BackButton.md)、[交互教程索引](InteractionGuide.md)、[宿主接口](../04-触感与宿主接口.md)。
