# 列表 · List

列表由容器、分组、行、文本和分割线组成。按压背景属于整行，标题、说明和尾部控件只负责内容。

## 组成

| 组件 | 作用 |
| --- | --- |
| `CMS.Content.List` / `CMSList` | 只控制组间隙 |
| `CMS.Content.Section` / `CMSSection` | 卡片分组，自动绘制行间分割线 |
| `CMS.Content.ListSection` / `CMSListSection` | 相同的行布局，不自动添加分割线 |
| `CMS.Content.ListRow` / `CMSListRow` | 前导、内容、尾部三个槽位 |
| `CMSListRowText` | 标题和可选说明 |
| `CMS.Content.ToggleRow` / `CMSToggleRow` | 点击整行或开关切换 |
| `CMS.Content.MenuRow` / `CMSMenuRow` | 点击或长按整行打开菜单，以右侧胶囊定位 |
| `CMS.Content.SectionTitle` | 独立组标题 |
| `CMS.Content.Divider` | 独立分割线，可指定颜色、粗细和左右缩进 |
| `CMS.Content.RowDivider` | 独立分割线，可显式添加上下留白 |

`CMSSettingsCard` 是保留容器内边距的通用内容卡片，其中的列表行也由卡片层绘制按压底色。设置列表使用 `Section` 或 `ListSection`，让内容缩进与整行触摸区域分离。

## 布局约定

| 参数 | 默认值 | 含义 |
| --- | --- | --- |
| `groupSpacing` | 12dp | 组间隙 |
| 分组 `contentPadding` | 左右 16dp，上下 0dp | 左右值传给行内容；上下值是卡片边缘留白 |
| `sectionCorner` | 20dp | 卡片连续圆角 |
| 行 `minHeight` | 48dp | 包含内容内边距的最小高度 |
| 行 `contentPadding` | 上下 10dp | 内容与整行边缘的距离 |
| `highlightShape` | 0dp 圆角 | 整行按压背景的形状，外边缘由卡片圆角裁切 |
| 标题 / 说明 / 组标题 | 15sp / 11sp / 13sp | 关闭字体额外上下留白 |
| `RowDivider.verticalPadding` | 0dp | 不自动增加行间空白 |

行高是最小值，长文本或更大的上下内边距会自然撑高内容。不要同时给分组与行添加相同的水平留白。通用行的 `horizontalInset` 可显式覆盖分组下发的左右缩进；`null` 表示继承，支持不对称和 RTL。

分组内的按压色由卡片层绘制在内容后面，铺满卡片宽度，不受行的内容缩进或旧卡片容器内边距裁切。贴近卡片上下边缘的行同时覆盖对应边缘留白；上下存在其他内容时不覆盖其他内容。独立行自行绘制按压底色。按压不改变文字、图标或控件的颜色。前导与内容、内容与尾部各间隔 12dp；没有槽位时不保留间隙。

## 基本用法

```kotlin
CMS.Content.List {
    CMS.Content.Section {
        CMS.Content.ListRow("导航行", desc = "说明", onClick = onNavigate)
        CMS.Content.ToggleRow("开关行", checked = on, onCheckedChange = { on = it })
        CMS.Content.MenuRow("菜单行", value = "跟随系统",
            onOpen = { anchor = it; visible = true })
    }
}
```

`Section` 的分割线由实际行位置决定，不占布局高度，也不依赖声明时的计数。增删、调整顺序或重组时，首行不画线。不要在启用自动分割线的分组中重复插入 `RowDivider`。

## 自定义内容和按压

```kotlin
CMS.Content.Section(
    contentPadding = PaddingValues(horizontal = 16.dp),
    separatorIndent = 16.dp,
    separatorEndIndent = 16.dp
) {
    CMS.Content.ListRow(
        minHeight = 52.dp,
        contentPadding = PaddingValues(vertical = 10.dp),
        pressedColor = Color(0x1F8E8E93),
        onClick = onNavigate,
        leading = { Text("★") },
        trailing = { Text("更多") }
    ) {
        CMSListRowText("自定义标题", "说明文字")
    }
}
```

`CMSMenuRow` 同样支持行高、内边距、按压形状与颜色、标题与说明字号及颜色。菜单保持长按滑动选择，松手后或滚动取消时结束按压反馈。

## 状态归属与交互边界

| 行类型 | 状态 / 回调 | 接入规则 |
| --- | --- | --- |
| 导航行 | onClick | 宿主管理路由，箭头只表达可进入 |
| ToggleRow | checked / onCheckedChange | 点击整行和开关共享同一个值，避免重复翻转 |
| MenuRow | value / onOpen(IntRect) | value 显示业务值，窗口坐标用于菜单定位 |
| 通用 ListRow | 可空 onClick / enabled | 没有 onClick 时只展示内容 |
| danger 导航行 | 危险文字色 | 需要确认时再打开 Alert，不把危险色当作确认过程 |

标题和说明必须能独立表达动作或状态。尾部可操作控件不要被另一层全行透明点击区域遮住。系统字体变大时让行高自然增长，检查长说明、RTL、不对称缩进和动态删除首行后的分割线。

大量数据需要惰性列表或网格，List / Section 不是虚拟化容器。带固定顶栏的设置页优先使用 Page；菜单显示和弹窗显示状态由宿主持有。

相关：[Page](Page.md)、[开关](Switch.md)、[菜单](SelectMenu.md)、[确认弹窗](Alert.md)。
