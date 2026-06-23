# 128. Start Submitted Replay

## Problem

`startExam` threw an exception when the attempt status was already submitted or
finalized. The frontend treated that as an entry failure and left the exam page,
but it did not get the idempotent submit result and could leave local draft or
submit-token state behind.

For a real exam system, re-entering a submitted attempt should be a recoverable
state confirmation, not an exceptional path.

## Change

- `startExam` now returns `submittedAttemptResult` for `status >= 2`.
- The returned payload includes `submitted = true` and `remainingSeconds = 0`.
- `ExamTaking` treats `submitted`, `alreadySubmitted`, `autoSubmitted`, or
  `status >= 2` start responses as successful submission cleanup.
- Local draft and submit token are cleared before leaving the exam page.

## Acceptance

- Refreshing or re-entering a submitted attempt no longer depends on the error
  path.
- Student-side local draft state is cleared after submitted replay.
- Submit replay remains score-redacted and keeps `scoreVisible = false`.
- Quality gates cover backend replay and frontend cleanup behavior.
