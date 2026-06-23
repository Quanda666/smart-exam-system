# 364. Attempt ID Boundary

## 背景

考试尝试 ID 是学生端作答、草稿、心跳、提交，以及教师/管理员强制交卷的共同入口。非法的 `attemptId` 如果进入数据库查询，会扩大接口探测面，也会让错误语义从“参数非法”退化成“记录不存在”。

## 本步改造

- `ExamService` 新增 `requirePositiveAttemptId`，统一拒绝 `null`、0 和负数。
- 学生端入口 `startExam`、`saveDraft`、`submitExam`、`attemptHeartbeat` 在数据库读取前校验 `attemptId`。
- 教师/管理员入口 `forceSubmitAttempt` 在权限和数据库读取前校验 `attemptId`。
- 底层 `loadAttemptForSubmit` 再次校验，兜住内部调用和后续新增调用点。
- 质量门新增尝试 ID 边界检查，防止后续接口绕过该校验。

## 三端影响

- 学生端：候考、作答、草稿保存、心跳和交卷对非法尝试 ID 返回明确错误。
- 教师端：监考强制交卷不能用非法尝试 ID 触发数据库查询或权限判断。
- 管理员端：全局监控/运维操作调用强制交卷时获得一致的参数边界。

## 验收点

- 非正数 `attemptId` 在访问 `exam_attempt` 前被拒绝。
- 已有合法考试流程不受影响。
- 所有共享尝试入口都使用同一个服务层校验方法。
