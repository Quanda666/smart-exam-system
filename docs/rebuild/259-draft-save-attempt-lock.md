# 259. 草稿保存与交卷串行化

## 背景

第 258 批把 `exam_attempt(exam_id, user_id, attempt_no)` 收紧为唯一身份后，下一处高风险点是学生端草稿保存。

原 `saveDraft` 先用普通查询判断 attempt 是否处于进行中，然后再写 `exam_answer_draft` 或 Redis 缓存。这个过程没有事务，也没有锁 attempt。真实考试中如果学生端自动保存、手动交卷、心跳超时交卷、教师强制交卷同时发生，可能出现：

- 保存请求先判断 attempt 仍在进行中；
- 交卷事务随后把 attempt 改成已提交/待批阅/已完成，并删除草稿；
- 保存请求最后继续写入草稿，导致已提交 attempt 遗留旧草稿。

这会污染学生端恢复作答、教师监考审计和管理员异常排查。

## 本批改动

- `saveDraft` 增加 `@Transactional`。
- `saveDraft` 进入后先调用 `loadAttemptForSubmit(... FOR UPDATE)` 锁定 attempt 行。
- 新增 `attemptDraftSaveOpen`，在锁内统一判断：
  - attempt 必须是进行中 `status = 1`；
  - 考试必须仍是已发布且未删除；
  - 考试开始时间必须已到；
  - 服务端截止时间不存在或仍大于当前时间。
- `loadAttemptForSubmit` 增加 `examStarted` 计算字段，供草稿保存和后续状态判断复用。
- 质量门新增检查，防止草稿保存退回无事务、无锁、先写后判的实现。

## 三端协同影响

- 学生端：自动保存与交卷按钮、心跳提交不会再交错产生“已交卷但仍写入草稿”的状态。
- 教师端：强制交卷与学生端自动保存串行，监考处置后的 attempt 不会被后续草稿保存重新污染。
- 管理员端：异常恢复、草稿缓存状态、已提交答卷审计可以继续把 submitted attempt 视为终态，不需要额外清理迟到草稿。

## 验收

- 并发保存草稿与提交答卷时，`saveDraft` 必须等待提交事务释放 attempt 锁。
- 提交完成后到达的保存请求返回 `saved=false`，不得写入 `exam_answer_draft` 或 Redis dirty draft。
- 未开始、未发布、已结束、已提交的 attempt 都不能保存草稿。
- 本批质量门必须覆盖事务、`FOR UPDATE` 锁、`attemptDraftSaveOpen` 和 `examStarted`。
