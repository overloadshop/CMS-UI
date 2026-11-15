# 轮播视图 · Carousel

CMSCarousel 使用连续物理位置完成翻页、惯性、回弹和指示器变化。横向轮播可与页面纵向滚动连续换向。

方向仲裁统一采用 CMSTouchPrioritySession。通过 CMS.Interaction.TouchPriority 作用域可调横竖权重、
触发距离、最近运动窗口与是否允许换向。完整应用页面优先使用 [CMS Pager + Page](Page.md)，本组件用于页面内轮播。

轮播登记自己的有效视口，父级分页与独立 ScrollView 在按下时让出该区域。轮播内嵌的宽表格或代码使用 HorizontalScrollView 或 cmsHorizontalScrollTouchPriority，最小的局部视口优先；横向轮播与局部横滑均通过父级桥连续交接纵滑。上下左右小抖动按净位移抵消，明确连续换向不必等待旧窗口过期。

## 页面参数

- itemCount：页数；content(page)：每页内容。
- direction：CMSCarouselDirection.Horizontal / Vertical。
- pageHeight：视口高度；pageSpacing：页面间距；clipRadius：连续圆角。
- autoPlay：自动播放；autoPlayInterval：毫秒，最低 500L。
- showArrows：显示切换按钮；arrowSize：按钮大小。
- edgeOverflow：CMSCarouselOverscroll.Spring / None。
- snapBack：松手吸附；freeMode：自由惯性滚动。
- pageWidthFraction：自由模式下每页占比，0.1f～1f。

## 指示器参数

- indicatorStyle：CMSCarouselIndicator.None / DotIndicator / DigitIndicator。
- indicatorVisibleCount：可见圆点数量。
- indicatorSize：普通圆点大小。
- indicatorActiveWidth：选中长度，不能小于普通圆点。
- indicatorBottomInset：横向时距底部，纵向时距右侧。
- indicatorInteractive：允许点按圆点切换。
- indicatorColor：指示器强调色。

```kotlin
CMSCarousel(
    itemCount = 3,
    direction = CMSCarouselDirection.Horizontal,
    pageHeight = 190.dp,
    showArrows = true,
    arrowSize = 36.dp,
    indicatorStyle = CMSCarouselIndicator.DotIndicator,
    indicatorSize = 6.dp,
    indicatorActiveWidth = 14.dp,
    indicatorColor = Color(0xFF5F9BFF)
) { page -> PageContent(page) }
```

## 布局与采样

普通点与选中点的边缘间距统一为 5dp。纵向指示器排列在右侧。切换箭头共用返回按钮的图形并镜像，玻璃与箭头从轮播页内容采样。采样源不包含指示器和按钮。

## 默认参数与模式

| 参数 | 默认值 | 契约 |
| --- | --- | --- |
| itemCount / content | 必填 | 页数与每页内容 |
| direction | Horizontal | 可选 Vertical |
| autoPlay / autoPlayInterval | false / 3000ms | 自动播放最低间隔 500ms |
| pageHeight / pageSpacing / clipRadius | 140dp / 8dp / 12dp | 视口和页面几何 |
| edgeOverflow / snapBack / freeMode | Spring / true / false | 越界、吸附与自由惯性 |
| pageWidthFraction | 1f | 自由模式页面占比 |
| showArrows / arrowSize | false / 28dp | 切换操作 |
| indicatorStyle | DotIndicator | 可选 None / DigitIndicator |
| indicatorVisibleCount / indicatorSize | 5 / 6dp | 可见数量与点尺寸 |
| indicatorActiveWidth / indicatorBottomInset | 14dp / 8dp | 活跃点长度与边距 |
| indicatorInteractive / indicatorColor | true / 主题色 | 点击切页与指示器色 |

PageView 自己管理轮播位置，不接受外部 PagerState 或选中页回调；需要业务受控分页时使用 CMS.Content.Pager。内容页索引只是绘制插槽，不应在重组中触发业务提交。

| 检查场景 | 预期 |
| --- | --- |
| 横向轮播内转纵滑 | 可交接外层主 ScrollView |
| 抬手后重新横滑 | 新方向会话不沿用旧锁定 |
| 首末页快速甩动 | 按 edgeOverflow 和 snapBack 处理边界 |
| 自动播放中操作 | 检查手势与播放是否争抢，阅读类内容优先关闭自动播放 |
| 图片 / 动态数量 | 宿主提供实际资源，避免在 content 中加载阻塞操作 |

图像应提供有意义的描述，切换箭头和指示器需要能完成相同导航。内容资源由宿主提供。

相关：[触控优先级](TouchPriority.md)、[滚动](Scroll.md)、[Page](Page.md)。
