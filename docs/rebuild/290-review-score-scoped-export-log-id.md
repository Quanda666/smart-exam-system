# Step 290 - Review Score Scoped Export Log ID

## Background

Review score audit rows are now searchable by exact log ID and copyable from both administrator and teacher-facing views. The teacher scoped CSV export for a single attempt still omitted `review_score_log.id`, so exported evidence could not be directly matched back to a global audit deep link.

## Changes

- Added `Log ID` as the first column in `ReviewService.exportReviewScoreLogs`.
- Export rows now include `review_score_log.id`.
- Extended `scripts/run-quality-gates.ps1` so scoped review score exports must keep the audit ID.

## Acceptance

- Teacher scoped review score log CSV exports include the immutable audit log ID.
- Exported rows can be matched to `/monitor/logs?reviewScoreLogId=<id>`.
- Quality gates fail if the scoped export drops the log ID.
