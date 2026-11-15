package com.lindicra.cms.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lindicra.cms.CMS
import com.lindicra.cms.CMS_VERSION
import com.lindicra.cms.CMSSettings
import com.lindicra.cms.ui.theme.HarmonySansSC
import com.lindicra.cms.ui.theme.LocalClearIsDark
import com.lindicra.cms.ui.theme.PageBackground
import com.lindicra.cms.ui.theme.PrimaryText
import com.lindicra.cms.ui.theme.SecondaryText
import com.lindicra.cms.ui.theme.TertiaryText

/** 组件清单的一项：分类 + 中英文名 + 调用入口 + 当前状态。 */
internal data class CatalogEntry(
    val section: String,
    val chinese: String,
    val english: String,
    val api: String,
    val status: CatalogStatus
)

internal enum class CatalogStatus(val label: String) {
    Demo("有演示"),
    ApiOnly("仅 API"),
    NeedsWork("待优化")
}

internal object ComponentCatalog {
    val entries = listOf(
        CatalogEntry("Appearance 外观", "主题与外观", "Theme & Appearance", "CMS.Appearance.Theme", CatalogStatus.Demo),
        CatalogEntry("Appearance 外观", "全局默认", "Settings", "CMSSettings / CMSDefaults", CatalogStatus.Demo),
        CatalogEntry("Appearance 外观", "触觉反馈", "Haptic Feedback", "CMSHaptics", CatalogStatus.Demo),

        CatalogEntry("Navigation 导航", "Tab 栏", "TabView", "CMS.Navigation.TabView", CatalogStatus.Demo),
        CatalogEntry("Navigation 导航", "胶囊导航", "Capsule Tag", "CMS.Navigation.CapsuleNavigation", CatalogStatus.Demo),
        CatalogEntry("Navigation 导航", "标签导航", "Tab Navigation", "CMS.Navigation.TabNavigation", CatalogStatus.Demo),
        CatalogEntry("Navigation 导航", "Tab 侧栏", "TabView · Rail", "CMS.Navigation.TabView(form = Rail)", CatalogStatus.NeedsWork),
        CatalogEntry("Navigation 导航", "返回按钮", "Back Button", "CMS.Navigation.BackButton", CatalogStatus.Demo),
        CatalogEntry("Navigation 导航", "玻璃顶栏", "Glass Top Bar", "CMS.Navigation.MainTopBar / SecondaryTopBar", CatalogStatus.Demo),

        CatalogEntry("Controls 控件", "开关", "Toggle", "CMS.Controls.Toggle", CatalogStatus.Demo),
        CatalogEntry("Controls 控件", "滑块", "Slider", "CMS.Controls.Slider", CatalogStatus.Demo),
        CatalogEntry("Controls 控件", "分段控件", "Segmented Picker", "CMS.Controls.SegmentedPicker", CatalogStatus.Demo),
        CatalogEntry("Controls 控件", "菜单", "Menu", "CMS.Controls.Menu", CatalogStatus.Demo),
        CatalogEntry("Controls 控件", "玻璃按钮", "Glass Button", "CMS.Controls.GlassButton", CatalogStatus.Demo),

        CatalogEntry("Content 内容", "采样源", "Source", "CMS.Content.Source", CatalogStatus.Demo),
        CatalogEntry("Content 内容", "滚动视图", "ScrollView", "CMS.Content.ScrollView", CatalogStatus.Demo),
        CatalogEntry("Content 内容", "Page 页面容器", "Page", "CMS.Content.Page", CatalogStatus.Demo),
        CatalogEntry("Content 内容", "分页视图", "PageView", "CMS.Content.PageView", CatalogStatus.Demo),
        CatalogEntry("Content 内容", "列表", "List", "CMS.Content.ListSection / ListRow", CatalogStatus.Demo),
        CatalogEntry("Content 内容", "滚动条", "Scrollbar", "CMSScrollbarStyle / CMSScrollSection", CatalogStatus.ApiOnly),

        CatalogEntry("Presentation 呈现", "面板", "Sheet", "CMS.Presentation.Sheet", CatalogStatus.Demo),

        CatalogEntry("Content 内容", "基础文本", "Text", "CMS.Content.Text", CatalogStatus.Demo),
        CatalogEntry("Content 内容", "代码块", "Code Block", "CMS.Content.CodeBlock", CatalogStatus.Demo),
        CatalogEntry("Content 内容", "表格", "Table", "CMS.Content.Table", CatalogStatus.Demo),
        CatalogEntry("Content 内容", "标签文本", "Tag", "CMS.Content.Tag", CatalogStatus.Demo),
        CatalogEntry("Content 内容", "链接文字", "Link", "CMS.Content.Link", CatalogStatus.Demo),

        CatalogEntry("Controls 控件", "滚筒", "Wheel Picker", "CMS.Controls.WheelPicker", CatalogStatus.Demo),
        CatalogEntry("Controls 控件", "日期选择器", "Date Picker", "CMS.Controls.DatePicker", CatalogStatus.Demo),
        CatalogEntry("Controls 控件", "时间选择器", "Time Picker", "CMS.Controls.TimePicker", CatalogStatus.Demo),
        CatalogEntry("Controls 控件", "颜色选择器", "Color Picker", "CMS.Controls.ColorPicker", CatalogStatus.Demo),
        CatalogEntry("Controls 控件", "输入框", "Text Field", "CMS.Controls.TextField", CatalogStatus.Demo),

        CatalogEntry("Presentation 呈现", "警告弹窗", "Alert", "CMS.Presentation.Alert", CatalogStatus.Demo),
        CatalogEntry("Presentation 呈现", "动作列表", "Action Sheet", "CMS.Presentation.ActionSheet", CatalogStatus.Demo),
        CatalogEntry("Presentation 呈现", "自定义弹窗", "Dialog", "CMS.Presentation.Dialog", CatalogStatus.Demo),

        CatalogEntry("Layout 布局", "叠放容器", "ZStack", "CMS.Layout.ZStack", CatalogStatus.Demo),
        CatalogEntry("Layout 布局", "纵向布局", "VStack", "CMS.Layout.VStack", CatalogStatus.Demo),
        CatalogEntry("Layout 布局", "横向布局", "HStack", "CMS.Layout.HStack", CatalogStatus.Demo),
        CatalogEntry("Layout 布局", "分组容器", "Group", "CMS.Layout.Group", CatalogStatus.Demo),

        CatalogEntry("Foundation 基础", "平板布局", "Layout Metrics", "TABLET_MIN_DP / screenScaleFactor", CatalogStatus.ApiOnly),
        CatalogEntry("Foundation 基础", "调试覆盖层", "Debug Overlay", "CMSDebugOverlay", CatalogStatus.ApiOnly),
        CatalogEntry("Foundation 基础", "开发者设置", "Developer Settings", "DeveloperSettings", CatalogStatus.ApiOnly),
        CatalogEntry("Foundation 基础", "原生动效", "Motion Engine", "CLEARMotion（JNI）", CatalogStatus.ApiOnly),
        CatalogEntry("Foundation 基础", "连续圆角", "Continuous Corner", "ContinuousRoundedCornerShape", CatalogStatus.ApiOnly),
        CatalogEntry("Interaction 交互接入", "触控优先级", "Touch Priority", "CMS.Interaction.TouchPriority / CMS.Content.Pager", CatalogStatus.Demo),
        CatalogEntry("Interaction 交互接入", "玻璃融合", "Glass Fusion", "CMS.Interaction.GlassFusion / GlassSearch", CatalogStatus.Demo),
        CatalogEntry("Interaction 交互接入", "返回历史", "Back History", "CMS.Interaction.BackHistory / CMSBackHistory", CatalogStatus.Demo)
    )

