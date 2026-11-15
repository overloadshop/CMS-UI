// ─────────────────────────────────────────────────────────────
// CMS-UI 1.1.0 集成片段 —— 追加到宿主工程的 settings.gradle.kts 末尾
//
// 前置：完整 CMS-UI 文件夹与宿主 app 模块同级。
// 宿主 version catalog 需要合并本包 gradle/libs.versions.toml 中的别名。
// ─────────────────────────────────────────────────────────────

include(":cms-core")
include(":liquidglass")
include(":continuous-rounded-corner")
include(":shared-elements")
project(":cms-core").projectDir = file("CMS-UI/library/cms-core")
project(":liquidglass").projectDir = file("CMS-UI/library/modules/liquidglass")
project(":continuous-rounded-corner").projectDir = file("CMS-UI/library/modules/continuous-rounded-corner")
project(":shared-elements").projectDir = file("CMS-UI/library/modules/shared-elements")

// 说明：
// 1) 三个模块的 build.gradle.kts 使用 version catalog 别名
//    （libs.plugins.kotlin.android / libs.androidx.compose.bom 等），
//    宿主工程需具备 gradle/libs.versions.toml；推荐直接使用本包
//    gradle/libs.versions.toml（1.1.0 对应 Kotlin 2.3.21 / Compose BOM 2025.04.01）。
// 2) 若宿主工程已有同名模块，请合并而不是重复 include。
// 3) 宿主 dependencies { implementation(project(":cms-core")) }。
//    组件、主题、资源与原生库由 cms-core 统一引入；
//    不要同时编译宿主中的同名 CMS 源码副本。
// 4) optional/CMPlayerBackground.kt 为可选组件，依赖 coil 与宿主播放器类，
//    不复制也能正常使用其余组件。
