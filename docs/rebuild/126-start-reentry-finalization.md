# 126. Start Reentry Finalization

## Problem

`startExam` validated the current exam window before handling active attempts
that had already passed their deadline. If the global exam `end_time` had passed,
an in-progress attempt could be rejected with "exam is not open" before the
server finalized it from the saved draft.

That breaks the exam lifecycle: an attempt can remain in progress after the
exam is over, which blocks review, score publication, and student result
visibility.

## Change

- `startExam` now loads the attempt through the same locked submit query used by
  submit and heartbeat paths.
- Before rejecting the exam window, `startExam` checks active attempts for:
  - exam no longer open or unpublished;
  - final deadline passed beyond the submit grace period.
- Those attempts are finalized from the best saved draft as `FORCED` or
  `TIMEOUT` and returned as `autoSubmitted`.
- New attempts that never started are still rejected by the normal exam window
  validation.

## Acceptance

- Re-entering an already-started attempt after exam end finalizes it instead of
  leaving it stuck in progress.
- Re-entering after a teacher/admin closes the exam finalizes as forced submit.
- Not-yet-started attempts remain blocked when the exam is not open.
- Quality gates verify finalization happens before `validateExamWindow`.
