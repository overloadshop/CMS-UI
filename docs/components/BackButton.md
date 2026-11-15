# 返回按钮 · Back Button

CMSBackButton 提供短按返回，以及长按滑动选择历史页面。

## 参数契约

| 参数 | 默认值 | 含义 |
| --- | --- | --- |
| onClick | 必填 | 宿主执行返回 |
| iconColor | null | 使用组件前景色；显式传色可独立覆盖 |
| exclusiveTouchGesture | false | 启用静止按压与拖动的方向判定 |
| modifier | Modifier | 外部布局；触摸区 44dp，内部玻璃 40dp |

```kotlin
CMS.Navigation.BackButton(
    onClick = onBack,
    iconColor = Color(0xFF5F9BFF),
    exclusiveTouchGesture = true
)
```

## 返回历史 · Back History

使用 `CMS.Interaction.BackHistory(history) { ... }` 为页面提供 `CMSBackHistory`，也可在主题的 `backHistory` 参数传入。previous 返回真实可回退的 CMSBackDestination 列表；returnTo 根据 id 跳回对应页面；updateTitle 更新标题。嵌套主题默认继承外层历史。未提供历史时，短按仍有效，长按不显示历史菜单。

长按历史菜单的宿主契约与多级接入示例见 [返回历史](BackHistory.md)。

返回按钮不采样滚动内容，避免顶栏出现透出背景内容的窗口。不要叠加透明点击层。

## 手势与历史状态

| 操作 | 行为 |
| --- | --- |
| 短按 | 调用 onClick，应用决定弹栈或关闭窗口 |
| 长按，有历史提供者 | 展示历史并支持滑动选择 |
| 长按，无历史 | 不展示历史菜单，短按仍有效 |
| 修改页面标题 | 由 backHistory.updateTitle 同步历史显示 |

返回栈由应用管理，按钮不猜测当前 Activity 或导航框架。自定义历史 id 应保持稳定；跳转后让宿主重新提供历史列表。无可返回目的地时应由页面决定是否显示按钮。

检查系统返回与按钮返回是否一致，长按取消是否保留当前页面，RTL、深浅色和大字体下历史条目是否清晰。固定顶栏优先使用 [Page](Page.md) 的顶栏槽位。
