# 241. Insight Answer Stats Expected Set

## Goal

Keep teacher-facing student insight answer-completeness statistics aligned with the exam's authoritative question set. A student's released score history should not show inflated answered counts because of stale or out-of-scope `answer_record` rows.

## Scope

- Update `StudentInsightService` exam history `answeredCount` to count distinct answered question IDs inside `exam_question_snapshot`.
- Fall back to `paper_question` only for legacy exams without question snapshots.
- Make `unansweredCount` subtract the scoped answered count from the same snapshot-first total.
- Keep score-release, finalized-attempt, scored-attempt, and open-recheck visibility gates unchanged.
- Add quality-gate anchors for expected-set joins and distinct answer counting.

## Three-End Coordination

- Teacher end: student insight drawers now use the same answer-completeness semantics as monitor and score exports.
- Student end: no behavior change in this batch; student-facing result statistics will be tightened separately.
- Admin/audit end: exported grade evidence and teacher insight history now agree on scoped answer counts.

## Acceptance Notes

- Insight score history still excludes unreleased, unscored, non-final, and open-recheck attempts.
- Current-paper fallback remains only for exams without a question snapshot.
- This batch changes statistics only; it does not change scores or visibility.
