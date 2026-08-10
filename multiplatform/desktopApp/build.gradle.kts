import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Invokes Apple's Core ML compiler")
abstract class CompileMacCaptchaModel : DefaultTask() {
    @get:InputDirectory
    abstract val sourceModel: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun compile() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        execOperations.exec {
            commandLine(
                "xcrun",
                "coremlcompiler",
                "compile",
                sourceModel.get().asFile.absolutePath,
                output.absolutePath,
                "--platform",
                "macOS",
                "--deployment-target",
                "12.0",
            )
        }.assertNormalExitValue()
    }
}

@DisableCachingByDefault(because = "Invokes the Swift compiler")
abstract class CompileMacCaptchaHelper : DefaultTask() {
    @get:InputFile
    abstract val swiftSource: RegularFileProperty

    @get:OutputFile
    abstract val executable: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun compile() {
        val output = executable.get().asFile
        output.parentFile.mkdirs()
        execOperations.exec {
            commandLine(
                "xcrun",
                "swiftc",
                "-O",
                "-parse-as-library",
                "-target",
                "arm64-apple-macos12.0",
                "-framework",
                "CoreML",
                "-framework",
                "CoreGraphics",
                "-framework",
                "ImageIO",
                "-framework",
                "Foundation",
                swiftSource.get().asFile.absolutePath,
                "-o",
                output.absolutePath,
            )
        }.assertNormalExitValue()
    }
}

@DisableCachingByDefault(because = "Invokes Apple's Clang compiler")
abstract class CompileMacInputSourceHelper : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputFile
    abstract val library: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun compile() {
        val output = library.get().asFile
        output.parentFile.mkdirs()
        execOperations.exec {
            commandLine(
                "xcrun",
                "clang",
                "-O2",
                "-dynamiclib",
                "-fblocks",
                "-fobjc-arc",
                "-target",
                "arm64-apple-macos12.0",
                "-framework",
                "AppKit",
                "-framework",
                "Foundation",
                source.get().asFile.absolutePath,
                "-o",
                output.absolutePath,
            )
        }.assertNormalExitValue()
    }
}

@DisableCachingByDefault(because = "Mutates and re-signs the packaged macOS app bundle")
abstract class FinalizeMacDistributable : DefaultTask() {
    @get:InputFile
    abstract val privacyManifest: RegularFileProperty

    @get:Internal
    abstract val appBundle: DirectoryProperty

    @get:InputFile
    abstract val captchaHelper: RegularFileProperty

    @get:InputDirectory
    abstract val captchaModel: DirectoryProperty

    @get:InputFile
    abstract val inputSourceHelper: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun finalizeBundle() {
        val bundle = appBundle.get().asFile
        val resourcesDirectory = bundle.resolve("Contents/Resources")
        resourcesDirectory.mkdirs()
        privacyManifest.get().asFile.copyTo(
            resourcesDirectory.resolve("PrivacyInfo.xcprivacy"),
            overwrite = true,
        )
        val captchaDirectory = resourcesDirectory.resolve("Captcha")
        captchaDirectory.deleteRecursively()
        captchaDirectory.mkdirs()
        val helperTarget = captchaDirectory.resolve("BJTUCaptchaHelper")
        captchaHelper.get().asFile.copyTo(helperTarget, overwrite = true)
        helperTarget.setExecutable(true, false)
        captchaModel.get().asFile.copyRecursively(
            captchaDirectory.resolve("BJTUCaptcha.mlmodelc"),
            overwrite = true,
        )
        val inputSourceDirectory = resourcesDirectory.resolve("InputSource")
        inputSourceDirectory.deleteRecursively()
        inputSourceDirectory.mkdirs()
        inputSourceHelper.get().asFile.copyTo(
            inputSourceDirectory.resolve("libBJTUInputSourceHelper.dylib"),
            overwrite = true,
        )
        // App Store 导出合规：仅使用系统 HTTPS/Keychain，无非豁免加密。
        // createDistributable 可能为 UP-TO-DATE，因此收尾任务必须可重复执行。
        val encryptionKey = ":ITSAppUsesNonExemptEncryption"
        val infoPlist = bundle.resolve("Contents/Info.plist").absolutePath
        fun setOrAddPlistString(key: String, value: String) {
            val setResult = execOperations.exec {
                commandLine(
                    "/usr/libexec/PlistBuddy",
                    "-c",
                    "Set :$key $value",
                    infoPlist,
                )
                isIgnoreExitValue = true
            }
            if (setResult.exitValue != 0) {
                execOperations.exec {
                    commandLine(
                        "/usr/libexec/PlistBuddy",
                        "-c",
                        "Add :$key string $value",
                        infoPlist,
                    )
                }.assertNormalExitValue()
            }
        }
        fun setOrAddPlistBool(key: String, value: Boolean) {
            val setResult = execOperations.exec {
                commandLine(
                    "/usr/libexec/PlistBuddy",
                    "-c",
                    "Set :$key $value",
                    infoPlist,
                )
                isIgnoreExitValue = true
            }
            if (setResult.exitValue != 0) {
                execOperations.exec {
                    commandLine(
                        "/usr/libexec/PlistBuddy",
                        "-c",
                        "Add :$key bool $value",
                        infoPlist,
                    )
                }.assertNormalExitValue()
            }
        }
        // 菜单栏应用名 / Launchpad / 某些系统对话框用这两个键；packageName 保持英文文件名。
        setOrAddPlistString("CFBundleName", "交大自由行 KMP")
        setOrAddPlistString("CFBundleDisplayName", "交大自由行 KMP")
        setOrAddPlistBool("ITSAppUsesNonExemptEncryption", false)

        execOperations.exec {
            commandLine(
                "codesign",
                "--force",
                "--deep",
                "--sign",
                "-",
                bundle.absolutePath,
            )
        }.assertNormalExitValue()
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)
}

