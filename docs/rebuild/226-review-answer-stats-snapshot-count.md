# 226. Review Answer Stats Snapshot Count

## Goal

Align review-workbench answer completeness with the published exam snapshot. Reviewers should see the same total-question count that students, monitor views, and exports use after an exam is released.

## Scope

- Update pending review list statistics to count questions from `exam_question_snapshot` first.
- Keep `paper_question` as a compatibility fallback for older exams without snapshots.
- Update review detail header statistics with the same count rule.
- Keep answered/unanswered counts based on non-empty persisted `answer_record` rows.
- Add quality-gate anchors for snapshot-first review statistics.

## Three-End Coordination

- Teacher end: pending review rows and review detail drawers now use snapshot-first question totals.
- Student end: result detail and score summary already use the published-paper context.
- Admin/audit end: review stats now align with monitor and export answer-completeness fields.

## Acceptance Notes

- Review scoring behavior is unchanged.
- Pending review membership still depends on `answer_record.review_status = 0`.
- Snapshot question count is authoritative when available; current paper question count is only a legacy fallback.
