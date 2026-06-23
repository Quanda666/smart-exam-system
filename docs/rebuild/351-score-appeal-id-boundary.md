# Step 351 - Score Appeal ID Boundary

## 背景

学生端成绩申诉是成绩发布闭环的一部分，后端必须把所有入口条件收紧到接口级。现有 `ScoreAppealService` 已校验本人答卷、成绩已发布、答卷已出分、申诉窗口、重复申诉、题目归属和复查状态，但请求 DTO 对 `attemptId`、`questionId` 只做了空值校验。

非正数 ID 虽然通常会在数据库查询中失败，但这会把参数错误混入业务状态错误，不利于接口契约、审计和后续自动化测试。

## 本次改动

- `ScoreAppealRequest.attemptId` 增加 `@Positive`，HTTP 入参必须为正数。
- `ScoreAppealRequest.questionId` 增加 `@Positive`，题目级申诉传值时必须为正数。
- `ScoreAppealService.submitAppeal` 增加 service 边界校验，避免内部调用绕过 Controller 的 Bean Validation。
- 质量门新增结构性断言，要求申诉入口在访问数据库前拒绝非正数答卷 ID 和题目 ID。

## 协同意义

- 学生端：错误输入会被稳定识别为参数错误。
- 教师端：不会收到由非法 ID 触发的噪声申诉。
- 管理员端：申诉日志与审计中只保留有效业务对象，降低追溯成本。

## 验收点

- `attemptId <= 0` 不能进入申诉查询和写入流程。
- `questionId <= 0` 不能进入题目归属查询。
- 合法的整卷申诉仍允许 `questionId = null`。
- 已有发布、出分、窗口期、复查和重复申诉约束不变。
