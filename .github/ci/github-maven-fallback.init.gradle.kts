// GitHub-hosted runners often get 502 from maven.aliyun.com. The frozen
// Android settings still prefer Aliyun for China; this init script only
// adds Google / Maven Central so CI can finish when Aliyun is down.
beforeSettings {
    pluginManagement.repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

settingsEvaluated {
    dependencyResolutionManagement.repositories {
        google()
        mavenCentral()
    }
}
