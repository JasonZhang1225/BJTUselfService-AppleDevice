package team.bjtuss.bjtuselfservice.shared.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream

private const val MODEL_ASSET = "BJTUCaptcha.pt"
private const val MODEL_CACHE_FILE = "bjtu-captcha-v1.pt"
private const val CAPTCHA_WIDTH = 130
private const val CAPTCHA_HEIGHT = 42

class AndroidTorchCaptchaRecognizer(
    context: Context,
    private val minimumConfidence: Float = DEFAULT_AUTO_CAPTCHA_CONFIDENCE,
) : CaptchaRecognizer {
    private val applicationContext = context.applicationContext
    private val module: Module by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Module.load(copyModelAssetToPrivateCache().absolutePath)
    }

    override suspend fun recognize(imageBytes: ByteArray): CaptchaRecognitionResult =
        withContext(Dispatchers.Default) {
            runCatching {
                val tensorData = preprocess(imageBytes)
                    ?: return@withContext CaptchaRecognitionResult.Failed(
                        CaptchaRecognitionFailure.IMAGE_DECODE_FAILED,
                    )
                val output = synchronized(module) {
                    module.forward(
                        IValue.from(
                            Tensor.fromBlob(
                                tensorData,
                                longArrayOf(1, 3, CAPTCHA_HEIGHT.toLong(), CAPTCHA_WIDTH.toLong()),
                            ),
                        ),
                    ).toTensor()
                }
                if (!output.shape().contentEquals(longArrayOf(8, 1, 15))) {
                    CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INVALID_OUTPUT)
                } else {
                    decodeCaptchaLogits(output.dataAsFloatArray, minimumConfidence)
                }
            }.getOrElse {
                CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INFERENCE_FAILED)
            }
        }

    private fun preprocess(imageBytes: ByteArray): FloatArray? {
        val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
        val scaled = Bitmap.createScaledBitmap(decoded, CAPTCHA_WIDTH, CAPTCHA_HEIGHT, true)
        if (scaled !== decoded) decoded.recycle()
        return try {
            val pixels = IntArray(CAPTCHA_WIDTH * CAPTCHA_HEIGHT)
            scaled.getPixels(pixels, 0, CAPTCHA_WIDTH, 0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT)
            FloatArray(3 * CAPTCHA_WIDTH * CAPTCHA_HEIGHT).also { output ->
                val plane = CAPTCHA_WIDTH * CAPTCHA_HEIGHT
                pixels.forEachIndexed { index, pixel ->
                    output[index] = ((pixel ushr 16) and 0xFF) / 255f
                    output[plane + index] = ((pixel ushr 8) and 0xFF) / 255f
                    output[2 * plane + index] = (pixel and 0xFF) / 255f
                }
            }
        } finally {
            scaled.recycle()
        }
    }

    private fun copyModelAssetToPrivateCache(): File {
        val target = File(applicationContext.filesDir, MODEL_CACHE_FILE)
        if (target.exists() && target.length() > 0L) return target
        val temporary = File(applicationContext.filesDir, "$MODEL_CACHE_FILE.tmp")
        applicationContext.assets.open(MODEL_ASSET).use { input ->
            FileOutputStream(temporary).use(input::copyTo)
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        return target
    }
}
