# BJTUselfService KMP 迁移工作记忆

> 最后更新：2026-08-29
> 当前分支：`maildev`（从 `main@c181dbf` 派生；本轮未改写或推送 `main`；含校历入口替换、M13/M15/M16 相关改动、PR #3 ABI 拆分、冻结 Android CI Maven 兜底和 KMP Android 共用签名配置）
> 阶段状态：**173B 基座已同步；M13 代码层初步开发完成。校历入口已移除失效下载接口并改为公众号文章。M15 邮箱已完成 Coremail 只读文件夹/列表/详情扩展，宽屏三栏与紧凑端二级阅读 UI 按 Apple Mail 方向重做；本轮修正实际文件夹 FID 和紧凑端重复返回入口。Mac 已有真实登录态列表/详情证据，写信/删除/附件下载和真实登录后的 iOS 邮箱验收未完成。M16 VPN 仅保留调研，当前不开发。PR #3 已合入。Android 已改为本地/CI 共用上传签名（证书 SHA-256 `5d0dabc3…c773`）。`v1.7.4-KMP` Release Android 包为仅 `arm64-v8a`、无 debug 文件名（142,270,345 字节）。实体 iPhone 仍缺 provisioning profile。当前版本为 `1.7.4-KMP`。**
> 分支创建点：`9d8da18`；上游对照基线：`v1.7.0@419313d`；KMP 自身基线：**`v1.7.3-KMP-B` (`a342615`)**；当前发布 **`v1.7.4-KMP`**；上一发布 `v1.7.3-KMP-B@a342615` 保留。
> 完整历史与已归档的验收细节：见 `history_full.md`（按里程碑归档，只读）
> 本文件是实时工作记忆，不是只追加日志：任务开始读、结束改，只保留当前接续工作需要的状态。

## 1. 本阶段已做到（≤10 行）

