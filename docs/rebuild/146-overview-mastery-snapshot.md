# 146. Overview Mastery Snapshot

## 背景

学生端 `getKnowledgePointMastery` 已经改为优先使用 `exam_question_snapshot.knowledge_point_id`。
继续检查首页/概览后发现，`OverviewService` 的知识点薄弱项仍然通过实时题库 `question.knowledge_point_id` 统计。

这会造成同一个学生在不同入口看到的知识点掌握度不一致，也会让教师后续修改题库知识点影响历史考试分析。

## 本批改动

- `OverviewService`
  - 首页知识点掌握度统计增加 `exam_question_snapshot` 关联。
  - 有考试题目快照时使用 `exam_question_snapshot.knowledge_point_id`。
  - 没有快照的旧数据才回退到 `question.knowledge_point_id`。
  - 保留成绩发布、答卷完成和开放复核申诉排除规则。
- `scripts/run-quality-gates.ps1`
  - 锁定概览知识点统计必须关联 `exam_question_snapshot`。
  - 锁定知识点分组必须优先使用快照知识点。

## 验收重点

- 学生首页/概览的知识点薄弱项与学生成绩/错题链路使用同一套历史快照原则。
- 教师编辑题库知识点后，不应影响已有考试快照的首页掌握度统计。
- 未发布成绩、未最终完成答卷、处于复核中的答卷仍不得进入概览统计。

## 后续建议

- 后续把教师端学情分析中的更多题目维度统计统一抽为“发布成绩可见数据集”查询，减少各服务重复写发布/复核门禁。
