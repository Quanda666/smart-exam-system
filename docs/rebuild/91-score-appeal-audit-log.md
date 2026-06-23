# 91. 成绩申诉生命周期审计日志

## 背景

成绩申诉已经具备提交、教师回复、需要复核、复核完成等状态流转。但此前这些动作只体现在 `score_appeal` 当前状态字段中，缺少 append-only 操作流水。真实考试系统需要能复盘“谁在什么时候做了什么处理、状态如何变化、处理说明是什么”。

## 本次改动

- 新增 `score_appeal_log` 审计表。
- 新库 `schema.sql` 直接创建该表。
- 旧库启动迁移自动创建该表。
- 在 `ScoreAppealService` 中记录三类动作：
  - `SUBMIT`：学生提交申诉，`status_to = 0`。
  - `REPLY`：教师处理申诉，`status_from = 0`、`status_to = 1`，记录 `handling_result` 和处理意见。
  - `CLOSE_RECHECK`：教师完成复核，`status_from = 1`、`status_to = 2`，记录复核说明。
- 考试清理流程删除相关 `score_appeal_log`，避免测试/验收环境残留孤立日志。

## 表字段

- `appeal_id`：申诉 ID。
- `attempt_id`：答卷 ID。
- `exam_id`：考试 ID。
- `question_id`：申诉题目 ID，整卷申诉为空。
- `user_id`：申诉学生。
- `action`：动作类型。
- `status_from` / `status_to`：状态变化。
- `handling_result`：结构化处理结果。
- `note`：本次动作说明。
- `actor_id`：动作执行人。

## 验收点

- 学生提交申诉会写入 `SUBMIT` 日志。
- 教师回复申诉会写入 `REPLY` 日志。
- 教师完成复核会写入 `CLOSE_RECHECK` 日志。
- 审计日志为追加写入，不覆盖历史记录。
- 本地质量门禁通过。

## 后续

- 增加教师端单条申诉审计抽屉。
- 增加管理员端全局成绩申诉审计页，支持按考试、学生、教师、动作、时间范围过滤。
