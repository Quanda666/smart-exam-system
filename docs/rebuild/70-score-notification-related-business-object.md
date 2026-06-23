# 第 70 批：成绩通知绑定业务对象

## 背景

第 69 批已经给通知表补充 `related_type`、`related_id`，并让监考提醒绑定到当前答卷。成绩发布和成绩申诉回复仍然只是按类型发送普通通知，学生端能看到消息，但系统无法严格证明这条消息对应哪一次答卷或哪一条申诉。

本批把成绩相关通知也纳入业务关联约束，继续收紧三端协同链路。

## 本批改动

- 成绩发布通知不再使用无关联的 `sendBatch` 批量发送。
- `publishScores` 会遍历本场考试所有已完成答卷，并为每份答卷发送一条通知：
  - `type = SCORE`
  - `relatedType = EXAM_ATTEMPT`
  - `relatedId = attemptId`
- 发布接口保留 `notifiedStudents` 作为去重学生数，新增 `notifiedAttempts` 表示已绑定通知的答卷数。
- 成绩申诉回复通知绑定到申诉记录：
  - `type = SCORE_APPEAL`
  - `relatedType = SCORE_APPEAL`
  - `relatedId = appealId`
- 前端考试 API 类型补充 `notifiedAttempts`，保持成绩发布提示的原有口径。
- 总质量门新增静态护栏：
  - 禁止成绩发布回退到无关联 `sendBatch`。
  - 要求成绩发布通知包含 `EXAM_ATTEMPT`。
  - 要求申诉回复通知绑定 `SCORE_APPEAL` 的申诉 ID。
- `-CheckScoreReleaseVisibilityCycle` 验收增强：发布成绩后会检查学生通知列表中存在绑定当前 `attemptId` 的 `SCORE` 通知。

## 三端协同影响

- 教师端：发布成绩仍按考试维度操作，但通知落库细化到答卷维度，后续可以准确展示某个学生是否已收到某次答卷的成绩通知。
- 学生端：通知中心返回的成绩通知可以直接定位到具体答卷或申诉，不再依赖标题、时间或考试名称做弱匹配。
- 管理员端：审计通知送达时，可以用 `related_type/related_id` 关联答卷、成绩发布状态、申诉处理记录，形成可追溯证据链。

## 验收方式

静态与构建质量门：

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

## 后续建议

- 给通知列表接口增加 `relatedType`、`relatedId` 过滤参数，避免前端拉取后再过滤。
- 成绩撤回也可发送绑定 `EXAM_ATTEMPT` 的撤回通知，减少学生只看到旧发布通知的困惑。
- 通知中心可按业务对象聚合，展示“成绩发布、申诉回复、监考提醒”的完整时间线。
