# 在线考试系统重建开发文档

本目录是“智慧在线考试系统”从课程设计级项目升级为学院级真实可用平台的开发文档集。建设路径采用渐进重构：保留现有 Vue 3、Spring Boot 3、MySQL 技术栈，先修复考试闭环和工程底座，再扩展三端能力与高可用能力。

## 文档阅读顺序

1. [现状审计与重构边界](01-current-audit-and-boundary.md)
2. [领域模型与状态机设计](02-domain-model-and-state-machine.md)
3. [权限与三端菜单设计](03-permission-and-three-end-design.md)
4. [管理员端开发说明](04-admin-end-development.md)
5. [教师端开发说明](05-teacher-end-development.md)
6. [学生端开发说明](06-student-end-development.md)
7. [接口与数据库迁移说明](07-api-and-database-migration.md)
8. [测试与验收说明](08-test-and-acceptance.md)
9. [部署与运维说明](09-deployment-and-operations.md)
10. [上线迭代计划](10-sprint-roadmap.md)

## 总体目标

- 学院级使用规模：数千学生、数百并发考试。
- 三端闭环：管理员维护组织与规则，教师发布与批阅考试，学生完成作答与学习反馈。
- 高可用基线：考试过程不因刷新、短暂断网、单点服务重启而丢失作答。
- 高集成基线：题库、试卷、考试、监考、阅卷、成绩、错题、通知、AI 辅助均有明确数据流。

## 当前第一批落地

- 发布考试时后端写入可考试状态。
- 学生开考必须满足考试状态、开始时间、结束时间。
- 草稿保存只允许进行中的本人答卷。
- 交卷按试卷题目全量生成答题记录，未作答也留痕。
- 填空题先按精确答案自动判分，主观题进入人工阅卷。
- 阅卷分数不得超过题目分值。
- 作答页上报切屏、失焦、复制、粘贴、退出全屏等监考事件。

## 当前第二批落地

- 前端引入 `vue-router`，用 Router 接管 URL 和浏览器前进后退。
- `App.vue` 不再手写 `window.history` 和 `popstate` 分发。
- 增加基础数据子路径别名，例如 `/basic/notices`、`/basic/subjects` 归属到 `/basic/data` 菜单权限。
- 基础数据 tab 切换会同步 URL，刷新后能回到对应 tab。
- 管理员工作台“发布公告”等快捷入口不再因为子路径未在菜单中显式授权而进入 404。

## 当前第三批落地

- 后端新增 `@RequireRoles` 统一权限注解，支持标注在 Controller 类或方法上。
- 新增 `RequireRolesAspect`，统一从 `AuthContext` 读取当前登录用户并进行角色校验。
- `UserController`、`RoleController`、`MonitorController` 已迁移为注解式权限样板。
- 系统用户管理、角色授权、监控日志、AI 日志和学生监考事件接口不再依赖 Controller 内散落的手写角色判断。
- 后续题库、试卷、考试、阅卷、学生接口可按同样方式逐批迁移，并在 Service 层保留数据范围校验。

## 当前第四批落地

- `QuestionBankController` 已迁移到类级 `@RequireRoles({"ADMIN", "TEACHER"})`。
- `PaperController` 已迁移到类级 `@RequireRoles({"ADMIN", "TEACHER"})`。
- `ReviewController` 已迁移到类级 `@RequireRoles({"ADMIN", "TEACHER"})`。
- `ExamController` 已按方法标注教师/管理员接口与学生作答接口的角色权限。
- 题库、试卷、考试、阅卷接口继续把当前用户传给 Service 层，保留原有创建者和授课范围数据隔离。

## 当前第五批落地

- `AiController`、`MaterialLibraryController`、`AnalysisController`、`StudentInsightController`、`NotificationController`、`StudentController`、`BasicDataController` 已完成 `@RequireRoles` 迁移。
- Controller 层不再残留 `RoleAccessService` 的手写登录/角色判断，页面级权限开始收敛为统一注解入口。
- 新增后端 `ErrorCode` 常量，`ApiResponse.ok`、全局异常处理、未登录过滤器不再散落硬编码错误码。
- `IllegalStateException` 统一返回 `INVALID_STATE`，用于考试状态、时间窗、已交卷、流程状态不允许等状态机拒绝。
- 前端请求层新增 `ApiError`，保留后端 `code` 与 HTTP `status`，为考试作答、草稿恢复、阅卷提交等页面提供精确错误处理基础。

