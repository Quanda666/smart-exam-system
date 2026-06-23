# 143. Knowledge Mastery Snapshot

## 背景

学生端知识点掌握度原先通过 `question.knowledge_point_id` 统计。
这会导致一个严重的历史数据漂移问题：教师在考试结束后编辑题库知识点，学生过去考试的掌握度也会跟着变化。

正式在线考试系统中，成绩、错题、分析都应基于发布时快照，而不是实时题库。

## 本批改动

- `exam_question_snapshot`
  - 新增 `knowledge_point_id` 字段。
  - 新增 `idx_exam_question_snapshot_kp` 索引，支撑后续按知识点统计。
- `DatabaseMigrationRunner`
  - 新库建表时直接包含 `knowledge_point_id`。
  - 旧库启动自愈时为 `exam_question_snapshot` 补列和索引。
  - 历史快照回填时写入当前题库知识点，作为旧数据的兼容快照。
- `ExamService.createPaperSnapshot`
  - 发布考试生成题目快照时，冻结题目版本或当前题目的 `knowledge_point_id`。
- `StudentService.getKnowledgePointMastery`
  - 有题目快照时使用 `exam_question_snapshot.knowledge_point_id`。
  - 无快照的旧数据才回退到 `question.knowledge_point_id`。
  - 分组改为 `kp.id, kp.point_name`，减少实时题库字段对历史统计的影响。
- `scripts/run-quality-gates.ps1`
  - 增加 schema、迁移、发布快照和学生统计查询的静态约束。

## 验收重点

- 新发布考试的知识点掌握度必须来自发布时冻结的题目快照。
- 教师后续修改题库知识点，不应影响已有快照考试的学生知识点统计。
- 旧库升级后能自动补齐列、索引，并尽力回填历史快照知识点。
- 未发布成绩或处于复核中的成绩仍不得进入知识点掌握度统计。

## 兼容说明

历史考试如果在本批之前已经生成快照，系统只能用升级时的当前题库知识点做一次兼容回填。
从本批之后新发布的考试开始，知识点会在发布动作中真实冻结。
