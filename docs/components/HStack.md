# 横向堆栈 · HStack

`CMS.Layout.HStack` 按横向排列内容，接收 Compose `RowScope`。适合图标与文字、标签与值等少量并排内容。

| 参数 | 默认值 | 作用 |
| --- | --- | --- |
| spacing | 0dp | 默认横向项间距 |
| verticalAlignment | Alignment.CenterVertically | 子项竖直对齐 |
| horizontalArrangement | Arrangement.spacedBy(spacing) | 自定义水平分布；显式传入时覆盖默认间距策略 |
| minWidth / maxWidth | Dp.Unspecified | 可选宽度边界 |
| minHeight / maxHeight | Dp.Unspecified | 可选高度边界 |
| modifier | Modifier | 外部布局 |
| content | RowScope | 支持 weight 和 align |

```kotlin
CMS.Layout.HStack(
    spacing = 8.dp,
    modifier = Modifier.fillMaxWidth()
) {
    CMS.Content.Text("存储位置", modifier = Modifier.weight(1f))
    CMS.Content.Tag("本机")
}
```

## 布局边界

`weight` 用于在有限宽度内分配剩余空间，可以防止长标题挤掉尾部状态。堆栈不会自动换行，也不会自动添加横向滚动；大量标签需要宿主采用 FlowRow 或专门的筛选布局。

父布局约束优先于堆栈尺寸边界。需要整行按压、列表分割线、前导与尾部槽位时使用 ListRow，避免在 HStack 外再次拼接不一致的点击区域。检查 RTL 和大字体，使用 Start / End 等方向相关对齐值。

相关组件：[纵向堆栈](VStack.md)、[列表行](SettingsRows.md)、[标签](Tag.md)。
