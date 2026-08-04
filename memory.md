# BJTUselfService KMP 迁移工作记忆

> 最后更新：2026-08-05
> 当前分支：`main`（跟踪 `mine/main` → `JasonZhang1225/BJTUselfService-AppleDevice`）
> 分支创建点：`9d8da18`；发布与功能基线：`v1.7.0@419313d`
> 远端历史：2026-08-03 已强制改写为单根提交 `46f6ef9`；远端仅 `HEAD/main`、无标签/PR/下游 fork。旧 SHA 仍可被 GitHub 缓存直接解析；Support 要求先轮换泄露凭据，用户决定不再提交清缓存工单并自行更换密码。
> 完整历史与已归档的验收细节：见 `history_full.md`（按里程碑归档，只读）
> 本文件是实时工作记忆，不是只追加日志：任务开始读、结束改，只保留当前接续工作需要的状态。

## 1. 本阶段已做到（≤10 行）

- 2026-08-05 成绩页新增课程性质功能（M9，真实教务页面验证后实施）：成绩接口表格本身无课程性质列（8 列为序号/学年/课程/学分/成绩/加分成绩/教师/详情），课程性质从培养方案页交叉比对获得——每次手动刷新成绩时抓 `/training/training/program/` 列表页与全部 `stuview/<id>/` 详情页（rowspan/colspan 课组跟踪解析，实测 943 门课、成绩匹配率 100%），产出"课程号→必修/限选/任选/体育"映射，与成绩、自选记录同事务落库（新表 `program_course_type_cache`，`2.sqm` v2→v3 迁移）；成绩行不冗余存类别，计算时按课程名前缀课程号 join，查不到安全降级"其他类别"。自选课程模式新增 5 个三态 chips（必修/限选/任选/体育/其他类别，全选/部分/未选配色+计数），支持按类别批量勾选与排除（保研口径=必修+限选可一键达成）；体育课因学校培养方案 PDF/教务系统方案页/官方成绩单三层口径不一致（环节必修 vs 课程任选），独立为"体育"类别（体育Ⅰ/92 门专项课/体测课全覆盖）；修复课程号贪婪匹配吞课程名首字母 bug（`M202015BC语言`→`M202015B`）。desktopTest/assembleDebug 全绿，修订记录见 `docs/migration/m9-grade-course-type-plan.md`，待办见根目录 `体育课疑惑.md`（待问学校确认体育专项课是否计入保研）。

