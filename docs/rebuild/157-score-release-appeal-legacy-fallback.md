# 157. 成绩发布申诉阻断的历史数据回退

## 背景

第 156 批为 `score_appeal` 增加了 `exam_id` 快照字段。新提交的申诉会直接写入考试 ID，老库启动时也会从 `exam_attempt` 回填。

考虑到迁移期可能存在极少量历史脏数据或回填失败数据，本批让成绩发布阻断逻辑同时兼容两种来源：

- 优先使用 `score_appeal.exam_id`。
- 如果历史申诉的 `exam_id` 为空，则通过 `score_appeal.attempt_id -> exam_attempt.exam_id` 回退匹配。

## 本批改动

- 教师端考试列表的 `pendingScoreAppealCount` 增加历史回退匹配。
- 教师端考试列表的 `openRecheckAppealCount` 增加历史回退匹配。
- 发布成绩前的待处理申诉检查增加历史回退匹配。
- 发布成绩前的待复核申诉检查增加历史回退匹配。
- 质量门禁固定列表统计和发布检查必须包含 `attempt` 回退逻辑。

## 三端协同影响

- 学生端：无界面变化，提交新申诉仍会写入考试快照。
- 教师端：考试管理列表能更准确显示“待处理申诉”和“待复核申诉”阻断原因。
- 管理员端：后续全局考试监控读取这些计数字段时，不会因为迁移期空快照漏掉风险。

## 验收点

- 新申诉按 `score_appeal.exam_id` 被统计和阻断。
- 旧申诉即使 `score_appeal.exam_id` 为空，只要能通过 `attempt_id` 找到考试，也会被统计和阻断。
- 待处理申诉 `status = 0` 必须阻断成绩发布。
- 待复核申诉 `status = 1` 且 `handling_result = 'RECHECK_REQUIRED'` 必须阻断成绩发布。
