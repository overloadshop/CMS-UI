# 标签导航 · Tab Navigation

这是 [Tabbar 家族](TabBar.md)的下划线形态，入口为 `CMS.Navigation.TabNavigation`。用于固定、数量少的页面内分类，例如推荐、排行、歌单、歌手；与胶囊导航、玻璃 Tab 栏共用受控选择方式。文字与下划线由 Compose 自绘，点击不使用默认涟漪，保留 Tab 语义与触觉反馈。

## CLEAR 默认规格

| 属性 | 默认值 |
| --- | --- |
| 行高 | 48dp |
| 字号 | 14sp |
| 选中文字 | 当前主题强调色，Bold（700） |
| 未选文字 | SecondaryText，常规字重 |
| 下划线 | 高 5dp，圆角 2dp |
| 下划线颜色 | 当前主题强调色 |
| 下划线宽度 | 单项宽度的 60% |
| 文字与下划线间距 | 3dp，文字和线整体居中 |
| 选中文字大小 | 视觉放大到基准的 1.06 倍，以底部中心为缩放锚点 |
| 指示线弥散光 | 起步渐亮，后半程减弱，落位时完全消失；跟随圆角轮廓与位置 |
| 指示线形变 | 始终平直，运动时略微变窄并加粗，减速挤压后轻微回弹 |

## 完整最小示例

```kotlin
import androidx.compose.runtime.*
import com.lindicra.cms.*

@Composable
fun MusicCategoryNavigation() {
    var selected by remember { mutableIntStateOf(1) }
    CMS.Navigation.TabNavigation(
        labels = listOf("推荐", "排行", "歌单", "歌手"),
        selectedIndex = selected,
        onSelectedChange = { selected = it }
    )
}
```

## 可调参数

labels、selectedIndex、onSelectedChange、modifier、enabled、height、fontSize、selectedColor、unselectedColor、indicatorColor、indicatorHeight、indicatorRadius、indicatorWidthFraction、pageProgress、indicatorGap。

pageProgress 与胶囊导航一致，可以直接绑定 Pager 的连续位置；未绑定时用弹簧移动下划线。文字和下划线支持布局方向，动态减少分类时安全限制选中索引；空列表隐藏。

通过参数可配置分类文字、选中项、行高、字体、下划线尺寸、颜色与文字间距。选中项用主题色和粗字重突出，未选项保持次级文字与常规字重，颜色切换连续过渡。每项保留完整行高的点击区域；大字体时行高至少容纳文字、间距和指示线。默认不添加横向滚动，不适合塞入大量或很长的分类。

文字颜色、常规／粗体字形混合比例与缩放由受控选中项驱动，以临界阻尼弹簧连续过渡。跨项点击时仅原选项淡出、目标项增强，下划线路过的文字不被点亮；连续改选保留各项当前过渡状态。Pager 的 currentPage 变化同样平滑衔接。字体使用独立字重文件，因此常规与粗体采用交叉渐变；文字保持基准布局，以底部中心为锚点放大到 1.06 倍，底边与下划线的间距保持稳定。

下划线使用保留速度的位移弹簧，以及独立的宽厚伸缩弹簧。线始终保持平直，运动时略微变窄并相应加粗，减速时增加少量挤压，落位后沿已有形变速度轻微回弹。宽厚变化保持面积稳定，形变目标最多约缩窄 13%、加粗 15%，回弹采用较高阻尼。连续改选从当前位移、速度和形变继续运动，Pager 接管位置时用采样速度驱动形变。

弥散光采用更明显的柔和渐变，向下扩散约 10dp，光与线共用平直圆角轮廓和伸缩变换。亮度随移动速度连续变化：起步渐亮、减速时提前变暗，动画完成时归零，不额外启动延迟淡出；禁用仍平滑淡出。指示线的颜色、宽度、厚度和圆角参数变化同样经过过渡。

## 参数契约

| 参数 | 默认值 | 作用 |
| --- | --- | --- |
| labels / selectedIndex / onSelectedChange | 必填 | 受控分类 |
| enabled | true | 点击开关 |
| height / fontSize | 48dp / 14sp | 行高与文字 |
| selectedColor / unselectedColor | 主题强调色 / SecondaryText | 前景颜色 |
| indicatorColor | 主题强调色 | 下划线 |
| indicatorHeight / indicatorRadius | 5dp / 2dp | 下划线几何 |
| indicatorWidthFraction | 0.6f | 单项宽度的占比 |
| indicatorGap | 3dp | 文字与指示线的布局间距，范围 0～24dp |
| pageProgress | null | 连续页位置；省略时指示线弹簧落位 |

## 与分页绑定

```kotlin
CMS.Navigation.TabNavigation(
    labels = listOf("推荐", "排行"),
    selectedIndex = pager.currentPage,
    onSelectedChange = { scope.launch { pager.animateScrollToPage(it) } },
    pageProgress = pager.currentPage + pager.currentPageOffsetFraction
)
```

`pager` 使用 `rememberPagerState { 2 }`，`scope` 使用 `rememberCoroutineScope()`，内容放在 CMS.Content.Pager。导航只提交选择意图，不创建业务页面或保存路由。

点击、拖动和程序跳页应共享同一个页状态。不要同时维护会相互不同步的 selected 和 PagerState。检查快速连续点击、动画中横竖换向、分类删减及大字体；保留各项 Tab 语义和选中态。

相关：[Page / Pager](Page.md)、[胶囊导航](CapsuleNavigation.md)、[触控优先级](TouchPriority.md)。
