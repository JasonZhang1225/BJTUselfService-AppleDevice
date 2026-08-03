package team.bjtuss.bjtuselfservice.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.decodeToImageBitmap
import team.bjtuss.bjtuselfservice.shared.auth.AuthenticationResult
import team.bjtuss.bjtuselfservice.shared.auth.AutomaticLoginCoordinator
import team.bjtuss.bjtuselfservice.shared.auth.AutomaticLoginResult
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaChallenge
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognizer
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognitionResult
import team.bjtuss.bjtuselfservice.shared.auth.ChallengeResult
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.auth.LoginEvent
import team.bjtuss.bjtuselfservice.shared.auth.LoginFailure
import team.bjtuss.bjtuselfservice.shared.auth.LoginState
import team.bjtuss.bjtuselfservice.shared.auth.SchoolLoginProtocol
import team.bjtuss.bjtuselfservice.shared.auth.reduceLoginState
import team.bjtuss.bjtuselfservice.shared.cache.CacheOpenState
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.cache.CacheStoreHandle
import team.bjtuss.bjtuselfservice.shared.data.grade.CacheStoreGradeLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.grade.DefaultGradeRepository
import team.bjtuss.bjtuselfservice.shared.data.grade.SchoolGradeRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.course.CacheStoreCourseScheduleLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.course.DefaultCourseScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.course.SchoolCourseScheduleRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.exam.CacheStoreExamScheduleLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.exam.DefaultExamScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.exam.SchoolExamScheduleRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.homework.CacheStoreHomeworkLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.homework.DefaultHomeworkRepository
import team.bjtuss.bjtuselfservice.shared.data.homework.SchoolHomeworkRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.courseware.CacheStoreCoursewareLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.courseware.DefaultCoursewareRepository
import team.bjtuss.bjtuselfservice.shared.data.courseware.SchoolCoursewareRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.otherfunction.DefaultOtherFunctionRepository
import team.bjtuss.bjtuselfservice.shared.data.otherfunction.SchoolOtherFunctionRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.classroom.DefaultClassroomRepository
import team.bjtuss.bjtuselfservice.shared.data.classroom.SchoolClassroomRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.home.CacheStoreHomeStatusLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.home.DefaultHomeStatusRepository
import team.bjtuss.bjtuselfservice.shared.data.home.SchoolHomeStatusRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.home.CacheStoreHomeChangeFeedRepository
import team.bjtuss.bjtuselfservice.shared.data.home.courseChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.examChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.gradeChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.homeworkChangeRecorder
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.grade.AuthenticatedAppShell
import team.bjtuss.bjtuselfservice.shared.feature.grade.GradeScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.OtherFunctionScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppCommandBus
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.network.createSchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.security.AccountSecurityCoordinator
import team.bjtuss.bjtuselfservice.shared.security.AccountSecurityStore
import team.bjtuss.bjtuselfservice.shared.security.CredentialRestoreResult

