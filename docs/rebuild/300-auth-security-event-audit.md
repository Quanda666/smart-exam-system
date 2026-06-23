# 300 认证安全事件审计扩展

## Summary

第 299 步把登录成功/失败接入管理员统一审计。本批继续补齐账号安全链路，把注册、退出、改密、验证码发送、邮箱绑定、资料更新等敏感认证动作写入同一认证审计目标，方便管理员从统一日志页追踪账号风险。

## Backend

- `AuthController` 新增统一辅助方法 `recordAuthSecurityEvent(...)`。
- 新增认证安全审计动作：
  - `REGISTER_REQUEST`
  - `LOGOUT`
  - `PASSWORD_CHANGED`
  - `LOGIN_CODE_SENT`
  - `BIND_CODE_SENT`
  - `EMAIL_BOUND`
  - `PROFILE_UPDATED`
- 继续沿用 `operation_log`，`target = 认证`。
- 失败登录仍使用：
  - `LOGIN_FAILED`
  - `CODE_LOGIN_FAILED`
- 审计详情不写入密码、验证码等敏感明文。

## Personal Login Logs

个人资料页的最近登录记录仍只展示真实登录动作：

- `登录系统`
- `验证码登录`

管理员统一登录审计页可以看到完整认证安全事件，个人最近登录不混入改密、绑邮箱或验证码发送记录。

## Frontend

- `SystemLog.vue` 的 `Login Audit` 搜索提示扩展为账号安全动作语义。
- 现有筛选和导出继续复用：
  - 日志 ID
  - 关键词
  - 动作
  - 操作者 ID
  - 成功/失败
  - 时间范围

## Quality Gates

- 检查认证安全动作必须写入审计。
- 检查认证审计不直接读取或记录登录密码。
- 检查个人最近登录查询只保留登录动作，避免混入账号安全事件。

## Acceptance

- 管理员能在 `Login Audit` 页签搜索到改密、绑邮箱、验证码发送等认证安全事件。
- 普通用户个人资料页的最近登录列表仍只显示登录记录。
- 验证码、密码不会出现在审计详情中。
