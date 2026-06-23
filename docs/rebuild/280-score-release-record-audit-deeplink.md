# 280 Score Release Record Audit Deeplink

## Background

The score publish and revoke workflow now returns a concrete `scoreReleaseLogId`, and the administrator System Log page can filter by that ID. The remaining gap was navigation: the exam management score release record drawer still showed local history only, so administrators had to manually copy the ID into the global audit page.

## Changes

- Added the score release log ID column to the exam management score release record drawer.
- Added an administrator-only audit action on each score release record row.
- The action routes to `/monitor/logs?scoreReleaseLogId=<id>`.
- The System Log page then hydrates the route query, switches to the score release audit tab, and filters the exact audit row.
- Extended the quality gate so the exam management drawer must keep the deep-link contract.

## Three-End Coordination

- Teacher side keeps the exam-local score publish/revoke record view.
- Administrator side can jump from the same business record into global score release audit.
- Student side remains unaffected; score visibility and notification behavior are unchanged.

## Acceptance

- Admin opens an exam's score release records and sees each record's log ID.
- Admin clicks the audit action and lands on the System Log score release audit tab.
- The target audit page is filtered by the selected `scoreReleaseLogId`.
- Teacher role does not receive the administrator-only global audit action.
