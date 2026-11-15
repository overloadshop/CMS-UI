# 表格 · Table

CMS.Content.Table 与代码块共用一套外观：圆角可调（0 为直角）、固定 / 最大 / 无限高度、
行分隔线与斑马纹；列宽不足时横向滚动，四边到边有阻尼回弹与轻触。

## 调用参数

| 参数 | 默认值 | 用途 |
| --- | --- | --- |
| columns | 必填 | 表头文字 |
| rows | 必填 | 数据行，每行长度不足时补空 |
| cornerRadius | 14dp | 圆角；0dp 为直角 |
| height | null | 固定高度，内容超出时纵向滚动 |
| maxHeight | null | 最大高度，内容不足时按内容收缩 |
| minColumnWidth | 120dp | 单列最小宽度；放不下时横向滚动 |
| cellPadding | 12dp / 10dp | 单元格内边距 |
| fontSize / lineHeight | 13sp / 20sp | 单元格字号与行高 |
| surfaceColor | TertiaryFill | 容器底色 |
| headerColor / textColor | PrimaryText | 表头与正文颜色 |
| dividerColor | SecondaryText 14% | 行分隔线 |
| showDividers | true | 显示行分隔线 |
| striped | true | 斑马纹 |
| alignments | 空 | 每列对齐：Start / Center / End |
| edgeFeedback | true | 触边时回弹并触发一次轻触 |

## 与代码块一致的规则

- 两者都没有外描边，只靠底色与圆角区分层级。
- 圆角统一由 `cornerRadius` 控制，`0.dp` 即直角，表格与代码块可分别设置。
- 高度策略一致：不传为无限高度，`maxHeight` 为上限，`height` 为固定值。
- 超出宽度后横向滚动，触边回弹并触发轻触；纵向超出时同理。
- 使用 CMS 统一触控优先级，横向边缘不会抢占父级切页；换轴需要明确方向，横滑中的纵向抖动不会单独启动页面滚动。
- 有父级 Page / ScrollView 时，纵向先滚表格，到顶底后将剩余运动交给页面；拖动交接后保持页面接管到抬手。没有父级时才由表格自身处理上下回弹。
- 所有行按表头列数布局，缺少的单元格补空，多出的单元格不增加未计入滚动范围的隐藏宽度。

```kotlin
CMS.Content.Table(
    columns = listOf("参数", "类型", "说明"),
    rows = listOf(
        listOf("cornerRadius", "Dp", "0.dp 为直角"),
        listOf("maxHeight", "Dp?", "null 为无限高度")
    ),
    cornerRadius = 14.dp,
    minColumnWidth = 148.dp,
    striped = true,
    edgeFeedback = true
)
```

## 状态与组合

| 情况 | 行为 |
| --- | --- |
| 列数多 | 每列不小于 `minColumnWidth`，整体横向滚动 |
| 行数多 | 设置 `maxHeight` 后纵向滚动 |
| 深浅色 | 分隔线与斑马纹自动换算 |
| 与代码块同页 | 圆角、分隔线、触边手感保持一致 |

相关：[代码块](CodeBlock.md)、[滚动视图](Scroll.md)、[文本](Text.md)。
