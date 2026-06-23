# 147. Score Total Snapshot

## 背景

考试发布后已经冻结题目快照和题目分值，但部分成绩出口仍使用 `paper.total_score` 作为总分。
如果教师在考试结束后编辑试卷或分值，历史成绩导出和教师端学生画像会出现总分漂移。

真实考试系统中，学生分数和试卷总分必须来自同一份发布时快照。

## 本批改动

- `ExportService.examScoreSheet`
  - 成绩导出中的 `totalScore` 优先使用 `exam_question_snapshot.score` 汇总。
  - 旧数据缺少快照时回退 `paper.total_score`。
- `ExportService.studentScores`
  - 学生历史成绩导出同样优先使用考试题目快照总分。
- `StudentInsightService.studentInsight`
  - 教师端学生画像考试列表中的总分优先使用考试题目快照总分。
- `scripts/run-quality-gates.ps1`
  - 锁定成绩导出和学生画像必须使用快照总分优先、实时试卷总分兜底的策略。

## 验收重点

- 发布后的考试即使后续试卷分值变化，历史成绩导出的总分也不应变化。
- 教师端查看学生画像时，考试分数和总分来自同一份发布时快照。
- 旧数据没有题目快照时仍能回退显示 `paper.total_score`。
- 未发布成绩和开放复核中的答卷仍不得进入这些出口。

## 后续建议

- 后续可以在 `exam_question_snapshot` 上补充发布时 `total_score` 冗余字段，减少每次查询的子查询汇总成本。
