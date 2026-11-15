# CMS-UI

**简体中文** · [English](README.en.md)

一套开箱可用的 UI 与动效组件库：组件、主题、字体、原生弹簧引擎和完整文档都在仓库里。
克隆下来就能跑示例，把 `library/` 复制进工程就能接入。

**版本 1.1.0** · Kotlin `2.3.21` · Compose BOM `2025.04.01` · 最低 Android 12

## 先跑起来

```bash
git clone https://github.com/overloadshop/CMS-UI.git
```

- **看效果**：用 Android Studio 打开仓库根目录，选 `example` 运行；或直接下载 Releases 里的 APK。
- **接进自己的工程**：把 `library/` 放到宿主 `app` 同级，合并 `gradle/settings-snippet.gradle.kts`，
  再 `implementation(project(":cms-core"))`。包名、资源、字体、JNI 都由这个模块带入，不用改宿主 namespace。
- **只要源码**：复制 `library/cms/com`、`library/theme/com`、`library/res`、`library/native`，
  按 [`docs/03-集成指南.md`](docs/03-集成指南.md) 补齐依赖。

默认值就能用：一次 `CMS.Appearance.Provide { CMS.Appearance.Theme { … } }` 统一深浅色、强调色、
玻璃材质与触感，单个组件仍可单独覆盖。

---

## 一、目录结构

| 路径 | 内容 |
| --- | --- |
| `library/cms/` | CMS 组件源码（Compose + 自定义 View），可独立编译 |
| `library/cms-core/` | 正式 Android 库模块，统一编译组件、主题、资源与 JNI |
| `example/` | 示例应用：组件目录、编辑页、独立进程预览与离线文档 |
| `library/optional/` | 可选组件（依赖宿主业务类，按需复制） |
| `library/theme/` | 主题与宿主接口：色板、`GlassMaterial`、`CMSHaptics`、`CMSBackHistory` |
| `library/res/font/` | HarmonyOS Sans SC 七个字重（`Type.kt` 依赖 `R.font.*`） |
| `library/res/drawable/` | 组件图标（`chevron_left.xml`） |
| `library/modules/liquidglass/` | 液态玻璃库模块，namespace `com.qmdeve.liquidglass` |
| `library/modules/continuous-rounded-corner/` | 连续圆角（Squircle）库模块 |
| `library/modules/shared-elements/` | Compose 共享元素转场库模块 |
| `library/native/` | CLEAR Motion Pro 预编译库与 JNI 桥（引擎源码见独立仓库） |
| `gradle/` | `libs.versions.toml` + `settings-snippet.gradle.kts` |
| `docs/` | 1.1.0 文档；`docs/v1.0/` 为 1.0 历史文档 |
| `docs/components/` | 独立组件文档与交互索引：参数、状态、接入边界和通用代码示例 |

---

## 二、组件清单（1.1.0）

组件采用统一的中英文名称。命名和兼容迁移见 [`docs/05-组件命名.md`](docs/05-组件命名.md)，新版入口集中在 `library/cms/CMSComponents.kt`，沿用现有组件实现。

### 玻璃与采样

| 文件 | 导出 | 说明 |
| --- | --- | --- |
| `CMSGlassButton.kt` | `CMSGlassButton`、`CMBlurLayer` | 通用液态玻璃按钮：材质、融合、触点高光、长按、采样源 |
| `CMSGlassBackButton.kt` | `CMSBackButton` | 返回按钮 · Back Button：短按返回，长按弹出返回历史（独立 Popup） |
| `CMGlassContentSource.kt` | `CMSGlassContentSource` | 玻璃采样源 · Glass Content Source：隔离内容与顶栏/浮层 |
| `CMMenuTouch.kt` | `CMSMenuTouchState`、`rememberCMSMenuTouchState`、`Modifier.cmsMenuAnchor` | 一次触摸贯通锚点与菜单，滚动/悬停选择 |
| `CMElasticMotion.kt` | 内部弹性物理 | 越界、回弹、惯性、滚动条几何 |
| `CMGestureAxis.kt` | `CMSVerticalDragBridge` | 菜单接管纵向拖拽的桥接 |