@Composable
fun LoginRoute(
    platform: PlatformInfo,
    windowClass: WindowClass,
    accountSecurityStore: AccountSecurityStore,
    cacheStoreHandle: CacheStoreHandle,
    homeworkFileGateway: HomeworkFileGateway,
    coursewareDirectoryGateway: CoursewareDirectoryGateway,
    appCommandBus: AppCommandBus?,
    appPreferences: AppPreferences,
    onPreferencesChanged: (AppPreferences) -> Boolean,
    captchaRecognizer: CaptchaRecognizer,
) {
    val transport = remember {
        lazy(LazyThreadSafetyMode.NONE) { createSchoolHttpTransport() }
    }
    val protocol = remember {
        lazy(LazyThreadSafetyMode.NONE) {
            SchoolLoginProtocol(transport.value)
        }
    }
    val securityCoordinator = remember(accountSecurityStore) {
        AccountSecurityCoordinator(accountSecurityStore)
    }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<LoginState>(LoginState.SignedOut) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captchaAnswer by remember { mutableStateOf("") }
    var rememberCredentials by remember {
        mutableStateOf(securityCoordinator.canStoreCredentials)
    }
    var storageReady by remember { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf<String?>(null) }
    var automationMessage by remember { mutableStateOf<String?>(null) }
    var manualDialogChallenge by remember { mutableStateOf<CaptchaChallenge?>(null) }
    var manualDialogAttempts by remember { mutableStateOf(0) }
    val cacheStore = cacheStoreHandle.store

    suspend fun finishLogin(
        profile: team.bjtuss.bjtuselfservice.shared.auth.StudentProfile,
        credentials: Credentials,
        persistCredentials: Boolean,
    ): LoginState {
        val linking = LoginState.LinkingAcademicSystem(profile)
        state = linking
        if (!protocol.value.linkAcademicSystem()) {
            return LoginState.Failed(LoginFailure.ACADEMIC_LINK_FAILED, canRetry = true)
        }
        val cacheReady = runCatching {
            cacheStore.claimLegacyAccountData(profile.studentId)
        }.isSuccess
        val stored = if (credentials.isValid) {
            securityCoordinator.persistAfterLogin(
                credentials = credentials,
                rememberCredentials = persistCredentials,
            )
        } else {
            true
        }
        storageMessage = when {
            !cacheReady -> "登录成功，但本地缓存初始化失败。"
            !stored -> "登录成功，但系统安全存储操作失败。"
            persistCredentials && credentials.isValid -> "登录信息已保存到系统安全存储。"
            else -> null
        }
        return LoginState.SignedIn(profile)
    }

    suspend fun recognizeChallenge(challenge: CaptchaChallenge) {
        when (val recognition = captchaRecognizer.recognize(challenge.imageBytes)) {
            is CaptchaRecognitionResult.Success -> {
                captchaAnswer = recognition.value.answer
            }
            is CaptchaRecognitionResult.Failed -> Unit
        }
    }

    fun loadChallenge(
        openManualDialog: Boolean = false,
        retryNetworkOnce: Boolean = false,
    ) {
        automationMessage = null
        state = LoginState.CheckingSession
        scope.launch {
            state = try {
                val requestChallenge: suspend () -> ChallengeResult = {
                    try {
                        protocol.value.requestCaptchaChallenge(username.trim())
                    } catch (_: Exception) {
                        ChallengeResult.Failed(LoginFailure.NETWORK)
                    }
                }
                val firstResult = requestChallenge()
                val result = if (
                    retryNetworkOnce &&
                    firstResult is ChallengeResult.Failed &&
                    firstResult.reason == LoginFailure.NETWORK
                ) {
                    delay(500)
                    requestChallenge()
                } else {
                    firstResult
                }
                when (result) {
                    is ChallengeResult.SessionActive -> {
                        finishLogin(
                            profile = result.profile,
                            credentials = Credentials(username.trim(), password),
                            persistCredentials = rememberCredentials,
                        )
                    }
                    is ChallengeResult.Ready -> {
                        captchaAnswer = ""
                        val awaiting = LoginState.AwaitingCaptcha(result.challenge)
                        state = awaiting
                        if (openManualDialog) manualDialogChallenge = result.challenge
                        recognizeChallenge(result.challenge)
                        awaiting
                    }
                    is ChallengeResult.Failed -> LoginState.Failed(result.reason, canRetry = true)
                }
            } catch (_: Exception) {
                LoginState.Failed(LoginFailure.NETWORK, canRetry = true)
            }
        }
    }

    LaunchedEffect(securityCoordinator, captchaRecognizer) {
        val restoredCredentials = when (val restored = securityCoordinator.restore()) {
            CredentialRestoreResult.Empty,
            CredentialRestoreResult.Unavailable,
            -> null
            CredentialRestoreResult.Failed -> {
                storageMessage = "无法读取已保存的登录信息，已恢复为手动登录。"
                null
            }
            is CredentialRestoreResult.Restored -> {
                username = restored.credentials.username
                password = restored.credentials.password
                rememberCredentials = true
                restored.credentials
            }
        }
        if (cacheStoreHandle.state == CacheOpenState.RECOVERED_AFTER_RESET) {
            storageMessage = "本地缓存损坏，已安全重建。"
        }
        storageReady = true

        if (restoredCredentials == null) {
            loadChallenge(retryNetworkOnce = true)
            return@LaunchedEffect
        }

        state = LoginState.CheckingSession
        automationMessage = "正在检查登录，正在自动登录"
        val automaticResult = try {
            AutomaticLoginCoordinator(
                gateway = protocol.value,
                captchaRecognizer = captchaRecognizer,
            ).login(restoredCredentials) { attempt, maximum ->
                automationMessage = "正在检查登录，正在自动登录（第 $attempt/$maximum 次）"
            }
        } catch (_: Exception) {
            null
        }
        state = when (automaticResult) {
            is AutomaticLoginResult.SessionActive -> finishLogin(
                profile = automaticResult.profile,
                credentials = restoredCredentials,
                persistCredentials = true,
            )
            is AutomaticLoginResult.Authenticated -> finishLogin(
                profile = automaticResult.profile,
                credentials = restoredCredentials,
                persistCredentials = true,
            )
            is AutomaticLoginResult.ManualRequired -> {
                captchaAnswer = ""
                val challenge = automaticResult.challenge
                if (challenge != null) {
                    manualDialogAttempts = automaticResult.attempts
                    manualDialogChallenge = challenge
                    LoginState.AwaitingCaptcha(challenge)
                } else {
                    LoginState.Failed(automaticResult.reason, canRetry = true)
                }
            }
            null -> LoginState.Failed(LoginFailure.NETWORK, canRetry = true)
        }
        automationMessage = null
    }

    fun submit(challenge: CaptchaChallenge) {
        val credentials = Credentials(username.trim(), password)
        if (!credentials.isValid || captchaAnswer.isBlank()) {
            state = LoginState.Failed(LoginFailure.INVALID_CREDENTIALS, canRetry = true)
            return
        }
        manualDialogChallenge = null
        manualDialogAttempts = 0
        state = LoginState.SubmittingCredentials
        scope.launch {
            state = try {
                when (val result = protocol.value.authenticateMis(credentials, challenge, captchaAnswer.trim())) {
                    is AuthenticationResult.Failed -> LoginState.Failed(result.reason, canRetry = true)
                    is AuthenticationResult.Success -> finishLogin(
                        profile = result.profile,
                        credentials = credentials,
                        persistCredentials = rememberCredentials,
                    )
                }
            } catch (_: Exception) {
                LoginState.Failed(LoginFailure.NETWORK, canRetry = true)
            }
        }
    }

    fun logout(accountScope: String) {
        if (protocol.isInitialized()) protocol.value.logout()
        username = ""
        password = ""
        captchaAnswer = ""
        rememberCredentials = securityCoordinator.canStoreCredentials
        automationMessage = null
        manualDialogChallenge = null
        manualDialogAttempts = 0
        storageMessage = null
        state = reduceLoginState(state, LoginEvent.Logout)
        scope.launch {
            val secureCleared = securityCoordinator.clear()
            val cacheCleared = accountScope.isBlank() || runCatching {
                cacheStore.clearAccount(accountScope)
            }.isSuccess
            storageMessage = when {
                !secureCleared && !cacheCleared -> "会话已退出，但安全存储和本地缓存清除失败。"
                !secureCleared -> "会话已退出，但系统安全存储清除失败。"
                !cacheCleared -> "会话已退出，但本地缓存清除失败。"
                else -> null
            }
        }
    }

    val signedIn = state as? LoginState.SignedIn
    if (signedIn != null && protocol.isInitialized()) {
        val smartPlatformEndpoint = remember(platform.family) {
            smartPlatformEndpointFor(platform.family)
        }
        val homeChangeFeed = remember(signedIn.profile.studentId, cacheStore) {
            CacheStoreHomeChangeFeedRepository(signedIn.profile.studentId, cacheStore)
        }
        val gradeRepository = remember(signedIn.profile.studentId, cacheStore) {
            DefaultGradeRepository(
                accountScope = signedIn.profile.studentId,
                local = CacheStoreGradeLocalDataSource(cacheStore),
                remote = SchoolGradeRemoteDataSource(transport.value),
            )
        }
        val gradeModel = remember(gradeRepository, homeChangeFeed) {
            GradeScreenModel(gradeRepository, gradeChangeRecorder(homeChangeFeed))
        }
        val courseScheduleRepository = remember(signedIn.profile.studentId, cacheStore) {
            DefaultCourseScheduleRepository(
                accountScope = signedIn.profile.studentId,
                local = CacheStoreCourseScheduleLocalDataSource(cacheStore),
                remote = SchoolCourseScheduleRemoteDataSource(transport.value),
            )
        }
        val courseScheduleModel = remember(courseScheduleRepository, homeChangeFeed) {
            CourseScheduleScreenModel(
                repository = courseScheduleRepository,
                changeRecorder = courseChangeRecorder(homeChangeFeed),
            )
        }
        val examScheduleRepository = remember(signedIn.profile.studentId, cacheStore) {
            DefaultExamScheduleRepository(
                accountScope = signedIn.profile.studentId,
                local = CacheStoreExamScheduleLocalDataSource(cacheStore),
                remote = SchoolExamScheduleRemoteDataSource(transport.value),
            )
        }
        val examScheduleModel = remember(examScheduleRepository, homeChangeFeed) {
            ExamScheduleScreenModel(examScheduleRepository, examChangeRecorder(homeChangeFeed))
        }
        val homeworkRepository = remember(signedIn.profile.studentId, cacheStore, smartPlatformEndpoint) {
            DefaultHomeworkRepository(
                accountScope = signedIn.profile.studentId,
                local = CacheStoreHomeworkLocalDataSource(cacheStore),
                remote = SchoolHomeworkRemoteDataSource(
                    transport = transport.value,
                    endpoint = smartPlatformEndpoint,
                ),
            )
        }
        val homeworkModel = remember(homeworkRepository, homeChangeFeed) {
            HomeworkScreenModel(homeworkRepository, homeworkChangeRecorder(homeChangeFeed))
        }
        val coursewareRepository = remember(signedIn.profile.studentId, cacheStore, smartPlatformEndpoint) {
            DefaultCoursewareRepository(
                accountScope = signedIn.profile.studentId,
                local = CacheStoreCoursewareLocalDataSource(cacheStore),
                remote = SchoolCoursewareRemoteDataSource(
                    transport = transport.value,
                    endpoint = smartPlatformEndpoint,
                ),
            )
        }
        val coursewareModel = remember(coursewareRepository) {
            CoursewareScreenModel(coursewareRepository)
        }
        val otherFunctionRepository = remember {
            DefaultOtherFunctionRepository(
                remote = SchoolOtherFunctionRemoteDataSource(transport.value),
            )
        }
        val otherFunctionModel = remember(otherFunctionRepository, homeworkFileGateway) {
            OtherFunctionScreenModel(otherFunctionRepository, homeworkFileGateway)
        }
        val classroomRepository = remember {
            DefaultClassroomRepository(
                remote = SchoolClassroomRemoteDataSource(createSchoolHttpTransport()),
            )
        }
        val classroomModel = remember(classroomRepository) {
            ClassroomScreenModel(classroomRepository)
        }
        val settingsModel = remember(signedIn.profile.studentId, cacheStore) {
            SettingsScreenModel(
                initialPreferences = appPreferences,
                persistPreferences = onPreferencesChanged,
                clearAccountCache = {
                    runCatching { cacheStore.clearAccount(signedIn.profile.studentId) }.isSuccess
                },
            )
        }
        val mailboxModel = remember(signedIn.profile.studentId) {
            MailboxScreenModel(transport.value)
        }
        val homeStatusRepository = remember(signedIn.profile.studentId, cacheStore) {
            DefaultHomeStatusRepository(
                accountScope = signedIn.profile.studentId,
                local = CacheStoreHomeStatusLocalDataSource(cacheStore),
                remote = SchoolHomeStatusRemoteDataSource(transport.value),
            )
        }
        val homeModel = remember(homeStatusRepository) { HomeScreenModel(homeStatusRepository) }
        AuthenticatedAppShell(
            profile = signedIn.profile,
            platform = platform,
            windowClass = windowClass,
            gradeModel = gradeModel,
            courseScheduleModel = courseScheduleModel,
            examScheduleModel = examScheduleModel,
            homeworkModel = homeworkModel,
            coursewareModel = coursewareModel,
            otherFunctionModel = otherFunctionModel,
            classroomModel = classroomModel,
            settingsModel = settingsModel,
            loginSyncPreferences = appPreferences,
            mailboxModel = mailboxModel,
            homeModel = homeModel,
            homeChangeFeed = homeChangeFeed,
            homeworkFileGateway = homeworkFileGateway,
            coursewareDirectoryGateway = coursewareDirectoryGateway,
            appCommandBus = appCommandBus,
            onLogout = { logout(signedIn.profile.studentId) },
        )
        return
    }

    val loginContent = @Composable {
        LoginScreen(
            platform = platform,
            windowClass = windowClass,
            state = state,
            username = username,
            password = password,
            captchaAnswer = captchaAnswer,
            canRememberCredentials = securityCoordinator.canStoreCredentials,
            rememberCredentials = rememberCredentials,
            storageReady = storageReady,
            storageMessage = storageMessage,
            automationMessage = automationMessage,
            onUsernameChange = { username = it },
            onPasswordChange = { password = it },
            onCaptchaAnswerChange = { captchaAnswer = it },
            onRememberCredentialsChange = { enabled ->
                // 只更新勾选状态；是否真正写入/清除系统安全存储由登录提交或退出登录时统一处理，
                // 避免未签名平台上每次取消勾选都触发 Keychain 报错。
                rememberCredentials = enabled
            },
            onLoadChallenge = {
                captchaAnswer = ""
                loadChallenge()
            },
            onSubmit = { challenge -> submit(challenge) },
            onLogout = { logout(username.trim()) },
        )
    }

    val fallbackChallenge = manualDialogChallenge
    if (fallbackChallenge != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("需要手动输入验证码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "自动登录已尝试 ${manualDialogAttempts.coerceAtLeast(1)} 次。" +
                            "请手动输入当前验证码答案，或重新填写账号和密码。",
                    )
                    CaptchaBlock(
                        challenge = fallbackChallenge,
                        loading = false,
                        answer = captchaAnswer,
                        enabled = state !is LoginState.SubmittingCredentials,
                        canSubmit = captchaAnswer.isNotBlank(),
                        onAnswerChange = { captchaAnswer = it },
                        onRefresh = {
                            captchaAnswer = ""
                            loadChallenge(openManualDialog = true)
                        },
                        onSubmit = { submit(fallbackChallenge) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    manualDialogChallenge = null
                    manualDialogAttempts = 0
                    if (protocol.isInitialized()) protocol.value.logout()
                    username = ""
                    password = ""
                    captchaAnswer = ""
                    rememberCredentials = securityCoordinator.canStoreCredentials
                    state = LoginState.SignedOut
                    scope.launch {
                        securityCoordinator.clear()
                        loadChallenge()
                    }
                }) {
                    Text("重新输入账号和密码")
                }
            },
        )
    }

    if (platform.family == PlatformFamily.MacOS) {
        // macOS：右键输入框用 AWT JPopupMenu 渲染原生样式菜单（剪切/拷贝/粘贴/全选）。
        ProvideNativeTextContextMenu { loginContent() }
    } else {
        loginContent()
    }
}

