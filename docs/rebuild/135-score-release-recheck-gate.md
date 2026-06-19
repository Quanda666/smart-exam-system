# 135. Score Release Recheck Gate

## Problem

Student result queries already hide scores when an active appeal is marked
`RECHECK_REQUIRED`, but score publishing only checked exam end state and attempt
finalization.

That left a workflow mismatch: a teacher/admin could republish scores while a
recheck appeal was still open, sending release notifications even though the
student-visible score should remain blocked.

## Change

- `ExamService.requireExamReadyForScoreRelease` now rejects score publishing
  when the exam has any open `score_appeal` with:
  - `status = 1`
  - `handling_result = 'RECHECK_REQUIRED'`
- Quality gates now require the recheck gate as part of score release
  preconditions.

## Three-End Impact

- Teacher/admin end: must finish or close recheck appeals before publishing or
  republishing scores.
- Student end: remains aligned with the existing visibility rule; open rechecks
  do not trigger contradictory release notifications.
- Audit/operation end: score release logs stay meaningful because release
  actions represent a publishable score set.

## Acceptance

- Scores cannot be published while recheck appeals are open.
- Existing release visibility still requires released, finalized, and
  non-rechecking attempts.
