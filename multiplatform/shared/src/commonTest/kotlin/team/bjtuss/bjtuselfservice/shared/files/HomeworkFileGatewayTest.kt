package team.bjtuss.bjtuselfservice.shared.files

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent

class HomeworkFileGatewayTest {
    @Test
    fun selectedFilesDoNotExposeNamesOrBytesInStringForm() {
        val result = HomeworkFilePickResult.Selected(
            listOf(
                HomeworkFileContent(
                    fileName = "private-homework.pdf",
                    contentType = "application/pdf",
                    bytes = "private-file-body".encodeToByteArray(),
                ),
            ),
        )

        assertFalse("private-homework.pdf" in result.toString())
        assertFalse("private-file-body" in result.toString())
        assertFalse("private-homework.pdf" in result.files.single().toString())
    }

    @Test
    fun unavailableGatewayReturnsTypedFailureWithoutSideEffects() = runBlocking {
        val picked = UnavailableHomeworkFileGateway.pickFiles()
        val saved = UnavailableHomeworkFileGateway.saveFile(
            HomeworkFileContent("file.bin", "application/octet-stream", byteArrayOf()),
        )

        assertEquals(
            HomeworkFileGatewayFailure.UNAVAILABLE,
            assertIs<HomeworkFilePickResult.Failed>(picked).reason,
        )
        assertEquals(
            HomeworkFileGatewayFailure.UNAVAILABLE,
            assertIs<HomeworkFileSaveResult.Failed>(saved).reason,
        )
    }
}
