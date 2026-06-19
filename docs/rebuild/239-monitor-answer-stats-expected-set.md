# 239. Monitor Answer Stats Expected Set

## Goal

Prevent monitor-session answer completeness from being inflated by stale or out-of-scope `answer_record` rows. The monitor dashboard should count answered questions only inside the exam's authoritative question set.

## Scope

- Keep monitor `questionCount` snapshot-first with legacy `paper_question` fallback.
- Change monitor `answeredCount` to count distinct answered question IDs only when they belong to `exam_question_snapshot`.
- Fall back to `paper_question` only for legacy exams without question snapshots.
- Make `unansweredCount` subtract the scoped answered count from the same snapshot-first total.
- Add quality-gate anchors for snapshot-first answered-count scoping.

## Three-End Coordination

- Teacher end: monitor answer completeness can no longer be inflated by stray answer rows.
- Admin/audit end: monitor CSV export uses the same scoped values as the live monitor table.
- Student end: answer submission and draft behavior are unchanged; this batch only changes monitor reporting.

## Acceptance Notes

- Risk events and monitor actions are unchanged.
- `answeredCount` counts distinct question IDs to stay defensive around historical duplicate rows.
- Current-paper fallback remains only for exams that have no published question snapshot.
