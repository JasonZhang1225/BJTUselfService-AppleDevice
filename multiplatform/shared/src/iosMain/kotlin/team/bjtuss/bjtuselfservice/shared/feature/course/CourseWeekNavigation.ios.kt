package team.bjtuss.bjtuselfservice.shared.feature.course

import androidx.compose.ui.Modifier

internal actual fun Modifier.courseWeekScrollNavigation(
    accumulator: CourseWeekScrollAccumulator,
    onDirection: (CourseWeekScrollDirection) -> Unit,
): Modifier = this
