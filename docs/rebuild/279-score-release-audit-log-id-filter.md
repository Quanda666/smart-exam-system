# 279 Score Release Audit Log ID Filter

## Background

Step 278 made score publish and revoke APIs return `scoreReleaseLogId`, but administrators still had to search the audit table by exam name, paper name, operator, or time range. That weakened the traceability loop from a business operation result to the exact audit row.

## Changes

- Added optional `logId` filtering to `GET /api/monitor/score-release-logs`.
- Added the same `logId` filtering to `GET /api/monitor/score-release-logs/export`.
- Reused the shared score release audit filter builder so the list and CSV export stay consistent.
- Added `ScoreReleaseAuditQuery.logId` to the admin frontend API client.
- Added a score release log ID input and visible log ID column to the System Log score release audit tab.
- Added route hydration for `scoreReleaseLogId`; `/monitor/logs?scoreReleaseLogId=123` now opens the score release audit tab and filters that record.
- Also accepts `logId` when the route tab is already `scoreRelease`, for compact internal links.
- Extended the quality gate with backend and frontend checks for the new audit lookup contract.

## Three-End Coordination

- Teacher/admin score publish and revoke actions now return a concrete audit ID.
- Admin audit can locate that exact score release record through URL or manual filtering.
- Student score visibility remains unchanged; this step only improves administrator traceability.

## Acceptance

- Calling the list API with `logId=123` returns only `score_release_log.id = 123` when the row exists.
- Exporting with the same `logId` uses the same filter.
- Opening `/monitor/logs?scoreReleaseLogId=123` selects the score release audit tab, fills the ID filter, and loads page 1.
- Reset clears the log ID filter together with the other score release audit filters.
