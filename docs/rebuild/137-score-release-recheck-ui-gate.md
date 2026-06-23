# 137. Score Release Recheck UI Gate

## Problem

Batch 135 made the backend reject score publishing while
`RECHECK_REQUIRED` appeals are still open. The teacher/admin exam list did not
surface that blocker, so the publish button could still look available until
the API rejected the action.

Real exam workflows should expose the same readiness state across the backend
state machine and the operating UI.

## Change

- `ExamService.listTeacherExams` now returns `openRecheckAppealCount`.
- `frontend/src/api/exam.ts` adds the field to `ExamInfo`.
- `ExamManagement.vue` disables score publishing when
  `openRecheckAppealCount > 0`.
- The disabled reason tells the operator that score recheck appeals must be
  completed first.
- Quality gates now require backend exposure and frontend publish gating for
  open recheck appeals.

## Three-End Impact

- Admin/teacher end: score release readiness is visible before the publish API
  call.
- Student end: avoids contradictory release attempts while a student's recheck
  is still in progress.
- Audit end: score release logs remain cleaner because invalid publish attempts
  are discouraged at the UI layer as well as blocked by the backend.

## Acceptance

- Exam list rows include the open recheck appeal count.
- Publish scores is disabled when that count is greater than zero.
- Backend remains the source of truth and still rejects direct API calls while
  recheck appeals are open.
