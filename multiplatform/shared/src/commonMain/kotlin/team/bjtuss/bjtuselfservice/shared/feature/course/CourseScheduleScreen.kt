package team.bjtuss.bjtuselfservice.shared.feature.course

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.bjtuss.bjtuselfservice.shared.accessibleAlpha
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSyncFailure
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppErrorBanner

/** 详情/网格等完整日名。 */
private val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
/**
 * 紧凑端日选择：单字保证任意机型单行七等分；比「周一」或 Mon 更省宽。
 * 选中态用容器色区分，不依赖长文案。
 */
private val compactDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
private val slotLabels = listOf(
    "第一节\n08:00–09:50",
    "第二节\n10:10–12:00",
    "第三节\n12:10–14:00",
    "第四节\n14:10–16:00",
    "第五节\n16:20–18:10",
    "第六节\n19:00–20:50",
    "第七节\n21:00–21:50",
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CourseScheduleWorkspace(
    state: CourseScheduleUiState,
    expanded: Boolean,
    model: CourseScheduleScreenModel,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    var showSchedulePicker by remember { mutableStateOf(false) }

    LaunchedEffect(model) {
        model.initialize()
    }

    Column(
        modifier = if (expanded) {
            modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "课程表",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "查看本学期或选课课表，并按教学周过滤",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    Text(if (state.isRefreshing) "正在同步" else "同步课表")
                }
            }
        }

        if (state.isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.failure?.let { failure ->
            CourseFailureBanner(
                failure = failure,
                hasContent = state.courses.isNotEmpty(),
                onRetry = onRefresh,
                onDismiss = model::dismissFailure,
            )
        }

        when {
            state.isLoading && state.courses.isEmpty() -> CourseLoadingState()
            else -> {
                CourseSummary(
                    state = state,
                    onOpenPicker = { showSchedulePicker = true },
                )
                if (state.scheduleCourses.isEmpty()) {
                    CourseEmptyState(state.scheduleType, onRefresh)
                } else if (expanded) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        WeekGrid(
                            courses = state.visibleCourses,
                            selectedCourseId = state.selectedCourseId,
                            onOpen = model::showCourseDetails,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        CourseDetailPanel(
                            course = state.selectedCourse,
                            modifier = Modifier.widthIn(min = 270.dp, max = 340.dp).fillMaxHeight(),
                        )
                    }
                } else {
                    CompactDaySelector(state.selectedDay, model::selectDay)
                    DayScheduleList(
                        courses = state.visibleCourses,
                        day = state.selectedDay,
                        onOpen = model::showCourseDetails,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    state.selectedCourse?.let { course ->
                        ModalBottomSheet(onDismissRequest = model::dismissCourseDetails) {
                            CourseDetailContent(
                                course = course,
                                modifier = Modifier.fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    if (showSchedulePicker) {
        // skipPartiallyExpanded：打开即全高，周数芯片不用先拖一下才能看全。
        val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSchedulePicker = false },
            sheetState = pickerSheetState,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("课表与周数", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "课表类型",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.scheduleType == CourseScheduleType.CURRENT,
                        onClick = { model.selectScheduleType(CourseScheduleType.CURRENT) },
                        label = { Text("本学期课表") },
                    )
                    FilterChip(
                        selected = state.scheduleType == CourseScheduleType.SELECTION,
                        onClick = { model.selectScheduleType(CourseScheduleType.SELECTION) },
                        label = { Text("选课课表") },
                    )
                }
                Text(
                    "教学周",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "“全部”显示该课表中的所有课程；本学期首次进入会优先跟随当前周。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.selectedWeek == 0,
                        onClick = {
                            model.selectWeek(0)
                            showSchedulePicker = false
                        },
                        label = { Text("全部") },
                    )
                    // 紧挨「全部」：快捷选到学校当前教学周（括号标明第几周）。
                    if (state.currentWeek in 1..26) {
                        FilterChip(
                            selected = state.selectedWeek == state.currentWeek,
                            onClick = {
                                model.selectWeek(state.currentWeek)
                                showSchedulePicker = false
                            },
                            label = { Text("当前（第${state.currentWeek}周）") },
                        )
                    }
                    (1..26).forEach { week ->
                        FilterChip(
                            selected = state.selectedWeek == week,
                            onClick = {
                                model.selectWeek(week)
                                showSchedulePicker = false
                            },
                            label = { Text("第${week}周") },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun CourseSummary(
    state: CourseScheduleUiState,
    onOpenPicker: () -> Unit,
) {
    val typeLabel = if (state.scheduleType == CourseScheduleType.CURRENT) "本学期课表" else "选课课表"
    val weekLabel = if (state.selectedWeek == 0) "全部教学周" else "第 ${state.selectedWeek} 周"
    // 副行只保留当前教学周提示；条数对用户无意义，已去掉。
    val subtitle = if (state.currentWeek > 0) "当前第 ${state.currentWeek} 周" else null

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenPicker)
                    .padding(vertical = 2.dp, horizontal = 4.dp),
            ) {
                Text(
                    "$typeLabel·$weekLabel",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.accessibleAlpha(0.78f),
                    )
                }
            }
            Surface(
                onClick = onOpenPicker,
                color = MaterialTheme.colorScheme.surface.accessibleAlpha(0.86f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    "切换",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun WeekGrid(
    courses: List<Course>,
    selectedCourseId: Int?,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    val byLocation = courses.groupBy(Course::courseLocationIndex)
    Column(
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(16.dp),
        ).padding(1.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(42.dp)) {
            Box(modifier = Modifier.width(78.dp).fillMaxHeight())
            dayLabels.forEach { day ->
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(day, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        repeat(7) { slot ->
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    modifier = Modifier.width(78.dp).fillMaxHeight()
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        slotLabels[slot],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                repeat(7) { day ->
                    val location = slot * 8 + day + 1
                    CourseGridCell(
                        courses = byLocation[location].orEmpty(),
                        selectedCourseId = selectedCourseId,
                        onOpen = onOpen,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseGridCell(
    courses: List<Course>,
    selectedCourseId: Int?,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (courses.isEmpty()) {
            Text("—", color = MaterialTheme.colorScheme.outlineVariant)
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                courses.forEach { course ->
                    Surface(
                        onClick = { onOpen(course.id) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        color = if (course.id == selectedCourseId) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.accessibleAlpha(0.72f)
                        },
                        shape = RoundedCornerShape(7.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                course.courseName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                course.coursePlace,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactDaySelector(selectedDay: Int, onSelect: (Int) -> Unit) {
    // 固定单行七等分，不随字号/机型折成两行（原 FlowRow +「周一」会在 Pro Max 上折行）。
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.48f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            compactDayLabels.forEachIndexed { index, label ->
                val selected = selectedDay == index
                Surface(
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = RoundedCornerShape(11.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayScheduleList(
    courses: List<Course>,
    day: Int,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    val byLocation = courses.groupBy(Course::courseLocationIndex)
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(9.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        items((0 until 7).toList(), key = { it }) { slot ->
            val location = slot * 8 + day + 1
            val slotCourses = byLocation[location].orEmpty()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Text(
                    slotLabels[slot],
                    modifier = Modifier.width(88.dp).padding(top = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (slotCourses.isEmpty()) {
                        // 单层淡底 + 弱化文字，避免「外圈深、内块浅」的双层方框感。
                        Text(
                            "无课",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.accessibleAlpha(0.55f),
                        )
                    } else {
                        slotCourses.forEach { course ->
                            CourseListCard(course, onOpen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseListCard(course: Course, onOpen: (Int) -> Unit) {
    // 扁平 Surface：无 elevation 阴影描边，避免外圈偏深、正文区又叠浅色矩形的双层感。
    Surface(
        onClick = { onOpen(course.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.accessibleAlpha(0.72f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(course.courseName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${course.coursePlace} · ${course.courseTeacher.ifBlank { "教师未知" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.accessibleAlpha(0.78f),
            )
            Text(
                course.courseTime,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.accessibleAlpha(0.72f),
            )
        }
    }
}

@Composable
private fun CourseDetailPanel(course: Course?, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.54f),
        shape = RoundedCornerShape(22.dp),
    ) {
        if (course == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("选择一门课程查看详情", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            CourseDetailContent(
                course = course,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            )
        }
    }
}

@Composable
private fun CourseDetailContent(course: Course, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "课程详情",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(course.courseName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        CourseDetailLine("编号", course.courseId)
        CourseDetailLine("教师", course.courseTeacher.ifBlank { "未提供" })
        CourseDetailLine("周次", course.courseTime)
        CourseDetailLine("地点", course.coursePlace)
        val slot = course.courseLocationIndex / 8
        val day = course.courseLocationIndex % 8 - 1
        CourseDetailLine("时间", "${dayLabels.getOrElse(day) { "未知" }} · ${slotLabels.getOrElse(slot) { "未知" }.replace('\n', ' ')}")
        CourseDetailLine(
            "类型",
            if (course.isCurrentSemester) "选课课表" else "本学期课表",
        )
    }
}

@Composable
private fun CourseDetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CourseFailureBanner(
    failure: CourseScheduleSyncFailure,
    hasContent: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppErrorBanner(
        message = when (failure) {
            CourseScheduleSyncFailure.NETWORK -> if (hasContent) {
                "同步失败，正在显示本地课表。"
            } else {
                "无法连接教务系统，请检查网络后重试。"
            }
            CourseScheduleSyncFailure.SESSION_EXPIRED -> "教务会话已失效，请退出后重新登录。"
            CourseScheduleSyncFailure.MALFORMED_RESPONSE -> "教务课表页面结构已变化，暂时无法解析。"
            CourseScheduleSyncFailure.CACHE -> "本地课表缓存操作失败。"
        },
        onRetry = if (failure != CourseScheduleSyncFailure.CACHE) onRetry else null,
        onDismiss = onDismiss,
    )
}

@Composable
private fun CourseLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("正在读取本地课表并连接教务系统…")
        }
    }
}

@Composable
private fun CourseEmptyState(type: CourseScheduleType, onRefresh: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("暂无课表", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (type == CourseScheduleType.CURRENT) {
                    "本地和教务系统均没有返回本学期课程。"
                } else {
                    "教务系统当前没有可显示的选课课表。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRefresh) { Text("重新同步") }
        }
    }
}
