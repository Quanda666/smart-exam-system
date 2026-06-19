# 299 登录安全审计并入统一日志

## Summary

本批把登录/认证安全事件接入管理员统一日志，补齐“个人只能看自己的最近登录记录，管理员可以审计全局认证风险”的缺口。

## Backend

- 账号密码登录失败会写入 `operation_log`，动作：`LOGIN_FAILED`。
- 邮箱验证码登录失败会写入 `operation_log`，动作：`CODE_LOGIN_FAILED`。
- 失败日志只记录账号/邮箱与失败原因，不记录密码或验证码。
- 新增管理员接口：
  - `GET /api/monitor/login-logs`
  - `GET /api/monitor/login-logs/export`
- 登录审计查询支持：
  - `logId`
  - `keyword`
  - `action`
  - `operatorId`
  - `success`
  - `startFrom`
  - `startTo`

## Frontend

- `SystemLog.vue` 新增 `Login Audit` 页签。
- 支持按日志 ID、关键词、动作、操作者 ID、成功/失败、时间范围筛选。
- 支持导出登录审计 CSV。
- 支持复制登录审计 ID 和统一审计深链：
  - `/monitor/logs?loginLogId=<id>`
  - `/monitor/logs?tab=login&logId=<id>`

## Quality Gates

- 后端门禁检查认证失败审计、管理员登录审计接口、服务层查询/导出。
- 前端门禁检查 API 类型、统一日志页签、深链、复制工具和导出入口。

## Notes

- 本批沿用 `operation_log` 作为底层审计源，不新建登录日志表。
- 成功登录保留现有日志写入路径，失败登录新增安全审计记录。
- 作弊/风控判定仍不在登录审计中自动做结论，管理员只看到可追踪的认证风险记录。
