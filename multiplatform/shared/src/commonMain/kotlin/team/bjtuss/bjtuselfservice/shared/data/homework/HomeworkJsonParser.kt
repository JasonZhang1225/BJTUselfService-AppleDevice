package team.bjtuss.bjtuselfservice.shared.data.homework

import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkAttachment
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkDetail

data class SmartCourse(
    val id: Int,
    val name: String,
    val teacherId: Int?,
    val courseNumber: String = "",
    val groupId: String = "",
    val semesterCode: String = "",
)

data class HomeworkUploadReceipt(
    val fileNameNoExt: String,
    val fileExtName: String,
    val fileSize: String,
    val visitName: String,
)

sealed interface HomeworkJsonParseResult<out T> {
    data class Success<T>(val value: T) : HomeworkJsonParseResult<T>
    data class Failure(val field: String) : HomeworkJsonParseResult<Nothing>
}

fun parseSmartSessionId(body: String): HomeworkJsonParseResult<String> = parseObject(body) { root ->
    val sessionId = root.string("sessionId").orEmpty()
    if (sessionId.isBlank()) {
        HomeworkJsonParseResult.Failure("sessionId")
    } else {
        HomeworkJsonParseResult.Success(sessionId)
    }
}

fun parseCurrentSemesterCode(body: String): HomeworkJsonParseResult<String> = parseObject(body) { root ->
    if (!root.hasSuccessStatus()) return@parseObject HomeworkJsonParseResult.Failure("STATUS")
    val semester = root.array("result")
        ?.firstOrNull()
        .asObject()
        ?.string("xqCode")
        .orEmpty()
    if (semester.isBlank()) {
        HomeworkJsonParseResult.Failure("result.xqCode")
    } else {
        HomeworkJsonParseResult.Success(semester)
    }
}

fun parseSmartCourses(body: String): HomeworkJsonParseResult<List<SmartCourse>> = parseObject(body) { root ->
    if (!root.hasSuccessStatus()) return@parseObject HomeworkJsonParseResult.Failure("STATUS")
    val elements = root.arrayOrBlank("courseList")
        ?: return@parseObject HomeworkJsonParseResult.Failure("courseList")
    val courses = mutableListOf<SmartCourse>()
    for ((index, element) in elements.withIndex()) {
        val item = element.asObject()
            ?: return@parseObject HomeworkJsonParseResult.Failure("courseList[$index]")
        val id = item.int("id")
            ?: return@parseObject HomeworkJsonParseResult.Failure("courseList[$index].id")
        val name = item.string("name").orEmpty()
        if (name.isBlank()) {
            return@parseObject HomeworkJsonParseResult.Failure("courseList[$index].name")
        }
        courses += SmartCourse(
            id = id,
            name = name,
            teacherId = item.int("teacher_id"),
            courseNumber = item.string("course_num").orEmpty(),
            groupId = item.string("fz_id").orEmpty(),
            semesterCode = item.string("xq_code").orEmpty(),
        )
    }
    HomeworkJsonParseResult.Success(courses)
}

