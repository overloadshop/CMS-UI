plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.lindicra.cms.example"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.lindicra.cms.example"
        minSdk = 31
        targetSdk = 36
        versionCode = 110
        versionName = "1.1.0"
    }
    buildFeatures { compose = true }
    // Bundle component references and general integration guides with their relative links.
    sourceSets.getByName("main").assets.srcDir("../docs")
    // Optional build check compiles every exported snippet produced by unit tests.
    if (providers.gradleProperty("cms.compileSamples").orNull == "true") {
        sourceSets.getByName("debug").java.srcDir(layout.buildDirectory.dir("generated/previewSamples"))
    }
    testOptions.unitTests.all {
        it.systemProperty("cms.fixtureDirectory",
            layout.buildDirectory.dir("generated/previewSamples").get().asFile.absolutePath)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(project(":cms-core"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
