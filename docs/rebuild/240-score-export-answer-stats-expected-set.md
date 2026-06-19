# 240. Score Export Answer Stats Expected Set

## Goal

Keep released score CSV answer-completeness statistics aligned with the exam's authoritative question set. Exported audit evidence should not be inflated by stale, duplicated, or out-of-scope `answer_record` rows.

## Scope

- Update exam score sheet exports so `answeredCount` counts distinct answered question IDs inside `exam_question_snapshot`.
- Fall back to `paper_question` only for legacy exams without question snapshots.
- Apply the same scoped count to per-student score history exports.
- Make `unansweredCount` subtract the scoped answered count from the same snapshot-first total.
- Add quality-gate anchors for expected-set joins and distinct answer counting.

## Three-End Coordination

- Teacher end: downloaded grade sheets now match the same answer-completeness semantics used by monitor and submit replay.
- Admin/audit end: CSV exports are safer as formal evidence because stray answer rows cannot inflate completion.
- Student end: score visibility and result detail behavior are unchanged.

## Acceptance Notes

- Score exports still require released, finalized, scored attempts.
- Open recheck appeals remain excluded from exports.
- Current-paper fallback remains only for exams without a question snapshot.
