plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.adamglin.composecontinuousroundedcornershape"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.3.21")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.21")
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation("androidx.graphics:graphics-shapes:1.1.0")
    implementation("androidx.collection:collection:1.5.0")
    implementation("androidx.annotation:annotation:1.9.1")
}
