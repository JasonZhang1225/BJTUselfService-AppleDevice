package team.bjtuss.bjtuselfservice.windows

import ai.djl.modality.Input
import ai.djl.modality.Output
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import ai.djl.ndarray.types.Shape
import ai.djl.repository.zoo.Criteria
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognitionFailure
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognitionResult
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognizer
import team.bjtuss.bjtuselfservice.shared.auth.DEFAULT_AUTO_CAPTCHA_CONFIDENCE
import team.bjtuss.bjtuselfservice.shared.auth.decodeCaptchaLogits

private const val MODEL_RESOURCE = "/BJTUCaptcha.pt"
private const val MODEL_CACHE_FILE = "bjtu-captcha-v1.pt"
private const val CAPTCHA_WIDTH = 130
private const val CAPTCHA_HEIGHT = 42

/**
 * Windows 验证码识别：与原版 Android PyTorch 实现使用**同一个**
 * `BJTUCaptcha.pt` 冻结图，推理经 DJL PyTorch 引擎（libtorch CPU）。
 *
 * 输入语义与 Android 完全一致：130×42 缩放（双线性）、RGB 通道、
 * CHW 顺序、`[0,1]` 归一化；输出 `8×1×15` logits 走共享
 * `decodeCaptchaLogits`（同一字符表 / CTC 折叠 / 置信度门槛）。
 */
class WindowsTorchCaptchaRecognizer(
    modelFile: File? = null,
    private val minimumConfidence: Float = DEFAULT_AUTO_CAPTCHA_CONFIDENCE,
) : CaptchaRecognizer {
    private val modelFilePath: String? = modelFile?.absolutePath
        ?: System.getProperty(MODEL_PROPERTY)?.let { File(it).absolutePath }
    private val closed = AtomicBoolean(false)
    private val modelLock = Any()
    @Volatile
    private var modelHolder: ModelHolder? = null

    override suspend fun recognize(imageBytes: ByteArray): CaptchaRecognitionResult =
        withContext(Dispatchers.Default) {
            if (closed.get()) return@withContext CaptchaRecognitionResult.Failed(
                CaptchaRecognitionFailure.MODEL_UNAVAILABLE,
            )
            val holder = synchronized(modelLock) {
                modelHolder ?: runCatching {
                    loadModel().also { modelHolder = it }
                }.getOrElse {
                    return@withContext CaptchaRecognitionResult.Failed(
                        CaptchaRecognitionFailure.MODEL_UNAVAILABLE,
                    )
                }
            }

            runCatching {
                val tensorData = preprocess(imageBytes)
                    ?: return@withContext CaptchaRecognitionResult.Failed(
                        CaptchaRecognitionFailure.IMAGE_DECODE_FAILED,
                    )
                val outputShape: Shape
                val outputLogits: FloatArray
                // 每次推理用独立子 manager：predictor 输出依附于输入 NDArray 的 manager，
                // 关闭子 manager 不会影响 holder 的 base manager（predictor 生命周期跟随它）。
                holder.manager.newSubManager().use { manager ->
                    val input = manager.create(
                        tensorData,
                        Shape(1, 3, CAPTCHA_HEIGHT.toLong(), CAPTCHA_WIDTH.toLong()),
                    )
                    val output = holder.predictor.predict(NDList(input)).get(0)
                    outputShape = output.shape
                    // 必须在 manager 关闭前读取数据；predictor 输出 NDArray 依附于该 manager。
                    outputLogits = output.toFloatArray()
                }
                if (outputShape != Shape(8, 1, 15)) {
                    CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INVALID_OUTPUT)
                } else {
                    decodeCaptchaLogits(outputLogits, minimumConfidence)
                }
            }.getOrElse {
                CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INFERENCE_FAILED)
            }
        }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            modelHolder?.close()
            modelHolder = null
        }
    }

    /** 诊断用：返回原始 8×15 logits（与 Python torch 参考逐值比较）。 */
    fun recognizeRawLogits(imageBytes: ByteArray): FloatArray? =
        runCatching {
            val holder = modelHolder ?: loadModel().also { modelHolder = it }
            val tensorData = preprocess(imageBytes) ?: return null
            holder.manager.newSubManager().use { manager ->
                val input = manager.create(
                    tensorData,
                    Shape(1, 3, CAPTCHA_HEIGHT.toLong(), CAPTCHA_WIDTH.toLong()),
                )
                holder.predictor.predict(NDList(input)).get(0).toFloatArray()
            }
        }.getOrNull()

    private fun loadModel(): ModelHolder {
        val modelFile = modelFilePath?.let(::File)
            ?: extractModelFromResources()
        if (modelFile == null || !modelFile.isFile) {
            throw IllegalStateException("验证码模型不可用：${modelFile?.absolutePath}")
        }
        val criteria = Criteria.builder()
            .setTypes(NDList::class.java, NDList::class.java)
            .optModelPath(modelFile.toPath())
            .optEngine("PyTorch")
            .build()
        val model = criteria.loadModel()
        val predictor = model.newPredictor()
        return ModelHolder(model = model, predictor = predictor, manager = NDManager.newBaseManager())
    }

    private fun extractModelFromResources(): File? {
        val resource = javaClass.getResourceAsStream(MODEL_RESOURCE) ?: return null
        val cacheDirectory = File(
            System.getProperty("java.io.tmpdir"),
            "bjtu-kmp-captcha",
        ).apply { mkdirs() }
        val target = File(cacheDirectory, MODEL_CACHE_FILE)
        if (target.isFile && target.length() > 0L) return target
        val temporary = File(cacheDirectory, "$MODEL_CACHE_FILE.tmp")
        try {
            resource.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            return target.takeIf { it.isFile }
        } catch (_: IOException) {
            temporary.delete()
            return null
        }
    }

    private fun preprocess(imageBytes: ByteArray): FloatArray? {
        val decoded = runCatching { ImageIO.read(imageBytes.inputStream()) }.getOrNull() ?: return null
        val scaled = BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB).also { target ->
            val graphics = target.createGraphics()
            try {
                graphics.drawImage(decoded, 0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT, null)
            } finally {
                graphics.dispose()
            }
        }
        val pixels = IntArray(CAPTCHA_WIDTH * CAPTCHA_HEIGHT)
        scaled.getRGB(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT, pixels, 0, CAPTCHA_WIDTH)
        return FloatArray(3 * CAPTCHA_WIDTH * CAPTCHA_HEIGHT).also { output ->
            val plane = CAPTCHA_WIDTH * CAPTCHA_HEIGHT
            pixels.forEachIndexed { index, pixel ->
                output[index] = ((pixel ushr 16) and 0xFF) / 255f
                output[plane + index] = ((pixel ushr 8) and 0xFF) / 255f
                output[2 * plane + index] = (pixel and 0xFF) / 255f
            }
        }
    }

    private class ModelHolder(
        val model: ai.djl.Model,
        val predictor: ai.djl.inference.Predictor<NDList, NDList>,
        val manager: NDManager,
    ) {
        fun close() {
            runCatching { predictor.close() }
            runCatching { model.close() }
            runCatching { manager.close() }
        }
    }

    private companion object {
        const val MODEL_PROPERTY = "bjtu.captcha.model"
    }
}
