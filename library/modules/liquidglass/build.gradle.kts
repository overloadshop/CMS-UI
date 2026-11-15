plugins {
    id("com.android.library")
}

android {
    namespace = "com.qmdeve.liquidglass"
    compileSdk = 36

    defaultConfig {
        // 宿主 minSdk 23：真实渲染在 API 33+ 由 LiquidGlass 内部按版本启用
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.annotation:annotation-jvm:1.10.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
}