    val sections = entries.map { it.section }.distinct()
}

class ComponentCatalogActivity : ExampleActivity() {
    companion object {
        internal fun intent(context: Context) = Intent(context, ComponentCatalogActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        applyWindowTheme(window, DemoStore.appearance)
        ExampleBackHistory.updateTitle(this, "组件清单")
        setExampleContent {
            ApplySystemBars(resolveDark(), window)
            CMS.Appearance.Provide(CMSSettings(darkTheme = resolveDark())) {
                CMS.Appearance.Theme {
                    ComponentCatalogScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
private fun ComponentCatalogScreen(onBack: () -> Unit) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(PageBackground)) {
        CMS.Content.ScrollView(
            modifier = Modifier.fillMaxSize(),
            springOverscroll = true,
            showScrollbar = true
        ) {
            Spacer(Modifier.height(topInset + 64.dp))
            Column(
                Modifier.widthIn(max = 680.dp).fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "组件清单",
                    color = PrimaryText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = HarmonySansSC
                )
                Text(
                    "按 系统的组件体系分类 · $CMS_VERSION",
                    color = SecondaryText,
                    fontSize = 12.sp,
                    fontFamily = HarmonySansSC
                )
                Spacer(Modifier.height(4.dp))
                ComponentCatalog.sections.forEach { section ->
                    CMS.Content.SectionTitle(section)
                    CMS.Content.Section {
                        ComponentCatalog.entries.filter { it.section == section }
                            .forEachIndexed { index, entry ->
                                if (index > 0) CMS.Content.RowDivider()
                                CatalogRow(entry)
                            }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "状态说明：有演示 = 示例里可直接体验；仅 API = 只提供接口；待优化 = 已知不足，正在迭代。",
                    color = TertiaryText,
                    fontSize = 11.sp,
                    fontFamily = HarmonySansSC
                )
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp))
        }
        // 返回按钮悬浮在内容之上
        Box(Modifier.padding(top = topInset + 8.dp, start = 16.dp)) {
            CMS.Navigation.BackButton(onClick = onBack)
        }
    }
}

@Composable
private fun CatalogRow(entry: CatalogEntry) {
    val context = LocalContext.current
    val page = when (entry.english) {
        "Page", "Glass Top Bar" -> DemoPage.Page
        "Capsule Tag" -> DemoPage.CapsuleNavigation
        "Tab Navigation" -> DemoPage.TabNavigation
        "Touch Priority" -> DemoPage.TouchPriority
        "Glass Fusion" -> DemoPage.GlassFusion
        "Back History" -> DemoPage.BackHistory
        else -> null
    }
    // 用托管行，左右内缩由 Section 统一下发，不能自己画 Row（那会得到 0 内边距）
    CMS.Content.ListRow(
        onClick = page?.let { demo -> { context.startActivity(DemoActivity.intent(context, demo)) } },
        trailing = {
            val tint = when (entry.status) {
                CatalogStatus.Demo -> if (LocalClearIsDark.current)
                    androidx.compose.ui.graphics.Color(0xFF6FCF97)
                else androidx.compose.ui.graphics.Color(0xFF2E9E5B)
                CatalogStatus.ApiOnly -> TertiaryText
                CatalogStatus.NeedsWork -> androidx.compose.ui.graphics.Color(0xFFE0A458)
            }
            Text(
                entry.status.label,
                color = tint,
                fontSize = 10.sp,
                fontFamily = HarmonySansSC,
                fontWeight = FontWeight.Medium
            )
        }
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.chinese,
                    color = PrimaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = HarmonySansSC
                )
                Text(
                    "  ${entry.english}",
                    color = SecondaryText,
                    fontSize = 11.sp,
                    fontFamily = HarmonySansSC
                )
            }
            Text(
                entry.api,
                color = TertiaryText,
                fontSize = 10.sp,
                fontFamily = HarmonySansSC
            )
        }
    }
}
