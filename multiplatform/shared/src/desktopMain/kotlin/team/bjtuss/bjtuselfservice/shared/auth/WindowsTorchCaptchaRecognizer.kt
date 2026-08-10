package team.bjtuss.bjtuselfservice.shared.auth

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Windows 对原版 TorchScript `model.pt` 的兼容桥接。
 *
 * 首版不下载或静默安装 Python。它依次探测 `py -3` 和 `python`，要求环境已安装
 * PyTorch 与 Pillow；任何缺失或推理错误都安全回退为手动验证码。
 */
class WindowsTorchCaptchaRecognizer(
    private val minimumConfidence: Float = DEFAULT_AUTO_CAPTCHA_CONFIDENCE,
) : CaptchaRecognizer {
    override suspend fun recognize(imageBytes: ByteArray): CaptchaRecognitionResult =
        withContext(Dispatchers.IO) {
            val model = locateModel() ?: return@withContext unavailable()
            val script = extractHelper() ?: return@withContext unavailable()
            val command = findPython(script) ?: return@withContext unavailable()
            runCatching {
                val process = ProcessBuilder(command + listOf(script.absolutePath, model.absolutePath))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                process.outputStream.use { it.write(imageBytes) }
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@withContext failed()
                }
                if (process.exitValue() != 0) return@withContext failed()
                val logits = process.inputStream.bufferedReader().use { it.readText() }
                    .split(',').mapNotNull(String::toFloatOrNull).toFloatArray()
                decodeCaptchaLogits(logits, minimumConfidence)
            }.getOrElse { failed() }
        }

    private fun locateModel(): File? {
        System.getProperty("bjtu.captcha.torch.model")?.let(::File)?.takeIf(File::isFile)?.let {
            return it
        }
        val resource = javaClass.getResourceAsStream("/captcha/BJTUCaptcha.pt") ?: return null
        return File(System.getProperty("java.io.tmpdir"), "bjtu-captcha-v1.pt").also { target ->
            if (!target.isFile || target.length() == 0L) resource.use { it.copyTo(target.outputStream()) }
            else resource.close()
        }
    }

    private fun extractHelper(): File? = runCatching {
        val target = File(System.getProperty("java.io.tmpdir"), "bjtu-captcha-torch.py")
        javaClass.getResourceAsStream("/captcha/windows_torch_infer.py")!!.use { input ->
            input.copyTo(target.outputStream())
        }
        target
    }.getOrNull()

    private fun findPython(script: File): List<String>? = listOf(
        listOf("py", "-3"), listOf("python"),
    ).firstOrNull { prefix ->
        runCatching {
            ProcessBuilder(prefix + listOf(script.absolutePath, "--probe"))
                .redirectErrorStream(true).start().run { waitFor(5, TimeUnit.SECONDS) && exitValue() == 0 }
        }.getOrDefault(false)
    }

    private fun unavailable() = CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.MODEL_UNAVAILABLE)
    private fun failed() = CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INFERENCE_FAILED)
}
