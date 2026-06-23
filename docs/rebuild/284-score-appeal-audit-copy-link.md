# 284 Score Appeal Audit Copy Link

## Background

Score appeal audit now supports exact lookup by `scoreAppealLogId`. Administrators still needed a one-click way to share a concrete appeal lifecycle audit row after locating it in System Log.

## Changes

- Added score appeal audit clipboard helpers:
  - `copyScoreAppealAuditIdToClipboard`
  - `copyScoreAppealAuditLinkToClipboard`
  - `buildScoreAppealAuditDeepLink`
- The deep link points to `/monitor/logs?scoreAppealLogId=<id>`.
- Reworked the System Log score appeal audit `Log ID` column into a stable ID cell.
- Added one-click copy actions for both the audit ID and the audit link.
- Reused the same ID-cell layout pattern as score release and notification audit rows.
- Extended the quality gate so copyable score appeal audit evidence remains part of the contract.

## Three-End Coordination

- Administrator side can copy and share exact score appeal lifecycle audit rows.
- Teacher side score appeal handling behavior is unchanged.
- Student side appeal submission and score visibility behavior are unchanged.

## Acceptance

- Score appeal audit rows show `#<id>` in the System Log tab.
- Clicking the ID copy action copies the raw score appeal audit ID.
- Clicking the link copy action copies a URL containing `scoreAppealLogId=<id>`.
- Opening the copied URL hydrates the score appeal audit filter and locates the same row.
