package team.bjtuss.bjtuselfservice.shared.auth

import kotlin.math.exp

const val CAPTCHA_TIME_STEPS = 8
const val CAPTCHA_CLASS_COUNT = 15
const val DEFAULT_AUTO_CAPTCHA_CONFIDENCE = 0.55f

private val CaptchaCharset = charArrayOf(
    ' ', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '-', '*', '=',
)
private val CaptchaExpression = Regex("^(\\d+)([+\\-*])(\\d+)=$")

data class CaptchaRecognition(
    val expression: String,
    val answer: String,
    val confidence: Float,
)

enum class CaptchaRecognitionFailure {
    MODEL_UNAVAILABLE,
    IMAGE_DECODE_FAILED,
    INVALID_OUTPUT,
    INVALID_EXPRESSION,
    LOW_CONFIDENCE,
    INFERENCE_FAILED,
}

sealed interface CaptchaRecognitionResult {
    data class Success(val value: CaptchaRecognition) : CaptchaRecognitionResult
    data class Failed(val reason: CaptchaRecognitionFailure) : CaptchaRecognitionResult
}

fun interface CaptchaRecognizer {
    suspend fun recognize(imageBytes: ByteArray): CaptchaRecognitionResult
}

object UnavailableCaptchaRecognizer : CaptchaRecognizer {
    override suspend fun recognize(imageBytes: ByteArray): CaptchaRecognitionResult =
        CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.MODEL_UNAVAILABLE)
}

fun decodeCaptchaLogits(
    logits: FloatArray,
    minimumConfidence: Float = DEFAULT_AUTO_CAPTCHA_CONFIDENCE,
): CaptchaRecognitionResult {
    if (logits.size != CAPTCHA_TIME_STEPS * CAPTCHA_CLASS_COUNT) {
        return CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INVALID_OUTPUT)
    }

    val classes = IntArray(CAPTCHA_TIME_STEPS)
    val probabilities = FloatArray(CAPTCHA_TIME_STEPS)
    repeat(CAPTCHA_TIME_STEPS) { step ->
        val offset = step * CAPTCHA_CLASS_COUNT
        var maximum = Float.NEGATIVE_INFINITY
        var maximumIndex = 0
        repeat(CAPTCHA_CLASS_COUNT) { index ->
            val value = logits[offset + index]
            if (!value.isFinite()) {
                return CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INVALID_OUTPUT)
            }
            if (value > maximum) {
                maximum = value
                maximumIndex = index
            }
        }
        var denominator = 0.0
        repeat(CAPTCHA_CLASS_COUNT) { index ->
            denominator += exp((logits[offset + index] - maximum).toDouble())
        }
        classes[step] = maximumIndex
        probabilities[step] = (1.0 / denominator).toFloat()
    }

    val expression = buildString {
        classes.forEachIndexed { index, value ->
            if (value != 0 && (index == 0 || value != classes[index - 1])) {
                append(CaptchaCharset[value])
            }
        }
    }
    val match = CaptchaExpression.matchEntire(expression)
        ?: return CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INVALID_EXPRESSION)
    val left = match.groupValues[1].toLongOrNull()
        ?: return CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INVALID_EXPRESSION)
    val right = match.groupValues[3].toLongOrNull()
        ?: return CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INVALID_EXPRESSION)
    val answer = when (match.groupValues[2]) {
        "+" -> left + right
        "-" -> left - right
        "*" -> left * right
        else -> return CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INVALID_EXPRESSION)
    }.toString()

    val selectedProbabilities = classes.indices
        .filter { index -> classes[index] != 0 && (index == 0 || classes[index] != classes[index - 1]) }
        .map(probabilities::get)
    val confidence = selectedProbabilities.minOrNull() ?: 0f
    if (confidence < minimumConfidence) {
        return CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.LOW_CONFIDENCE)
    }
    return CaptchaRecognitionResult.Success(
        CaptchaRecognition(expression = expression, answer = answer, confidence = confidence),
    )
}