### 菜单与滚动

| 文件 | 导出 | 说明 |
| --- | --- | --- |
| `CMSelectMenu.kt` | `CMSPopupMenu`、`CMSMenuItem`、`CMSMenuType` | 弹出菜单 · Popup Menu；屏下 1/3 向上弹，支持二级菜单 |
| `CMScrollView.kt` | `CMSScrollView` | 滚动视图 · Scroll View：弹性越界、Lazy 网格与采样宿主 |
| `CMScrollbar.kt` | `CMSScrollbarStyle`、`CMSScrollbarIndexLayout`、`CMSScrollSection` | 可拖动滚动条，按下加粗，经典 / 首字母 / 分段索引 |
| `CMSSwitch.kt` | `CMSSwitch` | 切换开关 · Switch（拖拽形变 + 弹簧吸附） |

### 基础组件（1.0 起提供）

| 文件 | 导出 | 说明 |
| --- | --- | --- |
| `GlassTopBar.kt` | `CMSGlassPage`、`CMSMainGlassTopBar`、`CMSecondaryGlassTopBar`、`CMSGlassTopBarStyle`、`CMSGlassTopBarPadding` | 玻璃顶栏族（haze 1.7.1 API） |
| `CMBottomBar.kt` | `CMSBottomBar` | 悬浮玻璃底栏 |
| `CMSGlassTabBar.kt` | `CMSGlassTabBar`、`CMSGlassTabItem` | 悬浮底栏 · Tab Bar，透明按压镜片与液态拖动 |
| `CMProgressBar.kt` | `CMSSlider`、`CMSSliderThumbStyle` | 滑块 · Slider：连续数值、离散档位及只读展示 |
| `CMProgressMotion.kt` | 内部滑块物理 | 连续速度跟随、回弹与圆点贴边；单独复制滑块时需一并复制 |
| `CMSwiper.kt` | `CMSCarousel`、`CMSCarouselDirection`、`CMSCarouselOverscroll`、`CMSCarouselIndicator` | 轮播视图 · Carousel |
| `CMSCapsuleTabs.kt` | `CMSSegmentedControl` | 分段控件 · Segmented Control：2～4 项、弹簧滑动指示器、底板 / 描边 |
| `halfmodal/CMHalfModal.kt` | `CMSBottomSheet` | 底部面板 · Bottom Sheet |
| `CMDebugOverlay.kt` | `CMSDebugOverlay` | 调试覆盖层 |
| `CMCodeBlock.kt` | `CMSCodeBlock`、`CMSCodeTokenKind`、`cmsKotlinTokens` | 代码块 · Code Block |
| `CMTable.kt` | `CMSTable`、`CMSTableAlign` | 表格 · Table |
| `CMEdgeScroll.kt` | 内部四边触边滚动 | 代码块 / 表格共用的回弹与轻触 |
| `TabletLayout.kt` | `TABLET_MIN_DP`、`ScreenScale`、`screenScaleFactor`、`tabletContentMaxWidth`、`isTabletScreen`、`gridColumnsForWidth`、`gridRows` | 平板/响应式工具 |
| `CLEARMotion.kt` | `CLEARMotion`、`SpringDriver` | 原生弹簧 JNI 封装与 Kotlin 回退 |
| `DeveloperSettings.kt` | `DeveloperSettings` | 调试开关 |

### 主题与宿主接口（`library/theme/`）

| 文件 | 导出 | 说明 |
| --- | --- | --- |
| `Color.kt` | CLEAR Design token + 1.0 兼容别名 | `PageBackground`、`CardBackground`、`AccentBlue`… |
| `Theme.kt` | `CMSTheme`、`CLEARmusicTheme`、`LocalClearIsDark` | 主题入口 |
| `GlassMaterial.kt` | `GlassMaterial`、`LocalGlassMaterial`、`LocalGlassHazeState` | 玻璃材质与采样状态 |
| `CMSHaptics.kt` | `CMSHaptics`、`CMSHapticsProvider`、`CMSHapticKind`、`rememberCMSHaptic` | **统一震动接口** |
| `CMSBackHistory.kt` | `CMSBackHistory`、`CMSBackDestination`、`LocalCMSBackHistory` | 返回历史接口 |

