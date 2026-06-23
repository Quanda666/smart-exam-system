# 416. Student Recheck Evidence View

## Scope

This batch extends the appeal recheck evidence loop from the teacher workbench to the student result page.

Before this step, a teacher could inspect recheck readiness before closing an appeal, but the student only saw the reply, recheck note, and appeal logs. A closed recheck now has a student-facing evidence drawer with the reviewed answer scope and score audit IDs.

## Backend Changes

- Added `GET /api/student/appeals/{id}/evidence`.
- Added `ScoreAppealService.studentAppealEvidence(id, student)`.
- The endpoint reuses existing student ownership checks through `requireStudentAppealAccess`.
- Evidence is returned only when:
  - the appeal belongs to the current student
  - the appeal is `RECHECK_REQUIRED`
  - the appeal has been closed with status `2`
  - the attempt is finalized and still under an active score release
  - no open recheck appeal remains for the same attempt
- The response includes appeal context, recheck counts, score log count, recheck open time, appeal logs, and per-answer score evidence.

## Frontend Changes

- Added `getMyScoreAppealEvidence(id)` and typed evidence DTOs in the student API module.
- Added an `Evidence` action for closed recheck-required appeals in `StudentResultsPanel`.
- Added a `Recheck Evidence` drawer showing:
  - required, reviewed, pending, and score log counts
  - handling result, teacher reply, recheck note, opened time, and closed time
  - per-answer question, review status, score transition, review score audit ID, reviewer, review time, and stem
- Deep-linked appeal notifications now open the evidence drawer when the target appeal has closed recheck evidence; otherwise they keep opening the appeal log drawer.

## Coordination Impact

- Teacher side: the close action remains backend-authoritative and evidence-based from batch 415.
- Student side: closed rechecks now expose a clear, read-only explanation trail without exposing hidden scores during active recheck.
- Admin side: appeal logs and review score logs remain the audit source; this view only presents student-owned evidence.

## Acceptance Checks

- A student cannot fetch evidence for another student's appeal.
- A pending or open recheck appeal is rejected by the evidence endpoint.
- A revoked or not-yet-released result is rejected by the evidence endpoint.
- A closed recheck appeal returns per-answer review score log IDs when available.
- The student result page builds with the new evidence drawer and keeps the existing logs drawer.
