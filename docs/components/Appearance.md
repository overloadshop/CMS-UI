# 主题与外观 · Theme & Appearance

CMSTheme 提供主题色、玻璃材质、采样状态、触感与返回历史。

## 推荐组件库入口

应用根部使用 `CMS.Appearance.Provide` 提供默认配置，再用 `CMS.Appearance.Theme` 建立主题。直接调用 CMSTheme 仍然可用；新接入优先使用统一入口，组件上的显式参数可覆盖默认值。

| Theme 参数 | 默认来源 | 契约 |
| --- | --- | --- |
| darkTheme | CMSSettings，未设置时跟随系统 | 深浅主题 |
| accentColor | CMSSettings，null 使用主题默认值 | 同步 LocalCMSAccentColor 和 MaterialTheme 主色 |
| glassMaterial | CMSSettings 或当前 LocalGlassMaterial | GlassMaterial(advanced, clarity) |
| hazState | null 时创建 HazeState | 注意此参数名是 hazState；供主题内组件共享 |
| hapticsEnabled | CMSSettings，未设置时 true | 当前主题的触感开关 |
| haptics | null | 可选宿主触感实现 |
| backHistory | null | 可选返回历史提供者 |
| content | 必填 | 主题作用范围 |

```kotlin
CMS.Appearance.Provide(
    CMSSettings(
        accentColor = Color(0xFF3F78D1),
        glassMaterial = GlassMaterial(advanced = true, clarity = 2),
        hapticsEnabled = true
    )
) {
    CMS.Appearance.Theme { /* 应用内容 */ }
}
```

## 材质

advanced 为 false 时使用降级材质。clarity 为 0、1、2 时分别为毛玻璃、舒适玻璃、真实玻璃。LocalCMSAccentColor 提供当前主题的局部强调色，MaterialTheme.colorScheme.primary 同步更新。

## 默认配置与状态归属

| CMSSettings 字段 | 默认值 | 说明 |
| --- | --- | --- |
| darkTheme | null | 跟随系统 |
| accentColor / glassMaterial | null | 保留默认主题色和材质 |
| hapticsEnabled | null | Theme 最终默认启用 |
| tabForm | Auto | 窗口宽度 ≥ 600dp 用侧栏，否则贴边栏 |
| tabPlacement | Bottom | 导航形态配置；实际布局仍由宿主放置 |

Provide 不负责保存偏好，应用应持有设置状态并在变化后重新传入。嵌套 Theme 的作用范围由 Compose 树决定，可用于局部外观覆盖。

| 检查场景 | 预期 |
| --- | --- |
| 系统深浅切换 | 未显式覆盖时跟随系统 |
| 修改强调色 | 控件使用主题色；显式传入的控件颜色保持自己的值 |
| 降级材质 | 保留点击、焦点和导航功能 |
| 大字体 / 宽窗口 | 内容可撑高，导航按窗口宽度选择形态 |

相关：[Page](Page.md)、[触觉反馈](Haptics.md)、[Tab 栏](TabBar.md)。
