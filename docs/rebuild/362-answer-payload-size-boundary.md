# Step 362 - Answer Payload Size Boundary

## 背景

学生提交答案会进入答卷状态流转、未答题补齐、自动判分、人工阅卷、错题本和成绩发布链路。第 360 步已经确保题号必须为正数，本次继续收紧提交载荷大小，避免异常超大答案 Map 或单题超长内容进入请求解析、哈希计算和 `answer_record` 写入。

数据库中的 `answer_record.answer_content` 使用 `TEXT`，系统应在接口层和服务层提前给出清晰边界，而不是依赖数据库或序列化异常兜底。

## 本次改动

- `AnswerRequest.answers` 增加最大 1000 条限制。
- `AnswerRequest.answers` 的单题答案内容增加最大 20000 字符限制。
- `ExamService` 新增 `MAX_SUBMITTED_ANSWER_COUNT` 和 `MAX_SUBMITTED_ANSWER_LENGTH`。
- `ExamService.validateSubmissionAnswers` 在题目范围校验前拒绝超量答案。
- `ExamService.validateSubmissionAnswers` 在写库前调用 `requireBoundedAnswerContent`，拒绝超长单题答案。
- 质量门禁新增结构断言，确保提交答案大小边界持续存在。

## 协同意义

- 学生端：异常提交会得到明确错误，不会在交卷后才暴露为数据库或阅卷异常。
- 教师端：阅卷任务不会被超长异常内容拖慢或污染。
- 管理员端：提交链路的容量边界更明确，便于压测和故障排查。

## 验收点

- 提交答案超过 1000 个题号时被拒绝。
- 任一单题答案超过 20000 字符时被拒绝。
- 合法答案提交、自动判分、人工阅卷、错题本和成绩发布行为不变。
- 强制交卷使用草稿答案时也会经过同一服务层边界。
