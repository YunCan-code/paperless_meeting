pluginManagement {
    repositories {
        // 1. 优先用阿里云
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 2. 优先用阿里云
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // 3. JitPack 还是要加，用来下 Barteksc 的库 (或者其他第三方库)
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
}

rootProject.name = "PaperlessMeeting"
include(":app")
