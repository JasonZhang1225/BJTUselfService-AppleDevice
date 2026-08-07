# BJTUselfService KMP 迁移工作记忆

> 最后更新：2026-08-07
> 当前分支：`main`（跟踪 `mine/main` → `JasonZhang1225/BJTUselfService-AppleDevice`，本地可领先远端；M11 教室占用查询里程碑待本提交）
> 分支创建点：`9d8da18`；发布与功能基线：`v1.7.0@419313d`
> 远端历史：2026-08-03 已强制改写为单根提交 `46f6ef9`；远端仅 `HEAD/main`、无标签/PR/下游 fork。旧 SHA 仍可被 GitHub 缓存直接解析；Support 要求先轮换泄露凭据，用户决定不再提交清缓存工单并自行更换密码。
> 完整历史与已归档的验收细节：见 `history_full.md`（按里程碑归档，只读）
> 本文件是实时工作记忆，不是只追加日志：任务开始读、结束改，只保留当前接续工作需要的状态。

## 1. 本阶段已做到（≤10 行）

- **2026-08-07 M11 教室占用查询（新功能，老安卓无）**：教务 `room_view` 两级楼→占用；`jxlh` 数字 ID；学期/周弹层；bksy 校历 `executePublic`；弹层选周用详情 hostScope（修「同步中」卡死）；空闲格软绿。同批：底栏外提+水波纹、人数估计搜索防抖、Android `yaya.csoci.com` 明文、更多校园顺序/改名、sheet 可下滑、iOS embed 补 `UNLOCALIZED_RESOURCES_FOLDER_PATH`。细节见 `history_full.md` M11。
- **主线已通**：M0–M7、M9 成绩性质、M10 平台原生导航、紧凑壳/静默登录、08-07 紧凑端 UI 真机验收；细节见 `history_full.md`。
- **M5.5 验证码/登录链路已实现**：Android TorchScript、Apple Core ML，冒烟 21/24；自动识别默认开；主登录页隐藏验证码、失败最多 3 次后弹手动框。**未完**：真机 Password AutoFill/Keychain 时序、扩样 ≥300。
- **等外部条件**：真实数据变化样本、真实作业上传、退出账号往返（会话不再需要时）。
- **缺权限/签名**：iOS Keychain 合法签名往返、Apple Developer 正式签名/公证/隐私问卷。
- **本地分发包**：根目录 `builtapps/`（gitignore）含 1.7.0 原版与 KMP 三端 debug/开发签名包，非正式发布。

## 2. 当前痛点（≤8 条）

- **构建环境**：compose 1.12.0-beta03 要求 compileSdk 37，本机 SDK 36.1；`android.experimental.disableCompileSdkChecks=true` 已绕过。升 AGP 或装 android-37 后复查。
- **紧凑端 UI**：明文授权 banner 只显示一条待确认；课表课程详情弹窗仍 `skipPartiallyExpanded=false`（全项目唯一），若反馈卡半高再对齐 true。
- **CMP 下拉刷新与系统过滚不兼容（已放弃）**：用户决定日后 SwiftUI 重写再做 `.refreshable`。
- **验证码发布级准确率仍待扩样**：24 张冒烟集 87.5%；公版前需 ≥300 张独立留出集。
- **登录页仍缺 iPhone 真机 Password AutoFill→自动登录时序与 Keychain 专项复验**。
- **课件深层按需请求仍缺真实文件夹样本**；信息流增删改仍无服务器自然变化样本。
- 官方 1.7.0 / KMP PyTorch 2.1 在 API 37.1 有 16 KB page-size 提示；正式 Android 发布前需处理原生库对齐。

## 3. 接下来 1～3 个阶段

1. **M5.5 登录页与凭据收尾**：真机 Keychain 与 Password AutoFill 时序；自然失败时目视验证码弹框。
2. **自然样本补证与公版门禁**：课件文件夹、作业上传、信息流变化、验证码扩样；UI 逐页按用户指定推进。

## 维护规则

- 每次开始目标模式任务时先读本文件；结束前必须再次更新。
- 已完成事项压缩为一行保留在“本阶段已做到”；里程碑真正完成时，把细节归档进 `history_full.md` 并从本文件删除。
- 痛点解除即删除或改写，不保留已经失效的阻塞描述。
- 近期计划只保留接下来 1～3 个可执行阶段；远期内容留在 `goal.md`。
- 事实、命令结果和验证边界要具体；不能把计划写成已完成。
- 分支、基线、最新 Release 或工作区状态发生变化时，更新文件顶部摘要。
- 不在本文件写入账号、密码、Cookie、令牌、真实验证码会话或其他敏感信息。
