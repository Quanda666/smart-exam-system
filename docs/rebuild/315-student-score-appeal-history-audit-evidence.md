# 315. Student Score Appeal History Audit Evidence

## Context

Step 314 added immediate local audit evidence after a student submits a score appeal. That evidence is useful at submission time, but it disappears after refresh or later navigation. Students also need a durable way to find the audit row for an appeal already listed in the result drawer.

The existing score appeal audit model already records a `SUBMIT` lifecycle row in `score_appeal_log`. The missing piece was exposing that submit log ID in the appeal query and rendering it in the student history table.

## Changes

- `ScoreAppealService.baseAppealSelect()` now exposes the first `SUBMIT` log ID as `scoreAppealLogId`.
- The student result drawer appeal table now includes an `Audit` column.
- Each listed appeal with a submit audit row can copy:
  - the raw `scoreAppealLogId`
  - the global `/monitor/logs?scoreAppealLogId=<id>` deep link
- The quality gate now verifies both sides of the contract:
  - backend appeal list/detail queries include the submit audit log ID
  - student appeal history rows expose copyable audit ID and deep link actions

## Three-End Coordination

- Student side: appeal audit evidence is available after refresh, not only in the submission success banner.
- Teacher side: appeal handling remains unchanged; teachers still use the review workbench lifecycle log.
- Administrator side: copied links land on the unified score appeal audit lookup by exact log ID.

## Acceptance Notes

- `GET /api/student/appeals` rows include `scoreAppealLogId` when a submit log exists.
- The student result drawer shows `#<scoreAppealLogId>` in the appeal history table.
- Copying the row ID copies the raw audit ID.
- Copying the row link creates a global score appeal audit deep link.
