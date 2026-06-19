# 156. 成绩申诉考试快照字段闭环

## 背景

上一批已经要求成绩发布必须阻断待处理申诉和待复核申诉。发布动作是考试级操作，如果只依赖 `attempt_id` 再联查答卷，历史数据、异常数据或后续审计统计都更容易出现遗漏。

本批把 `score_appeal.exam_id` 固化为申诉创建时的考试快照字段，用于考试级发布阻断、审计查询和后续监控统计。

## 本批改动

- 新建库 schema：`score_appeal` 增加 `exam_id BIGINT NOT NULL`。
- 新建库 schema：增加 `idx_score_appeal_exam_status (exam_id, status, handling_result)`。
- 老库迁移：启动迁移时自动增加 `exam_id`，并从 `exam_attempt.exam_id` 回填。
- 老库迁移：为 `exam_id/status/handling_result` 增加查询索引。
- 业务写入：学生提交成绩申诉时，显式写入 `attempt_id` 对应的 `exam_id`。
- 质量门禁：检查 schema、迁移和申诉写入路径都包含考试快照字段。

## 协同关系

- 学生端：只能对已发布、已完成、已有分数的答卷提交申诉；提交时后端冻结考试 ID。
- 教师端：处理申诉和复核时继续以答卷、题目为明细，但考试级发布状态读取 `score_appeal.exam_id`。
- 管理员端：后续考试监控、申诉审计、异常统计可以直接按 `exam_id` 聚合，不依赖临时联查。

## 验收点

- 新提交申诉必须写入 `score_appeal.exam_id`。
- 老数据启动后应能从 `exam_attempt` 回填 `exam_id`。
- 发布成绩前，如果同一考试存在 `status = 0` 的申诉，必须拒绝发布。
- 发布成绩前，如果同一考试存在 `status = 1` 且 `handling_result = 'RECHECK_REQUIRED'` 的申诉，必须拒绝发布。
- 学生可见成绩仍必须满足已发布、已完成、已有分数、无待复核申诉四个条件。
