# 滚筒 · Wheel Picker

固定行高的滚筒选择器。拖动、惯性滚动、点击某行和外部改值共用真实布局中心；文字透视直接跟随滚动，没有额外的延迟缩放。停稳后提交 `onSelected`，滑过各项时提供轻触觉。

```kotlin
var index by remember { mutableIntStateOf(2) }
CMS.Controls.WheelPicker(
    items = listOf("周一", "周二", "周三", "周四", "周五"),
    selectedIndex = index,
    onSelected = { index = it },
    loop = true,
    itemHeight = 36.dp,
    visibleCount = 5,
    wheelRadius = 110.dp
)
```

`visibleCount` 使用 3–9 的奇数，偶数会向上调整，使选中行始终处于中心。非循环模式的首末项也能居中。循环模式从有限索引空间的中部开始，外部改值选择最近的同余位置。

`frictionMultiplier` 越大，惯性停得越快；`dampingRatio` 限制在 0.8–1，默认 1；`wheelRadius` 越小，透视越明显，半径不会被强行抬到行高。`showSelectionBand` 控制选中背景，`hapticEnabled` 控制逐项触觉，`edgeFadeStart` 控制透明淡出的范围，淡出适用于不同背景色。

旧的底层参数 `scaleTransitionMillis` 保留调用兼容，但不再给滚动透视叠加动画。

## 参数契约

| 参数 | 默认值 | 契约 |
| --- | --- | --- |
| items / selectedIndex / onSelected | 必填 | 文案列表、逻辑索引、停稳提交 |
| itemHeight / visibleCount | 36dp / 5 | 固定行高；可见行使用 3–9 的奇数 |
| loop | false | 是否循环 |
| frictionMultiplier | 1.4f | 越大，惯性越快停止 |
| dampingRatio / stiffness | 1f / 500f | 落位弹簧参数 |
| showSelectionBand | true | 中心选中背景 |
| wheelRadius | 110dp | 透视半径 |
| hapticEnabled | true | 逐项反馈，还受主题触感开关影响 |
| edgeFadeStart | 0.45f | 边缘淡出起始位置 |
| modifier | Modifier | 宽度及外部布局 |

## 数据与操作边界

| 场景 | 接入规则 |
| --- | --- |
| 用户拖动或点击项 | 组件定位到中心，停稳后 onSelected 提交 |
| 宿主修改 selectedIndex | 移动至对应项，不把程序改值当成新用户选择 |
| 循环模式 | 回调仍使用原始 items 的逻辑索引 |
| 动态修改 items | 同步修正选中索引；业务数据先用稳定 id 再映射索引 |
| 空数据 | 宿主显示空状态，避免依赖一个无内容的选择器 |

滚筒适合短且有顺序的集合，超长名称或搜索型集合更适合菜单或独立列表。行高需要适应系统字号，避免文字被中心带裁切。

分页内的独立滚筒不应注册为整页主滚动桥。将它的区域从 Pager.canStart 中排除，保留其吸附与惯性。检查首末项居中、快速反向、外部改值、列表缩短和关闭触觉。

相关：[日期](DatePicker.md)、[时间](TimePicker.md)、[触控优先级](TouchPriority.md)。
