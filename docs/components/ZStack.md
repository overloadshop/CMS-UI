# 叠层容器 · ZStack

`CMS.Layout.ZStack` 在同一区域叠放子项，接收 Compose `BoxScope`。适合背景与覆盖内容、页面与悬浮操作层。

| 参数 | 默认值 | 作用 |
| --- | --- | --- |
| alignment | Alignment.TopStart | 子项默认对齐 |
| minWidth / maxWidth | Dp.Unspecified | 可选宽度边界 |
| minHeight / maxHeight | Dp.Unspecified | 可选高度边界 |
| modifier | Modifier | 尺寸与外部布局 |
| content | BoxScope | 支持 align 和 matchParentSize |

```kotlin
CMS.Layout.ZStack(
    modifier = Modifier.fillMaxWidth().height(160.dp)
) {
    CMS.Content.Text("背景内容", modifier = Modifier.align(Alignment.Center))
    CMS.Content.Tag("本地", modifier = Modifier.align(Alignment.TopEnd))
}
```

默认后声明的子项绘制在前面；需要明确层级时用 Compose `zIndex`。`matchParentSize` 跟随容器尺寸，不用它给容器撑出高度。父布局约束仍然限制尺寸边界。

## 叠层与触摸

| 场景 | 接入规则 |
| --- | --- |
| 覆盖内容 | 只给实际操作区域添加点击处理 |
| 全屏透明层 | 会影响下层触摸；避免无意覆盖页面 |
| 玻璃采样 | Source 与玻璃放在兄弟节点，采样树不包含镜片自身 |
| 固定顶栏与滚动 | 优先使用 Page，统一处理避让和滚动桥 |

ZStack 不负责窗口边距、焦点或手势仲裁。涉及这些行为时使用对应组件或宿主 Modifier。

相关组件：[Page](Page.md)、[采样源](ContentSource.md)、[玻璃按钮](GlassButton.md)。
