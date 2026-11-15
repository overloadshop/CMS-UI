# 动作列表 · Action Sheet

`CMS.Presentation.ActionSheet` 为当前对象提供多个动作，例如复制、导出、删除。此接口使用独立弹窗呈现；需要自定义底部内容面板时使用 [Sheet](HalfModal.md)。

```kotlin
var visible by remember { mutableStateOf(true) }
if (visible) {
    CMS.Presentation.ActionSheet(
        title = "记录操作",
        actions = listOf(
            CMSDialogAction("复制", onClick = { /* 复制记录 */ }),
            CMSDialogAction("导出", onClick = { /* 准备导出 */ }),
            CMSDialogAction("删除", onClick = { /* 删除记录 */ }, destructive = true)
        ),
        onDismiss = { visible = false }
    )
}
```

## 参数与动作模型

| 参数 | 默认值 | 用途 |
| --- | --- | --- |
| actions | 必填 List<CMSDialogAction> | 按声明顺序展示动作 |
| title | 空字符串 | 空时隐藏标题 |
| cancelText | “取消” | 取消按钮文案 |
| onDismiss | 必填 | 退场后移除宿主显示状态 |
| actionLayout | Vertical | 可选 Horizontal，横向均分空间 |
| scrimAlpha | 0.32f | 遮罩透明度 |
| dismissOnOutsideClick / dismissOnBackPress | true / true | 外部与系统返回关闭 |
| titleLineHeight | 18sp | 标题行距 |

| CMSDialogAction 字段 | 契约 |
| --- | --- |
| title | 动作名称 |
| onClick | 选择动作后执行，随后组件启动退场 |
| destructive | 默认 false；true 使用危险文字色 |
| emphasized | 模型保留字段；当前 ActionSheetRow 未用它改变外观 |

## 状态与边界

点击取消、外部或返回只关闭，不执行动作。点击某个动作先调用其 `onClick`，退场完成后再调用 `onDismiss`。在动作回调里直接移除弹窗会跳过退场。

这个模型没有单项 enabled 或加载状态；暂不可用的动作可以由宿主过滤，需要解释禁用原因或持续执行状态时使用自定义 Dialog。多项或长文案优先纵向；横向排布需要检查窄窗口及大字体。

动作按钮有 Button 语义。名称应能独立说明操作，不只用颜色区分删除与普通动作。

相关组件：[确认弹窗](Alert.md)、[弹出菜单](SelectMenu.md)、[底部面板](HalfModal.md)。
