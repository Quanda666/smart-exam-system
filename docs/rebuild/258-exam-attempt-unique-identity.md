# 258. 作答次数身份唯一化

## 背景

`exam_attempt` 表原来只有 `(exam_id, user_id, attempt_no)` 普通索引。学生进入考试、发布考试补齐考生作答模板、交卷后生成下一次作答机会时，服务层依赖 `INSERT ... WHERE NOT EXISTS` 防重。

这个写法在单请求下可用，但在真实在线考试场景里会有并发缝隙：同一学生多标签页、刷新重试、网络抖动重放、教师重新发布补齐模板任务同时运行时，两个事务可能同时判断“不存在”，最终写出重复作答次数。后续阅卷、成绩发布、申诉、监考会话都会被这类重复身份污染。

## 本批改动

- 新库 schema 将 `exam_attempt(exam_id, user_id, attempt_no)` 升级为唯一键 `uk_attempt_exam_user_no`。
- 启动迁移新增 `ensureExamAttemptUniqueIdentity`：
  - 先识别重复 `(exam_id, user_id, attempt_no)`；
  - 按状态、分数、提交时间、更新时间选择保留 attempt；
  - 将答题记录、阅卷记录、阅卷分数日志、提交响应、草稿、成绩申诉、申诉日志、监考事件、监考会话、监考处置记录迁移到保留 attempt；
  - 对一对一或唯一子表先删除重复快照，避免迁移引用时触发唯一键冲突；
  - 删除重复 attempt 后再添加唯一键。
- 服务层 `insertAttemptIfMissing` 与 `createNextAttemptIfAllowed` 改成 `INSERT ... ON DUPLICATE KEY UPDATE id = id`，让并发初始化变成数据库约束保护下的幂等写入。
- 质量门新增检查，防止唯一键、历史去重迁移和幂等插入被回退。

## 三端协同影响

- 学生端：同一考试同一作答次数只会对应一个权威 attempt，多标签页进入、恢复草稿、重复点击进入考试不会生成并行作答身份。
- 教师端：阅卷队列、成绩统计、重评/仲裁、成绩发布不再因为重复 attempt 把同一学生同一次考试算多份。
- 管理员端：考试监控、监考审计、异常告警、导出报表的 attempt 维度更稳定，后续容量监控和审计链路可以把 attempt 当作强身份。

## 仍需后续处理

- 迁移当前仍是应用启动自愈脚本，后续要落到 Flyway/Liquibase 版本化迁移。
- 旧库如果存在极端复杂的重复 attempt 冲突，本批以“保留最可信行并迁移证据”为准，后续可增加迁移审计表记录被归并的 duplicate id。
- 下一步可继续收紧考试开始、草稿保存、提交交卷的事务边界，让 attempt 唯一身份和状态锁形成完整闭环。

## 验收

- 新库 schema 中必须存在 `uk_attempt_exam_user_no`。
- 重复历史数据迁移后，`exam_attempt` 不应再存在相同 `(exam_id, user_id, attempt_no)` 多行。
- 同一学生同一考试并发初始化作答时，最终只产生一条目标 attempt。
- 质量门必须覆盖 schema、迁移函数和服务层幂等插入。
