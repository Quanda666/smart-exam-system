# 281 Score Release Audit Copy Link

## Background

Score publish and revoke actions now produce a `scoreReleaseLogId`, and administrators can deep-link from exam management into the global score release audit page. The next traceability gap was evidence sharing: once an administrator found the audit row, there was no one-click way to copy the exact audit ID or URL for review, support, or compliance follow-up.

## Changes

- Added score release audit clipboard helpers:
  - `copyScoreReleaseAuditIdToClipboard`
  - `copyScoreReleaseAuditLinkToClipboard`
  - `buildScoreReleaseAuditDeepLink`
- The deep link points to `/monitor/logs?scoreReleaseLogId=<id>`.
- Reworked the System Log score release audit `Log ID` column into a stable ID cell.
- Added one-click copy actions for both the audit ID and the audit link.
- Reused the same ID-cell layout pattern as notification audit rows.
- Extended the quality gate so copyable score release audit evidence remains part of the contract.

## Three-End Coordination

- Administrator side can copy and share the exact score release/revoke audit row.
- Teacher side keeps its exam-local score release record view without global audit permissions.
- Student side remains unchanged; this step does not change score visibility or notifications.

## Acceptance

- Score release audit rows show `#<id>` in the System Log tab.
- Clicking the ID copy action copies the raw score release audit ID.
- Clicking the link copy action copies a URL containing `scoreReleaseLogId=<id>`.
- Opening the copied URL hydrates the score release audit filter and locates the same row.
