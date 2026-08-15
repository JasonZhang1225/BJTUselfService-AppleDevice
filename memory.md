# BJTUselfService KMP 迁移工作记忆

> 最后更新：2026-08-15
> 当前分支：`main`（跟踪 `mine/main` → `JasonZhang1225/BJTUselfService-KMP-Refreshed`）
> 阶段状态：**M12 已发布 `1.7.2-KMP`。课表培养方案冷启动 / 成绩变动弹窗 / 排序文案 / 限选配色待提交后升版发布。** 当前发布基线 `1.7.2-KMP`（tag `v1.7.2-KMP@fff061f`）。
> 分支创建点：`9d8da18`；上游对照基线：`v1.7.0@419313d`；KMP 自身基线：**`v1.7.2-KMP`（git tag，`fff061f`）**
> 完整历史与已归档的验收细节：见 `history_full.md`（按里程碑归档，只读）
> 本文件是实时工作记忆，不是只追加日志：任务开始读、结束改，只保留当前接续工作需要的状态。

## 1. 本阶段已做到（≤10 行）

- **第一阶段收口 + `1.7.1-KMP`/`1.7.2-KMP` + M12**：细节见 `history_full.md`。
- **2026-08-15 体验修复**：培养方案独立补拉并重试；分项成绩变动弹窗；排序文案默认顺序/正序/逆序；限选琥珀配色。用户平板已确认弹窗、新装分类色块与配色。
- **验证**：`./gradlew :shared:desktopTest --tests '*Grade*' --tests '*HomeChange*' --tests '*CourseSchedule*' --tests '*GradeSemantic*'` 通过；`:shared:compileKotlinIosSimulatorArm64` 通过。实机登录后课表上色与弹窗尚未目视。
- **本地 Android debug 包**（2026-08-15）：`~/Downloads/BJTUSelfService-KMP-1.7.2-KMP-debug-20260815.apk`（350M，debug 签名，含未提交三项修复）。命令：`multiplatform` 下 JBR 21 `:androidApp:assembleDebug --rerun-tasks`。

## 2. 当前痛点（≤8 条）

- **实机未看**：新装/清方案缓存后课表是否自动上色；有缓存改分项是否弹窗。
- **构建环境**：compose 1.12.0-beta03 要求 compileSdk 37，本机 SDK 36.1 已实验绕过。
- **验证码发布级准确率仍待扩样**；课件深层文件夹/信息流变化仍缺自然样本。
- 官方 1.7.0 / KMP PyTorch 2.1 在 API 37.1 有 16 KB page-size 提示。

## 3. 接下来 1～3 个阶段

1. **实机确认这三项**，需要时再提交。
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
