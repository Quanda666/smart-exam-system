# 426 Admin Action Center

## Scope

- 管理员首页新增全局 Action Center，与 424 教师待办中心、425 学生待办中心形成三端首页闭环。
- 后端在 `/api/overview/admin` 返回 `actionCenter`，不新增可写接口，不改变审批、阅卷、监考、成绩发布的后端状态机。
- 前端管理员仪表盘展示汇总计数和可点击待办行，业务待办进入现有路由，运维待办复用现有 ops drilldown 抽屉。

## Backend

- `OverviewService.adminOverview()` 新增 `actionCenter` 字段。
- 管理员待办聚合项：
  - 教师审核：待审核教师、已驳回教师跟踪。
  - 考试审批：待审批考试、超 SLA 审批、已过计划开考时间审批、发布前检查风险。
  - 阅卷积压：全局待阅卷考试、待阅卷答卷和待批题目数。
  - 运行监控：当前进行中的考试。
  - 运维事件：dirty Redis 草稿、stale DB 草稿、临近超时、超时仍答题、离线监考、高风险监考、今日强制交卷、不可钻取的 ops alert。
- 返回结构包含：
  - `total`
  - `pendingTeacherReviews`
  - `rejectedTeacherReviews`
  - `pendingApprovals`
  - `approvalOverdue`
  - `approvalStartPassed`
  - `pendingReviews`
  - `runningExams`
  - `opsAlerts`
  - `dirtyDrafts`
  - `staleDbDrafts`
  - `timeoutPressure`
  - `deadlinePassedActive`
  - `offlineMonitor`
  - `highRiskMonitor`
  - `forcedSubmitsToday`
  - `items`

## Frontend

- `AdminDashboard.vue` 新增管理员 Action Center 卡片。
- 汇总入口：
  - Teacher review -> `/system/users?role=TEACHER&status=0&teacherStatus=0`
  - Exam approval -> `/exam-approvals`
  - Approval risk -> `/exam-approvals`
  - Review backlog -> `/reviews`
  - Live monitor -> `/exam-monitor`
  - Ops alerts -> `/system/config`
- 明细行行为：
  - 带 `drilldownType` 的行打开 ops drilldown 抽屉。
  - 其他行跳转 `target`。
  - 空数据展示 `No open admin actions`。
- 加载时合并默认 `actionCenter` 和 `opsCapacity`，兼容旧接口或空库数据。

## Three-End Coordination

- 管理员端负责全局组织、审核、审批、运维和监控入口。
- 教师端负责自己授课范围内的阅卷、申诉、复核和成绩发布准备。
- 学生端负责考试准入、续考、成绩可见性、申诉状态和错题复练。
- 三端 Action Center 都是只读导航层：它们帮助用户发现待办，但所有真正状态变更仍由后端业务接口校验。

## Safety Invariants

- 管理员 Action Center 不返回学生作答内容、正确答案、解析或未发布成绩。
- 监考风险只作为风险记录和人工处理入口，不自动判定作弊。
- 成绩发布仍必须通过现有 readiness 和 publish 守卫。
- 审批、阅卷、复核、交卷、强制交卷仍受原接口状态机保护。

## Acceptance Checks

- `/api/overview/admin` 返回 `actionCenter` 且空库时结构稳定。
- 管理员首页可以看到 Action Center 汇总和待办行。
- 审批待办可跳到审批队列或指定考试审批。
- 阅卷积压可跳到 review workbench。
- 运维待办可打开现有 drilldown 抽屉并分页/导出。
- 前端构建通过，质量门禁通过。
