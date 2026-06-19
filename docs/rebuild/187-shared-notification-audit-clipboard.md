# 187. 通知审计 ID 复制工具收束

## 背景

教师端监考面板和管理员端通知审计表都支持复制通知审计 ID。两处最初各自实现了一套 `navigator.clipboard` 与 textarea fallback 逻辑，后续如果浏览器兼容策略或错误处理需要调整，容易出现行为分叉。

## 本批改动

- 新增 `frontend/src/utils/clipboard.ts`。
- 提供 `copyNotificationAuditIdToClipboard`，统一处理通知审计 ID 的空值、字符串化和复制。
- 保留底层 `writeClipboardText`，优先使用 `navigator.clipboard.writeText`，并保留 textarea 后备方案。
- 教师端 `ExamMonitorPanel` 改为调用共享工具。
- 管理员端 `SystemLog` 改为调用共享工具。
- 质量门禁改为检查共享工具和两处组件调用，避免重新引入重复实现。

## 三端协同影响

- 教师端：复制通知审计 ID 的交互不变。
- 管理员端：复制通知审计 ID 的交互不变。
- 学生端：不改变通知读取、考试作答或监考事件上报流程。

## 验收点

- 教师端复制最新通知 ID 与处置时间线通知 ID 仍可用。
- 管理员端通知审计结果行复制 ID 仍可用。
- 两处组件不再各自维护剪贴板 fallback 逻辑。
- 后续剪贴板兼容策略只需改共享工具。
