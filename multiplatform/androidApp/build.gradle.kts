import java.io.File
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

    // One upload keystore for local and CI, so APKs can overlay-install.
    val uploadKeystore = File(
        providers.environmentVariable("BJTU_ANDROID_KEYSTORE")
            .orElse(
                providers.provider {
                    File(System.getProperty("user.home"), ".android/bjtu-kmp-upload.keystore")
                        .absolutePath
                },
            )
            .get(),
    )
    require(uploadKeystore.isFile) {
        "Missing Android upload keystore at ${uploadKeystore.absolutePath}. " +
            "Copy the shared key to that path, or set BJTU_ANDROID_KEYSTORE."
    }
    fun envOrDefault(name: String, default: String): String {
        val value = providers.environmentVariable(name).orNull
        return if (value.isNullOrBlank()) default else value
    }
    val uploadStorePassword = envOrDefault("BJTU_ANDROID_STORE_PASSWORD", "android")
    val uploadKeyAlias = envOrDefault("BJTU_ANDROID_KEY_ALIAS", "androiddebugkey")
    val uploadKeyPassword = envOrDefault("BJTU_ANDROID_KEY_PASSWORD", "android")

    signingConfigs {
        create("shared") {
            storeFile = uploadKeystore
            storePassword = uploadStorePassword
            keyAlias = uploadKeyAlias
            keyPassword = uploadKeyPassword
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    // Align with the original author's v1.7.0 APK: do not bundle
    // PyTorch native libraries for unrelated CPU architectures.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
        }
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