## 当前第六批落地

- 新增 `score_release` 成绩发布状态表，并在旧库自愈迁移中自动补表。
- 教师/管理员端新增成绩发布与撤回接口：`POST /api/exams/{id}/scores/publish`、`POST /api/exams/{id}/scores/revoke`。
- 发布成绩前会校验没有待批阅答卷，且至少存在已完成答卷；发布后向学生发送站内通知。
- 学生端成绩列表和成绩详情必须匹配已发布记录，未发布或已撤回成绩不可见。
- 考试管理页增加“成绩发布”状态列和“发布成绩/撤回成绩”操作，成绩从自动可见改为显式发布闭环。

## 当前第七批落地

- 新增 `score_appeal` 成绩申诉表，并在旧库自愈迁移中自动补表。
- 学生端新增申诉接口：`GET /api/student/appeals`、`POST /api/student/appeals`，仅允许对已发布成绩提交申诉。
- 教师/管理员端新增申诉处理接口：`GET /api/reviews/appeals`、`POST /api/reviews/appeals/{id}/reply`，按管理员全局/教师授课范围过滤。
- 学生成绩详情页支持整卷申诉和单题申诉，并显示申诉处理状态与教师回复。
- 教师阅卷页新增“成绩申诉”处理区，支持按待处理/已回复/全部筛选并回复学生。
- 学生考试中心列表不再返回未发布分数，已完成但未发布的考试显示“待发布”，避免绕过成绩查询页泄露成绩。

## 当前第八批落地

- 新增 `system_config` 系统配置表，并在旧库自愈迁移中自动补表和默认配置。
- 新增管理员配置接口：`GET /api/system/configs`、`PUT /api/system/configs/{key}`，支持数字、布尔、文本类型校验。
- 管理员菜单新增“系统配置”，前端新增系统配置页，可按考试、监考、成绩、系统分类查看和编辑。
- 默认配置覆盖考试默认时长、最大考试次数、草稿保存间隔、监考事件上报间隔、成绩申诉开关、申诉窗口天数、维护模式预留开关。
- 成绩申诉流程已读取 `score.appealEnabled` 和 `score.appealWindowDays`，不再是硬编码申诉策略。

## 当前第九批落地

- 新增考试快照底座：`exam_candidate_snapshot`、`exam_question_snapshot`、`exam_question_option_snapshot`，并在旧库自愈迁移中自动建表。
- 发布/创建考试时固化考生范围、学生基础信息、试卷题目、题目分值和选择题选项，避免发布后班级或试卷调整影响已发布考试。
- 学生考试列表同步优先读取考生快照；已存在快照的考试不会再因为学生后来加入班级而自动获得考试资格。
- 学生开考时校验本人必须在该考试的考生快照内；作答页和提交判分优先读取题目快照。
- 阅卷详情、评分上限、学生成绩详情、知识点掌握度统计优先读取发布时的题目和分值快照。
- 修复系统配置页、系统配置 DTO、通用响应消息、学生服务数据库不可用提示中的编码/字符串损坏问题。

## 当前第十批落地

- 新增考试快照审计接口：`GET /api/exams/{id}/snapshot`，教师/管理员可查看某场考试发布时的考生快照与试卷题目快照。
- 考试管理页新增“快照”入口，使用抽屉展示考生数、题目数、总分、试卷名称、考生名单和题目明细。
- 快照审计接口支持历史数据兜底：若旧考试尚无快照记录，会从历史答卷与当前试卷结构生成只读展示结果。
- 清理考试 Controller 返回消息，避免接口成功提示继续出现乱码。
- 旧库自愈迁移切换到干净版 DDL/种子 SQL，避免被污染的中文注释导致 `score_release`、`score_appeal`、`system_config` 等表创建或默认配置回填失败。

## 当前第十一批落地

