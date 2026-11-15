plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// 中间产物放系统临时目录；源码包保持可直接复制。
allprojects {
    layout.buildDirectory.set(
        file("${System.getProperty("java.io.tmpdir")}/AndroidCMS-build/${project.name}")
    )
}
