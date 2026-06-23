# 316. Student Score Appeal Lifecycle Logs

## Context

Students can now submit score appeals, see the submit audit ID immediately, and copy the same submit audit ID from the appeal history table. The remaining student-side traceability gap was lifecycle visibility: after a teacher replies, opens a recheck, or closes the recheck, students could see the current appeal fields but not the ordered event trail.

The teacher workbench already uses `score_appeal_log` as the append-only lifecycle source. This step exposes a student-scoped read-only view of the same lifecycle, guarded by appeal ownership.

## Changes

- Added `GET /api/student/appeals/{id}/logs`.
- Added `ScoreAppealService.listMyAppealLogs(id, student)`.
- Student log access is constrained by `sa.id = ? AND sa.user_id = ?`.
- Shared the existing log query shape through `appealLogs(jt, id)`.
- Added frontend `ScoreAppealLog` and `getMyScoreAppealLogs(id)` to the student API client.
- Added a `Logs` action to the student result drawer appeal table.
- Added a read-only student log drawer showing:
  - log ID
  - action
  - status transition
  - handling result
  - note
  - time
- Each student-visible log row keeps copyable score appeal audit ID and deep-link actions.

## Three-End Coordination

- Student side: students can inspect the ordered lifecycle of their own appeal without using teacher routes.
- Teacher side: teachers continue to create the authoritative lifecycle rows when replying, opening recheck, and closing recheck.
- Administrator side: copied log links still land in the unified score appeal audit lookup by exact `scoreAppealLogId`.

## Acceptance Notes

- A student can list logs for an appeal they own.
- A student cannot list logs for another student's appeal because the access check includes `sa.user_id = currentStudentId`.
- The student log drawer uses the same `score_appeal_log` IDs that administrators can search globally.
- Static quality gates fail if the student endpoint, ownership check, student API client, or log drawer wiring is removed.
