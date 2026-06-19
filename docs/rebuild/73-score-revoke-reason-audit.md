# 第 73 批：成绩撤回原因审计

## 背景

第 72 批已经让成绩撤回动作向学生发送 `SCORE_REVOKED` 通知，并绑定到具体答卷。但撤回动作仍缺少一个关键审计字段：为什么撤回。

在真实在线考试系统里，成绩撤回属于高敏感操作。它会改变学生端成绩可见性，也可能引发申诉、复评和教学管理追责。因此撤回必须带原因，且原因要同时进入数据库、接口返回和学生通知。

## 本批改动

- 新增 `ScoreRevokeRequest`：
  - `reason`：撤回原因，最长 500 字。
- `POST /api/exams/{id}/scores/revoke` 接收请求体：

```json
{
  "reason": "发现评分规则需要复核，成绩将重新发布"
}
```

- 后端撤回成绩时要求原因非空，否则拒绝撤回。
- `score_release.note` 正式作为成绩发布/撤回备注字段使用：
  - 发布成绩时清空旧撤回原因。
  - 撤回成绩时写入本次撤回原因。
  - `scoreReleaseState` 返回 `note`。
  - 撤回响应额外返回 `revokeReason`。
- 旧库自愈迁移新增 `ensureScoreReleaseNoteColumn`，旧库缺少 `score_release.note` 时自动补列。
- 学生 `SCORE_REVOKED` 通知内容包含撤回原因。
- 教师/管理员端撤回成绩时改为弹出原因输入框，并强制填写。
- `-CheckScoreReleaseVisibilityCycle` 验收增强：
  - 撤回时传入唯一原因。
  - 校验撤回接口返回该原因。
  - 校验学生收到的 `SCORE_REVOKED` 通知包含该原因。
- 总质量门新增源码护栏，防止撤回原因、迁移补列、前端输入框或验收断言被回退。

## 三端协同影响

- 管理员端：撤回成绩有明确原因，可进入后续审计报表和操作日志。
- 教师端：撤回前必须说明原因，避免误操作和无依据撤回。
- 学生端：收到撤回通知时能看到原因，减少只看到“成绩消失”的困惑。

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

该验收会覆盖发布成绩、撤回成绩、撤回原因落入响应、撤回通知绑定答卷且包含原因、撤回后学生成绩隐藏。

## 后续建议

- 把 `score_release.note` 展示到考试管理页的成绩发布状态详情中。
- 将成绩发布、撤回、操作者、原因、通知统计写入专门审计列表。
- 撤回后可自动阻止新的成绩申诉提交，并提示等待重新发布。
