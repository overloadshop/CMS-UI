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
rootProject.name = "CMS-UI"
include(":cms-core", ":example", ":liquidglass", ":continuous-rounded-corner", ":shared-elements")
project(":cms-core").projectDir = file("library/cms-core")
project(":example").projectDir = file("example")
project(":liquidglass").projectDir = file("library/modules/liquidglass")
project(":continuous-rounded-corner").projectDir = file("library/modules/continuous-rounded-corner")
project(":shared-elements").projectDir = file("library/modules/shared-elements")
