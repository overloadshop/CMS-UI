pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}
rootProject.name = "AndroidCMS"
include(":cms-core", ":example", ":liquidglass", ":continuous-rounded-corner", ":shared-elements")
project(":liquidglass").projectDir = file("modules/liquidglass")
project(":continuous-rounded-corner").projectDir = file("modules/continuous-rounded-corner")
project(":shared-elements").projectDir = file("modules/shared-elements")
