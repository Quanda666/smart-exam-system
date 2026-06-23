# 202. 规则提醒通知学生端直达

## 目标

教师端已经可以对规则确认缺失的会话发送 `RULES_REMINDER`。本批次补齐学生端落地路径，确保学生点击通知后能直接定位到需要处理的考试，而不是只进入普通考试列表。

## 功能范围

- `MONITOR_RULES_REMINDER` 通知链接携带 `notice=rules` 和 `attemptId`。
- 通知铃铛改用 Vue Router 跳转，保留查询参数。
- 学生考试中心读取 `attemptId`，自动切换到目标考试所在分组。
- 目标考试行高亮展示。
- 当 `notice=rules` 时，考试中心展示规则确认提醒条，并提供 `Confirm rules` 操作。

## 协同链路

1. 教师在监考台筛选 `Missing rules`。
2. 教师点击 `Rules` 发送规则确认提醒。
3. 后端生成 `MONITOR_RULES_REMINDER` 通知，链接到 `/student/exams?notice=rules&attemptId=<id>`。
4. 学生点击通知后进入考试中心，系统自动定位目标考试。
5. 学生点击 `Confirm rules`，继续走原有规则确认弹窗和后端开考校验。

## 约束

- 通知跳转不绕过开考状态机。
- 学生仍必须通过原有规则确认弹窗和后端 `rulesConfirmed` 校验。
- 链接中的 `attemptId` 只用于前端定位，不作为权限凭证。

## 验收要点

- 点击规则提醒通知后，URL 保留 `notice=rules&attemptId=<id>`。
- 学生考试中心自动切到目标考试所属分组。
- 目标考试行有高亮。
- 顶部提醒条显示目标考试名称。
- 点击 `Confirm rules` 后仍弹出规则确认对话框。
