package com.lindicra.cms.example

/** The editor accepts typed literal declarations, never silently ignores code changes. */
internal enum class ValueKind { Boolean, Int, Long, Float, Dp, Color, Choice, String }

internal fun stringLiteral(text: String) = "\"" + text.replace("\\", "\\\\")
    .replace("\"", "\\\"").replace("\$", "\\\$")
    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

internal fun literalText(literal: String) = literal.removeSurrounding("\"")
    .replace(Regex("""\\(["\\nrt$])""")) {
        when (it.groupValues[1]) {
            "n" -> "\n"
            "r" -> "\r"
            "t" -> "\t"
            else -> it.groupValues[1]
        }
    }

internal class PreviewCodeError(val line: Int?, detail: String) :
    IllegalArgumentException(if (line != null) "第 $line 行：$detail" else detail)

internal data class PreviewField(
    val name: String,
    val title: String,
    val kind: ValueKind,
    val default: String,
    val choices: List<Pair<String, String>> = emptyList(),
    val minimum: Double = 0.0,
    val maximum: Double = 1000.0
) {
    fun normalize(raw: String): String {
        val text = raw.trim()
        return when (kind) {
            ValueKind.String -> {
                require(Regex(""""(?:[^"\\\r\n$]|\\["\\nrt$])*"""").matches(text)) {
                    "$title：使用双引号字符串"
                }
                require(literalText(text).length <= 200) { "$title：最多 200 个字符" }
                stringLiteral(literalText(text))
            }
            ValueKind.Boolean -> {
                require(text == "true" || text == "false") { "$title：请输入 true 或 false" }
                text
            }
            ValueKind.Choice -> {
                require(choices.any { it.second == text }) { "$title：请选择声明中的枚举值" }
                text
            }
            ValueKind.Color -> {
                val match = Regex("""Color\(0x([0-9a-fA-F]{8})\)""").matchEntire(text)
                require(match != null) { "$title：使用 Color(0xAARRGGBB)" }
                "Color(0x${match.groupValues[1].uppercase()})"
            }
            else -> {
                // 容错：拖动到边缘时可能瞬时出现负数 / NaN / 越界值，
                // 这里只提取数字并夹到范围内，绝不因为瞬时值崩溃。
                val raw = Regex("""-?\d+(?:\.\d+)?""").find(text)?.value
                require(raw != null) { "$title：数值格式错误" }
                val parsed = raw!!.toDouble()
                val number = if (!parsed.isFinite()) minimum
                else parsed.coerceIn(minimum, maximum)
                when (kind) {
                    ValueKind.Int -> number.toInt().toString()
                    ValueKind.Long -> "${number.toLong()}L"
                    ValueKind.Float -> "${number.toFloat()}f"
                    else -> "${number.toFloat()}.dp"
                }
            }
        }
    }

    fun editorText(value: String): String = when (kind) {
        ValueKind.String -> literalText(value)
        ValueKind.Color -> "#" + value.removePrefix("Color(0x").removeSuffix(")")
        ValueKind.Dp -> value.removeSuffix(".dp")
        ValueKind.Float -> value.removeSuffix("f")
        ValueKind.Long -> value.removeSuffix("L")
        else -> value
    }

    fun fromEditor(text: String): String = normalize(when (kind) {
        ValueKind.String -> stringLiteral(text)
        ValueKind.Color -> {
            val hex = text.trim().removePrefix("#").removePrefix("0x")
            if (text.trim().startsWith("Color(")) text.trim()
            else "Color(0x${if (hex.length == 6) "FF$hex" else hex})"
        }
        ValueKind.Dp -> text.trim().removeSuffix(".dp") + ".dp"
        ValueKind.Float -> text.trim().removeSuffix("f") + "f"
        ValueKind.Long -> text.trim().removeSuffix("L") + "L"
        else -> text.trim()
    })
}

internal class PreviewDefinition(val page: DemoPage) {
    private fun bool(name: String, title: String, value: Boolean) =
        PreviewField(name, title, ValueKind.Boolean, value.toString())
    private fun text(name: String, title: String, value: String) =
        PreviewField(name, title, ValueKind.String, stringLiteral(value))
    private fun dp(name: String, title: String, value: Int, min: Double = 0.0, max: Double = 400.0) =
        PreviewField(name, title, ValueKind.Dp, "$value.dp", minimum = min, maximum = max)
    private fun int(name: String, title: String, value: Int, min: Double, max: Double) =
        PreviewField(name, title, ValueKind.Int, "$value", minimum = min, maximum = max)
    private fun float(name: String, title: String, value: Float, min: Double, max: Double) =
        PreviewField(name, title, ValueKind.Float, "${value}f", minimum = min, maximum = max)
    private fun choice(name: String, title: String, type: String, labels: List<String>,
        values: List<String>, selected: Int = 0) =
        PreviewField(name, title, ValueKind.Choice, "$type.${values[selected]}",
            labels.zip(values.map { "$type.$it" }))

    val fields: List<PreviewField> = listOf(
        int("appearance", "外观", 0, 0.0, 2.0).copy(
            choices = listOf("跟随系统" to "0", "浅色" to "1", "深色" to "2")),
        bool("advanced", "高级材质", true),
        int("clarity", "通透度", 2, 0.0, 2.0).copy(
            choices = listOf("毛玻璃" to "0", "舒适玻璃" to "1", "真实玻璃" to "2")),
        bool("hapticsEnabled", "触感反馈", true),
        bool("previewTopBar", "预览顶栏",
            page != DemoPage.GlassButton && page != DemoPage.ContentSource && page != DemoPage.TabBar),
        PreviewField("themeColor", "主题色", ValueKind.Color, "Color(0xFF3F78D1)"),
        PreviewField("controlColor", "控件色", ValueKind.Color, "Color(0xFF5F9BFF)")
    ) + when (page) {
        DemoPage.CapsuleNavigation -> listOf(
            int("itemCount", "选项数量", 2, 2.0, 4.0),
            int("selectedIndex", "选中项", 0, 0.0, 3.0),
            text("labels", "选项文字（竖线分隔）", "计算|工具|历史|设置"),
            choice("variant", "外框样式", "CMSCapsuleVariant",
                listOf("底板", "描边"), listOf("Filled", "Outlined")),
            bool("enabled", "允许交互", true),
            bool("draggable", "允许拖拽", true),
            bool("liquidGlass", "液态玻璃", false),
            bool("liquidMotion", "玻璃形变", true),
            dp("height", "高度", 44, 36.0, 80.0),
            dp("outerRadius", "外框圆角", 22, 0.0, 40.0),
            dp("indicatorRadius", "指示器圆角", 18, 0.0, 36.0),
            dp("contentInset", "内边距", 4, 0.0, 16.0),
            dp("borderWidth", "描边宽度", 1, 0.0, 4.0).copy(default = "1.5.dp"),
            int("fontSize", "字号", 14, 10.0, 24.0),
            bool("customColors", "自定义颜色", false),
            PreviewField("selectedColor", "选中文字", ValueKind.Color, "Color(0xFFF3F5F7)"),
            PreviewField("unselectedColor", "未选文字", ValueKind.Color, "Color(0xFF8C95A3)"),
            PreviewField("containerColor", "底板色", ValueKind.Color, "Color(0x0FFFFFFF)"),
            PreviewField("indicatorColor", "指示器色", ValueKind.Color, "Color(0x26FFFFFF)"))
        DemoPage.TabNavigation -> listOf(
            int("itemCount", "分类数量", 4, 2.0, 6.0),
            int("selectedIndex", "选中项", 1, 0.0, 5.0),
            text("labels", "分类文字（竖线分隔）", "推荐|排行|歌单|歌手|专辑|收藏"),
            bool("enabled", "允许交互", true),
            dp("height", "行高", 48, 36.0, 80.0),
            int("fontSize", "字号", 14, 10.0, 24.0),
            dp("indicatorHeight", "下划线高度", 5, 0.0, 12.0),
            dp("indicatorRadius", "下划线圆角", 2, 0.0, 6.0),
            dp("indicatorGap", "文字与下划线间距", 3, 0.0, 24.0),
            float("indicatorWidthFraction", "下划线占每项宽度", 0.6f, 0.1, 1.0),
            bool("customColors", "自定义文字颜色", false),
            PreviewField("selectedColor", "选中文字", ValueKind.Color, "Color(0xFFF3F5F7)"),
            PreviewField("unselectedColor", "未选文字", ValueKind.Color, "Color(0xFF8C95A3)"))
        DemoPage.BackHistory -> listOf(
            int("pageCount", "示例页面总数", 4, 2.0, 8.0),
            bool("includeHostHistory", "包含宿主已访问的页面", true))
        DemoPage.TouchPriority -> listOf(
            float("horizontalPriority", "横向优先权重", 1f, 0.1, 8.0),
            float("verticalPriority", "纵向优先权重", 1f, 0.1, 8.0),
            float("directionRatio", "方向优势比例", 1.2f, 1.0, 8.0),
            float("slopMultiplier", "触发距离倍数", 1f, 0.1, 8.0),
            PreviewField("historyMillis", "最近运动窗口（毫秒）", ValueKind.Long, "80L",
                minimum = 16.0, maximum = 300.0),
            bool("allowAxisChange", "同一次触控允许换向", true),
            bool("interruptOnDown", "按下立即打断动画", true),
            bool("glassDragPriority", "优先拖拽拉伸玻璃（关闭后滚动页面）", true),
            bool("respectConsumedGestures", "尊重子控件已接管的手势", false))
        DemoPage.GlassFusion -> listOf(
            bool("fusionEnabled", "启用融合", true),
            dp("glassWidth", "玻璃宽度", 96, 36.0, 120.0),
            dp("glassHeight", "玻璃高度", 72, 36.0, 120.0),
            dp("cornerRadius", "玻璃圆角", 24, 0.0, 60.0),
            dp("gap", "靠近时的间距", 2, 0.0, 24.0),
            dp("separatedGap", "分开时的间距", 36, 24.0, 64.0),
            dp("mergeGap", "融合距离", 16, 0.0, 24.0),
            float("gravityStrength", "吸引强度", 1f, 0.0, 4.0),
            dp("caseHeight", "案例视口高度", 380, 260.0, 640.0))
        DemoPage.GlassButton -> listOf(
            dp("width", "宽度", 72, 24.0), dp("height", "高度", 44, 24.0),
            dp("cornerRadius", "圆角", 20),
            float("tintAlpha", "染色透明度", 0.35f, 0.0, 1.0),
            float("shadowStrength", "阴影强度", 2f, 0.0, 5.0))
        DemoPage.BackButton -> listOf(bool("exclusiveTouchGesture", "独占静止按压", true))
        DemoPage.TabBar -> listOf(
            int("itemCount", "图标数量", 4, 2.0, 5.0),
            choice("tabPlacement", "Tab 位置", "CMSTabPlacement",
                listOf("底部", "顶部"), listOf("Bottom", "Top"), 0),
            choice("tabForm", "Tab 形态", "CMSTabForm",
                listOf("自动", "贴边", "悬浮", "侧边"), listOf("Auto", "Edge", "Floating", "Rail"), 0),
            int("selectedIndex", "当前页面", 0, 0.0, 3.0),
            dp("barHeight", "底栏高度", 52, 44.0, 100.0),
            dp("indicatorInset", "指示标等距内间距", 4, 0.0, 16.0),
            dp("pressExpansion", "按下扩张", 20, 0.0, 30.0),
            dp("horizontalInset", "屏幕两侧距离", 20, 0.0, 60.0),
            dp("bottomInset", "底部安全区上方距离", 12, 0.0, 60.0),
            dp("iconSize", "图标大小", 24, 16.0, 32.0),
            dp("itemWidth", "每格基准宽", 88, 60.0, 120.0),
            dp("itemOverlap", "相邻项重叠", 10, 0.0, 20.0),
            bool("fillWidth", "铺满宽度", false),
            bool("showLabels", "显示文字", true),
            bool("liquidMotion", "液态拖动形变", true),
            PreviewField("indicatorColor", "浅色指示标颜色", ValueKind.Color, "Color(0x388E8E93)"),
            PreviewField("darkIndicatorColor", "深色指示标颜色", ValueKind.Color, "Color(0xB31C1C1E)"))
        DemoPage.ContentSource -> listOf(
            dp("width", "宽度", 164, 24.0), dp("height", "高度", 104, 24.0),
            dp("cornerRadius", "圆角", 26),
            float("tintAlpha", "染色透明度", 0.08f, 0.0, 1.0))
        DemoPage.SettingsRows -> listOf(
            text("navTitle", "导航标题", "导航行"),
            text("navDesc", "导航说明", "点击触发轻触反馈"),
            text("toggleTitle", "开关标题", "开关行"),
            text("toggleDesc", "开关说明", "点击整行或开关切换"),
            text("menuTitle", "菜单标题", "菜单行"),
            text("menuDesc", "菜单说明", "点击或长按整行选择"),
            text("secondTitle", "第二组标题", "第二组"),
            text("secondDesc", "第二组说明", "组间隙由列表统一控制"),
            bool("initialChecked", "开关状态", true),
            int("selectedIndex", "当前选项", 0, 0.0, 2.0).copy(
                choices = listOf("选项 A" to "0", "选项 B" to "1", "选项 C" to "2")),
            dp("sectionPadding", "卡片上下留白", 0, 0.0, 32.0),
            dp("groupSpacing", "组间隙", 12, 0.0, 32.0),
            dp("cornerRadius", "卡片圆角", 20, 0.0, 36.0),
            dp("rowHeight", "最小行高（含内边距）", 48, 32.0, 120.0),
            dp("dividerPadding", "分割线左右缩进", 16, 0.0, 64.0),
            int("titleSize", "标题字号", 15, 11.0, 20.0),
            int("descSize", "说明字号", 11, 9.0, 16.0),
            bool("showDividers", "显示分割线", true),
            dp("rowPaddingH", "行左右内边距", 16, 0.0, 32.0),
            dp("rowPaddingV", "行上下内边距", 10, 0.0, 24.0),
            dp("highlightRadius", "按下圆角", 0, 0.0, 24.0),
            bool("showChevron", "显示跳转图标", true),
            bool("customChevron", "使用自定义图标", false),
            text("chevronText", "自定义图标文字", "↗"),
            bool("customColors", "自定义卡片与文字配色", false),
            PreviewField("containerColor", "卡片色", ValueKind.Color, "Color(0xFFF2F2F7)"),
            PreviewField("titleColor", "标题色", ValueKind.Color, "Color(0xFF202124)"),
            PreviewField("descColor", "说明色", ValueKind.Color, "Color(0xFF737B89)"),
            PreviewField("pressedColor", "整行按压色", ValueKind.Color, "Color(0x1F8E8E93)"),
            PreviewField("separatorColor", "分割线色", ValueKind.Color, "Color(0x338E8E93)"))
        DemoPage.Switch -> listOf(bool("initialChecked", "开关状态", true),
            PreviewField("trackOffColor", "关闭轨道色", ValueKind.Color, "Color(0xFF737B89)"),
            PreviewField("thumbColor", "滑块色", ValueKind.Color, "Color(0xFFFFFFFF)"))
        DemoPage.Progress -> listOf(
            float("initialValue", "当前值", 0.42f, 0.0, 1.0),
            choice("thumbStyle", "滑块样式", "CMSSliderThumbStyle",
                listOf("无", "空心", "实心"), listOf("None", "Hollow", "Solid"), 1),
            int("steps", "档位（0 为连续）", 0, 0.0, 100.0),
            bool("readOnly", "只读", false), bool("thicknessAnimation", "粗细变化", true),
            bool("dampingEnabled", "阻尼跟随", true), bool("overscrollEnabled", "越界回弹", true),
            dp("trackThickness", "轨道粗细", 6, 2.0, 12.0),
            dp("pressedThickness", "按下加粗度", 13, 6.0, 24.0),
            float("dampingStrength", "阻尼强度", 1f, 0.0, 2.0),
            bool("readOnlyDim", "只读变灰", true))
        DemoPage.SelectMenu -> listOf(
            bool("dimBackground", "背景压暗", false),
            float("dimAmount", "压暗强度", 0.18f, 0.0, 0.8),
            int("selectedIndex", "当前选项", 0, 0.0, 2.0))
        DemoPage.Scroll -> listOf(
            bool("springOverscroll", "弹簧回弹", true),
            bool("showScrollbar", "显示滚动条", true),
            choice("scrollbarStyle", "滚动条样式", "CMSScrollbarStyle",
                listOf("经典", "首字母索引", "分段索引"), listOf("Classic", "Alphabet", "Sections")),
            choice("scrollbarIndexLayout", "索引排列", "CMSScrollbarIndexLayout",
                listOf("分散", "顶部聚集"), listOf("Distributed", "TopPacked"), 1),
            bool("scrollbarInteractive", "拖动定位", true),
            bool("scrollbarPressThickening", "按下加粗", true))
        DemoPage.Page -> listOf(
            text("title", "顶栏标题", "页面容器"),
            bool("glassEnabled", "顶栏玻璃", true),
            dp("topBarReserve", "顶栏预留（含内容间距）", 64, 52.0, 160.0),
            dp("bottomReserve", "底部额外预留", 24, 0.0, 200.0),
            dp("maxContentWidth", "最大内容宽度", 680, 240.0, 1200.0),
            int("rowCount", "内容行数", 24, 1.0, 60.0))
        DemoPage.Swiper -> listOf(
            int("itemCount", "页数", 3, 1.0, 50.0),
            choice("direction", "方向", "CMSCarouselDirection",
                listOf("横向", "纵向"), listOf("Horizontal", "Vertical")),
            bool("autoPlay", "自动播放", false),
            PreviewField("autoPlayInterval", "播放间隔（毫秒）", ValueKind.Long,
                "3000L", minimum = 500.0, maximum = 60000.0),
            bool("showArrows", "切换箭头", false), dp("arrowSize", "按钮大小", 28, 20.0, 80.0),
            choice("edgeOverflow", "越界回弹", "CMSCarouselOverscroll",
                listOf("关闭", "开启"), listOf("None", "Spring"), 1),
            bool("snapBack", "页面吸附", true), bool("freeMode", "自由滑动", false),
            float("pageWidthFraction", "每页占比", 1f, 0.1, 1.0),
            dp("pageHeight", "高度", 190, 60.0, 400.0),
            dp("pageSpacing", "页面间距", 8, 0.0, 80.0), dp("clipRadius", "圆角", 12),
            choice("indicatorStyle", "指示类型", "CMSCarouselIndicator",
                listOf("无", "圆点", "数字"), listOf("None", "DotIndicator", "DigitIndicator"), 1),
            int("indicatorVisibleCount", "可见圆点", 5, 1.0, 15.0),
            dp("indicatorSize", "圆点大小", 6, 2.0, 20.0),
            dp("indicatorActiveWidth", "选中长度", 14, 2.0, 60.0),
            dp("indicatorBottomInset", "底部／右侧距离", 8, 0.0, 80.0),
            bool("indicatorInteractive", "点按切换", true))
        DemoPage.HalfModal -> listOf(float("heightFraction", "面板高度占比", 0.86f, 0.2, 0.95))
        DemoPage.Haptics -> listOf(choice("kind", "反馈强度", "CMSHapticKind",
            listOf("轻触", "按键", "长按"), listOf("Tick", "Click", "LongPress")))
        DemoPage.Appearance -> emptyList()
        DemoPage.Text -> listOf(
            text("content", "文本内容", "长文本会在达到行数上限后被自动截断，用来演示 overflow 处理。"),
            choice("preset", "字号阶梯", "CMSTextPreset",
                listOf("跟随字号", "说明 Caption", "正文 Body", "标题 Title"),
                listOf("Auto", "Caption", "Body", "Title"), 0),
            int("fontSize", "字号（Auto 时生效）", 15, 10.0, 32.0),
            choice("weight", "字重", "FontWeight",
                listOf("常规", "中等", "半粗", "粗体"),
                listOf("Normal", "Medium", "SemiBold", "Bold"), 0),
            int("maxLines", "最大行数", 2, 1.0, 8.0),
            choice("overflow", "溢出处理", "TextOverflow",
                listOf("裁切", "省略号"), listOf("Clip", "Ellipsis"), 1),
            bool("respectFontScale", "跟随系统字号", true),
            int("lineHeight", "行高", 22, 12.0, 44.0))
        DemoPage.CodeBlock -> listOf(
            dp("cornerRadius", "圆角", 14, 0.0, 28.0),
            dp("maxHeight", "最大高度（0 为无限）", 220, 0.0, 600.0),
            bool("wrap", "自动换行", false),
            bool("highlight", "语法高亮", true),
            bool("showHeader", "显示头行", true),
            bool("edgeFeedback", "触边反馈", true))
        DemoPage.Table -> listOf(
            dp("cornerRadius", "圆角", 14, 0.0, 28.0),
            dp("maxHeight", "最大高度（0 为无限）", 0, 0.0, 600.0),
            dp("minColumnWidth", "列最小宽度", 120, 80.0, 240.0),
            bool("striped", "斑马纹", true),
            bool("showDividers", "行分隔线", true),
            bool("edgeFeedback", "触边反馈", true))
        DemoPage.Tag -> listOf(
            text("content", "标签文字", "状态标签"),
            choice("style", "样式", "CMSTagStyle",
                listOf("描边", "实心"), listOf("Outline", "Filled"), 0),
            int("fontSize", "字号", 11, 9.0, 20.0),
            dp("cornerRadius", "圆角", 6, 0.0, 24.0),
            dp("horizontalPadding", "左右内边距", 7, 2.0, 20.0),
            dp("verticalPadding", "上下内边距", 3, 0.0, 12.0))
        DemoPage.Link -> listOf(
            text("content", "链接文字", "查看完整文档 →"),
            int("fontSize", "字号", 15, 10.0, 28.0),
            bool("underline", "下划线", true),
            choice("weight", "字重", "FontWeight",
                listOf("常规", "中等", "半粗"), listOf("Normal", "Medium", "SemiBold"), 1),
            int("maxLines", "最大行数", 1, 1.0, 4.0))
        DemoPage.Alert -> listOf(
            text("title", "标题", "确认操作"),
            text("message", "说明", "这段说明文字会随内容自动换行，行距由标题与说明分别控制。"),
            text("confirmText", "确认按钮", "确定"),
            bool("showCancel", "显示取消按钮", true),
            bool("destructive", "危险操作", false),
            float("scrimAlpha", "遮罩不透明度", 0.32f, 0.0, 1.0),
            bool("dismissOnOutsideClick", "点击外部关闭", true),
            bool("dismissOnBackPress", "返回键关闭", true),
            choice("buttonLayout", "按钮排布", "CMSDialogButtonLayout",
                listOf("横向", "纵向"), listOf("Horizontal", "Vertical"), 0),
            int("titleLineHeight", "标题行距", 24, 16.0, 40.0),
            int("messageLineHeight", "说明行距", 20, 12.0, 36.0))
        DemoPage.ActionSheet -> listOf(
            text("sheetTitle", "标题", "选择操作"),
            text("actionOne", "动作一", "分享"),
            text("actionTwo", "动作二", "收藏"),
            text("actionThree", "动作三", "删除"),
            bool("destructiveAction", "第三项为危险操作", true),
            text("cancelText", "取消按钮", "取消"),
            float("scrimAlpha", "遮罩不透明度", 0.32f, 0.0, 1.0),
            bool("dismissOnOutsideClick", "点击外部关闭", true),
            bool("dismissOnBackPress", "返回键关闭", true),
            choice("actionLayout", "动作排布", "CMSDialogButtonLayout",
                listOf("纵向", "横向"), listOf("Vertical", "Horizontal"), 0))
        DemoPage.WheelPicker -> listOf(
            dp("itemHeight", "行高", 36, 24.0, 64.0),
            int("visibleCount", "可见行数", 5, 3.0, 7.0),
            bool("loop", "循环滚动", true),
            float("frictionMultiplier", "减速系数", 1.4f, 0.2, 5.0),
            float("dampingRatio", "吸附阻尼", 1f, 0.8, 1.0),
            float("stiffness", "回弹刚度", 500f, 50.0, 2000.0),
            dp("wheelRadius", "滚筒半径", 110, 60.0, 300.0),
            bool("hapticEnabled", "逐项触觉", true),
            int("selectedIndex", "当前项", 2, 0.0, 4.0))
        DemoPage.DatePicker -> listOf(
            text("selectedDate", "选中日期（ISO）", "2026-11-10"),
            choice("pickerMode", "选择方式", "CMSDatePickerMode",
                listOf("日历", "滚筒", "输入"), listOf("Calendar", "Wheel", "Input"), 0),
            bool("showModeSwitch", "显示方式切换", false),
            choice("weekStart", "周起始", "CMSWeekStart",
                listOf("周一", "周日"), listOf("Monday", "Sunday"), 0),
            bool("showToday", "显示今天按钮", true),
            int("rangeMonths", "可选范围（±月）", 12, 1.0, 36.0))
        DemoPage.TimePicker -> listOf(
            int("hour", "小时", 9, 0.0, 23.0),
            int("minute", "分钟", 30, 0.0, 59.0),
            bool("is24Hour", "24 小时制", true),
            int("minuteStep", "分钟步长", 5, 1.0, 30.0),
            dp("itemHeight", "滚筒行高", 36, 24.0, 64.0),
            int("visibleCount", "可见行数", 5, 3.0, 7.0),
            bool("loop", "循环滚动", false))
        DemoPage.ColorPicker -> listOf(
            choice("pickerMode", "选择方式", "CMSColorPickerMode",
                listOf("色谱", "色轮", "RGB 滑杆", "色板"),
                listOf("Spectrum", "Wheel", "Sliders", "Palette"), 0),
            choice("palette", "色板", "CMSColorPalette",
                listOf("鲜明", "柔和", "灰阶"), listOf("Vivid", "Pastel", "Neutral"), 0),
            bool("showModeSwitch", "显示方式切换", true),
            bool("showInputs", "显示颜色数值输入", true),
            bool("showPresets", "显示预设色板", true),
            bool("showAlpha", "显示透明度", true),
            int("recentLimit", "最近使用数量", 6, 0.0, 12.0),
            dp("panelHeight", "饱和度 / 亮度面板高度", 150, 80.0, 260.0))
        DemoPage.TextField -> listOf(
            text("inputValue", "输入内容", ""),
            text("label", "标签", "用户名"),
            text("placeholder", "占位提示", "请输入内容"),
            text("supportingText", "辅助说明", "支持中英文和数字"),
            choice("variant", "输入类型", "CMSTextFieldVariant",
                listOf("普通", "搜索", "数值", "密码", "代码", "步进"),
                listOf("Plain", "Search", "Numeric", "Password", "Code", "Stepper"), 0),
            text("prefixText", "前缀", ""),
            text("suffixText", "后缀 / 单位", ""),
            bool("showClearButton", "显示清除按钮", true),
            bool("isError", "错误状态", false),
            bool("isSuccess", "成功状态", false),
            bool("enabled", "允许输入", true),
            choice("fieldSize", "尺寸", "CMSTextFieldSize",
                listOf("大", "中", "小"), listOf("Large", "Medium", "Small"), 1))
        DemoPage.VStack -> listOf(
            dp("spacing", "间距", 10, 0.0, 40.0),
            choice("horizontalAlignment", "横向对齐", "Alignment",
                listOf("左", "居中", "右"), listOf("Start", "CenterHorizontally", "End"), 0),
            dp("minWidth", "最小宽度", 0, 0.0, 400.0),
            dp("maxWidth", "最大宽度", 320, 0.0, 400.0),
            dp("minHeight", "最小高度", 0, 0.0, 400.0),
            dp("maxHeight", "最大高度", 240, 0.0, 400.0))
        DemoPage.HStack -> listOf(
            dp("spacing", "间距", 10, 0.0, 40.0),
            choice("verticalAlignment", "纵向对齐", "Alignment",
                listOf("顶", "居中", "底"), listOf("Top", "CenterVertically", "Bottom"), 1),
            dp("minWidth", "最小宽度", 0, 0.0, 400.0),
            dp("maxWidth", "最大宽度", 320, 0.0, 400.0),
            dp("minHeight", "最小高度", 0, 0.0, 400.0),
            dp("maxHeight", "最大高度", 120, 0.0, 400.0))
        DemoPage.ZStack -> listOf(
            choice("alignment", "叠放对齐", "Alignment",
                listOf("左上", "居中", "右下"), listOf("TopStart", "Center", "BottomEnd"), 1),
            dp("minWidth", "最小宽度", 180, 0.0, 400.0),
            dp("maxWidth", "最大宽度", 320, 0.0, 400.0),
            dp("minHeight", "最小高度", 120, 0.0, 400.0),
            dp("maxHeight", "最大高度", 240, 0.0, 400.0))
        DemoPage.Group -> listOf(
            dp("spacing", "组内间距", 8, 0.0, 32.0),
            choice("horizontalAlignment", "横向对齐", "Alignment",
                listOf("左", "居中", "右"), listOf("Start", "CenterHorizontally", "End"), 0),
            dp("minWidth", "最小宽度", 0, 0.0, 400.0),
            dp("maxWidth", "最大宽度", 320, 0.0, 400.0),
            dp("minHeight", "最小高度", 0, 0.0, 400.0),
            dp("maxHeight", "最大高度", 240, 0.0, 400.0))
    }

    fun defaults() = fields.associate { it.name to it.normalize(it.default) }

    /** Both exported code and on-device preview consume the same validated declarations. */
    fun code(values: Map<String, String>): String {
        val declarations = fields.joinToString("\n") {
            "    val ${it.name} = ${it.normalize(values.getValue(it.name))}"
        }
        return """import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.lindicra.cms.*
import com.lindicra.cms.ui.theme.*
import com.lindicra.cms.example.R
import kotlinx.coroutines.launch

@Composable
fun ${page.exampleName}Example() {
$declarations
    val haze = remember { HazeState() }
    CMS.Appearance.Theme(
        darkTheme = when (appearance) {
            1 -> false
            2 -> true
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        },
        glassMaterial = GlassMaterial(advanced, clarity),
        hazState = haze,
        hapticsEnabled = hapticsEnabled,
        accentColor = themeColor
    ) {
        val haptic = rememberCMSHaptic()
${framedBody().prependIndent("        ")}
    }
}
"""
    }

    fun parse(source: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        var skeleton = source.replace("\r\n", "\n")
        fields.forEach { field ->
            val pattern = Regex("""(?m)^    val ${field.name} = ([^\n]+)$""")
            val matches = pattern.findAll(skeleton).toList()
            if (matches.size != 1) {
                val match = matches.lastOrNull()
                val line = match?.let { skeleton.take(it.range.first).count { char -> char == '\n' } + 1 }
                throw PreviewCodeError(line, "请保留唯一的 val ${field.name} 参数声明")
            }
            val line = skeleton.take(matches.single().range.first).count { it == '\n' } + 1
            values[field.name] = try {
                field.normalize(matches.single().groupValues[1])
            } catch (error: IllegalArgumentException) {
                throw PreviewCodeError(line, error.message ?: "参数格式错误")
            }
            skeleton = pattern.replace(skeleton) { "    val ${field.name} = <${field.name}>" }
        }
        var expected = code(defaults())
        fields.forEach { field ->
            expected = Regex("""(?m)^    val ${field.name} = ([^\n]+)$""")
                .replace(expected) { "    val ${field.name} = <${field.name}>" }
        }
        if (skeleton.trimEnd() != expected.trimEnd()) {
            val actualLines = skeleton.trimEnd().lines()
            val expectedLines = expected.trimEnd().lines()
            val line = (0 until maxOf(actualLines.size, expectedLines.size))
                .first { actualLines.getOrNull(it) != expectedLines.getOrNull(it) } + 1
            throw PreviewCodeError(line,
                "支持修改顶部 val 参数；函数体、导入与回调需保留。")
        }
        try {
            validateConstraints(values)
        } catch (error: IllegalArgumentException) {
            val line = source.lines().indexOfFirst { it.startsWith("    val indicatorActiveWidth = ") }
                .takeIf { it >= 0 }?.plus(1)
            throw PreviewCodeError(line, error.message ?: "参数组合无效")
        }
        return values
    }

    fun validateConstraints(values: Map<String, String>) {
        if (page == DemoPage.Swiper) {
            val size = values.getValue("indicatorSize").removeSuffix(".dp").toFloat()
            val active = values.getValue("indicatorActiveWidth").removeSuffix(".dp").toFloat()
            require(active >= size) { "选中长度不能小于圆点大小" }
        }
        if (page == DemoPage.DatePicker) {
            val raw = literalText(values.getValue("selectedDate"))
            require(runCatching { java.time.LocalDate.parse(raw) }.isSuccess) {
                "选中日期需使用 YYYY-MM-DD 格式"
            }
        }
    }

    private fun image() = """Image(
    painterResource(R.drawable.liquid_light_preview),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxSize()
)"""

    private fun framedBody(): String {
        if (page == DemoPage.BackHistory) return body()
        if (page == DemoPage.Page) return """val statusInset = with(androidx.compose.ui.platform.LocalDensity.current) {
    WindowInsets.statusBars.getTop(this).toDp()
}
${body()}"""
        val viewport = page == DemoPage.Scroll || page == DemoPage.GlassButton ||
            page == DemoPage.ContentSource || page == DemoPage.TabBar || page == DemoPage.TouchPriority ||
            page == DemoPage.CapsuleNavigation
        val demo = if (viewport) body() else """Box(
    Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        .padding(top = previewTopInset).padding(horizontal = 16.dp),
    contentAlignment = androidx.compose.ui.Alignment.Center
) {
${body().prependIndent("    ")}
}"""
        return """val statusInset = with(androidx.compose.ui.platform.LocalDensity.current) {
    WindowInsets.statusBars.getTop(this).toDp()
}
val previewTopInset = statusInset + if (previewTopBar) CM_SECONDARY_TOP_BAR_RESERVE else 0.dp
Box(Modifier.fillMaxSize().background(PageBackground)) {
    Box(Modifier.fillMaxSize()${if (page == DemoPage.TabBar || page == DemoPage.GlassFusion || page == DemoPage.TouchPriority || page == DemoPage.CapsuleNavigation) "" else ".hazeSource(haze)"}) {
${demo.prependIndent("        ")}
    }
    if (previewTopBar) CMS.Navigation.SecondaryTopBar(
        title = "${page.title} · 预览", topInset = statusInset, hazeState = haze,
        onBack = { /* 宿主返回操作 */ }
    )
}"""
    }

    private fun body(): String = when (page) {
        DemoPage.BackHistory -> backHistorySnippet()
        DemoPage.TouchPriority -> touchPrioritySnippet()
        DemoPage.GlassFusion -> glassFusionSnippet()
        DemoPage.CapsuleNavigation -> """val options = List(itemCount) { labels.split('|').getOrNull(it)?.takeIf { label -> label.isNotBlank() } ?: "选项 ${'$'}{it + 1}" }
var selected by remember(selectedIndex, itemCount) { mutableIntStateOf(selectedIndex.coerceIn(options.indices)) }
var source by remember { mutableStateOf<android.view.ViewGroup?>(null) }
Box(Modifier.fillMaxSize()) {
    if (liquidGlass) {
        CMS.Content.Source(Modifier.fillMaxSize(), hazeState = haze, onSourceReady = { source = it }) {
            CMS.Content.ScrollView(Modifier.fillMaxSize(), showScrollbar = false) {
                Spacer(Modifier.height(previewTopInset))
                Column(Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(12) { index ->
                        CMSSettingsCard {
                            Text("${'$'}{options[index % options.size]} · ${'$'}{index + 1}", color = controlColor)
                        }
                    }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
    Box(Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        .padding(top = previewTopInset).padding(horizontal = 16.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        CMS.Navigation.CapsuleNavigation(
            options, selected, { selected = it }, variant = variant, enabled = enabled,
            height = height, outerRadius = outerRadius, indicatorRadius = indicatorRadius,
            contentInset = contentInset, borderWidth = borderWidth, fontSize = fontSize.sp,
            selectedColor = if (customColors) selectedColor else PrimaryText,
            unselectedColor = if (customColors) unselectedColor else SecondaryText,
            containerColor = if (customColors) containerColor else Color.Unspecified,
            indicatorColor = if (customColors) indicatorColor else Color.Unspecified,
            draggable = draggable, liquidGlass = liquidGlass, liquidMotion = liquidMotion,
            samplingSource = source, hazeState = haze
        )
    }
}"""
        DemoPage.TabNavigation -> """val options = List(itemCount) { labels.split('|').getOrNull(it)?.takeIf { label -> label.isNotBlank() } ?: "分类 ${'$'}{it + 1}" }
var selected by remember(selectedIndex, itemCount) { mutableIntStateOf(selectedIndex.coerceIn(options.indices)) }
CMS.Navigation.TabNavigation(
    options, selected, { selected = it }, enabled = enabled, height = height, fontSize = fontSize.sp,
    selectedColor = if (customColors) selectedColor else LocalCMSAccentColor.current,
    unselectedColor = if (customColors) unselectedColor else SecondaryText,
    indicatorColor = controlColor, indicatorHeight = indicatorHeight,
    indicatorRadius = indicatorRadius, indicatorWidthFraction = indicatorWidthFraction,
    indicatorGap = indicatorGap
)"""
        DemoPage.Page -> """CMS.Content.Page(
    topInset = statusInset,
    modifier = Modifier.fillMaxSize(),
    glassEnabled = glassEnabled,
    topBarReserve = if (previewTopBar) topBarReserve else 12.dp,
    bottomReserve = bottomReserve + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    maxContentWidth = maxContentWidth,
    hazeState = haze,
    topBar = { pageHaze ->
        if (previewTopBar) CMS.Navigation.SecondaryTopBar(
            title = title, topInset = statusInset, hazeState = pageHaze,
            glassEnabled = glassEnabled, onBack = { /* 宿主返回操作 */ }
        )
    }
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CMS.Content.SectionTitle("页面内容")
        CMS.Content.Section {
            repeat(rowCount) { index ->
                CMS.Content.ListRow(
                    "内容 ${'$'}{index + 1}", "向上滚动可体验顶栏玻璃",
                    showChevron = false
                ) { haptic(CMSHapticKind.Tick) }
            }
        }
    }
}"""
        DemoPage.TabBar -> """var selected by remember(selectedIndex) { mutableIntStateOf(selectedIndex.coerceIn(0, itemCount - 1)) }
var source by remember { mutableStateOf<android.view.ViewGroup?>(null) }
val tabs = CMSGlassTabBarDefaults.items.take(itemCount)
Box(Modifier.fillMaxSize()) {
    CMS.Content.Source(Modifier.fillMaxSize(), hazeState = haze, onSourceReady = { source = it }) {
        CMS.Content.ScrollView(Modifier.fillMaxSize(), showScrollbar = false) {
            Spacer(Modifier.height(previewTopInset))
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(tabs[selected].label, fontSize = 40.sp, color = controlColor)
                repeat(10) { index ->
                    CMSSettingsCard { Text("${'$'}{tabs[selected].label} · ${'$'}{index + 1}", color = PrimaryText) }
                }
            }
            Spacer(Modifier.height(barHeight + bottomInset + 20.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
    Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        .padding(horizontal = horizontalInset, vertical = bottomInset)) {
        CMS.Navigation.TabView(
            items = tabs, selectedIndex = selected, onSelectedChange = { selected = it },
            modifier = Modifier, height = barHeight,
            indicatorInset = indicatorInset, pressExpansion = pressExpansion,
            iconSize = iconSize, showLabels = showLabels, liquidMotion = liquidMotion,
            itemWidth = itemWidth, itemOverlap = itemOverlap, fillWidth = fillWidth,
            selectedColor = controlColor,
            indicatorColor = if (LocalClearIsDark.current) darkIndicatorColor else indicatorColor,
            samplingSource = source, hazeState = haze
        )
    }
}"""
        DemoPage.GlassButton, DemoPage.ContentSource -> """var source by remember { mutableStateOf<android.view.ViewGroup?>(null) }
Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
    CMS.Content.Source(Modifier.fillMaxSize(), hazeState = haze, onSourceReady = { source = it }) {
${image().prependIndent("        ")}
    }
    Box(Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        .padding(top = previewTopInset),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
    source?.let { samplingSource ->
        CMS.Controls.GlassButton(
            onClick = { haptic(CMSHapticKind.Tick) },
            width = width, height = height, cornerRadius = cornerRadius,
            glassTint = controlColor, glassTintAlpha = tintAlpha,
            ${if (page == DemoPage.GlassButton) "shadowStrength = shadowStrength," else ""}
            sampleContent = true, samplingSource = samplingSource,
            hazeState = haze, exclusiveTouchGesture = true
        ) { color -> Text("玻璃", color = color) }
    }
    }
}"""
        DemoPage.BackButton -> """CMS.Navigation.BackButton(
    onClick = { /* 宿主返回操作 */ },
    iconColor = controlColor,
    exclusiveTouchGesture = exclusiveTouchGesture
)
// 长按返回历史：用 CMS.Interaction.BackHistory(history) 提供 CMSBackHistory。
// 完整接入见「交互 → 返回历史」。"""
        DemoPage.SettingsRows -> """var checked by remember(initialChecked) { mutableStateOf(initialChecked) }
var selected by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }
var anchor by remember { mutableStateOf(IntRect.Zero) }
var visible by remember { mutableStateOf(false) }
val options = listOf("选项 A", "选项 B", "选项 C")
val rowPad = PaddingValues(vertical = rowPaddingV)
val sectionPad = PaddingValues(horizontal = rowPaddingH, vertical = sectionPadding)
val highlight = androidx.compose.foundation.shape.RoundedCornerShape(highlightRadius)
val rowTitleColor = if (customColors) titleColor else PrimaryText
val rowDescColor = if (customColors) descColor else SecondaryText
val cardColor = if (customColors) containerColor else CardBackground
val chevron: @Composable () -> Unit = {
    if (showChevron) {
        if (customChevron) Text(chevronText, color = controlColor, fontSize = 14.sp)
        else androidx.compose.material3.Icon(
            painterResource(com.lindicra.cms.R.drawable.ic_chevron_right),
            contentDescription = null, tint = controlColor, modifier = Modifier.size(14.dp))
    }
}
CMS.Content.List(spacing = groupSpacing) {
    CMS.Content.Section(contentPadding = sectionPad, containerColor = cardColor,
        cornerRadius = cornerRadius, separatorIndent = dividerPadding,
        separatorEndIndent = dividerPadding, separatorColor = separatorColor,
        showSeparators = showDividers) {
        CMS.Content.ListRow(minHeight = rowHeight, contentPadding = rowPad,
            highlightShape = highlight, pressedColor = pressedColor,
            trailing = if (showChevron) chevron else null,
            onClick = { haptic(CMSHapticKind.Tick) }) {
            CMS.Content.ListRowText(navTitle, navDesc, titleColor = rowTitleColor,
                descColor = rowDescColor, titleSize = titleSize.sp, descSize = descSize.sp)
        }
        CMS.Content.ListRow(minHeight = rowHeight, contentPadding = rowPad,
            highlightShape = highlight, pressedColor = pressedColor,
            onClick = { checked = !checked },
            trailing = { CMS.Controls.Toggle(checked, { checked = it }, trackOnColor = controlColor) }) {
            CMS.Content.ListRowText(toggleTitle, toggleDesc, titleColor = rowTitleColor,
                descColor = rowDescColor, titleSize = titleSize.sp, descSize = descSize.sp)
        }
        CMS.Content.MenuRow(menuTitle, desc = menuDesc, value = options[selected],
            minHeight = rowHeight, contentPadding = rowPad,
            highlightShape = highlight, pressedColor = pressedColor,
            titleColor = rowTitleColor, descColor = rowDescColor, valueColor = controlColor,
            titleSize = titleSize.sp, descSize = descSize.sp,
            onOpen = { anchor = it; visible = true })
    }
    CMS.Content.Section(contentPadding = sectionPad, containerColor = cardColor,
        cornerRadius = cornerRadius, separatorIndent = dividerPadding,
        separatorEndIndent = dividerPadding, separatorColor = separatorColor,
        showSeparators = showDividers) {
        CMS.Content.ListRow(minHeight = rowHeight, contentPadding = rowPad,
            highlightShape = highlight, pressedColor = pressedColor,
            trailing = if (showChevron) chevron else null,
            onClick = { haptic(CMSHapticKind.Tick) }) {
            CMS.Content.ListRowText(secondTitle, secondDesc, titleColor = rowTitleColor,
                descColor = rowDescColor, titleSize = titleSize.sp, descSize = descSize.sp)
        }
    }
}
CMS.Controls.Menu(items = options.map { MenuItem(it) }, currentIndex = selected,
    visible = visible, anchorRect = anchor, hazeState = haze, accentColor = controlColor,
    onSelect = { selected = it; visible = false }, onDismiss = { visible = false })"""
        DemoPage.Switch -> """var checked by remember(initialChecked) { mutableStateOf(initialChecked) }
CMS.Controls.Toggle(
    checked = checked, onCheckedChange = { checked = it; haptic(CMSHapticKind.Tick) },
    trackOnColor = controlColor, trackOffColor = trackOffColor,
    thumbOnColor = thumbColor, thumbOffColor = thumbColor
)"""
        DemoPage.Progress -> """var value by remember(initialValue) { mutableFloatStateOf(initialValue) }
CMS.Controls.Slider(
    value = value, onValueChange = { value = it },
    onValueChangeFinished = { haptic(CMSHapticKind.Tick) }, modifier = Modifier.fillMaxWidth(),
    selectedColor = controlColor, thumbStyle = thumbStyle, steps = steps,
    readOnly = readOnly, thicknessAnimation = thicknessAnimation,
    dampingEnabled = dampingEnabled, overscrollEnabled = overscrollEnabled
)"""
        DemoPage.SelectMenu -> """var visible by remember { mutableStateOf(false) }
var modal by remember { mutableStateOf(false) }
var anchor by remember { mutableStateOf(IntRect.Zero) }
var selected by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }
val touch = rememberCMSMenuTouchState()
CMSSection {
    CMS.Content.MenuRow("操作菜单", value = "选项 ${'$'}{selected + 1}",
        onOpen = { anchor = it; visible = true })
}
CMS.Controls.Menu(
    items = listOf(MenuItem("选项 A"), MenuItem("选项 B"),
        MenuItem("更多", subItems = listOf(MenuItem("选项 C"),
            MenuItem("打开底部面板"))),
        MenuItem("不可用项目", disabled = true)),
    visible = visible, anchorRect = anchor, currentIndex = selected,
    type = CMSMenuType.Actions, hazeState = haze, touchState = touch,
    accentColor = controlColor, dimBackground = dimBackground, dimAmount = dimAmount,
    onSelect = {}, onSelectPath = {
        if (it == listOf(2, 1)) modal = true else selected = it.first()
        haptic(CMSHapticKind.Tick)
        visible = false
    },
    onDismiss = { visible = false }
)
if (modal) {
    CMS.Presentation.Sheet(onDismiss = { modal = false }) { dismiss ->
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Text("底部面板", color = controlColor)
            CMS.Controls.GlassButton(dismiss, width = 64.dp, height = 40.dp,
                glassTint = controlColor) { color -> Text("完成", color = color) }
            CMS.Content.ScrollView(Modifier.weight(1f), springOverscroll = true, showScrollbar = true) {
                repeat(10) { index ->
                    CMSSettingsCard { Text("内容 ${'$'}{index + 1}") }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}"""
        DemoPage.Scroll -> """val offsets = remember { mutableStateMapOf<String, Int>() }
var origin by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
val density = androidx.compose.ui.platform.LocalDensity.current
val topReservePx = with(density) { previewTopInset.toPx() }
val bottomReserve = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
CMS.Content.ScrollView(
    modifier = Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    springOverscroll = springOverscroll, showScrollbar = showScrollbar,
    scrollbarStyle = scrollbarStyle, scrollbarIndexLayout = scrollbarIndexLayout,
    scrollbarInteractive = scrollbarInteractive, scrollbarPressThickening = scrollbarPressThickening,
    scrollbarInsets = PaddingValues(start = 8.dp, top = previewTopInset + 8.dp,
        end = 8.dp, bottom = bottomReserve + 8.dp),
    scrollbarSections = ('A'..'H').map { CMSScrollSection(it.toString(), offsets[it.toString()] ?: 0) }
) {
    Column(Modifier.fillMaxWidth().onGloballyPositioned { origin = it }) {
        Spacer(Modifier.height(previewTopInset))
        // 记录内容相对坐标；布局后更新各分组真实偏移。
        ('A'..'H').forEach { letter ->
            Column(Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                origin?.takeIf { it.isAttached && coordinates.isAttached }?.let {
                    offsets[letter.toString()] = (it.localPositionOf(coordinates,
                        androidx.compose.ui.geometry.Offset.Zero).y - topReservePx).toInt().coerceAtLeast(0)
                }
            }) {
                CMS.Content.SectionTitle(letter.toString())
                repeat(3) { index ->
                    CMSSettingsCard { Text("${'$'}letter · ${'$'}{index + 1}", color = controlColor) }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}"""
        DemoPage.Swiper -> """CMS.Content.PageView(
    itemCount = itemCount, direction = direction,
    autoPlay = autoPlay, autoPlayInterval = autoPlayInterval,
    showArrows = showArrows, arrowSize = arrowSize, edgeOverflow = edgeOverflow,
    snapBack = snapBack, freeMode = freeMode, pageWidthFraction = pageWidthFraction,
    pageHeight = pageHeight, pageSpacing = pageSpacing, clipRadius = clipRadius,
    indicatorStyle = indicatorStyle, indicatorVisibleCount = indicatorVisibleCount,
    indicatorSize = indicatorSize, indicatorActiveWidth = indicatorActiveWidth,
    indicatorBottomInset = indicatorBottomInset, indicatorInteractive = indicatorInteractive,
    indicatorColor = controlColor
) { index ->
    Box(Modifier.fillMaxSize()) {
${image().prependIndent("        ")}
        Text("第 ${'$'}{index + 1} 页", color = Color.White)
    }
}"""
        DemoPage.HalfModal -> """var visible by remember { mutableStateOf(false) }
CMSSection {
    CMS.Content.ListRow("打开底部面板", "拖动关闭与惯性回弹", onClick = { visible = true })
}
if (visible) {
    CMS.Presentation.Sheet(onDismiss = { visible = false }, heightFraction = heightFraction) { dismiss ->
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Text("底部面板", color = controlColor)
            CMS.Controls.GlassButton(dismiss, width = 64.dp, height = 40.dp,
                glassTint = controlColor) { color -> Text("完成", color = color) }
            CMS.Content.ScrollView(Modifier.weight(1f), springOverscroll = true, showScrollbar = true) {
                repeat(10) { index ->
                    CMSSettingsCard { Text("内容 ${'$'}{index + 1}") }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}"""
        DemoPage.Haptics -> """CMS.Controls.GlassButton(
    onClick = { haptic(kind) }, width = 72.dp, height = 40.dp,
    glassTint = controlColor, exclusiveTouchGesture = true
) { color -> Text("体验", color = color) }"""
        DemoPage.Text -> """CMS.Content.Text(
    content,
    preset = preset,
    fontSize = fontSize.sp,
    fontWeight = weight,
    maxLines = maxLines,
    overflow = overflow,
    respectFontScale = respectFontScale,
    lineHeight = lineHeight.sp,
    color = controlColor
)"""
        DemoPage.CodeBlock -> """val sample = "fun greet(name: String) = \"Hello, \" + name"
CMS.Content.CodeBlock(
    sample,
    cornerRadius = cornerRadius,
    maxHeight = maxHeight.takeIf { it > 0.dp },
    wrap = wrap,
    highlight = highlight,
    showHeader = showHeader,
    edgeFeedback = edgeFeedback
)"""
        DemoPage.Table -> """CMS.Content.Table(
    columns = listOf("参数", "类型", "说明"),
    rows = listOf(
        listOf("cornerRadius", "Dp", "0.dp 为直角"),
        listOf("maxHeight", "Dp?", "null 为无限高度"),
        listOf("edgeFeedback", "Boolean", "四边回弹与轻触")
    ),
    cornerRadius = cornerRadius,
    maxHeight = maxHeight.takeIf { it > 0.dp },
    minColumnWidth = minColumnWidth,
    striped = striped,
    showDividers = showDividers,
    edgeFeedback = edgeFeedback
)"""
        DemoPage.Tag -> """CMS.Content.Tag(
    content,
    style = style,
    tint = controlColor,
    fontSize = fontSize.sp,
    cornerRadius = cornerRadius,
    horizontalPadding = horizontalPadding,
    verticalPadding = verticalPadding
)"""
        DemoPage.Link -> """CMS.Content.Link(
    content,
    onClick = { },
    color = controlColor,
    fontSize = fontSize.sp,
    underline = underline,
    fontWeight = weight,
    maxLines = maxLines
)"""
        DemoPage.Alert -> """var visible by remember { mutableStateOf(false) }
CMS.Content.ListRow("打开警告弹窗", showChevron = false) { visible = true }
if (visible) {
    CMS.Presentation.Alert(
        title = title,
        message = message,
        confirmText = confirmText,
        cancelText = if (showCancel) "取消" else null,
        destructive = destructive,
        scrimAlpha = scrimAlpha,
        dismissOnOutsideClick = dismissOnOutsideClick,
        dismissOnBackPress = dismissOnBackPress,
        buttonLayout = buttonLayout,
        titleLineHeight = titleLineHeight.sp,
        messageLineHeight = messageLineHeight.sp,
        onConfirm = { },
        onCancel = { },
        onDismiss = { visible = false }
    )
}"""
        DemoPage.ActionSheet -> """var visible by remember { mutableStateOf(false) }
CMS.Content.ListRow("打开动作列表", showChevron = false) { visible = true }
if (visible) {
    CMS.Presentation.ActionSheet(
        actions = listOf(
            CMSDialogAction(actionOne, {}),
            CMSDialogAction(actionTwo, {}),
            CMSDialogAction(actionThree, {}, destructive = destructiveAction)
        ),
        title = sheetTitle,
        cancelText = cancelText,
        scrimAlpha = scrimAlpha,
        dismissOnOutsideClick = dismissOnOutsideClick,
        dismissOnBackPress = dismissOnBackPress,
        actionLayout = actionLayout,
        onDismiss = { visible = false }
    )
}"""
        DemoPage.WheelPicker -> """var selected by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }
CMS.Controls.WheelPicker(
    items = listOf("周一", "周二", "周三", "周四", "周五"),
    selectedIndex = selected,
    onSelected = { selected = it },
    modifier = Modifier.fillMaxWidth(),
    itemHeight = itemHeight,
    visibleCount = visibleCount,
    loop = loop,
    frictionMultiplier = frictionMultiplier,
    dampingRatio = dampingRatio,
    stiffness = stiffness,
    wheelRadius = wheelRadius,
    hapticEnabled = hapticEnabled
)"""
        DemoPage.DatePicker -> """val today = java.time.LocalDate.now()
var date by remember(selectedDate) { mutableStateOf(java.time.LocalDate.parse(selectedDate)) }
CMS.Controls.DatePicker(
    selected = date,
    onSelected = { date = it },
    weekStart = weekStart,
    minDate = today.minusMonths(rangeMonths.toLong()),
    maxDate = today.plusMonths(rangeMonths.toLong()),
    showTodayButton = showToday,
    mode = pickerMode,
    showModeSwitch = showModeSwitch
)"""
        DemoPage.TimePicker -> """var hourValue by remember(hour) { mutableIntStateOf(hour) }
var minuteValue by remember(minute) { mutableIntStateOf(minute) }
CMS.Controls.TimePicker(
    hour = hourValue,
    minute = minuteValue,
    onTimeChange = { newHour, newMinute -> hourValue = newHour; minuteValue = newMinute },
    minuteStep = minuteStep,
    is24Hour = is24Hour,
    itemHeight = itemHeight,
    visibleCount = visibleCount,
    loop = loop
)"""
        DemoPage.ColorPicker -> """var picked by remember(controlColor) { mutableStateOf(controlColor) }
CMS.Controls.ColorPicker(
    color = picked,
    onColorChange = { picked = it },
    showPresets = showPresets,
    showAlpha = showAlpha,
    recentLimit = recentLimit,
    panelHeight = panelHeight,
    mode = pickerMode,
    palette = palette,
    showModeSwitch = showModeSwitch,
    showInputs = showInputs
)"""
        DemoPage.TextField -> """var input by remember(inputValue) { mutableStateOf(inputValue) }
CMS.Controls.TextField(
    value = input,
    onValueChange = { input = it },
    label = label,
    placeholder = placeholder,
    supportingText = supportingText,
    variant = variant,
    prefixText = prefixText.ifEmpty { null },
    suffixText = suffixText.ifEmpty { null },
    showClearButton = showClearButton,
    isError = isError,
    isSuccess = isSuccess,
    enabled = enabled,
    size = fieldSize
)"""
        DemoPage.VStack -> """CMS.Layout.VStack(
    spacing = spacing,
    horizontalAlignment = horizontalAlignment,
    minWidth = minWidth,
    maxWidth = maxWidth,
    minHeight = minHeight,
    maxHeight = maxHeight
) {
    CMS.Content.Text("第一项")
    CMS.Content.Text("第二项")
}"""
        DemoPage.HStack -> """CMS.Layout.HStack(
    spacing = spacing,
    verticalAlignment = verticalAlignment,
    minWidth = minWidth,
    maxWidth = maxWidth,
    minHeight = minHeight,
    maxHeight = maxHeight
) {
    CMS.Content.Text("左")
    CMS.Content.Text("右")
}"""
        DemoPage.ZStack -> """CMS.Layout.ZStack(
    alignment = alignment,
    minWidth = minWidth,
    maxWidth = maxWidth,
    minHeight = minHeight,
    maxHeight = maxHeight
) {
    Box(Modifier.size(96.dp).background(controlColor.copy(alpha = 0.18f)))
    CMS.Content.Text("叠放内容")
}"""
        DemoPage.Group -> """CMS.Layout.Group(
    spacing = spacing,
    horizontalAlignment = horizontalAlignment,
    minWidth = minWidth,
    maxWidth = maxWidth,
    minHeight = minHeight,
    maxHeight = maxHeight
) {
    CMS.Content.Text("第一组")
    CMS.Content.Text("第二组")
}"""
        DemoPage.Appearance -> """CMSSettingsCard {
    Text("外观预览", color = controlColor)
    Text("主题强调色", color = themeColor)
    CMS.Controls.Slider(value = 0.6f, selectedColor = controlColor, readOnly = true)
}"""
    }
}
