// GitHub-hosted runners often get 502 from maven.aliyun.com.
// Drop Aliyun from this CI Gradle process and use Google / Maven Central.
settingsEvaluated {
    pluginManagement.repositories.apply {
        clear()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencyResolutionManagement.repositories.apply {
        clear()
        maven { url = uri("${rootDir}/local-maven") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
