# 63. Score Release Audit Feedback

## 目标

本批继续补强成绩发布闭环，让发布和撤回动作具备更清晰的操作反馈与验收依据。

前几批已经收紧了学生端可见性、阅卷完整覆盖、交卷题号范围和未答统计。本批关注教师/管理员执行“发布成绩”和“撤回成绩”时，接口是否能返回足够的审计信息，以及验收脚本是否能证明“发布后可见，撤回后隐藏”的完整链路。

## 后端调整

涉及文件：

- `backend/src/main/java/com/smartexam/service/ExamService.java`

### 发布成绩返回增强

`publishScores` 继续保留原有前置约束：

- 考试必须已结束或已关闭。
- 不能存在进行中的答卷。
- 不能存在待阅卷答卷。
- 至少存在一份已完成答卷。

返回数据新增：

- `completedAttempts`：本次可发布的已完成答卷数。
- `pendingReviewAttempts`：发布时待阅卷答卷数，成功发布时为 0。
- `activeAttempts`：发布时进行中答卷数，成功发布时为 0。
- `notifiedStudents`：本次发送成绩发布通知的学生数。

### 撤回成绩返回增强

`revokeScores` 返回数据新增：

- `visibleAttemptsBeforeRevoke`：撤回前对学生可见的已完成答卷数。

撤回后 `score_release.status = 0`，学生成绩、成绩详情、错题本、掌握度和首页成绩指标都会按第 61 批约束同步隐藏。

## 前端调整

涉及文件：

- `frontend/src/api/exam.ts`
- `frontend/src/components/ExamManagement.vue`

API 类型补充发布/撤回统计字段。

教师端操作提示从固定文案升级为统计反馈：

- 发布成功：`成绩已发布：N 份答卷，已通知 M 名学生`
- 撤回成功：`成绩已撤回：N 份学生成绩已隐藏`

这让教师更容易确认操作影响范围，也方便后续做操作日志和审计看板。

## 验收脚本增强

涉及文件：

- `scripts/verify-attempt-resilience.ps1`
- `scripts/run-attempt-resilience-acceptance.ps1`

新增参数：

```powershell
-CheckScoreReleaseVisibilityCycle
```

该验收会在提交重放检查之后执行：

1. 使用管理员 token 关闭 fixture 考试。
2. 发布成绩。
3. 断言发布响应包含 `completedAttempts >= 1` 和 `notifiedStudents >= 1`。
4. 使用学生 token 查询 `/api/student/grades`，确认本次 `attemptId` 可见。
5. 使用学生 token 查询 `/api/student/exam-result/{attemptId}`，确认成绩详情可见。
6. 撤回成绩。
7. 断言撤回响应包含 `visibleAttemptsBeforeRevoke >= 1`。
8. 再次查询学生成绩列表和成绩详情，确认本次成绩不可见。

一次性验收示例：

```powershell
scripts\run-attempt-resilience-acceptance.ps1 -CheckForgedQuestionRejection -ExpectUnreleasedStudentInsightsHidden -CheckScoreReleaseVisibilityCycle
```

该链路适合临时 fixture 使用，不建议对真实考试运行，因为它会关闭考试、发布成绩并撤回成绩。

## 三端影响

管理员端：

- 发布/撤回动作具备更明确的审计数据基础。
- 后续可将这些统计写入操作日志或系统审计报表。

教师端：

- 发布和撤回后能直接看到影响范围。
- 减少“点了按钮但不知道发布了几份”的不确定性。

学生端：

- 发布后成绩、详情和学习反馈可见。
- 撤回后成绩、详情和学习反馈再次隐藏。

## 验收标准

- PowerShell 脚本语法解析通过。
- Java 全量源码卫生检查通过。
- 前端源码卫生检查通过。
- 完整本地质量门禁通过。
- 真实 fixture 环境中可用 `-CheckScoreReleaseVisibilityCycle` 验证发布后可见、撤回后隐藏。

## 后续增强

- 将发布/撤回统计写入操作日志，形成管理员端审计列表。
- 为成绩发布增加发布备注和撤回原因。
- 增加成绩发布前预检接口，提前告诉教师已完成、待阅卷、缺考、未开始、可发布人数。