- `exam_attempt` 新增 `submit_type`、`submit_reason` 字段，记录 `MANUAL`、`TIMEOUT`、`FORCED` 等交卷来源。
- 学生交卷改为服务端校验考试状态、考试结束时间和个人作答时长；超过截止时间仅允许短暂网络宽限，超出后拒绝继续提交。
- 学生断线后长时间重进考试时，服务端会使用已保存草稿自动按超时交卷处理，避免前端倒计时被绕过。
- 草稿保存同样校验考试窗口和个人作答时长，超时后不再继续覆盖草稿。
- 新增教师/管理员强制交卷接口：`POST /api/exams/attempt/{attemptId}/force-submit`，强制交卷使用服务端草稿生成答卷，未保存内容按未答处理。
- 考试快照抽屉的考生表新增答卷状态和“强制交卷”操作，教师可对正在作答的学生执行强制交卷并刷新快照状态。

## 当前第十二批落地

- 新增学生作答心跳接口：`POST /api/exams/attempt/{attemptId}/heartbeat`，返回服务端剩余秒数、答卷状态和服务器时间。
- 学生作答页每 30 秒调用心跳接口，用服务端剩余时间校正本地倒计时，避免只依赖浏览器计时。
- 心跳发现答卷已提交或被服务端自动超时提交时，学生端会清理本地草稿并退出作答页。
- 修复“超时重进自动交卷”事务语义：不再通过抛运行时异常结束流程，改为返回 `autoSubmitted`，确保服务端自动交卷结果能够提交。
- 心跳接口同样具备超时兜底能力，超过服务端宽限窗口后会基于服务端草稿按 `TIMEOUT` 自动交卷。

## 当前第十三批落地

- 新增 `exam_monitor_session` 监考会话表，并在旧库自愈迁移中自动创建，聚合 attempt、考试、学生、在线状态、最后心跳、最后事件、事件数和风险分。
- 学生开始考试、作答心跳、手动/超时/强制交卷都会同步维护监考会话；异常事件上报会累计风险分，并记录最后事件类型和事件时间。
- 新增教师/管理员监考接口：`GET /api/monitor/exams/{examId}/sessions`，按考试返回学生在线/离线/已交卷状态；原 `GET /api/monitor/cheat-events/{attemptId}` 补充考试作用域校验。
- 监考权限沿用三端协同边界：管理员全局可看，教师仅能查看自己创建或授课范围可访问的考试，避免监考页面绕过数据范围。
- 新增前端“实时监考”页面 `/exam-monitor`，支持考试选择、10 秒自动刷新、在线/离线/已交卷/风险事件指标、会话明细、事件抽屉和强制交卷操作。
- 管理员和教师菜单、角色授权种子、角色管理权限分组均接入 `/exam-monitor`，监考入口不再停留在后端事件记录层。

## 当前第十四批落地

- 监考事件新增 `client_event_id` 和 `client_event_time`，旧库迁移会自动补列和索引；客户端事件 ID 用于批量重试幂等，避免重复累计风险分。
- 新增学生端批量上报接口：`POST /api/monitor/cheat-events/batch`，单次最多 100 条，服务端只对新事件写入 `cheat_event` 并更新 `exam_monitor_session`。
- 学生作答页重写为干净 UTF-8 版本，保留服务端倒计时、心跳、自动保存、离线草稿恢复、退出确认、历史保护和交卷闭环。
- 学生端监考事件从“即时单条上报”改为本地队列：事件进入 `localStorage` 队列，每 10 秒或累计 5 条批量上报，失败保留并在网络恢复后重试。
- 学生端新增 `NETWORK_OFFLINE`、`NETWORK_ONLINE`、`HEARTBEAT_FAILED` 等事件，断网时优先写本地草稿和监考队列，恢复后补发事件并尝试保存草稿。
- 教师实时监考页补充高风险人数指标，事件明细优先展示客户端发生时间，并翻译网络断开/恢复、心跳失败等新增事件类型。

## 当前第十五批落地

- 新增 `exam_monitor_action` 监考处置记录表，记录会话、答卷、考试、学生、处置类型、处置说明、处理人和处理时间，旧库启动时自动补表。
- 系统配置新增 `monitor.riskWarningThreshold` 和 `monitor.riskHighThreshold`，监考风险提示和高风险阈值不再硬编码在前端。
- 监考会话列表返回 `riskLevel`、阈值、最新处置类型、最新处置说明、处理人和处理时间，教师打开看板即可看到风险是否已被关注。
- 新增处置接口：`POST /api/monitor/sessions/{sessionId}/actions` 和 `GET /api/monitor/sessions/{sessionId}/actions`，支持 `WARN`、`ACKNOWLEDGE`、`FORCE_SUBMIT`、`NOTE`。
- 处置接口沿用监考数据范围：管理员可处理全局，考试创建者可处理整场考试；非创建者教师只能处理自己授课范围内学生的监考会话。
- 实时监考页重写为干净 UTF-8 版本，新增阈值提示、最新处置列、事件/处置双页抽屉、记录处置弹窗；强制交卷后会自动写入 `FORCE_SUBMIT` 处置记录。

