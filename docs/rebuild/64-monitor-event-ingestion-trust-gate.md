# 第 64 批：监考事件上报可信入口

## 背景

监考链路已经具备学生端事件采集、本地队列、批量上报、服务端去重、教师实时看板和处置记录。但事件入口仍需要进一步收口：服务端不能接受任意事件类型，也不能允许缺少 `clientEventId` 的事件绕过去重键重复累加风险分。

## 本批改动

- `CheatEventRequest` 增加字段级校验：
  - `eventType` 必填且最多 64 字符。
  - `extraInfo` 最多 1000 字符。
  - `clientEventId` 必填且最多 80 字符。
  - `clientEventTime` 最多 64 字符。
- `MonitorService.recordCheatEvents` 在写库前统一规范化事件：
  - `eventType` 会 `trim` 后转为大写。
  - 只允许 `VISIBILITY_HIDDEN`、`WINDOW_BLUR`、`COPY`、`PASTE`、`FULLSCREEN_EXIT`、`NETWORK_OFFLINE`、`NETWORK_ONLINE`、`HEARTBEAT_FAILED`。
  - 不支持的事件类型直接拒绝，不能以默认风险分进入会话统计。
  - `clientEventId` 为空会被拒绝，确保 `(attempt_id, client_event_id)` 幂等键有效。
  - `clientEventTime` 只接受 ISO offset、ISO instant 或本地日期时间格式，非法时间不再静默改成服务器当前时间。
- 风险分计算收敛到白名单：
  - 粘贴 8 分、复制 6 分、退出全屏 5 分、网络断开 4 分、切屏/失焦 3 分、心跳失败 2 分、网络恢复 1 分。
- 前端 `CheatEventPayload.clientEventId` 改为必填类型，与学生端实际采集逻辑一致。
- `verify-attempt-resilience.ps1` 新增 `-CheckMonitorEventDedup`：
  - 同一个 `clientEventId` 在同批上报两次时必须返回 `accepted=1`、`duplicates=1`。
  - 伪造 `SCREEN_RECORDING` 事件必须被服务端拒绝。
- `run-attempt-resilience-acceptance.ps1` 透传 `-CheckMonitorEventDedup`，便于一次性跑 disposable attempt 验收。

## 三端协同影响

- 学生端：继续由作答页生成 `clientEventId` 并批量上报，断网重试时不会重复累计风险。
- 教师端：实时监考看板只接收受控事件类型，风险分和最后事件类型更可信。
- 管理员端：后续审计和告警策略可以基于稳定事件枚举配置阈值，不再需要兼容任意字符串。

## 验收方式

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -AdminUsername admin `
  -AdminPassword your-password `
  -CheckMonitorEventDedup `
  -SkipSubmit `
  -CleanupAfterRun
```

完整质量门仍使用：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

## 后续建议

- 将监考事件枚举抽成公开 DTO 或 OpenAPI 枚举，避免前后端手写字符串漂移。
- 在教师监考看板增加事件类型筛选和风险阈值说明。
- 后续可为 `cheat_event` 增加服务端接收批次号，便于排查客户端重试和网络抖动。
