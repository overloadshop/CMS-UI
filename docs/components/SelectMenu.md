# 弹出菜单 · Popup Menu

CMSPopupMenu 支持选择与操作菜单、二级菜单、长按滑动选择和独立窗口渲染。

## 参数契约

| 参数 | 默认值 | 契约 |
| --- | --- | --- |
| items | 必填 | CMSMenuItem 列表，可带 subItems 与 disabled |
| currentIndex | 必填 | 当前一级选中索引 |
| visible / anchorRect | 必填 | 显示状态与触发项窗口坐标，IntRect 使用 px |
| type | Select | Select 选择值；Actions 执行动作 |
| onSelect / onSelectPath | 必填 / null | 一级索引与完整层级路径 |
| onDismiss / onClosed | 必填 / null | 请求关闭与退场完成 |
| hazeState / samplingSource | 必填 / null | 毛玻璃状态与独立背景源 |
| touchState | null | 连续长按选择由 rememberCMSMenuTouchState 提供 |
| accentColor | 0xFFFF5B68 | 此入口默认固定色，需要时显式传主题色 |
| dimBackground / dimAmount | false / 0.24f | 遮罩开关与强度 |

```kotlin
CMS.Controls.Menu(
    hazeState = haze,
    items = listOf(CMSMenuItem("选项 A"), CMSMenuItem("选项 B")),
    currentIndex = selected,
    anchorRect = anchor,
    visible = visible,
    accentColor = Color(0xFF5F9BFF),
    dimBackground = false,
    onSelect = { selected = it; visible = false },
    onDismiss = { visible = false }
)
```

## 连续手势

通过 CMSSettingsMenuRow 或 cmsMenuAnchor 打开菜单。长按后无需抬手即可选择，松手确认。二级路径如 [2, 0] 表示第三个父项的第一个子项。菜单触发点在屏幕下部时优先向上显示。

## 状态与定位

| 操作 | 接入要求 |
| --- | --- |
| 点击打开 | 先更新 anchorRect，再设置 visible |
| 长按滑选 | 触发端与菜单共用 touchState，保留同一次触摸 |
| 选择 | 根据索引或路径更新业务状态，再请求关闭 |
| 取消 / 外部点击 | 关闭菜单，保留原选中值 |
| 动态删除选项 | 宿主同步修正选中值，避免索引指向另一项 |

`anchorRect` 是窗口坐标，不可直接使用布局局部坐标或 dp。菜单需要有效布局锚点，不应在触发项尚未测量时绑定空矩形。项顺序变化时，业务数据使用稳定 id，再映射为显示索引。

禁用项应保留可理解的名称；复杂动作的解释放在页面中。菜单适合短选项，长表单使用 Sheet。采样源不能包含菜单自身。

相关：[列表菜单行](SettingsRows.md)、[底部面板](HalfModal.md)、[采样源](ContentSource.md)。
