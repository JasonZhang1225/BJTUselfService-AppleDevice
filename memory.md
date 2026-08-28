# BJTUselfService KMP 迁移工作记忆

> 最后更新：2026-08-28
> 当前分支：`main`（已与 `mine/main` 对齐，含 PR #3 ABI 拆分、冻结 Android CI Maven 兜底、KMP Android 共用签名配置）
> 阶段状态：**173B 基座已同步；M13 代码层初步开发完成。PR #3 已合入。Android 已改为本地/CI 共用上传签名（证书 SHA-256 `5d0dabc3…c773`）。`v1.7.4-KMP` Release Android 包为仅 `arm64-v8a`、无 debug 文件名（142,270,345 字节）。GitHub Actions 密钥需在本机写入 Secrets 后才会用同一把钥匙。Mac 已完成开发包构建与真实登录态物理在线；实体 iPhone 仍缺 provisioning profile。当前版本为 `1.7.4-KMP`。**
> 分支创建点：`9d8da18`；上游对照基线：`v1.7.0@419313d`；KMP 自身基线：**`v1.7.3-KMP-B` (`a342615`)**；当前发布 **`v1.7.4-KMP`**；上一发布 `v1.7.3-KMP-B@a342615` 保留。
> 完整历史与已归档的验收细节：见 `history_full.md`（按里程碑归档，只读）
> 本文件是实时工作记忆，不是只追加日志：任务开始读、结束改，只保留当前接续工作需要的状态。

## 1. 本阶段已做到（≤10 行）

- **第一阶段收口 + `1.7.1-KMP`/`1.7.2-KMP` + M12 + `1.7.2-KMP-A` + M14 Windows**：细节见 `history_full.md`。
- **2026-08-17 `1.7.3-KMP-B` 基座**：教学周改 `getTimeList`；作业容错对齐 1.7.0；CI、Windows MSI ASCII 修复、macOS JDK/iOS 任务拆分均已合入 `a342615`。
- **Windows 移植（M14）**：DPAPI 凭据保险库、%LOCALAPPDATA% 缓存、AWT 文件网关、系统浏览器、Ktor CIO、GB18030、验证码推理、品牌图标与打包链路已实现；细节见 `history_full.md`。
- **M13 物理在线首版**：CAS/OAuth2 白名单握手、课程/作业/首页安排、按学号隔离缓存、失败提示、窄屏原生详情、自动同步“仅校园网”；Mac 真实登录态可读 3 门课、32 个活动。真实上传未执行。调研与结果见 `docs/migration/m13-phyvlab-integration-*.md`。
- **2026-08-28 iOS unsigned IPA**：`BJTUSelfService-KMP-1.7.4-KMP-iOS-unsigned.ipa` 已放入 `/Users/zjg/Downloads`；Bundle ID `team.bjtuss.bjtuselfservice.kmp.ios`、版本 `1.7.4-KMP`、Build `15`，包内无 `_CodeSignature`。SHA-256 `796d977f659de7732577e5729c035660a96d00f71ba9a36a611b7b7b2a1776ca`。iOS Simulator Debug 已启动到版本正确的登录页；实体机缺 provisioning profile。
- **2026-08-28 Android 共用签名**：本机 `~/.android/bjtu-kmp-upload.keystore` 与当前 Release APK 同一证书（SHA-256 `5d0dabc3…c773`）。`:androidApp` debug/release 都用这把钥匙；CI 从 `BJTU_ANDROID_KEYSTORE_BASE64` 等 Secrets 恢复。密钥文件未进 Git。`v1.7.4-KMP` APK 仍为 `BJTUSelfService-KMP-1.7.4-KMP-arm64-v8a.apk`（142,270,345 字节，SHA-256 `33eb697e042eed46040c296d852024fd9ef8dc1c5ed55c794ce106864a469c7f`）。旧 Actions 包需先卸载再装。
- **2026-08-28 冻结 Android CI**：PR #3 合入后 `Build Debug APK`（run 33162675067）因 `maven.aliyun.com` 502 失败。已在 Actions 上改写 Maven 源为 Google/Maven Central（不改冻结 `settings.gradle.kts`），纯 KMP/文档提交不再触发这份旧打包。复跑 [33165162229](https://github.com/JasonZhang1225/BJTUselfService-KMP-Refreshed/actions/runs/33165162229) 成功（4m49s，已上传 APK）。
- **2026-08-27 同步失败提示收口**：首页失败胶囊同时弹出模块清单并重试；物理在线失败且有缓存时顶栏为“同步失败·正显示缓存”，横幅改为校园网说明，不再显示原始 `network` 诊断。
- **2026-08-27 打包与图标**：`1.7.4-KMP` 四端产物曾由 CI 上传；macOS DMG 文件图标已换成圆角透明留白版本。开发版 `1.7.4-KMP-DEV` 仅作 Windows 验收副本，当前对外版本是 `1.7.4-KMP`。

