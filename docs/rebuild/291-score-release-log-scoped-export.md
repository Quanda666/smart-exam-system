# Step 291 - Score Release Log Scoped Export

## Background

Score release publish/revoke actions already produce audit log IDs, deep links, and a global administrator export. The exam management drawer still only listed a single exam's score release history on screen. Teachers and administrators could not export that scoped history directly for department review or incident evidence packages.

## Changes

- Added `ExamService.exportScoreReleaseLogs`.
- Added `GET /api/exams/{id}/score-release-logs/export`.
- The export reuses the existing exam ownership/data-scope check from score release log listing.
- Added frontend `exportScoreReleaseLogs`.
- Added an Export button to the score release record drawer in `ExamManagement.vue`.
- Added quality-gate checks for the backend CSV, controller route, frontend API, and drawer export control.

## Acceptance

- A teacher or administrator can export score release lifecycle logs for an accessible exam.
- The CSV contains log ID, time, exam, action, status transition, actor, visible attempt count, notification counts, and note.
- The log ID can be matched to `/monitor/logs?scoreReleaseLogId=<id>`.
- Quality gates fail if the scoped export route or frontend export action is removed.
