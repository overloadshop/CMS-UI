# CMS 安卓示例 · 1.1.0

独立应用 ID：`com.lindicra.cms.example`。
源码只依赖 `:cms-core`，不含任何宿主业务类，也没有复制组件实现。

## 打开与构建

用 Android Studio 打开 **CMS-UI 根目录**，选择 `example` 运行。
需要 Android SDK 36、NDK 27.0.12077973、CMake 3.22.1；最低 Android 12。

示例本身已改用统一入口：

```kotlin
CMS.Appearance.Provide(CMSSettings(...)) { CMS.Appearance.Theme { CMS.Navigation.TabView(...) } }
```

入口与全局默认见仓库根目录的 `docs/06-统一入口与全局配置.md`。

「悬浮底栏」编辑页新增三项设置：**图标数量**（2–5）、**Tab 位置**（底部 / 顶部）、
**Tab 形态**（贴边 / 悬浮 / 侧边）。改数量时选中项会自动夹到有效范围；
形态切到「侧边」时显示竖向 rail（首版为滑动指示器，未接液面物理）。

命令行：

```powershell
$env:ANDROID_HOME = "你的 SDK 路径"
.\gradlew.bat :example:assembleDebug
```

或在 CMS-UI 根目录创建 `local.properties`，设置 `sdk.dir`。
Gradle Wrapper、组件源码、资源、三个依赖模块和原生引擎都在 CMS-UI 内。
首次构建会下载 Maven 依赖，无需连接宿主工程。

中间输出位于系统临时目录 `CMS-UI-build/`。
示例 APK 构建路径为 `CMS-UI-build/example/outputs/apk/debug/example-debug.apk`。
安装包通过 GitHub Releases 提供，不随仓库提交。

## 可体验内容

组件及交互共提供 33 个可编辑入口，点击进入一级编辑页；编辑内的「文档」进入二级文档页。
主页的组件、样例、交互三页使用统一 CMS Pager + Page，支持左右切页、上下滑动及方向接管。组件按 Appearance、Navigation、Controls、Content、Layout、Presentation 分类；交互提供触控优先级、玻璃融合与返回历史。
“返回历史 · Back History”包含可调整的多级案例；编辑器、文档中心、组件文档、指南、组件清单共用实际访问历史。独立预览长按返回可选择编辑页及其上层页面；按稳定页面 id 返回并关闭中间页面。嵌套主题默认继承历史作用域。
文档和示例均离线可用。文档支持横向滚动表格、代码块、引用、列表和可点击的相关文档链接。编辑提供「快速」与「代码」两种模式，两种模式都能复制当前完整 Kotlin 调用。
快速设置与代码编辑共用参数，主题色和控件色分别自定义。每次重新进入编辑恢复默认，不写入全局偏好。
「快速 / 代码」复用正式 `CMS.Navigation.CapsuleNavigation`，选中底板连续滑动。

代码模式支持修改顶部 `val` 参数声明中的布尔、数值、Dp、枚举和 `Color(0xAARRGGBB)`。
代码提供 Kotlin 高亮、行号与错误行提示。修改后自动校验并同步已打开的预览；
导入、函数体和回调保持原样。无效输入显示错误并保留上次有效预览，
复制按钮始终导出当前有效调用。图片示例使用本模块的 `liquid_light_preview`，集成时一并复制或替换资源。

「打开预览」和「重新打开」都会立即检查当前完整代码及快捷输入，错误未修正时不会放行。
真正预览由 `:preview` 进程中的独立 Activity 渲染，拥有单独的任务窗口。
预览使用全屏视口，不套用编辑/文档的滚动列与宽度限制；玻璃采样背景和滚动视图铺满页面，
其他控件在可用区域展示，尺寸参数持续生效。顶栏覆盖在内容上，滚动内容可进入顶栏采样区域。
编辑页可进入、重新打开或关闭预览，并显示连接状态。有效参数经 Messenger 双向同步；
未完成的代码输入不会被预览交互覆盖。预览顶栏返回编辑时保留实例，系统返回关闭实例。
重新打开会重置交互状态并沿用当前参数；退出编辑会关闭预览窗口及绑定服务。
关闭后进程缓存由 Android 管理，不保留运行中的预览 Activity 或服务。
预览异常退出时编辑仍可保留，并允许重新打开。

