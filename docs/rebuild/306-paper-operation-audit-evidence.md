# 306 试卷操作即时审计证据

## 背景

试卷是题库和考试发布之间的关键承接物。教师创建、规则组卷、复制、发布、撤回、删除试卷后，系统必须能立即给出本次操作的审计证据，方便后续在管理员日志中追踪是谁、何时、对哪份试卷做了什么变更。

## 本步范围

- `PaperController` 注入 `OperationLogService`。
- 试卷写操作响应新增 `operationLogId`：
  - 手动创建试卷：`CREATE_PAPER`
  - 规则组卷：`GENERATE_PAPER`
  - 复制试卷：`COPY_PAPER`
  - 编辑试卷：`UPDATE_PAPER`
  - 发布/撤回：`UPDATE_PAPER_STATUS`
  - 删除试卷：`DELETE_PAPER`
- `api/paper.ts` 在 `PaperInfo` 和删除响应中声明 `operationLogId`。
- `PaperPanel.vue` 在单条和批量操作成功后展示最近一次试卷操作审计证据。
- 支持复制审计 ID 和 `/monitor/logs?operationLogId=<id>` 深链。
- `scripts/run-quality-gates.ps1` 增加后端与前端防回归检查。

## 三端协同关系

- 教师端：组卷、发布、撤回等动作完成后，教师可以立即复制审计证据交给教务或管理员核验。
- 管理员端：通过统一系统日志按 `operationLogId` 精确定位试卷变更记录，和考试发布、成绩发布日志形成连续链路。
- 学生端：不直接感知试卷审计信息，但学生考试入口依赖已发布试卷，管理员可用审计证据追踪试卷是否在考试前被正确发布。

## 验收点

- 试卷创建、规则组卷、复制、编辑、发布、撤回、删除接口均返回 `operationLogId`。
- 试卷页面操作成功后出现审计提示条。
- 批量发布/撤回/删除能收集多个 `operationLogId` 并展示。
- 复制审计 ID 和复制审计链接均可用。
- 本地质量门禁覆盖上述后端和前端改动。