## 2. 当前痛点（≤8 条）

- **Windows 安装器品牌化受限**：jpackage 安装向导 UI（横幅、右上角图标、进度框）无参数可定制；安装完成后的 EXE/快捷方式/窗口/任务栏图标已是品牌 logo。若用户要完全品牌化安装向导，需引入 Inno Setup 等替代打包管线（未授权、未规划）。
- **构建环境**：compose 1.12.0-beta03 要求 compileSdk 37，本机 SDK 36.1 已实验绕过；Windows 侧 Android SDK/AVD/APK 门禁已通过。Mac 侧 Xcode 27.0、iOS Simulator/iphoneos arm64 构建和 macOS arm64 分发构建均通过；iOS 模拟器已启动应用，实体机和登录后移动端仍待设备/签名条件。
- **打包 JDK**：JBR 无 jlink/jpackage，需完整 JDK（本机 Microsoft JDK 21 `C:/Users/zjg/jdk21/jdk-21.0.8+9`，`WINDOWS_PACKAGE_JAVA_HOME` 可覆盖）。
- **KMP Android 签名 Secrets 待写入**：Gradle/CI 已接共用钥匙，但本会话不能把 keystore 传到 GitHub Secrets。需在本机执行 `gh secret set` 写入 `BJTU_ANDROID_KEYSTORE_BASE64` / `BJTU_ANDROID_STORE_PASSWORD` / `BJTU_ANDROID_KEY_ALIAS` / `BJTU_ANDROID_KEY_PASSWORD` 后，Actions 才会签同一张证书。Windows 曾 `light.exe 311`。
- **Windows 卸载清凭据待复测**：请装带卸载清理的 MSI 后再卸，确认 AppData 缓存和注册表凭据被删。
- **iOS 真机签名/连接**：generic iPhoneOS unsigned 构建通过，但当前 Bundle ID 没有匹配 provisioning profile；实体 iPhone 在 `devicectl` 中为 `unavailable`，合法签名、安装、Keychain 往返仍未取得证据。
- **验证码发布级准确率仍待扩样**；课件深层变化仍缺自然样本；官方 1.7.0 / KMP PyTorch 2.1 在 API 37.1 有 16 KB page-size 提示。
- **M13 现场解析差异与可选编辑页**：详情页状态标签是 `作业状态`（未交为“尚未批改”），不是 fixture 的 `提交状态`；filemanager 的 context/client/repo 在脚本 JSON 里，不在 DOM `data-*`。Mac 真实登录态课程/活动和主详情读取成功；已提交作业没有可用 filemanager，辅助 `action=editsubmission` 页返回 404 时按可选能力处理，不再覆盖主详情。真实上传仍未执行；Android/iOS 登录后 M13、REST token、Unity 外链仍待后续。arm64-only APK 不能装 x86_64 模拟器。

## 3. 接下来 1～3 个阶段

1. **M13 跨平台详情复测**：Mac 主详情已修复；在 Android/iOS 登录条件具备后复测详情、会话续期和网页备用入口，保留不含敏感值的底层异常诊断。
2. **实体 iPhone 验证**：连接并解锁设备，准备匹配 Bundle ID 的合法 provisioning profile 后安装；再由用户明确确认一次小文件上传。
3. **Windows 卸载清凭据**：装含 CleanupUserData 的 MSI 后再卸，确认 AppData 缓存和注册表凭据被删。

## 维护规则

- 每次开始目标模式任务时先读本文件；结束前必须再次更新。
- 已完成事项压缩为一行保留在“本阶段已做到”；里程碑真正完成时，把细节归档进 `history_full.md` 并从本文件删除。
- 痛点解除即删除或改写，不保留已经失效的阻塞描述。
- 近期计划只保留接下来 1～3 个可执行阶段；远期内容留在 `goal.md`。
- 事实、命令结果和验证边界要具体；不能把计划写成已完成。
- 分支、基线、最新 Release 或工作区状态发生变化时，更新文件顶部摘要。
- 不在本文件写入账号、密码、Cookie、令牌、真实验证码会话或其他敏感信息。
