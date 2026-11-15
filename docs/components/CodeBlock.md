# 代码块 · Code Block

CMS.Content.CodeBlock 是用于展示代码的容器：圆角可调（0 为直角）、
固定 / 最大 / 无限高度、Kotlin 语法着色、头行语言与复制按钮，四边到边有阻尼回弹与轻触。

## 调用参数

| 参数 | 默认值 | 用途 |
| --- | --- | --- |
| code | 必填 | 代码文本 |
| language | kotlin | 头行语言标签；传 null 隐藏标签 |
| cornerRadius | 14dp | 圆角；0dp 为直角 |
| height | null | 固定高度，内容超出时纵向滚动 |
| maxHeight | null | 最大高度，内容不足时按内容收缩 |
| wrap | false | true 自动换行；false 横向滚动 |
| showHeader | true | 显示头行（语言 + 复制） |
| copyable | true | 头行是否提供复制 |
| highlight | true | 是否按 Kotlin 语法着色 |
| fontSize / lineHeight | 12sp / 19sp | 代码字号与行高 |
| contentPadding | 14dp / 12dp | 代码区内边距 |
| surfaceColor | TertiaryFill | 容器底色 |
| dividerColor | SecondaryText 16% | 头行与代码之间的细分隔线 |
| edgeFeedback | true | 触边时回弹并触发一次轻触 |

## 高度与滚动

- 不传 `height` / `maxHeight`：按代码内容撑开，无限高度。
- 只传 `maxHeight`：内容不足时收缩，超出后纵向滚动。
- 传 `height`：固定高度，内容超出后纵向滚动。
- `wrap = false` 时代码不换行，超出宽度横向滚动，左右到边回弹；
  `wrap = true` 时自动换行，不再横向滚动。
- 拖动使用 CMS 触控优先级；横向触边不会转为父级切页。换轴由同一套方向判断确认，横向拖动中的小幅纵向抖动不会单独启动页面滚动。
- 有父级 Page / ScrollView 时，纵向先滚代码，到顶底后将剩余拖动交给页面，并保持页面接管到抬手；局部惯性到边只传递剩余速度。无父级时使用代码区自身的上下回弹。
- 重新按下会停止旧惯性和回弹；反向惯性先收回拉伸，再继续滚动内容。文字选择的长按与复制按钮保留独立操作。

## 着色

关键字、字符串、数字、类型、注解、注释各有独立颜色，深浅色两套取值；
`cmsKotlinTokens` 与 `cmsCodeTokenColor` 对外开放，宿主可复用同一套着色规则。

```kotlin
CMS.Content.CodeBlock(
    code = sample,
    cornerRadius = 14.dp,
    maxHeight = 220.dp,
    wrap = false,
    highlight = true,
    edgeFeedback = true
)
```

## 状态与组合

| 情况 | 行为 |
| --- | --- |
| 复制 | 头行右侧「复制」，1.6 秒内显示「已复制」 |
| 触边 | 左右（或上下）到边后阻尼回弹，并触发一次轻触 |
| 深色 | 着色与底色自动切换 |
| 文档内 | 表格与代码块共用同一套圆角与分隔线规则 |

相关：[表格](Table.md)、[滚动视图](Scroll.md)、[文本](Text.md)。
