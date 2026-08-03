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
        val setResult = execOperations.exec {
            commandLine(
                "/usr/libexec/PlistBuddy",
                "-c",
                "Set $encryptionKey false",
                infoPlist,
            )
            isIgnoreExitValue = true
        }
        if (setResult.exitValue != 0) {
            execOperations.exec {
                commandLine(
                    "/usr/libexec/PlistBuddy",
                    "-c",
                    "Add $encryptionKey bool false",
                    infoPlist,
                )
            }.assertNormalExitValue()
        }

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
    implementation(libs.compose.desktop.macos.arm64)
    implementation(libs.kotlinx.coroutines.core)
}

compose.desktop {
    application {
        mainClass = "team.bjtuss.bjtuselfservice.desktop.MainKt"
        val captchaBuildDirectory = layout.buildDirectory.dir("generated/captcha")
        val inputSourceHelperFile =
            layout.buildDirectory.file("generated/input-source/libBJTUInputSourceHelper.dylib")
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
            "-Dbjtu.captcha.helper=${captchaBuildDirectory.get().file("BJTUCaptchaHelper").asFile.absolutePath}",
            "-Dbjtu.captcha.model=${captchaBuildDirectory.get().dir("model/BJTUCaptcha.mlmodelc").asFile.absolutePath}",
            "-Dbjtu.input-source.helper=${inputSourceHelperFile.get().asFile.absolutePath}",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            modules("java.sql")
            packageName = "BJTUselfServiceKMP"
            packageVersion = "1.0.0"
            description = "交大自由行 Kotlin Multiplatform macOS 应用"
            vendor = "BJTUselfService Contributors"
            macOS {
                iconFile.set(project.file("src/main/resources/BJTUselfServiceKMP-v2.icns"))
                bundleID = "team.bjtuss.bjtuselfservice.kmp.macos"
                dockName = "交大自由行"
                appCategory = "public.app-category.education"
                minimumSystemVersion = "12.0"
                packageBuildVersion = "1"
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
    finalizedBy(finalizeMacDistributable)
}

tasks.matching { it.name == "run" }.configureEach {
    dependsOn(compileMacCaptchaModel, compileMacCaptchaHelper, compileMacInputSourceHelper)
}
