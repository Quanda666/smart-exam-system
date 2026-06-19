# 第 72 批：成绩撤回通知闭环

## 背景

成绩发布已经具备显式发布、学生可见、通知触达和答卷绑定能力。但撤回成绩时，系统此前只更新 `score_release.status = 0` 并隐藏学生端成绩，没有通知受影响学生。

这会造成一个真实业务问题：学生已经收到“成绩已发布”通知，随后成绩被撤回，却没有新的站内消息说明状态变化。对学院级在线考试系统来说，成绩发布和撤回都属于高敏感动作，必须具备对学生可见的触达记录和可审计的业务关联。

## 本批改动

- `revokeScores` 在撤回前读取当前仍可见的已完成答卷。
- 撤回 `score_release` 后，按受影响答卷逐条发送通知：
  - `type = SCORE_REVOKED`
  - `relatedType = EXAM_ATTEMPT`
  - `relatedId = attemptId`
- 撤回接口返回新增：
  - `notifiedStudents`：本次撤回通知触达的去重学生数。
  - `notifiedAttempts`：本次撤回通知绑定的答卷数。
- 前端撤回成绩 API 类型补充 `notifiedStudents`、`notifiedAttempts`。
- `-CheckScoreReleaseVisibilityCycle` 验收增强：
  - 发布成绩后按 `EXAM_ATTEMPT/attemptId` 查询 `SCORE` 通知。
  - 撤回成绩后按 `EXAM_ATTEMPT/attemptId` 查询 `SCORE_REVOKED` 通知。
- 总质量门新增源码护栏，要求撤回成绩必须发送 `SCORE_REVOKED` 并返回通知统计。

## 三端协同影响

- 管理员端：撤回成绩不再只是后台状态变更，而是具备学生触达记录，后续可进入操作审计。
- 教师端：撤回动作仍在考试管理页完成，接口返回已包含通知统计，为后续优化提示文案或审计列表提供数据。
- 学生端：通知中心可以看到成绩撤回消息，且消息绑定具体答卷，避免只看到旧发布通知造成误解。

## 验收方式

本地质量门：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

真实 fixture 验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -AdminUsername admin `
  -AdminPassword your-password `
  -CheckScoreReleaseVisibilityCycle `
  -CleanupAfterRun
```

该验收会关闭考试、发布成绩、确认学生成绩可见、确认发布通知绑定当前答卷、撤回成绩、确认学生成绩隐藏，并确认撤回通知绑定当前答卷。

## 后续建议

- 成绩撤回接口增加撤回原因字段，并写入通知内容和操作日志。
- 通知中心支持按 `SCORE` / `SCORE_REVOKED` 类型筛选。
- 管理员端成绩发布审计页展示发布、撤回、通知触达和操作者时间线。
