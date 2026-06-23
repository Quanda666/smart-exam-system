# 398 Exam Taking Monitor Batch Isolation

## Scope
- Improve student-side monitor event upload resilience during an active exam.
- Keep monitor uploads non-blocking for answering and submission retry.

## Frontend
- `ExamTaking.flushMonitorEvents` now isolates permanent 400 responses after a batch upload fails.
- The page retries the failed batch one event at a time:
  - successfully uploaded events are removed from the local queue
  - permanently rejected events are removed so they cannot block later events
  - transient failures keep the remaining events in local storage for retry
- Forced flush before submit still drains all possible events and preserves any unflushed transient failures.

## Why This Matters
- Monitor ingestion now has stricter server-side timestamp and lifecycle checks.
- A single malformed, stale, or no-longer-acceptable event should not prevent valid later monitor events from reaching the teacher/admin audit trail.
- This closes a student terminal recovery gap without changing the rule that monitor events are risk records only.

## Verification
- `scripts/run-quality-gates.ps1` checks that `ExamTaking` isolates permanent monitor upload rejects and preserves transient failures.
