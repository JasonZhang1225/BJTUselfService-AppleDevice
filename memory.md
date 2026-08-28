# BJTUselfService KMP 迁移工作记忆

> 最后更新：2026-08-28
> 当前分支：`windows-dev`（已快进同步 `v1.7.3-KMP-B`，M13 物理在线首版改动仍在工作区）
> 阶段状态：**173B 基座已同步；M13 代码层初步开发完成。Mac 已完成开发包构建、真实登录态列表与作业详情交互，已修复可选编辑提交页 404 导致的详情误报，并补齐详情页宽窄屏自适应布局、中文 Dock 名称和物理在线首页安排/缓存。iOS 模拟器已安装启动到登录页，实体 iPhone 当前不可用且缺匹配 provisioning profile。原生附件提交仍待用户确认后的真实上传；触摸屏和移动端登录后运行仍待验收。当前版本为 `1.7.4-KMP`。**
> 分支创建点：`9d8da18`；上游对照基线：`v1.7.0@419313d`；KMP 自身基线：**`v1.7.3-KMP-B` (`a342615`)**；上一发布 `v1.7.3-KMP-A@6c69c85` 保留。
> 完整历史与已归档的验收细节：见 `history_full.md`（按里程碑归档，只读）
> 本文件是实时工作记忆，不是只追加日志：任务开始读、结束改，只保留当前接续工作需要的状态。

## 1. 本阶段已做到（≤10 行）

- **第一阶段收口 + `1.7.1-KMP`/`1.7.2-KMP` + M12 + `1.7.2-KMP-A` + M14 Windows**：细节见 `history_full.md`。
- **2026-08-17 `1.7.3-KMP-B` 基座**：教学周改 `getTimeList`；作业容错对齐 1.7.0；CI、Windows MSI ASCII 修复、macOS JDK/iOS 任务拆分均已合入 `a342615`。
- **Windows 移植（M14）**：DPAPI 凭据保险库、%LOCALAPPDATA% 缓存、AWT 文件网关、系统浏览器、Ktor CIO、GB18030、验证码推理、品牌图标与打包链路已实现；细节见 `history_full.md`。
- **2026-08-26 M13 调研**：站点确认为 Moodle 4.0.4+ / Adaptable；统一认证为 CAS OAuth2 authorization-code → Moodle `/admin/oauth2callback.php`；访客课程、标准活动与 Unity WebGL 边界已记录到 `docs/migration/m13-phyvlab-integration-plan.md`。
- **2026-08-27 M13 首版工作区**：更多 → 学业第一项接入物理在线；共享 CAS/OAuth2 白名单握手、服务端课程链接兜底、课程作业到期日解析、首页日程合并与自动同步开关已实现。Windows 构建版已用同一 Ktor/CAS 会话验证：3 门课程、32 个活动可读取，课程页到期安排可原生显示；作业详情已原生显示提交/批改/成绩/反馈/附件，点击不再自动跳浏览器。M13 续补了物理 MoodleSession 过期时的 App 内强制 CAS challenge 恢复路径。
- **2026-08-27 触摸与提交补强**：Windows 桌面主要纵向滚动和横向筛选 chip 增加触摸拖动兼容层；Android/iOS 复用平台文件选择器，普通 Moodle 作业提交实现“草稿上传 → 最终表单”协议并加显式确认；课程作业列表按 Moodle 活动 ID 默认新的在上方并支持正/逆序切换。真实上传尚未执行。
- **2026-08-27 Chrome DevTools 只读复核**：用户登录后可读 `course/view.php?id=72` 与作业详情；课程页 `modtype_assign`/到期日与解析器一致。已提交作业无 filemanager；可编辑作业的上传控件在 `action=editsubmission`（`files_filemanager` + `.filemanager`，data-* 不在 DOM）。Codex 浏览器策略拒绝仍在，见 `docs/migration/phyvlab-browser-policy-limit.md`。
- **2026-08-27 Android 网络授权**：启动 AVD 时出现的 `adb.exe` 公共/专用网络提示，用户已手动点击允许；未修改其他防火墙规则。若再次弹出，仅按用户已给的一次性授权处理同一个明确对话框。
- **2026-08-27 开发版打包**：统一版本更新为 `1.7.4-KMP-DEV`（Android/iOS build 15；Windows/desktop jpackage 数值版本 `1.7.4`），保留 Windows UpgradeCode 以覆盖 `1.7.3`；排序与 Android 网络权限修正后的 MSI/EXE 和 Android debug APK 均已生成并复制到用户 OneDrive 桌面，安装器描述和设置页均标记“开发版”。`Program Files` 中仍是较早安装副本，需用户覆盖安装后再验收日期/排序。
- **2026-08-27 DMG 文件图标**：最新 `BJTUselfServiceKMP-1.7.4.dmg` 已重打并复制到 `/Users/zjg/Downloads`；DMG 文件的 Finder 自定义图标改为带透明留白的圆角版本，应用本身和挂载卷图标保持原样。
- **2026-08-27 首页安排与物理在线缓存**：物理在线作业解析并生成“开放/截止”两条首页安排；普通作业和物理在线截止若恰好为次日 `00:00`，首页均归到前一天，开放事件不平移。物理在线课程/作业/安排/已读取详情使用按学号隔离的 `CacheStore` 快照，网络或 CAS 不可达时保留只读缓存，物理在线顶栏显示“未同步 · 缓存”；不缓存 Cookie、sesskey、草稿上传上下文或可提交能力。
- **2026-08-28 iOS unsigned IPA**：基于当前 `windows-dev` 工作区用 Xcode Release/generic iOS、`CODE_SIGNING_ALLOWED=NO` 生成并封装 `BJTUSelfService-KMP-1.7.4-KMP-iOS-unsigned.ipa`，已放入 `/Users/zjg/Downloads`；Bundle ID `team.bjtuss.bjtuselfservice.kmp.ios`、版本 `1.7.4-KMP`、Build `15`，包内无 `_CodeSignature`。SHA-256 为 `796d977f659de7732577e5729c035660a96d00f71ba9a36a611b7b7b2a1776ca`；无匹配 provisioning profile 时只能用于留档/后续签名，不能直接装实体机。
- **2026-08-28 移动端物理在线收口**：物理在线列表在窄屏增加自适应水平边距，安排月份控件在小宽度下换行；点击作业在紧凑窗口按作业功能同样进入原生二级详情页，宽屏继续使用底部详情弹窗。设置页将自动同步文案缩为“仅校园网”，移除开发版验证提示，Android/iOS/更新检测版本统一为 `1.7.4-KMP`。iOS Simulator Debug 已重新构建、安装并启动到版本正确的登录页。
- **2026-08-28 同步失败提示收口**：首页“部分同步失败/同步失败”胶囊点击后同时弹出失败模块清单并主动重试；清单会明确列出“物理在线（仅校园网下同步）”。物理在线失败且有缓存时，顶栏显示“同步失败·正显示缓存”，失败横幅改为“失败原因：网络问题。请确认已连接到校园网。”并标出“仅校园网下同步”“当前显示本地缓存”和缓存创建时间，不再显示原始 `network` 诊断。
- **2026-08-27 M13 初步开发记录**：本轮把 173B 基座同步、物理在线原生接入、触摸滚动、跨平台验证边界、真实提交限制和浏览器策略堵点集中记录到 `docs/migration/m13-phyvlab-integration-result.md`；该文档是后续 AI 审理和用户验收的依据。M14 仍专指 Windows 桌面端移植。
- **2026-08-27 Mac/iOS 验证**：Mac `:desktopApp:packageDmg` 成功，arm64/ad-hoc 严格签名通过；真实登录态进入物理在线后读取 3 门课程、32 个活动，课程切换、作业排序和月份切换通过；详情主页面返回 200，辅助编辑页返回 404 时现在保留主详情，打包版已实际显示提交状态、成绩/反馈字段和附件。iOS 27.0 Simulator Debug 已重新构建、安装、启动到登录页，AppIcon 资源存在；generic iPhoneOS Release arm64 unsigned 构建通过。实体 iPhone 15 Pro Max 在 `devicectl` 中为 `unavailable`，Apple Configurator 无连接设备；本机签名构建因无匹配 provisioning profile 失败。未上传或提交作业。
- **2026-08-27 `v1.7.3-KMP-B` 基线审计**：`v1.7.3-KMP-B^{}`（`a342615`）是 `windows-dev` M13 提交的直接父提交；逐文件核对未发现 B 的独立功能被删除。除物理在线接线、桌面滚动适配和其公共认证/传输支撑外，差异仅为版本/打包元数据、Android 网络权限与外链桥接；B 的 ASCII Windows 打包、macOS 中文 Dock/DMG 图标、课程表教学周和作业容错均保留。本次补齐 macOS build 15 与 `:desktopApp:run` 的中文 Dock 名称。