compose.desktop {
    application {
        mainClass = "team.bjtuss.bjtuselfservice.desktop.MainKt"
        val captchaBuildDirectory = layout.buildDirectory.dir("generated/captcha")
        val inputSourceHelperFile =
            layout.buildDirectory.file("generated/input-source/libBJTUInputSourceHelper.dylib")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            jvmArgs += listOf(
                "--enable-native-access=ALL-UNNAMED",
                "-Dbjtu.captcha.helper=${captchaBuildDirectory.get().file("BJTUCaptchaHelper").asFile.absolutePath}",
                "-Dbjtu.captcha.model=${captchaBuildDirectory.get().dir("model/BJTUCaptcha.mlmodelc").asFile.absolutePath}",
                "-Dbjtu.input-source.helper=${inputSourceHelperFile.get().asFile.absolutePath}",
            )
        }

        nativeDistributions {
            val hostOs = org.gradle.internal.os.OperatingSystem.current()
            when {
                hostOs.isWindows -> targetFormats(TargetFormat.Msi, TargetFormat.Exe)
                hostOs.isMacOsX -> targetFormats(TargetFormat.Dmg)
                else -> targetFormats(TargetFormat.Deb)
            }
            modules("java.sql")
            packageName = "BJTUselfServiceKMP"
            // Compose Desktop 的 packageVersion 仅允许数字点号；展示名/Release 用 1.7.1-KMP。
            packageVersion = "1.7.1"
            description = "交大自由行 Kotlin Multiplatform macOS 应用"
            vendor = "BJTUselfService Contributors"
            macOS {
                iconFile.set(project.file("src/main/resources/BJTUselfServiceKMP-v2.icns"))
                bundleID = "team.bjtuss.bjtuselfservice.kmp.macos"
                // 菜单栏 / Dock / About·Hide·Quit 显示名；packageName 仍用英文，保证 .app/.dmg 文件名稳定。
                dockName = "交大自由行 KMP"
                appCategory = "public.app-category.education"
                minimumSystemVersion = "12.0"
                packageBuildVersion = "1"
            }
            windows {
                menuGroup = "交大自由行 KMP"
                upgradeUuid = "67732ddd-7d37-44a0-ae80-909c7b9f11c9"
            }
        }
    }
}


val macPrivacyManifest = layout.projectDirectory.file(
    "src/main/appResources/macos/PrivacyInfo.xcprivacy",
)
val captchaSourceModel = project.file("../iosApp/iosApp/BJTUCaptcha.mlpackage")
val captchaSwiftSource = project.file("src/main/swift/CaptchaCoreMLHelper.swift")
val inputSourceHelperSource = project.file("src/main/native/InputSourceHelper.m")
val captchaBuildDirectory = layout.buildDirectory.dir("generated/captcha")
val captchaHelperFile = captchaBuildDirectory.map { it.file("BJTUCaptchaHelper") }
val captchaModelDirectory = captchaBuildDirectory.map { it.dir("model/BJTUCaptcha.mlmodelc") }
val inputSourceHelperFile =
    layout.buildDirectory.file("generated/input-source/libBJTUInputSourceHelper.dylib")

val compileMacCaptchaModel by tasks.registering(CompileMacCaptchaModel::class) {
    sourceModel.set(captchaSourceModel)
    outputDirectory.set(captchaBuildDirectory.map { it.dir("model") })
}

val compileMacCaptchaHelper by tasks.registering(CompileMacCaptchaHelper::class) {
    swiftSource.set(captchaSwiftSource)
    executable.set(captchaHelperFile)
}

val compileMacInputSourceHelper by tasks.registering(CompileMacInputSourceHelper::class) {
    source.set(inputSourceHelperSource)
    library.set(inputSourceHelperFile)
}

// Compose Desktop's appResourcesRootDir is a JVM runtime resource directory
// (Contents/app/resources), not Apple's required Contents/Resources location.
// Until formal Developer ID signing is configured, finish the local app image by
// copying the manifest to the bundle root and renewing its existing ad-hoc seal.
val finalizeMacDistributable by tasks.registering(FinalizeMacDistributable::class) {
    dependsOn(compileMacCaptchaModel, compileMacCaptchaHelper, compileMacInputSourceHelper)
    privacyManifest.set(macPrivacyManifest)
    captchaHelper.set(captchaHelperFile)
    captchaModel.set(captchaModelDirectory)
    inputSourceHelper.set(inputSourceHelperFile)
    appBundle.set(
        layout.buildDirectory.dir("compose/binaries/main/app/BJTUselfServiceKMP.app"),
    )
}

tasks.matching { it.name == "createDistributable" }.configureEach {
    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) finalizedBy(finalizeMacDistributable)
}

// packageDmg 只 dependsOn createDistributable，不保证等 finalizedBy 跑完；显式挂上，避免 Info.plist 中文名还没写进就打 DMG。
tasks.matching { it.name == "packageDmg" }.configureEach {
    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) dependsOn(finalizeMacDistributable)
}

tasks.matching { it.name == "run" }.configureEach {
    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        dependsOn(compileMacCaptchaModel, compileMacCaptchaHelper, compileMacInputSourceHelper)
    }
}

// Windows 使用原版 TorchScript 模型。当前桥接器优先调用用户机器上的
// Python/PyTorch；环境不可用时登录页会按既有策略回退到手动验证码。
tasks.named<ProcessResources>("processResources") {
    from(project(":androidApp").file("src/main/assets/BJTUCaptcha.pt")) {
        into("captcha")
    }
}
