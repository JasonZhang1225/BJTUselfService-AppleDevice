# BJTUselfService KMP 迁移工作记忆

> 最后更新：2026-08-16
> 当前分支：`main`（跟踪 `origin` → `JasonZhang1225/BJTUselfService-KMP-Refreshed`，已推送）
> 阶段状态：**1.7.3-KMP 已发布。** 已撤回 `perUserInstall` 那条错误提交，与系统级安装 / MSI 前台 UAC / 卸载清凭据 / 作业重试收成一条。下一步为 M13。
> 分支创建点：`9d8da18`；上游对照基线：`v1.7.0@419313d`；KMP 自身基线：**`v1.7.3-KMP`（git tag，`84bf479`）**
> 完整历史与已归档的验收细节：见 `history_full.md`（按里程碑归档，只读）
> 本文件是实时工作记忆，不是只追加日志：任务开始读、结束改，只保留当前接续工作需要的状态。

## 1. 本阶段已做到（≤10 行）

- **第一阶段收口 + `1.7.1-KMP`/`1.7.2-KMP` + M12 + `1.7.2-KMP-A` + M14 Windows**：细节见 `history_full.md`。
- **2026-08-16 发布 `1.7.3-KMP` 四端包**：git tag `v1.7.3-KMP`（`84bf479`）；Android debug APK / iOS 未签名 IPA / macOS DMG 本机构建上传，Windows EXE 由 Windows 端构建上传。发布页：https://github.com/JasonZhang1225/BJTUselfService-KMP-Refreshed/releases/tag/v1.7.3-KMP
- **2026-08-16 macOS/Windows 显示名修复**（提交 `1eb9939`）：macOS `FinalizeMacDmg` 把 `.app`/卷名改为「交大自由行 KMP」、卷图标换吉祥物；Windows `packageName` 改中文，桌面快捷方式/开始菜单与窗口标题一致。
- **2026-08-16 Windows 安装器改回系统级**：默认 `C:\Program Files\交大自由行 KMP`，推荐 MSI 前台 UAC。用户实测：卸 MSI 后再装仍记住密码。本机 elevated 卸载日志：`CleanupUserData` 跑了，但 immediate `WixQuietExec` 报 `0x80070057 Failed to get command line data` 被忽略；`Program Files` 已删，`%LOCALAPPDATA%\BJTUselfServiceKMP` 与 `HKCU\...\JavaSoft\Prefs\team\bjtuss\bjtuselfservice` 仍在。已改为 deferred + CustomActionData，卸载时清缓存和凭据。不在新安装启动时扫残留。

## 2. 当前痛点（≤8 条）

- **Windows 安装器品牌化受限**：jpackage 安装向导 UI（横幅、右上角图标、进度框）无参数可定制；安装完成后的 EXE/快捷方式/窗口/任务栏图标已是品牌 logo。若用户要完全品牌化安装向导，需引入 Inno Setup 等替代打包管线（未授权、未规划）。
- **构建环境**：compose 1.12.0-beta03 要求 compileSdk 37，本机 SDK 36.1 已实验绕过；本机无 Android SDK（Android/iOS 目标无法在本机编译）。
- **打包 JDK**：JBR 无 jlink/jpackage，需完整 JDK（本机 Microsoft JDK 21 `C:/Users/zjg/jdk21/jdk-21.0.8+9`，`WINDOWS_PACKAGE_JAVA_HOME` 可覆盖）。
- **Windows 卸载清凭据待复测**：新 MSI 已重建。当前机器已卸掉旧包，但上次失败的清理留下了 AppData 缓存和注册表凭据。需装新 MSI 后再卸一次，确认这两处被删。
- **作业静默入场偶发失败**：用户清缓存重登后已能刷出；代码已加重试与首页文案，实机是否不再误报待装新包后看。
- **iOS 包未签名**：需侧载自签；合法签名 / Keychain 往返仍缺 Developer Team。
- **验证码发布级准确率仍待扩样**；课件深层文件夹/信息流变化仍缺自然样本。
- 官方 1.7.0 / KMP PyTorch 2.1 在 API 37.1 有 16 KB page-size 提示。

## 3. 接下来 1～3 个阶段

1. **用户用新 MSI 装完再卸**：确认卸载后不再记住密码、AppData 缓存目录消失。
2. **M13 物理在线接入**（需内网调研）。规划见 `docs/migration/m13-phyvlab-integration-plan.md`。
3. **自然样本补证与公版门禁**：课件文件夹、作业上传、信息流变化、验证码扩样。

## 维护规则

- 每次开始目标模式任务时先读本文件；结束前必须再次更新。
- 已完成事项压缩为一行保留在“本阶段已做到”；里程碑真正完成时，把细节归档进 `history_full.md` 并从本文件删除。
- 痛点解除即删除或改写，不保留已经失效的阻塞描述。
- 近期计划只保留接下来 1～3 个可执行阶段；远期内容留在 `goal.md`。
- 事实、命令结果和验证边界要具体；不能把计划写成已完成。
- 分支、基线、最新 Release 或工作区状态发生变化时，更新文件顶部摘要。
- 不在本文件写入账号、密码、Cookie、令牌、真实验证码会话或其他敏感信息。
