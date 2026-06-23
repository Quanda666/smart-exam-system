# 第 76 批：学生画像与导出口径遵守成绩发布状态

## 背景

第 75 批已经让整场考试成绩单导出必须满足 `score_release.status = 1`。继续审计发现，学生画像和班级名单相关导出仍存在侧向泄露风险：

- 班级名单导出中的“已完成考试”和“平均分”直接统计 `exam_attempt.status = 5`。
- 学生画像列表中的完成次数、平均分直接统计已完成答卷。
- 学生画像详情和本地 CSV 导出的历次成绩直接读取已完成答卷。

这些数据虽然不一定出现在学生端成绩页，但教师/管理员端仍可能看到或导出未发布、已撤回成绩，和成绩发布状态机不一致。

## 本批改动

- `ExportService.classRoster`：
  - `completedCount` 只统计已发布成绩。
  - `avgScore` 只计算已发布成绩。
- `ExportService.studentScores`：
  - 个人成绩导出只导出已发布成绩。
- `StudentInsightService.listClassStudents`：
  - 学生列表完成次数和平均分只来自已发布成绩。
- `StudentInsightService.studentInsight`：
  - 历次成绩列表只展示已发布成绩。
  - 汇总统计 `count / avgScore / maxScore / minScore` 只基于已发布成绩。
- 总质量门新增源码护栏，确保导出服务和学生画像服务必须关联 `score_release.status = 1`。

## 三端协同影响

- 管理员端：班级名单导出和学生画像统计不再泄露未发布或已撤回成绩。
- 教师端：学生画像中的趋势、平均分、最高分、最低分与成绩发布状态一致。
- 学生端：学生端成绩可见性与教师端导出、画像统计口径保持一致。

## 验收方式

本地质量门：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

人工验收建议：

1. 创建一场考试并完成答卷，但不发布成绩。
2. 打开学生画像，该考试不应进入历次成绩和汇总统计。
3. 导出班级名单，完成次数和平均分不应包含未发布成绩。
4. 发布成绩后，上述数据才应出现。
5. 撤回成绩后，上述数据应再次消失。

## 后续建议

- 继续审计教师端分析看板、管理员概览等统计接口，统一只使用已发布成绩计算对外成绩指标。
- 为导出文件增加“统计口径：仅已发布成绩”说明行。
- 将所有成绩相关统计封装成统一查询 helper，减少各服务重复手写 `score_release` join。
