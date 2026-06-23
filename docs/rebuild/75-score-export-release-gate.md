# 第 75 批：成绩单导出遵守发布状态

## 背景

学生端成绩、错题、掌握度和成绩详情已经被 `score_release.status = 1` 保护；成绩撤回后学生不可见，也会收到撤回通知。但教师/管理员端的整场成绩单导出此前仍然可以直接导出已完成答卷分数。

这会造成口径不一致：学生端已经不可见或成绩尚未发布时，教师仍能导出一份正式成绩单。真实考试系统里，成绩单导出属于成绩发布链路的一部分，必须服从发布状态。

## 本批改动

- `ExportService.examScoreSheet` 在导出前校验：
  - 当前考试存在 `score_release.status = 1`。
  - 未发布或已撤回时拒绝导出。
- 学生端可见性不变，继续由 `score_release` 控制。
- 考试管理页“成绩单”按钮仅在成绩已发布时可点击。
- 未发布或已撤回时，按钮禁用并提示：
  - 成绩未发布，不能导出成绩单。
  - 成绩已撤回，不能导出成绩单。
- `-CheckScoreReleaseVisibilityCycle` 验收增强：
  - 发布前导出成绩单必须失败。
  - 撤回后导出成绩单必须失败。
- 总质量门新增源码护栏，防止后端导出校验或前端禁用逻辑被回退。

## 三端协同影响

- 管理员端：成绩单导出与成绩发布状态一致，避免导出未发布或撤回后的成绩。
- 教师端：导出按钮状态直接反映发布状态，减少误操作。
- 学生端：学生端可见性和教师端导出口径保持一致。

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

人工验收建议：

1. 未发布成绩时，考试管理页成绩单按钮应禁用。
2. 发布成绩后，成绩单按钮可点击。
3. 撤回成绩后，成绩单按钮再次禁用。
4. 直接请求 `/api/exams/{id}/scores/export`，未发布或已撤回时应失败。

## 后续建议

- 将学生画像中的个人成绩导出也统一收敛到已发布成绩口径。
- 在导出文件中写入成绩发布状态、发布时间和导出人，用于审计。
- 增加成绩发布审计抽屉，展示导出次数和最近导出时间。
