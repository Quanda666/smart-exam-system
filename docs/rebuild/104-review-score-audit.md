# 104. Review Score Audit

## Background

Batch 104 closes a gap in the review workflow: grading changes were persisted in `review_record` and `answer_record`, but teachers and administrators did not have a dedicated audit trail for each score decision.

This matters because review is a high-risk workflow. A real exam system must be able to answer who graded a question, when it was graded, what the old and new scores were, and whether the score stayed within the frozen paper score.

## Scope

- Add `review_score_log` as the immutable review grading audit table.
- Record one audit row for each answer reviewed in `ReviewService.submitReview`.
- Expose scoped teacher/admin APIs for listing and exporting score logs by attempt.
- Add a teacher-side Score Logs drawer in `ReviewPanel.vue`.
- Add quality-gate checks so the audit path is not removed silently.

## Backend Changes

- `schema.sql`
  - Adds `review_score_log`.
  - Captures attempt, answer record, question, exam, student, old score, new score, max score, comment, reviewer, and created time.
  - Adds indexes for attempt, answer, exam, and reviewer audit lookup.

- `DatabaseMigrationRunner`
  - Adds `ensureReviewScoreLogTable`.
  - Runs the migration during startup for old databases.

- `ReviewService`
  - Keeps existing score safety rules:
    - review payload must cover every pending answer for the attempt;
    - score cannot be negative;
    - score cannot exceed the frozen question max score.
  - Loads answer context before update.
  - Writes `review_score_log` inside the same transaction as `review_record` and `answer_record`.
  - Adds `listReviewScoreLogs`.
  - Adds `exportReviewScoreLogs`.
  - Reuses `requireReviewAccess`, so teachers only see attempts in their teaching scope and admins retain global audit access.

- `ReviewController`
  - Adds `GET /api/reviews/attempt/{attemptId}/score-logs`.
  - Adds `GET /api/reviews/attempt/{attemptId}/score-logs/export`.

## Frontend Changes

- `frontend/src/api/review.ts`
  - Adds `ReviewScoreLog`.
  - Adds `listReviewScoreLogs`.
  - Adds `exportReviewScoreLogs`.

- `ReviewPanel.vue`
  - Adds a `Logs` action beside pending review rows.
  - Opens a `Review Score Logs` drawer.
  - Displays time, question, answer record, old score, new score, max score, reviewer, and comment.
  - Supports CSV export.

## Three-End Collaboration

- Admin end:
  - Uses the same backend audit table as the source for future global review audit dashboards.
  - Can inspect scoped grading history through admin role access.

- Teacher end:
  - Reviews pending answers as before.
  - Can immediately open the attempt grading history after a review.
  - Can export attempt-level grading logs when handling disputes or internal checks.

- Student end:
  - No new score visibility is exposed.
  - The existing invariant remains: students only see scores after score release.
  - Review audit logs are not exposed to students directly; appeals can use them as teacher/admin evidence.

## Acceptance Points

- Submitting a valid review creates one `review_score_log` row per answer.
- Submitting an incomplete review is rejected before any grading audit rows are written.
- A score below 0 or above max score is rejected.
- Teacher/admin can list review score logs for an attempt within scope.
- Teacher/admin can export review score logs as CSV.
- Students cannot call the review audit APIs because the controller remains restricted to `ADMIN` and `TEACHER`.

## Follow-Up

- Add a global administrator review-audit page under system logs.
- Add re-review and arbitration events to the same audit stream once double-review workflow is introduced.
- Add integration tests for review audit transaction rollback.
