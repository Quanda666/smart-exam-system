# 243. Review Answer Stats Expected Set

## Goal

Make review-workbench answer-completeness statistics use the same authoritative question set as monitor, score exports, teacher insight, and student results. Reviewers should not see inflated answered counts caused by stale or out-of-scope `answer_record` rows.

## Scope

- Update pending review list `answeredCount` to count distinct answered question IDs inside `exam_question_snapshot`.
- Update review detail header `answeredCount` with the same scoped logic.
- Fall back to `paper_question` only for legacy exams without question snapshots.
- Make `unansweredCount` subtract the scoped answered count from the same snapshot-first total.
- Add quality-gate anchors for expected-set joins and distinct answer counting.

## Three-End Coordination

- Teacher end: review queue and review detail now match monitor and insight answer-completeness semantics.
- Student end: no behavior change; result statistics are already aligned.
- Admin/audit end: score exports and review workbench now agree on scoped answer counts.

## Acceptance Notes

- Review membership and scoring behavior are unchanged.
- Pending review still depends on `answer_record.review_status = 0`.
- Current-paper fallback remains only for exams without a question snapshot.
