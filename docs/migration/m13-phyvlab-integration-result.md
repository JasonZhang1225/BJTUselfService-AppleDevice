# M13：物理在线初步开发记录

> 记录日期：2026-08-27（Asia/Shanghai）
> 状态：**代码层初步开发完成，跨平台与真实账号验收待继续**。本文件是 M13 的收口记录，不把尚未取得运行证据的行为写成正式完成。

## 0. 里程碑归属（重要）

- **M13 = 物理在线（phyvlab.bjtu.edu.cn）接入**。
- **M14 = Windows 桌面端移植**，已在 `v1.7.3-KMP` 期间完成并归档于 [`windows-port-plan.md`](windows-port-plan.md) 与 `history_full.md`。
- 本轮物理在线初步开发整合为一个独立的 **M13**，不改名为 M14，也不覆盖 M14 的 Windows 历史。
- 对照未修改的 `main`/`origin/main`：当前共同指向 **`a342615` / `v1.7.3-KMP-B`**；这是已完成 M14 Windows 基座上的 M13 工作区改动。
- `history_full.md` 的历史顺序也保持不变：先记录 M13 物理在线规划，再记录 M14 Windows 移植；本文件只补充 M13 的当前首版结果。

## 1. 本轮实现范围

### 入口与会话

- “更多”→“学业”中，物理在线为第一项。
- 复用 App 的 CAS/MIS Ktor 会话，按 Moodle OAuth2 authorization-code 流程建立 `MoodleSession`；跳转仅允许 `https://phyvlab.bjtu.edu.cn` 与 `https://cas.bjtu.edu.cn`。
- 物理在线 MoodleSession 单独过期时，先在 App 内强制获取一次 CAS challenge，并用当前内存凭据与现有验证码识别器恢复；不会因为会话失效自动打开系统浏览器，恢复失败才提示退出并重新登录主账号。
- 不复制或持久化密码、Cookie、OAuth code、state、sesskey、draft item 等敏感/短期值。

### 课程、作业与首页日程

- 读取我的课程、课程中的标准 Moodle 作业、完成状态和开放/截止时间。
- `Thursday` 等英文星期标记会从日期展示中移除，统一为 `yyyy年MM月dd日 HH:mm`；日期解析、月份切换和首页日历归日均固定按北京时间（`Asia/Shanghai`），不随设备时区漂移。
- 课程作业列表增加正/逆序切换，默认按 Moodle 活动 ID 降序（新的在上方，直接反转原本旧的正序），切换后可按旧的在上方查看；截止时间只用于展示，不参与新旧判断。
- 首页日程窗格显示物理在线截止安排，并保留“物理截止”来源标记。
- 设置→自动同步中增加“自动同步物理在线”开关；开启后登录完成会自动拉取课程与作业安排，关闭后进入物理在线页仍可手动同步。

### 原生作业详情、成绩和上传

- 点击作业在 App 内打开原生详情，不再自动跳转浏览器。
- 详情显示作业要求、开放/截止时间、提交状态/时间、批改状态、成绩、教师评语和已提交文件。
- 普通 Moodle 文件型作业支持“草稿区上传→最终表单提交”；选文件后必须再次显式确认，提交后重新读取详情。
- Unity/WebGL 虚拟实验、quiz、选课、完成标记、讨论和 Unity 数据上传仍保留网页流程；网页备用入口只有用户主动点击才打开。

### 触摸与平台实现

- Windows/macOS 桌面主要纵向列表、详情页和横向筛选栏增加触摸拖动兼容层；滚轮保持原行为，Android/iOS 使用平台原生滚动。
- Android 使用系统文档选择器和 `Intent.ACTION_VIEW`；iOS 使用 `UIDocumentPicker`/WKWebView 相关平台实现；macOS 复用 desktopApp 的 JVM/CIO/文件网关路径。

## 2. 验证证据

