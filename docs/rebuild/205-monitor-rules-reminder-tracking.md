# 205. 规则提醒处置追踪

## 目标

教师端已经可以筛选缺失规则确认、发送规则提醒，并让学生端补记确认时间。本批次继续提升监考台的追踪能力：教师可以快速查看最近处置为规则提醒的会话，并导出同一范围的审计数据。

## 功能范围

- 监考会话筛选栏新增 `Latest action`。
- 支持按 `RULES_REMINDER`、`WARN`、`ACKNOWLEDGE`、`FORCE_SUBMIT`、`NOTE` 筛选最新处置。
- 监考指标区新增 `Rules reminders` 指标卡。
- 点击 `Rules reminders` 后自动应用 `latestActionType = RULES_REMINDER`。
- CSV 导出支持 `latestActionType` 参数。
- 导出文件名追加 `action_<type>` 片段，便于审计归档。

## 协同价值

- 教师端：发送规则提醒后，可以快速复查哪些学生最近被提醒过。
- 学生端：结合规则提醒通知直达和补记确认时间，形成“提醒 -> 学生确认 -> 教师复查”的闭环。
- 管理员端：导出的会话审计包含最近处置、通知状态、规则确认时间，可用于事后抽查。

## 验收要点

- `Latest action = Rules reminder` 时，列表只展示最近处置为规则提醒的会话。
- 点击 `Rules reminders` 指标卡后，自动切到该筛选。
- 重置筛选后，`Latest action` 回到 `All actions`。
- 导出时服务端按 `latestActionType` 过滤。
- 导出文件名包含 `action_RULES_REMINDER`。