## 2. 当前痛点（≤8 条）

- **Windows 安装器品牌化受限**：jpackage 安装向导 UI（横幅、右上角图标、进度框）无参数可定制；安装完成后的 EXE/快捷方式/窗口/任务栏图标已是品牌 logo。若用户要完全品牌化安装向导，需引入 Inno Setup 等替代打包管线（未授权、未规划）。
- **构建环境**：compose 1.12.0-beta03 要求 compileSdk 37，本机 SDK 36.1 已实验绕过；Windows 侧 Android SDK/AVD/APK 门禁已通过。Mac 侧 Xcode 27.0、iOS Simulator/iphoneos arm64 构建和 macOS arm64 分发构建均通过；iOS 模拟器已启动应用，实体机和登录后移动端仍待设备/签名条件。
- **打包 JDK**：JBR 无 jlink/jpackage，需完整 JDK（本机 Microsoft JDK 21 `C:/Users/zjg/jdk21/jdk-21.0.8+9`，`WINDOWS_PACKAGE_JAVA_HOME` 可覆盖）。
- **KMP Actions 三跑**：Android、macOS DMG、iOS IPA 成功（Xcode 26.6 / iPhoneOS 26.5）。Windows 仍 `light.exe 311`：包名已是 ASCII，中文 description 仍进 MSI 字符串表。Release job 在 Set up 失败。
- **Windows 卸载清凭据待复测**：请装带卸载清理的 MSI 后再卸，确认 AppData 缓存和注册表凭据被删。
- **iOS 真机签名/连接**：generic iPhoneOS unsigned 构建通过，但当前 Bundle ID 没有匹配 provisioning profile；实体 iPhone 在 `devicectl` 中为 `unavailable`，合法签名、安装、Keychain 往返仍未取得证据。
- **验证码发布级准确率仍待扩样**；课件深层变化仍缺自然样本；官方 1.7.0 / KMP PyTorch 2.1 在 API 37.1 有 16 KB page-size 提示。
- **M13 现场解析差异与可选编辑页**：详情页状态标签是 `作业状态`（未交为“尚未批改”），不是 fixture 的 `提交状态`；filemanager 的 context/client/repo 在脚本 JSON 里，不在 DOM `data-*`。Mac 真实登录态课程/活动和主详情读取成功；已提交作业没有可用 filemanager，辅助 `action=editsubmission` 页返回 404 时按可选能力处理，不再覆盖主详情。真实上传仍未执行；Android/iOS 登录后 M13、REST token、Unity 外链仍待后续。

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