| 验证项 | 当前证据 |
| --- | --- |
| 物理在线 HTML/日期/会话/上传协议专项测试 | 通过（脱敏 fixture/队列传输，含英文星期清理与作业正/逆序） |
| App 内 CAS 恢复专项测试 | 通过（强制 CAS challenge、验证码认证、敏感字段脱敏） |
| 最新 M13 定向桌面回归（phyvlab + SchoolLoginProtocol） | 通过 |
| `:windowsApp:compileKotlinWindows` | 通过 |
| `:desktopApp:compileKotlin` | 通过 |
| Windows EXE/MSI 打包 | 通过；桌面副本与源包 SHA-256 一致 |
| `:shared:desktopTest` 全量 | 432 项中 1 项既有 Windows 环境失败：macOS Keychain 类不可用 |
| Android 编译/模拟器 | 已在 `C:\Users\zjg\android-sdk-codex` 安装 Android SDK Platform 36、Build Tools 36.0.0、Android Emulator 37.1.11 与 `adb 37.0.1`；`:shared:compileAndroidMain`、`:androidApp:compileDebugKotlin` 和 `:androidApp:assembleDebug` 均通过。`codex-m13-api36` AVD 已启动（`sys.boot_completed=1`），带网络权限的新 APK 已安装并进入 Compose 登录页，`MainActivity` 保持 resumed 且无应用崩溃；尚未用真实账号进入物理在线课程页 |
| iOS 共享层/模拟器/真机 | `:shared:compileKotlinIosSimulatorArm64` 已在 Windows 交叉编译通过；`:shared:compileKotlinIosArm64` 已启动但在有限窗口内无终态后安全停止；当前主机无 `xcodebuild`，Gradle 仍提示 Simulator 测试需 macOS；iOS App 运行、签名和真机验收需 macOS/Xcode |

开发版：`1.7.4-KMP-DEV`（Android/iOS build 15；Windows 数值版本 1.7.4；保留 Windows UpgradeCode 支持覆盖安装）。本次重新打包后源包与桌面副本逐字节一致：

- `C:\Users\zjg\OneDrive\Desktop\交大自由行 KMP-1.7.4-KMP-DEV.msi`（113,213,258 bytes；SHA-256 `7AA795E592E5110909469173827ABFF371048E678FFF04C179A3FCFB9B909599`）
- `C:\Users\zjg\OneDrive\Desktop\交大自由行 KMP-1.7.4-KMP-DEV.exe`（113,874,944 bytes；SHA-256 `8640F3A40D814197EA733257C6C88C2C7F565A76EAFF10AB5782CD181553D9EC`）
- `C:\Users\zjg\OneDrive\Desktop\交大自由行 KMP-1.7.4-KMP-DEV-debug.apk`（368,489,589 bytes；SHA-256 `96A6EB987B8304A90E7AE7C481625FF38CEF83AB8562E11CBD9690A8D85C0A5B`）

本机 `C:\Program Files\交大自由行 KMP\交大自由行 KMP.exe` 的文件时间早于本轮桌面包；此前观察到的 `Thursday/Saturday` 和旧作业顺序来自该旧安装，不能作为新包日期清理/排序功能的验收证据。请先安装上面的桌面开发包再复测。

## 3. 未完成事项与堵点

