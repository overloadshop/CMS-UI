# 分组容器 · Group

`CMS.Layout.Group` 是没有背景的纵向分组，默认保留 8dp 间距。它不绘制卡片、边框、分割线或组标题。

默认尝试填满父布局可用宽度；需要按内容收缩的纵向容器时使用 VStack。

| 参数 | 默认值 | 作用 |
| --- | --- | --- |
| spacing | 8dp | 子项纵向间距 |
| horizontalAlignment | Alignment.Start | 水平对齐 |
| minWidth / maxWidth | Dp.Unspecified | 可选宽度边界 |
| minHeight / maxHeight | Dp.Unspecified | 可选高度边界 |
| modifier | Modifier | 外部布局 |
| content | ColumnScope | 分组内容 |

```kotlin
CMS.Layout.Group(spacing = 8.dp) {
    CMS.Content.Text("导出格式")
    CMS.Content.Label("文件保存在你选择的位置")
    CMS.Content.Tag("支持离线")
}
```

## 容器选择

| 需求 | 推荐 |
| --- | --- |
| 内容按顺序分组，仅留间距 | Group |
| 定制纵向空间分配 | VStack |
| 卡片内列表，自动分割线 | Section |
| 卡片内列表，手动控制分割线 | ListSection |
| 多个卡片分组之间的间距 | List |

尺寸参数按父布局约束解析。Group 不会把子项合并为一个无障碍节点，也不自动设置标题语义。需要合并朗读时由宿主提供语义，并确认内部操作仍可独立访问。

相关组件：[VStack](VStack.md)、[列表](SettingsRows.md)、[文本](Text.md)。
