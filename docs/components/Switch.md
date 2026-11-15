# 切换开关 · Switch

CMSSwitch 是受控组件，支持点击切换、拖动形变和弹簧回位。

## 概述

推荐入口 `CMS.Controls.Toggle`。用于立即生效的二元偏好，例如“触觉反馈”；三态或互斥选项组改用菜单或分段选择。组件不写入设置，应用在回调中持有 `checked` 并决定是否保存。

| 属性 | 类型 / 默认值 | 说明 |
| --- | --- | --- |
| `checked` | Boolean，必填 | 当前开关状态，视觉与语义的唯一来源 |
| `onCheckedChange` | `(Boolean) -> Unit`，必填 | 提交目标状态，不直接保存偏好 |
| `width` / `height` | Dp / 44dp、24dp | 轨道几何尺寸；整行触控可由 ToggleRow 扩大 |
| `trackOnColor` | Color / 主题强调色 | 开启轨道颜色 |
| `trackOffColor` | Color / TertiaryFill | 关闭轨道颜色 |
| `thumbOnColor` / `thumbOffColor` | Color / 白色、TertiaryText | 开启和关闭滑块颜色 |

```kotlin
var haptics by remember { mutableStateOf(true) }
CMS.Content.Section {
    CMS.Content.ToggleRow("触觉反馈", checked = haptics,
        onCheckedChange = { haptics = it })
}
```

## 交互与状态同步

点击切换、拖动确认和外部更新都应回到同一个状态变量。需要异步保存时先更新本地显示，再保存；失败后由应用回滚并给出原因。不要让整行和开关分别执行“取反”，否则一次点击可能切换两次，优先使用 ToggleRow。

## 无障碍

独立开关应配有可读标签；列表优先用 ToggleRow 把标题与状态关联。关闭态不能只靠透明度表达，也应保留滑块位置差异。此入口没有单独的 enabled 参数，禁止操作时由宿主决定是否展示只读行。

## 调用参数

- checked：当前状态。
- onCheckedChange：点击或拖动确认后的新状态。
- trackOnColor / trackOffColor：开启与关闭轨道色。
- thumbOnColor / thumbOffColor：开启与关闭滑块色。
- modifier：外部布局。

```kotlin
var checked by remember { mutableStateOf(true) }
CMSSwitch(
    checked = checked,
    onCheckedChange = { checked = it },
    trackOnColor = Color(0xFF5F9BFF),
    trackOffColor = Color(0xFF737B89),
    thumbOnColor = Color.White,
    thumbOffColor = Color.White
)
```

## 状态验收

| 场景 | 检查点 |
| --- | --- |
| 点击整行或开关 | 每次只提交一次目标状态 |
| 拖动后松手 | 回调与最终滑块位置一致 |
| 外部更新 checked | 视觉和朗读同步新的状态 |
| 保存失败 | 宿主回滚并说明原因 |
| 大字体 / RTL | 标题可读，控件不被挤出可用空间 |

相关：[列表](SettingsRows.md)、[主题](Appearance.md)、[触觉反馈](Haptics.md)。
