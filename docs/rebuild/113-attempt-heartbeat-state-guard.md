# 113. Attempt Heartbeat State Guard

## Background

Draft saving already requires an active student attempt, a published exam, an undeleted exam, a valid exam window, and an unexpired duration. The remaining gap was heartbeat behavior: an in-progress attempt could keep refreshing its monitor session if the exam was no longer open because of a concurrent close, status change, or data repair path.

## Scope

- Keep draft save strict and unchanged.
- Make heartbeat honor the same exam-open invariant as submit.
- Avoid marking a closed or deleted exam as `ONLINE`.
- Finalize the active attempt from the best available draft when heartbeat discovers the exam is no longer open.

## Backend Contract

- `ExamService.saveDraft`
  - Requires `exam_attempt.status = 1`.
  - Requires the attempt owner.
  - Requires `exam.deleted = 0`.
  - Requires `exam.status = PUBLISHED`.
  - Requires the current time to be inside the exam window and duration.

- `ExamService.submitExam`
  - Uses `attemptExamOpen`.
  - Rejects normal submission when the exam is deleted or no longer published.

- `ExamService.attemptHeartbeat`
  - Returns submitted state for already submitted or finalized attempts.
  - Requires an in-progress attempt.
  - Checks `attemptExamOpen` before touching the monitor session.
  - If the exam is no longer open, finalizes the attempt from the best draft as `FORCED`.
  - Only writes `ONLINE` heartbeat after the exam-open check passes.

## Three-End Collaboration

- Student end:
  - Receives `forcedSubmitted = true`, `autoSubmitted = true`, and `remainingSeconds = 0` when the exam is no longer open.
  - Should leave the taking screen and show the submitted/recovery state.
  - Distinguishes forced submit from timeout submit in the heartbeat message.

- Teacher end:
  - `closeExam` still actively finalizes in-progress attempts.
  - Heartbeat provides a second server-side safety net for concurrent or repaired states.

- Admin end:
  - Deleted, closed, rejected, or otherwise unpublished exams cannot keep producing online monitor heartbeats.
  - Monitor session state converges to submitted/finalized state through `finalizeAttempt`.

## Invariant

An active exam-taking heartbeat must never refresh an `ONLINE` monitor session unless:

```text
exam_attempt.status = 1
AND exam.deleted = 0
AND exam.status = PUBLISHED
```

If the attempt is active but the exam is no longer open, the server must finalize the attempt from the best draft instead of continuing the session.

## Quality Gate

`scripts/run-quality-gates.ps1` now checks that:

- `ExamService` has a shared `attemptExamOpen` guard.
- `attemptHeartbeat` uses the guard.
- Heartbeat has a forced-submit path for no-longer-open exams.
- Submit continues to reject normal submission when the exam is no longer open.
- `ExamTaking` recognizes `forcedSubmitted` heartbeat responses and does not show the timeout message for forced close.

## Acceptance Points

- Draft saving remains unavailable outside active, published exam windows.
- Heartbeat does not mark closed or deleted exams as online.
- Concurrent close/status-change cases converge to a finalized attempt.
- The monitor dashboard sees submitted/finalized state instead of stale online presence.