### 可选组件（`library/optional/`）

| 文件 | 导出 | 依赖 |
| --- | --- | --- |
| `CMPlayerBackground.kt` | `CMSPlayerBackground` | `coil-compose`、宿主 `PlayerViewModel`、`PerformanceGovernor` |

---

## 三、快速开始

统一入口是 `CMS` 门面对象，一次 `Provide` 就能设定全局深浅色、强调色、玻璃材质与 Tab 形态，
单个组件仍可独立覆盖：

```kotlin
import com.lindicra.cms.CMS
import com.lindicra.cms.CMSSettings

CMS.Appearance.Provide(CMSSettings(darkTheme = true, tabForm = CMSTabForm.Floating)) {
    CMS.Appearance.Theme {
        Box(Modifier.fillMaxSize()) {
            CMS.Content.Source(Modifier.fillMaxSize(), onSourceReady = { source = it }) {
                PageContent()
            }
            CMS.Navigation.TabView(items, selected, { selected = it },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                samplingSource = source)
        }
    }
}
```

详见 [`docs/06-统一入口与全局配置.md`](docs/06-统一入口与全局配置.md)。

推荐以库模块接入，完整步骤见 [集成指南](docs/03-集成指南.md)。完整分页页面使用 `CMS.Content.Pager`，内部直接放 `CMS.Content.Page` 或一个主 `ScrollView`，自动连接每页滚动和统一采样。默认 `respectConsumedGestures = false`，支持按下打断、按住换向与抬手后的新方向判断。

| 需求 | 推荐入口 | 文档 |
| --- | --- | --- |
| 固定顶栏与滚动 | CMS.Content.Page | [Page](docs/components/Page.md) |
| 完整页面分页 | CMS.Content.Pager | [触控优先级](docs/components/TouchPriority.md) |
| 表格 / 代码局部横滑 | CMS.Content.HorizontalScrollView / cmsHorizontalScrollTouchPriority | [触控优先级](docs/components/TouchPriority.md) |
| 页面内分类 | CapsuleNavigation / TabNavigation | [胶囊导航](docs/components/CapsuleNavigation.md) |
| 搜索与取消融合 | CMS.Interaction.GlassSearch | [玻璃融合](docs/components/GlassFusion.md) |
| 长按查看历史与跨级返回 | CMS.Interaction.BackHistory | [返回历史](docs/components/BackHistory.md) |

独立示例软件的构建与操作说明见 [`example/README.md`](example/README.md)。
宿主以模块接入时依赖 `:cms-core`，它自动带入主题、字体、VIBRATE 权限、JNI 与依赖模块。
源码复制方式如下：

1. 复制源码：`library/cms/com/lindicra/cms/` → 宿主 `com/lindicra/cms/`；`library/theme/com/lindicra/cms/ui/theme/` → 宿主同路径。
2. 复制资源：`library/res/font/*.ttf`、`library/res/drawable/chevron_left.xml`。
3. 复制模块：`library/modules/*` → 宿主根目录，按 `gradle/settings-snippet.gradle.kts` 执行 `include`。
4. 复制原生引擎：`library/native/jniLibs/*` → `app/src/main/jniLibs/`，桥接源码 → `app/src/main/cpp/`。
5. 补齐依赖（见下节），用 `CMSTheme` 或自己的主题提供 Local。

完整步骤见 [`docs/03-集成指南.md`](docs/03-集成指南.md)。

---

## 四、宿主需补齐的依赖

```kotlin
implementation(platform("androidx.compose:compose-bom:2025.04.01"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.foundation:foundation")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.animation:animation")
implementation("androidx.compose.material:material-icons-extended")
implementation("androidx.core:core-ktx:1.16.0")
implementation("androidx.activity:activity-compose:1.10.1")
implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
implementation("dev.chrisbanes.haze:haze:1.7.1")

implementation(project(":liquidglass"))
implementation(project(":continuous-rounded-corner"))
implementation(project(":shared-elements"))
```

`gradle/libs.versions.toml` 已包含上述别名。

---

## 五、1.1.0 更新摘要

