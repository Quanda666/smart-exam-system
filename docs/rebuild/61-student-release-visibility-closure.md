# 61. Student Release Visibility Closure

## 目标

本批补强成绩发布闭环中的学生端可见性。

系统已有 `score_release` 表，学生成绩列表和成绩详情已经要求成绩发布后才可见；教师发布成绩前也会校验是否仍有待阅卷答卷。但审计发现学生端仍存在侧向泄露：错题本、知识点掌握度、首页平均分、最高分和成绩趋势直接读取答题记录或错题表，可能在成绩未发布时提前暴露正确答案、解析或分数走势。

本批将这些学生可见数据统一绑定到“成绩已发布的已完成答卷”。

## 后端调整

涉及文件：

- `backend/src/main/java/com/smartexam/service/StudentService.java`
- `backend/src/main/java/com/smartexam/service/OverviewService.java`

### 学生成绩详情

保持原有约束：

- `getGrades` 必须 join `score_release sr ON sr.exam_id = e.id AND sr.status = 1`。
- `getExamResult` 必须 join `score_release`。
- 未发布成绩访问详情继续返回 `Score has not been released`。

### 错题本

调整前：

- 直接读取 `wrong_question_book`。
- 客观题交卷后即可能写入错题表，导致未发布成绩时也能看到题目答案和解析。

调整后：

- 改为从 `answer_record` 关联 `exam_attempt`、`exam`、`score_release` 计算。
- 只统计 `ea.status = 5` 且 `sr.status = 1` 的已发布答卷。
- 只展示 `review_status = 1` 且 `is_correct = 0` 的错题。

### 知识点掌握度

调整前：

- 直接基于该学生全部 `answer_record` 计算。

调整后：

- 必须关联 `score_release`，只使用已发布成绩的已完成答卷。

### 学生首页概览

以下字段改为只读取已发布成绩：

- `wrongQuestions`
- `avgScore`
- `bestScore`
- `scoreTrend`
- `knowledgePoints`

保留未开始/进行中考试数量和未完成考试列表的展示，因为它们不暴露成绩和答案。

## 验收脚本增强

涉及文件：

- `scripts/verify-attempt-resilience.ps1`
- `scripts/run-attempt-resilience-acceptance.ps1`

新增参数：

```powershell
-ExpectUnreleasedStudentInsightsHidden
```

用于一次性验收 fixture：

- 学生提交后，在成绩尚未发布时检查 `/api/student/grades` 不包含本次 `attemptId`。
- 检查 `/api/student/wrong-questions` 为空。
- 检查 `/api/student/mastery` 为空。

该参数适合与新建临时学生 fixture 搭配使用，避免历史已发布成绩影响判断。

示例：

```powershell
scripts\run-attempt-resilience-acceptance.ps1 -CheckForgedQuestionRejection -ExpectUnreleasedStudentInsightsHidden
```

## 三端影响

学生端：

- 成绩未发布时，不能通过成绩页、错题本、掌握度或首页趋势提前推断分数和答案。
- 成绩撤回后，相关学习反馈也会随发布状态隐藏。

教师端：

- 发布成绩动作成为学生学习反馈可见性的统一开关。
- 阅卷完成但未发布时，教师仍可检查数据，学生端不会提前暴露。

管理员端：

- 可通过验收脚本验证成绩发布策略是否真正闭环。
- 后续若增加系统配置化发布策略，可以沿用同一可见性边界。

## 验收标准

- Java 全量源码卫生检查通过。
- PowerShell 脚本语法解析通过。
- 完整本地质量门禁通过。
- 未发布成绩时，学生端成绩、错题、掌握度和首页成绩指标不暴露本次答卷数据。

## 后续增强

- 将教师端学生画像区分为“教师可见内部分析”和“学生已发布反馈”两类数据源。
- 给 `wrong_question_book` 增加来源考试或来源答卷字段，支持更精确的发布状态追踪。
- 为学生端发布可见性增加真实端到端测试：提交后不可见，发布后可见，撤回后再次不可见。