- 2026-08-05 导航重构为 CMP Navigation 2.9.2 NavHost：引入 `navigation-compose` 依赖；删除 `var section` 状态，`rememberNavController` + backstack 驱动；一级页走标准 tab 模式（popUpTo(HOME){saveState}+restoreState，切走保存/切回恢复各页状态），"更多"子页压栈 push，顶栏返回箭头与系统返回（keyevent/预测性返回手势）pop 回上一级；compact 分支 NavHost 带 iOS 风格过渡动画（进子页右滑入、返回右滑出、tab 切换淡入淡出），expanded 分支无动画；Android targetSdk 34→35 + manifest `enableOnBackInvokedCallback=true`，dumpsys 确认 Navigation 的 OnBackInvokedCallback 已注册（预测性返回生效）；二级页隐藏底栏且不再留导航条安全区占位（实测无遮挡）；NavHost 冷启动落首页、force-stop 后不恢复 backstack（uiautomator 验证：更多→子页→返回/系统返回→根→首页、tab 往返、成绩页正常显示）。模拟器"启动落设置页"疑云实为 install -r 不杀旧进程 + uiautomator dump 失败读旧文件叠加，非代码问题。
- 2026-08-05 "更多"子页隐藏底栏（二级页语义，对应 iOS push 后 tab bar 收起）：`isMoreSubPage` 统一判断，隐藏时以 `navigationBarsPadding()` 占位保证 Android edge-to-edge 下内容不被手势条遮挡，返回箭头回"更多"根后底栏恢复。模拟器验证：首页/更多根有底栏，校历下载/设置子页无底栏、返回恢复、设置页底部按钮不被手势条遮挡。
- 2026-08-05 校历下载页新增"当前最新"文件名展示：Remote 增加只解析页面不发 PDF 的 `fetchCalendarFileName()`（复用 fetchCalendarPostfix，文件名做 URL 解码），Repository 失败返 null 静默降级，Model 加 `calendarFileName/calendarFileNameLoading` 状态，进入页面 LaunchedEffect 自动拉取，卡片内以主题色小字显示"当前最新：2024-2025校历.pdf"，加载中显示"正在获取最新校历…"；Remote 3 个新用例 + Model 2 个新用例，desktopTest 通过，Android 模拟器真实数据验证（解析出 2024-2025校历.pdf）。
- 2026-08-05 成绩单下载页语言选择由 Switch 改为中文版/英文版分段按钮（选中段填主题色）；下载进行中 TaskStatusRow 的"正在下载…"去掉转圈只留文字，转圈仅保留在下载按钮内。用户实机确认无问题。
- 2026-08-04 "更多"页第二轮核对完成并 Android 模拟器截图验证：校历下载/成绩单下载从"其他功能"拆为独立 AppSection 列进"更多"根目录（点进去才有下载按钮，成绩单保留中英文开关），"其他功能"随之移除（含 AppCommand 与桌面菜单项）；所有"更多"子页顶栏左上角新增返回箭头（Canvas 左尖括号，点击回"更多"根目录，宽屏侧栏布局不变）；设置页内重复大标题改为仅宽屏显示。期间另一 session 改造成绩页（DefaultGradeRepository 新增 programRemote/课程性质映射），其测试 Fake 未同步导致 commonTest 编译暂断，主线三端编译与 assembleDebug 不受影响；未代为改其测试。
- 2026-08-04 静默自动登录入场已实现并三端测试通过：登录成功时把最小档案快照（姓名/学号/身份/学院）写入 CacheStore metadata；冷启动恢复凭据且档案存在时跳过登录页直接渲染主界面，顶栏指示器先"登录中"、登录完成才"同步中"，期间各 ScreenModel 初始化与下拉刷新被门控（避免无会话请求）；自动登录多次失败改为主界面上的引导弹窗，确认后回登录页；凭据恢复完成前不渲染，消除登录页闪帧。Android 模拟器冷启动链路（启动页→登录中→同步中→真实数据）截图验证，Desktop/iOS Simulator 测试通过；iOS 真机用户确认生效（首次生效需先完成一次登录写入档案缓存）。
- 2026-08-04 Apple Design 框架改造完成并实机验证：紧凑端改为底部 5 tab（首页/课程表/成绩/作业/更多），顶栏收敛为大标题+同步指示并修复状态栏重叠，内容区接入下拉刷新，考试/课件/其他/教室/邮箱/设置收进"更多"页；明文警告仅在作业/课件页出现且可会话内关闭。Android 明文 HTTP 已授权修复（策略三端统一走 `LegacyHttp`，networkSecurityConfig 仅放行 `123.121.147.7`），模拟器上作业页取得真实数据。逐页核对首轮（首页）已完成：顶栏背景改 background 与 iOS SwiftUI 根同色、iOS 去掉重复 statusBarsPadding（真机间距异常修复），首页本周日程提至首栏、校园卡/校园网余额半宽并列、compact 副标题移除。页内 UI 其余页面留待逐页核对。截图证据在 `.artifacts/uiaudit/`。
- 2026-08-04 本地四端分发包已输出到根目录 `builtapps/`（已 gitignore）：原版 Android `交大自由行NEO-v1.7.0-debug.apk`、KMP Android `交大自由行KMP-0.1.0-debug.apk`、KMP iOS 真机开发签名 `.app`+zip、KMP macOS `.app`+`BJTUselfServiceKMP-1.0.0.dmg`。均为 debug/开发签名，非正式发布包。
- **已完成（主线已通）**：M0 基线/工具链、M1 骨架、M2 领域层、M3 登录网络解析、M4 持久化（仅剩 iOS Keychain 签名往返）、M5 首页+八个业务切片、M6 平台能力（文件/网页/邮箱/macOS 菜单与窗口）、M7 三端真实登录验收。
- **等外部条件**：真实数据变化样本（等学校侧自然产生，不造假）、真实作业上传（等学校布置新作业）、退出账号往返（留到有效会话不再需要时）。
- **缺权限/签名**：iOS Keychain 合法签名往返（`errSecMissingEntitlement`）、Apple Developer Team/真机/正式签名/公证/隐私问卷。
- **当前最优先（按顺序）**：M5.5 登录页 UI + 验证码模型 → M5.6 用户手动确认全部 UI → 真实数据验证。
- 2026-08-01 移除验证性 iOS WidgetKit/App Group/共享快照实现；Desktop 233 项、iOS Simulator 218 项测试零失败，三端与 Apple 元数据脚本通过，最终 iOS `.app` 无 `PlugIns`。
- 2026-08-01 已完善首页跳转链接与二维码（完美校园/校园网，详见 `history_full.md` M5-0）；macOS 二维码与 Android 浏览器动作的独立复验仍未执行。
- 2026-08-01 M5.5.1 登录页 UI 首版已改（标题简化「交大自由行 / KMP Refreshed」、验证码缩小、答案框+登录按钮同行、删除安全存储小字与取消勾选即报错）；三端共享代码编译通过，尚未做三端视觉复验。
- 2026-08-01 M5.5.1 第二轮登录页 UI 已改并 iOS/Android 模拟器实机确认：整个 App 跟随系统深浅色（删除持久化主题偏好与设置页切换）、打开即自动取验证码、刷新右对齐、整体垂直居中、macOS 左窄右宽。
- 2026-08-01 M5.5.2 密码框平台语义已实现：新增 `LoginFieldPlatform` expect/actual；iOS 以同一 `UIView` 中两个原生字段承载 username/password AutoFill，Android 保留 Compose 字段与小眼睛。macOS 最终实现见本节 2026-08-03 收尾记录。
- 2026-08-01 M5.5 验证码链路已实现：原 23 MB PyTorch 权重重建为确定性模型（显式 `[0,1]→[-1,1]`、修正 argmax），Android 继续 TorchScript，iOS/macOS 使用同源 Core ML；24 张真实脱敏冒烟集两端均 21/24、argmax 24/24 一致，iOS Simulator `7+4→11`、Android 模拟器 `9×8→72`、macOS 分发包推理均通过。自动识别默认开启，安全保存默认勾选；有已存凭据时启动检查会话并最多自动登录 3 次，显示“正在检查登录，正在自动登录”，失败后弹出手动验证码与“重新输入账号和密码”。Desktop 242 项、iOS Simulator 227 项测试零失败，Android Debug、iOS arm64 Simulator 宿主、macOS 分发包及签名校验通过。
- 2026-08-02 已完成登录后同步与真机性能收尾：成绩/作业/课表/考试四项自动同步对未保存设置默认开启，用户显式关闭后仍持久化；作业 45 个列表请求与得分查询改为最多 3 个有界并发并保持原顺序，真机完整刷新由 16.258 秒降至 5.026 秒；课件改为先发布课程目录、当前课程首层按需加载、其他课程与文件夹按需加载，清缓存两轮从开始到首层分别约 2.06/1.89 秒（旧实现 12.143 秒）。临时 `BJTU_PERF`/expect-actual 日志已全部删除，最终无日志签名包已覆盖安装真机；Desktop/iOS 全测与 Android Debug 通过。
- 2026-08-02 已修复 iOS 真机签名：根因是 `iosApp.xcodeproj/project.pbxproj` Debug/Release 配置硬编码 `CODE_SIGNING_ALLOWED = NO`，产物完全无签名导致 `0xe800801c` 安装失败；移除该行并保留 `DEVELOPMENT_TEAM = 34S53DC6T6` 后，自动签名解析到 iOS Team Provisioning Profile，`xcodebuild` 真机构建、`codesign` 校验、`devicectl` 安装均通过；首次启动在 iPhone「设置→通用→VPN 与设备管理」信任 `Apple Development: 3236345078@qq.com` 后可用。iOS Keychain 合法签名往返的缺口已具备签名身份，待复验。
- 2026-08-03 iOS 登录页与安全区交互收尾：原生凭据框改为 14pt 圆角和动态配色，空白收键盘、Next→Go 与验证码 Go 链路已接通；键盘避让改为单一平台所有者，避免关闭键盘时残留白色 spacer。SwiftUI 根 `ZStack` 与 `ComposeView` 两层均穿透底部 container safe area；LLDB 确认 `UIWindow 402×874`、Compose host `(0,62;402,812)` 正好绘制到 y=874 且 bottom inset 为 0。成绩、课程表、考试、作业、课件、其他功能与教室紧凑页的固定底部 padding 已改为仅保留顶部/横向间距，首页与设置的滚动内容 padding 不变；iPhone 17 iOS 26.5 Simulator 已目视复核课件与考试底边，用户确认白边解决。
- 2026-08-03 登录凭据 UI 与自动验证码入口已收尾：主登录页隐藏验证码，只保留账号、密码、安全保存和全宽登录按钮；首次输入与已保存凭据统一后台使用新验证码自动登录，模型不可用、识别/提交失败及瞬时网络异常均最多尝试 3 次，随后弹出“请输入验证码”，提供换图、答案、继续登录和“修改账号和密码”，手动失败保持弹框并换新图。macOS 使用原生 AppKit 凭据框并保留系统中文“自动填充→密码…”；iOS 互操作承载层改用当前 `surface` 色，iPhone 17 Pro iOS 26.5 Simulator 已目视确认浅/深色无黑色矩形且主页无验证码占位。Desktop 测试、iOS Simulator 测试、Android Debug、macOS 分发包和 iOS Simulator 宿主构建通过；手动弹框真实失败路径与 iPhone 真机 Password AutoFill/Keychain 仍待复验。`cas.bjtu.edu.cn` AASA 为 404、`mis.bjtu.edu.cn` 为 403，网页系统凭据不保证跨字段成组返回。