- 交互更新：`CMSPopupMenu`（屏下 1/3 向上弹、触摸接管、退场保留内容）、
  `CMSScrollView`（Lazy 网格、拖拽桥、采样开关）、`CMSSwitch`；
- 新增 `CMSGlassButton` / `CMSBackButton` / `CMSGlassContentSource` /
  菜单触摸与弹性物理接口；
- 统一中英文名称与 CMS 前缀，旧公开函数保留兼容及 IDE 迁移提示；
- liquidglass 新增融合层、融球层、共享高光与新 `LiquidGlassView`；
- 新增统一震动接口 `CMSHaptics` 与返回历史接口 `CMSBackHistory`；
- 主题补齐 CLEAR Design 色板与 `GlassMaterial`；
- haze 升级到 1.7.1（移除 `haze-blur` 依赖）；
- `library/optional/CMPlayerBackground.kt` 移出默认编译范围。
- 新增 `cms-core` 与 `example`，可以脱离宿主独立构建；
- 返回历史菜单显式固定页面采样源，统一跨窗口坐标与绘制监听。
- 公开调用面改为 iOS / SwiftUI 式分类：`CMS.Appearance / Navigation / Controls / Content / Presentation`，并删除旧的 `CMS.*` 平铺名字。
- `CMS.Navigation.TabView` 支持 `placement`（Bottom/Top）与 `style`（Edge/Floating/Rail），底栏不再只有一种形态。
- 设置列表提供连续圆角、可配置内边距与分隔线，关闭 `includeFontPadding`、去掉系统波纹、菜单值为胶囊。
- 液态镜片改为“透明镜体 + 边缘折射”：只有边缘一圈参与折射，含色散、随抬起渐入的模糊与阻尼跟手。

1.0 文档与行为保留在 [`docs/v1.0/`](docs/v1.0/)。

---

## 六、注意事项

- **包名固定**：`com.lindicra.cms`；`cms-core` 内部包含字体资源，模块接入不必改宿主 namespace。
- **采样安全**：玻璃控件不得位于自己的采样源内，否则 `LiquidGlassView.bind()` 会拒绝绑定。
- **原生库**：Android 运行时需要 `libCLEARMotionPro.so` + 编译出的 `libclearmotion_jni.so`。
- **16KB 页对齐**：JNI 以 `max-page-size=16384` 构建；随附 arm64 引擎的 LOAD 对齐为 `0x4000`。
- **许可**：本项目为 Apache-2.0；第三方组件、字体与引擎二进制的许可见 [`NOTICE.md`](NOTICE.md)，`library/modules/*` 各自带 `LICENSE`。

---

## 七、文档索引

- [`docs/01-组件手册.md`](docs/01-组件手册.md) — 1.1.0 全部组件 API 与用法
- [`docs/02-CLEAR-Motion.md`](docs/02-CLEAR-Motion.md) — 原生引擎、JNI 与集成
- [`docs/03-集成指南.md`](docs/03-集成指南.md) — 从零接入到可编译
- [`docs/04-触感与宿主接口.md`](docs/04-触感与宿主接口.md) — **统一震动 `CMSHaptics`、返回历史 `CMSBackHistory`**
- [`docs/05-组件命名.md`](docs/05-组件命名.md) — 中英文名称与接口迁移
- [`docs/06-统一入口与全局配置.md`](docs/06-统一入口与全局配置.md) — **`CMS` 门面、`CMSSettings` 全局默认、Tab 三种形态**
- [`docs/v1.0/`](docs/v1.0/) — 1.0 历史文档

---

## 八、许可与第三方组件

- 本项目：Apache License 2.0，见 [`LICENSE`](LICENSE)。
- 第三方组件、字体与引擎二进制的完整清单与许可：[`NOTICE.md`](NOTICE.md)。
- HarmonyOS Sans 字体随整体软件分发，未经修改，许可文本见 `library/res/font/LICENSE-HarmonyOS-Sans.txt`。

---

© 2026 芜湖灵动亿创网络科技有限公司 (Wuhu Lindicra Network Technology Co., Ltd)

本项目的法律主体以上述中文名称为准。