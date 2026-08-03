@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package team.bjtuss.bjtuselfservice.shared.auth

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreML.MLDictionaryFeatureProvider
import platform.CoreML.MLFeatureValue
import platform.CoreML.MLFeatureDescription
import platform.CoreML.MLModel
import platform.CoreML.MLModelConfiguration
import platform.CoreML.featureValueWithCGImage
import platform.CoreML.imageConstraint
import platform.CoreML.objectAtIndexedSubscript
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

private const val MODEL_RESOURCE = "BJTUCaptcha"

class IosCoreMlCaptchaRecognizer(
    private val minimumConfidence: Float = DEFAULT_AUTO_CAPTCHA_CONFIDENCE,
) : CaptchaRecognizer {
    private val model: MLModel? by lazy {
        val url = NSBundle.mainBundle.URLForResource(MODEL_RESOURCE, withExtension = "mlmodelc")
            ?: return@lazy null
        runCatching {
            MLModel.modelWithContentsOfURL(
                url = url,
                configuration = MLModelConfiguration(),
                error = null,
            )
        }.getOrNull()
    }

    override suspend fun recognize(imageBytes: ByteArray): CaptchaRecognitionResult =
        withContext(Dispatchers.Default) {
            val loadedModel = model
                ?: return@withContext CaptchaRecognitionResult.Failed(
                    CaptchaRecognitionFailure.MODEL_UNAVAILABLE,
                )
            runCatching {
                val image = UIImage(data = imageBytes.toNSData())
                val cgImage = image.CGImage
                    ?: return@withContext CaptchaRecognitionResult.Failed(
                        CaptchaRecognitionFailure.IMAGE_DECODE_FAILED,
                    )
                val constraint = (
                    loadedModel.modelDescription.inputDescriptionsByName["captcha"] as? MLFeatureDescription
                )?.imageConstraint
                    ?: return@withContext CaptchaRecognitionResult.Failed(
                        CaptchaRecognitionFailure.INVALID_OUTPUT,
                    )
                val imageFeature = MLFeatureValue.featureValueWithCGImage(
                    cgImage = cgImage,
                    constraint = constraint,
                    options = null,
                    error = null,
                ) ?: return@withContext CaptchaRecognitionResult.Failed(
                    CaptchaRecognitionFailure.IMAGE_DECODE_FAILED,
                )
                val input = MLDictionaryFeatureProvider(
                    dictionary = mapOf("captcha" to imageFeature),
                    error = null,
                )
                val output = loadedModel.predictionFromFeatures(input, error = null)
                    ?: return@withContext CaptchaRecognitionResult.Failed(
                        CaptchaRecognitionFailure.INFERENCE_FAILED,
                    )
                val logits = output.featureValueForName("logits")?.multiArrayValue
                    ?: return@withContext CaptchaRecognitionResult.Failed(
                        CaptchaRecognitionFailure.INVALID_OUTPUT,
                    )
                val values = FloatArray(CAPTCHA_TIME_STEPS * CAPTCHA_CLASS_COUNT) { index ->
                    logits.objectAtIndexedSubscript(index.convert()).floatValue
                }
                decodeCaptchaLogits(values, minimumConfidence)
            }.getOrElse {
                CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INFERENCE_FAILED)
            }
        }
}

private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.convert())
}
