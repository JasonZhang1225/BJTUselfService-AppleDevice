package team.bjtuss.bjtuselfservice.shared.domain.otherfunction

/**
 * “其他功能”切片的共享领域对象：公众号校历入口与中英文成绩单下载。
 * 行为基线来自冻结 Android 工程的 OtherFunctionNetworkRepository。
 */

/** 成绩单语种。 */
enum class ReportCardLanguage {
    CHINESE,
    ENGLISH,
}

/** 下载任务的稳定标识，用于状态归属，避免并发点击串状态。 */
enum class OtherFunctionTask {
    REPORT_CARD,
}

/**
 * 一次“其他功能”下载的状态。
 * 状态归属到具体任务；保存面板取消用 [SaveCancelled]，与错误区分，不显示红色失败。
 */
sealed interface OtherFunctionTaskState {
    /** 尚未触发过。 */
    data object Idle : OtherFunctionTaskState

    /** 正在请求或解析。 */
    data object Downloading : OtherFunctionTaskState

    /** 已下载并保存成功，[fileName] 仅用于本次界面展示，不进入日志。 */
    data class Saved(val fileName: String) : OtherFunctionTaskState

    /** 系统保存面板被用户取消，属于正常返回路径。 */
    data object SaveCancelled : OtherFunctionTaskState

    /** 失败，[reason] 区分网络/解析/会话失效/保存失败。 */
    data class Failed(val reason: OtherFunctionFailure) : OtherFunctionTaskState
}

enum class OtherFunctionFailure {
    NETWORK,
    PARSE,
    SESSION_EXPIRED,
    SAVE_FAILED,
    SAVE_UNAVAILABLE,
}