@Composable
fun LoginScreen(
    platform: PlatformInfo,
    windowClass: WindowClass,
    state: LoginState,
    username: String,
    password: String,
    captchaAnswer: String,
    canRememberCredentials: Boolean,
    rememberCredentials: Boolean,
    storageReady: Boolean,
    storageMessage: String?,
    automationMessage: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCaptchaAnswerChange: (String) -> Unit,
    onRememberCredentialsChange: (Boolean) -> Unit,
    onLoadChallenge: () -> Unit,
    onSubmit: (CaptchaChallenge) -> Unit,
    onLogout: () -> Unit,
) {
    val scroll = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val dismissKeyboardModifier = Modifier.pointerInput(Unit) {
        detectTapGestures {
            focusManager.clearFocus(force = true)
            dismissPlatformKeyboard()
        }
    }
    if (windowClass == WindowClass.Expanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .platformLoginKeyboardAvoidance()
                .then(dismissKeyboardModifier)
                .padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoginIntroduction(platform, windowClass, Modifier.weight(2f))
            LoginCard(
                compact = false,
                state = state,
                username = username,
                password = password,
                captchaAnswer = captchaAnswer,
                canRememberCredentials = canRememberCredentials,
                rememberCredentials = rememberCredentials,
                storageReady = storageReady,
                storageMessage = storageMessage,
                automationMessage = automationMessage,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onCaptchaAnswerChange = onCaptchaAnswerChange,
                onRememberCredentialsChange = onRememberCredentialsChange,
                onLoadChallenge = onLoadChallenge,
                onSubmit = onSubmit,
                onLogout = onLogout,
                modifier = Modifier.weight(3f).heightIn(max = 760.dp).verticalScroll(scroll),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .platformLoginKeyboardAvoidance()
                .then(dismissKeyboardModifier)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            LoginIntroduction(platform, windowClass, Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            LoginCard(
                compact = true,
                state = state,
                username = username,
                password = password,
                captchaAnswer = captchaAnswer,
                canRememberCredentials = canRememberCredentials,
                rememberCredentials = rememberCredentials,
                storageReady = storageReady,
                storageMessage = storageMessage,
                automationMessage = automationMessage,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onCaptchaAnswerChange = onCaptchaAnswerChange,
                onRememberCredentialsChange = onRememberCredentialsChange,
                onLoadChallenge = onLoadChallenge,
                onSubmit = onSubmit,
                onLogout = onLogout,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LoginIntroduction(
    platform: PlatformInfo,
    windowClass: WindowClass,
    modifier: Modifier,
) {
    val compact = windowClass != WindowClass.Expanded
    Column(
        modifier = modifier.padding(
            horizontal = if (compact) 4.dp else 8.dp,
            vertical = if (compact) 8.dp else 16.dp,
        ),
    ) {
        Text(
            "交大自由行",
            fontSize = if (compact) 34.sp else 44.sp,
            lineHeight = if (compact) 40.sp else 52.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "KMP Refreshed",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 自绘的小眼睛图标（不引入 material-icons 依赖）。[visible] 为 true 表示眼睛睁开
 * （密码可见），false 表示带斜线的闭眼（密码隐藏）。
 */
@Composable
private fun PasswordVisibilityIcon(visible: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        // 眼睛外轮廓（杏仁形）
        drawOval(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.22f),
            size = Size(w * 0.84f, h * 0.56f),
            style = stroke,
        )
        // 瞳孔
        drawCircle(
            color = tint,
            radius = w * 0.13f,
            center = Offset(w / 2f, h / 2f),
        )
        if (!visible) {
            // 隐藏：画一条左上到右下的斜线
            drawLine(
                color = tint,
                start = Offset(w * 0.16f, h * 0.16f),
                end = Offset(w * 0.84f, h * 0.84f),
                strokeWidth = w * 0.09f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun LoginCard(    compact: Boolean,
    state: LoginState,
    username: String,
    password: String,
    captchaAnswer: String,
    canRememberCredentials: Boolean,
    rememberCredentials: Boolean,
    storageReady: Boolean,
    storageMessage: String?,
    automationMessage: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCaptchaAnswerChange: (String) -> Unit,
    onRememberCredentialsChange: (Boolean) -> Unit,
    onLoadChallenge: () -> Unit,
    onSubmit: (CaptchaChallenge) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier,
) {
    val busy = !storageReady ||
        state is LoginState.CheckingSession ||
        state is LoginState.SubmittingCredentials ||
        state is LoginState.LinkingAcademicSystem
    val challenge = (state as? LoginState.AwaitingCaptcha)?.challenge
    val canSubmit = challenge != null &&
        username.isNotBlank() &&
        password.isNotBlank() &&
        captchaAnswer.isNotBlank()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 18.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
        ) {
            Text("登录", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "先填写账号密码，再获取当前验证码。验证码刷新后，旧答案会立即失效。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (automationMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ProgressRow(
                        message = automationMessage,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            PlatformCredentialFields(
                username = username,
                password = password,
                enabled = !busy,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onPasswordImeAction = {
                    dismissPlatformKeyboard()
                    when {
                        canSubmit -> onSubmit(checkNotNull(challenge))
                        challenge == null && !busy -> onLoadChallenge()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (canRememberCredentials) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = rememberCredentials,
                            enabled = !busy,
                            role = Role.Checkbox,
                            onValueChange = onRememberCredentialsChange,
                        )
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = rememberCredentials,
                        onCheckedChange = null,
                        enabled = !busy,
                    )
                    Text(
                        "在此设备上安全保存登录信息",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // 验证码区块常驻：打开应用即自动加载，账号密码+登录区高度因此固定不跳动。
            CaptchaBlock(
                challenge = challenge,
                loading = state is LoginState.CheckingSession,
                answer = captchaAnswer,
                enabled = !busy,
                canSubmit = canSubmit,
                onAnswerChange = onCaptchaAnswerChange,
                onRefresh = onLoadChallenge,
                onSubmit = { challenge?.let(onSubmit) },
            )

            when (state) {
                LoginState.CheckingSession -> Unit
                LoginState.SubmittingCredentials -> ProgressRow("正在验证账号与验证码…")
                is LoginState.LinkingAcademicSystem -> ProgressRow("MIS 已通过，正在连接教务系统…")
                is LoginState.Failed -> ErrorMessage(state.reason)
                is LoginState.SignedIn -> Unit
                else -> Unit
            }
        }
    }
}

/** Android 以及原生 macOS helper 不可用时的 Compose 回退。 */
@Composable
internal fun ComposeCredentialFields(
    username: String,
    password: String,
    enabled: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordImeAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("学号") },
            singleLine = true,
            keyboardOptions = usernameKeyboardOptions().copy(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { passwordFocusRequester.requestFocus() },
            ),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = if (showsPasswordVisibilityToggle) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        PasswordVisibilityIcon(
                            visible = passwordVisible,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                null
            },
            keyboardOptions = passwordKeyboardOptions().copy(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    focusManager.clearFocus(force = true)
                    onPasswordImeAction()
                },
            ),
        )
    }
}

@Composable
private fun CaptchaBlock(
    challenge: CaptchaChallenge?,
    loading: Boolean,
    answer: String,
    enabled: Boolean,
    canSubmit: Boolean,
    onAnswerChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val bitmap = remember(challenge?.imageBytes) {
        try {
            challenge?.imageBytes?.decodeToImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                // 验证码本体约 130×42；区域常驻占位，加载完成前显示进度而非空白。
                modifier = Modifier
                    .width(150.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        bitmap != null -> Image(
                            bitmap = bitmap,
                            contentDescription = "验证码图片",
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            // Fit 保留像素比例，避免 FillBounds 拉糊。
                            contentScale = ContentScale.Fit,
                        )
                        loading -> CircularProgressIndicator(
                            modifier = Modifier.width(22.dp).height(22.dp),
                            strokeWidth = 2.dp,
                        )
                        else -> Text("点击刷新获取", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = {
                    focusManager.clearFocus(force = true)
                    dismissPlatformKeyboard()
                    onRefresh()
                },
                enabled = enabled,
            ) {
                Text("刷新")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = answer,
                onValueChange = onAnswerChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                label = { Text("验证码答案") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        focusManager.clearFocus(force = true)
                        dismissPlatformKeyboard()
                        when {
                            canSubmit -> onSubmit()
                            enabled && challenge == null -> onRefresh()
                        }
                    },
                ),
            )
            Button(
                onClick = {
                    focusManager.clearFocus(force = true)
                    dismissPlatformKeyboard()
                    onSubmit()
                },
                enabled = enabled && canSubmit,
            ) {
                Text("登录")
            }
        }
    }
}

@Composable
private fun ProgressRow(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorMessage(reason: LoginFailure) {
    val message = when (reason) {
        LoginFailure.INVALID_CREDENTIALS -> "请完整填写学号、密码和验证码答案。"
        LoginFailure.CAPTCHA_REJECTED -> "登录未通过。请刷新验证码，并检查账号、密码和答案。"
        LoginFailure.CAPTCHA_RECOGNITION_FAILED -> "验证码自动识别未通过，请手动输入当前验证码答案。"
        LoginFailure.SESSION_EXPIRED -> "检测到旧会话，但缺少可恢复资料。请清除会话后重试。"
        LoginFailure.MALFORMED_RESPONSE -> "学校页面结构已变化，暂时无法继续登录。"
        LoginFailure.NETWORK -> "无法连接学校登录服务，请检查网络后重试。"
        LoginFailure.ACADEMIC_LINK_FAILED -> "MIS 已登录，但教务系统连接失败。"
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
