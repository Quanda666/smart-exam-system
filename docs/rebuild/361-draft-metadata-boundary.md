# Step 361 - Draft Metadata Boundary

## 背景

学生端草稿保存已经支持本地草稿、服务端草稿、Redis 缓存、数据库回写和 stale revision 冲突保护。第 360 步收紧了草稿题号边界，本次继续收紧草稿元数据：`revision` 和 `clientDraftId`。

此前服务层会把负数 `revision` 静默压成 `0`，也会把过长 `clientDraftId` 截断。正式考试系统不应悄悄改写客户端元数据，否则排查草稿冲突、恢复链路和审计日志时会出现难以解释的状态。

## 本次改动

- `DraftRequest.answers` 增加最大长度约束，避免异常超大草稿请求进入解析和缓存链路。
- `DraftRequest.clientDraftId` 增加最大长度约束，保持和数据库/缓存保存口径一致。
- `DraftRequest.revision` 增加 `@PositiveOrZero`，负修订号在 HTTP 入参层被拒绝。
- `ExamController.saveDraft` 启用 `@Valid`。
- `ExamService.saveDraft` 改为调用 `normalizeDraftRevision` 和 `normalizeClientDraftId`。
- 服务层保留 `revision == null` 自动生成修订号的兼容行为，但拒绝负数修订号和过长客户端草稿 ID。
- 质量门禁新增结构断言，确保草稿元数据边界持续存在。

## 协同意义

- 学生端：草稿冲突恢复依赖稳定的修订号，不会把负数修订号伪装成最旧草稿。
- 教师端：阅卷和监考不会受到异常草稿元数据间接影响，作答状态更可信。
- 管理员端：草稿缓存状态和异常恢复排查可以基于未被服务端静默改写的元数据。

## 验收点

- `revision < 0` 的保存草稿请求被拒绝。
- `clientDraftId` 超过 80 字符的保存草稿请求被拒绝。
- 超大 `answers` 字符串在请求层被拒绝。
- `revision == null` 仍可保存草稿，并由服务端生成当前时间修订号。
- 合法草稿保存、stale conflict、Redis 写入和数据库回写行为不变。
