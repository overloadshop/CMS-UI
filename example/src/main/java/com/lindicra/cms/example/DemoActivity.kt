package com.lindicra.cms.example

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import com.lindicra.cms.ui.theme.*

/** Home preferences only; preview instances never write this store. */
internal object DemoStore {
    var appearance by mutableIntStateOf(0)
    var advanced by mutableStateOf(true)
    var clarity by mutableIntStateOf(2)
    var haptics by mutableStateOf(true)
    var swipeTabs by mutableStateOf(true)
}

internal enum class DemoCategory(val title: String) {
    Navigation("导航 Navigation"),
    Controls("控件 Controls"),
    Content("内容 Content"),
    Presentation("呈现 Presentation"),
    Appearance("外观 Appearance"),
    Layout("布局 Layout"),
    Interaction("交互接入 Interaction")
}

/**
 * 主页按「组件族」列出，单个部件写进英文副标题：
 * 一族一个入口，而不是把 Card / Row / Divider 拆成三条。
 */
internal enum class DemoPage(
    val title: String, val englishName: String, val exampleName: String, val category: DemoCategory
) {
    GlassButton("玻璃按钮", "Glass Button", "GlassButton", DemoCategory.Controls),
    BackButton("返回按钮", "Back Button", "BackButton", DemoCategory.Navigation),
    TabBar("Tab 栏", "TabView · 底栏 / 顶栏 / 侧栏", "GlassTabBar", DemoCategory.Navigation),
    CapsuleNavigation("胶囊导航", "Capsule Tag · 描边 / 底板", "CapsuleNavigation", DemoCategory.Navigation),
    TabNavigation("标签导航", "Tab Navigation · 下划线", "TabNavigation", DemoCategory.Navigation),
    TouchPriority("触控优先级", "双页滚动与切换", "TouchPriority", DemoCategory.Interaction),
    GlassFusion("玻璃融合", "两块普通玻璃靠近与分开", "GlassFusion", DemoCategory.Interaction),
    BackHistory("返回历史", "Back History · 长按跨级返回", "BackHistory", DemoCategory.Interaction),
    ContentSource("采样源", "Source", "GlassContentSource", DemoCategory.Content),
    SettingsRows("列表", "List · 卡片 / 行 / 分隔线", "SettingsList", DemoCategory.Content),
    Switch("开关", "Toggle", "Switch", DemoCategory.Controls),
    Progress("滑块", "Slider", "Slider", DemoCategory.Controls),
    SelectMenu("菜单", "Menu", "PopupMenu", DemoCategory.Controls),
    Scroll("滚动视图", "ScrollView · 滚动条", "ScrollView", DemoCategory.Content),
    Page("Page 页面容器", "顶栏 / 滚动 / 避让 / 触控优先级", "Page", DemoCategory.Content),
    Swiper("分页视图", "PageView · 指示器", "Carousel", DemoCategory.Content),
    HalfModal("面板", "Sheet", "BottomSheet", DemoCategory.Presentation),
    Haptics("触觉反馈", "Haptics", "HapticFeedback", DemoCategory.Appearance),
    Appearance("主题与外观", "Appearance · 全局默认", "ThemeAppearance", DemoCategory.Appearance),
    Text("文本", "Text · 字号阶梯 / 截断", "Text", DemoCategory.Content),
    CodeBlock("代码块", "Code Block · 语法着色 / 触边", "CodeBlock", DemoCategory.Content),
    Table("表格", "Table · 列宽 / 斑马纹", "Table", DemoCategory.Content),
    Tag("标签", "Tag", "Tag", DemoCategory.Content),
    Link("链接", "Link", "Link", DemoCategory.Content),
    Alert("警告弹窗", "Alert", "Alert", DemoCategory.Presentation),
    ActionSheet("动作列表", "Action Sheet", "ActionSheet", DemoCategory.Presentation),
    WheelPicker("滚筒", "Wheel Picker", "WheelPicker", DemoCategory.Controls),
    DatePicker("日期选择器", "Date Picker", "DatePicker", DemoCategory.Controls),
    TimePicker("时间选择器", "Time Picker", "TimePicker", DemoCategory.Controls),
    ColorPicker("颜色选择器", "Color Picker", "ColorPicker", DemoCategory.Controls),
    TextField("输入框", "Text Field", "TextField", DemoCategory.Controls),
    VStack("纵向布局", "VStack", "VStack", DemoCategory.Layout),
    HStack("横向布局", "HStack", "HStack", DemoCategory.Layout),
    ZStack("叠放布局", "ZStack", "ZStack", DemoCategory.Layout),
    Group("分组", "Group", "Group", DemoCategory.Layout);

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value } ?: GlassButton
    }
}

/** Component entries open the editor; documentation is one level below it. */
class DemoActivity : ExampleActivity() {
    companion object {
        private const val EXTRA_PAGE = "com.lindicra.cms.example.page"
        internal fun intent(context: Context, page: DemoPage): Intent =
            Intent(context, DemoActivity::class.java).putExtra(EXTRA_PAGE, page.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        applyWindowTheme(window, DemoStore.appearance)
        val page = DemoPage.from(intent.getStringExtra(EXTRA_PAGE))
        setExampleContent {
            ApplySystemBars(resolveDark(), window)
            ComponentEditorScreen(page, onBack = { finish() })
        }
    }
}

@Composable
internal fun resolveDark(): Boolean = when (DemoStore.appearance) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

internal fun applyWindowTheme(window: Window, appearance: Int) {
    val night = (window.context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val dark = when (appearance) { 1 -> false; 2 -> true; else -> night }
    window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF111318.toInt() else 0xFFF5F6F8.toInt()))
}

@Composable
internal fun ApplySystemBars(dark: Boolean, window: Window) {
    SideEffect {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
internal fun PreviewImage(modifier: Modifier) {
    Image(painter = painterResource(R.drawable.liquid_light_preview), contentDescription = null,
        contentScale = ContentScale.Crop, modifier = modifier)
}

@Composable
internal fun Label(text: String, size: TextUnit = 17.sp, weight: FontWeight? = null,
    color: Color = PrimaryText, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, color = color, fontSize = size, fontWeight = weight,
        fontFamily = HarmonySansSC,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
}
