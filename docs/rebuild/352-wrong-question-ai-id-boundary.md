# Step 352 - Wrong Question AI ID Boundary

## 背景

错题 AI 讲解属于学生端错题复练闭环。该接口不能信任前端传来的题干、答案或解析，必须由后端重新加载“本人、已发布、已出分、非复查中、真实答错”的题目快照。

现有 `AiService.loadReleasedWrongQuestionForExplain` 已经从后端快照重新取数，但 `WrongQuestionExplainRequest` 只校验 `questionId`、`examId` 非空。非正数 ID 会落到数据库查询后失败，错误语义不稳定。

## 本次改动

- `WrongQuestionExplainRequest.questionId` 增加 `@Positive`。
- `WrongQuestionExplainRequest.examId` 增加 `@Positive`。
- `AiService.loadReleasedWrongQuestionForExplain` 增加 service 层正数校验，避免内部调用绕过 Controller 的 Bean Validation。
- 质量门新增结构性断言，要求错题 AI 讲解接口同时具备 DTO 校验和 service 层兜底。

## 协同意义

- 学生端：错题讲解请求参数错误会被稳定拦截。
- 教师端：发布后的题目快照仍是学习反馈和 AI 讲解的事实来源。
- 管理员端：AI 使用日志不会混入非法题目/考试 ID 造成的噪声。

## 验收点

- `questionId <= 0` 不能进入错题快照查询。
- `examId <= 0` 不能进入错题快照查询。
- 前端传入的题干、答案、解析继续不作为可信来源。
- 讲解仍只允许已发布、已出分、非复查中的本人错题。
