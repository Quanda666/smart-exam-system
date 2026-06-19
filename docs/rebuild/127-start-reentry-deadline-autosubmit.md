# 127. Start Reentry Deadline Auto Submit

## Problem

Batch 126 made `startExam` finalize active attempts before rejecting a closed exam
window. A smaller deadline edge remained: an already-started attempt that re-entered
after its final deadline but still within the submit grace window could avoid the
server-side reentry finalization path.

The submit API should keep a short grace window for network delay, but the start
API should not reopen the answering page after the deadline has reached zero.

## Change

- `finalizeAttemptOnStartIfClosedOrExpired` now finalizes re-entered active
  attempts when `secondsUntilDeadline <= 0`.
- The attempt is finalized from the best saved draft as `TIMEOUT`.
- The existing submit endpoint still keeps its `SUBMIT_GRACE_SECONDS` behavior
  for in-flight submit requests.

## Acceptance

- Re-entering an active attempt at or after the final deadline auto-submits from
  the saved draft.
- The student is not returned to the answer UI with `remainingSeconds = 0`.
- Submit requests can still use the server-side grace window.
- Quality gates distinguish start reentry finalization from submit grace.
