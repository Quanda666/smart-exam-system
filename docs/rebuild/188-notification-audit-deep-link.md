# 188. 通知审计深链预填

## 背景

教师端和管理员端已经支持复制通知审计 ID，管理员端也能规范化搜索 `Notification #123`。但跨端协作仍需要管理员手动进入日志页、切换 `Notification Audit` 页签并粘贴 ID。

## 本批改动

- 管理员日志页支持读取 URL query。
- `/monitor/logs?tab=notification&notificationId=123` 会自动切换到 `Notification Audit` 并填入 `123`。
- `/monitor/logs?notificationId=Notification%20%23123` 也会自动切到通知审计，并复用既有规范化逻辑提取 `123`。
- `tab` 只接受已知日志页签，未知值不改变默认入口。
- 质量门禁增加 `useRoute`、query hydrate、通知页切换和 ID 预填断言。

## 三端协同影响

- 教师端：仍可复制通知 ID；后续可把 ID 拼入管理员审计链接中。
- 管理员端：可通过 URL 直接定位通知审计上下文，减少手动切页和粘贴步骤。
- 学生端：不改变通知读取、考试作答或监考事件上报流程。

## 验收点

- 打开 `/monitor/logs?tab=notification&notificationId=123` 时，默认页签为 `Notification Audit`。
- `Notification ID` 输入框显示 `123`。
- 打开 `/monitor/logs?notificationId=Notification%20%23123` 时，同样预填为 `123`。
- 未知 `tab` 不会导致异常或错误页。
