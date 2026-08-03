package team.bjtuss.bjtuselfservice.shared.domain.classroom

/**
 * “教室”切片的共享领域对象：教学楼两级列表 → 教室人数评估。
 *
 * 行为基线来自冻结 Android 工程的 `ClassroomCapacityService` 与
 * `DetectionScreen.kt` 的 `BuildingScreen`/`ClassroomScreen`：
 * - 教学楼清单与 v1.7.0 `BuildingScreen.buildingList` 完全一致（11 栋）。
 * - 接口是公开第三方明文 HTTP（`http://yaya.csoci.com:2333/api/classnum/`），
 *   不需要学校登录；返回 `time`（服务器轮询窗口首尾）与 `data` 教室行。
 * - 原 App 排序与进度条均用 `已用人数/容量` 计算；`data` 第二列“使用率”为
 *   百分比数值（0.39 表示 0.39%，100.0 表示 100%），仅作展示参考，
 *   本实现保留该字段但排序/筛选以 used/capacity 为准，与基线一致。
 * - 原 App 还有教室安排 WebView 弹层（`CLASSROOM_VIEW_URL`），M0 盘点时该
 *   弹层未稳定出现，标记为未验证边界；本切片不实现该弹层。
 */

/** 与 v1.7.0 `BuildingScreen.buildingList` 一致的教学楼清单。 */
val CLASSROOM_BUILDINGS: List<String> = listOf(
    "第十七号教学楼",
    "思源楼",
    "思源西楼",
    "思源东楼",
    "第九教学楼",
    "第八教学楼",
    "第五教学楼",
    "逸夫教学楼",
    "机械楼",
    "东区二教",
    "东区一教",
)

/**
 * 单个教室的人数评估。
 *
 * [usagePercent] 为服务器返回的“使用率”原始数值（百分比，0.0–100.0 区间，
 * 已观察到 0.39/0.9/6.25/100.0 等值）；排序与进度展示使用 [used]/[capacity]。
 */
data class ClassroomCapacity(
    val name: String,
    val usagePercent: Double,
    val used: Int,
    val capacity: Int,
) {
    /** 是否有空位（已用 < 容量）。 */
    val hasFreeSeat: Boolean get() = used < capacity

    /** 占用比例（0.0–1.0），容量为 0 时为 0，与基线进度条行为一致。 */
    val occupancyRatio: Double get() =
        if (capacity > 0) used.toDouble() / capacity.toDouble() else 0.0
}

/**
 * 一次教学楼教室查询成功的结果。
 *
 * [effectiveStart]/[effectiveEnd] 是服务器 JSON `time` 数组首尾两个字符串，
 * 表示该数据是服务器在最近一次轮询窗口内的快照；按原样展示，不做时区解析。
 */
data class ClassroomBuildingInfo(
    val buildingName: String,
    val effectiveStart: String,
    val effectiveEnd: String,
    val classrooms: List<ClassroomCapacity>,
)

/** 教室排序维度（基线为“教室名/占用率/人数”三种 + 升降序）。 */
enum class ClassroomSortField {
    /** 按教室名字典序。 */
    NAME,

    /** 按占用率（used/capacity），与基线“占用率”一致。 */
    OCCUPANCY,

    /** 按已用人数，与基线“人数”一致。 */
    USED,

    /** 按容量。 */
    CAPACITY,
}

/** 排序方向。 */
enum class ClassroomSortDirection {
    ASCENDING,
    DESCENDING,
}

/** 教室筛选条件；全部可选，默认不筛选。 */
data class ClassroomFilter(
    /** 按教室名包含的子串过滤（忽略大小写）；空白视为不过滤。 */
    val nameQuery: String = "",

    /** 仅显示有空位的教室（used < capacity）。 */
    val onlyWithFreeSeats: Boolean = false,

    /** 容量下界（含）；null 表示不限制。 */
    val minCapacity: Int? = null,

    /** 容量上界（含）；null 表示不限制。 */
    val maxCapacity: Int? = null,
)

/** 对教室列表应用筛选与排序，返回新列表。 */
fun List<ClassroomCapacity>.applyFilterAndSort(
    filter: ClassroomFilter,
    sortField: ClassroomSortField,
    direction: ClassroomSortDirection,
): List<ClassroomCapacity> {
    val query = filter.nameQuery.trim()
    val filtered = filter { room ->
        (query.isEmpty() || room.name.contains(query, ignoreCase = true)) &&
            (!filter.onlyWithFreeSeats || room.hasFreeSeat) &&
            (filter.minCapacity == null || room.capacity >= filter.minCapacity) &&
            (filter.maxCapacity == null || room.capacity <= filter.maxCapacity)
    }
    val comparator: Comparator<ClassroomCapacity> = when (sortField) {
        ClassroomSortField.NAME -> compareBy { it.name }
        ClassroomSortField.OCCUPANCY -> compareBy { it.occupancyRatio }
        ClassroomSortField.USED -> compareBy { it.used }
        ClassroomSortField.CAPACITY -> compareBy { it.capacity }
    }
    val sorted = filtered.sortedWith(comparator.thenBy { it.name })
    return if (direction == ClassroomSortDirection.DESCENDING) sorted.asReversed() else sorted
}
