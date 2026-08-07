package team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy

/**
 * “教室占用”切片的共享领域对象：教务 room_view 周视图 → 教室 × 星期 × 节次占用。
 *
 * 老安卓版只抓过色值未上线该功能；KMP 为全新实现，与第三方“教室人数估计”
 * 并存为两个入口。数据链路（浏览器实测）：
 * `aa.bjtu.edu.cn/classroom/timeholdresult/room_view/?zc=周次&jxlh=教学楼&jash=教室号&page=1&perpage=500`，
 * zxjxjhh（学期）省略即当前学期；响应每行一个教室，行首 `SY101 (90)`，
 * 随后 49 个 `td[title="星期X 第Y节"]`，单元格无文字只有底色。
 */

/**
 * 占用类型。页面图例五种占用：排课/调课/考试/实验/其他安排，白色为空闲；
 * 未识别的新底色归 [UNKNOWN] 并在 UI 单独提示。
 */
enum class OccupancyKind {
    FREE,
    SCHEDULED,
    RESCHEDULED,
    EXAM,
    EXPERIMENT,
    OTHER,
    UNKNOWN,
}

/**
 * 一个教室一整周的占用表。
 * [cells] 键为 (星期1-7, 节次1-7)；解析缺失的格子由 [kindAt] 兜底为空闲。
 */
data class ClassroomOccupancy(
    val room: String,
    val capacity: Int,
    val cells: Map<Pair<Int, Int>, OccupancyKind>,
) {
    fun kindAt(weekday: Int, period: Int): OccupancyKind =
        cells[weekday to period] ?: OccupancyKind.FREE
}

/**
 * 教学楼。id 为教务 room_view 页 jxlh 下拉的 option value（数字 ID）：
 * 请求 jxlh 必须传 id，传中文楼名只会返回表头、查不到教室。
 */
data class OccupancyBuilding(
    val id: String,
    val name: String,
)

/**
 * 学期（教务 room_view 页 zxjxjhh 下拉的一项）。
 * [id] 为 option value（如 `2025-2026-2-2`，请求时作为 zxjxjhh 参数）；
 * [label] 为显示文本（如 `2025-2026-2`），也是校历周日期 Map 的 key。
 */
data class OccupancySemester(
    val id: String,
    val label: String,
)

/**
 * 一个教学周（aa 系统 zc 编号）对应的起止日期，短格式如 `10/6`、`10/12`，
 * 跨月时也直接拼接（如 `9/29`、`10/5`）。来源：教务处校历页（bksy）。
 */
data class OccupancyWeekDate(
    val week: Int,
    val startMonthDay: String,
    val endMonthDay: String,
)

/**
 * 教务 room_view 页 jxlh 下拉的教学楼全量清单（id + 楼名，顺序即下拉顺序）。
 * 来源：aa 教务系统 room_view 页面 jxlh 下拉，2026-08-07 线上核对。
 */
val OCCUPANCY_BUILDINGS: List<OccupancyBuilding> = listOf(
    OccupancyBuilding("13", "第十七号教学楼"),
    OccupancyBuilding("100", "学生活动服务中心"),
    OccupancyBuilding("1", "思源楼"),
    OccupancyBuilding("2", "思源西楼"),
    OccupancyBuilding("3", "思源东楼"),
    OccupancyBuilding("4", "第九教学楼"),
    OccupancyBuilding("5", "第八教学楼"),
    OccupancyBuilding("6", "第五教学楼"),
    OccupancyBuilding("7", "第二教学楼"),
    OccupancyBuilding("11", "逸夫教学楼"),
    OccupancyBuilding("12", "机械楼"),
    OccupancyBuilding("91", "天佑会堂"),
    OccupancyBuilding("92", "工程素质"),
    OccupancyBuilding("93", "综合实验楼"),
    OccupancyBuilding("94", "机械实验馆"),
    OccupancyBuilding("9", "东区二教"),
    OccupancyBuilding("8", "东区一教"),
    OccupancyBuilding("10", "东教三楼"),
    OccupancyBuilding("90", "科技大厦"),
    OccupancyBuilding("14", "电气工程楼"),
    OccupancyBuilding("101", "综合体育馆"),
    OccupancyBuilding("102", "新综合体育馆"),
    OccupancyBuilding("16", "东校区计算机机房"),
    OccupancyBuilding("15", "交通运输科学馆"),
    OccupancyBuilding("17", "工程训练中心"),
    OccupancyBuilding("18", "第七教学楼"),
    OccupancyBuilding("19", "工程结构实验楼"),
    OccupancyBuilding("20", "土木工程楼"),
    OccupancyBuilding("103", "科技楼"),
    OccupancyBuilding("104", "思源楼A座"),
    OccupancyBuilding("105", "思源楼B座"),
    OccupancyBuilding("106", "致远楼"),
    OccupancyBuilding("107", "知行楼"),
    OccupancyBuilding("108", "逸夫楼"),
    OccupancyBuilding("109", "信息楼"),
)

/**
 * 节次时间段，index 0 对应第 1 节。每“节”实际是两小节连上的大节。
 * 来源：aa 学生课表页 stuschedule 表头。
 */
val SLOT_TIME_RANGES: List<String> = listOf(
    "08:00-09:50",
    "10:10-12:00",
    "12:10-14:00",
    "14:10-16:00",
    "16:20-18:10",
    "19:00-20:50",
    "21:00-21:50",
)
