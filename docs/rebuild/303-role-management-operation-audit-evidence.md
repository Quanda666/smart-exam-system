# 303 角色授权操作审计证据

## 背景

管理员端已经能在用户管理中拿到操作日志 ID，但角色授权属于更高风险的权限变更，保存页面权限后也必须给出可追溯证据。

## 本步范围

- 后端 `PUT /api/system/roles/{roleCode}/pages` 在保存角色页面权限后写入 `operation_log`。
- 接口响应新增 `operationLogId`，供前端展示和复制。
- 前端 `RoleManagement.vue` 在保存成功后显示审计提示，支持复制审计 ID 和全局日志深链。
- 本地质量门禁新增角色授权审计证据检查，防止后续回归。

## 协同关系

- 管理员端：角色授权变更后立即获得证据 ID，可交叉核验到系统日志。
- 系统日志：通过 `/monitor/logs?operationLogId=<id>` 精确定位该次授权变更。
- 权限底座：角色页面权限仍由 `MenuService.updateRolePages` 统一落库，审计只记录变更前后页面清单，不改变权限计算逻辑。

## 验收点

- 保存角色页面权限响应包含 `operationLogId`。
- `operation_log.action` 为 `UPDATE_ROLE_PAGES`，`target` 为 `ROLE#<roleCode>`。
- 角色管理页保存后出现审计提示。
- “Copy audit ID” 复制日志 ID，“Copy audit link” 复制可跳转到系统日志的深链。
- `scripts/run-quality-gates.ps1` 覆盖该能力。
