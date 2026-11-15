# 玻璃按钮 · Glass Button

CMSGlassButton 提供玻璃染色、背景采样、按压形变、共享光感和独立阴影。

## 调用参数

- onClick：点击回调。content 接收根据背景计算的前景色。
- width / height：玻璃宽高，Dp。
- cornerRadius：连续圆角半径，Dp。
- glassTint：控件染色，Color。glassTintAlpha：0～1 的染色透明度。
- shadowStrength：阴影强度，0 表示关闭。
- sampleContent：启用背景采样。samplingSource：独立内容源 ViewGroup。
- samplingUnderlay：追加采样的下层兄弟视图，连同其图标、文字及玻璃一起绘制在页面采样之上；不能包含当前镜片。
- clearLens：保留完整采样，关闭镜片自身底色与 `CMGlassSurface`；镜体透明，只绘制边缘折射。配合 `fluidShape` 时边缘带宽随抬起量增长，内部始终是“洞”（可直接看到真实屏幕）。
- clearLensBlur：clearLens 的采样模糊，Dp，默认 0。按 `fluidShape.opticalProgress` 渐入：静止为 0，浮起才到设定值，避免按下瞬间整层发糊。
- fluidShape：API 33+ 的液面几何。`CMSGlassFluidShape(width, height, strain, shear, restingTint, opticalProgress)` 指定固定采样视口内的实际轮廓；伸缩和弯曲保持面积，折射及高光跟随轮廓法线。视口的 `width`、`height` 需为形变预留空间。
- hazeState：与内容源共用的 HazeState。
- exclusiveTouchGesture：原生玻璃在按下请求禁止父层拦截；默认 glassDragPriority = true 时保留玻璃拖动，关闭玻璃优先后 CMS Pager / ScrollView 可接管页面滑动。
- highlightMaxWidth：高光宽度上限，Dp；0 隐藏边缘高光。默认自动随尺寸平滑调整，最高 0.8dp。
- highlightWidthScale：在自动宽度上进一步收细，默认 1。原生玻璃、融合轮廓与降级描边共用宽度规则；亮背景与按压只调整光照强度，原生放大形变补偿线宽。

## 采样与交互

将背景放入 CMSGlassContentSource，按钮作为内容源的兄弟节点。在 onSourceReady 获得有效源后创建按钮。采样树不能包含按钮自身。文字使用 content 回调提供的颜色，以适应采样背景。

```kotlin
CMSGlassButton(
    onClick = onClick,
    width = 72.dp,
    height = 44.dp,
    cornerRadius = 20.dp,
    glassTint = Color(0xFF5F9BFF),
    glassTintAlpha = 0.35f,
    shadowStrength = 2f,
    sampleContent = true,
    samplingSource = source,
    hazeState = haze
) { foreground -> Text("玻璃", color = foreground) }
```

## 推荐入口与默认参数

常规按钮使用 `CMS.Controls.GlassButton`。上面的 clearLens、fluidShape、samplingUnderlay 和高光参数属于底层 `CMSGlassButton` 的扩展能力，需要时直接调用底层入口。

| 统一入口参数 | 默认值 | 用途 |
| --- | --- | --- |
| onClick / content | 必填 | 操作及自适应前景色内容 |
| width / height | 36dp / 36dp | 玻璃尺寸；宿主应确保合理触摸区域 |
| enabled / onLongClick | true / null | 点击与可选长按 |
| useNativeGlass / sampleContent | true / false | 原生材质与背景采样 |
| samplingSource / hazeState | null / null | 可显式绑定背景来源 |
| cornerRadius | null | 自动按组件尺寸解析 |
| glassTint / glassTintAlpha | 白色 / null | 染色与自动透明度 |
| glassBackground | 白色 8% | 背景基底 |
| shadowStrength / touchEffectIntensity | 1f / 1f | 阴影与按压效果 |
| motionBlur | 0dp | 运动模糊 |
| exclusiveTouchGesture | false | 请求原生玻璃独占；是否让页面接管由 glassDragPriority 决定 |

## 状态与组合

| 情况 | 行为 / 宿主责任 |
| --- | --- |
| 禁用 | enabled = false，业务回调不应被触发 |
| 加载中 | 没有独立 loading 参数；宿主控制 enabled 与内容 |
| 图标按钮 | 给出可朗读的操作名称，不能只提供装饰图形 |
| 融合工具栏 | 自动继承 CMS.Interaction.GlassFusion 作用域 |
| 背景采样 | sampleContent = true，明确绑定独立背景 |
| 材质降级 | 操作仍应可用，内容使用回调前景色 |

按压反馈与业务选择状态分别管理；按钮不持有业务开关值。检查快速点按、滚动中按下、长按取消、深浅背景以及旧系统的降级外观。

相关：[采样源](ContentSource.md)、[玻璃融合](GlassFusion.md)、[触控优先级](TouchPriority.md)。