1. **浏览器管理员策略限制**：Edge 扩展和 Codex 内置浏览器仍会因管理员策略校验不可用而拒绝读取 `phyvlab.bjtu.edu.cn` DOM。2026-08-27 已改用本机 Chrome DevTools：用户自行登录后可以只读课程页与作业详情，并完成与解析器的结构对照；上传控件只在可编辑作业的 `action=editsubmission` 页出现。现场发现的 `作业状态`、filemanager 初始化 JSON 和反馈容器差异已在解析器中修复，并有专项测试；原始拒绝、安全边界和复核结论见 [`phyvlab-browser-policy-limit.md`](phyvlab-browser-policy-limit.md)。
2. **真实上传尚未执行**：当前只验证脱敏 Moodle 草稿上传/最终表单协议，尚未用真实账号和真实文件提交；真实提交必须由用户明确确认。
3. **移动端运行环境仍不完整**：Android SDK、`adb`、emulator、API 36 AVD 和 debug APK 已补齐，Android 登录页启动烟测已通过，但尚未用真实账号进入物理在线课程页。Windows 上 `:shared:compileKotlinIosSimulatorArm64` 已交叉编译通过（含 Skiko iOS 依赖）；`:shared:compileKotlinIosArm64` 已启动但在有限窗口内无终态后安全停止。没有 Xcode/Apple 模拟器运行闭环，iOS/macOS App 运行、签名和真机验收仍需 macOS/Xcode，不能把 Android 启动烟测或 Windows 交叉编译当作 Apple 端运行证据。
4. **Windows 真实触摸屏验收缺失**：触摸拖动通过桌面指针路径验证，UU 远程 Windows 触摸模式仍需用户实机确认。
5. **Moodle REST/Mobile token 未确认**：站点虽提供旧版移动客户端入口，但登录后 Web Service/token 权限尚未取证；首版采用登录态 HTML 解析，不保存猜测性 token。
6. **既有测试环境差异**：全量桌面测试的唯一失败是 Windows 不具备 macOS Keychain 类，不是物理在线专项失败。
7. **Windows 模拟器网络权限**：启动 Android AVD 后，Windows 曾弹出 `adb.exe` 公共/专用网络访问授权；首次出现时未代用户点击系统安全对话框，也未修改防火墙规则。用户随后已手动点击允许；后续若再次出现，只能在明确观察到同一个 `adb.exe` 授权对话框时使用这一次授权，不延伸到其他系统安全提示。

### Android 本机验证复现

本机已将命令行 SDK 放在 `C:\Users\zjg\android-sdk-codex`（不属于仓库），并安装/接受了编译所需许可。可用以下环境变量复现共享层检查：

```powershell
$env:JAVA_HOME = 'C:\Users\zjg\jdk21\jdk-21.0.8+9'
$env:ANDROID_HOME = 'C:\Users\zjg\android-sdk-codex'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_AVD_HOME = 'C:\Users\zjg\android-avd-codex'
.\gradlew.bat :shared:compileAndroidMain
```

`:shared:compileAndroidMain`、`:androidApp:compileDebugKotlin`、`:androidApp:assembleDebug` 均已通过。首次联网构建期间曾遇到 JDK 的 `Remote host terminated the handshake`，重试后依赖已补齐；debug APK 为 `C:\Users\zjg\OneDrive\Desktop\交大自由行 KMP-1.7.4-KMP-DEV-debug.apk`（368,489,589 bytes；SHA-256 `96A6EB987B8304A90E7AE7C481625FF38CEF83AB8562E11CBD9690A8D85C0A5B`）。`codex-m13-api36` AVD 已冷启动到 `emulator-5554`，`adb install -r -g` 返回 `Success`，`uiautomator` 可读取 Compose 登录页；重新生成的 APK Manifest 已用 `aapt dump permissions` 确认声明 `android.permission.INTERNET` 与 `android.permission.ACCESS_NETWORK_STATE`；未填写账号、未访问或提交物理在线数据。

## 4. M13 后续验收顺序

1. 安装 `1.7.4-KMP-DEV`，验证入口、课程/作业、统一日期、首页物理截止和自动同步开关。
2. 在真实 Windows 触摸模式验证整个应用的纵向列表和横向筛选栏拖动。
3. 在 Android、iOS、macOS 环境验证 CAS 会话恢复、原生详情、文件选择器和网页备用入口。
4. 选择普通 Moodle 文件型作业，先核对成绩/评语/附件，再由用户明确确认一次小文件上传；不要用 Unity 实验上传代替普通作业验收。
5. 如需继续浏览器取证，使用管理员策略允许的控制链路；不得复制 Cookie、密码或令牌，也不得绕过浏览器安全策略。
