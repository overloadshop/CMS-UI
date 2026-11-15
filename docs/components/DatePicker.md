# 日期选择器 · Date Picker

同一个日期值可通过紧凑日期输入 + 月历、年月日滚筒或文本输入选择。默认以日期输入和月历为主；滚筒与输入模式只在宿主明确指定或打开 `showModeSwitch` 后出现。

```kotlin
var date by remember { mutableStateOf(java.time.LocalDate.now()) }
CMS.Controls.DatePicker(
    selected = date,
    onSelected = { date = it },
    minDate = java.time.LocalDate.of(1900, 1, 1),
    maxDate = java.time.LocalDate.of(2100, 12, 31),
    mode = CMSDatePickerMode.Calendar
)
```

顶部日期字段接受 `YYYY-MM-DD`，点击“应用”后才提交；输入过程中不会回写宿主状态，也不会因为每个字符变化重建日历。日历标题中的年份和月份可点击，分别打开年份列表与月份网格。选择年月只改变浏览位置，点击具体日期才提交。日历保留六周高度，翻月不会让下方内容跳动；支持周一或周日作为一周起始，以及“今天”快捷操作。

滚筒模式只列出范围内的年、月、日。切换到短月份或非闰年时，日自动收至该月最后一天；部分月份范围会继续限制月与日。

输入模式接受 `YYYY-MM-DD`，在点击“应用日期”前校验真实日期与可选范围，错误输入不会被静默转换。

`minDate` / `maxDate` 为闭区间。未指定时使用 1970–2100 年；支持 1–9999 年，最早日期不能晚于最晚日期。外部传入超范围选中值时，显示最近的有效边界，不擅自触发回调。

## 参数契约

| 参数 | 默认值 | 用途 |
| --- | --- | --- |
| selected / onSelected | 必填 LocalDate / 回调 | 受控日期，不包含时间和时区 |
| weekStart | Monday | 可选 Sunday |
| minDate / maxDate | null / null | 未指定时使用组件默认范围 |
| showTodayButton | true | 显示今天快捷操作，仍受范围限制 |
| mode | Calendar | Calendar、Wheel、Input |
| showModeSwitch | false | 是否允许在组件内切换模式 |
| modifier | Modifier | 外部尺寸与布局 |

## 状态与验收

| 操作 | 状态变化 |
| --- | --- |
| 浏览年份 / 月份 | 只改变浏览位置，不提交日期 |
| 选择具体日期 | onSelected 提交，宿主更新 selected |
| 编辑日期字符串 | 保存内部草稿，点击应用且校验通过才提交 |
| 切换模式 | 使用同一个日期值 |
| 范围或外部日期改变 | 更新有效显示；宿主业务值是否修正由应用决定 |

应用保存日期时明确使用 LocalDate，不能把它直接当成某时区零点的时间戳。生日、有效期等纯日期适合此入口；日程开始时间需要结合 TimePicker 和应用时区。

检查闰年 2 月、月末切月、最小与最大边界、“今天”超范围，以及输入错误后原日期是否保留。可访问性检查应覆盖日期名称、当前选中态和模式切换。

相关：[时间](TimePicker.md)、[滚筒](WheelPicker.md)、[输入框](TextField.md)。
