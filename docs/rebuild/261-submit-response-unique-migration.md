# 261. 提交响应快照唯一身份迁移

## 背景

学生交卷后，前端可能因为网络抖动、刷新、重复点击或重试队列再次调用提交接口。系统已经用 `exam_submit_response` 保存首次提交响应，重复提交时优先回放快照，避免学生端看到两套不一致的提交结果。

但旧库如果早期创建过 `exam_submit_response`，启动迁移只会 `CREATE TABLE IF NOT EXISTS`，不会给旧表补齐列、索引和 `uk_exam_submit_response_attempt`。一旦缺少唯一键，`storeSubmitResponse` 的 `ON DUPLICATE KEY UPDATE` 会失去数据库约束基础，可能形成同一 attempt 多条响应快照。

## 本批改动

- `ensureExamSubmitResponseTable` 现在不仅建表，还会补齐旧表字段：
  - `submit_token`
  - `submit_payload_hash`
  - `response_json`
  - `created_at`
  - `updated_at`
- 新增 `deduplicateExamSubmitResponsesBeforeUniqueIndex`：
  - 按 `attempt_id` 查找重复提交响应；
  - 优先保留有 `response_json` 的记录；
  - 再按 `updated_at`、`id` 保留最新快照；
  - 删除同 attempt 的其他响应记录。
- 迁移后补齐：
  - `uk_exam_submit_response_attempt(attempt_id)`
  - `idx_exam_submit_response_token(submit_token)`
  - `idx_exam_submit_response_hash(submit_payload_hash)`
- 质量门新增覆盖，确保旧库自愈迁移不被回退。

## 三端协同影响

- 学生端：重复提交、恢复提交结果、刷新后确认提交状态时，只会拿到一份权威响应快照。
- 教师端：监考强制交卷和学生重复提交不会制造多份提交响应审计记录。
- 管理员端：排查提交重试、网络异常和响应回放时，可以按 attempt 唯一定位提交响应。

## 验收

- 任意数据库启动后，`exam_submit_response` 必须存在 `uk_exam_submit_response_attempt`。
- 历史重复响应会在加唯一键前保留最新有效快照并删除重复行。
- 提交接口的响应回放必须继续基于 attempt 维度唯一快照。
- 质量门必须覆盖补列、去重临时表、唯一键和两个查询索引。
