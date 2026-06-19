# 224. Insight Answer Stats

## Goal

Show submitted answer completeness in the teacher student-insight view. A teacher reviewing a student's score history should see whether each released score came from a complete or incomplete submission.

## Scope

- Add `questionCount`, `answeredCount`, and `unansweredCount` to `StudentInsightService` exam history rows.
- Prefer the published exam question snapshot for total question count, with the current paper question list as the compatibility fallback.
- Count answered questions from non-empty persisted `answer_record` rows.
- Extend the frontend insight API type.
- Display answer statistics in the student insight exam-history table.
- Include answer statistics in the local score-history CSV exported from the insight drawer.
- Add quality-gate anchors for backend SQL, API type, UI display, and CSV headers.

## Three-End Coordination

- Teacher end: student insight now shows `answered/total` and unanswered counts for each released exam.
- Student end: the same statistics are visible in the student's own released result view.
- Admin/audit end: CSV exports and monitor/review records keep the same answer-completeness vocabulary.

## Acceptance Notes

- Insight score history still only includes released, finalized, scored attempts.
- Open recheck appeals remain excluded from insight score history.
- Answer statistics come from server-side persisted records and exam snapshots, not frontend-local state.
