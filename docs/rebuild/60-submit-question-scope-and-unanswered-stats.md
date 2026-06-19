# 60. Submit Question Scope And Unanswered Stats

## 目标

本批回到考试作答主链路，补强交卷时的服务端约束和验收可见性。

开发文档中明确要求：交卷必须只接受本试卷题目，不允许伪造题号；未作答题目必须进入答题记录，否则成绩详情、错题统计和阅卷进度都会失真。现有实现已经按服务端试卷题目逐题写入 `answer_record`，未提交答案的题目会以空答案落库；但对客户端额外提交的非本试卷题号此前是静默忽略，安全语义不够明确。

## 后端调整

涉及文件：

- `backend/src/main/java/com/smartexam/service/ExamService.java`

交卷流程新增服务端校验：

- 加载服务端试卷题目集合，优先使用 `exam_question_snapshot`。
- 构建本次答卷允许提交的 `questionId` 集合。
- 若客户端提交的 `answers` 中存在空题号或非本次答卷题号，直接拒绝提交。
- 拒绝时抛出 `IllegalArgumentException`，由全局异常处理返回 400。
- 正常提交仍按服务端题目集合逐题写入 `answer_record`，未答题写入空答案。

新增提交统计字段：

- `questionCount`：服务端认定的本次答卷题目数。
- `answeredCount`：非空作答题目数。
- `unansweredCount`：未作答题目数。

这些字段会写入提交响应快照，因此重复提交重放时也能保持一致。

## 前端契约

涉及文件：

- `frontend/src/api/exam.ts`

`submitExam` 响应类型补充：

- `questionCount`
- `answeredCount`
- `unansweredCount`

前端当前不强依赖这些字段展示，但后续可以用于交卷确认、教师端进度分析和异常提交审计。

## 验收脚本增强

涉及文件：

- `scripts/verify-attempt-resilience.ps1`
- `scripts/run-attempt-resilience-acceptance.ps1`

`verify-attempt-resilience.ps1` 新增参数：

- `-CheckForgedQuestionRejection`：在真实交卷前先提交一个伪造题号，期望服务端拒绝。
- `-OmitLastAnswer`：真实交卷时故意漏掉最后一题，期望 `unansweredCount >= 1`。

原有提交重放验收继续保留，并新增断言：

- 首次提交必须返回 `questionCount`、`answeredCount`、`unansweredCount`。
- 完整答案提交时 `unansweredCount = 0`。
- 漏答模式下 `unansweredCount >= 1`。
- 重复提交重放时 `unansweredCount` 必须与首次提交一致。

`run-attempt-resilience-acceptance.ps1` 会将两个新增参数透传给底层验证脚本，便于在一次性验收流水线中覆盖伪造题号和未答统计。

## 三端影响

学生端：

- 防止篡改请求体向非本试卷题目提交答案。
- 交卷结果可明确知道是否存在未答题。

教师端：

- 阅卷和成绩分析基于完整 `answer_record`，未答题不会丢失。
- 后续可以按 `unansweredCount` 做缺答分析。

管理员端：

- 异常提交可以通过 400 错误和提交响应快照追踪。
- 压测或验收脚本可以显式证明伪造题号被拒绝。

## 验收标准

- PowerShell 脚本语法解析通过。
- Java 全量源码卫生检查通过。
- 前端源码卫生检查通过。
- 完整本地质量门禁通过。
- 运行真实环境验收时，可使用：

```powershell
scripts\run-attempt-resilience-acceptance.ps1 -CheckForgedQuestionRejection
```

或在一次性漏答验收中使用：

```powershell
scripts\run-attempt-resilience-acceptance.ps1 -OmitLastAnswer
```

## 后续增强

- 在端到端验收中增加“伪造题号被拒绝后仍可正常提交”的完整场景。
- 将 `questionCount`、`answeredCount`、`unansweredCount` 纳入教师端监考看板和成绩分析。
- 为 `answer_record` 增加题型、题目分值、题目序号快照字段，进一步降低阅卷对题库历史数据的依赖。
