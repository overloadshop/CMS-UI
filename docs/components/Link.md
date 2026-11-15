# 链接 · Link

`CMS.Content.Link` 将文字点击交给宿主。它不解析网址、不自动启动浏览器，也不负责应用内路由。

## 参数契约

| 参数 | 默认值 | 含义 |
| --- | --- | --- |
| text / onClick | 必填 | 文案及导航操作 |
| color | AccentBlue | 链接文字色 |
| fontSize / fontWeight | 15sp / Normal | 排版 |
| underline | false | 是否绘制下划线 |
| maxLines / overflow | 1 / Ellipsis | 行数和截断 |
| modifier | Modifier | 可用宽度、留白与语义 |

```kotlin
val uriHandler = LocalUriHandler.current
CMS.Content.Link(
    text = "查看隐私政策",
    underline = true,
    onClick = { uriHandler.openUri("https://example.com/privacy") }
)
```

需导入 `androidx.compose.ui.platform.LocalUriHandler` 和 `com.lindicra.cms.CMS`。应用内链接则在 `onClick` 中调用自己的导航方法。

## 行为与可访问性

| 情况 | 宿主责任 |
| --- | --- |
| 点击外部网址 | 决定是否用浏览器打开，并处理无法打开的情况 |
| 应用内跳转 | 管理返回栈和目的页 |
| 暂不可用 | 该入口没有 enabled 参数；可改用说明文本，或使用具有禁用状态的操作组件 |
| 链接过长 | 给出可理解的短名称，保留足够宽度 |
| 触摸区域较小 | 用 modifier 添加合理留白，检查朗读及点击 |

使用“查看使用协议”等能说明目的地的文案。仅显示“这里”会使独立朗读时缺少上下文。正文里有多个行内链接时，需要宿主使用 AnnotatedString 等富文本能力；这个入口负责整段文字的单一点击。

相关组件：[文本](Text.md)、[玻璃按钮](GlassButton.md)。
