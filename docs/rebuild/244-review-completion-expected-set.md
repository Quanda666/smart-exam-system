# 244. Review Completion Expected Set

## Goal

Make the manual-review completion decision use the same authoritative question set as review answer statistics. A stale or forged `answer_record` row outside the exam snapshot must not keep an attempt stuck in pending review, nor should it affect the recalculated attempt score.

## Scope

- Add a shared expected-answer scope condition in `ReviewService`.
- Apply that scope to the pending review queue membership query.
- Apply that scope to review detail answers and review submission validation.
- Recalculate attempt score from expected answers only after manual review.
- Count remaining pending review answers from expected answers only before moving the attempt to graded.
- Add quality-gate anchors so the raw unscoped pending-count query does not return.

## Three-End Coordination

- Teacher end: review queue, review detail, and post-review completion now agree on the same scoped answer set.
- Student end: grade visibility is less likely to be blocked by historical dirty answer rows after a teacher completes all real review items.
- Admin/audit end: score and review lifecycle state now align with the snapshot-first exam contract used by exports and monitor views.

## Acceptance Notes

- Exams with `exam_question_snapshot` use snapshot membership as authoritative.
- Legacy exams without snapshots still fall back to `paper_question`.
- Out-of-scope answer records remain in the database for audit/history, but they do not drive review completion or final score recalculation.
