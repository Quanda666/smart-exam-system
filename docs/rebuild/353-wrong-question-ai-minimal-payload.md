# Step 353 - Wrong Question AI Minimal Payload

## 背景

错题 AI 讲解必须以后端发布快照为准。上一轮已在后端为 `questionId`、`examId` 增加正数校验，并继续从服务端加载本人已发布错题快照。但前端 `WrongQuestionExplainPayload` 类型仍允许传入题干、学生答案、正确答案、解析、选项等字段。

即使后端当前不会信任这些字段，前端契约保留它们仍会误导后续开发者，增加敏感答案被带入请求体或日志的风险。

## 本次改动

- `frontend/src/api/ai.ts` 中 `WrongQuestionExplainPayload` 只保留：
  - `questionId`
  - `examId`
- 移除该类型对 `QuestionOption` 的依赖。
- 质量门扩大前端断言，禁止 `stem`、`questionType`、`studentAnswer`、`correctAnswer`、`analysis`、`wrongCount`、`options` 回到错题 AI 请求类型。

## 协同意义

- 学生端只提交定位错题所需的最小标识。
- 后端继续负责校验本人、已发布、已出分、非复查中和真实错题。
- AI 日志中减少前端冗余敏感字段进入请求体的机会。

## 验收点

- 前端 AI 错题讲解请求类型只包含 `questionId` 和 `examId`。
- 错题本组件调用 `explainWrongQuestion` 时只发送这两个字段。
- 后端仍以发布快照加载题干、答案、解析和选项。
