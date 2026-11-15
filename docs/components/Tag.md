# 标签 · Tag

`CMS.Content.Tag` 显示短状态或分类文案，例如“本地”“已同步”。它是展示组件，页面内分类切换使用 [胶囊导航](CapsuleNavigation.md)。

## 参数与样式

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| text | 必填 | 建议使用简短、完整的状态文字 |
| style | CMSTagStyle.Outline | Outline 描边；Filled 半透明填充 |
| tint | AccentBlue | 文字及背景的基础颜色 |
| fontSize | 11sp | Medium 字重 |
| cornerRadius | 6dp | 标签圆角 |
| horizontalPadding / verticalPadding | 7dp / 3dp | 内边距 |
| modifier | Modifier | 外部宽度与布局 |

| 变体 | 实际绘制 |
| --- | --- |
| Outline | tint 的 55% 透明度、1dp 描边 |
| Filled | tint 的 16% 透明度背景 |
| 两者共用 | tint 文字，单行，超出可用宽度显示省略号 |

```kotlin
CMS.Layout.HStack(spacing = 8.dp) {
    CMS.Content.Text("离线资料")
    CMS.Content.Tag("本地", style = CMSTagStyle.Filled)
}
```

## 状态与接入

状态由宿主持有，更新 `text` 即更新显示。组件不提供选中态、关闭按钮或点击回调；不要把展示标签当作已经具备完整操作语义的筛选控件。标签需要可操作时，应使用导航组件或为自定义控件提供点击和语义。

颜色只能辅助表达状态，必须保留“成功”“失败”等可朗读文案。长说明放在正文或列表说明中，避免为了容纳句子不断压缩字号。

相关组件：[文本](Text.md)、[胶囊导航](CapsuleNavigation.md)、[列表](SettingsRows.md)。