- **第一阶段收口 + `1.7.1-KMP`/`1.7.2-KMP` + M12 + `1.7.2-KMP-A` + M14 Windows**：细节见 `history_full.md`。
- **2026-08-17 `1.7.3-KMP-B` 基座**：教学周改 `getTimeList`；作业容错对齐 1.7.0；CI、Windows MSI ASCII 修复、macOS JDK/iOS 任务拆分均已合入 `a342615`。2026-08-29 实测学期末 `getTimeList` 与 `room_view` 都可能误给第 1 周，现用当前学期校历按日期校正并把校正值写回缓存，教学周范围统一为 1–30。
- **Windows 移植（M14）**：DPAPI 凭据保险库、%LOCALAPPDATA% 缓存、AWT 文件网关、系统浏览器、Ktor CIO、GB18030、验证码推理、品牌图标与打包链路已实现；细节见 `history_full.md`。
- **M13 物理在线首版**：CAS/OAuth2 白名单握手、课程/作业/首页安排、按学号隔离缓存、失败提示、窄屏原生详情、自动同步“仅校园网”；Mac 真实登录态可读 3 门课、32 个活动。真实上传未执行。调研与结果见 `docs/migration/m13-phyvlab-integration-*.md`。
- **2026-08-29 iOS/macOS 最新产物**：`BJTUSelfService-KMP-1.7.4-KMP-iOS-unsigned.ipa` 与 `BJTUselfServiceKMP-1.7.4.dmg` 已放入 `/Users/zjg/Downloads`；iOS Bundle ID `team.bjtuss.bjtuselfservice.kmp.ios`、版本 `1.7.4-KMP`、Build `15`，IPA 包内无 `_CodeSignature`，SHA-256 `6a9b37300270b83680e935018b0ecab88b08dc8817b4f22625252b14603a3cd3`；macOS DMG SHA-256 `239ccc9e82d360c31a15f6b49939f94391822b665bda8c9257f8de7055d5650c`。最新 iOS Simulator Debug 已安装并启动到登录页；实体机缺 provisioning profile。
- **2026-08-28 Android 共用签名**：本机 `~/.android/bjtu-kmp-upload.keystore` 与当前 Release APK 同一证书（SHA-256 `5d0dabc3…c773`）。`:androidApp` debug/release 都用这把钥匙；GitHub Secrets 已写入 `BJTU_ANDROID_KEYSTORE_BASE64` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`。密钥文件不进 Git。`v1.7.4-KMP` APK 为 `BJTUSelfService-KMP-1.7.4-KMP-arm64-v8a.apk`（142,270,345 字节）。旧 Actions 包需先卸载再装。
- **2026-08-28 冻结 Android CI**：PR #3 合入后 `Build Debug APK`（run 33162675067）因 `maven.aliyun.com` 502 失败。已在 Actions 上改写 Maven 源为 Google/Maven Central（不改冻结 `settings.gradle.kts`），纯 KMP/文档提交不再触发这份旧打包。复跑 [33165162229](https://github.com/JasonZhang1225/BJTUselfService-KMP-Refreshed/actions/runs/33165162229) 成功（4m49s，已上传 APK）。
- **2026-08-27 同步失败提示收口**：首页失败胶囊同时弹出模块清单并重试；物理在线失败且有缓存时顶栏为“同步失败·正显示缓存”，横幅改为校园网说明，不再显示原始 `network` 诊断。
- **2026-08-27 打包与图标**：`1.7.4-KMP` 四端产物曾由 CI 上传；macOS DMG 文件图标已换成圆角透明留白版本。开发版 `1.7.4-KMP-DEV` 仅作 Windows 验收副本，当前对外版本是 `1.7.4-KMP`。
- **2026-08-28 校历入口替换**：KMP “更多”中的校历改为打开指定公众号文章；移除 bksy 校历下载数据源、解析器、下载状态与相关测试，冻结根 Android `app/` 未修改。相关共享桌面测试、iOS Simulator 测试通过。
- **2026-08-28/29 M15/M16 首轮调研与 M15 切片**：直接 Chrome DevTools MCP 确认 Coremail XT5 传统打包前端、收件箱/详情 JSON 请求，以及 `vpn` 部分代理、`libvpn` 全代理的官方 OTP 登录入口；物理在线公开 HTTPS 首页可达，但未输入凭据、安装 VPN 或改系统设置。M15 已加入原始 JSON HTTP 传输、Coremail 列表/详情解析与只读响应式邮箱页；本轮将邮箱 UI 重做为宽屏文件夹—列表—阅读三栏、紧凑端列表→详情二级页，并补齐加载/空/失败态和自绘图标；修正三栏阈值按邮箱内容区而非外层窗口判断。Mac 真实登录态的列表→详情回归通过；最新 iOS Simulator 仅验证登录首屏。脱敏单测、桌面/iOS Simulator 单测和 Android/桌面编译通过。
- **2026-08-29 macOS 启动链路补丁**：确认登录页原生凭据输入框的同步 JNA 创建会造成 AWT EventQueue 与 AppKit 主线程互等；改为后台创建、完成后异步挂载，源码构建可正常显示单个“交大自由行 KMP”登录窗口。验证结束后已按精确路径回收源码实例，未关闭用户安装版。
- **2026-08-29 多窗口验证链路已定位**：`desktopApp:run` 的 Gradle 前台进程结束后，源码子 JVM 可能继续运行；连续启动会与已打开的 `/Applications` 安装版形成多个同名窗口。`Main.kt` 只有一个 `Window`，生命周期回调不创建新窗口。已停止本轮源码实例，规则已写入 `CLAUDE.md`；后续每次只启动一个源码实例并按精确路径回收。
- **2026-08-29 安装版/源码版对照**：`/Applications/交大自由行 KMP.app` 已用包含 M15 三栏阈值修复的最新 `BJTUselfServiceKMP.app` 逐文件覆盖，Info 版本为 `1.7.4`/Build `15`，签名校验通过；当前安装版已重新启动到登录页。验证时仍必须只保留一个明确路径的实例，不能用相同 Bundle ID 区分源码版与安装版。
- **2026-08-29 M15 邮箱文件夹扩展**：按 Coremail 实际树节点 FID 修正收件箱 `1`、待办 `-5`、草稿 `2`、已发送 `3`，并加入已删除 `4`、垃圾邮件 `5`、病毒邮件 `6` 的只读列表；紧凑端内嵌详情隐藏邮箱页顶栏返回，只保留“返回邮件列表”。桌面/iOS Simulator 测试通过，真实登录后的新侧栏尚待用户点验。
- **2026-08-29 `maildev` 提交前状态**：在 `main@c181dbf` 基础上创建 `maildev`，本轮不修改 `main`；补充发件箱按收件人显示、特殊文件夹分组和会话失效重置，新增导航/FID/解析回归测试。`:shared:desktopTest` 与 `:shared:iosSimulatorArm64Test` 均通过，冻结根 Android 文件无差异；最终一次 Xcode Simulator 构建由用户中断，未把中断写成通过，当前没有残留构建进程。

## 2. 当前痛点（≤8 条）

- **Windows 安装器品牌化受限**：jpackage 安装向导 UI（横幅、右上角图标、进度框）无参数可定制；安装完成后的 EXE/快捷方式/窗口/任务栏图标已是品牌 logo。若用户要完全品牌化安装向导，需引入 Inno Setup 等替代打包管线（未授权、未规划）。
- **构建环境**：compose 1.12.0-beta03 要求 compileSdk 37，本机 SDK 36.1 已实验绕过；Windows 侧 Android SDK/AVD/APK 门禁已通过。Mac 侧 Xcode 27.0、iOS Simulator/iphoneos arm64 构建和 macOS arm64 分发构建均通过；iOS 模拟器已启动应用，实体机和登录后移动端仍待设备/签名条件。
- **打包 JDK**：JBR 无 jlink/jpackage，需完整 JDK（本机 Microsoft JDK 21 `C:/Users/zjg/jdk21/jdk-21.0.8+9`，`WINDOWS_PACKAGE_JAVA_HOME` 可覆盖）。
- **Windows MSI**：曾 `light.exe 311`（中文 description 进 MSI 字符串表）。KMP Android 共用上传签名已写入 GitHub Secrets（`BJTU_ANDROID_KEYSTORE_BASE64` 等四项），与本机 `~/.android/bjtu-kmp-upload.keystore` 同一把钥匙。
- **Windows 卸载清凭据待复测**：请装带卸载清理的 MSI 后再卸，确认 AppData 缓存和注册表凭据被删。
- **iOS 真机签名/连接**：generic iPhoneOS unsigned 构建通过，但当前 Bundle ID 没有匹配 provisioning profile；实体 iPhone 在 `devicectl` 中为 `unavailable`，合法签名、安装、Keychain 往返仍未取得证据。
- **验证码发布级准确率仍待扩样**；课件深层变化仍缺自然样本；官方 1.7.0 / KMP PyTorch 2.1 在 API 37.1 有 16 KB page-size 提示。
- **M13 现场解析差异与可选编辑页**：详情页状态标签是 `作业状态`（未交为“尚未批改”），不是 fixture 的 `提交状态`；filemanager 的 context/client/repo 在脚本 JSON 里，不在 DOM `data-*`。Mac 真实登录态课程/活动和主详情读取成功；已提交作业没有可用 filemanager，辅助 `action=editsubmission` 页返回 404 时按可选能力处理，不再覆盖主详情。真实上传仍未执行；Android/iOS 登录后 M13、REST token、Unity 外链仍待后续。arm64-only APK 不能装 x86_64 模拟器。
- **M15 邮箱功能边界**：只读文件夹/列表/详情、分页加载与 Apple Mail 方向的响应式 UI 已完成首轮；紧凑端详情已接入 `MAILBOX_DETAIL` 平台原生二级路由，并修正内嵌详情与邮箱顶栏重复返回。本地正文缓存、写信、删除、移动、附件下载和真实登录后的 iOS 邮箱验收仍待单独切片，不能以当前构建通过代替真实邮箱验收。

## 3. 接下来 1～3 个阶段

1. **M15 邮箱只读验收与扩展**：在取得可用真实会话后复验宽屏三栏、紧凑端二级阅读页、动态字体与真实邮件数据；分页、本地缓存及写信/删除等动作仍单独评审。
2. **M13 Apple 端补验**：实体 iPhone 取得合法 provisioning profile 后安装；Android/iOS 登录后复测详情、会话续期和网页备用入口，真实上传仍需用户明确确认。
3. **M16 官方 VPN 验证**：暂缓，待用户明确恢复后由用户自行安装官方 Mac/iOS 客户端并用 OTP 建立连接，再分层验证物理在线登录与只读业务；不做代理或访问控制绕过。

## 维护规则

- 每次开始目标模式任务时先读本文件；结束前必须再次更新。
- 已完成事项压缩为一行保留在“本阶段已做到”；里程碑真正完成时，把细节归档进 `history_full.md` 并从本文件删除。
- 痛点解除即删除或改写，不保留已经失效的阻塞描述。
- 近期计划只保留接下来 1～3 个可执行阶段；远期内容留在 `goal.md`。
- 事实、命令结果和验证边界要具体；不能把计划写成已完成。
- 分支、基线、最新 Release 或工作区状态发生变化时，更新文件顶部摘要。
- 不在本文件写入账号、密码、Cookie、令牌、真实验证码会话或其他敏感信息。
