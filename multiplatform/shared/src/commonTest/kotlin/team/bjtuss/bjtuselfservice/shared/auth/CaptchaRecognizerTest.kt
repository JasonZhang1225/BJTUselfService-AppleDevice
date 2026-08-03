package team.bjtuss.bjtuselfservice.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CaptchaRecognizerTest {
    @Test
    fun decodesTimeMajorCtcLogitsAndCalculatesAnswer() {
        val result = assertIs<CaptchaRecognitionResult.Success>(
            decodeCaptchaLogits(logitsFor(2, 0, 11, 3, 0, 14, 0, 0)),
        ).value

        assertEquals("1+2=", result.expression)
        assertEquals("3", result.answer)
        assertTrue(result.confidence > 0.99f)
    }

    @Test
    fun blankSeparatesRepeatedDigits() {
        val result = assertIs<CaptchaRecognitionResult.Success>(
            decodeCaptchaLogits(logitsFor(2, 0, 2, 11, 3, 14, 0, 0)),
        ).value

        assertEquals("11+2=", result.expression)
        assertEquals("13", result.answer)
    }

    @Test
    fun rejectsMalformedOrLowConfidenceOutput() {
        assertIs<CaptchaRecognitionResult.Failed>(decodeCaptchaLogits(FloatArray(12)))
        assertEquals(
            CaptchaRecognitionFailure.LOW_CONFIDENCE,
            assertIs<CaptchaRecognitionResult.Failed>(
                decodeCaptchaLogits(logitsFor(2, 0, 12, 10, 0, 14, 0, 0), minimumConfidence = 1f),
            ).reason,
        )
    }

    private fun logitsFor(vararg classes: Int): FloatArray {
        require(classes.size == CAPTCHA_TIME_STEPS)
        return FloatArray(CAPTCHA_TIME_STEPS * CAPTCHA_CLASS_COUNT) { -8f }.also { logits ->
            classes.forEachIndexed { step, value -> logits[step * CAPTCHA_CLASS_COUNT + value] = 8f }
        }
    }
}
