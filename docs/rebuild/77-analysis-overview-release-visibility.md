# 第 77 批：分析与概览看板成绩指标遵守发布状态

## 背景

前几批已经让学生成绩、错题、画像、导出等路径逐步遵守 `score_release.status = 1`。继续审计发现，教师/管理员分析看板和概览首页仍有部分成绩衍生指标直接读取 `exam_attempt.score`：

- 全局分析页平均分、学科平均分、分数段统计。
- 教师分析页平均分、学科平均分、分数段统计。
- 管理员概览页考试通过率趋势。
- 教师概览页平均分、成绩分布。

这些指标虽然多出现在教师端或管理员端，但它们会影响对外展示、教学研判和管理决策。如果成绩尚未发布或已经撤回，继续进入看板会破坏“成绩发布才可见”的业务闭环。

## 本批改动

- `AnalysisService.overview`
  - `averageScore` 改为只统计已发布成绩。
  - `subjectStats.avgScore` 和同块 `attemptCount` 改为只统计已发布成绩下的完成答卷。
  - `scoreDistribution` 改为只统计已发布成绩。
- `AnalysisService.teacherOverview`
  - `averageScore` 改为只统计已发布成绩。
  - `subjectStats.avgScore` 和同块 `attemptCount` 改为只统计已发布成绩下的完成答卷。
  - `scoreDistribution` 改为只统计已发布成绩。
- `OverviewService.adminOverview`
  - `examTrend` 通过率趋势加入 `score_release.status = 1`，且仅统计 `ea.status = 5` 且 `ea.score IS NOT NULL` 的答卷。
- `OverviewService.teacherOverview`
  - `avgScore` 加入 `score_release.status = 1`，并收紧为最终完成状态 `ea.status = 5`。
  - `scoreDistribution` 加入 `score_release.status = 1`，避免未发布、撤回或空分答卷进入分数段。
- `scripts/run-quality-gates.ps1`
  - 增加 `AnalysisService` 和 `OverviewService` 的源码守护，防止成绩统计重新退回裸读 `exam_attempt.score`。

## 口径说明

本批只约束成绩衍生指标：

- 平均分、最高分、最低分、分数段、通过率、成绩趋势必须只来自已发布成绩。
- 作答数、考试数、待阅卷数、近期考试、运行中考试等流程运营指标不强制受成绩发布状态影响。

这样可以同时满足两类需求：考试管理仍能看到真实流程进度，成绩展示和教学分析不会泄露未发布或已撤回分数。

## 三端协同影响

- 管理员端：概览通过率趋势不再提前暴露未发布成绩，撤回成绩后趋势也会同步消失。
- 教师端：分析页和首页的均分、分布、学科统计与成绩发布动作保持一致。
- 学生端：学生侧可见成绩口径与教师/管理员侧统计口径继续靠拢，减少“学生看不到但教师看板已经统计”的割裂。

## 验收方式

本地质量门：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

人工验收建议：

1. 创建考试并完成一批答卷，不发布成绩。
2. 查看管理员分析页、管理员概览页、教师分析页、教师概览页，均分、分数段、通过率不应包含这场考试。
3. 发布成绩后，上述指标应纳入统计。
4. 撤回成绩后，上述指标应再次移除。
5. 同时确认考试数、作答数、待阅卷数、近期考试等流程指标仍按考试流程展示，不被成绩发布状态误伤。

## 后续建议

- 继续审计其它直接读取 `exam_attempt.score` 的服务，特别是排行、报告、班级统计和异步导出。
- 后续可以抽出统一的“已发布成绩查询视图/helper”，减少各服务重复拼接 `score_release` join。
- 增加真实接口级验收用例：发布前、发布后、撤回后三个阶段分别校验看板 JSON。
