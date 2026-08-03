pluginManagement {
    repositories {
        // 国内镜像优先，规避 Google/Maven Central TLS 中断
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 本地缓存的 JitPack 产物，避免访问 jitpack.io
        maven { url = uri("${rootDir}/local-maven") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // 1.7.0 壁纸裁剪：com.github.yalantis:ucrop
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "BJTUselfService"
include(":app")
