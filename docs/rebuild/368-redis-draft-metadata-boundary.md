# 368. Redis Draft Metadata Boundary

## 背景

学生端草稿保存入口已经拒绝过长 `clientDraftId`，但 Redis 草稿回写路径仍会把缓存中的 `clientDraftId` 静默截断到 80 字符后写入数据库。正式考试系统中，草稿 ID 用于定位客户端保存批次和恢复链路，不应在后台回写时被悄悄改写。

## 本步改造

- `flushRedisDrafts` 批量回写路径改为调用 `normalizeClientDraftId`。
- `flushRedisDraftForAttempt` 单次提交前回写路径同样调用 `normalizeClientDraftId`。
- 缓存中出现非法 `clientDraftId` 时，删除该脏草稿、记录 skipped，不写入被截断的元数据。
- 批量回写额外拒绝非正数 `attemptId`，避免无效缓存触发数据库查询。
- 质量门新增 Redis 草稿元数据边界检查，防止重新引入 `trimToLength(...clientDraftId..., 80)`。

## 三端影响

- 学生端：正常草稿保存和恢复不受影响；异常客户端草稿 ID 不会被改写后保存。
- 教师端：交卷前草稿回写得到更一致的作答快照，减少排查草稿冲突时的歧义。
- 管理员端：草稿缓存监控中的 skipped 更准确反映非法缓存数据。

## 验收点

- Redis 批量回写和单次回写都不再截断 `clientDraftId`。
- 非法缓存元数据不会进入 `exam_answer_draft`。
- 合法草稿回写仍保持原有 revision 冲突保护和 Redis clean 标记。
