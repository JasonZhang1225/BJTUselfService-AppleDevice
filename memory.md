# BJTUselfService KMP 迁移工作记忆

> 最后更新：2026-08-17
> 当前分支：`main`（跟踪 `origin` → `JasonZhang1225/BJTUselfService-KMP-Refreshed`，已推送）
> 阶段状态：**`1.7.3-KMP-B` 已推送，等 Actions 出包。** git tag `v1.7.3-KMP-B`（`7d85055`）。
> 分支创建点：`9d8da18`；上游对照基线：`v1.7.0@419313d`；KMP 自身基线：**`v1.7.3-KMP-B`（git tag，`7d85055`）**；上一发布 `v1.7.3-KMP-A@6c69c85` 保留。
> 完整历史与已归档的验收细节：见 `history_full.md`（按里程碑归档，只读）
> 本文件是实时工作记忆，不是只追加日志：任务开始读、结束改，只保留当前接续工作需要的状态。

## 1. 本阶段已做到（≤10 行）

- **第一阶段收口 + `1.7.1-KMP`/`1.7.2-KMP` + M12 + `1.7.2-KMP-A` + M14 Windows**：细节见 `history_full.md`。
- **2026-08-16 发布 `1.7.3-KMP-A`**：git tag `v1.7.3-KMP-A`（`6c69c85`）。发布页：https://github.com/JasonZhang1225/BJTUselfService-KMP-Refreshed/releases/tag/v1.7.3-KMP-A 。附件现为 APK / 未签名 IPA / DMG / MSI。Release Notes 安装表已从误留的 `1.7.2-KMP-A` 文件名改为当前 `1.7.3-KMP-A` 名称。
- **2026-08-16 macOS/Windows 显示名修复**（提交 `1eb9939`）：macOS `FinalizeMacDmg` 把 `.app`/卷名改为「交大自由行 KMP」、卷图标换吉祥物；Windows `packageName` 改中文，桌面快捷方式/开始菜单与窗口标题一致。
- **2026-08-17 `1.7.3-KMP-B`**：教学周改 `getTimeList`；作业容错对齐 1.7.0。`CURRENT_VERSION` / Android `versionName` / iOS 短版本 = `1.7.3-KMP-B`；`versionCode` / `CFBundleVersion` / `packageBuildVersion` = 14。新增 `.github/workflows/kmp-package.yml`（Android debug APK / Windows MSI / macOS DMG / 未签名 IPA，tag 后发 pre-release）。旧 `release.yml` 忽略含 `KMP` 的标签。

## 2. 当前痛点（≤8 条）

- **Windows 安装器品牌化受限**：jpackage 安装向导 UI（横幅、右上角图标、进度框）无参数可定制；安装完成后的 EXE/快捷方式/窗口/任务栏图标已是品牌 logo。若用户要完全品牌化安装向导，需引入 Inno Setup 等替代打包管线（未授权、未规划）。
- **构建环境**：compose 1.12.0-beta03 要求 compileSdk 37，本机 SDK 36.1 已实验绕过；本机无 Android SDK（Android/iOS 目标无法在本机编译）。
- **打包 JDK**：JBR 无 jlink/jpackage，需完整 JDK（本机 Microsoft JDK 21 `C:/Users/zjg/jdk21/jdk-21.0.8+9`，`WINDOWS_PACKAGE_JAVA_HOME` 可覆盖）。
- **1.7.3-KMP-B 待 Actions 出包并实机确认**：装新 MSI/APK 后看教学周是否约第 25 周、作业刷新是否还有「结构变化」。Windows 覆盖须 `msiexec REINSTALL=ALL`，不要先卸载。
- **Windows 卸载清凭据待复测**：请装带卸载清理的 MSI 后再卸，确认 AppData 缓存和注册表凭据被删。
- **iOS 包未签名**：需侧载自签；合法签名 / Keychain 往返仍缺 Developer Team。
- **验证码发布级准确率仍待扩样**；课件深层文件夹/信息流变化仍缺自然样本。
- 官方 1.7.0 / KMP PyTorch 2.1 在 API 37.1 有 16 KB page-size 提示。

## 3. 接下来 1～3 个阶段

1. **等 GitHub Actions `KMP package` 出包**：确认 APK/MSI/DMG/IPA 上传到 `v1.7.3-KMP-B` Release，再实机验证教学周与作业。
2. **M13 物理在线接入**（需内网调研）。规划见 `docs/migration/m13-phyvlab-integration-plan.md`。
3. **Windows 卸载清凭据**：装含 CleanupUserData 的 MSI 后再卸，确认不再记住密码。

## 维护规则

- 每次开始目标模式任务时先读本文件；结束前必须再次更新。
- 已完成事项压缩为一行保留在“本阶段已做到”；里程碑真正完成时，把细节归档进 `history_full.md` 并从本文件删除。
- 痛点解除即删除或改写，不保留已经失效的阻塞描述。
- 近期计划只保留接下来 1～3 个可执行阶段；远期内容留在 `goal.md`。
- 事实、命令结果和验证边界要具体；不能把计划写成已完成。
- 分支、基线、最新 Release 或工作区状态发生变化时，更新文件顶部摘要。
- 不在本文件写入账号、密码、Cookie、令牌、真实验证码会话或其他敏感信息。
