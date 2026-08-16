# BJTUselfService KMP 迁移工作记忆

> 最后更新：2026-08-16
> 当前分支：`main`（跟踪 `mine/main` → `JasonZhang1225/BJTUselfService-KMP-Refreshed`，HEAD `84bf479`）
> 阶段状态：**`1.7.3-KMP` pre-release 四端包已齐。** tag `v1.7.3-KMP`，Windows 安装器由远端上传；本机补传 Android / iOS / macOS。
> 分支创建点：`9d8da18`；上游对照基线：`v1.7.0@419313d`；KMP 自身基线：**`v1.7.3-KMP`（git tag，`84bf479`）**
> 完整历史与已归档的验收细节：见 `history_full.md`（按里程碑归档，只读）
> 本文件是实时工作记忆，不是只追加日志：任务开始读、结束改，只保留当前接续工作需要的状态。

## 1. 本阶段已做到（≤10 行）

- **第一阶段收口 + `1.7.1-KMP`/`1.7.2-KMP` + M12 + `1.7.2-KMP-A` + M14 Windows**：细节见 `history_full.md`。
- **2026-08-16 发布 `1.7.3-KMP` 四端包**：用户在 Windows 创建 pre-release 并上传 EXE；本机补传 Android debug APK、iOS 未签名 IPA、macOS DMG。发布页：https://github.com/JasonZhang1225/BJTUselfService-KMP-Refreshed/releases/tag/v1.7.3-KMP
- **macOS 显示名/DMG 图标**：不是 Applications 缓存。jpackage `packageName` 仍是英文 `BJTUselfServiceKMP`，所以 DMG 里的 `.app`、卷名是英文，卷图标是 Java Duke。已加 `FinalizeMacDmg`：卷名/应用文件名改成「交大自由行 KMP」，卷图标换成吉祥物 icns。Release 上的 DMG 已覆盖。
- **Windows 桌面快捷方式仍是英文**：安装后桌面是 `BJTUselfService…`，打开后标题栏/任务栏是「交大自由行 KMP」。窗口标题来自 `Window(title)`，快捷方式/开始菜单来自 `packageName = BJTUselfServiceKMP`。已把 Windows `packageName` 改成「交大自由行 KMP」。本机打不了 Windows 包，需在 Windows 上重打 `packageExe` 并覆盖 Release。安装前先卸载旧的英文名副本。

## 2. 当前痛点（≤8 条）

- **构建环境**：compose 1.12.0-beta03 要求 compileSdk 37，本机 SDK 36.1 已实验绕过。
- **iOS 包未签名**：需侧载自签；合法签名 / Keychain 往返仍缺 Developer Team。
- **验证码发布级准确率仍待扩样**；课件深层文件夹/信息流变化仍缺自然样本。
- 官方 1.7.0 / KMP PyTorch 2.1 在 API 37.1 有 16 KB page-size 提示。

## 3. 接下来 1～3 个阶段

1. **M13 物理在线接入**（需内网调研）。规划见 `docs/migration/m13-phyvlab-integration-plan.md`。
2. **自然样本补证与公版门禁**：课件文件夹、作业上传、信息流变化、验证码扩样。

## 维护规则

- 每次开始目标模式任务时先读本文件；结束前必须再次更新。
- 已完成事项压缩为一行保留在“本阶段已做到”；里程碑真正完成时，把细节归档进 `history_full.md` 并从本文件删除。
- 痛点解除即删除或改写，不保留已经失效的阻塞描述。
- 近期计划只保留接下来 1～3 个可执行阶段；远期内容留在 `goal.md`。
- 事实、命令结果和验证边界要具体；不能把计划写成已完成。
- 分支、基线、最新 Release 或工作区状态发生变化时，更新文件顶部摘要。
- 不在本文件写入账号、密码、Cookie、令牌、真实验证码会话或其他敏感信息。
