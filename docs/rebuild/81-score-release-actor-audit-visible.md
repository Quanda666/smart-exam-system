# 第 81 批：成绩发布与撤回操作者审计可见

## 背景

成绩发布/撤回已经有状态、时间和撤回原因，但教师/管理员在考试管理列表中仍看不到“谁发布”“谁撤回”。对于真实考试系统，成绩发布是敏感动作，审计信息不能只停留在数据库里。

## 本批改动

- `ExamService.listTeacherExams`
  - 关联 `score_release.published_by` 到 `sys_user`，返回：
    - `scorePublishedBy`
    - `scorePublishedByName`
  - 关联 `score_release.revoked_by` 到 `sys_user`，返回：
    - `scoreRevokedBy`
    - `scoreRevokedByName`
- `frontend/src/api/exam.ts`
  - `ExamInfo` 补充发布人、撤回人字段。
- `ExamManagement.vue`
  - 成绩发布状态 tooltip 增加：
    - 已发布：发布时间、发布人。
    - 已撤回：撤回时间、撤回人、撤回原因。
- `scripts/run-quality-gates.ps1`
  - 增加静态守护，要求后端列表暴露操作者字段，前端展示操作者字段。

## 三端协同影响

- 管理员端：能看到成绩发布和撤回的操作者，便于追责和审计。
- 教师端：多人协作阅卷/发布时，可以确认成绩状态由谁变更。
- 学生端：可见性不变，仍只通过正式发布状态查看成绩。

## 验收方式

本地质量门：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

人工验收建议：

1. 管理员或教师发布一场考试成绩。
2. 在考试管理列表悬停“成绩发布”状态，查看发布时间和发布人。
3. 撤回成绩并填写原因。
4. 再次悬停状态，查看撤回时间、撤回人和撤回原因。

## 后续建议

- 增加独立成绩发布审计抽屉，展示完整发布/撤回历史，而不只是当前 `score_release` 最新状态。
- 后续可将 `score_release.note` 拆分为 `publish_note` 和 `revoke_reason`。
