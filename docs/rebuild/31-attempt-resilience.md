# 31. 考试作答侧高可用基础

## 目标
- 提升学生考试作答链路在刷新、断网、重复点击、接口重试场景下的可靠性。
- 草稿保存支持幂等与旧版本保护。
- 交卷支持提交 token，重复提交返回既有结果，不再把学生推入错误状态。

## 数据库改造
- `exam_attempt` 新增：
  - `last_heartbeat_at`：最后一次作答心跳。
  - `last_draft_saved_at`：最后一次草稿保存时间。
  - `draft_version`：服务端确认的草稿版本。
  - `submit_token`：学生端提交 token。
  - `submit_payload_hash`：提交答案快照 hash。
- `exam_answer_draft` 新增：
  - `client_draft_id`：客户端草稿会话 ID。
  - `revision`：客户端草稿版本。
  - `saved_count`：服务端接受保存次数。
  - `created_at`：草稿创建时间。

## 后端改造
- `POST /api/exams/attempt/{attemptId}/save`
  - 请求支持 `clientDraftId`、`revision`。
  - 服务端只接受不低于当前版本的草稿。
  - 旧 revision 不覆盖新草稿，返回 `stale=true`。
  - 返回 `serverRevision`、`savedAt`、`clientDraftId`。
- `POST /api/exams/attempt/{attemptId}/submit`
  - 请求支持 `submitToken`。
  - 第一次提交写入 `submit_token` 和 `submit_payload_hash`。
  - 已提交状态再次调用时返回 `alreadySubmitted=true` 和既有状态。
- `POST /api/exams/attempt/{attemptId}/heartbeat`
  - 写入 `last_heartbeat_at`。
  - 返回草稿版本和最近保存信息，便于前端恢复。
- `startExam`
  - 返回草稿恢复元信息：`draftRevision`、`draftClientDraftId`、`draftSavedAt`、`lastDraftSavedAt`。

## 前端改造
- 学生作答页为每个 attempt 维护：
  - `clientDraftId`
  - `draftRevision`
  - `submitToken`
- 草稿保存：
  - 每次保存使用当前时间戳作为 revision。
  - 服务端返回旧版本冲突时，不覆盖服务器草稿，并提示学生。
- 交卷：
  - 提交前生成并持久化 `submitToken`。
  - 失败重试继续使用同一个 token。
  - 服务端返回 `alreadySubmitted` 时，按恢复成功处理。

## 协同价值
- 学生端：
  - 断网后本地草稿仍可恢复，联网后不会用旧草稿覆盖新草稿。
  - 连续点击交卷或网络重试不会造成重复答题记录。
- 教师端：
  - 阅卷数据来源更稳定，减少重复 answer_record 和异常提交。
- 管理员端：
  - 后续可基于 `last_heartbeat_at`、`last_draft_saved_at` 排查考试中断和网络异常。

## 后续建议
- Batch 32 可继续推进作答高可用：
  - 引入 Redis 草稿缓存，数据库作为最终落点。
  - 增加提交幂等记录表，支持更完整的 request/response 回放。
  - 增加学生端“最近保存时间/离线状态/恢复来源”的可视化。
