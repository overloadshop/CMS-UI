# 玻璃采样源 · Glass Content Source

CMSGlassContentSource 将 Compose 内容转换为玻璃可采样的独立 ViewGroup。

## 容器选择

完整分页页面优先使用 [CMS.Content.Pager + Page](Page.md)。Pager 管理统一采样并关闭子页重复采样。独立带顶栏的滚动页使用 Page。需要自己组织背景和玻璃叠层时，再显式使用 Source。

| 参数 | 默认值 | 契约 |
| --- | --- | --- |
| modifier | Modifier | 采样区域的尺寸与位置 |
| hazeState | null | 回落到主题状态；与玻璃共用同一个 HazeState |
| onSourceReady | 空回调 | 原生内容源创建后回传 ViewGroup |
| sourceTag | CMS_GLASS_CONTENT_SOURCE_TAG | 自动发现内容源的标识，自定义时宜显式传 samplingSource |
| backgroundColor | PageBackground | 原生源底色，应与实际页面背景一致 |
| content | 必填 | 被采样背景内容 |

```kotlin
var source by remember { mutableStateOf<ViewGroup?>(null) }
Box {
    CMS.Content.Source(
        modifier = Modifier.fillMaxSize(),
        hazeState = haze,
        onSourceReady = { source = it }
    ) { BackgroundContent() }
    source?.let {
        CMS.Controls.GlassButton(
            onClick = {},
            sampleContent = true,
            samplingSource = it,
            hazeState = haze
        ) {}
    }
}
```

## 源的边界

玻璃必须放在内容源之外。源只包含背景内容，不包含玻璃、顶栏和菜单。页面尺寸变化后组件自动更新采样尺寸。布局未就绪时不要强行绑定空源。

## 生命周期与嵌套

| 情况 | 接入规则 |
| --- | --- |
| 原生源尚未准备好 | 保留可空 source，不强行绑定无效 ViewGroup |
| 同一组玻璃 | 共用 source 和 haze，避免错位采样 |
| Source 内再放 Pager | Pager.sampleGlassContent = false；Page / ScrollView 也关闭重复采样 |
| 页面离开组合 | 不把旧 ViewGroup 长期保存到应用单例中 |
| 背景存在组件间空隙 | 使用实际页面底色，透明源不能提供空隙里的背景色 |

Source 内部包含独立 ComposeView，并继承父组合环境；它是原生采样边界。不要给每个普通行创建 Source，也不要无目的地嵌套多个采样容器。它不创建手势仲裁，分页统一由 Pager 处理。

检查页面尺寸变化、横竖屏、内容滚动、材质降级以及多个独立内容源是否采到正确页面。玻璃自身及其祖先不能被显式指定为该玻璃的 samplingSource。

相关：[Page](Page.md)、[玻璃按钮](GlassButton.md)、[玻璃融合](GlassFusion.md)。
