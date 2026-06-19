# 260. 草稿表唯一身份迁移

## 背景

第 259 批已经把 `saveDraft` 放进事务，并在保存前锁住 `exam_attempt`。但服务层的草稿保存仍依赖 `exam_answer_draft(attempt_id)` 唯一键来执行 `INSERT ... ON DUPLICATE KEY UPDATE`。

新库 schema 已经有 `uk_draft_attempt`，但旧库如果早期创建过 `exam_answer_draft`，启动迁移只补列和普通索引，不一定会补唯一键。这样旧库上可能出现同一个 attempt 多条草稿，导致：

- 自动保存 upsert 退化成普通插入；
- 恢复草稿时读到非预期版本；
- 提交后清理与迟到保存更难判断权威草稿。

## 本批改动

- 启动迁移先兜底创建 `exam_answer_draft` 表，保证 `DB_INIT_MODE=never` 或旧库缺表时也能自愈。
- 新增 `deduplicateExamAnswerDraftsBeforeUniqueIndex`：
  - 按 `attempt_id` 识别重复草稿；
  - 保留 `revision` 最大、`updated_at` 最新、`id` 最大的草稿；
  - 合并 `saved_count`，保留最早 `created_at`；
  - 删除同 attempt 的其他草稿。
- 迁移完成后补 `uk_draft_attempt(attempt_id)`。
- 质量门新增检查，覆盖 schema、建表、去重临时表和唯一键添加。

## 三端协同影响

- 学生端：恢复作答始终只有一份权威草稿，旧库不会因为多条草稿导致恢复错版本。
- 教师端：强制交卷后清理草稿更可靠，监考处置不会被同 attempt 多草稿干扰。
- 管理员端：草稿缓存状态、异常恢复候选和数据清理统计可以按 attempt 维度稳定计数。

## 验收

- 任意数据库启动后，`exam_answer_draft` 必须存在 `uk_draft_attempt`。
- 历史重复草稿会在加唯一键前归并。
- `saveDraft`、Redis 写回、提交前 flush 均可继续依赖 attempt 维度 upsert。
- 质量门必须防止迁移和唯一键被回退。
