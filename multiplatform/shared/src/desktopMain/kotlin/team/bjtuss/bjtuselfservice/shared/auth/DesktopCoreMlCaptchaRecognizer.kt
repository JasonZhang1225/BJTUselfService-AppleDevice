package team.bjtuss.bjtuselfservice.shared.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private const val HELPER_PROPERTY = "bjtu.captcha.helper"
private const val MODEL_PROPERTY = "bjtu.captcha.model"

class DesktopCoreMlCaptchaRecognizer(
    private val minimumConfidence: Float = DEFAULT_AUTO_CAPTCHA_CONFIDENCE,
) : CaptchaRecognizer {
    override suspend fun recognize(imageBytes: ByteArray): CaptchaRecognitionResult =
        withContext(Dispatchers.IO) {
            val runtime = locateRuntime()
                ?: return@withContext CaptchaRecognitionResult.Failed(
                    CaptchaRecognitionFailure.MODEL_UNAVAILABLE,
                )
            runCatching {
                val process = ProcessBuilder(
                    runtime.helper.absolutePath,
                    runtime.model.absolutePath,
                ).start()
                process.outputStream.use { it.write(imageBytes) }
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@withContext CaptchaRecognitionResult.Failed(
                        CaptchaRecognitionFailure.INFERENCE_FAILED,
                    )
                }
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (process.exitValue() != 0) {
                    return@withContext CaptchaRecognitionResult.Failed(
                        CaptchaRecognitionFailure.INFERENCE_FAILED,
                    )
                }
                val values = output.split(',').mapNotNull(String::toFloatOrNull).toFloatArray()
                decodeCaptchaLogits(values, minimumConfidence)
            }.getOrElse {
                CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INFERENCE_FAILED)
            }
    }

    private fun locateRuntime(): RuntimePaths? {
        val executable = ProcessHandle.current().info().command().orElse(null)?.let(::File)
        val contents = executable?.parentFile?.parentFile
        val captchaResources = File(contents, "Resources/Captcha")
        val bundledHelper = File(captchaResources, "BJTUCaptchaHelper")
        val bundledModel = File(captchaResources, "BJTUCaptcha.mlmodelc")
        // 安装包优先使用自己的资源，避免开发机构建目录被打包配置带进发布运行时。
        if (bundledHelper.canExecute() && bundledModel.isDirectory) {
            return RuntimePaths(bundledHelper, bundledModel)
        }

        // `desktopApp:run` 没有 bundle Resources，开发运行才使用注入路径。
        val configuredHelper = System.getProperty(HELPER_PROPERTY)?.let(::File)
        val configuredModel = System.getProperty(MODEL_PROPERTY)?.let(::File)
        return if (configuredHelper?.canExecute() == true && configuredModel?.isDirectory == true) {
            RuntimePaths(configuredHelper, configuredModel)
        } else {
            null
        }
    }

    private data class RuntimePaths(val helper: File, val model: File)
}
