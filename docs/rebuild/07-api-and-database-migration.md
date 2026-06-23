# 07 接口与数据库迁移说明

## API 规范

- 统一响应：`success`、`code`、`message`、`data`、`timestamp`。
- 统一分页：`page` 从 1 开始，`size` 最大 100，返回 `list`、`total`、`page`、`size`。
- 统一错误码：`UNAUTHORIZED`、`FORBIDDEN`、`BAD_REQUEST`、`INVALID_STATE`、`CONFLICT`、`NOT_FOUND`。
- 写接口必须幂等或明确不可重试，例如交卷重复调用返回已交卷状态。

## 新增接口组

- 考试生命周期：`publish`、`withdraw`、`close`、`cancel`、`sync-targets`。
- 作答会话：`enter`、`heartbeat`、`save-draft`、`submit`、`force-submit`。
- 快照查询：试卷快照、考生快照、答题明细。
- 成绩发布：发布、撤回、查询发布状态。
- 申诉：提交申诉、教师回复、管理员关闭。
- 监考：批量上报事件、教师查看事件统计、管理员导出审计。

## 数据库迁移工具

- 引入 Flyway，目录使用 `backend/src/main/resources/db/migration`。
- 新库只执行 Flyway，旧的 `schema.sql` 和 `data.sql` 逐步改为基线脚本。
- 每次结构变更一个迁移文件，命名如 `V20260616_001__exam_state_machine.sql`。

## 必增表

- `exam_paper_snapshot`: 考试发布时的试卷和题目快照。
- `exam_candidate_snapshot`: 考试发布时的考生快照。
- `score_release`: 成绩发布记录。
- `score_appeal`: 成绩申诉。
- `monitor_session`: 监考会话。
- `permission_action`: 操作权限。
- `file_resource`: 上传资料和附件元数据。
- `system_config`: 系统配置。

## 必改表

- `exam`: 明确 `status` 枚举、发布人、发布时间、关闭人、关闭原因。
- `exam_attempt`: 增加服务端截止时间、最后心跳、作废原因、发布可见状态。
- `answer_record`: 增加题目序号、题型快照、最大分值、批阅人、批阅时间。
- `review_record`: 增加批阅轮次、评分来源、AI 建议引用。
- `cheat_event`: 增加事件等级、处理状态、处理人、处理意见。

