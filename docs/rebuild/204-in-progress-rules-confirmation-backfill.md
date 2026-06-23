# 204. 进行中答卷规则确认补记

## 目标

规则确认提醒已经能从教师端发出并落到学生考试中心。本批次补齐旧数据和异常状态的闭环：如果答卷已经处于进行中，但 `rules_confirmed_at` 仍为空，学生从规则提醒进入时也必须确认规则，并由后端补写服务端审计时间。

## 功能范围

- 后端 `startExam` 在 `status = 1` 且 `rules_confirmed_at` 为空时，若收到 `rulesConfirmed = true`，补写 `rules_confirmed_at`。
- 首次开考仍保持强校验：`status = 0` 必须传入 `rulesConfirmed = true`。
- 前端普通续答不打扰学生。
- 只有从规则提醒链接进入，且目标答卷缺少 `rulesConfirmedAt` 时，进行中答卷也会弹出规则确认框。

## 协同价值

- 教师端：对 `Missing rules` 会话发送提醒后，学生可以真正消除缺失确认状态。
- 学生端：旧答卷或异常恢复场景下，确认规则后继续作答，不需要人工后台修数据。
- 管理员端：`exam_attempt.rules_confirmed_at` 继续作为统一审计字段，不引入前端本地状态作为证据。

## 验收要点

- 新答卷首次开考未确认规则仍被拒绝。
- 进行中答卷普通续答不弹规则确认框。
- 进行中答卷从 `notice=rules` 链接进入且 `rulesConfirmedAt` 为空时，会弹规则确认框。
- 确认后再次调用 `startExam` 会补写 `rules_confirmed_at`。
