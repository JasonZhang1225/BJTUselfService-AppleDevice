package team.bjtuss.bjtuselfservice.shared.data.classroom

import team.bjtuss.bjtuselfservice.shared.data.homework.StrictJsonValue
import team.bjtuss.bjtuselfservice.shared.data.homework.parseStrictJsonObject
import team.bjtuss.bjtuselfservice.shared.domain.classroom.ClassroomBuildingInfo
import team.bjtuss.bjtuselfservice.shared.domain.classroom.ClassroomCapacity

/**
 * 教室人数评估接口（`http://yaya.csoci.com:2333/api/classnum/`）的严格 JSON 解析。
 *
 * 响应结构（与冻结 Android 工程 `ClassroomCapacityService` 对齐）：
 * ```
 * {
 *   "time": ["<轮询窗口起>", "<轮询窗口止>"],
 *   "data": [["<教室名>", <使用率 Double>, <已用 Int>, <容量 Int>], ...]
 * }
 * ```
 *
 * 已观察到的真实样本：使用率是百分比混合值（0.0 / 0.39 / 6.25 / 100.0），
 * 已用与容量为整数。字段缺失或类型错误一律返回 [ClassroomJsonParseResult.Failure]，
 * 不抛出异常、不把响应正文写入错误信息。
 */
sealed interface ClassroomJsonParseResult {
    data class Success(val info: ClassroomBuildingInfo) : ClassroomJsonParseResult
    data class Failure(val field: String) : ClassroomJsonParseResult
}

fun parseClassroomCapacityJson(
    buildingName: String,
    body: String,
): ClassroomJsonParseResult {
    val root = parseStrictJsonObject(body)
        ?: return ClassroomJsonParseResult.Failure("root")

    val timeItems = (root["time"] as? StrictJsonValue.ArrayValue)?.items
        ?: return ClassroomJsonParseResult.Failure("time")
    if (timeItems.size < 2) return ClassroomJsonParseResult.Failure("time")
    val start = (timeItems[0] as? StrictJsonValue.StringValue)?.value
        ?: return ClassroomJsonParseResult.Failure("time[0]")
    val end = (timeItems[1] as? StrictJsonValue.StringValue)?.value
        ?: return ClassroomJsonParseResult.Failure("time[1]")

    val dataItems = (root["data"] as? StrictJsonValue.ArrayValue)?.items
        ?: return ClassroomJsonParseResult.Failure("data")

    val classrooms = mutableListOf<ClassroomCapacity>()
    dataItems.forEachIndexed { index, item ->
        val row = (item as? StrictJsonValue.ArrayValue)?.items
            ?: return ClassroomJsonParseResult.Failure("data[$index]")
        if (row.size < 4) return ClassroomJsonParseResult.Failure("data[$index]")

        val name = (row[0] as? StrictJsonValue.StringValue)?.value?.takeIf(String::isNotBlank)
            ?: return ClassroomJsonParseResult.Failure("data[$index][0]")
        // 使用率是 Double 百分比；服务端目前只返回数字，字符串视为类型错误。
        val usage = (row[1] as? StrictJsonValue.NumberValue)?.raw?.toDoubleOrNull()
            ?: return ClassroomJsonParseResult.Failure("data[$index][1]")
        // 已用与容量是整数；"32.5" 或 "32" 字符串都按类型错误处理，与基线
        // org.json getInt 行为一致（getInt 对非整数值抛异常）。
        val used = (row[2] as? StrictJsonValue.NumberValue)?.raw?.toIntOrNull()
            ?: return ClassroomJsonParseResult.Failure("data[$index][2]")
        val capacity = (row[3] as? StrictJsonValue.NumberValue)?.raw?.toIntOrNull()
            ?: return ClassroomJsonParseResult.Failure("data[$index][3]")

        classrooms += ClassroomCapacity(
            name = name,
            usagePercent = usage,
            used = used,
            capacity = capacity,
        )
    }

    return ClassroomJsonParseResult.Success(
        ClassroomBuildingInfo(
            buildingName = buildingName,
            effectiveStart = start,
            effectiveEnd = end,
            classrooms = classrooms,
        ),
    )
}
