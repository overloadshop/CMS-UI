# 触觉反馈 · Haptic Feedback

CMSHaptics 提供与宿主解耦的统一触感接口。

## 使用方法

在 CMSTheme 中设置 hapticsEnabled，并通过 rememberCMSHaptic() 获取反馈函数。

```kotlin
CMSTheme(hapticsEnabled = true) {
    val haptic = rememberCMSHaptic()
    CMSGlassButton(onClick = { haptic(CMSHapticKind.Click) }) { color ->
        Text("体验", color = color)
    }
}
```

## 反馈类型

| CMSHapticKind | 使用场景 |
| --- | --- |
| Tick | 滚筒跨项、轻量选择反馈 |
| Click | 按键确认 |
| LongPress | 长按达到触发条件 |

宿主可向 CMSTheme.haptics 传入 CMSHapticsProvider，或通过 LocalCMSHaptics 覆盖。hapticsEnabled 为 false 时，当前主题内的反馈停用。实际震感由设备硬件和系统设置决定。

## 接入与触发边界

| 入口 | 责任 |
| --- | --- |
| CMS.Appearance.Theme(hapticsEnabled = …) | 当前主题开关 |
| CMS.Appearance.Theme(haptics = …) | 替换宿主反馈实现 |
| rememberCMSHaptic() | 在组合期取得函数，在操作回调中调用 |
| WheelPicker.hapticEnabled | 该滚筒的逐项反馈开关 |

CMS 控件可能已在内部反馈。上例用于手动体验反馈；业务接入时先核对控件已有行为，避免一次操作重复触发。不要在每次重组、每一帧或网络重试时发出震动。

视觉状态必须独立表达操作结果，关闭触觉或设备没有震动器时仍能完整使用。检查系统关闭触觉、应用开关关闭和宿主自定义实现三种情况。

相关：[外观](Appearance.md)、[滚筒](WheelPicker.md)、[玻璃按钮](GlassButton.md)。