## 当前第六十四批落地

- 监考事件入口新增服务端白名单，只允许切屏、失焦、复制、粘贴、退出全屏、网络断开/恢复、心跳失败等受控事件类型。
- 学生端监考事件上报必须携带 `clientEventId`，保证批量重试和网络恢复补发不会重复累计风险分。
- 事件类型在服务端统一规范化为大写，风险分只基于受控枚举计算，不再接受任意字符串以默认分进入监考会话。
- 前端监考 API 类型同步要求 `clientEventId` 必填，贴合学生作答页实际采集行为。
- attempt resilience 验收脚本新增 `-CheckMonitorEventDedup`，覆盖重复事件去重和非法事件拒绝。

## 当前第六十五批落地

- `FORCE_SUBMIT` 监考处置记录必须绑定真实强制交卷结果，只有答卷已提交且 `submit_type=FORCED` 时才能写入。
- 仍在作答、手动交卷、超时交卷的答卷不能被补写“强制交卷”处置，避免监考审计记录脱离真实答卷状态。
- 教师实时监考页移除普通处置弹窗里的“强制交卷”选项，真实强制交卷继续通过表格操作按钮执行。
- attempt resilience 验收脚本新增 `-CheckMonitorActionForceSubmitBinding`，覆盖“直接伪造强制交卷处置必须失败”。

## 当前第六十六批落地

- 新增 `POST /api/monitor/sessions/{sessionId}/force-submit`，由后端在同一事务里完成真实强制交卷和 `FORCE_SUBMIT` 处置记录。
- 新接口继续沿用 `ExamService.forceSubmitAttempt` 的管理员/考试创建者权限，不通过监考处置权限扩大强制交卷范围。
- 重试监考强制交卷时会复用已有 `FORCE_SUBMIT` 处置记录并返回 `actionAlreadyRecorded=true`，避免重复审计。
- 教师实时监考页强制交卷按钮改为调用事务型监考接口，不再前端串联两个接口。
- attempt resilience 验收脚本新增 `-CheckMonitorForceSubmitTransaction`，覆盖强制交卷结果、处置记录和重试幂等。

## 当前第六十七批落地

- 监考 `WARN` 处置写入后会向对应学生发送 `MONITOR_WARNING` 站内通知，教师端“提醒学生”不再只是本端留痕。
- 首次事务型 `FORCE_SUBMIT` 处置会向学生发送 `MONITOR_FORCE_SUBMIT` 通知；重试复用已有处置时不会重复发送。
- 监考处置返回数据新增 `notificationSent`，用于表达本次处置是否已触达学生。
- attempt resilience 验收脚本新增 `-CheckMonitorWarnNotification`，覆盖提醒学生处置、通知发送和学生端通知可见性。

## 当前第六十八批落地

- 学生作答页接入监考通知轮询，每 15 秒检查新的 `MONITOR_WARNING` 和 `MONITOR_FORCE_SUBMIT` 通知。
- 新监考通知会在考试页 header 下方显示提醒横幅，并弹出轻量提示，避免学生作答时错过教师提醒。
- 作答页不会自动标记通知已读，通知中心仍保留未读记录供学生考后回看。
- 离开考试时会清理监考通知轮询定时器，不影响草稿保存、心跳和监考事件上报。

## 当前第六十九批落地

- `notification` 表新增 `related_type`、`related_id`，旧库启动时自动补列和索引。
- 通知列表接口返回业务关联字段，`NotificationService.send` 保持旧调用兼容并支持写入关联对象。
- 监考提醒和强制交卷通知绑定到 `relatedType=EXAM_ATTEMPT`、`relatedId=attemptId`。
- 学生作答页优先按当前 `attemptId` 精确过滤监考通知，旧通知保留时间窗口兜底。
- `-CheckMonitorWarnNotification` 验收增强为同时校验通知可见性和关联字段准确性。
