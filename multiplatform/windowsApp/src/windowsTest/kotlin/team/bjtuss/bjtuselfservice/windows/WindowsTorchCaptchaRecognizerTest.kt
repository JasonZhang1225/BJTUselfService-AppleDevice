package team.bjtuss.bjtuselfservice.windows

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 同进程内多次/并发推理验证：登录流程会连续尝试多次验证码识别，
 * 修复前每次识别关闭 base manager 导致第二次起全部失败。
 */
class WindowsTorchCaptchaRecognizerTest {

    private fun sampleImage(): File {
        // 用确定性合成图（与 logits 对齐验证同一张）
        val file = File(System.getProperty("java.io.tmpdir"), "bjtu-kmp-captcha-test.png")
        if (!file.exists()) {
            generateTestImage(file)
        }
        return file
    }

    private fun generateTestImage(target: File) {
        val image = java.awt.image.BufferedImage(130, 42, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = java.awt.Color(35, 38, 45)
        graphics.fillRect(0, 0, 130, 42)
        graphics.color = java.awt.Color(240, 240, 240)
        graphics.font = java.awt.Font("Arial", java.awt.Font.BOLD, 26)
        graphics.drawString("6*7=", 14, 30)
        graphics.dispose()
        javax.imageio.ImageIO.write(image, "png", target)
    }

    @Test
    fun `sequential recognition in same process stays consistent`() = runBlocking<Unit> {
        val recognizer = WindowsTorchCaptchaRecognizer(minimumConfidence = 0f)
        val image = sampleImage().readBytes()
        val first = recognizer.recognizeRawLogits(image)
        assertNotNull(first, "first recognition should succeed")
        repeat(3) { index ->
            val logits = recognizer.recognizeRawLogits(image)
            assertNotNull(logits, "recognition #$index should succeed")
            assertTrue(
                first.contentEquals(logits),
                "recognition #$index logits should equal first run",
            )
        }
        recognizer.close()
    }

    @Test
    fun `concurrent recognition works`() = runBlocking<Unit> {
        val recognizer = WindowsTorchCaptchaRecognizer(minimumConfidence = 0f)
        val image = sampleImage().readBytes()
        val results = (1..4).map {
            async {
                recognizer.recognizeRawLogits(image)
            }
        }.awaitAll()
        results.forEachIndexed { index, logits ->
            assertNotNull(logits, "concurrent recognition #$index should succeed")
        }
        assertEquals(1, results.map { it?.contentHashCode() }.distinct().size, "all results equal")
        recognizer.close()
    }
}
