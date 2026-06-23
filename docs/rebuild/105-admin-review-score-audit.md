# 105. Admin Review Score Audit

## Background

Batch 104 created attempt-level review score logs for teachers. Batch 105 promotes that audit trail into the administrator system log workbench so administrators can search and export grading changes globally.

This strengthens the formal exam workflow: review scoring is no longer only visible inside one pending review row. It becomes part of the administrative evidence chain used for internal audit, dispute handling, and quality checks.

## Scope

- Add administrator-only global review score audit APIs.
- Support keyword, exam, reviewer, and time filters.
- Export the filtered result set as CSV.
- Add a `Review Score Audit` tab to `SystemLog.vue`.
- Add quality-gate checks for backend API, frontend API, and system log UI integration.

## Backend Changes

- `MonitorController`
  - Adds `GET /api/monitor/review-score-logs`.
  - Adds `GET /api/monitor/review-score-logs/export`.
  - Both endpoints require `ADMIN`.

- `MonitorService`
  - Adds `getReviewScoreAuditLogs`.
  - Adds `exportReviewScoreAuditLogs`.
  - Adds `appendReviewScoreAuditFilters`.
  - Reads from `review_score_log`.
  - Joins exam, student, student profile, reviewer, and question data for audit context.
  - Export is capped at 5000 rows, matching existing global audit exports.

## Frontend Changes

- `frontend/src/api/admin.ts`
  - Adds `ReviewScoreAuditLog`.
  - Adds `ReviewScoreAuditQuery`.
  - Adds `listReviewScoreAuditLogs`.
  - Adds `exportReviewScoreAuditLogs`.

- `SystemLog.vue`
  - Adds `Review Score Audit` tab.
  - Supports keyword, exam ID, reviewer ID, and time range filtering.
  - Displays exam, student, student number, score change, reviewer, question, comment, and related IDs.
  - Supports CSV export through the same authenticated download helper as other audit tabs.

## Three-End Collaboration

- Admin end:
  - Can inspect grading decisions globally.
  - Can export filtered evidence for review quality inspection or disputes.

- Teacher end:
  - Continues to use the attempt-level `Logs` drawer from batch 104.
  - Teacher-scoped access remains on `/api/reviews/attempt/{attemptId}/score-logs`.

- Student end:
  - No direct access to grading audit logs is added.
  - Score visibility remains governed by score release status.
  - Appeals can be handled with administrator/teacher audit evidence without leaking unreleased score data.

## Acceptance Points

- Admin can page through global review score audit records.
- Admin can filter by keyword, exam ID, reviewer ID, and time range.
- Admin can export the filtered audit result set.
- Teacher/student roles cannot access global monitor review score audit APIs.
- Existing attempt-level teacher audit remains available.

## Follow-Up

- Add reviewer workload and score adjustment analytics.
- Add re-review and arbitration actions when double review is introduced.
- Add backend integration tests for role denial and filtered export correctness.