| 页面 | English | 可调整 |
| --- | --- | --- |
| 玻璃按钮 | Glass Button | 尺寸、圆角、色调与阴影 |
| 返回按钮 | Back Button | 短按返回，长按页面历史 |
| 悬浮底栏 | Tab Bar | 四页切换、四边等距内间距、按下透明镜片、液态拖动与落点选择、深浅色独立配色 |
| 玻璃采样源 | Glass Content Source | 高级材质与通透度，玻璃从图片实时取样 |
| 设置列表 | Settings List | 分组、开关、导航与弹出菜单 |
| 切换开关 | Switch | 拖动越界形变与弹簧回位 |
| 滑块 | Slider | 当前值、滑块样式、粗细变化、阻尼跟随、越界回弹与只读 |
| 弹出菜单 | Popup Menu | 长按滑动选择、二级菜单与背景压暗 |
| 滚动视图 | Scroll View | 越界开关、可拖滚动条、按下加粗、经典 / 首字母 / 分段索引、首字母分散或顶部聚集 |
| 轮播视图 | Carousel | 页数、方向、自动播放与间隔、箭头及按钮大小、越界、吸附、自由滑动与页面占比、高度、间距、圆角；圆点 / 数字指示、可见数量、大小、选中长度、底部或右侧距离及点按切换 |
| 底部面板 | Bottom Sheet | 高度与拖拽关闭 |
| 触觉反馈 | Haptic Feedback | 轻触 / 按键 / 长按三档强度与总开关 |
| 主题与外观 | Theme & Appearance | 跟随系统 / 浅色 / 深色 |
| Page 页面容器 | Page | 顶栏、上下留位、内容限宽、滚动与触控桥 |
| 胶囊导航 / 标签导航 | Capsule / Tab Navigation | 分类、受控索引、指示器几何与颜色 |
| 触控优先级 | Touch Priority | 全屏双页、宽表格、长代码与原生玻璃；抖动抵消、按住快速换向与抬手再换向；respectConsumedGestures 默认关闭 |
| 玻璃融合 | Glass Fusion | 搜索展开与取消回收、间距、融合距离与材质 |
| 文本 / 标签 / 链接 | Text / Tag / Link | 字号阶梯、状态样式与点击 |
| 确认 / 动作弹窗 | Alert / Action Sheet | 标题、动作、危险状态与布局 |
| 日期 / 时间 / 颜色 / 滚筒 | Pickers | 模式、范围、步长、受控值与物理参数 |
| 输入框 | Text Field | 尺寸、输入变体、校验与只读 |
| VStack / HStack / ZStack / Group | Layout | 对齐、间距与尺寸边界 |

在「弹出菜单」长按菜单行后保持手指接触并滑动选择，松手确认。
通透度选择同样使用 CMS 胶囊与窗口菜单；关闭高级材质后组件使用降级材质。

离线文档的宽表格、代码块与代码编辑区共用触控优先级，横滑局部内容后可按住转为页面纵滑。“触控优先级”全屏案例提供宽内容与原生玻璃，用于检查抖动、连续换向和抬手后重新接管。

## 接入示范

`MainActivity.kt`、`DemoActivity.kt`、`PreviewActivity.kt`、`RemotePreview.kt` 与 `PreviewCode.kt` 展示：

- `CMSTheme` 提供主题、材质、Haze 和震动开关；
- `CMSSettingsCard`、`CMSSettingsDivider`、`CMSSettingsSectionTitle` 与
  `CMSSettingsNavRow` / `CMSSettingsSwitchRow` / `CMSSettingsMenuRow` 提供统一的设置页布局；
- 一级编辑为 `DemoActivity`，二级文档为 `PreviewActivity`，
  独立进程预览为 `RemotePreviewActivity` 与 `PreviewHostService`；
- 组件页仅预留左右安全区，顶部空间通过列表首项 `Spacer` 预留，
  `CMSSecondaryTopBar` 负责 51dp 顶栏与 1dp 分隔线，滚动内容通过 `hazeSource` 为顶栏提供模糊；
- `CMSBackHistory` 提供返回主页和返回编辑的目的地；
- `rememberCMSHaptic()` 调用统一触感；
- 菜单 `CMSPopupMenu` 直接放在页面内，由组件自行采样，不再手写 Popup；
- 玻璃预览使用单独的原生内容源和 HazeState，避免把自身纳入采样；
- 全屏预览支持 `previewTopBar`，普通组件默认保留顶栏，玻璃按钮、采样背景与悬浮底栏默认隐藏；内容、索引轨道及分组定位同步避让；
- `CMSGlassTabBar` 位于内容采样源外，按下完整通透镜片超出胶囊上下边缘，左右拖动受两端边界限制，拖动取消回到原页；
- `CMSBottomSheet` 与 `CMSCarousel` 直接使用组件库实现。

布局参数：外间距 16dp、卡片内边距 18dp、连续圆角 20dp、
分组间距 14dp、标题 15sp、说明 11sp、宽屏内容最大 680dp；
二级顶栏为 51dp 主体加 1dp 分隔线。字体采用 HarmonyOS Sans，
禁用额外字体内边距，CMSTheme 统一处理浅色字号补偿 2%。
预览图片随示例打包，独立编译不需要访问其它工程目录。

触感取决于设备振动器；API 31–32 使用毛玻璃降级，API 33+ 支持原生光学玻璃。

## 验证

```powershell
.\gradlew.bat :cms-core:testDebugUnitTest :example:testDebugUnitTest
.\gradlew.bat :example:assembleDebug -Pcms.compileSamples=true
```

第一步检验参数往返、非法代码拒绝、错误行定位、代码高亮与实例独立，为全部组件路由生成导出示例，同时验证底栏几何与 Markdown 表格解析。
第二步实际编译这些示例，检查复制得到的代码与组件 API 一致。
