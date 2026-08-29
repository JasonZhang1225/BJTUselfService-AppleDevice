package team.bjtuss.bjtuselfservice.shared.feature.phyvlab

import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabActivity
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabAssignmentDetail

/** 作业列表中唯一可直接展示的截止状态；未知时不猜测颜色。 */
internal enum class PhyVlabActivityDeadlineState {
    SUBMITTED,
    LATE_SUBMITTED,
    DUE_SOON,
    OVERDUE,
    UNKNOWN,
}

/**
 * Moodle 课程页的完成标记是列表层可用的用户完成信号；详情页若拿到更准确的提交信息，
 * 可通过 [submitted] 覆盖它。截止时刻本身视为已到期，因此使用 >= 判断逾期。
 */
internal fun phyVlabActivityDeadlineState(
    activity: PhyVlabActivity,
    nowEpochSeconds: Long,
    submitted: Boolean = activity.completed,
    submittedAtEpochSeconds: Long? = null,
): PhyVlabActivityDeadlineState = when {
    submitted -> if (
        activity.dueTimestamp != null &&
        submittedAtEpochSeconds != null &&
        submittedAtEpochSeconds > activity.dueTimestamp
    ) {
        PhyVlabActivityDeadlineState.LATE_SUBMITTED
    } else {
        PhyVlabActivityDeadlineState.SUBMITTED
    }
    activity.dueTimestamp == null -> PhyVlabActivityDeadlineState.UNKNOWN
    nowEpochSeconds < activity.dueTimestamp -> PhyVlabActivityDeadlineState.DUE_SOON
    else -> PhyVlabActivityDeadlineState.OVERDUE
}

/** 详情页的提交状态比课程列表完成标记更具体，优先使用这些明确的提交信号。 */
internal fun phyVlabAssignmentDetailHasSubmission(detail: PhyVlabAssignmentDetail): Boolean {
    val status = detail.submissionStatus.trim().lowercase()
    return detail.submissionDateText?.isNotBlank() == true ||
        detail.submittedFiles.isNotEmpty() ||
        status.contains("已提交") ||
        (status.contains("submitted") && !status.contains("not submitted"))
}
