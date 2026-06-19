# 307 AI 生成题目保存审计证据

## 背景

教师端可以用 AI 生成题目草稿，再批量保存到题库。普通题库新增已经返回 `questionReviewLogId`，但 AI 批量保存原先只展示 `savedCount`，教师保存后无法立即拿到每道题对应的审核日志 ID，导致 AI 题目进入题库后的证据链不完整。

## 本步范围

- `AiController.saveGeneratedQuestions` 汇总每道题创建结果中的 `questionReviewLogId`。
- `/api/ai/questions/save` 响应新增 `questionReviewLogIds`，同时保留 `savedCount` 和 `questions`。
- `api/ai.ts` 声明 `SaveGeneratedQuestionsResult.questionReviewLogIds`。
- `QuestionBankPanel.vue` 在 AI 草稿批量保存成功后复用现有题库操作审计提示条。
- `scripts/run-quality-gates.ps1` 增加 AI 保存审计 ID 的后端与前端防回归检查。

## 三端协同关系

- 教师端：AI 题目批量保存后可立即复制多个题目审核日志 ID，便于教研组复核来源、内容和审核状态。
- 管理员端：可通过 `/monitor/logs?questionReviewLogId=<id>` 精确追踪 AI 题目入库记录。
- 学生端：不直接接触 AI 题目审计信息，但考试发布后若题目来源存在争议，可通过题库审计链回溯到 AI 生成与教师确认环节。

## 验收点

- AI 批量保存题目响应包含 `questionReviewLogIds`。
- 题库页面保存 AI 草稿后出现审计提示条。
- 可复制审计 ID 和统一日志深链。
- 质量门禁能阻止后端或前端遗漏该审计证据。
