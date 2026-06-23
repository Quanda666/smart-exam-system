# 164. 监考处置权限按答卷归属加载

## 背景

第 163 批修复了 `exam_monitor_session.exam_id/user_id`。继续审计监考处置链路后，`loadSessionForAction` 虽然会关联 `exam_attempt`，但查询考试、学生、候选快照和权限判断时仍读取 session 快照字段。

如果 session 快照被历史数据或外部写入污染，教师/管理员执行警告、备注、强制交卷时，权限判断和通知对象可能跟着错。本批把运行时处置加载改为以答卷归属为准。

## 本批改动

- `loadSessionForAction` 查询返回的 `exam_id`、`user_id` 改为来自 `exam_attempt`。
- 考试表、学生表、考生快照关联都改为使用 `a.exam_id/a.user_id`。
- 监考处置权限、通知对象、处置记录写入继续使用同一个已解析 session map。
- 质量门禁：固定监考处置加载必须从 `exam_attempt` 读取考试和学生归属。

## 三端协同影响

- 教师端：监考处置按钮的权限判断基于真实答卷所属考试和学生。
- 学生端：监考警告/强制交卷通知会发给真实答卷学生。
- 管理员端：监考处置审计链路不会因为 session 快照脏数据扩大或错配权限。

## 验收点

- 处置会话加载时，`exam_id` 来自 `exam_attempt.exam_id`。
- 处置会话加载时，`user_id` 来自 `exam_attempt.user_id`。
- 候选快照查询使用答卷归属，而不是 session 快照。
- 后续 `insertMonitorAction` 仍会再次解析 action 归属，形成双保险。