## 2. 当前痛点（≤8 条）

- **验证码发布级准确率仍待扩样**：当前 24 张固定真实冒烟集单次表达式正确率 87.5%（21/24），两端完全一致；3 次独立新验证码理论成功率约 99.8%，且失败会回退手动，但公版前仍应扩大到至少 300 张独立留出集，不能把 24 张冒烟集当成最终精度门禁。
- **登录页仍缺 iPhone 真机与真实失败验收**：2026-08-04 第二段 120 fps 真机录屏推翻“仅为滚动回弹”的判断：Password AutoFill 的系统认证 HUD 结束后，SwiftUI 仍按键盘 safe area 缩短 Compose 宿主，页面整体上移并在宿主底边露出纯黑横带。现已固定 SwiftUI 根宿主为全屏，由 Compose 仅在可编辑状态添加 `imePadding`，自动登录开始即移除 IME padding；同时保留加载态清焦点、滚动归零和禁用拖拽。iOS/Android/Desktop 102 项回归通过，iPhone 17 Pro Simulator 已目视确认键盘出现时表单可达、关闭后完整居中且无黑带，签名真机构建与覆盖安装成功，仍待用户复验 Password AutoFill→自动登录的确切真机时序。真机还需核对 Keychain 保存/重启读取；手动验证码弹框等待自然失败时目视。
- **课件深层按需请求仍缺真实文件夹样本**：当前首门课程首层为 4 个非文件夹项目，已验证课程目录与课程首层冷缓存往返，但无法验证首次展开文件夹；等真实课程出现文件夹时自然补测，不为此制造数据。
- 服务器真实数据变化样本缺失（四类信息流只验证了基线建立，新增/修改/删除仍无样本）。
- **iOS Keychain 运行往返仍待专项复验**：2026-08-02 已获得合法开发签名并多次覆盖安装成功，未签名 Simulator 的 `errSecMissingEntitlement` 已不再是权限阻塞；仍需用专项步骤证明保存、重启读取与退出清理。
- 官方 1.7.0 与当前 KMP PyTorch 2.1 在 API 37.1 模拟器首次启动都会出现 16 KB page-size 兼容提示（KMP 涉及 PyTorch、FBJNI、C++ shared 与 androidx graphics path）；兼容模式下推理已通过，但正式 Android 发布前需升级或替换不对齐的原生库。

