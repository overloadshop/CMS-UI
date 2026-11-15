# 警告与确认 · Alert

`CMS.Presentation.Alert` 用于一个明确决定，例如是否删除记录。组件负责入场和退场，宿主持有是否显示以及确认后的业务操作。

## 最小接入

```kotlin
var visible by remember { mutableStateOf(true) }
if (visible) {
    CMS.Presentation.Alert(
        title = "删除记录？",
        message = "删除后无法恢复。",
        confirmText = "删除",
        cancelText = "取消",
        destructive = true,
        onConfirm = { /* 执行删除，不在这里移除弹窗 */ },
        onDismiss = { visible = false }
    )
}
```

## 参数契约

| 参数 | 默认值 | 用途 |
| --- | --- | --- |
| title / message | 必填 / 空字符串 | 标题和可选说明 |
| confirmText / cancelText | “好” / null | null 时没有取消按钮 |
| destructive | false | 确认按钮使用危险色 |
| onConfirm | 必填 | 确认操作，随后组件启动退场 |
| onCancel | null | 取消按钮操作，随后启动退场 |
| onDismiss | 空回调 | 退场完成后移除宿主显示状态 |
| scrimAlpha | 0.32f | 遮罩透明度 |
| dismissOnOutsideClick / dismissOnBackPress | true / true | 允许外部点击和系统返回关闭 |
| buttonLayout | Horizontal | 可改为 Vertical 容纳较长按钮文案 |
| titleLineHeight / messageLineHeight | 24sp / 20sp | 标题和说明行距 |

## 事件顺序

| 用户操作 | 回调与结果 |
| --- | --- |
| 确认 | onConfirm → 退场 → onDismiss |
| 取消按钮 | onCancel（若提供）→ 退场 → onDismiss |
| 点击外部 / 系统返回 | 允许时退场 → onDismiss；不会调用 onCancel |

`onDismiss` 必须由宿主用于移除弹窗；默认空回调并不会自动修改外部的 `visible`。需要异步执行、加载中禁用按钮或失败后保留弹窗时，使用 `CMS.Presentation.Dialog` 组织自定义内容，Alert 没有忙碌状态参数。

标题已有 heading 语义，按钮有操作语义；仍需检查长文案、大字体和窗口较窄时的排版。确认文字应说明具体动作，危险动作同时用文字说明后果。

相关组件：[动作列表](ActionSheet.md)、[底部面板](HalfModal.md)。
