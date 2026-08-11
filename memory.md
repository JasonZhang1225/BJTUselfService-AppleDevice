# BJTUselfService KMP 迁移工作记忆

> 最后更新：2026-08-11
> 当前分支：`main`（跟踪 `mine/main` → `JasonZhang1225/BJTUselfService-KMP-Refreshed`）
> 阶段状态：**M12 已完成，下一阶段为 M13 物理在线接入调研**。第一阶段 pre-release `1.7.1-KMP` 仍为当前发布基线（tag `v1.7.1-KMP@8498f32`）。
> 分支创建点：`9d8da18`；上游对照基线：`v1.7.0@419313d`；KMP 自身基线：**`v1.7.1-KMP`（git tag，`8498f32`）**
> 远端历史：2026-08-03 曾强制改写，根提交为 `46f6ef9`；此后本地/远端已新增 16 个提交并带 `v1.7.1-KMP` 标签。旧 SHA 仍可被 GitHub 缓存直接解析；Support 要求先轮换泄露凭据，用户决定不再提交清缓存工单并自行更换密码。
> 完整历史与已归档的验收细节：见 `history_full.md`（按里程碑归档，只读）
> 本文件是实时工作记忆，不是只追加日志：任务开始读、结束改，只保留当前接续工作需要的状态。

## 1. 本阶段已做到（≤10 行）

- **第一阶段收口（M0–M11 + M5.5 + M5.6）**：登录、首页、成绩、课程表、考试、作业、课件、校历成绩单、教室（人数估计+占用查询）、设置、平台能力、原生导航全链路完成；登录页 UI/验证码与全部 UI 手动确认/真实数据验证已于 2026-08-09 由用户确认完成；细节全部见 `history_full.md`。
- **pre-release `1.7.1-KMP` 三端已发布**（2026-08-09）：本地构建上传 GitHub Release，git tag `v1.7.1-KMP` 指向 `8498f32`；M11 教室占用查询、壳层桌面/平板对齐、版本标识统一为最后三个里程碑提交。
- **M12 已完成（本次提交）**：日期映射/缓存跟随、跨本学期与选课学期“前往日期”、默认概览表格、五色图例、最左“全部教学周”与交替半格已完成；紧凑标题/日历确认文案已精简，课程地点按层级倒序并用 `-` 展示与导出。宽屏网格有性质配色、按钮及方向性无回弹动画；触摸板主路径使用 AppKit 原生 `phase/momentumPhase`，一次手势最多一周且无冷却，用户已验收。选课导出使用下一学期 `2026-2027-1`（第 1 周 `2026-09-07`），课程为 weekly recurrence，考试只允许单场加入；四端编译/测试通过。详见 M12 文档。
- **Apple 构建环境**：2026-08-11 经用户授权，全局 `xcode-select` 已切换到 `/Applications/Xcode-beta.app/Contents/Developer`；`xcrun xcodebuild -version` 为 Xcode 27.0（`27A5228h`），`simctl` 同样来自该路径。
- **M5.5 验证码/登录链路已实现**：Android TorchScript、Apple Core ML，冒烟 21/24；自动识别默认开；主登录页隐藏验证码、失败最多 3 次后弹手动框。
- **等外部条件**：真实数据变化样本、真实作业上传、退出账号往返（会话不再需要时）。
- **缺权限/签名**：iOS Keychain 合法签名往返、Apple Developer 正式签名/公证/隐私问卷。
- **本地分发包**：根目录 `builtapps/`（gitignore）含 1.7.0 原版与 KMP 三端 debug/开发签名包，非正式发布。
- **更新检测已预埋（worktree，未提交/未合并）**：新增 `shared/update/AppUpdateChecker`（kotlinx.serialization，指向本仓库 `JasonZhang1225/BJTUselfService-KMP-Refreshed`，用 `/releases` 列表含 pre-release，因 GitHub `/latest` 不返回 pre-release）；设置页「版本与项目」卡有手动「检查更新」按钮，进主界面后静默自动检测一次（仅新版本弹「前往下载」跳 GitHub 发布页，失败/无更新静默）；结果弹窗提升到 `AuthenticatedAppShell` 壳层。三端编译+339 桌面测试过，已 curl 验证真实 API 返回 `v1.7.1-KMP`。与教室占用重组（`0f234b3`）同在 worktree `claude/frosty-williams-5d71d3`，待 M12 落定后由用户合并。

## 2. 当前痛点（≤8 条）

- **构建环境**：compose 1.12.0-beta03 要求 compileSdk 37，本机 SDK 36.1；`android.experimental.disableCompileSdkChecks=true` 已绕过。升 AGP 或装 android-37 后复查。
- **紧凑端 UI**：明文授权 banner 只显示一条待确认；课表课程详情弹窗仍 `skipPartiallyExpanded=false`（全项目唯一），若反馈卡半高再对齐 true。
- **CMP 下拉刷新与系统过滚不兼容（已放弃）**：用户决定日后 SwiftUI 重写再做 `.refreshable`。
- **验证码发布级准确率仍待扩样**：24 张冒烟集 87.5%；公版前需 ≥300 张独立留出集。
- **课件深层按需请求仍缺真实文件夹样本**；信息流增删改仍无服务器自然变化样本。
- 官方 1.7.0 / KMP PyTorch 2.1 在 API 37.1 有 16 KB page-size 提示；正式 Android 发布前需处理原生库对齐。

## 3. 接下来 1～3 个阶段

1. **M13 物理在线接入**（需内网调研，外网 443 不通）。规划见 `docs/migration/m13-phyvlab-integration-plan.md`；M12 完整证据见 `m12-course-schedule-plan.md`。
2. **自然样本补证与公版门禁**：课件文件夹、作业上传、信息流变化、验证码扩样；UI 逐页按用户指定推进。

## 维护规则

- 每次开始目标模式任务时先读本文件；结束前必须再次更新。
- 已完成事项压缩为一行保留在“本阶段已做到”；里程碑真正完成时，把细节归档进 `history_full.md` 并从本文件删除。
- 痛点解除即删除或改写，不保留已经失效的阻塞描述。
- 近期计划只保留接下来 1～3 个可执行阶段；远期内容留在 `goal.md`。
- 事实、命令结果和验证边界要具体；不能把计划写成已完成。
- 分支、基线、最新 Release 或工作区状态发生变化时，更新文件顶部摘要。
- 不在本文件写入账号、密码、Cookie、令牌、真实验证码会话或其他敏感信息。
