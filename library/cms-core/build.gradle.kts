plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.lindicra.cms"
    compileSdk = 36
    ndkVersion = "27.0.12077973"
    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
    }
    sourceSets.getByName("main") {
        // 只编译 1.1.0 的 com.lindicra.cms 包；1.0 的 ldyc.qy.music 源码已移到 legacy-v1.0/。
        java.srcDirs("../cms/com", "../theme/com")
        res.srcDir("../res")
        jniLibs.srcDir("../native/jniLibs")
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("../native/CMakeLists.txt")
            version = "3.22.1"
            buildStagingDirectory = file("${System.getProperty("java.io.tmpdir")}/CMS-UI-native")
        }
    }
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    api(libs.haze)
    api(project(":liquidglass"))
    api(project(":continuous-rounded-corner"))
    api(project(":shared-elements"))
}
