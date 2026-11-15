# 文本 · Text

`CMS.Content.Text` 统一字体、字号阶梯、字重与溢出规则。标题、正文和说明优先使用主题文字色，避免在浅色主题中固定使用白色。

## 参数契约

| 参数 | 类型 / 默认值 | 用途 |
| --- | --- | --- |
| text | String，必填 | 显示内容 |
| color | Color / PrimaryText | 文字颜色 |
| fontSize / fontWeight | 15sp / Normal | Auto 预设下的字号与字重 |
| preset | CMSTextPreset.Auto | Caption 为 12sp，Body 为 15sp，Title 为 20sp；预设覆盖 fontSize |
| respectFontScale | Boolean / true | 跟随系统字体缩放 |
| maxLines / overflow | Int.MAX_VALUE / Clip | 行数上限与截断方式 |
| textAlign | TextAlign.Start | 按布局方向对齐 |
| lineHeight / letterSpacing | TextUnit.Unspecified | 继承排版默认值，可显式指定 |
| modifier | Modifier | 宽度、留白与语义 |

## 推荐用法

```kotlin
CMS.Layout.VStack(spacing = 8.dp) {
    CMS.Content.Text("账户", preset = CMSTextPreset.Title)
    CMS.Content.Text(
        "显示名称可以随时修改",
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    CMS.Content.Label("修改后会同步到本机资料")
}
```

示例片段在 Compose 环境中使用，需导入 `com.lindicra.cms.*`、`androidx.compose.ui.unit.dp` 和 `androidx.compose.ui.text.style.TextOverflow`。

## 排版与边界

| 场景 | 建议 |
| --- | --- |
| 正文 | 保留字体缩放，允许换行 |
| 单行名称 | 同时指定 maxLines = 1 和 Ellipsis，并约束可用宽度 |
| 说明文字 | 使用 Label；该入口默认最多两行 |
| 页面标题 | 使用 Title 预设；需要无障碍标题时由宿主添加 heading 语义 |
| 可复制正文 | 宿主使用 Compose SelectionContainer 包裹 |
| 链接 / 状态 | 分别使用 Link / Tag |

`Text` 自身没有点击或选择行为。只有需要尺寸严格固定的专用排版才考虑关闭 `respectFontScale`，并检查大字体下的可读性。底层 `CMSText` 额外开放 `minLines`、`softWrap` 和 `style`，需要这些能力时可以直接调用。

相关组件：[链接](Link.md)、[标签](Tag.md)、[列表](SettingsRows.md)。
