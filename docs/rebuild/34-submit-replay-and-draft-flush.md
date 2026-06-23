# 34. 提交响应回放与提交前草稿刷盘

## 目标

- 保证 Redis 写回模式下，学生点击交卷前当前 attempt 的 dirty 草稿会先落库。
- 保证学生端因网络抖动重复提交时，同一个 `submitToken` 可以拿到第一次提交的响应快照。
- 避免重复提交、刷新确认、移动网络重试导致学生端看到不一致的提交结果。

## 后端改造

- 新增 `exam_submit_response` 表：
  - `attempt_id` 唯一，绑定一次作答。
  - `submit_token` 记录学生端提交幂等 token。
  - `submit_payload_hash` 记录提交答案规范化后的 SHA-256。
  - `response_json` 保存首次提交返回给前端的响应快照。
- 旧库启动自愈迁移新增 `ensureExamSubmitResponseTable`，生产升级时自动补表。
- `submitExam` 在真正判分前调用单 attempt Redis 草稿刷盘：
  - 只处理当前 attempt。
  - 只处理 Redis payload 中 `dirty=true` 的草稿。
  - Redis revision 小于 DB revision 时跳过并计入 `flushSkipped`。
  - 成功刷盘后将 Redis payload 标记为 clean。
- `finalizeAttempt` 完成判分、状态更新和草稿清理后，会保存提交响应快照。
- 已提交 attempt 再次调用提交接口时：
  - token 相同：优先回放 `exam_submit_response.response_json`。
  - token 缺失：兼容旧客户端，尽量按 attempt 回放已有响应。
  - token 不同：返回当前 attempt 简版结果，并标记 `submitTokenMismatch=true`。

## 前端配合

- 学生作答页已经持久化 `submitToken` 到 `sessionStorage/localStorage`。
- `submitExam` 响应类型补充：
  - `submitPayloadHash`
  - `responseReplayed`
  - `submitTokenMismatch`
  - `draftFlushedBeforeSubmit`
- 当前页面仍按原成功提示处理，后续可以把 `responseReplayed` 用于更明确的恢复提示。

## 协同效果

- 学生端：刷新、重复点击、弱网重试时不会生成第二份答卷，也不会因为响应丢失而误以为未交卷。
- 教师端：阅卷和成绩统计只基于第一次成功提交的答题记录。
- 管理员端：后续可在运维面板中按 `submit_payload_hash`、`submit_token` 排查重复提交与异常恢复。

## 下一批建议

- 增加考试作答链路的端到端测试：
  - Redis 写回草稿保存。
  - 点击交卷前强制刷盘。
  - 首次提交响应丢失后的同 token 重试。
  - 不同 token 重复提交的风险标记。
- 管理员草稿缓存卡片增加 dirty 阈值告警和最近刷盘时间。
