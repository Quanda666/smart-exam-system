# 114. Monitor Event Submit Preservation

## Background

The student taking screen already records focus loss, visibility changes, copy/paste, fullscreen exit, network changes, and heartbeat failures as monitor events. It also flushes monitor events before submit. The remaining risk was submit-time data loss: if the final monitor flush failed but the answer submit succeeded, the frontend cleared the local monitor event queue anyway.

For a real exam system, monitoring events are audit evidence. They must remain risk records and must not block answering, but the client should not delete unaccepted events by itself.

## Scope

- Keep monitoring non-blocking for students.
- Preserve unflushed monitor events when submit succeeds but the monitor event flush fails.
- Clear monitor events only after the server confirms the queue is fully flushed.
- Add a quality-gate rule for the submit preservation behavior.

## Frontend Contract

- `ExamTaking.flushMonitorEvents`
  - Returns `true` when the local queue is empty or the flushed batch leaves no remaining events.
  - Returns `false` when a flush is already in flight, the browser is offline in non-force mode, or the server call fails.
  - Persists the queue on every failure path.

- `ExamTaking.submit`
  - Calls `flushMonitorEvents(true)` before answer submit.
  - Submits answers even if monitor flush fails, preserving the principle that monitoring must not block answering.
  - Clears monitor queue only when `monitorFlushed` is true.
  - Persists remaining monitor events when `monitorFlushed` is false.

## Three-End Collaboration

- Student end:
  - Can submit normally even if monitor-event reporting has a transient failure.
  - Keeps unflushed monitor records in local storage instead of silently deleting them.

- Teacher end:
  - Receives all monitor events that the server accepted.
  - Does not receive fabricated violation verdicts; events remain risk/audit records.

- Admin end:
  - Audit exports remain based on server-accepted monitor events.
  - Client-side preservation reduces silent evidence loss during submit-time network instability.

## Invariant

Submitting answers must not delete monitor events unless the server has accepted or deduplicated the flushed batch and the local queue is empty.

```text
answer_submit_success
AND monitor_flush_failed
=> local monitor event queue is persisted
```

## Quality Gate

`scripts/run-quality-gates.ps1` now checks that:

- `ExamTaking.submit` stores the result of `flushMonitorEvents(true)`.
- Monitor events are cleared only behind an `if (monitorFlushed)` branch.
- `flushMonitorEvents` has explicit failure returns.
- The function returns whether the monitor queue is empty after a successful flush.

## Acceptance Points

- Normal submit still succeeds if monitor reporting fails.
- Successfully flushed monitor events are removed from local storage.
- Failed or partial monitor flushes leave remaining events persisted.
- Future edits cannot easily reintroduce unconditional submit-time monitor queue clearing.
