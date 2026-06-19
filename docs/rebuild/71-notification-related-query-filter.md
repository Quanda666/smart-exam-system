# 第 71 批：通知按业务对象精确查询

## 背景

第 69、70 批已经让监考、成绩发布、成绩申诉通知写入 `related_type` 和 `related_id`。但查询接口仍然只能按“我的通知”分页拉取，作答页为了找当前答卷的监考提醒，需要先取最新通知再在前端过滤。

本批补上查询侧能力，让通知的写入和读取都能围绕业务对象闭环。

## 本批改动

- `/api/notifications/my` 新增可选查询参数：
  - `type`
  - `relatedType`
  - `relatedId`
- 旧调用保持兼容，不传参数时仍返回当前用户的全部通知分页。
- 后端 `NotificationService.myNotifications` 新增动态 where：
  - 永远限定 `user_id = 当前登录用户`。
  - 可叠加 `type = ?`。
  - 可叠加 `related_type = ?`。
  - 可叠加 `related_id = ?`。
- 前端 `getMyNotifications` 新增可选 `NotificationQuery`，统一用 `URLSearchParams` 拼接查询参数。
- 学生作答页监考通知初始化和轮询改为请求：
  - `relatedType = EXAM_ATTEMPT`
  - `relatedId = 当前 attemptId`
- 总质量门新增源码护栏，确保：
  - Controller 暴露 `relatedType/relatedId` 参数。
  - Service 按 `related_type/related_id` 过滤。
  - 作答页按当前 `EXAM_ATTEMPT` 查询通知。

## 三端协同影响

- 学生端：考试中只查询当前答卷相关通知，降低误读其他考试通知的概率，也减少通知列表分页窗口带来的漏查。
- 教师端：监考提醒、强制交卷等通知已经和答卷绑定，学生端读取路径现在同样按答卷绑定。
- 管理员端：后续审计页面可以复用同一过滤能力，按答卷、申诉、成绩发布对象追踪通知送达链路。

## 验收方式

本地质量门：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

真实接口抽查示例：

```powershell
GET /api/notifications/my?page=1&size=10&relatedType=EXAM_ATTEMPT&relatedId={attemptId}
```

预期只返回当前登录学生名下、且绑定到该答卷的通知。

## 后续建议

- 通知中心页面增加筛选入口：通知类型、业务对象、未读状态。
- 为 `type` 支持多选或逗号列表，方便一次查询 `MONITOR_WARNING` 和 `MONITOR_FORCE_SUBMIT`。
- 后续引入 SSE/WebSocket 时，服务端可直接按 `relatedType/relatedId` 推送作答页需要的通知。
