# 285 Review Score Audit Log ID Filter

## Background

Score release and score appeal audit now support exact lookup by audit log ID. Review score audit still relied on keyword, exam ID, reviewer ID, and time filters, which made it harder to verify a specific scoring change when administrators already had the concrete `review_score_log.id`.

## Changes

- Added optional `logId` filtering to `GET /api/monitor/review-score-logs`.
- Added the same `logId` filtering to `GET /api/monitor/review-score-logs/export`.
- Reused the shared review score audit filter builder so list and export stay consistent.
- Added `ReviewScoreAuditQuery.logId` to the admin frontend API client.
- Added a review score log ID input to the System Log Review Score Audit tab.
- Added a visible `Log ID` column to review score audit results.
- Added route hydration for `reviewScoreLogId`; `/monitor/logs?reviewScoreLogId=123` now opens the Review Score Audit tab and filters that record.
- Also accepts `logId` when the route tab is already `reviewScore`.
- Extended the quality gate with backend and frontend checks for the exact lookup contract.

## Three-End Coordination

- Administrator side can locate one exact grading score change for audit or support.
- Teacher side grading behavior is unchanged.
- Student side score visibility and appeal behavior remain unchanged.

## Acceptance

- Calling the list API with `logId=123` returns only `review_score_log.id = 123` when the row exists.
- Exporting with the same `logId` uses the same filter.
- Opening `/monitor/logs?reviewScoreLogId=123` selects the Review Score Audit tab, fills the ID filter, and loads page 1.
- Reset clears the log ID filter together with the other review score audit filters.
