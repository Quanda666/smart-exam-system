# 02 领域模型与状态机设计

## 核心领域

- 用户域：用户、角色、教师档案、学生档案、登录会话、登录日志。
- 教学域：学院、专业、年级、班级、课程、课程班、授课分配、学生选课。
- 题库域：题目、选项、知识点、题目版本、题目审核、AI 来源。
- 试卷域：试卷、试卷题目、试卷快照、组卷规则、总分。
- 考试域：考试任务、考试范围、考生快照、答卷、作答草稿、答题记录。
- 阅卷域：待阅卷题、评分量表、阅卷记录、复评记录、仲裁记录。
- 成绩域：成绩汇总、成绩发布、成绩撤回、成绩申诉、错题本。
- 监考域：监考会话、风险事件、事件统计、处理记录。

## 状态枚举

- `QuestionStatus`: `DRAFT` 草稿、`REVIEWING` 审核中、`PUBLISHED` 可用、`REJECTED` 驳回、`ARCHIVED` 归档。
- `PaperStatus`: `DRAFT` 草稿、`PUBLISHED` 可发布考试、`LOCKED` 已被考试引用锁定、`ARCHIVED` 归档。
- `ExamStatus`: `DRAFT` 草稿、`PUBLISHED` 已发布、`IN_PROGRESS` 进行中、`CLOSED` 已结束、`CANCELLED` 已取消。
- `AttemptStatus`: `NOT_STARTED` 未开始、`IN_PROGRESS` 作答中、`SUBMITTED` 已交卷、`REVIEWING` 待阅卷、`COMPLETED` 已评分、`INVALIDATED` 作废。
- `ReviewStatus`: `PENDING` 待批、`REVIEWED` 已批、`RETURNED` 退回、`ARBITRATING` 仲裁中。
- `ScoreReleaseStatus`: `HIDDEN` 未发布、`PUBLISHED` 已发布、`WITHDRAWN` 已撤回。

## 考试状态流转

- 教师创建考试为 `DRAFT`，补齐试卷、时间、规则、考生范围。
- 发布考试时生成考生快照和试卷快照，状态变为 `PUBLISHED`。
- 到达开始时间后系统视图显示 `IN_PROGRESS`，也可由定时任务物化状态。
- 教师提前结束或到达结束时间后进入 `CLOSED`。
- 未开始前可撤回为 `DRAFT`，已开始后只能关闭或取消并留审计记录。

## 答卷状态流转

- 发布考试生成 `NOT_STARTED` 答卷。
- 学生通过准入校验后进入 `IN_PROGRESS`，记录开始时间和监考会话。
- 交卷后客观题与填空题自动判分，存在主观题则进入 `REVIEWING`。
- 全部待阅卷题完成后进入 `COMPLETED`。
- 违规处理或管理员作废进入 `INVALIDATED`，必须记录处理人和理由。

## 分数可见性

- `AttemptStatus=COMPLETED` 只代表已评分，不代表学生可见。
- 学生端成绩查询必须同时满足成绩发布记录为 `PUBLISHED`。
- 发布可按考试整体、班级、个人三个粒度控制。
- 撤回成绩只影响可见性，不删除原始评分记录。

