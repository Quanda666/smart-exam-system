# 283 Score Appeal Audit Log ID Filter

## Background

Score release audit already supports exact lookup by audit log ID. Score appeal audit still depended on keyword, action, handling result, and time range filters, which made support and review workflows slower when someone had a concrete score appeal audit row to verify.

## Changes

- Added optional `logId` filtering to `GET /api/monitor/score-appeal-logs`.
- Added the same `logId` filtering to `GET /api/monitor/score-appeal-logs/export`.
- Reused the shared score appeal audit filter builder so list and export stay consistent.
- Added `ScoreAppealAuditQuery.logId` to the admin frontend API client.
- Added a score appeal log ID input to the System Log score appeal audit tab.
- Added a visible `Log ID` column to score appeal audit results.
- Added route hydration for `scoreAppealLogId`; `/monitor/logs?scoreAppealLogId=123` now opens the score appeal audit tab and filters that record.
- Also accepts `logId` when the route tab is already `scoreAppeal`.
- Extended the quality gate with backend and frontend checks for the exact lookup contract.

## Three-End Coordination

- Administrator side can locate one exact score appeal lifecycle log for review, support, or compliance checks.
- Teacher side score appeal handling remains unchanged.
- Student side appeal submission and score visibility behavior remain unchanged.

## Acceptance

- Calling the list API with `logId=123` returns only `score_appeal_log.id = 123` when the row exists.
- Exporting with the same `logId` uses the same filter.
- Opening `/monitor/logs?scoreAppealLogId=123` selects the score appeal audit tab, fills the ID filter, and loads page 1.
- Reset clears the log ID filter together with the other score appeal audit filters.
