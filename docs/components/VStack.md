# 纵向堆栈 · VStack

`CMS.Layout.VStack` 按纵向排列内容，接收 Compose `ColumnScope`。它负责布局，不提供滚动、背景或卡片装饰。

| 参数 | 默认值 | 作用 |
| --- | --- | --- |
| spacing | 0dp | 默认纵向项间距 |
| horizontalAlignment | Alignment.Start | 子项水平对齐 |
| verticalArrangement | Arrangement.spacedBy(spacing) | 自定义纵向分布；显式传入时以此为准 |
| minWidth / maxWidth | Dp.Unspecified | 可选宽度边界 |
| minHeight / maxHeight | Dp.Unspecified | 可选高度边界 |
| modifier | Modifier | 外部尺寸与留白 |
| content | ColumnScope | 支持 weight、align 等作用域修饰符 |

```kotlin
CMS.Layout.VStack(
    spacing = 12.dp,
    modifier = Modifier.fillMaxWidth()
) {
    CMS.Content.Text("资料", preset = CMSTextPreset.Title)
    CMS.Content.Text("按顺序填写以下信息")
}
```

宿主父布局的约束仍然有效，指定最大宽度不会突破父布局。最小与最大尺寸应使用一致且有效的值。`weight` 需要父布局具有可分配的高度；无限高度的滚动内容里不应依赖它撑满视口。

| 需求 | 选择 |
| --- | --- |
| 少量垂直内容 | VStack |
| 需要上下滚动 | ScrollView 或 Page |
| 多个设置分组 | List + Section |
| 只需要分组并默认留出间距 | Group |

相关组件：[横向堆栈](HStack.md)、[分组](Group.md)、[滚动](Scroll.md)。
