# 145. AI Wrong Explain Release Gate

## 背景

错题本数据已经受成绩发布、答卷完成、复核申诉状态约束。
但 AI 错题讲解接口原先直接信任前端传入的题干、正确答案、解析和选项。
正常页面路径不会明显出错，但接口本身没有和成绩发布状态闭合，存在绕开错题本数据门禁的风险。

真实考试系统里，AI 只能作为辅助解释，不能成为绕过成绩发布和复核流程的数据出口。

## 本批改动

- `AiController`
  - `/api/ai/wrong-question/explain` 收紧为学生端接口。
  - 调用服务层时传入当前登录用户。
- `WrongQuestionExplainRequest`
  - `questionId` 改为必填。
  - `stem` 不再作为必填可信输入，后端会自行加载题目快照上下文。
- `AiService`
  - 新增 `loadReleasedWrongQuestionForExplain`。
  - AI 讲解前按当前学生和 `questionId` 查询已发布、已完成、已批阅且确认为错误的答题记录。
  - 排除处于 `RECHECK_REQUIRED` 的开放复核申诉。
  - 优先使用 `exam_question_snapshot` 和 `exam_question_option_snapshot` 构造题干、题型、正确答案、解析、学生答案、错题次数和选项。
  - 找不到符合条件的错题时拒绝讲解。
- `StudentWrongBook.vue`
  - AI 讲解请求只提交 `questionId`。
  - 页面展示仍使用错题本接口返回的数据。
- `scripts/run-quality-gates.ps1`
  - 锁定 AI 错题讲解必须由后端已发布错题快照构造上下文。
  - 锁定前端不得再提交正确答案和解析给 AI 讲解接口。

## 验收重点

- 未发布成绩、未完成阅卷、复核中的答题记录不能生成 AI 错题讲解。
- 学生只能为自己的错题生成 AI 讲解。
- AI 讲解使用考试发布时冻结的题目与选项快照。
- 前端不能通过伪造题干、正确答案或解析影响 AI 讲解的数据来源。

## 后续建议

- 将 AI 讲解输出记录与 `questionId`、`attemptId`、快照版本关联，便于教师审计学生学习反馈。
- 后续可把“错题复练”也做成服务端生成任务，避免前端自行拼题。