## 3. 接下来 1～3 个阶段

1. **M5.5 登录页与凭据收尾**：在 iPhone 真机专项验证 Keychain 保存、重启读取和退出清理，并目视密码键盘/自动填充；等待自然自动登录失败时目视手动验证码弹框，不为此故意提交错误密码；macOS 待用户用真实系统密码条目复验一次选择填充。
2. **自然样本补证**：课程出现真实文件夹时验证首次展开按需请求；学校布置新作业时再验证上传；服务器自然产生变化时验证信息流新增/修改/删除。
3. **M5.5/M5.6 后续**：验证码公版前补至少 300 张独立留出集；UI 人工确认已恢复，当前处于逐页核对阶段（框架改造 2026-08-04 完成，页内细节按用户逐页指定推进）。

## 维护规则

- 每次开始目标模式任务时先读本文件；结束前必须再次更新。
- 已完成事项压缩为一行保留在“本阶段已做到”；里程碑真正完成时，把细节归档进 `history_full.md` 并从本文件删除。
- 痛点解除即删除或改写，不保留已经失效的阻塞描述。
- 近期计划只保留接下来 1～3 个可执行阶段；远期内容留在 `goal.md`。
- 事实、命令结果和验证边界要具体；不能把计划写成已完成。
- 分支、基线、最新 Release 或工作区状态发生变化时，更新文件顶部摘要。
- 不在本文件写入账号、密码、Cookie、令牌、真实验证码会话或其他敏感信息。