fun parseHomeworkList(
    body: String,
    homeworkType: Int,
): HomeworkJsonParseResult<List<Homework>> = parseObject(body) { root ->
    // STATUS="2" 表示该课程当前类型没有作业，是合法空列表而非错误（对齐原 Android 默认值容错）。
    if (root.isEmptyDataStatus()) return@parseObject HomeworkJsonParseResult.Success(emptyList())
    if (!root.hasSuccessStatus()) return@parseObject HomeworkJsonParseResult.Failure("STATUS")
    val elements = root.arrayOrBlank("courseNoteList")
        ?: return@parseObject HomeworkJsonParseResult.Failure("courseNoteList")
    val homework = mutableListOf<Homework>()
    for ((index, element) in elements.withIndex()) {
        val item = element.asObject()
            ?: return@parseObject HomeworkJsonParseResult.Failure("courseNoteList[$index]")
        val upId = item.int("id")
            ?: return@parseObject HomeworkJsonParseResult.Failure("courseNoteList[$index].id")
        val courseId = item.int("course_id")
            ?: return@parseObject HomeworkJsonParseResult.Failure("courseNoteList[$index].course_id")
        val courseName = item.string("course_name").orEmpty()
        val title = item.string("title").orEmpty()
        if (courseName.isBlank() || title.isBlank()) {
            return@parseObject HomeworkJsonParseResult.Failure("courseNoteList[$index].identity")
        }
        homework += Homework(
            upId = upId,
            idSnId = item.int("snId"),
            score = item.string("stu_score").orEmpty(),
            userId = item.int("user_id") ?: 0,
            courseId = courseId,
            courseName = courseName,
            title = title,
            content = item.string("content").orEmpty(),
            createDate = item.string("create_date").orEmpty(),
            endTime = item.string("end_time").orEmpty(),
            openDate = item.string("open_date").orEmpty(),
            status = item.int("status") ?: 0,
            submitCount = item.int("submitCount") ?: 0,
            allCount = item.int("allCount") ?: 0,
            subStatus = item.string("subStatus").orEmpty(),
            scoreId = item.int("scoreId") ?: 0,
            homeworkType = homeworkType,
        )
    }
    HomeworkJsonParseResult.Success(homework)
}

fun parseHomeworkDetail(
    body: String,
    fallbackContent: String,
): HomeworkJsonParseResult<HomeworkDetail> = parseObject(body) { root ->
    if (!root.hasSuccessStatus()) return@parseObject HomeworkJsonParseResult.Failure("STATUS")
    val detail = root["homeWork"].asObject()
        ?: return@parseObject HomeworkJsonParseResult.Failure("homeWork")
    val attachments = root.arrayOrBlank("picList")
        ?: return@parseObject HomeworkJsonParseResult.Failure("picList")
    val parsedAttachments = mutableListOf<HomeworkAttachment>()
    for ((index, element) in attachments.withIndex()) {
        val item = element.asObject()
            ?: return@parseObject HomeworkJsonParseResult.Failure("picList[$index]")
        val id = item.int("id") ?: continue
        if (id == 0) continue
        parsedAttachments += HomeworkAttachment(
            id = id,
            fileName = item.string("file_name")
                ?.replace('+', ' ')
                ?.takeIf(String::isNotBlank)
                ?: "附件 $id",
            sizeBytes = item.long("pic_size") ?: 0L,
            sourcePath = item.string("url").orEmpty(),
        )
    }
    HomeworkJsonParseResult.Success(
        HomeworkDetail(
            content = detail.string("content").orEmpty().ifBlank { fallbackContent },
            attachments = parsedAttachments,
        ),
    )
}

fun parseHomeworkUploadReceipt(body: String): HomeworkJsonParseResult<HomeworkUploadReceipt> =
    parseObject(body) { root ->
        val name = root.string("fileNameNoExt").orEmpty()
        val extension = root.string("fileExtName").orEmpty()
        val size = root.string("fileSize").orEmpty()
        val visitName = root.string("visitName").orEmpty()
        if (name.isBlank() || size.isBlank() || visitName.isBlank()) {
            HomeworkJsonParseResult.Failure("uploadReceipt")
        } else {
            HomeworkJsonParseResult.Success(
                HomeworkUploadReceipt(
                    fileNameNoExt = name,
                    fileExtName = extension,
                    fileSize = size,
                    visitName = visitName,
                ),
            )
        }
    }

private inline fun <T> parseObject(
    body: String,
    transform: (Map<String, StrictJsonValue>) -> HomeworkJsonParseResult<T>,
): HomeworkJsonParseResult<T> = try {
    val root = parseStrictJsonObject(body)
        ?: return HomeworkJsonParseResult.Failure("root")
    transform(root)
} catch (_: Exception) {
    HomeworkJsonParseResult.Failure("json")
}
