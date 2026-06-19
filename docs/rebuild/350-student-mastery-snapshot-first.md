# Step 350 - Student Mastery Snapshot First

## 背景

学生成绩、错题本和学习反馈都必须基于发布考试时冻结的题目快照。此前成绩详情和错题本已优先使用 `exam_question_snapshot`，但知识点掌握度 `getKnowledgePointMastery` 仍然先无条件关联当前 `paper_question`。如果教师在考试发布后调整试卷题目，学生端掌握度可能漏算历史答题记录。

## 本次改动

- `StudentService.getKnowledgePointMastery` 改为构造统一 `expected` 题目集：
  - 已存在考试题目快照时，使用 `exam_question_snapshot` 中的题目、知识点和分值。
  - 没有快照的旧考试，才回退到当前 `paper_question` 和 `question`。
- 掌握度分母改为 `expected.score`，避免当前试卷分值变化影响历史结果。
- 知识点归属改为 `expected.knowledgePointId`，避免题目当前知识点调整污染已发布考试结果。
- 继续保留成绩发布、已最终出分、非复查中三项可见性约束。
- 质量门新增结构性断言，要求掌握度查询使用快照优先的 `expected` 题目集。

## 三端协同意义

- 教师端发布考试后，试卷快照成为学生成绩、错题和学习反馈的共同依据。
- 学生端看到的知识点掌握度与当次考试实际作答题目一致。
- 管理员端后续审计成绩争议时，学习反馈不会因为发布后的题库或试卷维护而漂移。

## 验收点

- 已发布考试存在快照时，掌握度不依赖当前 `paper_question`。
- 老数据没有快照时，仍可从当前试卷题目回退计算。
- 未发布、未出分、复查中的考试仍不会进入学生掌握度统计。
