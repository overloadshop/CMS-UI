# 底部面板 · Bottom Sheet

CMSBottomSheet 在独立 Dialog 中显示面板，支持随手拖动、松手惯性与弹簧退场。

## 参数契约

| 参数 | 类型 / 默认值 | 契约 |
| --- | --- | --- |
| onDismiss | 必填回调 | 退场结束后关闭宿主显示状态 |
| heightFraction | Float / 0.86f | 相对窗口高度比例 |
| maxHeight | Dp / 640dp | 面板最大高度，实际还受窗口限制 |
| content | ColumnScope.(dismiss) | 内容及启动退场的函数 |

```kotlin
var visible by remember { mutableStateOf(false) }
if (visible) {
    CMS.Presentation.Sheet(
        onDismiss = { visible = false },
        heightFraction = 0.86f
    ) { dismiss ->
        Text("底部面板")
        CMSGlassButton(onClick = dismiss) { color ->
            Text("完成", color = color)
        }
    }
}
```

## 关闭与滚动

使用 dismiss 启动物理退场，不要在按钮点击时直接将 visible 设为 false。内容顶部手势条由组件绘制，无需添加关闭叉号。内部 CMSScrollView 滚到顶部后可继续下拉关闭。

## 状态与操作顺序

| 阶段 | 宿主 / 组件行为 |
| --- | --- |
| 打开 | 宿主将 visible 设为 true，组成 Sheet |
| 内容确认 | 宿主提交数据，然后调用内容提供的 dismiss |
| 下拉关闭 | 组件处理拖动、惯性和退场 |
| 退场结束 | onDismiss 将 visible 设为 false |
| 再次打开 | 宿主决定保留草稿还是重置内容 |

Sheet 不替应用保存表单值。希望取消后丢弃修改时，使用独立草稿状态，确认后再提交。不要把所有退出途径都当作确认。

长内容在内部使用 ScrollView，避免多个同方向滚动容器重复接管。输入场景需要检查软键盘、系统导航栏和小窗口下的可见空间。Sheet 提供手势关闭；具体焦点、标题和操作名称由内容负责。

相关：[ScrollView](Scroll.md)、[确认弹窗](Alert.md)、[动作列表](ActionSheet.md)。
