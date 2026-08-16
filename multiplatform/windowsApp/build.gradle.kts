import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

group = "team.bjtuss.bjtuselfservice"
version = "0.1.0"

kotlin {
    jvm("windows") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val windowsMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(libs.compose.desktop.windows.x64)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.jna)
                implementation(libs.jna.platform)
                implementation(libs.ktor.client.cio)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.djl.pytorch.engine)
                implementation(libs.djl.pytorch.native.win)
            }
        }
        val windowsTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

compose.desktop {
    application {
        mainClass = "team.bjtuss.bjtuselfservice.windows.MainKt"
        // jpackage 只在完整 JDK 有，Android Studio/PyCharm JBR 没有；打包显式指向本机完整 JDK。
        javaHome = providers.environmentVariable("WINDOWS_PACKAGE_JAVA_HOME")
            .orElse("C:/Users/zjg/jdk21/jdk-21.0.8+9")
            .get()
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            modules("java.sql")
            // Windows 桌面快捷方式、开始菜单项、「应用和功能」名称都走 packageName。
            // 与 macOS 不同：这边不必为了可执行文件名锁 ASCII，可以直接用中文显示名。
            packageName = "交大自由行 KMP"
            packageVersion = "1.7.3"
            description = "交大自由行 Kotlin Multiplatform Windows 应用"
            vendor = "BJTUselfService Contributors"
            windows {
                // 按用户安装：装到 %LOCALAPPDATA%\Programs，免 UAC 弹窗（未签名安装器提权弹窗会在后台闪烁，
                // 容易被误认为安装卡死）。桌面快捷方式、开始菜单项仍照常生成（用户级）。
                perUserInstall = true
                menu = true
                shortcut = true
                menuGroup = "交大自由行 KMP"
                iconFile.set(project.file("src/windowsMain/resources/BJTUselfServiceKMP.ico"))
            }
        }
    }
}
