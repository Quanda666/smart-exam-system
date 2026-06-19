# 305 题库操作即时审计证据

## 背景

题库已经有 `question_review_log`，并且系统日志可以统一检索题目创建、编辑、提交审核、审核通过、驳回、上下架和删除记录。但教师或管理员完成操作后，页面没有立即拿到本次生成的审计日志 ID，证据链还需要手动打开日志抽屉或系统日志查询。

## 本步范围

- `QuestionBankService.recordQuestionLog` 改为返回 `question_review_log.id`。
- 题库写操作响应新增 `questionReviewLogId`：
  - 新建题目
  - 编辑题目
  - 删除题目
  - 上架/下架题目
  - 提交审核
  - 审核通过
  - 审核驳回
- `QuestionBankPanel.vue` 在单题和批量操作成功后展示即时审计证据。
- 支持复制审计 ID 和 `/monitor/logs?questionReviewLogId=<id>` 深链。
- 本地质量门禁检查后端生成 ID 返回和前端展示/复制能力。

## 协同关系

- 教师端：题目创建、修改、提交审核后能立刻复制审计证据，便于教研组复核。
- 管理员端：审核题目和处理争议时可以直接跳到统一系统日志。
- 试卷/考试链路：题目作为试卷快照来源，内容变更证据可追溯到具体题目版本和审核动作。

## 验收点

- 题库写接口响应包含 `questionReviewLogId`。
- 批量操作能收集多个 `questionReviewLogId` 并展示数量。
- 题库页出现审计提示条，可复制原始 ID 或统一系统日志深链。
- `scripts/run-quality-gates.ps1` 覆盖后端与前端防回归检查。
