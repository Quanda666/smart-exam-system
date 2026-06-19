# 256. Exam Close Transition Lock

## Goal

Make exam close deterministic when teachers or administrators close the same published exam concurrently.

## Scope

- Route `closeExam` through the locked exam status check.
- Keep the existing idempotent behavior: closing an already closed exam returns without rewriting state.
- Preserve the rule that only published exams can be closed.
- Keep active-attempt finalization on the existing answer-attempt lock path.
- Extend quality gates so close, approve, reject, and resubmit lifecycle transitions all require locked exam status checks.

## Three-End Coordination

- Teacher end: repeated or concurrent close actions do not race through the published-state check.
- Student end: active attempts are force-submitted from one authoritative close transition.
- Admin/audit end: exam lifecycle state remains a single ordered transition instead of parallel close operations.

## Acceptance Notes

- The exam lifecycle lock is scoped to one `exam` row.
- Individual active attempts are still locked by `loadAttemptForSubmit` before forced submission.
- This complements approval and score release transition locks from batches 254 and 255.
