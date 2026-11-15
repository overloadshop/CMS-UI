# 时间选择器 · Time Picker

时、分滚筒共享通用滚筒的中心、吸附、透视和触觉。12 小时制额外显示上午/下午滚筒，可以选择完整的一天；回调小时始终使用 0–23。

```kotlin
var hour by remember { mutableIntStateOf(9) }
var minute by remember { mutableIntStateOf(30) }
CMS.Controls.TimePicker(
    hour = hour,
    minute = minute,
    onTimeChange = { h, m -> hour = h; minute = m },
    is24Hour = false,
    minuteStep = 5
)
```

`minuteStep` 支持 1–60。未落在步长上的外部分钟值显示最近的有效分钟；下一次用户选择提交对齐后的分钟。12 点上午对应小时 0，12 点下午对应小时 12。

`itemHeight`、`visibleCount` 和 `loop` 与通用滚筒一致。上午/下午列不循环。

## 参数契约

| 参数 | 默认值 | 契约 |
| --- | --- | --- |
| hour / minute | 必填 | 宿主按 0–23 / 0–59 传入 |
| onTimeChange | 必填 (Int, Int) -> Unit | 回调小时始终为 24 小时值 |
| minuteStep | 1 | 1–60，约束可选分钟 |
| is24Hour | true | false 时增加上午 / 下午 |
| itemHeight | 36dp | 单行高度 |
| visibleCount | 5 | 与 WheelPicker 的奇数可见行规则一致 |
| loop | false | 时、分列是否循环 |
| modifier | Modifier | 外部布局 |

## 格式与状态

| 用户可见值 | 回调值 |
| --- | --- |
| 上午 12:00 | 0, 0 |
| 中午 12:00 | 12, 0 |
| 下午 1:30 | 13, 30 |
| 24 小时制 23:55 | 23, 55 |

步长为 5 时可选 0、5、10 等分钟；外部传入 12 分可能显示对齐后的分钟，显示归一化不应当作业务提交。宿主仍应保持合法状态，在回调时同时更新时和分。

TimePicker 不包含日期、时区、秒或跨天规则。预约、闹钟等业务由应用把日期和时间组合，并处理夏令时或跨天条件。检查 00:00、12:00、23:59、步长边界以及切换 12/24 小时制后业务值是否一致。

相关：[日期](DatePicker.md)、[滚筒](WheelPicker.md)、[面板](HalfModal.md)。
