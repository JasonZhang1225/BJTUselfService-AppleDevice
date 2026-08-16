# BJTUselfService KMP 迁移工作记忆

> 最后更新：2026-08-16
> 当前分支：`windows-dev`（Windows 移植完成，提交 `642a5c8` 未推送；发布暂停等用户验证其他平台）
> 阶段状态：**Windows 桌面端移植（M14）完成，待发布。** 用户已人工确认 Windows 端基本通过；用户暂停发布以先验证 Android/iOS/macOS 可用性；代码层面已核查其他平台无功能影响（见 memory 痛点/验证节）。
> 分支创建点：`9d8da18`；上游对照基线：`v1.7.0@419313d`；KMP 自身基线：**`v1.7.2-KMP-A`（git tag，`b85747f`）**（1.7.3-KMP 版本号已改但发布未完成，暂不更新基线）
> 完整历史与已归档的验收细节：见 `history_full.md`（按里程碑归档，只读）
> 本文件是实时工作记忆，不是只追加日志：任务开始读、结束改，只保留当前接续工作需要的状态。

## 1. 本阶段已做到（≤10 行）

- **第一阶段收口 + `1.7.1-KMP`/`1.7.2-KMP` + M12 + `1.7.2-KMP-A`**：细节见 `history_full.md`。
- **2026-08-16 Windows 移植（windows-dev 分支，提交 `642a5c8` 未推送）**：
  - 新增 `multiplatform/windowsApp` 模块（JVM target "windows"），依赖 `:shared` desktop 产物复用全部 commonMain 业务与 UI；不修改冻结 Android 工程。
  - `shared/src/desktopMain/Platform.desktop.kt` 按 OS 返回 displayName（Windows/macOS），UI 分类仍复用 `PlatformFamily.MacOS` 桌面分支。
  - Windows 平台实现：DPAPI 凭据保险库（JNA Crypt32，测试覆盖往返/清除/篡改防护）、`%LOCALAPPDATA%` 缓存、AWT FileDialog 文件网关、系统浏览器网页引导、Ktor CIO、GB18030。
  - **验证码自动识别**：`WindowsTorchCaptchaRecognizer` 用 DJL PyTorch 引擎（`ai.djl.pytorch` 0.33.0 + native-cpu 2.5.1 win-x86_64）加载原版 `BJTUCaptcha.pt`；与 Python torch 参考 logits **max abs diff ≤0.000012、argmax 三图全部一致**（浮点精度内）；同进程连续/并发推理测试通过。
  - **窗口/EXE 图标**：品牌 ICO（16-256 多尺寸，不透明白底圆角）嵌入 EXE + 窗口标题栏 PNG 图标；**深色标题栏**：Windows DWM + macOS appearance 属性。
  - Windows EXE 打包成功（`packageExe`，完整 JDK 21 含 jpackage）；1.7.3 安装器 `BJTUselfServiceKMP-1.7.3.exe` 已生成。
  - 规划文档：`docs/migration/windows-port-plan.md`；`history_full.md` 已归档 M14 节。
  - 版本标识已改 1.7.3-KMP（AppUpdateChecker/androidApp/iosApp/desktopApp/windowsApp），随 `642a5c8` 提交。

## 2. 当前痛点（≤8 条）

- **发布暂停中**：用户要求先验证 Android/iOS/macOS 可用性；`642a5c8` 未推送、未合并 main、未发布。
- **构建环境**：compose 1.12.0-beta03 要求 compileSdk 37，本机 SDK 36.1 已实验绕过；本机无 Android SDK（Android/iOS 目标无法在本机编译）。
- **打包 JDK**：JBR 无 jlink/jpackage，需完整 JDK（已下载 Microsoft JDK 21 到 `C:/Users/zjg/jdk21/`，`WINDOWS_PACKAGE_JAVA_HOME` 环境变量可覆盖）。
- **Windows 验证码真实正确率**：24 张固定冒烟集样本不在本机，仅完成 logits 对齐验证；真实正确率需样本集评测（用户已人工确认登录基本通过）。
- **验证码发布级准确率仍待扩样**；课件深层文件夹/信息流变化仍缺自然样本。
- 官方 1.7.0 / KMP PyTorch 2.1 在 API 37.1 有 16 KB page-size 提示。

## 3. 接下来 1～3 个阶段

1. **等用户验证其他平台**（Android/iOS/macOS 无功能影响已代码层核查：冻结根零改动、commonMain 仅版本常量、desktopMain 仅 displayName、desktopApp 仅 appearance 属性+版本号）。
2. **验证通过后发布 1.7.3-KMP**：推送 windows-dev → 合并 main → tag v1.7.3-KMP → 上传 Windows 安装器（`BJTUselfServiceKMP-1.7.3.exe`）→ GitHub Release（pre-release）。
3. **M13 物理在线接入**（需内网调研）。规划见 `docs/migration/m13-phyvlab-integration-plan.md`。

## 维护规则

- 每次开始目标模式任务时先读本文件；结束前必须再次更新。
- 已完成事项压缩为一行保留在“本阶段已做到”；里程碑真正完成时，把细节归档进 `history_full.md` 并从本文件删除。
- 痛点解除即删除或改写，不保留已经失效的阻塞描述。
- 近期计划只保留接下来 1～3 个可执行阶段；远期内容留在 `goal.md`。
- 事实、命令结果和验证边界要具体；不能把计划写成已完成。
- 分支、基线、最新 Release 或工作区状态发生变化时，更新文件顶部摘要。
- 不在本文件写入账号、密码、Cookie、令牌、真实验证码会话或其他敏感信息。
