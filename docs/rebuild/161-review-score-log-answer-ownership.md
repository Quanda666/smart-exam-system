# 161. 阅卷评分审计日志答题记录归属修复

## 背景

第 160 批修复了 `review_score_log.exam_id`。继续审计后发现，阅卷评分日志同时保存 `attempt_id`、`answer_record_id`、`question_id`、`exam_id`、`user_id`。其中最权威的归属来源应是 `answer_record_id` 对应的答题记录，再通过 `answer_record.attempt_id -> exam_attempt` 推导考试和考生。

如果只修正 `exam_id`，历史脏数据仍可能让日志里的答卷、题目或学生字段与真实答题记录不一致，影响管理员阅卷审计、导出、成绩复核和申诉追踪。

## 本批改动

- 启动迁移：按 `review_score_log.answer_record_id -> answer_record -> exam_attempt` 修正 `attempt_id`、`question_id`、`exam_id`、`user_id`。
- 写入路径：`recordReviewScoreLog` 写日志前重新解析 `answer_record` 所属答卷、题目、考试和考生。
- 保留评分快照：`old_score`、`new_score`、`max_score`、`comment`、`reviewer_id` 仍记录当次阅卷动作本身，不被归属修正覆盖。
- 质量门禁：固定运行时必须解析 `answer_record`，固定迁移必须同步四个归属字段。

## 三端协同影响

- 教师端：人工阅卷产生的日志绑定真实答题记录，后续复核时能追到正确题目。
- 管理员端：阅卷评分审计列表和导出不会出现同一日志的答卷、题目、学生互相不一致。
- 学生端：申诉和成绩复核引用的阅卷轨迹更可信，避免错题、错人、错考试的审计噪音。

## 验收点

- 历史日志的 `attempt_id` 会与 `answer_record.attempt_id` 保持一致。
- 历史日志的 `question_id` 会与 `answer_record.question_id` 保持一致。
- 历史日志的 `exam_id`、`user_id` 会与 `exam_attempt` 保持一致。
- 新写入阅卷评分日志时，归属字段从 `answer_record` 重新解析。
- 阅卷分数、评语和阅卷人仍保留当次操作快照。
