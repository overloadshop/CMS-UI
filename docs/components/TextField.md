# 输入框 · Text Field

`CMS.Controls.TextField` 是 CLEAR 的标准文本输入组件，按设计规范提供大、中、小三种尺寸，以及默认、聚焦、已填、错误、禁用、成功状态。

```kotlin
var value by remember { mutableStateOf("") }
CMS.Controls.TextField(
    value = value,
    onValueChange = { value = it },
    label = "用户名",
    placeholder = "请输入用户名",
    supportingText = "支持中英文和数字",
    isError = value.isEmpty()
)
```

标准尺寸为 52dp / 44dp / 36dp，普通输入框使用 12dp 圆角，左右内边距为 16dp（小尺寸为 12dp）。聚焦时使用主题色描边与轻微光晕，错误和成功状态分别使用红色、绿色描边及辅助说明。

`enabled = false` 禁用输入；`readOnly = true` 保留复制和焦点但禁止修改。`keyboardOptions`、`keyboardActions` 和 `visualTransformation` 直接传给底层输入控件；`leadingContent` / `trailingContent` 可放置图标、清除按钮或单位。

`variant` 提供 `Plain`、`Search`、`Numeric`、`Password`、`Code`、`Stepper` 六种常用输入场景。`prefixText` / `suffixText` 用于货币、单位和公式前后缀；`showClearButton` 提供不抢焦点的清除操作；`Stepper` 会在右侧提供减一和加一。

输入状态完全由 `value` 控制。回调中可以立即更新外部状态，不会通过重新创建输入控件打断焦点和光标。

## 参数契约

| 参数 | 默认值 | 用途 |
| --- | --- | --- |
| value / onValueChange | 必填 | 受控字符串与输入回调 |
| placeholder / label / supportingText | null | 占位、标签和辅助说明 |
| isError / isSuccess | false / false | 宿主决定校验状态；避免同时设为 true |
| enabled / readOnly | true / false | 禁用与只读 |
| size / variant | Medium / Plain | 尺寸和输入场景 |
| prefixText / suffixText | null | 货币或单位 |
| showClearButton | false | 清除操作 |
| singleLine / maxLines | true / 单行 1、多行 4 | 最大行数 |
| keyboardOptions / keyboardActions | Default | 输入法类型与动作 |
| visualTransformation | None | 显示转换，Password 场景应核对遮蔽行为 |
| leadingContent / trailingContent | null | 自定义前后内容 |

## 状态管理与验证

| 情况 | 建议 |
| --- | --- |
| 输入过程中 | 即时保存 value，允许保留尚未完成的输入 |
| 提交时 | 宿主验证业务规则，再显示错误或成功 |
| 错误 | supportingText 说明如何修正，不能只改变边框颜色 |
| 数值输入 | 键盘类型只是输入提示，宿主仍须校验格式与范围 |
| 密码 | 核对遮蔽、切换可见性和提交值，不将显示转换当作数据加密 |
| 分页内文本选择 | 用 Pager.canStart 排除输入区域，保持光标和选择手势 |

标签应保持可见或可朗读，不能只依赖输入后消失的 placeholder。多行输入需要检查大字体、软键盘及滚动后焦点。此入口使用 String 状态，需要精确管理 selection / composition 时使用适合该需求的原生 Compose 输入 API。

相关：[Page](Page.md)、[触控优先级](TouchPriority.md)、[文本](Text.md)。
