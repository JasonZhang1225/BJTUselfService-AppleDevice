# BJTUselfService KMP 迁移工作记忆

> 最后更新：2026-08-03
> 当前分支：`main`（跟踪 `mine/main` → `JasonZhang1225/BJTUselfService-AppleDevice`）
> 分支创建点：`9d8da18`；发布与功能基线：`v1.7.0@419313d`
> 完整历史与已归档的验收细节：见 `history_full.md`（按里程碑归档，只读）
> 本文件是实时工作记忆，不是只追加日志：任务开始读、结束改，只保留当前接续工作需要的状态。

## 1. 本阶段已做到（≤10 行）

- **已完成（主线已通）**：M0 基线/工具链、M1 骨架、M2 领域层、M3 登录网络解析、M4 持久化（仅剩 iOS Keychain 签名往返）、M5 首页+八个业务切片、M6 平台能力（文件/网页/邮箱/macOS 菜单与窗口）、M7 三端真实登录验收。
- **等外部条件**：真实数据变化样本（等学校侧自然产生，不造假）、真实作业上传（等学校布置新作业）、退出账号往返（留到有效会话不再需要时）。
- **缺权限/签名**：iOS Keychain 合法签名往返（`errSecMissingEntitlement`）、Apple Developer Team/真机/正式签名/公证/隐私问卷。
- **当前最优先（按顺序）**：M5.5 登录页 UI + 验证码模型 → M5.6 用户手动确认全部 UI → 真实数据验证。
- 2026-08-01 移除验证性 iOS WidgetKit/App Group/共享快照实现；Desktop 233 项、iOS Simulator 218 项测试零失败，三端与 Apple 元数据脚本通过，最终 iOS `.app` 无 `PlugIns`。
- 2026-08-01 已完善首页跳转链接与二维码（完美校园/校园网，详见 `history_full.md` M5-0）；macOS 二维码与 Android 浏览器动作的独立复验仍未执行。
- 2026-08-01 M5.5.1 登录页 UI 首版已改（标题简化「交大自由行 / KMP Refreshed」、验证码缩小、答案框+登录按钮同行、删除安全存储小字与取消勾选即报错）；三端共享代码编译通过，尚未做三端视觉复验。
- 2026-08-01 M5.5.1 第二轮登录页 UI 已改并 iOS/Android 模拟器实机确认：整个 App 跟随系统深浅色（删除持久化主题偏好与设置页切换）、打开即自动取验证码、刷新右对齐、整体垂直居中、macOS 左窄右宽。
- 2026-08-01 M5.5.2 密码框平台语义已实现：新增 `LoginFieldPlatform` expect/actual。iOS 以同一 `UIView` 中两个原生字段承载 username/password AutoFill，Android/macOS 保留 Compose 字段与小眼睛；macOS AppKit 覆盖字段因窗口挂载不稳定已撤回，当前输入框稳定可见，但原生右键菜单仍待后续独立方案。
- 2026-08-01 M5.5 验证码链路已实现：原 23 MB PyTorch 权重重建为确定性模型（显式 `[0,1]→[-1,1]`、修正 argmax），Android 继续 TorchScript，iOS/macOS 使用同源 Core ML；24 张真实脱敏冒烟集两端均 21/24、argmax 24/24 一致，iOS Simulator `7+4→11`、Android 模拟器 `9×8→72`、macOS 分发包推理均通过。自动识别默认开启，安全保存默认勾选；有已存凭据时启动检查会话并最多自动登录 3 次，显示“正在检查登录，正在自动登录”，失败后弹出手动验证码与“重新输入账号和密码”。Desktop 242 项、iOS Simulator 227 项测试零失败，Android Debug、iOS arm64 Simulator 宿主、macOS 分发包及签名校验通过。
- 2026-08-02 已完成登录后同步与真机性能收尾：成绩/作业/课表/考试四项自动同步对未保存设置默认开启，用户显式关闭后仍持久化；作业 45 个列表请求与得分查询改为最多 3 个有界并发并保持原顺序，真机完整刷新由 16.258 秒降至 5.026 秒；课件改为先发布课程目录、当前课程首层按需加载、其他课程与文件夹按需加载，清缓存两轮从开始到首层分别约 2.06/1.89 秒（旧实现 12.143 秒）。临时 `BJTU_PERF`/expect-actual 日志已全部删除，最终无日志签名包已覆盖安装真机；Desktop/iOS 全测与 Android Debug 通过。
- 2026-08-02 已修复 iOS 真机签名：根因是 `iosApp.xcodeproj/project.pbxproj` Debug/Release 配置硬编码 `CODE_SIGNING_ALLOWED = NO`，产物完全无签名导致 `0xe800801c` 安装失败；移除该行并保留 `DEVELOPMENT_TEAM = 34S53DC6T6` 后，自动签名解析到 iOS Team Provisioning Profile，`xcodebuild` 真机构建、`codesign` 校验、`devicectl` 安装均通过；首次启动在 iPhone「设置→通用→VPN 与设备管理」信任 `Apple Development: 3236345078@qq.com` 后可用。iOS Keychain 合法签名往返的缺口已具备签名身份，待复验。
- 2026-08-03 iOS 登录页与安全区交互收尾：原生凭据框改为 14pt 圆角和动态配色，空白收键盘、Next→Go 与验证码 Go 链路已接通；键盘避让改为单一平台所有者，避免关闭键盘时残留白色 spacer。SwiftUI 根 `ZStack` 与 `ComposeView` 两层均穿透底部 container safe area；LLDB 确认 `UIWindow 402×874`、Compose host `(0,62;402,812)` 正好绘制到 y=874 且 bottom inset 为 0。成绩、课程表、考试、作业、课件、其他功能与教室紧凑页的固定底部 padding 已改为仅保留顶部/横向间距，首页与设置的滚动内容 padding 不变；iPhone 17 iOS 26.5 Simulator 已目视复核课件与考试底边，用户确认白边解决。
- 2026-08-03 macOS 凭据输入已收尾：账号与密码均只接受 ASCII 可打印字符（粘贴同样过滤）；Compose/AWT 的实验性拦截与直接 TIS/JNA 方案已撤销，后者曾因在 `AWT-EventQueue-0` 调 HIToolbox 触发 `_dispatch_assert_queue_fail`/`SIGTRAP`。最终由打包进 App 的小型 AppKit 桥接库在 macOS 主线程给当前 `NSTextInputContext.allowedInputSourceLocales` 设置 `NSAllRomanInputSourcesLocaleIdentifier`，字段或窗口失焦时恢复原配置；Desktop 编译/测试、macOS 分发包与签名通过，Computer Use 和用户手动使用微信输入法均确认账号/密码不再弹中文候选窗且应用不闪退。

