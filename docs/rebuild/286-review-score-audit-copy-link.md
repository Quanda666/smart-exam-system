# Step 286 - Review Score Audit Copy Link

## Background

Step 285 added exact Review Score Audit lookup by `reviewScoreLogId`. Administrators could open a precise grading-score audit row, but the table still exposed the row ID as plain text only. That made evidence sharing weaker than score release and score appeal audit workflows.

## Changes

- Added Review Score Audit clipboard helpers:
  - `copyReviewScoreAuditIdToClipboard`
  - `copyReviewScoreAuditLinkToClipboard`
  - `buildReviewScoreAuditDeepLink`
- The deep link points to `/monitor/logs?reviewScoreLogId=<id>`.
- Updated the Review Score Audit Log ID column to show `#<id>` with copy-ID and copy-link actions.
- Reused the existing System Log ID-cell layout for a consistent administrator audit experience.
- Extended `scripts/run-quality-gates.ps1` so this traceability affordance cannot disappear silently.

## Acceptance

- Review Score Audit rows expose a copyable raw log ID.
- Review Score Audit rows expose a copyable deep link.
- Opening the copied link selects the Review Score Audit tab and filters to that row via the Step 285 route hydration.
- Quality gates verify the clipboard helpers, deep-link URL, row controls, and System Log wiring.
