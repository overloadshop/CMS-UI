# 颜色选择器 · Color Picker

提供色谱、色轮、RGB 滑杆和色板四种入口，并支持 HEX / RGBA 数值输入、透明度与最近使用。

```kotlin
var color by remember { mutableStateOf(Color(0xFF3B82F6)) }
CMS.Controls.ColorPicker(
    color = color,
    onColorChange = { color = it },
    mode = CMSColorPickerMode.Wheel,
    palette = CMSColorPalette.Pastel,
    showModeSwitch = true,
    showInputs = true,
    showAlpha = true
)
```

色谱使用横向饱和度、纵向亮度与独立色相条。色轮的角度控制色相、半径控制饱和度，亮度通过独立滑杆调整。RGB 模式直接调节红、绿、蓝通道。

色板支持鲜明、柔和、灰阶三套，可通过 `palette` 指定初始套装。`presets` 仍接受任意宿主色板；`showPresets` 只控制这组预设，不影响内置色板模式。

HEX 六位采用 `RRGGBB`，八位采用 **`AARRGGBB`**，与 Android `Color(0xAARRGGBB)` 一致。无效或未完成的输入不会提交。RGBA 通道输入范围均为 0–255。关闭 `showAlpha` 后只输出不透明颜色，并只接受六位 HEX。

拖动实时更新颜色，结束后记入当前组件的最近使用；点击预设、内置色板、历史或应用 HEX 也会记入历史。`recentLimit = 0` 关闭历史。最近使用不跨组件持久化。

切换到黑白灰时保留原色相，提高饱和度或亮度后可以继续之前的颜色。色相条与色块扩大触摸区域，选中点和数值随交互同步更新。

## 参数契约

| 参数 | 默认值 | 含义 |
| --- | --- | --- |
| color / onColorChange | 必填 | 受控 Compose Color |
| mode | Spectrum | Spectrum、Wheel、Sliders（RGB 通道）、Palette |
| showModeSwitch / showInputs | true / true | 模式入口与数值输入 |
| showPresets / presets | true / CMSColorPresets | 宿主预设色板 |
| showAlpha | true | 开启透明度编辑 |
| recentLimit | 6 | 当前实例最近颜色数量，0 关闭 |
| panelHeight | 150dp | 色彩面板高度 |
| palette | Vivid | Vivid、Pastel、Neutral（灰阶） |
| modifier | Modifier | 外部布局 |

## 输入与状态

| 入口 | 提交方式 |
| --- | --- |
| 色谱 / 色轮 / 通道滑杆 | 拖动中实时更新，结束时记入最近颜色 |
| 预设 / 色板 / 最近颜色 | 点击后更新并记录 |
| HEX | 完整、合法并点击应用后提交 |
| RGBA | 通道需在 0–255 内，错误草稿不提交 |
| showAlpha = false | 输出不透明色，HEX 只接受六位 |

需要“取消恢复原色”时，宿主保存独立草稿，确认后再修改最终主题色。最近颜色只属于当前组件实例，不替应用保存色板。

颜色选择区域与滑杆具有专用拖动；放在 Pager 内时用 canStart 排除，避免横向切页抢走色相调整。检查透明黑、透明白、六位与八位 HEX、大字体下数值输入及软键盘。纯色块应有能读出的名称或数值。

相关：[主题](Appearance.md)、[滑块](Progress.md)、[触控优先级](TouchPriority.md)。
