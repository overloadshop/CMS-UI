# 滑块 · Slider

CMSSlider 提供连续数值调节、离散档位、只读展示和物理拖动。

## 使用场景与状态

推荐入口 `CMS.Controls.Slider`。用于有明确范围的连续值或档位，例如音量、模糊强度；精确数值输入应同时提供数值字段。`value` 始终使用业务范围内的真实值，不必自行归一化到 0～1。

| 参数 | 默认值 | 契约 |
| --- | --- | --- |
| `range` | `0f..1f` | 数值范围；端点和当前值使用同一单位 |
| `steps` | 0 | 0 连续；正值 N 使用 N 等分，即含端点 N+1 个档位 |
| `liveUpdate` | false | 跟随实现的提交节奏；需要实时更新数值时可显式开启 |
| `onValueChangeStarted` | 空回调 | 开始拖动，可暂停业务层冲突更新 |
| `onValueChange` | 空回调 | 回写受控 value |
| `onValueChangeFinished` | 空回调 | 拖动完成后持久化或提交耗时操作 |
| `enabled` / `readOnly` | true / false | 禁用交互 / 只读展示 |
| `vertical` | false | 改为纵向轨道，需要宿主给出足够高度 |
| `trackThickness` / `pressedThickness` | 6dp / 13dp | 静止和按压轨道厚度 |
| `dampingStrength` | 1f | 跟随阻尼强度，业务值不应再次叠加低通延迟 |

```kotlin
var volume by remember { mutableFloatStateOf(40f) }
CMS.Controls.Slider(
    value = volume, range = 0f..100f, steps = 20,
    onValueChange = { volume = it },
    onValueChangeFinished = { /* 保存 volume */ }
)
```

## 接入边界

跟随阻尼、端点越界和按压增粗分别由 dampingEnabled、overscrollEnabled、thicknessAnimation 控制。关闭越界只改变视觉与弹簧，不改变范围。不要在每帧回调内启动网络保存。

滑块和页面横滑使用相同方向时，在 CMS Pager 的 `canStart` 中排除滑块的视口范围。触控优先级默认允许父级接管，不能只依赖子控件消费事件。滑块旁提供当前值与单位，避免用户只能凭轨道估计数值。

## 调用参数

- value：当前真实值；默认范围为 0f～1f。
- onValueChange：拖动时的新值；onValueChangeStarted / onValueChangeFinished：开始与结束回调。
- selectedColor：已选轨道色。
- thumbStyle：CMSSliderThumbStyle.None / Hollow / Solid。
- range：自定义浮点区间；steps：0 为连续，N 为 N+1 个档位。
- readOnly：只读；enabled：是否启用。
- thicknessAnimation：按压增粗；dampingEnabled：阻尼跟随。
- overscrollEnabled：端点拉伸与回弹。
- vertical：竖向；liveUpdate：值更新策略。

```kotlin
CMSSlider(
    value = value,
    onValueChange = { value = it },
    selectedColor = Color(0xFF5F9BFF),
    thumbStyle = CMSSliderThumbStyle.Hollow,
    steps = 0,
    readOnly = false,
    thicknessAnimation = true,
    dampingEnabled = true,
    overscrollEnabled = true
)
```

## 状态验收

| 场景 | 检查点 |
| --- | --- |
| 自定义 0–100 范围 | value 和回调均为真实值 |
| 离散档位 | 含端点 N+1 档，显示数值与落位一致 |
| enabled = false / readOnly = true | 不提交用户修改 |
| 阻尼与越界 | 不把视觉超出当作范围外业务值 |
| Pager 内调节 | canStart 排除轨道区域，调节不误切页 |

相关：[输入框](TextField.md)、[触控优先级](TouchPriority.md)、[主题](Appearance.md)。
