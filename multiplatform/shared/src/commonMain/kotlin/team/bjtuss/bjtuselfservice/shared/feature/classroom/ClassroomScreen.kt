package team.bjtuss.bjtuselfservice.shared.feature.classroom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.shared.data.classroom.ClassroomFetchFailure
import team.bjtuss.bjtuselfservice.shared.domain.classroom.ClassroomCapacity
import team.bjtuss.bjtuselfservice.shared.domain.classroom.ClassroomSortDirection
import team.bjtuss.bjtuselfservice.shared.domain.classroom.ClassroomSortField

/** iPhone 两级列表、macOS 列表—详情的共享教室人数评估页面。 */
@Composable
fun ClassroomWorkspace(
    model: ClassroomScreenModel,
    expanded: Boolean,
    // compact 下选中教学楼后由 shell push 出第三级详情页；expanded 列表-详情并排，用不到。
    onOpenBuilding: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()

    if (expanded) {
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BuildingList(
                buildings = state.buildings,
                selected = state.selectedBuilding,
                onSelect = { building -> scope.launch { model.selectBuilding(building) } },
                modifier = Modifier.width(230.dp).fillMaxHeight(),
            )
            ClassroomDetail(
                state = state,
                model = model,
                onRefresh = { scope.launch { model.refresh() } },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    } else {
        // compact 只列教学楼，选中后由 NavHost push 出详情页（ClassroomBuildingWorkspace）。
        BuildingList(
            buildings = state.buildings,
            selected = null,
            onSelect = { building ->
                scope.launch { model.selectBuilding(building) }
                onOpenBuilding()
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}

/** 教室详情的第三级页面：返回（含系统返回/手势 pop）时清除教学楼选中。 */
@Composable
fun ClassroomBuildingWorkspace(
    model: ClassroomScreenModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        onDispose { model.clearSelection() }
    }
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text("返回教学楼") }
        ClassroomDetail(
            state = state,
            model = model,
            onRefresh = { scope.launch { model.refresh() } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun BuildingList(
    buildings: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 8.dp).padding(top = 10.dp),
        contentPadding = PaddingValues(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "选择教学楼",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Text(
                "人数来自第三方明文接口的实时评估，仅作找空教室参考。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        items(buildings, key = { it }) { building ->
            ElevatedCard(
                onClick = { onSelect(building) },
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (selected == building) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    building,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected == building) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassroomDetail(
    state: ClassroomUiState,
    model: ClassroomScreenModel,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    val selected = state.selectedBuilding
    if (selected == null) {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("从左侧选择教学楼", style = MaterialTheme.typography.titleLarge)
                Text(
                    "选择后显示教室容量、已用人数和空位状态。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        return
    }

    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(selected, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val info = when (val load = state.buildingState) {
                    is ClassroomBuildingState.Loaded -> load.info
                    is ClassroomBuildingState.Failed -> load.cached
                    else -> null
                }
                if (info != null) {
                    Text(
                        "数据窗口：${info.effectiveStart} — ${info.effectiveEnd}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(onClick = onRefresh, enabled = !state.isLoading) { Text("刷新") }
        }

        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        (state.buildingState as? ClassroomBuildingState.Failed)?.let { failed ->
            ErrorCard(failed.reason)
        }

        OutlinedTextField(
            value = state.filter.nameQuery,
            onValueChange = model::setNameQuery,
            label = { Text("搜索教室名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = state.filter.onlyWithFreeSeats,
                        role = Role.Checkbox,
                        onValueChange = model::setOnlyWithFreeSeats,
                    )
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.filter.onlyWithFreeSeats,
                    onCheckedChange = null,
                )
                Text("仅显示有空位")
            }
            CapacityChip("不限容量", state.filter.minCapacity == null) { model.setCapacityRange(null, null) }
            CapacityChip("≥ 50", state.filter.minCapacity == 50) { model.setCapacityRange(50, null) }
            CapacityChip("≥ 100", state.filter.minCapacity == 100) { model.setCapacityRange(100, null) }
            CapacityChip("≥ 200", state.filter.minCapacity == 200) { model.setCapacityRange(200, null) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClassroomSortField.entries.forEach { field ->
                FilterChip(
                    selected = state.sortField == field,
                    onClick = { model.setSortField(field) },
                    label = {
                        val label = when (field) {
                            ClassroomSortField.NAME -> "名称"
                            ClassroomSortField.OCCUPANCY -> "占用率"
                            ClassroomSortField.USED -> "已用人数"
                            ClassroomSortField.CAPACITY -> "容量"
                        }
                        val arrow = if (state.sortField == field) {
                            if (state.sortDirection == ClassroomSortDirection.ASCENDING) " ↑" else " ↓"
                        } else ""
                        Text(label + arrow)
                    },
                )
            }
        }

        when {
            state.isLoading && state.visibleClassrooms.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text("正在读取教室人数…", modifier = Modifier.padding(top = 10.dp))
                }
            }
            state.visibleClassrooms.isEmpty() -> {
                Text(
                    if (state.buildingState is ClassroomBuildingState.Idle) "尚未加载" else "当前筛选下没有教室",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.visibleClassrooms, key = { it.name }) { room -> ClassroomRow(room) }
                }
            }
        }
    }
}

@Composable
private fun CapacityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ClassroomRow(room: ClassroomCapacity) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    room.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "已用 ${room.used} / 容量 ${room.capacity} · 估计占用 ${formatPercent(room.occupancyRatio)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (room.hasFreeSeat) "有空位" else "可能已满",
                color = if (room.hasFreeSeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ErrorCard(reason: ClassroomFetchFailure) {
    val message = when (reason) {
        ClassroomFetchFailure.NETWORK -> "教室接口暂时不可达，请稍后重试。"
        ClassroomFetchFailure.PARSE -> "教室接口返回格式已变化，暂时无法解析。"
        ClassroomFetchFailure.SECURE_CHANNEL_UNAVAILABLE ->
            "该第三方教室接口只支持明文 HTTP，不满足当前系统安全要求。"
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
    }
}

private fun formatPercent(value: Double): String = "${(value * 100).toInt().coerceIn(0, 999)}%"
