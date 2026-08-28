import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "team.bjtuss.bjtuselfservice.kmp"
    compileSdk = 36

    defaultConfig {
        applicationId = "team.bjtuss.bjtuselfservice.kmp"
        minSdk = 28
        targetSdk = 35
        versionCode = 15
        versionName = "1.7.4-KMP"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
}
