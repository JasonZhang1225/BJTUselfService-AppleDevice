package team.bjtuss.bjtuselfservice.shared

import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.data.home.HomeChangeFeedRepository
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.grade.GradeScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.OtherFunctionScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsScreenModel
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway

/**
 * 登录后页面共享的应用级会话。
 *
 * 平台原生导航会为二级页面创建新的 Activity/UIViewController，但它们必须继续观察同一组
 * ScreenModel 和会话状态，不能各自重新登录或重新创建 Repository。此对象只在当前进程内
 * 存活，不负责落盘，也不持有 Activity、UIViewController 或 Window。
 */
class AuthenticatedSession(
    val profile: StudentProfile,
    val entryLoggingIn: Boolean,
    val gradeModel: GradeScreenModel,
    val courseScheduleModel: CourseScheduleScreenModel,
    val examScheduleModel: ExamScheduleScreenModel,
    val homeworkModel: HomeworkScreenModel,
    val coursewareModel: CoursewareScreenModel,
    val otherFunctionModel: OtherFunctionScreenModel,
    val classroomModel: ClassroomScreenModel,
    val settingsModel: SettingsScreenModel,
    val loginSyncPreferences: AppPreferences,
    val mailboxModel: MailboxScreenModel,
    val homeModel: HomeScreenModel,
    val homeChangeFeed: HomeChangeFeedRepository,
    val homeworkFileGateway: HomeworkFileGateway,
    val coursewareDirectoryGateway: CoursewareDirectoryGateway,
    val onLogout: () -> Unit,
) {
    /**
     * 智慧教学明文 HTTP 风险提示是否已在本登录态关闭。
     * 必须挂在 session 上：原生 push 的二级页会新建 Compose 树，
     * 若只用 remember，每次进课件/作业都会再弹一次。
     */
    var legacyHttpWarningDismissed: Boolean = false

    /**
     * 教室人数估计首页引导 Banner（请选择教学楼 / 人数仅供参考）
     * 是否已在本登录态关闭。
     */
    var classroomIntroBannerDismissed: Boolean = false
}

/** 只有这些目的地属于一级 tab 之上的原生导航层级。 */
fun isNativeDetailRoute(routeId: String): Boolean =
    routeId == "EXAMS" ||
        routeId == "COURSEWARE" ||
        routeId == "CLASSROOMS" ||
        routeId == "CLASSROOM_DETAIL" ||
        routeId == "HOMEWORK_DETAIL" ||
        routeId == "MAILBOX" ||
        routeId == "CALENDAR_DOWNLOAD" ||
        routeId == "REPORT_CARD_DOWNLOAD" ||
        routeId == "SETTINGS"
