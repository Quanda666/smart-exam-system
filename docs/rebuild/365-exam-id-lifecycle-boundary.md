# 365. Exam ID Lifecycle Boundary

## 背景

考试 ID 是教师端考试管理、管理员审批、成绩发布/撤回、审计日志和成绩导出的共同入口。此前多数接口会在 ownership 或状态查询时自然查不到非法 ID，但非正数仍可能进入数据库访问，错误语义也不够统一。

## 本步改造

- `ExamService` 新增 `requirePositiveExamId`。
- 审批、驳回、编辑、删除、关闭、成绩发布、成绩撤回、详情读取统一在服务层入口校验考试 ID。
- `currentExamStatus`、`currentExamStatusForUpdate`、`requireOwnedExam` 增加兜底校验，避免后续内部调用绕过入口边界。
- `ExportService.examScoreSheet` 和 `requireScoresReleased` 同步校验 `examId`，覆盖成绩导出旁路。
- 质量门新增考试生命周期和成绩导出 ID 边界检查。

## 三端影响

- 管理员端：审批、驳回、成绩发布/撤回和审计导出对非法考试 ID 返回明确参数错误。
- 教师端：考试管理、关闭考试、成绩导出等操作不会用非法 ID 触发数据库查询或权限判断。
- 学生端：无直接交互变化，但成绩发布和导出链路的服务端边界更一致。

## 验收点

- 非正数 `examId` 在考试生命周期状态查询前被拒绝。
- 非正数 `examId` 在成绩导出检查成绩发布状态前被拒绝。
- 合法考试 ID 的原有状态机、权限和成绩可见性规则不变。
