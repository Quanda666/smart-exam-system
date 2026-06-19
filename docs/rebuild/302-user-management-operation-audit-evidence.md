# 302 用户管理操作返回审计证据

## Summary

第 301 步让通用操作日志可以按 ID 精确定位和复制深链。本批继续把用户管理这类高风险管理员操作接入“操作完成即可拿到审计证据”的闭环。

## Backend

- `OperationLogService.record(...)` 从 `void` 调整为 `Long`。
- 日志写入仍然吞掉异常，不影响主业务流程。
- 写入成功时返回 `operation_log.id`。
- `UserController` 在以下操作响应中返回 `operationLogId`：
  - 新建用户
  - 编辑用户
  - 启用/禁用用户
  - 重置密码
  - 删除用户

## Frontend

- `admin.ts` 新增 `UserOperationResult`。
- 用户管理高风险操作响应类型补充 `operationLogId`。
- `UserManagement.vue` 新增最近操作审计提示条：
  - 展示最新 `operationLogId`
  - 批量操作展示审计日志数量
  - 可复制审计 ID
  - 可复制统一操作日志深链

## Audit Flow

1. 管理员执行用户操作。
2. 后端写入 `operation_log` 并返回 `operationLogId`。
3. 前端显示审计证据提示。
4. 管理员可复制 `/monitor/logs?operationLogId=<id>` 分享给其他管理员复核。

## Security Notes

- 重置密码操作只返回审计 ID，不返回新密码。
- 日志写入失败不会阻断用户管理主流程，但前端不会显示审计证据。
- 该批只补用户管理入口；后续可逐步扩展到角色、基础数据、题库、试卷等管理员高风险操作。

## Quality Gates

- 检查 `OperationLogService.record(...)` 返回生成 ID。
- 检查用户管理接口响应包含 `operationLogId`。
- 检查前端用户管理页展示并复制操作审计证据。

## Acceptance

- 管理员新建、编辑、启停、重置密码、删除用户后，页面显示审计提示条。
- 点击复制审计 ID 得到操作日志 ID。
- 点击复制审计链接得到可在 `SystemLog` 精确定位的链接。
- 完整质量门禁通过。