## 2. 当前痛点（≤8 条）

- **验证码发布级准确率仍待扩样**：当前 24 张固定真实冒烟集单次表达式正确率 87.5%（21/24），两端完全一致；3 次独立新验证码理论成功率约 99.8%，且失败会回退手动，但公版前仍应扩大到至少 300 张独立留出集，不能把 24 张冒烟集当成最终精度门禁。
- **登录页仍缺两项真机验收**：iOS Simulator 的样式、软键盘收起、Next/Go 与白色键盘 spacer 修复已通过；仍需在 iPhone 真机核对键盘过渡、Password AutoFill 一次成组填充与 Keychain 保存/重启读取，在 macOS 收尾原生右键菜单。跨页 token 仍未全面收敛。
- **课件深层按需请求仍缺真实文件夹样本**：当前首门课程首层为 4 个非文件夹项目，已验证课程目录与课程首层冷缓存往返，但无法验证首次展开文件夹；等真实课程出现文件夹时自然补测，不为此制造数据。
- 服务器真实数据变化样本缺失（四类信息流只验证了基线建立，新增/修改/删除仍无样本）。
- **iOS Keychain 运行往返仍待专项复验**：2026-08-02 已获得合法开发签名并多次覆盖安装成功，未签名 Simulator 的 `errSecMissingEntitlement` 已不再是权限阻塞；仍需用专项步骤证明保存、重启读取与退出清理。
- 官方 1.7.0 与当前 KMP PyTorch 2.1 在 API 37.1 模拟器首次启动都会出现 16 KB page-size 兼容提示（KMP 涉及 PyTorch、FBJNI、C++ shared 与 androidx graphics path）；兼容模式下推理已通过，但正式 Android 发布前需升级或替换不对齐的原生库。

## 3. 接下来 1～3 个阶段

1. **M5.5 登录页与凭据收尾**：在 iPhone 真机专项验证 Keychain 保存、重启读取和退出清理，并目视密码键盘/自动填充；macOS 核对右键菜单与小眼睛。
2. **自然样本补证**：课程出现真实文件夹时验证首次展开按需请求；学校布置新作业时再验证上传；服务器自然产生变化时验证信息流新增/修改/删除。
3. **M5.5/M5.6 后续**：验证码公版前补至少 300 张独立留出集；UI 人工确认按用户决定暂缓，恢复后再进入全页面 M5.6。

## 维护规则

- 每次开始目标模式任务时先读本文件；结束前必须再次更新。
- 已完成事项压缩为一行保留在“本阶段已做到”；里程碑真正完成时，把细节归档进 `history_full.md` 并从本文件删除。
- 痛点解除即删除或改写，不保留已经失效的阻塞描述。
- 近期计划只保留接下来 1～3 个可执行阶段；远期内容留在 `goal.md`。
- 事实、命令结果和验证边界要具体；不能把计划写成已完成。
- 分支、基线、最新 Release 或工作区状态发生变化时，更新文件顶部摘要。
- 不在本文件写入账号、密码、Cookie、令牌、真实验证码会话或其他敏感信息。
